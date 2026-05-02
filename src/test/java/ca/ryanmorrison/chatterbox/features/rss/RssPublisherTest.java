package ca.ryanmorrison.chatterbox.features.rss;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEnclosureImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssPublisherTest {

    private static Feed testFeed() {
        return new Feed(1L, 100L, 200L, "https://example.com/feed", "Test Feed",
                7L, 60, Optional.empty(), Optional.empty(), Optional.empty(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static SyndEntry entry(String title, String link, String html, String author) {
        var e = new SyndEntryImpl();
        e.setTitle(title);
        e.setLink(link);
        e.setAuthor(author);
        SyndContent desc = new SyndContentImpl();
        desc.setValue(html);
        desc.setType("text/html");
        e.setDescription(desc);
        return e;
    }

    @Test
    void embedHasLinkedTitleAuthorAndCleanedDescription() {
        var entry = entry("Article Title",
                "https://example.com/articles/1",
                "<p>Hello <b>world</b>! &amp; goodbye.</p>",
                "Jane Doe");
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 1);

        assertEquals("Article Title", embed.getTitle());
        assertEquals("https://example.com/articles/1", embed.getUrl());
        assertNotNull(embed.getAuthor());
        assertEquals("Jane Doe", embed.getAuthor().getName());
        assertEquals("Hello world! & goodbye.", embed.getDescription());
        assertEquals("Test Feed", embed.getFooter().getText());
    }

    @Test
    void footerShowsExtraCount() {
        var entry = entry("t", "https://x", "<p>p</p>", null);
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 3);
        assertTrue(embed.getFooter().getText().contains("+2 more items"),
                () -> "got: " + embed.getFooter().getText());
    }

    @Test
    void singleExtraIsSingular() {
        var entry = entry("t", "https://x", "<p>p</p>", null);
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 2);
        assertTrue(embed.getFooter().getText().contains("+1 more item"),
                () -> "got: " + embed.getFooter().getText());
        assertTrue(!embed.getFooter().getText().contains("items"));
    }

    @Test
    void longDescriptionTruncated() {
        String longText = "x".repeat(2000);
        var entry = entry("t", "https://x", longText, null);
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 1);
        assertEquals(RssPublisher.PREVIEW_MAX_CHARS, embed.getDescription().length());
        assertTrue(embed.getDescription().endsWith("…"));
    }

    @Test
    void blankDescriptionLeavesEmbedDescriptionUnset() {
        var entry = entry("t", "https://x", "", null);
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 1);
        assertNull(embed.getDescription());
    }

    @Test
    void enclosureImageBecomesThumbnail() {
        var entry = entry("t", "https://x", "<p>body</p>", null);
        SyndEnclosure enc = new SyndEnclosureImpl();
        enc.setUrl("https://cdn.example.com/pic.jpg");
        enc.setType("image/jpeg");
        entry.setEnclosures(List.of(enc));
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 1);
        assertEquals("https://cdn.example.com/pic.jpg", embed.getThumbnail().getUrl());
    }

    @Test
    void firstImgTagFallbackThumbnail() {
        var entry = entry("t", "https://x",
                "<p>some <img src=\"https://cdn.example.com/inline.png\"/> text</p>", null);
        MessageEmbed embed = RssPublisher.buildEmbed(testFeed(), entry, 1);
        assertNotNull(embed.getThumbnail());
        assertEquals("https://cdn.example.com/inline.png", embed.getThumbnail().getUrl());
    }
}
