package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameWeek(String date,
                       @JsonProperty("dayAbbrev") String dayAbbreviation,
                       int numberOfGames,
                       List<Game> games) {
}
