package ca.ryanmorrison.chatterbox.features.nhl;

import ca.ryanmorrison.chatterbox.features.nhl.dto.ScheduleResponse;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handles {@code /nhl schedule [team]} interactions: dispatches to
 * {@link NhlClient} and renders the result via {@link NhlEmbedBuilder}.
 *
 * <p>The team parameter is autocompleted from {@link NhlTeams#abbreviations()}
 * — Discord shows the matching abbreviations as the user types.
 */
final class NhlHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(NhlHandler.class);
    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;

    private final NhlClient client;

    NhlHandler(NhlClient client) {
        this.client = client;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!NhlModule.COMMAND.equals(event.getName())) return;
        if (!NhlModule.SUBCOMMAND_SCHEDULE.equals(event.getSubcommandName())) return;

        var teamOpt = event.getOption(NhlModule.OPTION_TEAM);
        String teamAbbrev = teamOpt == null ? null : teamOpt.getAsString().trim();

        if (teamAbbrev != null && !teamAbbrev.isEmpty() && !NhlTeams.isKnown(teamAbbrev)) {
            event.reply("Unknown team `" + teamAbbrev + "`. Use the autocomplete suggestions for "
                    + "the three-letter abbreviation (e.g. `TOR`, `EDM`).")
                    .setEphemeral(true).queue();
            return;
        }

        // Network call follows — defer so we don't trip Discord's 3s reply window.
        event.deferReply().queue();

        String canonical = teamAbbrev == null || teamAbbrev.isEmpty()
                ? null
                : teamAbbrev.toUpperCase(Locale.ROOT);

        ScheduleResponse schedule;
        try {
            schedule = canonical == null ? client.leagueWeek() : client.teamWeek(canonical);
        } catch (NhlClient.NhlException e) {
            event.getHook().sendMessage("Couldn't load the NHL schedule: " + e.getMessage())
                    .setEphemeral(true).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error fetching NHL schedule", e);
            event.getHook().sendMessage("Something went wrong loading the NHL schedule.")
                    .setEphemeral(true).queue();
            return;
        }

        MessageEmbed embed = NhlEmbedBuilder.build(schedule, canonical);
        if (embed == null) {
            String msg = canonical == null
                    ? "No NHL games scheduled in the next 7 days."
                    : "No upcoming games for " + canonical + " in the next 7 days.";
            event.getHook().sendMessage(msg).setEphemeral(true).queue();
            return;
        }
        event.getHook().sendMessageEmbeds(embed).queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!NhlModule.COMMAND.equals(event.getName())) return;
        if (!NhlModule.OPTION_TEAM.equals(event.getFocusedOption().getName())) return;

        String prefix = event.getFocusedOption().getValue().trim().toUpperCase(Locale.ROOT);
        List<Command.Choice> choices = NhlTeams.abbreviations().stream()
                .filter(abbrev -> prefix.isEmpty() || abbrev.startsWith(prefix))
                .limit(MAX_AUTOCOMPLETE_CHOICES)
                .map(abbrev -> new Command.Choice(
                        abbrev + " — " + NhlTeams.displayName(abbrev).orElse(abbrev),
                        abbrev))
                .collect(Collectors.toList());
        event.replyChoices(choices).queue();
    }
}
