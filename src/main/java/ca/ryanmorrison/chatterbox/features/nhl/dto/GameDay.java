package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/** A single day within the seven-day {@code gameWeek}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameDay(LocalDate date, List<Game> games) {

    public List<Game> games() {
        return games == null ? List.of() : games;
    }
}
