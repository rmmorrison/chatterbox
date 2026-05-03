package ca.ryanmorrison.chatterbox.features.shortener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pure parser for the auto-shortener: walks a message body, splits it into
 * literal text and bare-URL spans, and reassembles the body with chosen URLs
 * substituted. No side effects — testable in isolation.
 *
 * <h2>What gets skipped</h2>
 * URLs inside the following structures are emitted as part of a literal text
 * span and never offered to the substitution function, so user-formatted
 * content is left intact:
 * <ul>
 *   <li>Triple-backtick code blocks ({@code ```...```})</li>
 *   <li>Single-backtick code spans ({@code `...`})</li>
 *   <li>Spoiler tags ({@code ||...||})</li>
 *   <li>Markdown links ({@code [text](url)}) — the URL is already labelled</li>
 *   <li>Angle-bracket-wrapped URLs ({@code <https://...>}) — the user opted out
 *       (mirrors Discord's "don't auto-embed" convention)</li>
 * </ul>
 *
 * <h2>Bare URL boundaries</h2>
 * A URL ends at the first whitespace or after trailing punctuation that
 * couldn't reasonably be part of the URL. Trailing {@code )}/{@code ]}/{@code }}
 * are kept only when a matching opener appears earlier in the URL (so
 * Wikipedia-style {@code https://en.wikipedia.org/wiki/Foo_(bar)} works).
 */
final class MessageRewriter {

    /** A piece of the message: either inert text or a candidate URL. */
    sealed interface Span {
        record Text(String text) implements Span {}
        record ShortenableUrl(String url) implements Span {}
    }

    private MessageRewriter() {}

    static List<Span> tokenize(String content) {
        List<Span> spans = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int i = 0;
        int n = content.length();

        while (i < n) {
            // Triple-backtick code block — outermost so it wins over single backtick.
            if (content.startsWith("```", i)) {
                int end = content.indexOf("```", i + 3);
                if (end >= 0) {
                    flushBuffer(spans, buf);
                    spans.add(new Span.Text(content.substring(i, end + 3)));
                    i = end + 3;
                    continue;
                }
            }
            char c = content.charAt(i);

            // Single-backtick code span.
            if (c == '`') {
                int end = content.indexOf('`', i + 1);
                if (end >= 0) {
                    flushBuffer(spans, buf);
                    spans.add(new Span.Text(content.substring(i, end + 1)));
                    i = end + 1;
                    continue;
                }
            }

            // Spoiler.
            if (c == '|' && content.startsWith("||", i)) {
                int end = content.indexOf("||", i + 2);
                if (end >= 0) {
                    flushBuffer(spans, buf);
                    spans.add(new Span.Text(content.substring(i, end + 2)));
                    i = end + 2;
                    continue;
                }
            }

            // Markdown link [text](url) — keep the whole structure as text.
            if (c == '[') {
                int closeBracket = content.indexOf(']', i + 1);
                if (closeBracket >= 0
                        && closeBracket + 1 < n
                        && content.charAt(closeBracket + 1) == '(') {
                    int closeParen = content.indexOf(')', closeBracket + 2);
                    if (closeParen >= 0) {
                        flushBuffer(spans, buf);
                        spans.add(new Span.Text(content.substring(i, closeParen + 1)));
                        i = closeParen + 1;
                        continue;
                    }
                }
            }

            // Angle-bracket URL — opt-out wrapper.
            if (c == '<') {
                int closeAngle = content.indexOf('>', i + 1);
                if (closeAngle >= 0) {
                    String inside = content.substring(i + 1, closeAngle);
                    if (looksLikeUrl(inside)) {
                        flushBuffer(spans, buf);
                        spans.add(new Span.Text(content.substring(i, closeAngle + 1)));
                        i = closeAngle + 1;
                        continue;
                    }
                }
            }

            // Bare HTTP(S) URL.
            if ((c == 'h' || c == 'H')
                    && (content.regionMatches(true, i, "http://", 0, 7)
                            || content.regionMatches(true, i, "https://", 0, 8))) {
                int urlEnd = findBareUrlEnd(content, i);
                if (urlEnd > i) {
                    flushBuffer(spans, buf);
                    spans.add(new Span.ShortenableUrl(content.substring(i, urlEnd)));
                    i = urlEnd;
                    continue;
                }
            }

            buf.append(c);
            i++;
        }
        flushBuffer(spans, buf);
        return spans;
    }

    /**
     * Reassembles a tokenised message with each {@code ShortenableUrl} optionally
     * rewritten via the supplied function. Returning {@link Optional#empty()}
     * means "leave this URL alone".
     */
    static String rewrite(List<Span> spans, Function<String, Optional<String>> substitution) {
        StringBuilder out = new StringBuilder();
        for (Span span : spans) {
            switch (span) {
                case Span.Text(String text) -> out.append(text);
                case Span.ShortenableUrl(String url) ->
                        out.append(substitution.apply(url).orElse(url));
            }
        }
        return out.toString();
    }

    private static void flushBuffer(List<Span> spans, StringBuilder buf) {
        if (buf.isEmpty()) return;
        spans.add(new Span.Text(buf.toString()));
        buf.setLength(0);
    }

    private static boolean looksLikeUrl(String s) {
        return s.regionMatches(true, 0, "http://", 0, 7)
                || s.regionMatches(true, 0, "https://", 0, 8);
    }

    /**
     * Finds the end of a bare URL starting at {@code start}. Consumes
     * non-whitespace, then trims trailing punctuation that was almost
     * certainly part of the surrounding sentence rather than the URL.
     */
    static int findBareUrlEnd(String content, int start) {
        int i = start;
        int n = content.length();
        while (i < n && !Character.isWhitespace(content.charAt(i))) {
            i++;
        }
        while (i > start && shouldTrim(content.charAt(i - 1), content.substring(start, i))) {
            i--;
        }
        return i;
    }

    private static boolean shouldTrim(char c, String urlSoFar) {
        return switch (c) {
            case '.', ',', '!', '?', ';', ':', '"', '\'' -> true;
            case ')' -> urlSoFar.indexOf('(') < 0;
            case ']' -> urlSoFar.indexOf('[') < 0;
            case '}' -> urlSoFar.indexOf('{') < 0;
            default -> false;
        };
    }
}
