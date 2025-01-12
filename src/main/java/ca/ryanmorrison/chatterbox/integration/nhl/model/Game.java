package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record Game(long id,
                   int season,
                   int gameType,
                   Venue venue,
                   boolean neutralSite,
                   Instant startTimeUTC,
                   String easternUTCOffset,
                   String venueUTCOffset,
                   String venueTimezone,
                   String gameState,
                   String gameScheduleState,
                   @JsonProperty("tvBroadcasts") List<TVBroadcast> TVBroadcasts,
                   Team awayTeam,
                   Team homeTeam,
                   PeriodDescriptor periodDescriptor,
                   String ticketsLink,
                   @JsonProperty("ticketsLinkFr") String ticketsLinkFrench,
                   String gameCenterLink) {
}