package ca.ryanmorrison.chatterbox.features.wiki.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

/**
 * Response shape from {@code /api/rest_v1/page/summary/{title}}.
 *
 * <p>Field selection: only what the embed renders. The API returns much more
 * (revision, timestamp, namespace, languagelinks, …) but we ignore unknown
 * fields so the bot doesn't break when Wikipedia adds something.
 *
 * <p>{@link #type()} drives the disambiguation branch — values are
 * {@code "standard"}, {@code "disambiguation"}, {@code "no-extract"}, and
 * a handful of error-shaped strings on 404 (e.g.
 * {@code "https://mediawiki.org/wiki/HyperSwitch/errors/not_found"}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageSummary(
        String type,
        String title,
        String description,
        String extract,
        Thumbnail thumbnail,
        @JsonProperty("content_urls") Map<String, ContentUrlSet> contentUrls) {

    public static final String TYPE_DISAMBIGUATION = "disambiguation";

    public Optional<String> articleUrl() {
        if (contentUrls == null) return Optional.empty();
        ContentUrlSet desktop = contentUrls.get("desktop");
        if (desktop == null || desktop.page() == null) return Optional.empty();
        return Optional.of(desktop.page());
    }

    public boolean isDisambiguation() {
        return TYPE_DISAMBIGUATION.equalsIgnoreCase(type);
    }
}
