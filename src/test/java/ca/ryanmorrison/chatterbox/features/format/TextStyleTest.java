package ca.ryanmorrison.chatterbox.features.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void clapExpansionIsTruncatedToDiscordsLimit() {
        // clap replaces each whitespace run with " \uD83D\uDC4F " -- 4 UTF-16 units --
        // so a 1500-char input of single-character words expands well past 2000.
        String input = ("a ".repeat(750)).trim();
        String expanded = TextStyle.CLAP.apply(input);
        assertTrue(expanded.length() > FormatModule.MAX_MESSAGE_LENGTH,
                "precondition: the style must actually overflow");

        String out = FormatModule.truncate(expanded, FormatModule.MAX_MESSAGE_LENGTH);

        assertTrue(out.length() <= FormatModule.MAX_MESSAGE_LENGTH,
                () -> "truncated to " + out.length() + " chars");
        assertFalse(Character.isHighSurrogate(out.charAt(out.length() - 2)),
                "a lone high surrogate would render as a replacement char");
    }

    @Test
    void truncateLeavesShortStringsAlone() {
        assertEquals("hello", FormatModule.truncate("hello", FormatModule.MAX_MESSAGE_LENGTH));
    }
}
