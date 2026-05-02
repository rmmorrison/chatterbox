package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One scheduled game. Field names mirror {@code api-web.nhle.com}'s
 * camel-case keys. {@code gameDate} is populated on the team-week endpoint
 * (which returns a flat games list) and used by the client to group games
 * by day. The league-week endpoint omits it, but games there are already
 * grouped by their containing {@link GameDay}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Game(
        long id,
        LocalDate gameDate,
        @JsonProperty("startTimeUTC") Instant startTimeUtc,
        String gameState,
        Team awayTeam,
        Team homeTeam) {
}

