package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaEmbedBuilderTest {

    private static String fieldValue(MessageEmbed embed, String name) {
        for (MessageEmbed.Field f : embed.getFields()) {
            if (name.equals(f.getName())) return f.getValue();
        }
        throw new AssertionError("Missing field: " + name);
    }

    private static TriviaRound roundFor(TriviaQuestion q, List<String> labels, int correct,
                                        int roundNumber, int totalRounds) {
        return new TriviaRound("rid12345", "gid12345", roundNumber, totalRounds,
                100L, 200L, q, labels, correct, Collections.emptySet());
    }

    @Test
    void questionEmbedTitleIncludesRoundProgressAndCategory() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "easy", "Geography",
                "Capital of Canada?", "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
        TriviaRound round = roundFor(q,
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"), 1, 2, 5);

        MessageEmbed embed = TriviaEmbedBuilder.question(round);
        assertEquals("Trivia · Round 2 of 5 · Geography", embed.getTitle());
        assertEquals(TriviaEmbedBuilder.COLOR_LIVE, embed.getColorRaw());

        String choices = fieldValue(embed, "Choices");
        assertTrue(choices.contains("**A.** Toronto"), choices);
        assertTrue(choices.contains("**B.** Ottawa"), choices);

        String footer = embed.getFooter().getText();
        assertTrue(footer.contains("Easy"), footer);
        assertTrue(footer.toLowerCase().contains("first correct click"), footer);
    }

    @Test
    void questionEmbedDoesNotLeakCorrectAnswer() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "hard", "Cat",
                "Q?", "RIGHT",
                List.of("WRONG1", "WRONG2", "WRONG3"));
        TriviaRound round = roundFor(q,
                List.of("WRONG1", "RIGHT", "WRONG2", "WRONG3"), 1, 1, 5);

        MessageEmbed embed = TriviaEmbedBuilder.question(round);
        String choices = fieldValue(embed, "Choices");
        long boldCount = choices.chars().filter(c -> c == '*').count();
        assertEquals(16L, boldCount,
                "all four choices should be rendered with identical bold-letter prefix");
        assertTrue(!choices.contains("✅") && !choices.contains("✓"));
    }

    @Test
    void winnerEmbedShowsCorrectAnswerAndMentions() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "medium", "Geography",
                "Capital of Canada?", "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
        TriviaRound round = roundFor(q,
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"), 1, 1, 3);

        MessageEmbed embed = TriviaEmbedBuilder.winner(round, "<@123456>");
        assertEquals(TriviaEmbedBuilder.COLOR_CORRECT, embed.getColorRaw());
        assertTrue(fieldValue(embed, "Correct answer").contains("Ottawa"));
        assertEquals("<@123456>", fieldValue(embed, "Winner"));
        // Round numbering survives into the resolution embed.
        assertTrue(embed.getTitle().contains("Round 1 of 3"));
    }

    @Test
    void timeoutEmbedShowsAnswerAndGreyAccent() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.BOOLEAN, "easy", "Cat",
                "Sky is blue.", "True", List.of("False"));
        TriviaRound round = roundFor(q, List.of("True", "False"), 0, 3, 5);

        MessageEmbed embed = TriviaEmbedBuilder.timeout(round);
        assertEquals(TriviaEmbedBuilder.COLOR_TIMEOUT, embed.getColorRaw());
        assertTrue(fieldValue(embed, "Correct answer").contains("True"));
        assertTrue(fieldValue(embed, "Result").toLowerCase().contains("no winner"));
    }

    // -- final leaderboard --------------------------------------------------

    private static TriviaGame gameWithScores(int totalRounds, long... wins) {
        TriviaGame game = new TriviaGame("g1", 200L, 99L, TriviaFilter.any(), totalRounds, null);
        for (long userId : wins) game.recordWin(userId);
        return game;
    }

    @Test
    void leaderboardSortsDescendingAndUsesMentions() {
        TriviaGame game = gameWithScores(5, 100L, 100L, 100L, 200L, 300L, 200L);
        // user 100 → 3, user 200 → 2, user 300 → 1
        MessageEmbed embed = TriviaEmbedBuilder.gameOver(game);
        assertEquals(TriviaEmbedBuilder.COLOR_FINAL, embed.getColorRaw());
        String board = fieldValue(embed, "Leaderboard");
        // 100 > 200 > 300 by score, ordered top-to-bottom.
        int p100 = board.indexOf("<@100>");
        int p200 = board.indexOf("<@200>");
        int p300 = board.indexOf("<@300>");
        assertTrue(p100 < p200 && p200 < p300,
                "leaderboard should be highest-scorer-first; got: " + board);
        assertTrue(board.contains("<@100> — 3"));
        assertTrue(board.contains("<@200> — 2"));
        assertTrue(board.contains("<@300> — 1"));
    }

    @Test
    void leaderboardSharesRankOnTies() {
        TriviaGame game = gameWithScores(5, 100L, 200L, 300L, 100L, 200L);
        // 100 → 2, 200 → 2 (tie at rank 1), 300 → 1 (rank 3, not 2)
        String board = fieldValue(TriviaEmbedBuilder.gameOver(game), "Leaderboard");
        assertTrue(board.contains("**1.** <@100>"), board);
        assertTrue(board.contains("**1.** <@200>"), board);
        assertTrue(board.contains("**3.** <@300>"), board);
    }

    @Test
    void emptyScoresShowFriendlyMessage() {
        TriviaGame game = gameWithScores(5);
        MessageEmbed embed = TriviaEmbedBuilder.gameOver(game);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().toLowerCase().contains("nobody scored"),
                () -> embed.getDescription());
    }

    @Test
    void footerCarriesFilterSummaryWhenSet() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                new TriviaFilter(15, "hard"), 5, null);
        game.recordWin(100L);
        String footer = TriviaEmbedBuilder.gameOver(game).getFooter().getText();
        assertTrue(footer.contains("Hard"), footer);
        assertTrue(footer.contains("Video Games"), footer);
    }
}
