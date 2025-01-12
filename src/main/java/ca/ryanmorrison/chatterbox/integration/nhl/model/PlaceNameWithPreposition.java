package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlaceNameWithPreposition(@JsonProperty("default") String defaultPlace,
                                       @JsonProperty("fr") String defaultPlaceFrench) {
}
