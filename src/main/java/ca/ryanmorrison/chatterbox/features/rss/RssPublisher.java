package ca.ryanmorrison.chatterbox.features.rss;

import com.rometools.rome.feed.module.Module;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndPerson;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Builds and posts a single embed for the most recent item in a feed,
 * with a "+N more items" suffix when more than one new item is in the batch.
 */
final class RssPublisher {

    static final int PREVIEW_MAX_CHARS = 400;
    private static final Color EMBED_COLOR = new Color(0xE67E22); // soft orange — RSS-ish

    private RssPublisher() {}

    /**
     * Send an embed for {@code latest} into {@code channel}. {@code newCount}
     * is the total number of new items in this batch including {@code latest};
     * the footer shows the surplus when {@code newCount > 1}.
     */
    static void post(TextChannel channel, Feed feed, SyndEntry latest, int newCount) {
        channel.sendMessageEmbeds(buildEmbed(feed, latest, newCount)).queue();
    }

    static MessageEmbed buildEmbed(Feed feed, SyndEntry latest, int newCount) {
        EmbedBuilder eb = new EmbedBuilder().setColor(EMBED_COLOR);

        String title = stringOrFallback(latest.getTitle(), "(untitled)");
        String link = latest.getLink();
        eb.setTitle(truncate(title, MessageEmbed.TITLE_MAX_LENGTH), nullIfBlank(link));

        authorName(latest).ifPresent(eb::setAuthor);
        thumbnail(latest).ifPresent(eb::setThumbnail);

        String preview = preview(latest);
        if (!preview.isEmpty()) {
            eb.setDescription(preview);
        }

        OffsetDateTime published = publishedAt(latest);
        if (published != null) {
            eb.setTimestamp(published);
        }

        String footer = feed.title();
        int extra = newCount - 1;
        if (extra > 0) {
            footer += "  ·  +" + extra + " more " + (extra == 1 ? "item" : "items");
        }
        eb.setFooter(truncate(footer, MessageEmbed.TEXT_MAX_LENGTH));

        return eb.build();
    }

    // ---- helpers ----

    private static Optional<String> authorName(SyndEntry entry) {
        if (entry.getAuthor() != null && !entry.getAuthor().isBlank()) {
            return Optional.of(truncate(entry.getAuthor().trim(), MessageEmbed.AUTHOR_MAX_LENGTH));
        }
        List<SyndPerson> authors = entry.getAuthors();
        if (authors != null) {
            for (SyndPerson p : authors) {
                if (p.getName() != null && !p.getName().isBlank()) {
                    return Optional.of(truncate(p.getName().trim(), MessageEmbed.AUTHOR_MAX_LENGTH));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find a usable thumbnail URL. Searches in order: media:thumbnail,
     * media:content image, image enclosure, first &lt;img&gt; tag in any
     * description/content body.
     */
    private static Optional<String> thumbnail(SyndEntry entry) {
        Optional<String> media = mediaThumbnail(entry);
        if (media.isPresent()) return media;

        if (entry.getEnclosures() != null) {
            for (SyndEnclosure enc : entry.getEnclosures()) {
                String type = enc.getType();
                if (enc.getUrl() != null && type != null && type.toLowerCase().startsWith("image/")) {
                    return Optional.of(enc.getUrl());
                }
            }
        }

        // Fall back to scanning HTML bodies for the first <img>.
        for (String html : htmlBodies(entry)) {
            Element img = Jsoup.parse(html).selectFirst("img[src]");
            if (img != null) {
                String src = img.attr("abs:src").isBlank() ? img.attr("src") : img.attr("abs:src");
                if (!src.isBlank()) return Optional.of(src);
            }
        }
        return Optional.empty();
    }

    /**
     * Look up the {@code media} module without importing rome-modules-mediarss
     * at compile time — Rome already pulls it transitively, but using
     * reflection-safe URI lookup keeps this resilient if it's ever excluded.
     */
    private static Optional<String> mediaThumbnail(SyndEntry entry) {
        Module mod = entry.getModule("http://search.yahoo.com/mrss/");
        if (mod == null) return Optional.empty();
        try {
            // MediaEntryModule#getMediaContents / #getMetadata#getThumbnail
            var clazz = mod.getClass();
            var mc = clazz.getMethod("getMediaContents").invoke(mod);
            if (mc instanceof Object[] arr) {
                for (Object content : arr) {
                    var ref = content.getClass().getMethod("getReference").invoke(content);
                    if (ref != null) {
                        String url = ref.toString();
                        // Heuristic: only use if it looks like an image
                        if (url.matches("(?i).+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$")) {
                            return Optional.of(url);
                        }
                    }
                }
            }
            var meta = clazz.getMethod("getMetadata").invoke(mod);
            if (meta != null) {
                var thumbs = meta.getClass().getMethod("getThumbnail").invoke(meta);
                if (thumbs instanceof Object[] tarr && tarr.length > 0) {
                    var url = tarr[0].getClass().getMethod("getUrl").invoke(tarr[0]);
                    if (url != null) return Optional.of(url.toString());
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Module shape changed; fall through.
        }
        return Optional.empty();
    }

    private static List<String> htmlBodies(SyndEntry entry) {
        java.util.ArrayList<String> bodies = new java.util.ArrayList<>(2);
        if (entry.getContents() != null) {
            for (SyndContent c : entry.getContents()) {
                if (c.getValue() != null && !c.getValue().isBlank()) bodies.add(c.getValue());
            }
        }
        if (entry.getDescription() != null && entry.getDescription().getValue() != null
                && !entry.getDescription().getValue().isBlank()) {
            bodies.add(entry.getDescription().getValue());
        }
        return bodies;
    }

    /**
     * Plain-text preview built from the first available body (description for
     * RSS, content for Atom). HTML stripped, whitespace collapsed, truncated
     * to {@link #PREVIEW_MAX_CHARS} with an ellipsis if it overflows.
     */
    private static String preview(SyndEntry entry) {
        String html = null;
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            html = entry.getDescription().getValue();
        } else if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            html = entry.getContents().get(0).getValue();
        }
        if (html == null || html.isBlank()) return "";
        Document doc = Jsoup.parse(html);
        String text = doc.text();              // strip tags, decode entities
        text = text.replaceAll("\\s+", " ").trim();
        return truncate(text, PREVIEW_MAX_CHARS);
    }

    private static OffsetDateTime publishedAt(SyndEntry entry) {
        if (entry.getPublishedDate() != null) {
            return entry.getPublishedDate().toInstant().atOffset(ZoneOffset.UTC);
        }
        if (entry.getUpdatedDate() != null) {
            return entry.getUpdatedDate().toInstant().atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private static String stringOrFallback(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
