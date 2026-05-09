package ca.ryanmorrison.chatterbox.features.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * <h2>Zone semantics</h2>
 * The {@code zone} parameter is the <em>interpretation</em> zone — used to
 * resolve relative inputs ({@code today}, {@code tomorrow}, weekday names,
 * bare times) and to attach a wall clock to ISO inputs. The handler's
 * <em>display</em> zone is a separate concern (see {@link ZoneResolution}).
 *
 * <p>When {@code zone} is empty:
 * <ul>
 *   <li>Zone-independent inputs ({@code now}, relative offsets) succeed.</li>
 *   <li>ISO datetimes/dates fall back to {@link ZoneOffset#UTC} — they're
 *       fully specified moments, just need <em>some</em> wall-clock zone
 *       to attach to.</li>
 *   <li>Calendar-relative inputs return
 *       {@link Result.RequiresZone}; the handler surfaces this as a
 *       "set your timezone" prompt.</li>
 * </ul>
 *
 * <p>DST and offset edges are handled by {@link ZonedDateTime}.
 */
public final class TimeParser {

    private TimeParser() {}

    public sealed interface Result {
        record Ok(Instant instant) implements Result {}
        record Failed(String reason) implements Result {}
        /**
         * The input is calendar-relative ({@code today}, {@code tomorrow},
         * weekday name, bare time) but no interpretation zone was
         * supplied. Resolving it without one would either guess wrong
         * (the original "tomorrow in Kolkata picks Sunday when I'm in
         * Toronto" surprise) or pick UTC, which is also rarely what the
         * user means.
         */
        record RequiresZone(String reason) implements Result {}
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

    private static final String NEEDS_ZONE =
            "I don't know which timezone to interpret this in. Set yours with "
                    + "`/timezone set tz:<your zone>` first, pass `in:<zone>` on the command, "
                    + "or use an absolute form like `2026-12-25 14:00`.";

    public static Result parse(String input, Optional<ZoneId> zone, Clock clock) {
        if (input == null) return new Result.Failed("No time provided. " + EXAMPLES);
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return new Result.Failed("No time provided. " + EXAMPLES);

        // Each parser returns null if it doesn't recognise the shape; any
        // non-null Result short-circuits the chain.
        Result r;
        if ((r = tryNow(trimmed, clock)) != null)            return r;
        if ((r = tryRelative(trimmed, clock)) != null)       return r;
        // ISO inputs are fully specified; if no zone provided, fall back to UTC.
        ZoneId isoZone = zone.orElse(ZoneOffset.UTC);
        if ((r = tryIsoDateTime(trimmed, isoZone)) != null)  return r;
        if ((r = tryIsoDate(trimmed, isoZone)) != null)      return r;
        // Calendar-relative inputs require a zone; without one we can't decide
        // whose calendar to consult.
        if ((r = tryTodayTomorrow(trimmed, zone, clock)) != null) return r;
        if ((r = tryWeekday(trimmed, zone, clock)) != null)       return r;
        if ((r = tryBareTime(trimmed, zone, clock)) != null)      return r;

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

    private static Result tryIsoDateTime(String s, ZoneId zone) {
        if (!ISO_DATETIME.matcher(s).matches()) return null;
        // The space-separator form is common in input; the ISO parser wants 'T'.
        String normalised = s.replace(' ', 'T');
        // Add seconds if missing — ISO_LOCAL_DATE_TIME insists on them in some forms.
        if (normalised.length() == 16) normalised = normalised + ":00";
        try {
            LocalDateTime ldt = LocalDateTime.parse(normalised, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return new Result.Ok(ldt.atZone(zone).toInstant());
        } catch (DateTimeParseException e) {
            return new Result.Failed("`" + s + "` isn't a valid date/time.");
        }
    }

    private static Result tryIsoDate(String s, ZoneId zone) {
        if (!ISO_DATE.matcher(s).matches()) return null;
        try {
            LocalDate date = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return new Result.Ok(date.atStartOfDay(zone).toInstant());
        } catch (DateTimeParseException e) {
            return new Result.Failed("`" + s + "` isn't a valid date.");
        }
    }

    private static Result tryTodayTomorrow(String s, Optional<ZoneId> zoneOpt, Clock clock) {
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

        if (zoneOpt.isEmpty()) return new Result.RequiresZone(NEEDS_ZONE);
        ZoneId zone = zoneOpt.get();

        LocalDate date = ZonedDateTime.now(clock.withZone(zone)).toLocalDate().plusDays(offsetDays);
        LocalTime time = rest.isEmpty() ? LocalTime.MIDNIGHT : parseLocalTime(rest);
        if (time == null) return new Result.Failed("`" + rest + "` isn't a valid time.");
        return new Result.Ok(ZonedDateTime.of(date, time, zone).toInstant());
    }

    private static Result tryWeekday(String s, Optional<ZoneId> zoneOpt, Clock clock) {
        String lower = s.toLowerCase(Locale.ROOT);
        // First token might be a weekday; the rest is an optional time string.
        int sp = lower.indexOf(' ');
        String head = sp < 0 ? lower : lower.substring(0, sp);
        DayOfWeek dow = WEEKDAYS.get(head);
        if (dow == null) return null;

        if (zoneOpt.isEmpty()) return new Result.RequiresZone(NEEDS_ZONE);
        ZoneId zone = zoneOpt.get();

        String rest = sp < 0 ? "" : lower.substring(sp + 1).trim();
        LocalTime time = rest.isEmpty() ? LocalTime.MIDNIGHT : parseLocalTime(rest);
        if (time == null) return new Result.Failed("`" + rest + "` isn't a valid time.");

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        LocalDate today = now.toLocalDate();
        // Strictly-future rule: even if today is the requested weekday, prefer
        // the future moment. If a time is given and today's that weekday with
        // the time still ahead, today wins; otherwise advance.
        ZonedDateTime candidate = ZonedDateTime.of(today, time, zone);
        int daysUntil = (dow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        if (daysUntil == 0 && !candidate.isAfter(now)) daysUntil = 7;
        candidate = ZonedDateTime.of(today.plusDays(daysUntil), time, zone);
        return new Result.Ok(candidate.toInstant());
    }

    private static Result tryBareTime(String s, Optional<ZoneId> zoneOpt, Clock clock) {
        LocalTime time = parseLocalTime(s);
        if (time == null) return null;

        if (zoneOpt.isEmpty()) return new Result.RequiresZone(NEEDS_ZONE);
        ZoneId zone = zoneOpt.get();

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        ZonedDateTime candidate = ZonedDateTime.of(now.toLocalDate(), time, zone);
        // "Tonight's 7pm" semantics: if the time has passed today, jump to tomorrow.
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
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
