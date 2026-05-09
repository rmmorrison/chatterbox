package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimezonesTest {

    @Test
    void emptyPrefixReturnsPopularList() {
        List<String> out = Timezones.suggest("");
        assertEquals(Timezones.POPULAR, out);
    }

    @Test
    void blankPrefixReturnsPopularList() {
        assertEquals(Timezones.POPULAR, Timezones.suggest("   "));
        assertEquals(Timezones.POPULAR, Timezones.suggest(null));
    }

    @Test
    void cityPrefixMatchesByCity() {
        // "tor" should surface America/Toronto without the user knowing the region.
        List<String> out = Timezones.suggest("tor");
        assertTrue(out.contains("America/Toronto"), () -> "expected Toronto, got " + out);
    }

    @Test
    void caseInsensitivePrefix() {
        List<String> upper = Timezones.suggest("TOKYO");
        List<String> lower = Timezones.suggest("tokyo");
        assertEquals(upper, lower);
        assertTrue(upper.contains("Asia/Tokyo"), () -> "expected Tokyo, got " + upper);
    }

    @Test
    void regionPrefixAlsoMatches() {
        List<String> out = Timezones.suggest("america");
        assertTrue(out.size() > 0);
        assertTrue(out.stream().allMatch(z -> z.toLowerCase().contains("america")),
                () -> "non-America zones leaked: " + out);
    }

    @Test
    void resultsRespectAutocompleteCap() {
        // "a" matches a lot of zones; ensure we never exceed Discord's cap.
        List<String> out = Timezones.suggest("a");
        assertTrue(out.size() <= Timezones.MAX_AUTOCOMPLETE_CHOICES,
                () -> "too many suggestions: " + out.size());
    }

    @Test
    void unknownPrefixReturnsEmpty() {
        assertTrue(Timezones.suggest("zzzzzz-not-a-place").isEmpty());
    }

    // ---- resolve ----

    @Test
    void resolvesIanaZones() {
        assertEquals(ZoneId.of("America/Toronto"),
                Timezones.resolve("America/Toronto").orElseThrow());
    }

    @Test
    void resolvesUtcAndZ() {
        // ZoneId preserves the input id ("UTC" vs "Z" stay distinct objects),
        // but both must represent the same zero-offset zone.
        assertEquals("UTC", Timezones.resolve("UTC").orElseThrow().getId());
        assertEquals("Z",   Timezones.resolve("Z").orElseThrow().getId());
        assertEquals(ZoneOffset.UTC.getRules(),
                Timezones.resolve("UTC").orElseThrow().getRules());
    }

    @Test
    void resolvesOffsets() {
        assertEquals(ZoneOffset.of("+05:30"),
                Timezones.resolve("+05:30").orElseThrow());
    }

    @Test
    void invalidZoneReturnsEmpty() {
        assertFalse(Timezones.resolve("Mars/Olympus").isPresent());
        assertFalse(Timezones.resolve("garbage").isPresent());
        assertFalse(Timezones.resolve("").isPresent());
        assertFalse(Timezones.resolve(null).isPresent());
    }

    @Test
    void resolveTrimsWhitespace() {
        assertEquals(ZoneId.of("America/Toronto"),
                Timezones.resolve("  America/Toronto  ").orElseThrow());
    }
}
