package ca.ryanmorrison.chatterbox.features.eightball;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EightBallAnswersTest {

    @Test
    void canonicalSplitMatchesThePhysicalToy() {
        // 10 affirmative, 5 non-committal, 5 negative — total 20.
        assertEquals(10, EightBallAnswers.AFFIRMATIVE.size());
        assertEquals(5, EightBallAnswers.NON_COMMITTAL.size());
        assertEquals(5, EightBallAnswers.NEGATIVE.size());
        assertEquals(20, EightBallAnswers.ALL.size());
    }

    @Test
    void allAnswersAreDistinct() {
        Set<String> deduped = new HashSet<>(EightBallAnswers.ALL);
        assertEquals(EightBallAnswers.ALL.size(), deduped.size(),
                "no duplicate answers in the bowl");
    }

    @Test
    void allAnswersAreNonBlankAndPunctuated() {
        for (String a : EightBallAnswers.ALL) {
            assertTrue(a != null && !a.isBlank(), "blank answer: " + a);
            char last = a.charAt(a.length() - 1);
            assertTrue(last == '.' || last == '?' || last == '!',
                    "answer should end with sentence punctuation: " + a);
        }
    }

    @Test
    void pickAlwaysReturnsAnAnswerFromTheBowl() {
        // Sample enough to exercise the random source meaningfully.
        for (int i = 0; i < 200; i++) {
            String picked = EightBallAnswers.pick();
            assertTrue(EightBallAnswers.ALL.contains(picked),
                    "pick returned something not in the bowl: " + picked);
        }
    }

    @Test
    void repeatedPicksHitMultipleAnswers() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) seen.add(EightBallAnswers.pick());
        // 500 samples from a 20-element pool — collisions for fewer than ~10 unique
        // would be vanishingly improbable even with a heavy bias.
        assertTrue(seen.size() >= 15,
                "expected most of the pool to be sampled across 500 draws, got " + seen.size());
    }
}
