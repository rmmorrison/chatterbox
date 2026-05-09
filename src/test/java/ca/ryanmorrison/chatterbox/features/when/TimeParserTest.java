package ca.ryanmorrison.chatterbox.features.when;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
        var r = TimeParser.parse(s, zone, CLOCK);
        return ((TimeParser.Result.Ok) r).instant();
    }

    private static String parseFailReason(String s, ZoneId zone) {
        var r = TimeParser.parse(s, zone, CLOCK);
        return ((TimeParser.Result.Failed) r).reason();
    }

    // ---- now ----

    @Test
    void nowReturnsCurrentInstant() {
        assertEquals(NOW, parseOk("now", UTC));
        assertEquals(NOW, parseOk("NOW", UTC));
    }

    // ---- relative ----

    @Test
    void relativeMinutesAndHours() {
        assertEquals(NOW.plusSeconds(30 * 60), parseOk("in 30m", UTC));
        assertEquals(NOW.plusSeconds(30 * 60), parseOk("in 30 minutes", UTC));
        assertEquals(NOW.plusSeconds(2 * 3600), parseOk("in 2 hours", UTC));
        assertEquals(NOW.plusSeconds(3 * 3600), parseOk("in 3h", UTC));
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
                TimeParser.parse("in 0 hours", UTC, CLOCK));
    }

    // ---- ISO date / datetime ----

    @Test
    void isoDateTimeAtUtc() {
        // 2026-12-25 12:00 UTC.
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
    void isoDateTimeWithSeconds() {
        assertEquals(Instant.parse("2026-12-25T12:34:56Z"),
                parseOk("2026-12-25T12:34:56", UTC));
    }

    @Test
    void isoDateAloneIsMidnightInZone() {
        assertEquals(Instant.parse("2026-12-25T00:00:00Z"),
                parseOk("2026-12-25", UTC));
    }

    // ---- today / tomorrow ----

    @Test
    void todayWithTimeUsesGivenTimeOnNowDate() {
        // 2026-05-08 18:30 UTC.
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

    // ---- bare time / today-or-tomorrow rollover ----

    @Test
    void bareTimeFutureTodayPicksToday() {
        // It's 14:00 UTC, "7pm" is later today.
        assertEquals(Instant.parse("2026-05-08T19:00:00Z"),
                parseOk("7pm", UTC));
    }

    @Test
    void bareTimePastTodayRollsToTomorrow() {
        // It's 14:00 UTC, "10am" is in the past today; should roll to tomorrow.
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

    // ---- weekday names ----

    @Test
    void sameWeekdayWithFutureTimeIsToday() {
        // Friday at 14:00 UTC; "friday 7pm" is later today.
        assertEquals(Instant.parse("2026-05-08T19:00:00Z"),
                parseOk("friday 7pm", UTC));
    }

    @Test
    void sameWeekdayWithPastTimeRollsAWeek() {
        // Friday at 14:00 UTC; "friday 10am" is past today, → next Friday.
        assertEquals(Instant.parse("2026-05-15T10:00:00Z"),
                parseOk("friday 10am", UTC));
    }

    @Test
    void bareWeekdayPicksNextOccurrence() {
        // Friday now; "monday" → next Monday at midnight.
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
        var failed = (TimeParser.Result.Failed) TimeParser.parse("monday garbage", UTC, CLOCK);
        assertTrue(failed.reason().contains("garbage"), failed.reason());
    }

    // ---- failure paths ----

    @Test
    void emptyAndNullRejected() {
        assertInstanceOf(TimeParser.Result.Failed.class, TimeParser.parse(null, UTC, CLOCK));
        assertInstanceOf(TimeParser.Result.Failed.class, TimeParser.parse("", UTC, CLOCK));
        assertInstanceOf(TimeParser.Result.Failed.class, TimeParser.parse("  ", UTC, CLOCK));
    }

    @Test
    void garbageRejectedWithExamples() {
        String reason = parseFailReason("not a real time", UTC);
        assertTrue(reason.toLowerCase().contains("couldn't parse"), reason);
        assertTrue(reason.toLowerCase().contains("try"), reason);
    }

    @Test
    void invalidIsoDateRejected() {
        // Pattern matches but the values are bad.
        assertInstanceOf(TimeParser.Result.Failed.class,
                TimeParser.parse("2026-13-01", UTC, CLOCK));
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
