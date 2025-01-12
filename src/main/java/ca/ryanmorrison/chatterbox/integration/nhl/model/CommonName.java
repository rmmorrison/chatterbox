package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CommonName(@JsonProperty("default") String defaultName,
                         @JsonProperty("fr") String frenchName) {
}
