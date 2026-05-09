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

    private static TriviaRound buildRound(String roundId, String gameId,
                                          int roundNumber, int totalRounds,
                                          int correctIdx, List<String> labels) {
        return new TriviaRound(roundId, gameId, roundNumber, totalRounds,
                100L, 200L, sampleMultiple(), labels, correctIdx, Collections.emptySet());
    }

    @Test
    void shuffleMultipleHasAllAnswersAndCorrectIndex() {
        TriviaQuestion q = sampleMultiple();
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
        TriviaRound round = buildRound("r1", "g1", 1, 5, 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));
        rounds.register(round, 60, () -> { /* won't fire */ });

        assertEquals(TriviaRounds.Outcome.WRONG, rounds.attempt("r1", 1L, 1).outcome());

        TriviaRounds.Result correct = rounds.attempt("r1", 2L, 0);
        assertEquals(TriviaRounds.Outcome.CORRECT_FIRST, correct.outcome());
        assertEquals("Ottawa", correct.round().orElseThrow().correctAnswerLabel());

        assertEquals(TriviaRounds.Outcome.NOT_FOUND,
                rounds.attempt("r1", 3L, 0).outcome());
    }

    @Test
    void wrongAnswerLocksOutThatUser() {
        TriviaRound round = buildRound("r1", "g1", 1, 5, 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));
        rounds.register(round, 60, () -> {});

        assertEquals(TriviaRounds.Outcome.WRONG, rounds.attempt("r1", 1L, 1).outcome());
        assertEquals(TriviaRounds.Outcome.ALREADY_ANSWERED,
                rounds.attempt("r1", 1L, 0).outcome());
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
        TriviaRound round = buildRound("r1", "g1", 1, 5, 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));

        CountDownLatch latch = new CountDownLatch(1);
        rounds.register(round, 0, latch::countDown);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "timeout runnable should fire");
        assertEquals(TriviaRounds.Outcome.NOT_FOUND,
                rounds.attempt("r1", 1L, 0).outcome());
    }

    @Test
    void winningClickCancelsTimeout() throws Exception {
        TriviaRound round = buildRound("r1", "g1", 1, 5, 0,
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));

        AtomicInteger timeoutFires = new AtomicInteger();
        rounds.register(round, 1, timeoutFires::incrementAndGet);

        assertEquals(TriviaRounds.Outcome.CORRECT_FIRST,
                rounds.attempt("r1", 1L, 0).outcome());

        Thread.sleep(1500);
        assertEquals(0, timeoutFires.get(),
                "timeout should be cancelled when a player wins first");
    }

    @Test
    void roundIdsAreEightCharsAndSomewhatRandom() {
        String a = rounds.newId();
        String b = rounds.newId();
        assertEquals(8, a.length());
        assertEquals(8, b.length());
        assertNotEquals(a, b);
    }

    @Test
    void concurrentClicksProduceExactlyOneWinner() throws Exception {
        TriviaRound round = buildRound("r1", "g1", 1, 5, 0,
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

    // -- game registry ------------------------------------------------------

    @Test
    void registerAndLookupGameById() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), 5, "tok");
        rounds.registerGame(game);
        assertEquals(game, rounds.game("g1").orElseThrow());
    }

    @Test
    void removeGameForgetsIt() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), 5, null);
        rounds.registerGame(game);
        rounds.removeGame("g1");
        assertTrue(rounds.game("g1").isEmpty());
    }
}
