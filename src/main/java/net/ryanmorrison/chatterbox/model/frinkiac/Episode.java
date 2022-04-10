package net.ryanmorrison.chatterbox.model.frinkiac;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Episode(long id,
                      String key,
                      int season,
                      int episodeNumber,
                      String title,
                      @JsonProperty("director") String directors,
                      @JsonProperty("writer") String writers,
                      String originalAirDate,
                      String wikiLink) {
}
