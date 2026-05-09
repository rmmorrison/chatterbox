package ca.ryanmorrison.chatterbox.features.isitdown;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /isitdown url:<text> [private:<bool>]} — probes a URL with a
 * bounded HTTP request and reports whether it's responding.
 *
 * <p>The check uses a {@code Range}-limited GET with a 10-second timeout
 * and a 4 KB body cap, plus an SSRF deny-list ({@link UrlGuard}) so the
 * bot won't be coerced into probing internal addresses.
 *
 * <p>Public reply by default — when a service is down, the channel
 * usually wants to commiserate together — but {@code private:true} keeps
 * it ephemeral.
 */
public final class IsItDownModule implements Module {

    static final String COMMAND     = "isitdown";
    static final String OPT_URL     = "url";
    static final String OPT_PRIVATE = "private";

    @Override public String name() { return "isitdown"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(Commands.slash(COMMAND,
                        "Check whether a URL is responding (\"is it down for everyone, or just me?\").")
                .addOption(OptionType.STRING, OPT_URL,
                        "Full HTTP(S) URL to probe.", true)
                .addOption(OptionType.BOOLEAN, OPT_PRIVATE,
                        "Show the result only to you instead of the channel.", false));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new IsItDownHandler(new IsItDownChecker()));
    }
}
