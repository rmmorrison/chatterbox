package ca.ryanmorrison.chatterbox.features.nhl;

import ca.ryanmorrison.chatterbox.features.nhl.dto.Game;
import ca.ryanmorrison.chatterbox.features.nhl.dto.GameDay;
import ca.ryanmorrison.chatterbox.features.nhl.dto.ScheduleResponse;
import ca.ryanmorrison.chatterbox.features.nhl.dto.Team;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Renders a {@link ScheduleResponse} as a Discord embed with one field per
 * day. Times use Discord's {@code <t:unix:t>} markdown so each viewer sees
 * their local time.
 */
final class NhlEmbedBuilder {

    /** Game states the NHL API uses for in-progress play. */
    private static final Set<String> LIVE_STATES = Set.of("LIVE", "CRIT");

    /** Game states meaning the game has finished. */
    private static final Set<String> FINAL_STATES = Set.of("OFF", "FINAL");

    private static final Color EMBED_COLOR = new Color(0x041E42); // NHL navy
    private static final DateTimeFormatter DAY_HEADING =
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH);

    private NhlEmbedBuilder() {}

    /**
     * @param schedule    the parsed week
     * @param teamAbbrev  null for the league-wide view, otherwise the
     *                    canonical three-letter code that drove the request
     * @return an embed ready to send, or {@code null} if the week contains
     *         no games (caller should report that to the user instead)
     */
    static MessageEmbed build(ScheduleResponse schedule, String teamAbbrev) {
        List<GameDay> daysWithGames = schedule.gameWeek().stream()
                .filter(d -> !d.games().isEmpty())
                .toList();
        if (daysWithGames.isEmpty()) {
            return null;
        }

        EmbedBuilder eb = new EmbedBuilder().setColor(EMBED_COLOR);
        eb.setTitle(title(teamAbbrev));

        for (GameDay day : daysWithGames) {
            String heading = day.date() == null ? "Upcoming" : DAY_HEADING.format(day.date());
            StringJoiner lines = new StringJoiner("\n");
            for (Game game : day.games()) {
                lines.add(renderGame(game));
            }
            eb.addField(heading, lines.toString(), false);
        }
        return eb.build();
    }

    private static String title(String teamAbbrev) {
        if (teamAbbrev == null) return "NHL Schedule — Next 7 Days";
        String canonical = teamAbbrev.toUpperCase(Locale.ROOT);
        return NhlTeams.displayName(canonical)
                .map(name -> name + " — Next 7 Days")
                .orElse(canonical + " — Next 7 Days");
    }

    private static String renderGame(Game game) {
        String matchup = abbrev(game.awayTeam()) + " at " + abbrev(game.homeTeam());
        String state = game.gameState() == null ? "" : game.gameState().toUpperCase(Locale.ROOT);
        if (LIVE_STATES.contains(state)) {
            return matchup + " — Live";
        }
        if (FINAL_STATES.contains(state)) {
            int away = game.awayTeam() == null ? 0 : game.awayTeam().score();
            int home = game.homeTeam() == null ? 0 : game.homeTeam().score();
            return matchup + " — Final " + away + "-" + home;
        }
        if (game.startTimeUtc() != null) {
            return matchup + " — <t:" + game.startTimeUtc().getEpochSecond() + ":t>";
        }
        return matchup;
    }

    private static String abbrev(Team t) {
        if (t == null || t.abbrev() == null || t.abbrev().isBlank()) return "TBD";
        return t.abbrev();
    }
}
