package ca.ryanmorrison.chatterbox.features.wiki;

import ca.ryanmorrison.chatterbox.features.wiki.dto.PageSummary;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.Optional;

/**
 * Renders a {@link PageSummary} as a Discord embed.
 *
 * <p>Layout:
 * <ul>
 *   <li>Title: page title, hyperlinked to the article URL.</li>
 *   <li>Description: optional short Wikipedia description on the first line,
 *       a blank line, then the lead paragraph (capped at
 *       {@link #MAX_EXTRACT_LENGTH} chars on a word boundary).</li>
 *   <li>Thumbnail: {@code thumbnail.source} if present.</li>
 *   <li>Footer: "Wikipedia".</li>
 * </ul>
 *
 * <p>For disambiguation pages a small italic hint precedes the extract so
 * readers know to refine the query rather than treat the list as the answer.
 */
final class WikiEmbedBuilder {

    /**
     * Discord's embed-description max is 4096 chars; we keep the extract under
     * 1500 to leave room for the description-line + disambiguation hint and
     * to keep the message scrollable rather than wall-of-text.
     */
    static final int MAX_EXTRACT_LENGTH = 1500;
    static final String DISAMBIGUATION_HINT =
            "_This is a disambiguation page — try a more specific query._";
    private static final Color EMBED_COLOR = new Color(0xCDD2D6); // Wikipedia grey

    private WikiEmbedBuilder() {}

    static MessageEmbed build(PageSummary summary) {
        EmbedBuilder eb = new EmbedBuilder().setColor(EMBED_COLOR);
        eb.setTitle(summary.title(), summary.articleUrl().orElse(null));

        StringBuilder body = new StringBuilder();
        if (summary.isDisambiguation()) {
            body.append(DISAMBIGUATION_HINT).append("\n\n");
        }
        Optional.ofNullable(summary.description())
                .filter(s -> !s.isBlank())
                .ifPresent(d -> body.append("*").append(d.trim()).append("*\n\n"));
        Optional.ofNullable(summary.extract())
                .filter(s -> !s.isBlank())
                .ifPresent(e -> body.append(truncate(e.trim(), MAX_EXTRACT_LENGTH)));
        if (body.length() > 0) eb.setDescription(body.toString());

        if (summary.thumbnail() != null && summary.thumbnail().source() != null
                && !summary.thumbnail().source().isBlank()) {
            eb.setThumbnail(summary.thumbnail().source());
        }

        eb.setFooter("Wikipedia");
        return eb.build();
    }

    /**
     * Truncate to {@code max} chars on a word boundary, appending an ellipsis.
     * Falls back to a hard cut if no whitespace is found in the last quarter
     * of the window — pathological case but covers it cleanly.
     */
    static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max);
        if (cut < max * 3 / 4) cut = max;
        return s.substring(0, cut).stripTrailing() + "…";
    }
}
