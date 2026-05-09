package ca.ryanmorrison.chatterbox.features.wiki;

import ca.ryanmorrison.chatterbox.features.wiki.dto.ContentUrlSet;
import ca.ryanmorrison.chatterbox.features.wiki.dto.PageSummary;
import ca.ryanmorrison.chatterbox.features.wiki.dto.Thumbnail;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiEmbedBuilderTest {

    private static PageSummary toronto() {
        return new PageSummary(
                "standard",
                "Toronto",
                "Capital city of Ontario, Canada",
                "Toronto is the capital city of the Canadian province of Ontario.",
                new Thumbnail("https://upload.wikimedia.org/thumb.jpg", 320, 213),
                Map.of("desktop", new ContentUrlSet("https://en.wikipedia.org/wiki/Toronto")));
    }

    @Test
    void titleIsHyperlinkedToArticle() {
        MessageEmbed embed = WikiEmbedBuilder.build(toronto());
        assertEquals("Toronto", embed.getTitle());
        assertEquals("https://en.wikipedia.org/wiki/Toronto", embed.getUrl());
    }

    @Test
    void descriptionContainsShortDescriptionAndExtract() {
        MessageEmbed embed = WikiEmbedBuilder.build(toronto());
        String desc = embed.getDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("Capital city of Ontario, Canada"), desc);
        assertTrue(desc.contains("Toronto is the capital city"), desc);
    }

    @Test
    void thumbnailIsSet() {
        MessageEmbed embed = WikiEmbedBuilder.build(toronto());
        assertEquals("https://upload.wikimedia.org/thumb.jpg", embed.getThumbnail().getUrl());
    }

    @Test
    void footerIsWikipedia() {
        MessageEmbed embed = WikiEmbedBuilder.build(toronto());
        assertEquals("Wikipedia", embed.getFooter().getText());
    }

    // ---- disambiguation ----

    @Test
    void disambiguationDescriptionLeadsWithHint() {
        PageSummary s = new PageSummary(
                "disambiguation", "Java", null, "Java may refer to:",
                null, Map.of("desktop", new ContentUrlSet("https://en.wikipedia.org/wiki/Java")));
        MessageEmbed embed = WikiEmbedBuilder.build(s);
        String desc = embed.getDescription();
        assertNotNull(desc);
        assertTrue(desc.startsWith(WikiEmbedBuilder.DISAMBIGUATION_HINT),
                () -> "expected disambiguation hint to lead, got: " + desc);
        assertTrue(desc.contains("Java may refer to:"), () -> desc);
    }

    // ---- defensive paths ----

    @Test
    void missingThumbnailIsNotSet() {
        PageSummary s = new PageSummary(
                "standard", "Toronto", "desc", "extract", null,
                Map.of("desktop", new ContentUrlSet("https://en.wikipedia.org/wiki/Toronto")));
        MessageEmbed embed = WikiEmbedBuilder.build(s);
        assertNull(embed.getThumbnail());
    }

    @Test
    void missingDescriptionStillRendersExtract() {
        PageSummary s = new PageSummary(
                "standard", "Toronto", null, "Just an extract.", null,
                Map.of("desktop", new ContentUrlSet("https://en.wikipedia.org/wiki/Toronto")));
        MessageEmbed embed = WikiEmbedBuilder.build(s);
        assertTrue(embed.getDescription().contains("Just an extract."), embed.getDescription());
    }

    @Test
    void missingArticleUrlStillProducesEmbed() {
        PageSummary s = new PageSummary(
                "standard", "Toronto", "desc", "extract", null, Map.of());
        MessageEmbed embed = WikiEmbedBuilder.build(s);
        assertEquals("Toronto", embed.getTitle());
        assertNull(embed.getUrl());
    }

    @Test
    void emptyDescriptionAndExtractProducesNoDescriptionField() {
        PageSummary s = new PageSummary(
                "standard", "Toronto", "", "", null, Map.of());
        MessageEmbed embed = WikiEmbedBuilder.build(s);
        // Embed itself still builds with title; description should be unset.
        assertNull(embed.getDescription());
    }

    // ---- truncation ----

    @Test
    void shortExtractIsNotTruncated() {
        String s = "Hello world.";
        assertEquals(s, WikiEmbedBuilder.truncate(s, 100));
    }

    @Test
    void longExtractIsTruncatedWithEllipsisOnWordBoundary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append("word ");
        String long200 = sb.toString();
        String truncated = WikiEmbedBuilder.truncate(long200, 50);
        assertTrue(truncated.length() <= 51, () -> "got " + truncated.length() + ": " + truncated);
        assertTrue(truncated.endsWith("…"), truncated);
        // Should end on a word boundary — no orphan partial word before the ellipsis.
        String beforeEllipsis = truncated.substring(0, truncated.length() - 1);
        assertTrue(beforeEllipsis.endsWith("word"), () -> beforeEllipsis);
    }

    @Test
    void truncationFallsBackToHardCutWhenNoLateSpace() {
        // Pathological: a single very long word with no whitespace late in the
        // window. We hard-cut at max rather than scanning back so far we
        // truncate to almost nothing.
        String word = "a".repeat(200);
        String truncated = WikiEmbedBuilder.truncate(word, 50);
        assertEquals("a".repeat(50) + "…", truncated);
    }
}
