package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderer-only checks for {@link WhenHandler#formatReply}. The handler's
 * orchestration (zone resolution, repo lookup, parser dispatch) is covered
 * indirectly via {@link ZoneResolutionTest} and {@link TimeParserTest} —
 * the values produced by those plug straight into {@code formatReply}.
 */
class WhenHandlerTest {

    @Test
    void replyContainsDisplayZoneAndBothTimestampStyles() {
        Instant moment = Instant.parse("2026-12-25T17:00:00Z"); // 12pm in Toronto
        String msg = WhenHandler.formatReply(ZoneId.of("America/Toronto"), moment);

        long epoch = moment.getEpochSecond();
        assertTrue(msg.contains("America/Toronto"), msg);
        assertTrue(msg.contains("<t:" + epoch + ":F>"), msg);
        assertTrue(msg.contains("<t:" + epoch + ":R>"), msg);
    }

    @Test
    void wallClockLineRendersInDisplayZoneNotUtc() {
        // 17:00 UTC = 22:30 IST in Asia/Kolkata (UTC+5:30, no DST).
        Instant moment = Instant.parse("2026-05-08T17:00:00Z");
        String msg = WhenHandler.formatReply(ZoneId.of("Asia/Kolkata"), moment);
        assertTrue(msg.contains("**Friday, May 8, 2026 at 10:30 PM**"),
                () -> "expected Kolkata wall clock, got: " + msg);
    }

    /**
     * The exact output for the bug-fix scenario, end-to-end of the rendering
     * step. Caller-in-Toronto-with-stored-zone, in:Asia/Kolkata,
     * "tomorrow 12pm" at Friday 11pm EDT resolves (in {@link TimeParserTest})
     * to {@code 2026-05-09T16:00:00Z} (Saturday noon Toronto time).
     * The display zone is Kolkata; that instant in Kolkata is Saturday 21:30 IST.
     */
    @Test
    void bugFixSaturdayNoonTorontoRendersAsSaturday930PmInKolkata() {
        Instant moment = Instant.parse("2026-05-09T16:00:00Z");
        String msg = WhenHandler.formatReply(ZoneId.of("Asia/Kolkata"), moment);
        // Epoch second hand-verified: Instant.parse("2026-05-09T16:00:00Z").getEpochSecond() == 1778342400.
        assertEquals(
                "<t:1778342400:F> (<t:1778342400:R>) in Asia/Kolkata "
                        + "→ **Saturday, May 9, 2026 at 9:30 PM**",
                msg);
    }

    /**
     * Same instant, but rendered with the caller's own zone as display.
     * For a Toronto user with no in: option (or in:=Toronto), the wall-clock
     * literal ought to read "12:00 PM" — matching what they'd expect to see
     * when they meant "tomorrow at noon".
     */
    @Test
    void bugFixSaturdayNoonRendersAsNoonInToronto() {
        Instant moment = Instant.parse("2026-05-09T16:00:00Z");
        String msg = WhenHandler.formatReply(ZoneId.of("America/Toronto"), moment);
        assertTrue(msg.contains("**Saturday, May 9, 2026 at 12:00 PM**"),
                () -> "expected Toronto noon, got: " + msg);
    }

    @Test
    void wallClockUsesEnglishLocaleRegardlessOfDefault() {
        java.util.Locale prior = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);
            Instant moment = Instant.parse("2026-05-08T17:00:00Z");
            String msg = WhenHandler.formatReply(ZoneId.of("UTC"), moment);
            assertTrue(msg.contains("Friday"), () -> "expected English weekday, got: " + msg);
            assertTrue(msg.contains("May"),    () -> "expected English month, got: " + msg);
        } finally {
            java.util.Locale.setDefault(prior);
        }
    }

    @Test
    void singleLineLayout() {
        // Light formatting check so future cosmetic tweaks don't pass silently.
        // Epoch second 1 = 1970-01-01 00:00:01 UTC.
        String msg = WhenHandler.formatReply(ZoneId.of("UTC"), Instant.ofEpochSecond(1L));
        assertEquals(
                "<t:1:F> (<t:1:R>) in UTC → **Thursday, January 1, 1970 at 12:00 AM**",
                msg);
    }
}
