package ca.ryanmorrison.chatterbox.features.eightball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slash-handler glue is exercised on staging; this covers the pure renderer. */
class EightBallModuleTest {

    @Test
    void rendersQuestionAndAnswer() {
        String body = EightBallModule.renderResult("will it rain?", "Yes definitely.");
        assertTrue(body.contains("🎱"));
        assertTrue(body.contains("\"will it rain?\""));
        assertTrue(body.contains("**Yes definitely.**"));
    }

    @Test
    void truncatesOverlongQuestions() {
        String huge = "x".repeat(EightBallModule.MAX_QUESTION_LENGTH + 50);
        String body = EightBallModule.renderResult(huge, "Yes.");
        assertTrue(body.contains("…"), "should ellipsise an overlong question");
        assertFalse(body.length() > 2000,
                "rendered message must remain under Discord's 2000 char cap");
    }

    @Test
    void preservesShortQuestionsExactly() {
        String body = EightBallModule.renderResult("hello?", "Most likely.");
        assertTrue(body.contains("\"hello?\""));
        assertFalse(body.contains("…"));
    }
}
