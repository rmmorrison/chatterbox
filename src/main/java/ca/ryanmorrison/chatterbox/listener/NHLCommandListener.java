package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.NHLConstants;
import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.integration.nhl.model.Game;
import ca.ryanmorrison.chatterbox.integration.nhl.model.GameWeek;
import ca.ryanmorrison.chatterbox.integration.nhl.model.Schedule;
import ca.ryanmorrison.chatterbox.integration.nhl.model.TVBroadcast;
import ca.ryanmorrison.chatterbox.integration.nhl.service.NHLService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class NHLCommandListener extends FormattedListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final NHLService service;

    public NHLCommandListener(@Autowired NHLService service) {
        this.service = service;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.getName().equals(NHLConstants.SCHEDULE_COMMAND_NAME)) return;

        event.deferReply().setEphemeral(true).queue();

        Schedule schedule;
        try {
            schedule = service.getCurrentWeekSchedule();
        } catch (IOException | InterruptedException e) {
            log.error("An error occurred while fetching the current day's NHL schedule", e);
            event.getHook().sendMessageEmbeds(
                    buildErrorResponse("An error occurred while fetching the current day's NHL schedule.")
            ).queue();
            return;
        }

        if (schedule == null || schedule.gameWeek().isEmpty()) {
            event.getHook().sendMessageEmbeds(
                    buildWarningResponse("There are no games scheduled for today.")
            ).queue();
            return;
        }

        event.getHook().sendMessageEmbeds(buildGamesEmbeds(schedule)).queue();
    }

    private List<MessageEmbed> buildGamesEmbeds(Schedule schedule) {
        GameWeek day = schedule.gameWeek().getFirst(); // we only queried the current day

        return day.games().stream()
                .map(this::mapGameToEmbed)
                .toList();
    }

    private MessageEmbed mapGameToEmbed(Game game) {
        LocalDateTime localStart = game.startTimeUTC().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String startValue = DateTimeFormatter.ofPattern("h:mm a z").format(localStart.atZone(ZoneId.systemDefault()));

        String canadianBroadcasts = game.TVBroadcasts().stream()
                .filter(broadcast -> broadcast.countryCode().equals("CA"))
                .map(TVBroadcast::network)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Not available");

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(String.format("%s at %s", game.awayTeam().abbreviation(), game.homeTeam().abbreviation()))
                .setImage(game.homeTeam().logo())
                .addField("Location", game.venue().ref(), true)
                .addField("Game Start", startValue, true)
                .addField("Watch On", canadianBroadcasts, true);

        if (!game.gameState().equals("FUT") && !game.gameState().equals("PRE")) { // either the game is in progress or final
            builder.addField("Score",
                    String.format("%s %d, %s %d",
                            game.awayTeam().abbreviation(), game.awayTeam().score(),
                            game.homeTeam().abbreviation(), game.homeTeam().score()), true);

            String period = game.gameState().equals("FINAL") ? "Final" :
                    game.periodDescriptor().periodType().equals("OT") ? "Overtime" : periodToString(game.periodDescriptor().number());

            builder.addField("Period", period, true);
        }

        return builder.build();
    }

    private String periodToString(int period) {
        return switch (period) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> "OT";
        };
    }
}
