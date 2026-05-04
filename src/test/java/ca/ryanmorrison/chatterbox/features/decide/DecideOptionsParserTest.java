package ca.ryanmorrison.chatterbox.features.decide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecideOptionsParserTest {

    @Test
    void splitsOnWhitespaceWhenNoOrPresent() {
        assertEquals(List.of("pizza", "tacos", "sushi"),
                DecideOptionsParser.parse("pizza tacos sushi"));
    }

    @Test
    void splitsOnOrWhenPresent() {
        assertEquals(List.of("say hello", "be silent"),
                DecideOptionsParser.parse("say hello or be silent"));
    }

    @Test
    void orMatchIsCaseInsensitive() {
        assertEquals(List.of("yes", "no"),
                DecideOptionsParser.parse("yes OR no"));
        assertEquals(List.of("yes", "no"),
                DecideOptionsParser.parse("yes Or no"));
    }

    @Test
    void multipleOrSeparatedOptions() {
        assertEquals(List.of("rock", "paper", "scissors"),
                DecideOptionsParser.parse("rock or paper or scissors"));
    }

    @Test
    void orPreemptsWhitespaceWhenBothPresent() {
        // "say hello or be silent" — whitespace alone would give 5 tokens; the
        // " or " split is the user-friendly interpretation.
        List<String> parsed = DecideOptionsParser.parse("say hello or be silent");
        assertEquals(2, parsed.size());
    }

    @Test
    void trimsTrailingPunctuation() {
        // Common natural phrasing: "say hello, or be silent."
        assertEquals(List.of("say hello", "be silent"),
                DecideOptionsParser.parse("say hello, or be silent."));
    }

    @Test
    void trimsLeadingPunctuation() {
        assertEquals(List.of("hi", "bye"), DecideOptionsParser.parse(",hi or ,bye"));
    }

    @Test
    void collapsesRunsOfWhitespace() {
        assertEquals(List.of("a", "b", "c"),
                DecideOptionsParser.parse("a   b\tc"));
    }

    @Test
    void blankInputProducesNoOptions() {
        assertTrue(DecideOptionsParser.parse("").isEmpty());
        assertTrue(DecideOptionsParser.parse("   ").isEmpty());
        assertTrue(DecideOptionsParser.parse(null).isEmpty());
    }

    @Test
    void allPunctuationProducesNoOptions() {
        assertTrue(DecideOptionsParser.parse(",,,...!!!").isEmpty());
    }

    @Test
    void singleOptionPassesThrough() {
        assertEquals(List.of("yes"), DecideOptionsParser.parse("yes"));
    }

    @Test
    void wordContainingOrIsNotSplit() {
        // "morning" contains the substring "or" but the regex requires
        // whitespace boundaries on both sides.
        assertEquals(List.of("morning", "evening"),
                DecideOptionsParser.parse("morning evening"));
    }

    @Test
    void unboundedOrAtStartOrEndStaysWithItsToken() {
        // The separator regex requires whitespace on BOTH sides of "or", so a
        // leading or trailing "or" without surrounding whitespace is preserved
        // as part of the adjacent option rather than silently dropped.
        assertEquals(List.of("or a", "b"), DecideOptionsParser.parse("or a or b"));
        assertEquals(List.of("a", "b or"), DecideOptionsParser.parse("a or b or"));
    }

    @Test
    void rendersOptionsWithMultiwordChoicesIntact() {
        List<String> parsed = DecideOptionsParser.parse(
                "drive to the store or order delivery or skip the meal entirely");
        assertEquals(3, parsed.size());
        assertEquals("drive to the store", parsed.get(0));
        assertEquals("order delivery", parsed.get(1));
        assertEquals("skip the meal entirely", parsed.get(2));
    }
}
