package ca.ryanmorrison.chatterbox.features.wiki.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One hit from {@code /w/rest.php/v1/search/page}.
 *
 * <p>{@code key} is the URL slug (used to fetch the summary), {@code title}
 * is the display title. Other fields (excerpt, thumbnail, description) exist
 * on the response but we only need the title to chain into a summary call.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchHit(String key, String title) {
}
