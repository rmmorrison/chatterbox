package ca.ryanmorrison.chatterbox.features.shout;

import java.util.regex.Pattern;

/**
 * Decides whether a message is the user "shouting" — i.e. an all-caps message
 * with enough letters to plausibly be intentional.
 *
 * <p>The check strips substrings that don't reflect the author's tone (URLs,
 * Discord mentions, custom emoji) before evaluating case. A message qualifies
 * iff the residue contains at least {@value #MIN_CASED_LETTERS} cased letters
 * and every cased letter is uppercase.
 *
 * <p>Uncased characters (digits, punctuation, scripts without case like CJK)
 * are ignored entirely: they neither count toward the minimum nor disqualify
 * the message. This is what makes "$100" or "🤔🤔🤔" naturally fall below the
 * threshold without special-casing them.
 */
public final class ShoutDetector {

    static final int MIN_CASED_LETTERS = 5;

    private static final Pattern URLS = Pattern.compile(
            "(?i)\\bhttps?://\\S+|\\bwww\\.\\S+");

    private static final Pattern DISCORD_TOKENS = Pattern.compile(
            "<a?:\\w+:\\d+>"     // custom emoji
          + "|<@!?\\d+>"         // user mention
          + "|<@&\\d+>"          // role mention
          + "|<#\\d+>"           // channel mention
          + "|<t:\\d+(?::\\w)?>" // timestamp
    );

    public boolean isShouting(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String residue = DISCORD_TOKENS.matcher(URLS.matcher(raw).replaceAll(" ")).replaceAll(" ");

        int cased = 0;
        int len = residue.length();
        for (int i = 0; i < len; ) {
            int cp = residue.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            // Skip uncased letters (e.g. CJK) — they neither count nor disqualify.
            if (Character.toLowerCase(cp) == cp && Character.toUpperCase(cp) == cp) continue;
            if (!Character.isUpperCase(cp)) return false;
            cased++;
        }
        return cased >= MIN_CASED_LETTERS;
    }
}
