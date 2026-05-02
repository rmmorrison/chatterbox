package ca.ryanmorrison.chatterbox.features.nhl;

import ca.ryanmorrison.chatterbox.features.nhl.dto.Game;
import ca.ryanmorrison.chatterbox.features.nhl.dto.GameDay;
import ca.ryanmorrison.chatterbox.features.nhl.dto.ScheduleResponse;
import ca.ryanmorrison.chatterbox.features.nhl.dto.Team;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NhlEmbedBuilderTest {

    private static Game game(String away, String home, String state, Instant start, int awayScore, int homeScore) {
        return new Game(1L, null, start, state, new Team(away, awayScore), new Team(home, homeScore));
    }

    @Test
    void buildsOneFieldPerDayWithGames() {
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of(
                        game("EDM", "TOR", "FUT", Instant.parse("2026-05-02T23:00:00Z"), 0, 0))),
                new GameDay(LocalDate.of(2026, 5, 3), List.of(
                        game("BOS", "MTL", "FUT", Instant.parse("2026-05-03T23:00:00Z"), 0, 0)))));

        MessageEmbed embed = NhlEmbedBuilder.build(resp, null);
        assertNotNull(embed);
        assertEquals("NHL Schedule — Next 7 Days", embed.getTitle());
        assertEquals(2, embed.getFields().size());
        assertEquals("Saturday, May 2", embed.getFields().get(0).getName());
        assertTrue(embed.getFields().get(0).getValue().contains("EDM at TOR"),
                () -> "got: " + embed.getFields().get(0).getValue());
    }

    @Test
    void liveGameRendersAsLive() {
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of(
                        game("EDM", "TOR", "LIVE", Instant.parse("2026-05-02T23:00:00Z"), 1, 2))),
                new GameDay(LocalDate.of(2026, 5, 3), List.of(
                        game("BOS", "MTL", "CRIT", Instant.parse("2026-05-03T01:00:00Z"), 3, 3)))));

        MessageEmbed embed = NhlEmbedBuilder.build(resp, null);
        assertEquals("EDM at TOR — Live", embed.getFields().get(0).getValue());
        assertEquals("BOS at MTL — Live", embed.getFields().get(1).getValue());
    }

    @Test
    void finalGameShowsScore() {
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of(
                        game("EDM", "TOR", "FINAL", Instant.parse("2026-05-02T23:00:00Z"), 4, 2)))));

        MessageEmbed embed = NhlEmbedBuilder.build(resp, null);
        assertEquals("EDM at TOR — Final 4-2", embed.getFields().get(0).getValue());
    }

    @Test
    void futureGameUsesDiscordTimestamp() {
        Instant start = Instant.parse("2026-05-02T23:00:00Z");
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of(
                        game("EDM", "TOR", "FUT", start, 0, 0)))));

        MessageEmbed embed = NhlEmbedBuilder.build(resp, null);
        String expected = "EDM at TOR — <t:" + start.getEpochSecond() + ":t>";
        assertEquals(expected, embed.getFields().get(0).getValue());
    }

    @Test
    void teamFilteredTitleUsesFullName() {
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of(
                        game("EDM", "TOR", "FUT", Instant.parse("2026-05-02T23:00:00Z"), 0, 0)))));

        MessageEmbed embed = NhlEmbedBuilder.build(resp, "TOR");
        assertEquals("Toronto Maple Leafs — Next 7 Days", embed.getTitle());
    }

    @Test
    void emptyWeekReturnsNullSoCallerCanReportInstead() {
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of()),
                new GameDay(LocalDate.of(2026, 5, 3), List.of())));
        assertNull(NhlEmbedBuilder.build(resp, null));
    }

    @Test
    void daysWithoutGamesAreOmittedFromEmbed() {
        ScheduleResponse resp = new ScheduleResponse(List.of(
                new GameDay(LocalDate.of(2026, 5, 2), List.of()),
                new GameDay(LocalDate.of(2026, 5, 3), List.of(
                        game("BOS", "MTL", "FUT", Instant.parse("2026-05-03T23:00:00Z"), 0, 0)))));

        MessageEmbed embed = NhlEmbedBuilder.build(resp, null);
        assertEquals(1, embed.getFields().size());
        assertEquals("Sunday, May 3", embed.getFields().get(0).getName());
    }
}
