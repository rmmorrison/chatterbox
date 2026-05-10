package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the in-memory state for trivia: the per-channel "one game at a
 * time" lock, the active games map, the active rounds map, and the
 * timeout scheduler that closes lobby phases and answer windows.
 *
 * <p>Concurrency model:
 * <ul>
 *   <li><b>Per-channel session lock</b>: {@link #tryClaimChannel} uses
 *       {@code putIfAbsent} so two concurrent {@code /trivia} invocations
 *       in the same channel can't both create games.</li>
 *   <li><b>Round resolution</b>: {@link #consumeRound} atomically removes
 *       a round from the map. Both the answer-button path (when all
 *       joined players have answered) and the timeout path call
 *       {@code consumeRound}; only one wins, so the round is resolved
 *       exactly once.</li>
 *   <li><b>Per-answer state</b>: {@link #recordAnswer} uses
 *       {@link ConcurrentHashMap#compute} so concurrent button clicks
 *       don't race on the answers map.</li>
 * </ul>
 *
 * <p>IDs are short alphanumeric tokens so they fit comfortably in
 * Discord's 100-char {@code custom_id} limit alongside their prefix.
 */
final class TriviaRounds {

    private static final char[] ID_ALPHABET =
            "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int ID_LENGTH = 8;

    private final Map<String, TriviaRound> rounds = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final Map<String, TriviaGame> games = new ConcurrentHashMap<>();
    /** channelId → gameId; enforces "one game per channel". */
    private final Map<Long, String> channelClaims = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    TriviaRounds() {
        this(defaultScheduler());
    }

    /** Test seam — supply a controllable scheduler. */
    TriviaRounds(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private static ScheduledExecutorService defaultScheduler() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "trivia-timeout-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Generate a fresh id; collisions are astronomically unlikely. */
    String newId() {
        char[] out = new char[ID_LENGTH];
        for (int i = 0; i < ID_LENGTH; i++) {
            out[i] = ID_ALPHABET[ThreadLocalRandom.current().nextInt(ID_ALPHABET.length)];
        }
        return new String(out);
    }

    // -- per-channel session locking ---------------------------------------

    /**
     * Atomically claim {@code channelId} for {@code gameId}. Returns true
     * if the channel was free; false if another game already owns it.
     */
    boolean tryClaimChannel(long channelId, String gameId) {
        return channelClaims.putIfAbsent(channelId, gameId) == null;
    }

    /** Release the channel claim — call when the game ends or aborts. */
    void releaseChannel(long channelId, String gameId) {
        // remove(K, V) only removes if the value still matches.
        channelClaims.remove(channelId, gameId);
    }

    Optional<TriviaGame> activeGameInChannel(long channelId) {
        String gid = channelClaims.get(channelId);
        if (gid == null) return Optional.empty();
        return game(gid);
    }

    // -- game registry -----------------------------------------------------

    void registerGame(TriviaGame game) {
        games.put(game.gameId(), game);
    }

    Optional<TriviaGame> game(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    void removeGame(String gameId) {
        games.remove(gameId);
    }

    // -- timeout scheduling ------------------------------------------------

    /** Schedule a one-shot task; returns the future for cancellation. */
    ScheduledFuture<?> schedule(Runnable task, long delaySeconds) {
        return scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Register a freshly-built round and schedule its answer-window
     * timeout. When the timer fires the round is atomically removed and
     * passed to {@code onTimeout}; if a concurrent click already
     * resolved the round (via {@link #consumeRound}), the timer's
     * removal is a no-op and {@code onTimeout} is skipped.
     */
    void register(TriviaRound round, long timeoutSeconds,
                  java.util.function.Consumer<TriviaRound> onTimeout) {
        rounds.put(round.roundId(), round);
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            TriviaRound removed = rounds.remove(round.roundId());
            timeouts.remove(round.roundId());
            if (removed != null) {
                onTimeout.accept(removed);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        timeouts.put(round.roundId(), task);
    }

    Optional<TriviaRound> snapshot(String roundId) {
        return Optional.ofNullable(rounds.get(roundId));
    }

    /**
     * Atomically remove a round from the active set, cancelling its
     * timeout. Returns the removed round (for resolution work) or empty
     * if it was already consumed by a concurrent caller.
     *
     * <p>This is the synchronisation point shared by the all-answered
     * early-end path and the timeout path.
     */
    Optional<TriviaRound> consumeRound(String roundId) {
        TriviaRound[] removed = new TriviaRound[1];
        rounds.compute(roundId, (k, current) -> {
            if (current == null) return null;
            removed[0] = current;
            return null;
        });
        if (removed[0] != null) {
            ScheduledFuture<?> t = timeouts.remove(roundId);
            if (t != null) t.cancel(false);
        }
        return Optional.ofNullable(removed[0]);
    }

    /**
     * Record one player's answer.
     *
     * <p>Outcomes:
     * <ul>
     *   <li>{@link AttemptOutcome#NOT_FOUND} — round already resolved.</li>
     *   <li>{@link AttemptOutcome#NOT_JOINED} — clicker isn't a player in
     *       this session.</li>
     *   <li>{@link AttemptOutcome#ALREADY_ANSWERED} — that user already
     *       picked.</li>
     *   <li>{@link AttemptOutcome#RECORDED} — answer accepted; the
     *       round is still in flight.</li>
     *   <li>{@link AttemptOutcome#RECORDED_LAST} — answer accepted and
     *       it was the last outstanding answer; the caller should close
     *       the round (call {@link #consumeRound}).</li>
     * </ul>
     */
    AttemptResult recordAnswer(String roundId, long userId, int choiceIndex) {
        AttemptOutcome[] outcome = new AttemptOutcome[]{ AttemptOutcome.NOT_FOUND };
        TriviaRound[] snapshot = new TriviaRound[1];
        rounds.compute(roundId, (k, current) -> {
            if (current == null) {
                outcome[0] = AttemptOutcome.NOT_FOUND;
                return null;
            }
            if (!current.joinedPlayers().contains(userId)) {
                outcome[0] = AttemptOutcome.NOT_JOINED;
                snapshot[0] = current;
                return current;
            }
            if (current.answers().containsKey(userId)) {
                outcome[0] = AttemptOutcome.ALREADY_ANSWERED;
                snapshot[0] = current;
                return current;
            }
            TriviaRound next = current.withAnswer(userId, choiceIndex);
            snapshot[0] = next;
            outcome[0] = next.allJoinedAnswered()
                    ? AttemptOutcome.RECORDED_LAST
                    : AttemptOutcome.RECORDED;
            return next;
        });
        return new AttemptResult(outcome[0], Optional.ofNullable(snapshot[0]));
    }

    // -- shutdown ----------------------------------------------------------

    /** Stop the scheduler and forget all in-flight state. */
    void stop() {
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        rounds.clear();
        timeouts.clear();
        games.clear();
        channelClaims.clear();
    }

    // -- types -------------------------------------------------------------

    enum AttemptOutcome {
        NOT_FOUND, NOT_JOINED, ALREADY_ANSWERED, RECORDED, RECORDED_LAST
    }

    record AttemptResult(AttemptOutcome outcome, Optional<TriviaRound> round) {}

    // -- choice shuffling --------------------------------------------------

    /**
     * Build the display order for a question. Multiple-choice questions
     * are shuffled; true/false is forced to {@code [True, False]} for a
     * stable left-to-right reading order.
     */
    static Shuffled shuffle(TriviaQuestion question) {
        if (question.type() == TriviaQuestion.Type.BOOLEAN) {
            return new Shuffled(List.of("True", "False"),
                    "true".equalsIgnoreCase(question.correctAnswer()) ? 0 : 1);
        }
        var combined = new java.util.ArrayList<String>();
        combined.add(question.correctAnswer());
        combined.addAll(question.incorrectAnswers());
        Collections.shuffle(combined, ThreadLocalRandom.current());
        int idx = combined.indexOf(question.correctAnswer());
        return new Shuffled(List.copyOf(combined), idx);
    }

    record Shuffled(List<String> labels, int correctIndex) {}

    /** Visible for tests so they can substitute deterministic shuffles. */
    static Shuffled shuffleWith(TriviaQuestion question, java.util.Random rng) {
        if (question.type() == TriviaQuestion.Type.BOOLEAN) {
            return new Shuffled(List.of("True", "False"),
                    "true".equalsIgnoreCase(question.correctAnswer()) ? 0 : 1);
        }
        var combined = new java.util.ArrayList<String>();
        combined.add(question.correctAnswer());
        combined.addAll(question.incorrectAnswers());
        Collections.shuffle(combined, rng);
        int idx = combined.indexOf(question.correctAnswer());
        return new Shuffled(List.copyOf(combined), idx);
    }
}
