package ca.ryanmorrison.chatterbox.features.timezone;

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
 * {@code /timezone set|show|clear} — per-user IANA zone preference.
 *
 * <p>Used by {@code /when} to disambiguate relative-day inputs
 * (today / tomorrow / weekday / bare time), which would otherwise have
 * to be interpreted in the {@code in:} zone. Discord doesn't expose
 * user timezones to bots, so we ask once and store.
 */
public final class TimezoneModule implements Module {

    @Override public String name() { return "timezone"; }

    @Override
    public List<String> migrationLocations() {
        return List.of("classpath:db/migration/user-timezones");
    }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData tz = new OptionData(OptionType.STRING, TimezoneHandler.OPT_TZ,
                "Your IANA timezone (e.g. America/Toronto).",
                true, true); // required, autocomplete

        return List.of(Commands.slash(TimezoneHandler.CMD_NAME,
                        "Manage your personal timezone for relative-day parsing.")
                .addSubcommands(
                        new SubcommandData(TimezoneHandler.SUB_SET,
                                "Set your timezone (used by /when for today/tomorrow/weekday parsing).")
                                .addOptions(tz),
                        new SubcommandData(TimezoneHandler.SUB_SHOW,
                                "Show your currently-set timezone."),
                        new SubcommandData(TimezoneHandler.SUB_CLEAR,
                                "Clear your timezone preference.")));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new TimezoneHandler(new UserTimezonesRepository(ctx.database())));
    }
}
