package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * One scheduled game. Field names mirror {@code api-web.nhle.com}'s
 * camel-case keys; we accept both {@code startTimeUTC} (current) and
 * {@code startTimeUtc} (historical) defensively.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Game(
        long id,
        @JsonProperty("startTimeUTC") Instant startTimeUtc,
        String gameState,
        Team awayTeam,
        Team homeTeam) {
}
