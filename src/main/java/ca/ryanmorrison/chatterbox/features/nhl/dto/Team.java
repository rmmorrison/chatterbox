package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One side of a scheduled game. {@code score} is only meaningful once the
 * game is in progress or final; Jackson defaults primitives to zero, so the
 * caller should consult {@link Game#gameState()} before reading it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Team(String abbrev, int score) {
}
