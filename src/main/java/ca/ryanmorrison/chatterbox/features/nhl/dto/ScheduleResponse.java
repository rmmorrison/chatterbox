package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Top-level shape for {@code /v1/schedule/now} and
 * {@code /v1/club-schedule/{ABBR}/week/now}. Both endpoints return a
 * {@code gameWeek} array; only that field is interesting here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleResponse(List<GameDay> gameWeek) {

    public List<GameDay> gameWeek() {
        return gameWeek == null ? List.of() : gameWeek;
    }
}
