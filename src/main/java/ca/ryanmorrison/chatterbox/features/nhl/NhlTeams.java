package ca.ryanmorrison.chatterbox.features.nhl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Static lookup of the 32 NHL franchises by three-letter abbreviation.
 * Used for slash-command autocomplete and to resolve a friendly display name
 * for the embed title. The list is small and changes about once a decade
 * (UTA replacing ARI in 2024 was the most recent), so hard-coding it avoids
 * an extra round-trip on every command invocation.
 */
final class NhlTeams {

    private static final Map<String, String> NAMES_BY_ABBREV = build();

    private NhlTeams() {}

    /** Three-letter codes in alphabetical order. */
    static List<String> abbreviations() {
        return List.copyOf(NAMES_BY_ABBREV.keySet());
    }

    /** Full display name (e.g. {@code TOR} → {@code Toronto Maple Leafs}). */
    static Optional<String> displayName(String abbrev) {
        if (abbrev == null) return Optional.empty();
        return Optional.ofNullable(NAMES_BY_ABBREV.get(abbrev.trim().toUpperCase(Locale.ROOT)));
    }

    /** True if the abbreviation matches a known franchise (case-insensitive). */
    static boolean isKnown(String abbrev) {
        return displayName(abbrev).isPresent();
    }

    private static Map<String, String> build() {
        // Insertion order = display order, so we use a LinkedHashMap and add
        // alphabetically by abbreviation.
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("ANA", "Anaheim Ducks");
        m.put("BOS", "Boston Bruins");
        m.put("BUF", "Buffalo Sabres");
        m.put("CAR", "Carolina Hurricanes");
        m.put("CBJ", "Columbus Blue Jackets");
        m.put("CGY", "Calgary Flames");
        m.put("CHI", "Chicago Blackhawks");
        m.put("COL", "Colorado Avalanche");
        m.put("DAL", "Dallas Stars");
        m.put("DET", "Detroit Red Wings");
        m.put("EDM", "Edmonton Oilers");
        m.put("FLA", "Florida Panthers");
        m.put("LAK", "Los Angeles Kings");
        m.put("MIN", "Minnesota Wild");
        m.put("MTL", "Montréal Canadiens");
        m.put("NJD", "New Jersey Devils");
        m.put("NSH", "Nashville Predators");
        m.put("NYI", "New York Islanders");
        m.put("NYR", "New York Rangers");
        m.put("OTT", "Ottawa Senators");
        m.put("PHI", "Philadelphia Flyers");
        m.put("PIT", "Pittsburgh Penguins");
        m.put("SEA", "Seattle Kraken");
        m.put("SJS", "San Jose Sharks");
        m.put("STL", "St. Louis Blues");
        m.put("TBL", "Tampa Bay Lightning");
        m.put("TOR", "Toronto Maple Leafs");
        m.put("UTA", "Utah Hockey Club");
        m.put("VAN", "Vancouver Canucks");
        m.put("VGK", "Vegas Golden Knights");
        m.put("WPG", "Winnipeg Jets");
        m.put("WSH", "Washington Capitals");
        return m;
    }
}
