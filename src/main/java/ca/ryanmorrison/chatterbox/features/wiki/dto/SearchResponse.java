package ca.ryanmorrison.chatterbox.features.wiki.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Top-level shape of {@code /w/rest.php/v1/search/page}: just {@code pages}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(List<SearchHit> pages) {

    public List<SearchHit> hits() {
        return pages == null ? List.of() : pages;
    }
}
