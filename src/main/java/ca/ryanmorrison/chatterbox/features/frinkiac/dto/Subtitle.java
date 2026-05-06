package ca.ryanmorrison.chatterbox.features.frinkiac.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Subtitle(
        @JsonProperty("Id") long id,
        @JsonProperty("RepresentativeTimestamp") long representativeTimestamp,
        @JsonProperty("Episode") String episode,
        @JsonProperty("StartTimestamp") long startTimestamp,
        @JsonProperty("EndTimestamp") long endTimestamp,
        @JsonProperty("Content") String content,
        @JsonProperty("Language") String language) {
}
