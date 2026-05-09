package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderer-only checks for {@link WhenHandler#formatReply}. The handler's
 * orchestration (zone resolution, repo lookup, parser dispatch) is covered
 * by {@link ZoneResolutionTest} and {@link TimeParserTest}.
 */
class WhenHandlerTest {

    @Test
    void replyWithoutDisplayZoneIsJustTheTimestamp() {
        // No in: → reply is just the Discord markdown. Discord auto-localizes
        // it for each viewer, so a wall-clock literal in the caller's own zone
        // would just be redundant noise.
        Instant moment = Instant.parse("2026-12-25T17:00:00Z");
        String msg = WhenHandler.formatReply(Optional.empty(), moment);

        long epoch = moment.getEpochSecond();
        assertEquals("<t:" + epoch + ":F> (<t:" + epoch + ":R>)", msg);
    }

    @Test
    void replyWithDisplayZoneLeadsWithLiteralThenDiscordTimestamp() {
        Instant moment = Instant.parse("2026-12-25T17:00:00Z");
        String msg = WhenHandler.formatReply(Optional.of(ZoneId.of("America/Toronto")), moment);

        long epoch = moment.getEpochSecond();
        // Bold literal leads, then em-dash, then the Discord timestamp.
        assertTrue(msg.startsWith("**"), () -> msg);
        assertTrue(msg.contains(" in America/Toronto — <t:" + epoch + ":F>"), () -> msg);
        assertTrue(msg.endsWith("(<t:" + epoch + ":R>)"), () -> msg);
    }

    @Test
    void wallClockLineRendersInDisplayZoneNotUtc() {
        // 17:00 UTC = 22:30 IST in Asia/Kolkata.
        Instant moment = Instant.parse("2026-05-08T17:00:00Z");
        String msg = WhenHandler.formatReply(Optional.of(ZoneId.of("Asia/Kolkata")), moment);
        assertTrue(msg.contains("**Friday, May 8, 2026 at 10:30 PM**"),
                () -> "expected Kolkata wall clock, got: " + msg);
    }

    /**
     * The user-reported confusion case. Sunday May 10 9:00 AM in Europe/London
     * (epoch 1778400000) — the literal leads now so the answer to "9 AM London"
     * isn't buried after the Discord timestamp that renders in viewer locale.
     */
    @Test
    void bugFixSundayNineAmInLondon() {
        Instant moment = Instant.parse("2026-05-10T08:00:00Z"); // = Sun May 10 09:00 BST
        String msg = WhenHandler.formatReply(Optional.of(ZoneId.of("Europe/London")), moment);
        // Hand-verified: Instant.parse("2026-05-10T08:00:00Z").getEpochSecond() == 1778400000.
        assertEquals(
                "**Sunday, May 10, 2026 at 9:00 AM** in Europe/London — "
                        + "<t:1778400000:F> (<t:1778400000:R>)",
                msg);
    }

    /**
     * Same instant, no in: → reply collapses to just the timestamp markdown.
     * Discord renders {@code <t:F>} for the Toronto-stored caller as
     * "Saturday, May 9, 2026 at 12:00 PM" — exactly the noon they expected.
     */
    @Test
    void bugFixSaturdayNoonRendersAsBareTimestampWhenNoInProvided() {
        Instant moment = Instant.parse("2026-05-09T16:00:00Z");
        String msg = WhenHandler.formatReply(Optional.empty(), moment);
        assertEquals("<t:1778342400:F> (<t:1778342400:R>)", msg);
    }

    @Test
    void wallClockUsesEnglishLocaleRegardlessOfDefault() {
        java.util.Locale prior = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);
            Instant moment = Instant.parse("2026-05-08T17:00:00Z");
            String msg = WhenHandler.formatReply(Optional.of(ZoneId.of("UTC")), moment);
            assertTrue(msg.contains("Friday"), () -> "expected English weekday, got: " + msg);
            assertTrue(msg.contains("May"),    () -> "expected English month, got: " + msg);
        } finally {
            java.util.Locale.setDefault(prior);
        }
    }

    @Test
    void singleLineLayoutWithDisplayZone() {
        // Light formatting check so future cosmetic tweaks don't pass silently.
        // Epoch second 1 = 1970-01-01 00:00:01 UTC.
        String msg = WhenHandler.formatReply(Optional.of(ZoneId.of("UTC")), Instant.ofEpochSecond(1L));
        assertEquals(
                "**Thursday, January 1, 1970 at 12:00 AM** in UTC — <t:1:F> (<t:1:R>)",
                msg);
    }
}
