package ca.ryanmorrison.chatterbox.features.format;

import java.util.regex.Pattern;

/**
 * Available text styles for {@code /format}. Each carries:
 * <ul>
 *   <li>a slash-command choice value (lowercase identifier the user picks),</li>
 *   <li>a human-readable label shown in the choice picker,</li>
 *   <li>the transform itself.</li>
 * </ul>
 *
 * <p>Adding a new style is a one-liner: append an enum value with its
 * transform. {@code FormatModule} reflects the enum into Discord choices
 * automatically.
 */
enum TextStyle {

    CLAP("clap", "Clap 👏", TextStyle::clap),
    SPONGECASE("spongecase", "SpOnGeCaSe", TextStyle::spongecase);

    private final String value;
    private final String label;
    private final java.util.function.UnaryOperator<String> transform;

    TextStyle(String value, String label, java.util.function.UnaryOperator<String> transform) {
        this.value = value;
        this.label = label;
        this.transform = transform;
    }

    String value() { return value; }
    String label() { return label; }

    String apply(String text) {
        return transform.apply(text);
    }

    static TextStyle fromValue(String value) {
        for (TextStyle s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown style: " + value);
    }

    // -- transforms ---------------------------------------------------------

    /**
     * Inserts {@code 👏} between whitespace-separated words. Collapses runs
     * of whitespace to a single clap so {@code "make  it stop"} doesn't
     * yield doubled punctuation.
     */
    static String clap(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return WHITESPACE.matcher(trimmed).replaceAll(" 👏 ");
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Alternates letter case across the whole string, advancing only on
     * letters so non-letter characters (spaces, punctuation, digits, emoji)
     * pass through without disturbing the rhythm:
     *
     * <pre>
     *   "alternating caps" → "aLtErNaTiNg CaPs"
     * </pre>
     *
     * Starts lowercase, matching the canonical Wikipedia example.
     */
    static String spongecase(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        int letterIndex = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp)) {
                int transformed = (letterIndex % 2 == 0)
                        ? Character.toLowerCase(cp)
                        : Character.toUpperCase(cp);
                out.appendCodePoint(transformed);
                letterIndex++;
            } else {
                out.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
