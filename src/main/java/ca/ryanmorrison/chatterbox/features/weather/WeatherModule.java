package ca.ryanmorrison.chatterbox.features.weather;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /weather location:<text> [units:<metric|imperial>] [private:<bool>]} —
 * current conditions plus a 3-day forecast for any location wttr.in
 * recognises (city, country, postal code, {@code lat,long}, etc.).
 *
 * <p>No API key, no DB, no SSRF concerns: hostname is hard-coded
 * {@code wttr.in} and user input is just a location string passed to
 * its open lookup. Ten-second timeout, response-size cap, and a small
 * {@link WeatherClient.WeatherException} mapping for the failure modes
 * we've observed in the wild — see {@link WeatherClient}.
 */
public final class WeatherModule implements Module {

    static final String COMMAND      = "weather";
    static final String OPT_LOCATION = "location";
    static final String OPT_UNITS    = "units";
    static final String OPT_PRIVATE  = "private";

    static final String UNITS_METRIC   = "metric";
    static final String UNITS_IMPERIAL = "imperial";

    @Override public String name() { return "weather"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData location = new OptionData(OptionType.STRING, OPT_LOCATION,
                "City, country, postal code, or `lat,long`.", true, false);
        OptionData units = new OptionData(OptionType.STRING, OPT_UNITS,
                "Temperature units (default metric).", false, false)
                .addChoice("Metric (°C, km/h)",   UNITS_METRIC)
                .addChoice("Imperial (°F, mph)",  UNITS_IMPERIAL);
        OptionData priv = new OptionData(OptionType.BOOLEAN, OPT_PRIVATE,
                "Show the result only to you instead of the channel.",
                false, false);
        return List.of(Commands.slash(COMMAND,
                        "Show current conditions and a 3-day forecast for any location.")
                .addOptions(location, units, priv));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new WeatherHandler(new WeatherClient()));
    }
}
