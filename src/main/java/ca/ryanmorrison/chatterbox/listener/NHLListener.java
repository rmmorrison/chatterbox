package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.NHLConstants;
import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.integration.nhl.model.Game;
import ca.ryanmorrison.chatterbox.integration.nhl.model.GameWeek;
import ca.ryanmorrison.chatterbox.integration.nhl.model.Schedule;
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
public class NHLListener extends FormattedListenerAdapter {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final NHLService service;

    public NHLListener(@Autowired NHLService service) {
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
            LOGGER.error("An error occurred while fetching the current day's NHL schedule", e);
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

        return new EmbedBuilder()
                .setTitle(String.format("%s at %s", game.awayTeam().abbreviation(), game.homeTeam().abbreviation()))
                .setImage(game.homeTeam().logo())
                .addField("Location", game.venue().defaultVenue(), true)
                .addField("Game Start", startValue, true)
                .build();
    }
}
