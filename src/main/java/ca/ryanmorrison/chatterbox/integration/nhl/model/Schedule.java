package ca.ryanmorrison.chatterbox.integration.nhl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Schedule(String nextStartDate,
                       String previousStartDate,
                       List<GameWeek> gameWeek,
                       String preSeasonStartDate,
                       String regularSeasonStartDate,
                       String regularSeasonEndDate,
                       String playoffEndDate,
                       int numberOfGames) {
}
