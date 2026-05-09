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
 * Owns the in-memory map of in-flight trivia games and rounds, plus the
 * timeout scheduler that auto-resolves abandoned rounds.
 *
 * <p>Concurrency model: every round-state transition goes through
 * {@link ConcurrentHashMap#compute}, which is atomic per key. That gives
 * us "first correct click wins" without explicit locking — when a round
 * is resolved (correct answer or timeout) it is removed from the map
 * atomically; any subsequent click on the same buttons sees
 * {@link Outcome#NOT_FOUND}. Game-level state is mutated only by the
 * single thread that resolved the round, so no further locking is needed.
 *
 * <p>Round and game IDs are short alphanumeric tokens (8 chars) so they
 * fit comfortably under Discord's 100-char {@code custom_id} limit.
 */
final class TriviaRounds {

    private static final char[] ID_ALPHABET =
            "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int ID_LENGTH = 8;

    private final Map<String, TriviaRound> rounds = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final Map<String, TriviaGame> games = new ConcurrentHashMap<>();
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
        return Executors.newScheduledThreadPool(1, r -> {
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

    void registerGame(TriviaGame game) {
        games.put(game.gameId(), game);
    }

    Optional<TriviaGame> game(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    void removeGame(String gameId) {
        games.remove(gameId);
    }

    /**
     * Register a freshly-built round and schedule its timeout.
     * {@code onTimeout} runs only if the round is still present at
     * deadline (i.e. nobody won first).
     */
    void register(TriviaRound round, long timeoutSeconds, Runnable onTimeout) {
        rounds.put(round.roundId(), round);
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            TriviaRound removed = rounds.remove(round.roundId());
            timeouts.remove(round.roundId());
            if (removed != null) {
                onTimeout.run();
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        timeouts.put(round.roundId(), task);
    }

    Optional<TriviaRound> snapshot(String roundId) {
        return Optional.ofNullable(rounds.get(roundId));
    }

    /**
     * Atomic answer attempt.
     *
     * <p>Returns the outcome plus a snapshot of the round (taken inside
     * the compute lambda). For {@link Outcome#CORRECT_FIRST} the round
     * has been removed from the map; for {@link Outcome#WRONG} the
     * snapshot already includes the new wrong-answerer.
     */
    Result attempt(String roundId, long userId, int choiceIndex) {
        Outcome[] outcome = new Outcome[]{ Outcome.NOT_FOUND };
        TriviaRound[] snapshot = new TriviaRound[1];
        rounds.compute(roundId, (k, current) -> {
            if (current == null) {
                outcome[0] = Outcome.NOT_FOUND;
                return null;
            }
            if (current.wrongAnswerers().contains(userId)) {
                outcome[0] = Outcome.ALREADY_ANSWERED;
                snapshot[0] = current;
                return current;
            }
            if (choiceIndex == current.correctIndex()) {
                outcome[0] = Outcome.CORRECT_FIRST;
                snapshot[0] = current;
                return null; // remove — round is over.
            }
            outcome[0] = Outcome.WRONG;
            TriviaRound next = current.withWrongAnswerer(userId);
            snapshot[0] = next;
            return next;
        });
        if (outcome[0] == Outcome.CORRECT_FIRST) {
            ScheduledFuture<?> t = timeouts.remove(roundId);
            if (t != null) t.cancel(false);
        }
        return new Result(outcome[0], Optional.ofNullable(snapshot[0]));
    }

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
    }

    enum Outcome { CORRECT_FIRST, WRONG, ALREADY_ANSWERED, NOT_FOUND }

    record Result(Outcome outcome, Optional<TriviaRound> round) {}

    // -- choice shuffling ---------------------------------------------------

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
