package ca.ryanmorrison.chatterbox.features.frinkiac.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EpisodeInfo(
        @JsonProperty("Id") long id,
        @JsonProperty("Key") String key,
        @JsonProperty("Season") int season,
        @JsonProperty("EpisodeNumber") int episodeNumber,
        @JsonProperty("Title") String title,
        @JsonProperty("Director") String director,
        @JsonProperty("Writer") String writer,
        @JsonProperty("OriginalAirDate") String originalAirDate,
        @JsonProperty("WikiLink") String wikiLink) {
}
