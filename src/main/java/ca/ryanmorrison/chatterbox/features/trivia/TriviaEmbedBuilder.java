package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure rendering helpers for trivia embeds — no Discord side effects. */
final class TriviaEmbedBuilder {

    static final int COLOR_LOBBY    = 0xF1C40F; // gold
    static final int COLOR_LIVE     = 0x4F8DDC; // accent blue (live question)
    static final int COLOR_RESULT   = 0x5865F2; // discord blurple (round result)
    static final int COLOR_TIMEOUT  = 0x90A4AE; // grey (no winner)
    static final int COLOR_FINAL    = 0xF1C40F; // gold

    private TriviaEmbedBuilder() {}

    /**
     * Lobby embed shown while players opt in. {@code secondsRemaining}
     * is folded into the description as an absolute Discord timestamp so
     * users see a live countdown without us editing every second.
     *
     * @param startsAtEpochSeconds when the lobby closes (UTC seconds);
     *                             rendered as {@code <t:N:R>}
     */
    static MessageEmbed lobby(TriviaGame game, long startsAtEpochSeconds) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎲 Trivia lobby")
                .setColor(COLOR_LOBBY)
                .setDescription(
                        "<@" + game.initiatorId() + "> started a trivia session.\n"
                        + "Click **Join** to play. Game starts <t:" + startsAtEpochSeconds + ":R>.")
                .addField("Settings", settingsLine(game), false)
                .addField("Players (" + game.joinedCount() + ")",
                        mentionList(game.joinedSnapshot()), false);
        return eb.build();
    }

    /** Posted when the lobby closes with no players (besides the initiator opting out is impossible — they're auto-joined). */
    static MessageEmbed lobbyCancelled(TriviaGame game, String reason) {
        return new EmbedBuilder()
                .setTitle("🎲 Trivia cancelled")
                .setColor(COLOR_TIMEOUT)
                .setDescription(reason)
                .build();
    }

    /** Live-round embed: the prompt with lettered choices and player roster. */
    static MessageEmbed question(TriviaRound round, long answerWindowEndsAtEpochSeconds) {
        TriviaQuestion q = round.question();
        return new EmbedBuilder()
                .setTitle(roundTitle(round))
                .setColor(COLOR_LIVE)
                .setDescription(q.question())
                .addField("Choices", choiceList(round.shuffledChoices()), false)
                .addField("Players (" + round.joinedPlayers().size() + ")",
                        mentionList(round.joinedPlayers()), false)
                .setFooter("Difficulty: " + capitalise(q.difficulty())
                        + " · Answer window closes <t:" + answerWindowEndsAtEpochSeconds + ":R>"
                        + " or when everyone has picked.")
                .build();
    }

    /**
     * Round result: shows the correct answer and a per-player breakdown
     * (✅ correct, ❌ wrong-with-pick, ⏰ no answer).
     */
    static MessageEmbed roundResult(TriviaRound round) {
        TriviaQuestion q = round.question();
        boolean anyCorrect = false;
        for (Map.Entry<Long, Integer> e : round.answers().entrySet()) {
            if (e.getValue() == round.correctIndex()) { anyCorrect = true; break; }
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(roundTitle(round))
                .setColor(anyCorrect ? COLOR_RESULT : COLOR_TIMEOUT)
                .setDescription(q.question())
                .addField("Correct answer",
                        "**" + letter(round.correctIndex()) + ". " + round.correctAnswerLabel() + "**",
                        false)
                .addField("Results", playerResults(round), false);
        return eb.build();
    }

    /**
     * End-of-game leaderboard. Includes every joined player so people who
     * actually played but scored zero still see their entry — clearer
     * than silently omitting them.
     */
    static MessageEmbed gameOver(TriviaGame game) {
        var entries = game.leaderboard();
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏆 Game over")
                .setColor(COLOR_FINAL);

        if (entries.isEmpty()) {
            eb.setDescription("Nobody played. Try again!");
        } else if (entries.get(0).getValue() == 0) {
            eb.setDescription("Nobody scored across " + game.totalRounds() + " round"
                    + (game.totalRounds() == 1 ? "" : "s") + ". Tough crowd!");
            eb.addField("Players", mentionList(game.joinedSnapshot()), false);
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

    private static String settingsLine(TriviaGame game) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.totalRounds()).append(" round")
          .append(game.totalRounds() == 1 ? "" : "s");
        TriviaFilter f = game.filter();
        if (f != null) {
            if (f.difficulty() != null) sb.append(" · ").append(capitalise(f.difficulty()));
            String catName = categoryDisplayName(game);
            if (catName != null) sb.append(" · ").append(catName);
        }
        sb.append(" · ").append(game.roundSeconds()).append("s per round");
        return sb.toString();
    }

    private static String filterSummary(TriviaGame game) {
        TriviaFilter f = game.filter();
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        if (f.difficulty() != null) sb.append(capitalise(f.difficulty()));
        String catName = categoryDisplayName(game);
        if (catName != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(catName);
        }
        return sb.toString();
    }

    /**
     * Render-safe category name. Uses the cache-resolved name stored on
     * the game; falls back to {@code Category #N} when the cache couldn't
     * resolve (e.g. opentdb's category endpoint was down at game start).
     * Returns null if no category filter was set.
     */
    private static String categoryDisplayName(TriviaGame game) {
        TriviaFilter f = game.filter();
        if (f == null || f.categoryId() == null) return null;
        if (game.categoryName() != null) return game.categoryName();
        return "Category #" + f.categoryId();
    }

    private static String mentionList(Set<Long> userIds) {
        if (userIds.isEmpty()) return "*(none yet)*";
        StringBuilder sb = new StringBuilder();
        for (Long id : userIds) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("<@").append(id).append('>');
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

    /**
     * Per-player round result lines. Groups all correct answerers on one
     * line, then a line per wrong answerer (with their pick), then a line
     * for each non-answerer.
     */
    private static String playerResults(TriviaRound round) {
        StringBuilder correct = new StringBuilder();
        StringBuilder wrong = new StringBuilder();
        StringBuilder noAnswer = new StringBuilder();

        for (Long userId : round.joinedPlayers()) {
            Integer pick = round.answers().get(userId);
            if (pick == null) {
                if (noAnswer.length() > 0) noAnswer.append('\n');
                noAnswer.append("⏰ <@").append(userId).append("> — no answer");
            } else if (pick == round.correctIndex()) {
                if (correct.length() > 0) correct.append(' ');
                correct.append("<@").append(userId).append('>');
            } else {
                if (wrong.length() > 0) wrong.append('\n');
                wrong.append("❌ <@").append(userId).append("> chose **")
                     .append(letter(pick)).append(". ")
                     .append(round.shuffledChoices().get(pick)).append("**");
            }
        }
        StringBuilder out = new StringBuilder();
        if (correct.length() > 0) {
            out.append("✅ ").append(correct);
        }
        if (wrong.length() > 0) {
            if (out.length() > 0) out.append('\n');
            out.append(wrong);
        }
        if (noAnswer.length() > 0) {
            if (out.length() > 0) out.append('\n');
            out.append(noAnswer);
        }
        return out.length() == 0 ? "*(no players)*" : out.toString();
    }

    /** {@code "**1.** <@id> — 3"}, one per line, ties share the rank. */
    private static String leaderboardLines(List<Map.Entry<Long, Integer>> sorted) {
        StringBuilder sb = new StringBuilder();
        int displayedRank = 0;
        int previousScore = Integer.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, Integer> e = sorted.get(i);
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
