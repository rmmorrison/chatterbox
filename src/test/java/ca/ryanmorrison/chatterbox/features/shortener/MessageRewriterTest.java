package ca.ryanmorrison.chatterbox.features.shortener;

import ca.ryanmorrison.chatterbox.features.shortener.MessageRewriter.Span;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRewriterTest {

    /** A substitution that replaces every shortenable URL with a fixed short URL. */
    private static final Function<String, Optional<String>> SHORTEN_ALL =
            url -> Optional.of("https://x.test/abc123");

    /** A substitution that leaves every URL alone. */
    private static final Function<String, Optional<String>> SHORTEN_NONE = url -> Optional.empty();

    @Test
    void emptyContentProducesNoSpans() {
        assertTrue(MessageRewriter.tokenize("").isEmpty());
    }

    @Test
    void plainTextProducesSingleTextSpan() {
        var spans = MessageRewriter.tokenize("hello world, no url here");
        assertEquals(1, spans.size());
        assertInstanceOf(Span.Text.class, spans.get(0));
        assertEquals("hello world, no url here", ((Span.Text) spans.get(0)).text());
    }

    @Test
    void bareUrlIsExtractedAsItsOwnSpan() {
        var spans = MessageRewriter.tokenize("hey check https://example.com/foo cool");
        assertEquals(3, spans.size());
        assertEquals("hey check ", textOf(spans.get(0)));
        assertEquals("https://example.com/foo", urlOf(spans.get(1)));
        assertEquals(" cool", textOf(spans.get(2)));
    }

    @Test
    void httpAndHttpsBothMatch() {
        assertEquals("http://example.com",
                urlOf(MessageRewriter.tokenize("see http://example.com").get(1)));
        assertEquals("https://example.com",
                urlOf(MessageRewriter.tokenize("see https://example.com").get(1)));
    }

    @Test
    void schemeMatchIsCaseInsensitive() {
        assertEquals("HTTPS://EXAMPLE.COM/X",
                urlOf(MessageRewriter.tokenize("see HTTPS://EXAMPLE.COM/X here").get(1)));
    }

    @Test
    void trailingSentencePunctuationIsTrimmed() {
        var spans = MessageRewriter.tokenize("look at https://example.com/foo.");
        assertEquals("https://example.com/foo", urlOf(spans.get(1)));
        assertEquals(".", textOf(spans.get(2)));
    }

    @Test
    void wikipediaParensAreKeptWhenMatched() {
        var spans = MessageRewriter.tokenize("https://en.wikipedia.org/wiki/Example_(song)");
        assertEquals(1, spans.size());
        assertEquals("https://en.wikipedia.org/wiki/Example_(song)", urlOf(spans.get(0)));
    }

    @Test
    void unmatchedTrailingClosingParenIsTrimmed() {
        var spans = MessageRewriter.tokenize("(see https://example.com/foo)");
        assertEquals("https://example.com/foo", urlOf(spans.get(1)));
        assertEquals(")", textOf(spans.get(2)));
    }

    @Test
    void angleBracketUrlIsOptOut() {
        var spans = MessageRewriter.tokenize("don't shorten <https://really-long.example/path> please");
        // Angle-wrapped URL must be a single text span, not a ShortenableUrl.
        for (Span s : spans) {
            assertTrue(!(s instanceof Span.ShortenableUrl),
                    "angle-bracket URL should not be eligible for shortening");
        }
        assertEquals("don't shorten <https://really-long.example/path> please",
                MessageRewriter.rewrite(spans, SHORTEN_ALL));
    }

    @Test
    void angleBracketWithoutUrlIsNotConsumed() {
        // <foo> is not a URL opt-out — should not swallow surrounding URLs.
        var spans = MessageRewriter.tokenize("<foo> https://example.com/bar");
        // Find the URL span.
        boolean found = spans.stream().anyMatch(s -> s instanceof Span.ShortenableUrl);
        assertTrue(found, "bare URL after non-URL angle brackets should still be eligible");
    }

    @Test
    void inlineCodeSpanProtectsUrls() {
        var spans = MessageRewriter.tokenize("compare `https://example.com/inline` with regular");
        for (Span s : spans) {
            assertTrue(!(s instanceof Span.ShortenableUrl),
                    "URL inside backticks should not be eligible");
        }
    }

    @Test
    void tripleBacktickBlockProtectsUrls() {
        String body = "before ```\nhttps://example.com/inblock\n``` after https://example.com/outside";
        var spans = MessageRewriter.tokenize(body);
        long urlCount = spans.stream().filter(s -> s instanceof Span.ShortenableUrl).count();
        assertEquals(1, urlCount, "only the URL outside the code block should be eligible");
    }

    @Test
    void spoilerProtectsUrls() {
        var spans = MessageRewriter.tokenize("warn ||https://example.com/spoiler|| hidden");
        for (Span s : spans) {
            assertTrue(!(s instanceof Span.ShortenableUrl),
                    "URL inside spoiler should not be eligible");
        }
    }

    @Test
    void markdownLinkIsLeftAlone() {
        var spans = MessageRewriter.tokenize("see [the docs](https://example.com/docs) please");
        for (Span s : spans) {
            assertTrue(!(s instanceof Span.ShortenableUrl),
                    "URL inside markdown link should not be eligible");
        }
    }

    @Test
    void rewriteSubstitutesEachShortenableUrl() {
        var spans = MessageRewriter.tokenize("a https://x.example/aaaa b https://y.example/bbbb c");
        String out = MessageRewriter.rewrite(spans,
                url -> Optional.of(url.contains("x.example") ? "https://s/1" : "https://s/2"));
        assertEquals("a https://s/1 b https://s/2 c", out);
    }

    @Test
    void rewritePassesThroughUrlsWhenSubstitutionEmpty() {
        String body = "a https://x.example/aaaa b https://y.example/bbbb c";
        var spans = MessageRewriter.tokenize(body);
        assertEquals(body, MessageRewriter.rewrite(spans, SHORTEN_NONE));
    }

    @Test
    void multipleStructuresInOneMessageAreTokenizedCorrectly() {
        String body = "outer `code` and ||spoiler|| then [link](https://md.example/x) "
                + "and <https://opt.example/y> and finally https://bare.example/z!";
        var spans = MessageRewriter.tokenize(body);
        long urlCount = spans.stream().filter(s -> s instanceof Span.ShortenableUrl).count();
        assertEquals(1, urlCount, "only the trailing bare URL should be eligible");
        // Confirm the lone shortenable URL has its trailing ! trimmed off.
        spans.stream().filter(s -> s instanceof Span.ShortenableUrl)
                .forEach(s -> assertEquals("https://bare.example/z", urlOf(s)));
    }

    @Test
    void unterminatedCodeBlockFallsBackToTextScanning() {
        // Triple backtick without closer — the URL after it should still be eligible.
        var spans = MessageRewriter.tokenize("```partial https://example.com/bare");
        long urlCount = spans.stream().filter(s -> s instanceof Span.ShortenableUrl).count();
        assertEquals(1, urlCount,
                "unterminated code block shouldn't swallow URLs that follow");
    }

    @Test
    void rewriteRoundTripsUnchangedContent() {
        String body = "no urls here, just text";
        assertEquals(body, MessageRewriter.rewrite(MessageRewriter.tokenize(body), SHORTEN_ALL));
    }

    @Test
    void sameUrlTwiceIsExtractedTwiceForRewriting() {
        var spans = MessageRewriter.tokenize("a https://example.com/x b https://example.com/x c");
        long urlCount = spans.stream().filter(s -> s instanceof Span.ShortenableUrl).count();
        assertEquals(2, urlCount);
    }

    @Test
    void findBareUrlEndStopsAtWhitespace() {
        String body = "https://example.com/foo bar";
        assertEquals(body.indexOf(' '), MessageRewriter.findBareUrlEnd(body, 0));
    }

    private static String textOf(Span s) { return ((Span.Text) s).text(); }
    private static String urlOf(Span s) { return ((Span.ShortenableUrl) s).url(); }
}
