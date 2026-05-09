package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container for a multi-round trivia session in a single channel.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link Phase#LOBBY} — players opt in via the Join button. The
 *       initiator is auto-joined.</li>
 *   <li>{@link Phase#PLAYING} — rounds run sequentially; the joined-player
 *       set is frozen at the lobby's close.</li>
 *   <li>{@link Phase#FINISHED} — leaderboard posted; the game is removed
 *       from the per-channel map.</li>
 * </ol>
 *
 * <p>Mutation is single-threaded per game: the lobby is mutated only by
 * Join clicks (atomic via the joined-set), and round resolution is
 * serialised through the round-level CAS in {@link TriviaRounds}, so
 * scoring and round advancement never race.
 *
 * <p>{@code sessionToken} is opentdb's no-repeat token; null means we
 * couldn't get one and fetches will use the untoked endpoint.
 */
final class TriviaGame {

    private final String gameId;
    private final long channelId;
    private final long initiatorId;
    private final TriviaFilter filter;
    private final int totalRounds;
    private final int lobbySeconds;
    private final int roundSeconds;
    private final String sessionToken;

    /** Concurrent so Join clicks from any thread are race-safe. */
    private final Set<Long> joined = ConcurrentHashMap.newKeySet();

    /** Insertion-ordered for stable display when scores are tied. */
    private final Map<Long, Integer> scores = new LinkedHashMap<>();

    private volatile Phase phase = Phase.LOBBY;
    private volatile long lobbyMessageId = 0L;
    private int currentRoundNumber;

    TriviaGame(String gameId, long channelId, long initiatorId,
               TriviaFilter filter, int totalRounds,
               int lobbySeconds, int roundSeconds, String sessionToken) {
        this.gameId = gameId;
        this.channelId = channelId;
        this.initiatorId = initiatorId;
        this.filter = filter;
        this.totalRounds = totalRounds;
        this.lobbySeconds = lobbySeconds;
        this.roundSeconds = roundSeconds;
        this.sessionToken = sessionToken;
        this.currentRoundNumber = 0;
        // Initiator opts in by virtue of running the command.
        joined.add(initiatorId);
    }

    String gameId() { return gameId; }
    long channelId() { return channelId; }
    long initiatorId() { return initiatorId; }
    TriviaFilter filter() { return filter; }
    int totalRounds() { return totalRounds; }
    int lobbySeconds() { return lobbySeconds; }
    int roundSeconds() { return roundSeconds; }
    String sessionToken() { return sessionToken; }
    int currentRoundNumber() { return currentRoundNumber; }
    Phase phase() { return phase; }
    long lobbyMessageId() { return lobbyMessageId; }

    void setLobbyMessageId(long messageId) { this.lobbyMessageId = messageId; }

    void transitionToPlaying() { this.phase = Phase.PLAYING; }
    void markFinished() { this.phase = Phase.FINISHED; }

    /**
     * Add a player to the lobby. Returns true if they were newly added,
     * false if already in. Callers can use the boolean to decide whether
     * to refresh the lobby embed.
     */
    boolean addPlayer(long userId) {
        if (phase != Phase.LOBBY) return false;
        return joined.add(userId);
    }

    boolean isJoined(long userId) {
        return joined.contains(userId);
    }

    int joinedCount() {
        return joined.size();
    }

    /** Snapshot of joined players, in insertion order (initiator first). */
    Set<Long> joinedSnapshot() {
        return Set.copyOf(joined);
    }

    /** Advance to the next round and return the new 1-based round number. */
    int advance() {
        return ++currentRoundNumber;
    }

    /** Record one correct answer for {@code userId}. */
    void recordWin(long userId) {
        scores.merge(userId, 1, Integer::sum);
    }

    /** True once {@link #advance} has been called {@code totalRounds} times. */
    boolean isLastRoundComplete() {
        return currentRoundNumber >= totalRounds;
    }

    /** Snapshot of the current scoreboard, sorted descending by score. */
    List<Map.Entry<Long, Integer>> leaderboard() {
        List<Map.Entry<Long, Integer>> list = new java.util.ArrayList<>();
        // Include all joined players so a zero-scorer still appears at the
        // bottom — useful so people who actually played see they're
        // accounted for.
        for (Long userId : joined) {
            list.add(Map.entry(userId, scores.getOrDefault(userId, 0)));
        }
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return Collections.unmodifiableList(list);
    }

    enum Phase { LOBBY, PLAYING, FINISHED }
}
