package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Top-level shape for {@code /v1/club-schedule/{ABBR}/week/now}. Unlike the
 * league endpoint, this one returns a flat {@code games} array (with each
 * game carrying its own {@code gameDate}) instead of a {@code gameWeek}
 * grouping. {@link ca.ryanmorrison.chatterbox.features.nhl.NhlClient} adapts
 * it into the common {@link ScheduleResponse} shape so the embed builder
 * doesn't need to care which endpoint the data came from.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamWeekResponse(List<Game> games) {

    public List<Game> games() {
        return games == null ? List.of() : games;
    }
}
