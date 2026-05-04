package ca.ryanmorrison.chatterbox.features.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextStyleTest {

    // -- clap ---------------------------------------------------------------

    @Test
    void clapInsertsClapsBetweenWords() {
        assertEquals("make 👏 it 👏 stop", TextStyle.clap("make it stop"));
    }

    @Test
    void clapHandlesSingleWord() {
        assertEquals("hello", TextStyle.clap("hello"));
    }

    @Test
    void clapCollapsesRunsOfWhitespace() {
        assertEquals("a 👏 b 👏 c", TextStyle.clap("a   b\tc"));
    }

    @Test
    void clapTrimsEdges() {
        assertEquals("hello 👏 world", TextStyle.clap("   hello world   "));
    }

    @Test
    void clapHandlesEmptyAndWhitespaceOnly() {
        assertEquals("", TextStyle.clap(""));
        assertEquals("", TextStyle.clap("   "));
        assertEquals("", TextStyle.clap(null));
    }

    @Test
    void clapPreservesPunctuationAttachedToWords() {
        assertEquals("hello, 👏 world!", TextStyle.clap("hello, world!"));
    }

    // -- spongecase ---------------------------------------------------------

    @Test
    void spongecaseMatchesWikipediaExample() {
        assertEquals("aLtErNaTiNg CaPs", TextStyle.spongecase("alternating caps"));
    }

    @Test
    void spongecaseStartsLowercase() {
        assertEquals("hElLo", TextStyle.spongecase("hello"));
        assertEquals("hElLo", TextStyle.spongecase("HELLO"));
    }

    @Test
    void spongecaseAdvancesOnlyOnLetters() {
        // Non-letters don't bump the alternation cursor, so "ab cd" reads
        // a-LOWER, b-UPPER, [space], c-LOWER, d-UPPER.
        assertEquals("aB cD", TextStyle.spongecase("ab cd"));
    }

    @Test
    void spongecasePreservesDigitsAndPunctuation() {
        assertEquals("h1 W0rLd!", TextStyle.spongecase("H1 w0RlD!"));
    }

    @Test
    void spongecaseHandlesEmpty() {
        assertEquals("", TextStyle.spongecase(""));
        assertEquals("", TextStyle.spongecase(null));
    }

    @Test
    void spongecasePassesEmojiThroughUnchanged() {
        // Emoji aren't letters; they pass through and don't disturb the
        // alternation. Letters [h, e, l, l, o] cycle lower/upper from index 0,
        // yielding h(lower), 👋(skip), E(upper), l(lower), L(upper), o(lower).
        assertEquals("h👋ElLo", TextStyle.spongecase("h👋ello"));
    }

    // -- enum bookkeeping ---------------------------------------------------

    @Test
    void fromValueLooksUpKnownStyles() {
        assertEquals(TextStyle.CLAP,       TextStyle.fromValue("clap"));
        assertEquals(TextStyle.SPONGECASE, TextStyle.fromValue("spongecase"));
    }

    @Test
    void fromValueRejectsUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> TextStyle.fromValue("bogus"));
    }

    @Test
    void everyStyleHasNonBlankLabel() {
        for (TextStyle s : TextStyle.values()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    s.label() != null && !s.label().isBlank(),
                    "style " + s.name() + " missing a label");
            org.junit.jupiter.api.Assertions.assertTrue(
                    s.value() != null && !s.value().isBlank(),
                    "style " + s.name() + " missing a value");
        }
    }
}
