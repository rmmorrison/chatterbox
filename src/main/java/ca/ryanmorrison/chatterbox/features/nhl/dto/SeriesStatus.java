package ca.ryanmorrison.chatterbox.features.nhl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Playoff series context attached to a game, when present. Regular season
 * games omit this field entirely. {@code topSeedTeamAbbrev} /
 * {@code bottomSeedTeamAbbrev} are only populated once the matchup is set
 * (i.e. once both clubs are known) — earlier games in a bracket may report
 * wins without the abbreviations, so consumers should treat them as
 * optional.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeriesStatus(
        String seriesTitle,
        String topSeedTeamAbbrev,
        int topSeedWins,
        String bottomSeedTeamAbbrev,
        int bottomSeedWins) {
}
