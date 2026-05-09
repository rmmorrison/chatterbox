package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;
import java.util.Locale;

/** Pure rendering helpers for trivia embeds — no Discord side effects. */
final class TriviaEmbedBuilder {

    static final int COLOR_LIVE     = 0x4F8DDC; // accent blue (live question)
    static final int COLOR_CORRECT  = 0x43B581; // green
    static final int COLOR_TIMEOUT  = 0x90A4AE; // grey

    private TriviaEmbedBuilder() {}

    /** Question-time embed: the prompt with lettered choices. */
    static MessageEmbed question(TriviaRound round) {
        TriviaQuestion q = round.question();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Trivia: " + q.category())
                .setColor(COLOR_LIVE)
                .setDescription(q.question())
                .addField("Choices", choiceList(round.shuffledChoices()), false)
                .setFooter("Difficulty: " + capitalise(q.difficulty())
                        + " · Source: Open Trivia DB · First correct click wins.");
        return eb.build();
    }

    /**
     * Resolution embed for a winning click. {@code winnerMention} is rendered
     * as a Discord mention (e.g. {@code <@123>}) so it pings the right user.
     */
    static MessageEmbed winner(TriviaRound round, String winnerMention) {
        TriviaQuestion q = round.question();
        return new EmbedBuilder()
                .setTitle("Trivia: " + q.category())
                .setColor(COLOR_CORRECT)
                .setDescription(q.question())
                .addField("Correct answer", "**" + round.correctAnswerLabel() + "**", false)
                .addField("Winner", winnerMention, false)
                .setFooter("Difficulty: " + capitalise(q.difficulty()) + " · Source: Open Trivia DB")
                .build();
    }

    /** Resolution embed used when nobody answered before the timeout. */
    static MessageEmbed timeout(TriviaRound round) {
        TriviaQuestion q = round.question();
        return new EmbedBuilder()
                .setTitle("Trivia: " + q.category())
                .setColor(COLOR_TIMEOUT)
                .setDescription(q.question())
                .addField("Correct answer", "**" + round.correctAnswerLabel() + "**", false)
                .addField("Result", "*Time's up — no winner.*", false)
                .setFooter("Difficulty: " + capitalise(q.difficulty()) + " · Source: Open Trivia DB")
                .build();
    }

    static String letter(int index) {
        // A, B, C, D — multiple-choice. True/False rounds use T/F instead.
        return String.valueOf((char) ('A' + index));
    }

    private static String choiceList(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("**").append(letter(i)).append(".** ").append(labels.get(i));
        }
        return sb.toString();
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return "?";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
