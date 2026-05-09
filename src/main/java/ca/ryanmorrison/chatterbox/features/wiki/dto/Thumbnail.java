package ca.ryanmorrison.chatterbox.features.wiki.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Thumbnail(
        String source,
        Integer width,
        Integer height) {
}
