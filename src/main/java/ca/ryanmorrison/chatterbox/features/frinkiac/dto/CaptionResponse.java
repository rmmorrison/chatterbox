package ca.ryanmorrison.chatterbox.features.frinkiac.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response shape for {@code /api/caption?e=...&t=...}: episode metadata,
 * the requested frame, and the subtitles whose interval contains or
 * neighbours the frame timestamp. The website joins these with newlines
 * to display the on-screen caption beneath each frame.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptionResponse(
        @JsonProperty("Episode") EpisodeInfo episode,
        @JsonProperty("Frame") FrameRef frame,
        @JsonProperty("Subtitles") List<Subtitle> subtitles,
        @JsonProperty("MinTimestamp") long minTimestamp,
        @JsonProperty("MaxTimestamp") long maxTimestamp) {
}
