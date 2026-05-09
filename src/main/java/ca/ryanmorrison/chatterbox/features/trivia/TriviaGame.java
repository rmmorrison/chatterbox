package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for a multi-round trivia session.
 *
 * <p>State that mutates between rounds — the running per-user score map
 * and {@code currentRoundNumber} — is stored here. Mutation is always
 * single-threaded per game because round resolution is serialised through
 * the per-round CAS in {@link TriviaRounds#attempt}.
 *
 * <p>{@code sessionToken} is opentdb's no-repeat token; null means we
 * couldn't get one and fetches will use the untoked endpoint. {@code initiator}
 * is just the user who ran {@code /trivia} (used for footer attribution).
 */
final class TriviaGame {

    private final String gameId;
    private final long channelId;
    private final long initiatorId;
    private final TriviaFilter filter;
    private final int totalRounds;
    private final String sessionToken;

    /** Insertion-ordered for stable display when scores are tied. */
    private final Map<Long, Integer> scores = new LinkedHashMap<>();

    private int currentRoundNumber;

    TriviaGame(String gameId, long channelId, long initiatorId,
               TriviaFilter filter, int totalRounds, String sessionToken) {
        this.gameId = gameId;
        this.channelId = channelId;
        this.initiatorId = initiatorId;
        this.filter = filter;
        this.totalRounds = totalRounds;
        this.sessionToken = sessionToken;
        this.currentRoundNumber = 0;
    }

    String gameId() { return gameId; }
    long channelId() { return channelId; }
    long initiatorId() { return initiatorId; }
    TriviaFilter filter() { return filter; }
    int totalRounds() { return totalRounds; }
    String sessionToken() { return sessionToken; }
    int currentRoundNumber() { return currentRoundNumber; }

    /** Advance to the next round and return the new 1-based round number. */
    int advance() {
        return ++currentRoundNumber;
    }

    /** Record one correct answer for {@code userId}. */
    void recordWin(long userId) {
        scores.merge(userId, 1, Integer::sum);
    }

    /** True once {@link #advance} has been called {@code totalRounds} times. */
    boolean isFinished() {
        return currentRoundNumber >= totalRounds;
    }

    /** Snapshot of the current scoreboard, sorted descending by score. */
    List<Map.Entry<Long, Integer>> leaderboard() {
        List<Map.Entry<Long, Integer>> list = new java.util.ArrayList<>(scores.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return Collections.unmodifiableList(list);
    }
}
