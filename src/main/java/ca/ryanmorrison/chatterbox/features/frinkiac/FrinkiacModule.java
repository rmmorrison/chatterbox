package ca.ryanmorrison.chatterbox.features.frinkiac;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * Exposes {@code /frinkiac query:<text>}: search The Simpsons frames on
 * frinkiac.com, browse hits with prev/next, optionally rewrite the caption,
 * and post the (possibly captioned) frame in the originating channel.
 */
public final class FrinkiacModule implements Module {

    static final String COMMAND = "frinkiac";
    static final String OPTION_QUERY = "query";

    @Override public String name() { return "frinkiac"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData query = new OptionData(OptionType.STRING, OPTION_QUERY,
                "Search text to find a Simpsons frame.",
                true, false);
        return List.of(
                Commands.slash(COMMAND, "Search The Simpsons frames via Frinkiac.")
                        .addOptions(query));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new FrinkiacHandler(new FrinkiacClient(), new FrinkiacSessions()));
    }
}
