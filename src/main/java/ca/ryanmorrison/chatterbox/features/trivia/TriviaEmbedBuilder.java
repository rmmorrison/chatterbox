package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure rendering helpers for trivia embeds — no Discord side effects. */
final class TriviaEmbedBuilder {

    static final int COLOR_LIVE     = 0x4F8DDC; // accent blue (live question)
    static final int COLOR_CORRECT  = 0x43B581; // green
    static final int COLOR_TIMEOUT  = 0x90A4AE; // grey
    static final int COLOR_FINAL    = 0xF1C40F; // gold

    private TriviaEmbedBuilder() {}

    /** Question-time embed: the prompt with lettered choices. */
    static MessageEmbed question(TriviaRound round) {
        TriviaQuestion q = round.question();
        return new EmbedBuilder()
                .setTitle(roundTitle(round))
                .setColor(COLOR_LIVE)
                .setDescription(q.question())
                .addField("Choices", choiceList(round.shuffledChoices()), false)
                .setFooter(footer(q) + " · First correct click wins this round.")
                .build();
    }

    /** Resolution embed for a winning click. */
    static MessageEmbed winner(TriviaRound round, String winnerMention) {
        TriviaQuestion q = round.question();
        return new EmbedBuilder()
                .setTitle(roundTitle(round))
                .setColor(COLOR_CORRECT)
                .setDescription(q.question())
                .addField("Correct answer", "**" + round.correctAnswerLabel() + "**", false)
                .addField("Winner", winnerMention, false)
                .setFooter(footer(q))
                .build();
    }

    /** Resolution embed used when nobody answered before the timeout. */
    static MessageEmbed timeout(TriviaRound round) {
        TriviaQuestion q = round.question();
        return new EmbedBuilder()
                .setTitle(roundTitle(round))
                .setColor(COLOR_TIMEOUT)
                .setDescription(q.question())
                .addField("Correct answer", "**" + round.correctAnswerLabel() + "**", false)
                .addField("Result", "*Time's up — no winner.*", false)
                .setFooter(footer(q))
                .build();
    }

    /**
     * End-of-game leaderboard. Users with zero correct answers are
     * omitted; if everyone scored zero we render a friendly empty-state.
     */
    static MessageEmbed gameOver(TriviaGame game) {
        var entries = game.leaderboard();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏆 Game over")
                .setColor(COLOR_FINAL);

        if (entries.isEmpty() || entries.get(0).getValue() == 0) {
            eb.setDescription("Nobody scored. Tough crowd!");
        } else {
            eb.setDescription("Final scores after " + game.totalRounds() + " round"
                    + (game.totalRounds() == 1 ? "" : "s") + ":");
            eb.addField("Leaderboard", leaderboardLines(entries), false);
        }
        String filterDesc = filterSummary(game);
        eb.setFooter(filterDesc.isEmpty()
                ? "Source: Open Trivia DB"
                : "Source: Open Trivia DB · " + filterDesc);
        return eb.build();
    }

    static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    // -- helpers ------------------------------------------------------------

    private static String roundTitle(TriviaRound round) {
        return "Trivia · Round " + round.roundNumber() + " of " + round.totalRounds()
                + " · " + round.question().category();
    }

    private static String footer(TriviaQuestion q) {
        return "Difficulty: " + capitalise(q.difficulty()) + " · Source: Open Trivia DB";
    }

    private static String filterSummary(TriviaGame game) {
        TriviaFilter f = game.filter();
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        if (f.difficulty() != null) sb.append(capitalise(f.difficulty()));
        if (f.categoryId() != null) {
            String name = TriviaCategories.all().entrySet().stream()
                    .filter(e -> e.getValue().equals(f.categoryId()))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (name != null) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(name);
            }
        }
        return sb.toString();
    }

    private static String choiceList(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("**").append(letter(i)).append(".** ").append(labels.get(i));
        }
        return sb.toString();
    }

    /** {@code "**1.** <@id> — 3"}, one per line, ties share the rank. */
    private static String leaderboardLines(List<Map.Entry<Long, Integer>> sorted) {
        StringBuilder sb = new StringBuilder();
        int displayedRank = 0;
        int previousScore = Integer.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, Integer> e = sorted.get(i);
            if (e.getValue() == 0) break; // omit zero-scorers from the medal list
            if (e.getValue() != previousScore) {
                displayedRank = i + 1;
                previousScore = e.getValue();
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append("**").append(displayedRank).append(".** ")
              .append("<@").append(e.getKey()).append("> — ")
              .append(e.getValue());
        }
        return sb.toString();
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return "?";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
