package ca.ryanmorrison.chatterbox.features.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error envelope Yahoo returns alongside HTTP 4xx/5xx, e.g.
 * {@code {"code": "Not Found", "description": "No data found, symbol may be delisted"}}.
 * Surfaced for typo-correction messages so the user sees Yahoo's actual
 * complaint rather than just "HTTP 404".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartError(
        @JsonProperty("code") String code,
        @JsonProperty("description") String description) {
}
