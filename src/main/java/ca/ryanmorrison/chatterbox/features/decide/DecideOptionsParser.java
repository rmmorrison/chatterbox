package ca.ryanmorrison.chatterbox.features.decide;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Splits a free-form {@code /decide} options string into a list of
 * choices. Two flavours, picked automatically:
 *
 * <ul>
 *   <li><b>"or"-separated</b> — when the input contains the standalone
 *       word {@code or} (case-insensitive) bracketed by whitespace,
 *       split on it. Lets users phrase complex choices naturally:
 *       {@code "say hello or be silent"} → {@code ["say hello", "be silent"]}.</li>
 *   <li><b>Whitespace-separated</b> — fallback for the bare
 *       {@code "pizza tacos sushi"} form.</li>
 * </ul>
 *
 * <p>Each option is trimmed; surrounding punctuation
 * ({@code , . ; : ! ?}) is stripped so {@code "say hello, or be silent"}
 * parses cleanly. Empty options are dropped.
 */
final class DecideOptionsParser {

    /** Matches " or " (any case) bracketed by whitespace, including line breaks. */
    private static final Pattern OR_SEPARATOR = Pattern.compile("(?i)\\s+or\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TRIM_PUNCTUATION = Pattern.compile("^[\\s,.;:!?]+|[\\s,.;:!?]+$");

    private DecideOptionsParser() {}

    /**
     * @return the parsed options, possibly empty if the input was blank or
     *         all-punctuation.
     */
    static List<String> parse(String raw) {
        if (raw == null) return List.of();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return List.of();

        Pattern splitter = OR_SEPARATOR.matcher(trimmed).find()
                ? OR_SEPARATOR
                : WHITESPACE;

        return Arrays.stream(splitter.split(trimmed))
                .map(s -> TRIM_PUNCTUATION.matcher(s).replaceAll(""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
