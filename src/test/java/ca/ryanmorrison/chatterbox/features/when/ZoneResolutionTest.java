package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneResolutionTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId TOKYO   = ZoneId.of("Asia/Tokyo");

    @Test
    void storedWinsForInterpretationWhenBothSupplied() {
        // The bug fix: caller has stored Toronto, asks in:Kolkata.
        // "12pm" must be interpreted as Toronto noon, NOT Kolkata noon.
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.of(KOLKATA));
        assertEquals(Optional.of(TORONTO), r.interpretZone());
        assertEquals(KOLKATA, r.displayZone());
    }

    @Test
    void inWinsForDisplayWhenBothSupplied() {
        // The other half of the bug fix: in: changes only what the
        // wall-clock literal in the reply shows.
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.of(KOLKATA));
        assertEquals(KOLKATA, r.displayZone());
    }

    @Test
    void storedAlonePicksItForBoth() {
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.empty());
        assertEquals(Optional.of(TORONTO), r.interpretZone());
        assertEquals(TORONTO, r.displayZone());
    }

    @Test
    void inAlonePicksItForBoth() {
        // No /timezone set; in: drives both. (Pre-/timezone behaviour.)
        var r = ZoneResolution.resolve(Optional.empty(), Optional.of(KOLKATA));
        assertEquals(Optional.of(KOLKATA), r.interpretZone());
        assertEquals(KOLKATA, r.displayZone());
    }

    @Test
    void neitherLeavesInterpretationEmptyAndDisplayInUtc() {
        var r = ZoneResolution.resolve(Optional.empty(), Optional.empty());
        assertTrue(r.interpretZone().isEmpty());
        assertEquals(ZoneOffset.UTC, r.displayZone());
    }

    @Test
    void threeDistinctZonesNotInvolvedAreNotConfused() {
        // Sanity: the helper returns exactly the inputs in their roles, no shuffling.
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.of(TOKYO));
        assertEquals(Optional.of(TORONTO), r.interpretZone());
        assertEquals(TOKYO, r.displayZone());
    }
}
