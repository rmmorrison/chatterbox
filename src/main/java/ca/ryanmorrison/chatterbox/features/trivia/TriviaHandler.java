package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles {@code /trivia} (slash) plus the Join and Answer button clicks
 * that drive a session through its lobby and round phases.
 *
 * <p>Game lifecycle:
 * <ol>
 *   <li>Slash → claim the channel (one game per channel) → request
 *       opentdb session token → post the lobby embed with a Join button.</li>
 *   <li>Join clicks add the user to the joined set and re-render the
 *       lobby embed live.</li>
 *   <li>Lobby timer fires → freeze the joined set → fetch round 1's
 *       question → post it with answer buttons.</li>
 *   <li>Each Answer click is recorded; if it's the last outstanding
 *       answer the round resolves immediately, otherwise it waits for
 *       the round timer.</li>
 *   <li>Round resolution scores the round, posts a result embed, then
 *       fetches and posts the next round (or the final leaderboard).</li>
 * </ol>
 *
 * <p>Custom-id formats:
 * <ul>
 *   <li>{@code trivia:join:<gameId>}</li>
 *   <li>{@code trivia:answer:<roundId>:<choiceIndex>} — index encodes
 *       only the position in the shuffled choice list, never the answer
 *       text.</li>
 * </ul>
 *
 * <p>Why a worker pool: opentdb fetches block on the network (and self-rate-limit
 * up to 5.5s). Doing them on the JDA event thread or the timeout
 * scheduler thread would freeze unrelated work; a single dedicated
 * worker keeps fetches off both.
 */
final class TriviaHandler extends ListenerAdapter {

    static final String COMMAND        = "trivia";
    static final String OPT_DIFFICULTY = "difficulty";
    static final String OPT_CATEGORY   = "category";
    static final String OPT_ROUNDS     = "rounds";
    static final String OPT_LOBBY      = "lobby";

    static final String BUTTON_JOIN_PREFIX   = "trivia:join:";
    static final String BUTTON_ANSWER_PREFIX = "trivia:answer:";

    static final int MIN_ROUNDS         = 1;
    static final int MAX_ROUNDS         = 10;
    static final int DEFAULT_ROUNDS     = 5;
    static final int MIN_LOBBY_SECONDS  = 5;
    static final int MAX_LOBBY_SECONDS  = 120;
    static final int DEFAULT_LOBBY_SECS = 30;
    static final int ROUND_SECONDS      = 20;

    private static final Logger log = LoggerFactory.getLogger(TriviaHandler.class);

    private final TriviaClient client;
    private final TriviaRounds rounds;
    private final ScheduledExecutorService worker;
    private volatile JDA jda;

    TriviaHandler(TriviaClient client, TriviaRounds rounds) {
        this(client, rounds, defaultWorker());
    }

    /** Test seam — supply a deterministic worker. */
    TriviaHandler(TriviaClient client, TriviaRounds rounds, ScheduledExecutorService worker) {
        this.client = client;
        this.rounds = rounds;
        this.worker = worker;
    }

    private static ScheduledExecutorService defaultWorker() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "trivia-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Called from {@link TriviaModule#onStart} once JDA is ready. */
    void setJda(JDA jda) {
        this.jda = jda;
    }

    void shutdown() {
        rounds.stop();
        worker.shutdownNow();
    }

    // -- slash command -----------------------------------------------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        String difficulty = stringOption(event, OPT_DIFFICULTY);
        Integer categoryId = intOption(event, OPT_CATEGORY);
        int totalRounds = clamp(intOptionOrDefault(event, OPT_ROUNDS, DEFAULT_ROUNDS),
                MIN_ROUNDS, MAX_ROUNDS);
        int lobbySeconds = clamp(intOptionOrDefault(event, OPT_LOBBY, DEFAULT_LOBBY_SECS),
                MIN_LOBBY_SECONDS, MAX_LOBBY_SECONDS);

        TriviaFilter filter;
        try {
            filter = TriviaFilter.validated(categoryId, difficulty);
        } catch (TriviaClient.TriviaException e) {
            event.reply(e.getMessage()).setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannelIdLong();
        String gameId = rounds.newId();

        if (!rounds.tryClaimChannel(channelId, gameId)) {
            event.reply("There's already a trivia session in this channel. "
                    + "Wait for it to finish.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        worker.execute(() -> openLobby(event, channelId, gameId, filter,
                totalRounds, lobbySeconds));
    }

    /**
     * Pre-load all questions for the session before the lobby opens.
     * Failing here means we never post the lobby — players don't sit
     * through 30 seconds only to be told the upstream is unhappy.
     *
     * <p>Two opentdb calls happen here, gated by the client's 5.5s
     * rate-limiter: the (optional) session-token request and the batched
     * question fetch. From the user's perspective {@code /trivia} takes
     * roughly 6 seconds before the lobby appears; that's the cost of
     * never hitting a 429 mid-game.
     */
    private void openLobby(SlashCommandInteractionEvent event,
                           long channelId,
                           String gameId,
                           TriviaFilter filter,
                           int totalRounds,
                           int lobbySeconds) {
        String token = null;
        try {
            token = client.requestSessionToken().orElse(null);
        } catch (TriviaClient.TriviaException e) {
            log.debug("Trivia session token request failed: {}", e.getMessage());
        }

        List<TriviaQuestion> questions;
        try {
            questions = client.fetchBatch(filter, token, totalRounds);
        } catch (TriviaClient.TokenExhaustedException e) {
            // Tokens last 6h server-side; an exhausted token at game-start
            // is rare but possible if the same one's been used a lot. Retry
            // tokenless.
            try {
                questions = client.fetchBatch(filter, totalRounds);
            } catch (TriviaClient.TriviaException retry) {
                event.getHook().sendMessage(retry.getMessage()).setEphemeral(true).queue();
                rounds.releaseChannel(channelId, gameId);
                return;
            }
        } catch (TriviaClient.TriviaException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
            rounds.releaseChannel(channelId, gameId);
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error pre-loading trivia questions for game {}", gameId, e);
            event.getHook().sendMessage("Something went wrong starting that game.")
                    .setEphemeral(true).queue();
            rounds.releaseChannel(channelId, gameId);
            return;
        }

        TriviaGame game = new TriviaGame(gameId, channelId,
                event.getUser().getIdLong(),
                filter, questions, lobbySeconds, ROUND_SECONDS);
        rounds.registerGame(game);

        long startsAt = Instant.now().getEpochSecond() + lobbySeconds;
        MessageEmbed embed = TriviaEmbedBuilder.lobby(game, startsAt);
        Button joinButton = Button.success(BUTTON_JOIN_PREFIX + gameId, "Join");

        event.getHook().sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(joinButton))
                .queue(msg -> {
                    game.setLobbyMessageId(msg.getIdLong());
                    rounds.schedule(() -> closeLobbyAndStartGame(game), lobbySeconds);
                }, err -> {
                    log.warn("Failed to post trivia lobby for game {}: {}",
                            gameId, err.toString());
                    rounds.removeGame(gameId);
                    rounds.releaseChannel(channelId, gameId);
                });
    }

    // -- buttons -----------------------------------------------------------

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith(BUTTON_JOIN_PREFIX)) {
            handleJoin(event, id.substring(BUTTON_JOIN_PREFIX.length()));
        } else if (id.startsWith(BUTTON_ANSWER_PREFIX)) {
            handleAnswer(event, id.substring(BUTTON_ANSWER_PREFIX.length()));
        }
    }

    private void handleJoin(ButtonInteractionEvent event, String gameId) {
        TriviaGame game = rounds.game(gameId).orElse(null);
        if (game == null || game.phase() != TriviaGame.Phase.LOBBY) {
            event.reply("This lobby is closed.").setEphemeral(true).queue();
            return;
        }
        long userId = event.getUser().getIdLong();
        boolean newlyJoined = game.addPlayer(userId);
        if (!newlyJoined) {
            event.reply("You're already in this session.").setEphemeral(true).queue();
            return;
        }
        // Refresh the lobby embed to reflect the new roster.
        long startsAt = Instant.now().getEpochSecond()
                + secondsRemainingInLobby(game, event);
        event.editMessageEmbeds(TriviaEmbedBuilder.lobby(game, startsAt))
                .queue(null, err -> log.debug(
                        "Failed to refresh lobby embed for game {}: {}",
                        gameId, err.toString()));
    }

    /**
     * Best-effort countdown estimator for the lobby refresh — the absolute
     * deadline isn't stored anywhere we can read here, so we approximate
     * from the original duration. The relative-timestamp render
     * ({@code <t:N:R>}) hides any ±1s drift.
     */
    private long secondsRemainingInLobby(TriviaGame game, ButtonInteractionEvent event) {
        // We don't track lobbyClosesAt explicitly; best estimate is "click
        // happened, lobby still open, so use full lobbySeconds from now." A
        // user joining late will see a slightly-too-long countdown for one
        // refresh — preferable to plumbing a deadline through every call.
        return game.lobbySeconds();
    }

    private void handleAnswer(ButtonInteractionEvent event, String rest) {
        int sep = rest.lastIndexOf(':');
        if (sep <= 0 || sep == rest.length() - 1) {
            event.reply("This trivia button is malformed.").setEphemeral(true).queue();
            return;
        }
        String roundId = rest.substring(0, sep);
        int choiceIndex;
        try {
            choiceIndex = Integer.parseInt(rest.substring(sep + 1));
        } catch (NumberFormatException e) {
            event.reply("This trivia button is malformed.").setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        TriviaRounds.AttemptResult result = rounds.recordAnswer(roundId, userId, choiceIndex);

        switch (result.outcome()) {
            case NOT_FOUND -> event.reply("This round has ended.").setEphemeral(true).queue();
            case NOT_JOINED -> event.reply("You aren't in this session — wait for the next one!")
                    .setEphemeral(true).queue();
            case ALREADY_ANSWERED -> event.reply("You've already locked in an answer.")
                    .setEphemeral(true).queue();
            case RECORDED -> event.reply("✅ Locked in!").setEphemeral(true).queue();
            case RECORDED_LAST -> {
                // Acknowledge the click first, then close the round.
                event.reply("✅ Locked in!").setEphemeral(true).queue();
                rounds.consumeRound(roundId).ifPresent(this::resolveRound);
            }
        }
    }

    // -- lobby close → start game -----------------------------------------

    private void closeLobbyAndStartGame(TriviaGame game) {
        if (game.joinedCount() == 0) {
            // Initiator is auto-joined so this should be unreachable, but
            // guard anyway.
            cancelGame(game, "Nobody joined the lobby.");
            return;
        }
        game.transitionToPlaying();
        // Strip the Join button off the lobby message; leave the embed for history.
        MessageChannel channel = resolveChannel(game.channelId());
        if (channel != null && game.lobbyMessageId() != 0L) {
            channel.editMessageComponentsById(game.lobbyMessageId())
                    .setComponents()
                    .queue(null, err -> log.debug(
                            "Failed to clear lobby buttons for game {}: {}",
                            game.gameId(), err.toString()));
        }
        worker.execute(() -> postNextRound(game));
    }

    // -- round flow --------------------------------------------------------

    private void postNextRound(TriviaGame game) {
        if (game.isLastRoundComplete()) {
            finishGame(game);
            return;
        }

        int roundNumber = game.advance();
        TriviaQuestion question = game.questionForRound(roundNumber);
        TriviaRound round = buildRound(game, roundNumber, question);
        long answerWindowEndsAt = Instant.now().getEpochSecond() + ROUND_SECONDS;
        MessageEmbed embed = TriviaEmbedBuilder.question(round, answerWindowEndsAt);
        List<Button> buttons = buildAnswerButtons(round.roundId(), round.shuffledChoices());

        MessageChannel channel = resolveChannel(game.channelId());
        if (channel == null) {
            log.warn("Channel {} unavailable; abandoning game {}.",
                    game.channelId(), game.gameId());
            forgetGame(game);
            return;
        }
        channel.sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(buttons))
                .queue(msg -> {
                    TriviaRound posted = round.withMessage(msg.getIdLong(), msg.getChannelIdLong());
                    rounds.register(posted, ROUND_SECONDS,
                            () -> rounds.consumeRound(posted.roundId())
                                    .ifPresent(this::resolveRound));
                }, err -> {
                    log.warn("Failed to post round {} of game {}: {}",
                            roundNumber, game.gameId(), err.toString());
                    forgetGame(game);
                });
    }

    /** Score the round, post the result, then advance. */
    private void resolveRound(TriviaRound round) {
        TriviaGame game = rounds.game(round.gameId()).orElse(null);
        if (game != null) {
            for (Map.Entry<Long, Integer> e : round.answers().entrySet()) {
                if (e.getValue() == round.correctIndex()) {
                    game.recordWin(e.getKey());
                }
            }
        }
        MessageChannel channel = resolveChannel(round.channelId());
        if (channel != null) {
            // Edit the original question message to show the result + drop
            // the answer buttons. Players see what they got right or wrong.
            channel.editMessageEmbedsById(round.messageId(), TriviaEmbedBuilder.roundResult(round))
                    .setComponents()
                    .queue(null, err -> log.debug(
                            "Failed to edit round-result for round {}: {}",
                            round.roundId(), err.toString()));
        }
        if (game == null) return;
        // Move on after a brief pause so players can read the result.
        rounds.schedule(() -> worker.execute(() -> postNextRound(game)), 3);
    }

    private void finishGame(TriviaGame game) {
        MessageChannel channel = resolveChannel(game.channelId());
        if (channel != null) {
            channel.sendMessageEmbeds(TriviaEmbedBuilder.gameOver(game))
                    .queue(null, err -> log.debug(
                            "Failed to post final leaderboard for game {}: {}",
                            game.gameId(), err.toString()));
        }
        forgetGame(game);
    }

    private void cancelGame(TriviaGame game, String reason) {
        MessageChannel channel = resolveChannel(game.channelId());
        if (channel != null) {
            channel.sendMessageEmbeds(TriviaEmbedBuilder.lobbyCancelled(game, reason)).queue();
        }
        forgetGame(game);
    }

    private void forgetGame(TriviaGame game) {
        game.markFinished();
        rounds.removeGame(game.gameId());
        rounds.releaseChannel(game.channelId(), game.gameId());
    }

    // -- helpers -----------------------------------------------------------

    private MessageChannel resolveChannel(long channelId) {
        JDA local = this.jda;
        if (local == null) return null;
        return local.getChannelById(MessageChannel.class, channelId);
    }

    private TriviaRound buildRound(TriviaGame game, int roundNumber, TriviaQuestion question) {
        TriviaRounds.Shuffled shuffled = TriviaRounds.shuffle(question);
        return new TriviaRound(
                rounds.newId(),
                game.gameId(),
                roundNumber,
                game.totalRounds(),
                0L, 0L,
                question,
                shuffled.labels(),
                shuffled.correctIndex(),
                game.joinedSnapshot(),
                Collections.unmodifiableMap(new HashMap<>()));
    }

    static List<Button> buildAnswerButtons(String roundId, List<String> labels) {
        List<Button> out = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            String label = TriviaEmbedBuilder.letter(i) + ". " + truncateForButton(labels.get(i));
            out.add(Button.secondary(BUTTON_ANSWER_PREFIX + roundId + ":" + i, label));
        }
        return out;
    }

    /** Discord caps button labels at 80 characters. */
    private static String truncateForButton(String s) {
        int max = 75;
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    private static Integer intOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsInt();
    }

    private static int intOptionOrDefault(SlashCommandInteractionEvent event, String name, int dflt) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? dflt : opt.getAsInt();
    }

}
