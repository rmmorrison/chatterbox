package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhenHandlerTest {

    @Test
    void replyEchoesInputAndZoneAndIncludesBothTimestampStyles() {
        Instant moment = Instant.parse("2026-12-25T17:00:00Z"); // 12pm in Toronto
        String msg = WhenHandler.formatReply("12pm", ZoneId.of("America/Toronto"), moment);

        long epoch = moment.getEpochSecond();
        assertTrue(msg.contains("\"12pm\""), msg);
        assertTrue(msg.contains("America/Toronto"), msg);
        assertTrue(msg.contains("<t:" + epoch + ":F>"), msg);
        assertTrue(msg.contains("<t:" + epoch + ":R>"), msg);
    }

    @Test
    void utcTimestampUsesUnixSeconds() {
        Instant moment = Instant.ofEpochSecond(1_800_000_000L);
        String msg = WhenHandler.formatReply("now", ZoneOffset.UTC, moment);
        assertTrue(msg.contains("<t:1800000000:F>"), msg);
        assertTrue(msg.contains("<t:1800000000:R>"), msg);
    }

    @Test
    void zoneIdIsCanonicalNotPretty() {
        // We use ZoneId.getId() so users can copy-paste back into /when in:.
        String msg = WhenHandler.formatReply("9am", ZoneId.of("Europe/London"),
                Instant.parse("2026-06-01T08:00:00Z"));
        assertTrue(msg.contains("Europe/London"), msg);
    }

    @Test
    void inputIsEchoedVerbatim() {
        // Whatever the user typed, even if odd, comes back so they can verify.
        String msg = WhenHandler.formatReply("In  3   Hours", ZoneOffset.UTC,
                Instant.parse("2026-05-08T17:00:00Z"));
        assertTrue(msg.contains("\"In  3   Hours\""), msg);
    }

    @Test
    void usesUtcEmojiAndQuotes() {
        // Light formatting check so future cosmetic tweaks don't pass silently.
        // Use ZoneId.of("UTC") not ZoneOffset.UTC — the latter's id is "Z" not "UTC".
        String msg = WhenHandler.formatReply("now", ZoneId.of("UTC"), Instant.ofEpochSecond(1L));
        assertEquals("🕒 *\"now\" in UTC*\n<t:1:F> (<t:1:R>)", msg);
    }
}
