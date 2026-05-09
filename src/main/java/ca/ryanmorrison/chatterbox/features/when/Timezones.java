package ca.ryanmorrison.chatterbox.features.when;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Curated IANA-zone list for {@code /when}'s {@code in:} autocomplete.
 *
 * <p>Discord's autocomplete shows up to 25 choices at a time and there are
 * ~600 IANA zones, so we curate a friendly subset rather than dump the full
 * list. Empty input shows the most globally common zones; typing filters
 * by case-insensitive substring against both the canonical id (e.g.
 * {@code America/Toronto}) and a stripped-prefix form (e.g. {@code Toronto})
 * so a user typing "tor" finds Toronto without having to know the region.
 *
 * <p>{@link #resolve} accepts <em>any</em> valid {@link ZoneId} string
 * (including offsets like {@code +05:30} and short ids like {@code UTC}),
 * so power users aren't fenced in by the curated list.
 */
public final class Timezones {

    private Timezones() {}

    /** Front-of-list zones shown when the user hasn't typed anything yet. */
    static final List<String> POPULAR = List.of(
            "UTC",
            "America/Los_Angeles",
            "America/Denver",
            "America/Chicago",
            "America/New_York",
            "America/Toronto",
            "America/Vancouver",
            "America/Sao_Paulo",
            "Europe/London",
            "Europe/Dublin",
            "Europe/Lisbon",
            "Europe/Paris",
            "Europe/Berlin",
            "Europe/Amsterdam",
            "Europe/Madrid",
            "Europe/Rome",
            "Europe/Athens",
            "Europe/Moscow",
            "Asia/Dubai",
            "Asia/Kolkata",
            "Asia/Singapore",
            "Asia/Hong_Kong",
            "Asia/Tokyo",
            "Australia/Sydney",
            "Pacific/Auckland");

    /**
     * Larger curated set used as the autocomplete substring index. Order
     * here doesn't matter; matches keep the first-seen alphabetical
     * ordering at query time.
     */
    static final List<String> ALL = List.of(
            "UTC",
            // Americas
            "America/Anchorage",
            "America/Argentina/Buenos_Aires",
            "America/Bogota",
            "America/Caracas",
            "America/Chicago",
            "America/Denver",
            "America/Edmonton",
            "America/Halifax",
            "America/Havana",
            "America/Honolulu",
            "America/Lima",
            "America/Los_Angeles",
            "America/Mexico_City",
            "America/Montevideo",
            "America/Montreal",
            "America/New_York",
            "America/Phoenix",
            "America/Regina",
            "America/Santiago",
            "America/Sao_Paulo",
            "America/St_Johns",
            "America/Toronto",
            "America/Vancouver",
            "America/Winnipeg",
            // Europe
            "Atlantic/Reykjavik",
            "Europe/Amsterdam",
            "Europe/Athens",
            "Europe/Belgrade",
            "Europe/Berlin",
            "Europe/Brussels",
            "Europe/Bucharest",
            "Europe/Budapest",
            "Europe/Copenhagen",
            "Europe/Dublin",
            "Europe/Helsinki",
            "Europe/Istanbul",
            "Europe/Kyiv",
            "Europe/Lisbon",
            "Europe/London",
            "Europe/Madrid",
            "Europe/Moscow",
            "Europe/Oslo",
            "Europe/Paris",
            "Europe/Prague",
            "Europe/Rome",
            "Europe/Sofia",
            "Europe/Stockholm",
            "Europe/Vienna",
            "Europe/Warsaw",
            "Europe/Zurich",
            // Africa
            "Africa/Cairo",
            "Africa/Casablanca",
            "Africa/Johannesburg",
            "Africa/Lagos",
            "Africa/Nairobi",
            // Asia
            "Asia/Almaty",
            "Asia/Baghdad",
            "Asia/Bangkok",
            "Asia/Dhaka",
            "Asia/Dubai",
            "Asia/Hong_Kong",
            "Asia/Jakarta",
            "Asia/Jerusalem",
            "Asia/Karachi",
            "Asia/Kolkata",
            "Asia/Kuala_Lumpur",
            "Asia/Manila",
            "Asia/Riyadh",
            "Asia/Seoul",
            "Asia/Shanghai",
            "Asia/Singapore",
            "Asia/Taipei",
            "Asia/Tehran",
            "Asia/Tokyo",
            // Pacific / Oceania
            "Australia/Adelaide",
            "Australia/Brisbane",
            "Australia/Darwin",
            "Australia/Hobart",
            "Australia/Melbourne",
            "Australia/Perth",
            "Australia/Sydney",
            "Pacific/Auckland",
            "Pacific/Fiji",
            "Pacific/Guam",
            "Pacific/Honolulu");

    /** Hard cap from Discord; any more and the API rejects the autocomplete reply. */
    static final int MAX_AUTOCOMPLETE_CHOICES = 25;

    /**
     * Suggestions for the autocomplete dropdown. Empty {@code prefix}
     * returns {@link #POPULAR} verbatim; otherwise filters {@link #ALL}
     * by case-insensitive substring match against either the canonical
     * id or its city portion (everything after the last {@code /}).
     */
    public static List<String> suggest(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return POPULAR;
        }
        String needle = prefix.toLowerCase(Locale.ROOT);
        return ALL.stream()
                .filter(z -> {
                    String lower = z.toLowerCase(Locale.ROOT);
                    if (lower.contains(needle)) return true;
                    int slash = lower.lastIndexOf('/');
                    return slash >= 0 && lower.substring(slash + 1).contains(needle);
                })
                .limit(MAX_AUTOCOMPLETE_CHOICES)
                .toList();
    }

    /**
     * Resolves the user's chosen string to a {@link ZoneId}, accepting
     * anything {@link ZoneId#of(String)} accepts — IANA names, offsets
     * ({@code +05:30}), short ids ({@code Z}, {@code UTC}). Returns
     * {@link Optional#empty()} on invalid input so the handler can show
     * a clean error rather than leaking the {@link DateTimeException}.
     */
    public static Optional<ZoneId> resolve(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
