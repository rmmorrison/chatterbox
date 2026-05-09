package ca.ryanmorrison.chatterbox.features.trivia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaRoundsTest {

    private ScheduledExecutorService scheduler;
    private TriviaRounds rounds;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(1);
        rounds = new TriviaRounds(scheduler);
    }

    @AfterEach
    void tearDown() {
        rounds.stop();
    }

    private static TriviaQuestion sampleMultiple() {
        return new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE,
                "easy",
                "Geography",
                "What is the capital of Canada?",
                "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
    }

    private static TriviaRound buildRound(String roundId, TriviaQuestion q, int correctIdx, List<String> labels) {
        return new TriviaRound(roundId, 100L, 200L, q, labels, correctIdx, Collections.emptySet());
    }

    @Test
    void shuffleMultipleHasAllAnswersAndCorrectIndex() {
        TriviaQuestion q = sampleMultiple();
        // Deterministic shuffle for the assertion.
        TriviaRounds.Shuffled s = TriviaRounds.shuffleWith(q, new Random(42));
        assertEquals(4, s.labels().size());
        assertTrue(s.labels().contains("Ottawa"));
        assertEquals("Ottawa", s.labels().get(s.correctIndex()));
    }

    @Test
    void shuffleBooleanIsAlwaysTrueFalseInOrder() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.BOOLEAN, "easy", "Cat",
                "Sky is blue.", "True", List.of("False"));
        TriviaRounds.Shuffled s = TriviaRounds.shuffle(q);
        assertEquals(List.of("True", "False"), s.labels());
        assertEquals(0, s.correctIndex());

        TriviaQuestion qFalse = new TriviaQuestion(
                TriviaQuestion.Type.BOOLEAN, "easy", "Cat",
                "Up is down.", "False", List.of("True"));
        assertEquals(1, TriviaRounds.shuffle(qFalse).correctIndex());
    }

    @Test
    void firstCorrectAnswerWins() {
        TriviaRound round = buildRound("r1", sampleMultiple(), 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));
        rounds.register(round, 60, () -> { /* won't fire in this test */ });

        // Wrong guess from user A.
        TriviaRounds.Result wrong = rounds.attempt("r1", 1L, 1);
        assertEquals(TriviaRounds.Outcome.WRONG, wrong.outcome());

        // Correct guess from user B.
        TriviaRounds.Result correct = rounds.attempt("r1", 2L, 0);
        assertEquals(TriviaRounds.Outcome.CORRECT_FIRST, correct.outcome());
        assertEquals("Ottawa", correct.round().orElseThrow().correctAnswerLabel());

        // Round is gone — subsequent clicks see NOT_FOUND.
        TriviaRounds.Result late = rounds.attempt("r1", 3L, 0);
        assertEquals(TriviaRounds.Outcome.NOT_FOUND, late.outcome());
    }

    @Test
    void wrongAnswerLocksOutThatUser() {
        TriviaRound round = buildRound("r1", sampleMultiple(), 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));
        rounds.register(round, 60, () -> {});

        assertEquals(TriviaRounds.Outcome.WRONG, rounds.attempt("r1", 1L, 1).outcome());
        // Same user tries again — even with the correct answer — they're locked out.
        assertEquals(TriviaRounds.Outcome.ALREADY_ANSWERED,
                rounds.attempt("r1", 1L, 0).outcome());
        // Round is still live; another user can still play.
        assertEquals(TriviaRounds.Outcome.CORRECT_FIRST,
                rounds.attempt("r1", 2L, 0).outcome());
    }

    @Test
    void unknownRoundIdReturnsNotFound() {
        assertEquals(TriviaRounds.Outcome.NOT_FOUND,
                rounds.attempt("nope", 1L, 0).outcome());
    }

    @Test
    void timeoutRunnableFiresAndRemovesRound() throws Exception {
        TriviaRound round = buildRound("r1", sampleMultiple(), 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));

        CountDownLatch latch = new CountDownLatch(1);
        // Schedule with effectively-zero delay so the test doesn't sit around.
        // ScheduledExecutorService accepts 0 as "as-soon-as-possible".
        rounds.register(round, 0, latch::countDown);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "timeout runnable should fire");
        // Round should have been removed atomically before the runnable ran,
        // so subsequent clicks are NOT_FOUND.
        assertEquals(TriviaRounds.Outcome.NOT_FOUND,
                rounds.attempt("r1", 1L, 0).outcome());
    }

    @Test
    void winningClickCancelsTimeout() throws Exception {
        TriviaRound round = buildRound("r1", sampleMultiple(), 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));

        AtomicInteger timeoutFires = new AtomicInteger();
        rounds.register(round, 1 /* second */, timeoutFires::incrementAndGet);

        // Win immediately.
        assertEquals(TriviaRounds.Outcome.CORRECT_FIRST,
                rounds.attempt("r1", 1L, 0).outcome());

        // Wait past the timeout deadline. The timeout runnable must NOT fire,
        // because the winning click cancelled it.
        Thread.sleep(1500);
        assertEquals(0, timeoutFires.get(),
                "timeout should be cancelled when a player wins first");
    }

    @Test
    void roundIdsAreEightCharsAndSomewhatRandom() {
        String a = rounds.newRoundId();
        String b = rounds.newRoundId();
        assertEquals(8, a.length());
        assertEquals(8, b.length());
        assertNotEquals(a, b, "ids should differ between calls (statistically)");
    }

    @Test
    void concurrentClicksProduceExactlyOneWinner() throws Exception {
        TriviaRound round = buildRound("r1", sampleMultiple(), 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));
        rounds.register(round, 60, () -> {});

        int contestants = 20;
        ExecutorService pool = Executors.newFixedThreadPool(contestants);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        Future<?>[] futures = new Future<?>[contestants];

        for (int i = 0; i < contestants; i++) {
            final long userId = 1000L + i;
            futures[i] = pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (rounds.attempt("r1", userId, 0).outcome()
                        == TriviaRounds.Outcome.CORRECT_FIRST) {
                    winners.incrementAndGet();
                }
            });
        }
        start.countDown();
        for (Future<?> f : futures) f.get(2, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, winners.get(),
                "exactly one concurrent click should win — atomic CAS contract");
    }
}
