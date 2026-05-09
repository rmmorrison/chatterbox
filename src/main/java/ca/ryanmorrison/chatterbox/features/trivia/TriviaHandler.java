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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles {@code /trivia} (slash) and the answer button clicks that
 * follow. Owns multi-round game lifecycle: posts each round, scores the
 * winner on resolution, advances to the next round, and posts a final
 * leaderboard at game-end.
 *
 * <p>Custom-id format on each button: {@code trivia:answer:<roundId>:<index>}.
 * The round id keys into {@link TriviaRounds}; the index is the zero-based
 * position in the shuffled choice list. The correct answer is never
 * encoded in the button.
 *
 * <p>Why a dedicated worker pool: opentdb fetches block on the network and
 * we want round advancement to happen off the JDA event thread (and off
 * the timeout-scheduler thread). One thread is enough — at most one fetch
 * is in flight per game and games are infrequent.
 */
final class TriviaHandler extends ListenerAdapter {

    static final String COMMAND          = "trivia";
    static final String OPT_DIFFICULTY   = "difficulty";
    static final String OPT_CATEGORY     = "category";
    static final String OPT_ROUNDS       = "rounds";

    static final String BUTTON_PREFIX = "trivia:answer:";
    static final long ROUND_TIMEOUT_SECONDS = 60L;
    static final int  MIN_ROUNDS = 1;
    static final int  MAX_ROUNDS = 10;
    static final int  DEFAULT_ROUNDS = 5;

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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        String difficulty = stringOption(event, OPT_DIFFICULTY);
        Integer categoryId = intOption(event, OPT_CATEGORY);
        int totalRounds = clamp(intOptionOrDefault(event, OPT_ROUNDS, DEFAULT_ROUNDS),
                MIN_ROUNDS, MAX_ROUNDS);

        TriviaFilter filter;
        try {
            filter = TriviaFilter.validated(categoryId, difficulty);
        } catch (TriviaClient.TriviaException e) {
            event.reply(e.getMessage()).setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        // Off-thread: token request + first-question fetch + post.
        worker.execute(() -> startGame(event, filter, totalRounds));
    }

    private void startGame(SlashCommandInteractionEvent event,
                           TriviaFilter filter,
                           int totalRounds) {
        String token = null;
        try {
            token = client.requestSessionToken().orElse(null);
        } catch (TriviaClient.TriviaException e) {
            // Token request failed — proceed without one. Repeats are
            // possible but the game can still play.
            log.debug("Trivia session token request failed: {}", e.getMessage());
        }

        TriviaGame game = new TriviaGame(
                rounds.newId(),
                event.getChannelIdLong(),
                event.getUser().getIdLong(),
                filter,
                totalRounds,
                token);
        rounds.registerGame(game);

        TriviaQuestion question;
        try {
            question = client.fetch(filter, token);
        } catch (TriviaClient.TokenExhaustedException e) {
            // First-question token miss is exotic but possible. Retry once
            // tokenless before giving up.
            try {
                question = client.fetch(filter);
            } catch (TriviaClient.TriviaException retry) {
                rounds.removeGame(game.gameId());
                event.getHook().sendMessage(retry.getMessage()).setEphemeral(true).queue();
                return;
            }
        } catch (TriviaClient.TriviaException e) {
            rounds.removeGame(game.gameId());
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error fetching trivia question", e);
            rounds.removeGame(game.gameId());
            event.getHook().sendMessage("Something went wrong starting that game.")
                    .setEphemeral(true).queue();
            return;
        }

        int roundNumber = game.advance();
        TriviaRound round = buildRound(game, roundNumber, question);
        MessageEmbed embed = TriviaEmbedBuilder.question(round);
        List<Button> buttons = buildButtons(round.roundId(), round.shuffledChoices());

        event.getHook().sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(buttons))
                .queue(msg -> {
                    TriviaRound posted = round.withMessage(msg.getIdLong(), msg.getChannelIdLong());
                    rounds.register(posted, ROUND_TIMEOUT_SECONDS,
                            () -> revealOnTimeout(posted));
                }, err -> {
                    log.warn("Failed to post first round of game {}: {}",
                            game.gameId(), err.toString());
                    rounds.removeGame(game.gameId());
                });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(BUTTON_PREFIX)) return;

        String rest = id.substring(BUTTON_PREFIX.length());
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
        TriviaRounds.Result result = rounds.attempt(roundId, userId, choiceIndex);

        switch (result.outcome()) {
            case CORRECT_FIRST -> handleCorrect(event, result.round().orElseThrow());
            case WRONG -> event.reply("❌ Not quite! That answer is locked in for you.")
                    .setEphemeral(true).queue();
            case ALREADY_ANSWERED -> event.reply("You've already answered this one.")
                    .setEphemeral(true).queue();
            case NOT_FOUND -> event.reply("This round has ended.").setEphemeral(true).queue();
        }
    }

    private void handleCorrect(ButtonInteractionEvent event, TriviaRound round) {
        long userId = event.getUser().getIdLong();
        TriviaGame game = rounds.game(round.gameId()).orElse(null);
        if (game != null) {
            game.recordWin(userId);
        }
        MessageEmbed reveal = TriviaEmbedBuilder.winner(round, event.getUser().getAsMention());
        event.editMessageEmbeds(reveal).setComponents().queue(
                v -> advanceOrFinish(round.gameId()),
                err -> {
                    log.warn("Failed to reveal trivia winner for round {}: {}",
                            round.roundId(), err.toString());
                    advanceOrFinish(round.gameId());
                });
    }

    /**
     * Called after a round resolves (correct click or timeout). Decides
     * whether to post the next round or finalise the game. Runs on the
     * caller's thread but the worker pool serialises subsequent fetches.
     */
    private void advanceOrFinish(String gameId) {
        worker.execute(() -> {
            TriviaGame game = rounds.game(gameId).orElse(null);
            if (game == null) return;

            if (game.isFinished()) {
                postFinalLeaderboard(game);
                rounds.removeGame(gameId);
                return;
            }

            TriviaQuestion question;
            try {
                question = client.fetch(game.filter(), game.sessionToken());
            } catch (TriviaClient.TokenExhaustedException e) {
                // No-repeat pool drained mid-game. Continue tokenless.
                try {
                    question = client.fetch(game.filter());
                } catch (TriviaClient.TriviaException retry) {
                    abortMidGame(game, retry.getMessage());
                    return;
                }
            } catch (TriviaClient.TriviaException e) {
                abortMidGame(game, e.getMessage());
                return;
            } catch (RuntimeException e) {
                log.warn("Unexpected error fetching next trivia question for game {}",
                        gameId, e);
                abortMidGame(game, "Something went wrong fetching the next question.");
                return;
            }

            int roundNumber = game.advance();
            TriviaRound round = buildRound(game, roundNumber, question);
            MessageEmbed embed = TriviaEmbedBuilder.question(round);
            List<Button> buttons = buildButtons(round.roundId(), round.shuffledChoices());

            MessageChannel channel = resolveChannel(game.channelId());
            if (channel == null) {
                log.warn("Channel {} unavailable; abandoning game {}.",
                        game.channelId(), gameId);
                rounds.removeGame(gameId);
                return;
            }
            channel.sendMessageEmbeds(embed)
                    .addComponents(ActionRow.of(buttons))
                    .queue(msg -> {
                        TriviaRound posted = round.withMessage(msg.getIdLong(), msg.getChannelIdLong());
                        rounds.register(posted, ROUND_TIMEOUT_SECONDS,
                                () -> revealOnTimeout(posted));
                    }, err -> {
                        log.warn("Failed to post round {} of game {}: {}",
                                roundNumber, gameId, err.toString());
                        rounds.removeGame(gameId);
                    });
        });
    }

    private void abortMidGame(TriviaGame game, String reason) {
        MessageChannel channel = resolveChannel(game.channelId());
        if (channel != null) {
            channel.sendMessage("Game ended early: " + reason).queue();
            // Still post whatever leaderboard we accumulated.
            channel.sendMessageEmbeds(TriviaEmbedBuilder.gameOver(game)).queue();
        }
        rounds.removeGame(game.gameId());
    }

    private void postFinalLeaderboard(TriviaGame game) {
        MessageChannel channel = resolveChannel(game.channelId());
        if (channel == null) return;
        channel.sendMessageEmbeds(TriviaEmbedBuilder.gameOver(game))
                .queue(null, err -> log.debug("Failed to post final leaderboard for game {}: {}",
                        game.gameId(), err.toString()));
    }

    private void revealOnTimeout(TriviaRound round) {
        MessageChannel channel = resolveChannel(round.channelId());
        if (channel != null) {
            channel.editMessageEmbedsById(round.messageId(), TriviaEmbedBuilder.timeout(round))
                    .setComponents()
                    .queue(null, err -> log.debug(
                            "Failed to edit timed-out trivia message {} in {}: {}",
                            round.messageId(), round.channelId(), err.toString()));
        }
        advanceOrFinish(round.gameId());
    }

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
                Collections.emptySet());
    }

    static List<Button> buildButtons(String roundId, List<String> labels) {
        List<Button> out = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            String label = TriviaEmbedBuilder.letter(i) + ". " + truncateForButton(labels.get(i));
            out.add(Button.secondary(BUTTON_PREFIX + roundId + ":" + i, label));
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
