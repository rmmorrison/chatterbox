package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Venue(@JsonProperty("default") String defaultVenue,
                    String fr) {
}
