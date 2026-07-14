package ca.ryanmorrison.chatterbox.features.trivia;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaGameTest {

    private static TriviaQuestion sampleQuestion() {
        return new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "easy", "Geography",
                "Capital of Canada?", "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
    }

    private static TriviaGame game(int totalRounds) {
        var qs = new java.util.ArrayList<TriviaQuestion>();
        for (int i = 0; i < totalRounds; i++) qs.add(sampleQuestion());
        return new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), null, qs, 30, 20);
    }

    @Test
    void initiatorIsAutoJoined() {
        TriviaGame g = game(5);
        assertEquals(1, g.joinedCount());
        assertTrue(g.isJoined(99L));
        assertEquals(TriviaGame.Phase.LOBBY, g.phase());
    }

    @Test
    void addPlayerOnlyDuringLobby() {
        TriviaGame g = game(5);
        assertTrue(g.addPlayer(100L));
        assertFalse(g.addPlayer(100L), "second add for same user should be no-op");
        assertEquals(2, g.joinedCount());
        g.transitionToPlaying();
        assertFalse(g.addPlayer(200L), "joining is closed once playing begins");
        assertFalse(g.isJoined(200L));
    }

    @Test
    void advanceMonotonicallyAndReportsLastRoundComplete() {
        TriviaGame g = game(3);
        assertEquals(0, g.currentRoundNumber());
        assertFalse(g.isLastRoundComplete());
        assertEquals(1, g.advance());
        assertEquals(2, g.advance());
        assertFalse(g.isLastRoundComplete());
        assertEquals(3, g.advance());
        assertTrue(g.isLastRoundComplete());
    }

    @Test
    void recordWinAccumulatesPerUser() {
        TriviaGame g = game(5);
        g.addPlayer(100L);
        g.addPlayer(200L);
        g.recordWin(100L);
        g.recordWin(100L);
        g.recordWin(200L);

        List<Map.Entry<Long, Integer>> board = g.leaderboard();
        // All joined players appear, sorted descending. Initiator (99) has 0.
        assertEquals(3, board.size());
        assertEquals(100L, board.get(0).getKey());
        assertEquals(2, board.get(0).getValue());
        assertEquals(200L, board.get(1).getKey());
        assertEquals(1, board.get(1).getValue());
        assertEquals(99L, board.get(2).getKey());
        assertEquals(0, board.get(2).getValue());
    }

    @Test
    void leaderboardListsAllJoinedEvenWithZeros() {
        TriviaGame g = game(5);
        g.addPlayer(100L);
        g.addPlayer(200L);
        // No wins recorded.
        List<Map.Entry<Long, Integer>> board = g.leaderboard();
        assertEquals(3, board.size(), "leaderboard should include all joined players");
        for (var e : board) {
            assertEquals(0, e.getValue());
        }
    }

    @Test
    void preLoadedQuestionsAreAccessibleByRoundNumber() {
        TriviaQuestion q1 = new TriviaQuestion(TriviaQuestion.Type.MULTIPLE, "easy", "Cat",
                "Q1?", "A", List.of("B", "C", "D"));
        TriviaQuestion q2 = new TriviaQuestion(TriviaQuestion.Type.BOOLEAN, "easy", "Cat",
                "Q2?", "True", List.of("False"));
        TriviaGame g = new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), null, List.of(q1, q2), 30, 20);

        assertEquals(2, g.totalRounds(), "totalRounds is the size of the pre-loaded list");
        assertEquals(q1, g.questionForRound(1));
        assertEquals(q2, g.questionForRound(2));
    }
}
