package ca.ryanmorrison.chatterbox.features.weather;

import ca.ryanmorrison.chatterbox.features.weather.dto.WeatherResponse;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles {@code /weather location:<text> [units:<choice>] [private:<bool>]}.
 *
 * <p>Defers the reply (the network call can take a few seconds), fetches via
 * {@link WeatherClient}, and renders a 3-day-forecast embed via
 * {@link WeatherEmbedBuilder}. Failures map to ephemeral text replies with
 * the user-safe message stored in {@link WeatherClient.WeatherException} —
 * the underlying exception is never surfaced.
 */
final class WeatherHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(WeatherHandler.class);

    private final WeatherClient client;

    WeatherHandler(WeatherClient client) {
        this.client = client;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!WeatherModule.COMMAND.equals(event.getName())) return;

        String location = stringOption(event, WeatherModule.OPT_LOCATION);
        if (location == null || location.isBlank()) {
            event.reply("Tell me a location.").setEphemeral(true).queue();
            return;
        }

        WeatherEmbedBuilder.Units units = parseUnits(stringOption(event, WeatherModule.OPT_UNITS));
        boolean ephemeral = booleanOption(event, WeatherModule.OPT_PRIVATE);

        event.deferReply(ephemeral).queue();

        WeatherResponse response;
        try {
            response = client.fetch(location);
        } catch (WeatherClient.WeatherException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error fetching weather for {}", location, e);
            event.getHook().sendMessage("Something went wrong fetching the weather.").queue();
            return;
        }

        MessageEmbed embed = WeatherEmbedBuilder.build(response, location.trim(), units);
        event.getHook().sendMessageEmbeds(embed).queue();
    }

    private static WeatherEmbedBuilder.Units parseUnits(String raw) {
        if (raw == null) return WeatherEmbedBuilder.Units.METRIC;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "imperial" -> WeatherEmbedBuilder.Units.IMPERIAL;
            default         -> WeatherEmbedBuilder.Units.METRIC;
        };
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    private static boolean booleanOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null && opt.getAsBoolean();
    }
}
