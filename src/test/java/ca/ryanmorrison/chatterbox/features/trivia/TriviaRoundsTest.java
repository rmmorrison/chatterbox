package ca.ryanmorrison.chatterbox.features.trivia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaRoundsTest {

    private ScheduledExecutorService scheduler;
    private TriviaRounds rounds;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
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

    private static TriviaRound buildRound(String roundId, Set<Long> joinedPlayers, int correctIdx) {
        return new TriviaRound(roundId, "g1", 1, 5, 100L, 200L,
                sampleMultiple(),
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"),
                correctIdx, joinedPlayers,
                Collections.unmodifiableMap(new HashMap<>()));
    }

    // -- per-channel session lock -----------------------------------------

    @Test
    void firstClaimWinsTheChannel() {
        assertTrue(rounds.tryClaimChannel(200L, "g1"));
        assertFalse(rounds.tryClaimChannel(200L, "g2"),
                "second concurrent /trivia in the same channel must be rejected");
    }

    @Test
    void releaseOnlyClearsMatchingClaim() {
        rounds.tryClaimChannel(200L, "g1");
        rounds.releaseChannel(200L, "g2"); // wrong gameId — should be a no-op
        assertFalse(rounds.tryClaimChannel(200L, "g3"),
                "claim should still be held by g1");
        rounds.releaseChannel(200L, "g1");
        assertTrue(rounds.tryClaimChannel(200L, "g4"),
                "channel should be free after the right release");
    }

    // -- shuffling ---------------------------------------------------------

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

    // -- recordAnswer state machine ---------------------------------------

    @Test
    void nonJoinedClickRejected() {
        TriviaRound round = buildRound("r1", Set.of(1L, 2L), 0);
        rounds.register(round, 60, () -> {});
        TriviaRounds.AttemptResult r = rounds.recordAnswer("r1", 99L, 0);
        assertEquals(TriviaRounds.AttemptOutcome.NOT_JOINED, r.outcome());
    }

    @Test
    void firstAnswerRecordsAndMarksRecorded() {
        TriviaRound round = buildRound("r1", Set.of(1L, 2L, 3L), 0);
        rounds.register(round, 60, () -> {});
        TriviaRounds.AttemptResult r = rounds.recordAnswer("r1", 1L, 1);
        assertEquals(TriviaRounds.AttemptOutcome.RECORDED, r.outcome());
        assertEquals(1, r.round().orElseThrow().answers().size());
    }

    @Test
    void duplicateAnswerSameUserIsRejected() {
        TriviaRound round = buildRound("r1", Set.of(1L, 2L, 3L), 0);
        rounds.register(round, 60, () -> {});
        rounds.recordAnswer("r1", 1L, 1);
        TriviaRounds.AttemptResult r = rounds.recordAnswer("r1", 1L, 0);
        assertEquals(TriviaRounds.AttemptOutcome.ALREADY_ANSWERED, r.outcome());
        // Original (wrong) answer is preserved.
        assertEquals(1, r.round().orElseThrow().answers().get(1L));
    }

    @Test
    void lastOutstandingAnswerSignalsRecordedLast() {
        TriviaRound round = buildRound("r1", Set.of(1L, 2L), 0);
        rounds.register(round, 60, () -> {});
        assertEquals(TriviaRounds.AttemptOutcome.RECORDED,
                rounds.recordAnswer("r1", 1L, 0).outcome());
        TriviaRounds.AttemptResult last = rounds.recordAnswer("r1", 2L, 1);
        assertEquals(TriviaRounds.AttemptOutcome.RECORDED_LAST, last.outcome(),
                "last joined player to answer should signal early-end");
    }

    @Test
    void recordedLastFromConcurrentClicksFiresExactlyOnce() throws Exception {
        TriviaRound round = buildRound("r1", Set.of(1L, 2L, 3L), 0);
        rounds.register(round, 60, () -> {});

        int contestants = 3;
        var pool = Executors.newFixedThreadPool(contestants);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger lastCount = new AtomicInteger();
        var futures = new java.util.concurrent.Future<?>[contestants];
        long[] users = {1L, 2L, 3L};
        for (int i = 0; i < contestants; i++) {
            final long userId = users[i];
            futures[i] = pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                if (rounds.recordAnswer("r1", userId, 0).outcome()
                        == TriviaRounds.AttemptOutcome.RECORDED_LAST) {
                    lastCount.incrementAndGet();
                }
            });
        }
        start.countDown();
        for (var f : futures) f.get(2, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, lastCount.get(),
                "exactly one click should be tagged as the round-closer");
    }

    @Test
    void unknownRoundIdReturnsNotFound() {
        assertEquals(TriviaRounds.AttemptOutcome.NOT_FOUND,
                rounds.recordAnswer("nope", 1L, 0).outcome());
    }

    // -- consumeRound (the resolution synchroniser) -----------------------

    @Test
    void consumeRoundReturnsRoundOnceThenEmpty() {
        TriviaRound round = buildRound("r1", Set.of(1L), 0);
        rounds.register(round, 60, () -> {});
        assertTrue(rounds.consumeRound("r1").isPresent());
        assertTrue(rounds.consumeRound("r1").isEmpty(),
                "second consume should see the round already gone");
    }

    @Test
    void consumeRoundCancelsTheTimeoutCallback() throws Exception {
        TriviaRound round = buildRound("r1", Set.of(1L), 0);
        AtomicInteger fires = new AtomicInteger();
        rounds.register(round, 1, fires::incrementAndGet);
        rounds.consumeRound("r1");
        Thread.sleep(1500);
        assertEquals(0, fires.get(),
                "timeout should not fire after consumeRound succeeded");
    }

    @Test
    void timeoutAndConsumeAreMutuallyExclusive() throws Exception {
        // If the timeout removes the round first, the runnable fires AND
        // consumeRound sees nothing. If consumeRound wins first, the timeout
        // removal returns null and the runnable doesn't fire. Either way,
        // exactly one resolution path runs.
        TriviaRound round = buildRound("r1", Set.of(1L), 0);
        AtomicInteger fires = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        rounds.register(round, 0, () -> { fires.incrementAndGet(); latch.countDown(); });
        // Race: try to consume just as the timer fires. Won't be reliable
        // every time but verifies the contract whichever side wins.
        boolean timedOut = latch.await(2, TimeUnit.SECONDS);
        var consumed = rounds.consumeRound("r1");
        if (timedOut) {
            assertEquals(1, fires.get());
            assertTrue(consumed.isEmpty());
        } else {
            assertTrue(consumed.isPresent());
            assertEquals(0, fires.get());
        }
    }

    // -- ids + game registry ----------------------------------------------

    @Test
    void idsAreEightCharsAndSomewhatRandom() {
        String a = rounds.newId();
        String b = rounds.newId();
        assertEquals(8, a.length());
        assertEquals(8, b.length());
        assertNotEquals(a, b);
    }

    @Test
    void registerAndLookupGameById() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), List.of(sampleMultiple()), 30, 20);
        rounds.registerGame(game);
        assertEquals(game, rounds.game("g1").orElseThrow());
    }

    @Test
    void activeGameInChannelLooksUpViaClaim() {
        TriviaGame game = new TriviaGame("g1", 200L, 99L,
                TriviaFilter.any(), List.of(sampleMultiple()), 30, 20);
        rounds.tryClaimChannel(200L, "g1");
        rounds.registerGame(game);
        assertEquals(game, rounds.activeGameInChannel(200L).orElseThrow());
        assertTrue(rounds.activeGameInChannel(999L).isEmpty());
    }
}
