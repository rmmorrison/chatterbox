package ca.ryanmorrison.chatterbox.features.frinkiac.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FrameRef(
        @JsonProperty("Id") long id,
        @JsonProperty("Episode") String episode,
        @JsonProperty("Timestamp") long timestamp) {
}
