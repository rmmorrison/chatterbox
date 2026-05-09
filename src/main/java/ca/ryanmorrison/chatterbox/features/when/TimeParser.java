package ca.ryanmorrison.chatterbox.features.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the {@code at:} string from {@code /when} into an {@link Instant}.
 *
 * <p>Supports a deliberately small grammar: ISO datetimes/dates, bare times
 * ({@code 7pm}, {@code 19:00}), {@code today}/{@code tomorrow} + optional
 * time, weekday names + optional time, relative offsets ({@code in 30m},
 * {@code in 3 hours}), and {@code now}. Each parser is tried in turn; the
 * first match wins.
 *
 * <h2>Two zones, two roles</h2>
 * Inputs decompose into a calendar reference (the date) and a wall clock
 * (the time of day). The parser separates these:
 * <ul>
 *   <li>{@code callerZone} — used to resolve "today", "tomorrow", weekday
 *       names, and the today-or-tomorrow rollover for bare times. This is
 *       the user's own timezone, kept by the {@code /timezone} command.</li>
 *   <li>{@code targetZone} — used to interpret wall-clock times against.
 *       This is the {@code in:} option on {@code /when}.</li>
 * </ul>
 * Inputs that don't depend on a calendar reference ({@code now}, relative
 * offsets, fully-specified ISO date / datetime) work without {@code callerZone}.
 * Inputs that <em>do</em> depend on it return {@link Result.RequiresCallerZone}
 * when it's absent, with a message pointing the user at {@code /timezone set}.
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>Bare times default to today (in {@code callerZone}); if the resolved
 *       moment is already past, roll over to tomorrow.</li>
 *   <li>Weekday names always pick the soonest strictly-future occurrence
 *       in {@code callerZone}.</li>
 *   <li>DST and offset edges are handled by {@link ZonedDateTime}.</li>
 * </ul>
 */
public final class TimeParser {

    private TimeParser() {}

    public sealed interface Result {
        record Ok(Instant instant) implements Result {}
        record Failed(String reason) implements Result {}
        /**
         * The input is calendar-relative ({@code today}, {@code tomorrow},
         * weekday name, bare time) but no caller zone was supplied.
         * Resolving it without one would either guess wrong (using
         * {@code targetZone} as a stand-in, the source of the original
         * "tomorrow in Kolkata picks Sunday when I'm in Toronto" surprise)
         * or pick UTC, which is also rarely what the user means.
         */
        record RequiresCallerZone(String reason) implements Result {}
    }

    /** Names recognised after "today"/"tomorrow"/"<weekday>". */
    private static final Pattern BARE_TIME =
            Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", Pattern.CASE_INSENSITIVE);

    /** "in 30m", "in 3 hours", "in 2 days". */
    private static final Pattern RELATIVE = Pattern.compile(
            "^in\\s+(\\d+)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks)$",
            Pattern.CASE_INSENSITIVE);

    /** "2026-12-25 18:00" or "2026-12-25T18:00" (with optional :seconds). */
    private static final Pattern ISO_DATETIME =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{1,2}:\\d{2}(:\\d{2})?$");

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** Lowercase weekday names → DayOfWeek. */
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("mon", DayOfWeek.MONDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("tue", DayOfWeek.TUESDAY),
            Map.entry("tues", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY),
            Map.entry("wed", DayOfWeek.WEDNESDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("thu", DayOfWeek.THURSDAY),
            Map.entry("thur", DayOfWeek.THURSDAY),
            Map.entry("thurs", DayOfWeek.THURSDAY),
            Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("fri", DayOfWeek.FRIDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("sat", DayOfWeek.SATURDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY),
            Map.entry("sun", DayOfWeek.SUNDAY));

    private static final String EXAMPLES =
            "Try `7pm`, `19:00`, `tomorrow 9am`, `friday 3pm`, `in 2 hours`, "
                    + "or `2026-12-25 14:00`.";

    private static final String NEEDS_TIMEZONE =
            "I don't have your timezone on file, so this depends on which "
                    + "calendar I should consult. Set yours with `/timezone set tz:<your zone>` "
                    + "first, or use an absolute form like `2026-12-25 14:00`.";

    public static Result parse(String input, Optional<ZoneId> callerZone, ZoneId targetZone, Clock clock) {
        if (input == null) return new Result.Failed("No time provided. " + EXAMPLES);
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return new Result.Failed("No time provided. " + EXAMPLES);

        // Each parser returns null if it doesn't recognise the shape; any
        // non-null Result short-circuits the chain.
        Result r;
        if ((r = tryNow(trimmed, clock)) != null)                                return r;
        if ((r = tryRelative(trimmed, clock)) != null)                            return r;
        if ((r = tryIsoDateTime(trimmed, targetZone)) != null)                    return r;
        if ((r = tryIsoDate(trimmed, targetZone)) != null)                        return r;
        if ((r = tryTodayTomorrow(trimmed, callerZone, targetZone, clock)) != null) return r;
        if ((r = tryWeekday(trimmed, callerZone, targetZone, clock)) != null)     return r;
        if ((r = tryBareTime(trimmed, callerZone, targetZone, clock)) != null)    return r;

        return new Result.Failed("Couldn't parse `" + input + "`. " + EXAMPLES);
    }

    // ---- shape parsers (return null when not applicable) ----

    private static Result tryNow(String s, Clock clock) {
        if (!s.equalsIgnoreCase("now")) return null;
        return new Result.Ok(clock.instant());
    }

    private static Result tryRelative(String s, Clock clock) {
        Matcher m = RELATIVE.matcher(s);
        if (!m.matches()) return null;
        long n;
        try {
            n = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return new Result.Failed("Relative offset is too large.");
        }
        if (n <= 0) return new Result.Failed("Relative offset must be positive.");

        String unit = m.group(2).toLowerCase(Locale.ROOT);
        long seconds = switch (unit) {
            case "m", "min", "mins", "minute", "minutes" -> n * 60L;
            case "h", "hr", "hrs", "hour", "hours"       -> n * 3_600L;
            case "d", "day", "days"                      -> n * 86_400L;
            case "w", "week", "weeks"                    -> n * 604_800L;
            default -> -1L;
        };
        if (seconds < 0) return new Result.Failed("Unknown time unit `" + unit + "`.");
        return new Result.Ok(clock.instant().plusSeconds(seconds));
    }

    private static Result tryIsoDateTime(String s, ZoneId targetZone) {
        if (!ISO_DATETIME.matcher(s).matches()) return null;
        // The space-separator form is common in input; the ISO parser wants 'T'.
        String normalised = s.replace(' ', 'T');
        // Add seconds if missing — ISO_LOCAL_DATE_TIME insists on them in some forms.
        if (normalised.length() == 16) normalised = normalised + ":00";
        try {
            LocalDateTime ldt = LocalDateTime.parse(normalised, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return new Result.Ok(ldt.atZone(targetZone).toInstant());
        } catch (DateTimeParseException e) {
            return new Result.Failed("`" + s + "` isn't a valid date/time.");
        }
    }

    private static Result tryIsoDate(String s, ZoneId targetZone) {
        if (!ISO_DATE.matcher(s).matches()) return null;
        try {
            LocalDate date = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return new Result.Ok(date.atStartOfDay(targetZone).toInstant());
        } catch (DateTimeParseException e) {
            return new Result.Failed("`" + s + "` isn't a valid date.");
        }
    }

    private static Result tryTodayTomorrow(String s, Optional<ZoneId> callerZone,
                                           ZoneId targetZone, Clock clock) {
        String lower = s.toLowerCase(Locale.ROOT);
        int offsetDays;
        String rest;
        if (lower.equals("today")) {
            offsetDays = 0; rest = "";
        } else if (lower.equals("tomorrow")) {
            offsetDays = 1; rest = "";
        } else if (lower.startsWith("today ")) {
            offsetDays = 0; rest = lower.substring("today ".length()).trim();
        } else if (lower.startsWith("tomorrow ")) {
            offsetDays = 1; rest = lower.substring("tomorrow ".length()).trim();
        } else {
            return null;
        }

        if (callerZone.isEmpty()) return new Result.RequiresCallerZone(NEEDS_TIMEZONE);
        ZoneId calendar = callerZone.get();

        LocalDate date = ZonedDateTime.now(clock.withZone(calendar)).toLocalDate().plusDays(offsetDays);
        if (rest.isEmpty()) {
            // No wall-clock time given — use midnight in the *caller's* zone.
            // The target zone is irrelevant for a time-less moment.
            return new Result.Ok(date.atStartOfDay(calendar).toInstant());
        }
        LocalTime time = parseLocalTime(rest);
        if (time == null) return new Result.Failed("`" + rest + "` isn't a valid time.");
        return new Result.Ok(ZonedDateTime.of(date, time, targetZone).toInstant());
    }

    private static Result tryWeekday(String s, Optional<ZoneId> callerZone,
                                     ZoneId targetZone, Clock clock) {
        String lower = s.toLowerCase(Locale.ROOT);
        // First token might be a weekday; the rest is an optional time string.
        int sp = lower.indexOf(' ');
        String head = sp < 0 ? lower : lower.substring(0, sp);
        DayOfWeek dow = WEEKDAYS.get(head);
        if (dow == null) return null;

        if (callerZone.isEmpty()) return new Result.RequiresCallerZone(NEEDS_TIMEZONE);
        ZoneId calendar = callerZone.get();

        String rest = sp < 0 ? "" : lower.substring(sp + 1).trim();
        LocalTime time = rest.isEmpty() ? LocalTime.MIDNIGHT : parseLocalTime(rest);
        if (time == null) return new Result.Failed("`" + rest + "` isn't a valid time.");

        ZoneId wallClockZone = rest.isEmpty() ? calendar : targetZone;
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(calendar));
        LocalDate today = now.toLocalDate();
        // Strictly-future rule: even if today is the requested weekday, prefer
        // the future moment. If a time is given and today's that weekday with
        // the time still ahead, today wins; otherwise advance.
        ZonedDateTime candidate = ZonedDateTime.of(today, time, wallClockZone);
        int daysUntil = (dow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        if (daysUntil == 0 && !candidate.isAfter(now)) daysUntil = 7;
        candidate = ZonedDateTime.of(today.plusDays(daysUntil), time, wallClockZone);
        return new Result.Ok(candidate.toInstant());
    }

    private static Result tryBareTime(String s, Optional<ZoneId> callerZone,
                                      ZoneId targetZone, Clock clock) {
        LocalTime time = parseLocalTime(s);
        if (time == null) return null;

        if (callerZone.isEmpty()) return new Result.RequiresCallerZone(NEEDS_TIMEZONE);
        ZoneId calendar = callerZone.get();

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(calendar));
        ZonedDateTime candidate = ZonedDateTime.of(now.toLocalDate(), time, targetZone);
        // "Tonight's 7pm" semantics: if the time has passed in real time,
        // jump forward a day in the caller's calendar.
        if (!candidate.isAfter(now)) {
            candidate = ZonedDateTime.of(now.toLocalDate().plusDays(1), time, targetZone);
        }
        return new Result.Ok(candidate.toInstant());
    }

    /**
     * Parses {@code 7pm}, {@code 7:30pm}, {@code 19:00}, etc. into a
     * {@link LocalTime}. Returns null on no-match (so callers can chain) and
     * accepts both 12-hour (with am/pm) and 24-hour (without) forms.
     */
    static LocalTime parseLocalTime(String s) {
        Matcher m = BARE_TIME.matcher(s);
        if (!m.matches()) return null;
        int hour;
        try {
            hour = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String meridiem = m.group(3);
        if (meridiem == null) {
            // 24-hour form. Hour 0-23, minute 0-59.
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return LocalTime.of(hour, minute);
        }
        // 12-hour form. Hour 1-12.
        if (hour < 1 || hour > 12 || minute < 0 || minute > 59) return null;
        int normalised = switch (meridiem.toLowerCase(Locale.ROOT)) {
            case "am" -> hour == 12 ? 0  : hour;
            case "pm" -> hour == 12 ? 12 : hour + 12;
            default   -> -1;
        };
        if (normalised < 0) return null;
        return LocalTime.of(normalised, minute);
    }
}
