package ca.ryanmorrison.chatterbox.features.frinkiac.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One hit from {@code /api/search}. Frinkiac uses PascalCase keys; the
 * record components are camelCase to stay idiomatic Java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResult(
        @JsonProperty("Id") long id,
        @JsonProperty("Episode") String episode,
        @JsonProperty("Timestamp") long timestamp,
        @JsonProperty("Content") String content,
        @JsonProperty("Title") String title) {
}
