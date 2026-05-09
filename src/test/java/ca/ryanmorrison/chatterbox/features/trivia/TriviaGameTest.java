package ca.ryanmorrison.chatterbox.features.trivia;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaGameTest {

    @Test
    void freshGameStartsAtRoundZeroAndAdvanceMonotonically() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L, TriviaFilter.any(), 3, null);
        assertEquals(0, game.currentRoundNumber());
        assertFalse(game.isFinished());
        assertEquals(1, game.advance());
        assertEquals(2, game.advance());
        assertFalse(game.isFinished());
        assertEquals(3, game.advance());
        assertTrue(game.isFinished(), "after totalRounds advances the game should be finished");
    }

    @Test
    void recordWinAccumulatesPerUser() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L, TriviaFilter.any(), 5, null);
        game.recordWin(100L);
        game.recordWin(100L);
        game.recordWin(200L);

        List<Map.Entry<Long, Integer>> board = game.leaderboard();
        assertEquals(2, board.size());
        // Sorted descending by score.
        assertEquals(100L, board.get(0).getKey());
        assertEquals(2, board.get(0).getValue());
        assertEquals(200L, board.get(1).getKey());
        assertEquals(1, board.get(1).getValue());
    }

    @Test
    void leaderboardEmptyForNoWins() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L, TriviaFilter.any(), 5, null);
        assertTrue(game.leaderboard().isEmpty());
    }
}
