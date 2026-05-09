package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneResolutionTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId TOKYO   = ZoneId.of("Asia/Tokyo");

    @Test
    void storedWinsForInterpretationWhenBothSupplied() {
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.of(KOLKATA));
        assertEquals(Optional.of(TORONTO), r.interpretZone());
    }

    @Test
    void inWinsForDisplayWhenBothSupplied() {
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.of(KOLKATA));
        assertEquals(Optional.of(KOLKATA), r.displayZone());
    }

    @Test
    void storedAlonePicksItForInterpretAndOmitsDisplay() {
        // No in: → no wall-clock literal in the reply (would be redundant for the caller).
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.empty());
        assertEquals(Optional.of(TORONTO), r.interpretZone());
        assertTrue(r.displayZone().isEmpty(),
                () -> "expected empty displayZone when no in:, got " + r.displayZone());
    }

    @Test
    void inAlonePicksItForBoth() {
        // No /timezone set; in: drives interpretation and display.
        var r = ZoneResolution.resolve(Optional.empty(), Optional.of(KOLKATA));
        assertEquals(Optional.of(KOLKATA), r.interpretZone());
        assertEquals(Optional.of(KOLKATA), r.displayZone());
    }

    @Test
    void neitherLeavesBothEmpty() {
        // No information at all — parser will reject calendar-relative inputs;
        // pure-relative inputs (now / in N) still produce just-timestamp output.
        var r = ZoneResolution.resolve(Optional.empty(), Optional.empty());
        assertTrue(r.interpretZone().isEmpty());
        assertTrue(r.displayZone().isEmpty());
    }

    @Test
    void threeDistinctZonesNotInvolvedAreNotConfused() {
        var r = ZoneResolution.resolve(Optional.of(TORONTO), Optional.of(TOKYO));
        assertEquals(Optional.of(TORONTO), r.interpretZone());
        assertEquals(Optional.of(TOKYO),   r.displayZone());
    }
}
