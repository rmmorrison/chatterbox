package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Team(int id,
                   DefaultReference commonName,
                   DefaultReference placeName,
                   DefaultReference placeNameWithPreposition,
                   @JsonProperty("abbrev") String abbreviation,
                   String logo,
                   String darkLogo,
                   boolean awaySplitSquad,
                   int score,
                   String radioLink) {
}
