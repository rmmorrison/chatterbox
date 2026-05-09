package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeParserTest {

    // Pin "now" to a Friday for predictable weekday and same-day rollover tests:
    // 2026-05-08 14:00 UTC is Friday afternoon.
    private static final Instant NOW = Instant.parse("2026-05-08T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private static Instant parseOk(String s, ZoneId zone) {
        var r = TimeParser.parse(s, Optional.of(zone), CLOCK);
        return ((TimeParser.Result.Ok) r).instant();
    }

    private static String parseFailReason(String s, ZoneId zone) {
        var r = TimeParser.parse(s, Optional.of(zone), CLOCK);
        return ((TimeParser.Result.Failed) r).reason();
    }

    // ---- now / relative / iso (zone-independent or zone-optional) ----

    @Test
    void nowReturnsCurrentInstant() {
        assertEquals(NOW, parseOk("now", UTC));
        assertEquals(NOW, parseOk("NOW", UTC));
    }

    @Test
    void nowWorksWithoutZone() {
        var r = TimeParser.parse("now", Optional.empty(), CLOCK);
        assertEquals(NOW, ((TimeParser.Result.Ok) r).instant());
    }

    @Test
    void relativeMinutesAndHours() {
        assertEquals(NOW.plusSeconds(30 * 60), parseOk("in 30m", UTC));
        assertEquals(NOW.plusSeconds(30 * 60), parseOk("in 30 minutes", UTC));
        assertEquals(NOW.plusSeconds(2 * 3600), parseOk("in 2 hours", UTC));
        assertEquals(NOW.plusSeconds(3 * 3600), parseOk("in 3h", UTC));
    }

    @Test
    void relativeWorksWithoutZone() {
        var r = TimeParser.parse("in 3 hours", Optional.empty(), CLOCK);
        assertEquals(NOW.plusSeconds(3 * 3600), ((TimeParser.Result.Ok) r).instant());
    }

    @Test
    void relativeDaysAndWeeks() {
        assertEquals(NOW.plusSeconds(2 * 86_400), parseOk("in 2 days", UTC));
        assertEquals(NOW.plusSeconds(86_400), parseOk("in 1d", UTC));
        assertEquals(NOW.plusSeconds(7 * 86_400), parseOk("in 1 week", UTC));
    }

    @Test
    void relativeRejectsNonPositive() {
        assertInstanceOf(TimeParser.Result.Failed.class,
                TimeParser.parse("in 0 hours", Optional.of(UTC), CLOCK));
    }

    @Test
    void isoDateTimeAtUtc() {
        assertEquals(Instant.parse("2026-12-25T12:00:00Z"),
                parseOk("2026-12-25 12:00", UTC));
        assertEquals(Instant.parse("2026-12-25T12:00:00Z"),
                parseOk("2026-12-25T12:00", UTC));
    }

    @Test
    void isoDateTimeRespectsZone() {
        // Toronto is EST (UTC-5) in December — no DST.
        assertEquals(Instant.parse("2026-12-25T17:00:00Z"),
                parseOk("2026-12-25 12:00", TORONTO));
    }

    @Test
    void isoDateTimeFallsBackToUtcWithoutZone() {
        // ISO datetimes are fully specified — when no zone is supplied,
        // UTC is the convention.
        var r = TimeParser.parse("2026-12-25 12:00", Optional.empty(), CLOCK);
        assertEquals(Instant.parse("2026-12-25T12:00:00Z"), ((TimeParser.Result.Ok) r).instant());
    }

    @Test
    void isoDateTimeWithSeconds() {
        assertEquals(Instant.parse("2026-12-25T12:34:56Z"),
                parseOk("2026-12-25T12:34:56", UTC));
    }

    @Test
    void isoDateAloneIsMidnightInZone() {
        assertEquals(Instant.parse("2026-12-25T00:00:00Z"),
                parseOk("2026-12-25", UTC));
    }

    @Test
    void isoDateFallsBackToUtcWithoutZone() {
        var r = TimeParser.parse("2026-12-25", Optional.empty(), CLOCK);
        assertEquals(Instant.parse("2026-12-25T00:00:00Z"), ((TimeParser.Result.Ok) r).instant());
    }

    // ---- today / tomorrow ----

    @Test
    void todayWithTimeUsesGivenTimeOnNowDate() {
        assertEquals(Instant.parse("2026-05-08T18:30:00Z"),
                parseOk("today 18:30", UTC));
    }

    @Test
    void tomorrowWithTimeAdvancesOneDay() {
        assertEquals(Instant.parse("2026-05-09T09:00:00Z"),
                parseOk("tomorrow 9am", UTC));
    }

    @Test
    void todayBareIsMidnightInZone() {
        assertEquals(Instant.parse("2026-05-08T00:00:00Z"),
                parseOk("today", UTC));
    }

    @Test
    void todayTomorrowRequireZone() {
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("today",        Optional.empty(), CLOCK));
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("tomorrow",     Optional.empty(), CLOCK));
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("tomorrow 9am", Optional.empty(), CLOCK));
    }

    @Test
    void requiresZoneMessageMentionsTimezoneCommand() {
        var r = (TimeParser.Result.RequiresZone)
                TimeParser.parse("tomorrow 9am", Optional.empty(), CLOCK);
        assertTrue(r.reason().toLowerCase().contains("/timezone"),
                () -> "expected /timezone hint, got: " + r.reason());
    }

    /**
     * The bug we shipped this branch to fix. Toronto user, Friday 23:00 EDT,
     * "tomorrow 12pm" — interpretation must use Toronto's calendar AND
     * Toronto's wall clock (because /timezone wins for interpretation).
     * Result: Saturday May 9 12:00 EDT = 2026-05-09T16:00:00Z.
     *
     * <p>Hand-verified with a separate Java program:
     * <pre>
     *   ZonedDateTime.of(2026, 5, 9, 12, 0, 0, 0, ZoneId.of("America/Toronto")).toInstant()
     *     == 2026-05-09T16:00:00Z
     * </pre>
     */
    @Test
    void tomorrowAtNoonInTorontoFromTorontoLateFridayNight() {
        // Friday May 8 23:00 EDT == Saturday May 9 03:00 UTC.
        Clock fridayLateInToronto = Clock.fixed(Instant.parse("2026-05-09T03:00:00Z"), ZoneOffset.UTC);
        var r = TimeParser.parse("tomorrow 12pm", Optional.of(TORONTO), fridayLateInToronto);
        Instant got = ((TimeParser.Result.Ok) r).instant();
        assertEquals(Instant.parse("2026-05-09T16:00:00Z"), got,
                "Caller is in Toronto at Friday 11pm EDT; 'tomorrow 12pm' must be Saturday noon EDT, "
                        + "which is 16:00 UTC. Got " + got + " instead.");
    }

    /**
     * Same caller and clock as above, but the parser is given Kolkata instead
     * of Toronto. Asserts the OLD (pre-/timezone) behaviour still works:
     * "tomorrow" follows Kolkata's calendar (already Saturday at this instant),
     * so "tomorrow 12pm Kolkata" lands on Sunday May 10. The handler reaches
     * this branch only when no /timezone is set.
     */
    @Test
    void tomorrowAtNoonInKolkataFromKolkata() {
        Clock fridayLateInToronto = Clock.fixed(Instant.parse("2026-05-09T03:00:00Z"), ZoneOffset.UTC);
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        // Now in Kolkata = Saturday May 9 08:30 IST → tomorrow = Sun May 10
        // Sun May 10 12:00 IST = Sun May 10 06:30 UTC
        var r = TimeParser.parse("tomorrow 12pm", Optional.of(kolkata), fridayLateInToronto);
        Instant got = ((TimeParser.Result.Ok) r).instant();
        assertEquals(Instant.parse("2026-05-10T06:30:00Z"), got);
    }

    // ---- bare time ----

    @Test
    void bareTimeFutureTodayPicksToday() {
        assertEquals(Instant.parse("2026-05-08T19:00:00Z"),
                parseOk("7pm", UTC));
    }

    @Test
    void bareTimePastTodayRollsToTomorrow() {
        assertEquals(Instant.parse("2026-05-09T10:00:00Z"),
                parseOk("10am", UTC));
    }

    @Test
    void bareTime24HourSyntax() {
        assertEquals(Instant.parse("2026-05-08T19:30:00Z"),
                parseOk("19:30", UTC));
    }

    @Test
    void bareTimeNoonAndMidnight() {
        assertEquals(LocalTime.NOON,     TimeParser.parseLocalTime("12pm"));
        assertEquals(LocalTime.MIDNIGHT, TimeParser.parseLocalTime("12am"));
    }

    @Test
    void bareTimeRejectsInvalidValues() {
        assertNull(TimeParser.parseLocalTime("25:00"));
        assertNull(TimeParser.parseLocalTime("13pm"));
        assertNull(TimeParser.parseLocalTime("7:99"));
    }

    @Test
    void bareTimeRequiresZone() {
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("7pm",   Optional.empty(), CLOCK));
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("19:00", Optional.empty(), CLOCK));
    }

    // ---- weekday names ----

    @Test
    void sameWeekdayWithFutureTimeIsToday() {
        assertEquals(Instant.parse("2026-05-08T19:00:00Z"),
                parseOk("friday 7pm", UTC));
    }

    @Test
    void sameWeekdayWithPastTimeRollsAWeek() {
        assertEquals(Instant.parse("2026-05-15T10:00:00Z"),
                parseOk("friday 10am", UTC));
    }

    @Test
    void bareWeekdayPicksNextOccurrence() {
        assertEquals(Instant.parse("2026-05-11T00:00:00Z"),
                parseOk("monday", UTC));
    }

    @Test
    void weekdayShortFormsAccepted() {
        assertEquals(Instant.parse("2026-05-11T00:00:00Z"),
                parseOk("mon", UTC));
        assertEquals(Instant.parse("2026-05-14T15:00:00Z"),
                parseOk("thu 15:00", UTC));
    }

    @Test
    void weekdayWithUnparseableTimeFails() {
        var failed = (TimeParser.Result.Failed)
                TimeParser.parse("monday garbage", Optional.of(UTC), CLOCK);
        assertTrue(failed.reason().contains("garbage"), failed.reason());
    }

    @Test
    void weekdayRequiresZone() {
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("friday",     Optional.empty(), CLOCK));
        assertInstanceOf(TimeParser.Result.RequiresZone.class,
                TimeParser.parse("monday 9am", Optional.empty(), CLOCK));
    }

    // ---- failure paths ----

    @Test
    void emptyAndNullRejected() {
        assertInstanceOf(TimeParser.Result.Failed.class,
                TimeParser.parse(null, Optional.of(UTC), CLOCK));
        assertInstanceOf(TimeParser.Result.Failed.class,
                TimeParser.parse("",   Optional.of(UTC), CLOCK));
        assertInstanceOf(TimeParser.Result.Failed.class,
                TimeParser.parse("  ", Optional.of(UTC), CLOCK));
    }

    @Test
    void garbageRejectedWithExamples() {
        String reason = parseFailReason("not a real time", UTC);
        assertTrue(reason.toLowerCase().contains("couldn't parse"), reason);
        assertTrue(reason.toLowerCase().contains("try"), reason);
    }

    @Test
    void invalidIsoDateRejected() {
        assertInstanceOf(TimeParser.Result.Failed.class,
                TimeParser.parse("2026-13-01", Optional.of(UTC), CLOCK));
    }

    // ---- DST sanity ----

    @Test
    void dstBoundaryHandledByZonedDateTime() {
        // 2026-03-08 02:30 in America/New_York doesn't exist (skipped by spring-forward).
        // ZonedDateTime resolves it forward to 03:30 EDT, which is 07:30 UTC.
        Instant got = parseOk("2026-03-08 02:30", ZoneId.of("America/New_York"));
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), got);
    }
}
