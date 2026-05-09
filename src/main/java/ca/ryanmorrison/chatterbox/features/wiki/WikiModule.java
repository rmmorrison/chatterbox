package ca.ryanmorrison.chatterbox.features.wiki;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /wiki query:<text> [private:<bool>]} — Wikipedia summary lookup.
 *
 * <p>Direct title lookup with a search-on-404 fallback so typos and
 * partial matches still resolve. English Wikipedia only; no API key
 * required. Hostname is hard-coded {@code en.wikipedia.org} so there's
 * no SSRF surface to defend.
 */
public final class WikiModule implements Module {

    static final String COMMAND     = "wiki";
    static final String OPT_QUERY   = "query";
    static final String OPT_PRIVATE = "private";

    @Override public String name() { return "wiki"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData query = new OptionData(OptionType.STRING, OPT_QUERY,
                "What to look up on Wikipedia.", true, false);
        OptionData priv = new OptionData(OptionType.BOOLEAN, OPT_PRIVATE,
                "Show the result only to you instead of the channel.",
                false, false);
        return List.of(Commands.slash(COMMAND, "Look up a topic on Wikipedia.")
                .addOptions(query, priv));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new WikiHandler(new WikiClient()));
    }
}
