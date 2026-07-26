package ca.ryanmorrison.chatterbox.features.nhl;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

/**
 * Exposes {@code /nhl schedule [team]}: the upcoming seven-day NHL schedule,
 * either league-wide or filtered to a single franchise.
 *
 * <p>Currently a single subcommand, but registered as a group so future
 * NHL features (standings, scores, etc.) can slot in alongside.
 */
public final class NhlModule implements Module {

    static final String COMMAND = "nhl";
    static final String SUBCOMMAND_SCHEDULE = "schedule";
    static final String OPTION_TEAM = "team";

    /** Retained so {@link #onStop()} can release its HTTP client. */
    private volatile NhlClient client;

    @Override public String name() { return "nhl"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData team = new OptionData(OptionType.STRING, OPTION_TEAM,
                "Three-letter team code (e.g. TOR, EDM). Omit for the league-wide schedule.",
                false, true); // required = false, autoComplete = true
        return List.of(
                Commands.slash(COMMAND, "NHL schedule and league information.")
                        .addSubcommands(new SubcommandData(SUBCOMMAND_SCHEDULE,
                                "Show the upcoming NHL schedule for the next 7 days.")
                                .addOptions(team)));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        NhlClient c = new NhlClient();
        this.client = c;
        return List.of(new NhlHandler(c));
    }

    @Override
    public void onStop() {
        NhlClient c = client;
        if (c != null) c.close();
    }

}
