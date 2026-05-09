package ca.ryanmorrison.chatterbox.features.when;

import ca.ryanmorrison.chatterbox.features.timezone.UserTimezonesRepository;
import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /when at:<text> in:<timezone> [private:<bool>]} — converts a
 * time-in-place to Discord timestamp markdown so every viewer sees it in
 * their own local zone.
 *
 * <p>{@code in:} is required because Discord doesn't expose the user's
 * own timezone. Autocomplete drives a curated list of common zones; any
 * IANA name or offset is accepted. {@code at:} accepts a small but
 * forgiving grammar; see {@link TimeParser}.
 *
 * <p>Public reply by default — sharing is the point — with a
 * {@code private:true} escape hatch for previewing.
 */
public final class WhenModule implements Module {

    static final String COMMAND     = "when";
    static final String OPT_AT      = "at";
    static final String OPT_IN      = "in";
    static final String OPT_PRIVATE = "private";

    @Override public String name() { return "when"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData at = new OptionData(OptionType.STRING, OPT_AT,
                "Time to convert (e.g. `7pm`, `tomorrow 9am`, `friday 3pm`, `in 2 hours`).",
                true, false);
        // Required + autocompleted: Discord doesn't expose user timezones,
        // so we have to ask. Curated list keeps the dropdown useful.
        OptionData in = new OptionData(OptionType.STRING, OPT_IN,
                "Timezone the time is given in (e.g. America/Toronto).",
                true, true);
        OptionData priv = new OptionData(OptionType.BOOLEAN, OPT_PRIVATE,
                "Show the result only to you instead of the channel.",
                false, false);

        return List.of(Commands.slash(COMMAND,
                        "Convert a time to Discord timestamp markdown so everyone sees it in their own timezone.")
                .addOptions(at, in, priv));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new WhenHandler(new UserTimezonesRepository(ctx.database())));
    }
}
