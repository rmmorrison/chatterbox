package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles {@code /trivia} (slash) and the answer button clicks that follow.
 *
 * <p>Lifecycle: built once in {@link TriviaModule#listeners}; the JDA
 * reference is set in {@link TriviaModule#onStart} so the round-timeout
 * callback can resolve a channel without an interaction context.
 *
 * <p>Custom-id format on each button: {@code trivia:answer:<roundId>:<index>}.
 * The {@code roundId} keys into {@link TriviaRounds}; {@code index} is the
 * zero-based position in the shuffled choice list. The correct answer is
 * <em>not</em> encoded in the button — only the index is — so a curious
 * client inspecting the components can't see which choice is right.
 */
final class TriviaHandler extends ListenerAdapter {

    static final String COMMAND      = "trivia";
    static final String OPT_DIFFICULTY = "difficulty";

    static final String BUTTON_PREFIX = "trivia:answer:";
    static final long ROUND_TIMEOUT_SECONDS = 60L;

    private static final Logger log = LoggerFactory.getLogger(TriviaHandler.class);

    private final TriviaClient client;
    private final TriviaRounds rounds;
    private volatile JDA jda;

    TriviaHandler(TriviaClient client, TriviaRounds rounds) {
        this.client = client;
        this.rounds = rounds;
    }

    /** Called from {@link TriviaModule#onStart} once JDA is ready. */
    void setJda(JDA jda) {
        this.jda = jda;
    }

    void shutdown() {
        rounds.stop();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        String difficulty = stringOption(event, OPT_DIFFICULTY);

        event.deferReply().queue();

        TriviaQuestion question;
        try {
            question = client.fetch(difficulty);
        } catch (TriviaClient.TriviaException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error fetching trivia question", e);
            event.getHook().sendMessage("Something went wrong fetching that question.")
                    .setEphemeral(true).queue();
            return;
        }

        TriviaRounds.Shuffled shuffled = TriviaRounds.shuffle(question);
        String roundId = rounds.newRoundId();

        // Build the round shell now (without message id) so the embed can
        // reference it; we'll patch in messageId/channelId once Discord
        // confirms the post.
        TriviaRound shell = new TriviaRound(
                roundId, 0L, 0L, question,
                shuffled.labels(), shuffled.correctIndex(),
                Collections.emptySet());

        MessageEmbed embed = TriviaEmbedBuilder.question(shell);
        List<Button> buttons = buildButtons(roundId, shuffled.labels());

        event.getHook().sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(buttons))
                .queue(msg -> {
                    TriviaRound finalRound = new TriviaRound(
                            roundId, msg.getIdLong(), msg.getChannelIdLong(), question,
                            shuffled.labels(), shuffled.correctIndex(),
                            Collections.emptySet());
                    rounds.register(finalRound, ROUND_TIMEOUT_SECONDS,
                            () -> revealOnTimeout(finalRound));
                }, err -> log.warn("Failed to post trivia question for round {}: {}",
                        roundId, err.toString()));
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
            case CORRECT_FIRST -> {
                TriviaRound round = result.round().orElseThrow();
                MessageEmbed reveal = TriviaEmbedBuilder.winner(round, event.getUser().getAsMention());
                event.editMessageEmbeds(reveal).setComponents().queue(
                        null,
                        err -> log.warn("Failed to reveal trivia winner for round {}: {}",
                                roundId, err.toString()));
            }
            case WRONG -> event.reply("❌ Not quite! That answer is locked in for you.")
                    .setEphemeral(true).queue();
            case ALREADY_ANSWERED -> event.reply("You've already answered this one.")
                    .setEphemeral(true).queue();
            case NOT_FOUND -> event.reply("This round has ended.").setEphemeral(true).queue();
        }
    }

    private void revealOnTimeout(TriviaRound round) {
        JDA local = this.jda;
        if (local == null) return;
        MessageChannel channel = local.getChannelById(MessageChannel.class, round.channelId());
        if (channel == null) {
            log.debug("Channel {} unavailable for trivia timeout reveal of {}.",
                    round.channelId(), round.roundId());
            return;
        }
        channel.editMessageEmbedsById(round.messageId(), TriviaEmbedBuilder.timeout(round))
                .setComponents()
                .queue(null, err -> log.debug(
                        "Failed to edit timed-out trivia message {} in {}: {}",
                        round.messageId(), round.channelId(), err.toString()));
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
        // Reserve 4 chars for the "X. " prefix added in buildButtons plus a safety margin.
        int max = 75;
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…"; // ellipsis
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }
}
