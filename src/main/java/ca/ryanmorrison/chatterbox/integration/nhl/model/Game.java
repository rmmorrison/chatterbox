package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record Game(long id,
                   int season,
                   int gameType,
                   DefaultReference venue,
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
                   GameOutcome gameOutcome,
                   WinningGoalie winningGoalie,
                   WinningGoalScorer winningGoalScorer,
                   String threeMinRecap,
                   @JsonProperty("threeMinRecapFr") String threeMinRecapFrench,
                   String condensedGame,
                   String gameCenterLink) {
}
