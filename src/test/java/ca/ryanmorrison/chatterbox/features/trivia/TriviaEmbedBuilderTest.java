package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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

    private static TriviaQuestion sampleMultiple() {
        return new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "easy", "Geography",
                "Capital of Canada?", "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
    }

    private static TriviaRound roundFor(TriviaQuestion q, List<String> labels, int correct,
                                        int roundNumber, int totalRounds,
                                        Set<Long> joined,
                                        java.util.Map<Long, Integer> answers) {
        return new TriviaRound("rid12345", "gid12345", roundNumber, totalRounds,
                100L, 200L, q, labels, correct, joined,
                Collections.unmodifiableMap(new LinkedHashMap<>(answers)));
    }

    private static TriviaGame freshGame(int totalRounds) {
        var qs = new java.util.ArrayList<TriviaQuestion>();
        for (int i = 0; i < totalRounds; i++) qs.add(sampleMultiple());
        return new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), qs, 30, 20);
    }

    // -- lobby --------------------------------------------------------------

    @Test
    void lobbyShowsInitiatorAndStartTimestamp() {
        TriviaGame game = freshGame(5);
        MessageEmbed embed = TriviaEmbedBuilder.lobby(game, 1_000_000L);
        assertEquals("🎲 Trivia lobby", embed.getTitle());
        assertEquals(TriviaEmbedBuilder.COLOR_LOBBY, embed.getColorRaw());
        assertTrue(embed.getDescription().contains("<@99>"),
                "initiator should be mentioned");
        assertTrue(embed.getDescription().contains("<t:1000000:R>"),
                "start time should be a relative Discord timestamp");
        // Settings line includes round count + per-round seconds.
        String settings = fieldValue(embed, "Settings");
        assertTrue(settings.contains("5 rounds"), settings);
        assertTrue(settings.contains("20s per round"), settings);
        // Initiator is auto-joined → players field shows them.
        assertTrue(fieldValue(embed, "Players (1)").contains("<@99>"));
    }

    @Test
    void lobbyShowsAllJoinedPlayers() {
        TriviaGame game = freshGame(5);
        game.addPlayer(100L);
        game.addPlayer(200L);
        MessageEmbed embed = TriviaEmbedBuilder.lobby(game, 1L);
        String roster = fieldValue(embed, "Players (3)");
        assertTrue(roster.contains("<@99>"));
        assertTrue(roster.contains("<@100>"));
        assertTrue(roster.contains("<@200>"));
    }

    // -- live question ------------------------------------------------------

    @Test
    void questionEmbedTitleIncludesRoundProgressAndCategory() {
        TriviaRound round = roundFor(sampleMultiple(),
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"),
                1, 2, 5, Set.of(99L, 100L), new HashMap<>());

        MessageEmbed embed = TriviaEmbedBuilder.question(round, 1_500_000L);
        assertEquals("Trivia · Round 2 of 5 · Geography", embed.getTitle());
        assertEquals(TriviaEmbedBuilder.COLOR_LIVE, embed.getColorRaw());

        String choices = fieldValue(embed, "Choices");
        assertTrue(choices.contains("**A.** Toronto"), choices);
        assertTrue(choices.contains("**B.** Ottawa"), choices);

        // Player roster surfaces in the live embed too so spectators know
        // who's playing.
        String roster = fieldValue(embed, "Players (2)");
        assertTrue(roster.contains("<@99>"));
        assertTrue(roster.contains("<@100>"));

        String footer = embed.getFooter().getText();
        assertTrue(footer.contains("Easy"), footer);
        assertTrue(footer.contains("<t:1500000:R>"),
                "answer-window deadline should render as a relative timestamp");
    }

    @Test
    void questionEmbedDoesNotLeakCorrectAnswer() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "hard", "Cat",
                "Q?", "RIGHT",
                List.of("WRONG1", "WRONG2", "WRONG3"));
        TriviaRound round = roundFor(q,
                List.of("WRONG1", "RIGHT", "WRONG2", "WRONG3"),
                1, 1, 5, Set.of(99L), new HashMap<>());

        String choices = fieldValue(TriviaEmbedBuilder.question(round, 1L), "Choices");
        long boldCount = choices.chars().filter(c -> c == '*').count();
        assertEquals(16L, boldCount,
                "all four choices should render with identical bold-letter prefix");
        assertTrue(!choices.contains("✅") && !choices.contains("✓"));
    }

    // -- round result ------------------------------------------------------

    @Test
    void roundResultGroupsCorrectAnswerersAndShowsWrongPicks() {
        var answers = new LinkedHashMap<Long, Integer>();
        answers.put(99L, 1);   // correct
        answers.put(100L, 1);  // correct
        answers.put(200L, 0);  // wrong: chose Toronto
        // 300L joined but didn't answer
        TriviaRound round = roundFor(sampleMultiple(),
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"),
                1, 1, 5,
                new java.util.LinkedHashSet<>(List.of(99L, 100L, 200L, 300L)),
                answers);

        MessageEmbed embed = TriviaEmbedBuilder.roundResult(round);
        assertEquals(TriviaEmbedBuilder.COLOR_RESULT, embed.getColorRaw());
        assertTrue(fieldValue(embed, "Correct answer").contains("Ottawa"));
        String results = fieldValue(embed, "Results");
        assertTrue(results.contains("✅"), results);
        assertTrue(results.contains("<@99>") && results.contains("<@100>"),
                "both correct answerers should appear on the ✅ line");
        assertTrue(results.contains("❌ <@200>") && results.contains("Toronto"),
                "wrong answerer's pick should be revealed: " + results);
        assertTrue(results.contains("⏰ <@300>"),
                "non-answerer should be flagged with ⏰: " + results);
    }

    @Test
    void roundResultGoesGreyWhenNobodyAnswersCorrectly() {
        var answers = new LinkedHashMap<Long, Integer>();
        answers.put(99L, 0);  // wrong (correct is index 1)
        TriviaRound round = roundFor(sampleMultiple(),
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"),
                1, 1, 5, Set.of(99L), answers);

        MessageEmbed embed = TriviaEmbedBuilder.roundResult(round);
        assertEquals(TriviaEmbedBuilder.COLOR_TIMEOUT, embed.getColorRaw());
    }

    // -- final leaderboard --------------------------------------------------

    private static TriviaGame gameWithScores(int totalRounds, long... wins) {
        var qs = new java.util.ArrayList<TriviaQuestion>();
        for (int i = 0; i < totalRounds; i++) qs.add(sampleMultiple());
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), qs, 30, 20);
        // Ensure every winner is also a joined player.
        for (long userId : wins) game.addPlayer(userId);
        for (long userId : wins) game.recordWin(userId);
        return game;
    }

    @Test
    void leaderboardSortsDescendingAndUsesMentions() {
        TriviaGame game = gameWithScores(5, 100L, 100L, 100L, 200L, 300L, 200L);
        MessageEmbed embed = TriviaEmbedBuilder.gameOver(game);
        assertEquals(TriviaEmbedBuilder.COLOR_FINAL, embed.getColorRaw());
        String board = fieldValue(embed, "Leaderboard");
        int p100 = board.indexOf("<@100>");
        int p200 = board.indexOf("<@200>");
        int p300 = board.indexOf("<@300>");
        assertTrue(p100 < p200 && p200 < p300, "got: " + board);
        assertTrue(board.contains("<@100> — 3"));
        assertTrue(board.contains("<@200> — 2"));
        assertTrue(board.contains("<@300> — 1"));
        // Initiator (99) is also joined but didn't win → bottom with 0.
        assertTrue(board.contains("<@99> — 0"),
                "joined-but-zero players should still appear: " + board);
    }

    @Test
    void leaderboardSharesRankOnTies() {
        TriviaGame game = gameWithScores(5, 100L, 200L, 300L, 100L, 200L);
        // 100→2, 200→2, 300→1, 99→0
        String board = fieldValue(TriviaEmbedBuilder.gameOver(game), "Leaderboard");
        assertTrue(board.contains("**1.** <@100>"), board);
        assertTrue(board.contains("**1.** <@200>"), board);
        assertTrue(board.contains("**3.** <@300>"), board);
        assertTrue(board.contains("**4.** <@99>"), board);
    }

    @Test
    void allZeroScoresShowFriendlyMessageAndPlayerList() {
        TriviaGame game = freshGame(5);
        game.addPlayer(100L);
        MessageEmbed embed = TriviaEmbedBuilder.gameOver(game);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().toLowerCase().contains("nobody scored"),
                () -> embed.getDescription());
        // Players still listed so they know they're accounted for.
        String roster = fieldValue(embed, "Players");
        assertTrue(roster.contains("<@99>") && roster.contains("<@100>"));
    }

    @Test
    void footerCarriesFilterSummaryWhenSet() {
        var qs = new java.util.ArrayList<TriviaQuestion>();
        for (int i = 0; i < 5; i++) qs.add(sampleMultiple());
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                new TriviaFilter(15, "hard"), qs, 30, 20);
        game.recordWin(99L);
        String footer = TriviaEmbedBuilder.gameOver(game).getFooter().getText();
        assertTrue(footer.contains("Hard"), footer);
        assertTrue(footer.contains("Video Games"), footer);
    }
}
