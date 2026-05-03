package ca.ryanmorrison.chatterbox.features.shortener;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGraphScraperTest {

    @Test
    void extractsCanonicalOgTags() {
        var doc = Jsoup.parse("""
                <html><head>
                  <title>Page Title</title>
                  <meta property="og:title" content="OG Title">
                  <meta property="og:description" content="OG Description">
                  <meta property="og:image" content="https://example.com/img.png">
                  <meta property="og:site_name" content="Example">
                </head><body></body></html>""");
        OpenGraphMetadata m = OpenGraphScraper.extract(doc);
        assertEquals("OG Title", m.title());
        assertEquals("OG Description", m.description());
        assertEquals("https://example.com/img.png", m.image());
        assertEquals("Example", m.siteName());
    }

    @Test
    void fallsBackToTwitterCardsThenHtmlBasics() {
        var doc = Jsoup.parse("""
                <html><head>
                  <title>Doc Title</title>
                  <meta name="twitter:title" content="TW Title">
                  <meta name="twitter:description" content="TW Desc">
                  <meta name="twitter:image" content="https://example.com/tw.png">
                  <meta name="description" content="HTML Desc">
                </head><body></body></html>""");
        OpenGraphMetadata m = OpenGraphScraper.extract(doc);
        assertEquals("TW Title", m.title());
        assertEquals("TW Desc", m.description());
        assertEquals("https://example.com/tw.png", m.image());
        assertNull(m.siteName());
    }

    @Test
    void usesDocumentTitleAndMetaDescriptionWhenNoCardsPresent() {
        var doc = Jsoup.parse("""
                <html><head>
                  <title>Plain Title</title>
                  <meta name="description" content="Plain description.">
                </head><body></body></html>""");
        OpenGraphMetadata m = OpenGraphScraper.extract(doc);
        assertEquals("Plain Title", m.title());
        assertEquals("Plain description.", m.description());
        assertNull(m.image());
    }

    @Test
    void emptyDocumentYieldsEmptyMetadata() {
        var doc = Jsoup.parse("<html><head></head><body></body></html>");
        OpenGraphMetadata m = OpenGraphScraper.extract(doc);
        assertTrue(m.isEmpty());
    }

    @Test
    void truncatesOverlongDescription() {
        String huge = "x".repeat(OpenGraphScraper.MAX_DESCRIPTION_LEN + 500);
        var doc = Jsoup.parse("<html><head><meta property=\"og:description\" content=\"" + huge + "\"></head></html>");
        OpenGraphMetadata m = OpenGraphScraper.extract(doc);
        assertEquals(OpenGraphScraper.MAX_DESCRIPTION_LEN, m.description().length());
    }

    @Test
    void blankContentIsIgnored() {
        var doc = Jsoup.parse("""
                <html><head>
                  <meta property="og:title" content="   ">
                  <title>Real Title</title>
                </head></html>""");
        OpenGraphMetadata m = OpenGraphScraper.extract(doc);
        assertEquals("Real Title", m.title());
    }

    @Test
    void rejectsLoopbackUrl() {
        assertFalse(OpenGraphScraper.isPublicUrl("http://127.0.0.1/"));
        assertFalse(OpenGraphScraper.isPublicUrl("http://localhost/"));
        assertFalse(OpenGraphScraper.isPublicUrl("http://[::1]/"));
    }

    @Test
    void rejectsRfc1918Ranges() {
        assertFalse(OpenGraphScraper.isPublicUrl("http://10.0.0.1/"));
        assertFalse(OpenGraphScraper.isPublicUrl("http://192.168.1.1/"));
        assertFalse(OpenGraphScraper.isPublicUrl("http://172.16.0.1/"));
    }

    @Test
    void rejectsLinkLocalAndMulticast() {
        assertFalse(OpenGraphScraper.isPublicUrl("http://169.254.169.254/"));
        assertFalse(OpenGraphScraper.isPublicUrl("http://224.0.0.1/"));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertFalse(OpenGraphScraper.isPublicUrl("file:///etc/passwd"));
        assertFalse(OpenGraphScraper.isPublicUrl("ftp://example.com/"));
        assertFalse(OpenGraphScraper.isPublicUrl("gopher://example.com/"));
    }

    @Test
    void rejectsMissingHost() {
        assertFalse(OpenGraphScraper.isPublicUrl("http:///"));
        assertFalse(OpenGraphScraper.isPublicUrl("not a url"));
    }
}
