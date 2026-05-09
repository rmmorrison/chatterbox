package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhenHandlerTest {

    @Test
    void replyContainsZoneAndBothTimestampStyles() {
        Instant moment = Instant.parse("2026-12-25T17:00:00Z"); // 12pm in Toronto
        String msg = WhenHandler.formatReply(ZoneId.of("America/Toronto"), moment);

        long epoch = moment.getEpochSecond();
        assertTrue(msg.contains("America/Toronto"), msg);
        assertTrue(msg.contains("<t:" + epoch + ":F>"), msg);
        assertTrue(msg.contains("<t:" + epoch + ":R>"), msg);
    }

    @Test
    void wallClockLineRendersInRequestedZoneNotUtc() {
        // 17:00 UTC = 22:30 IST in Asia/Kolkata (UTC+5:30, no DST). The literal
        // line must show the Kolkata wall clock, not the input UTC instant.
        Instant moment = Instant.parse("2026-05-08T17:00:00Z");
        String msg = WhenHandler.formatReply(ZoneId.of("Asia/Kolkata"), moment);
        assertTrue(msg.contains("**Friday, May 8, 2026 at 10:30 PM**"),
                () -> "expected Kolkata wall clock, got: " + msg);
    }

    @Test
    void wallClockLineCrossesDateBoundaryWhenZoneIsAhead() {
        // 02:00 UTC May 9 ≈ 22:00 EDT May 8 ≈ 07:30 IST May 9. The literal
        // line must show "May 9" (Kolkata's day), not "May 8".
        Instant moment = Instant.parse("2026-05-09T02:00:00Z");
        String msg = WhenHandler.formatReply(ZoneId.of("Asia/Kolkata"), moment);
        assertTrue(msg.contains("Saturday, May 9, 2026 at 7:30 AM"),
                () -> "expected May 9 in Kolkata, got: " + msg);
    }

    @Test
    void wallClockUsesEnglishLocaleRegardlessOfDefault() {
        // Sanity check that month/weekday words are English even if the JVM
        // default locale is something else. Pin and restore.
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
    void timestampUsesUnixSeconds() {
        Instant moment = Instant.ofEpochSecond(1_800_000_000L);
        String msg = WhenHandler.formatReply(ZoneId.of("UTC"), moment);
        assertTrue(msg.contains("<t:1800000000:F>"), msg);
        assertTrue(msg.contains("<t:1800000000:R>"), msg);
    }

    @Test
    void zoneIdIsCanonicalNotPretty() {
        // We use ZoneId.getId() so users can copy-paste back into /when in:.
        String msg = WhenHandler.formatReply(ZoneId.of("Europe/London"),
                Instant.parse("2026-06-01T08:00:00Z"));
        assertTrue(msg.contains("Europe/London"), msg);
    }

    @Test
    void singleLineLayout() {
        // Light formatting check so future cosmetic tweaks don't pass silently.
        // Epoch second 1 = 1970-01-01 00:00:01 UTC. Single-line output keeps
        // the viewer-time-then-zone-time order so it reads as a sentence.
        String msg = WhenHandler.formatReply(ZoneId.of("UTC"), Instant.ofEpochSecond(1L));
        assertEquals(
                "<t:1:F> (<t:1:R>) in UTC → **Thursday, January 1, 1970 at 12:00 AM**",
                msg);
    }
}
