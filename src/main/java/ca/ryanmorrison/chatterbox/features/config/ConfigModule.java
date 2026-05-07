package ca.ryanmorrison.chatterbox.features.config;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

/**
 * The {@code /config} slash command: per-guild overrides for runtime-mutable
 * configuration values.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code list} — every registered key with its current effective value
 *       and source (override / env / default).</li>
 *   <li>{@code get key:<name>} — focused view of one key.</li>
 *   <li>{@code set key:<name> value:<text>} — validates and persists a
 *       per-guild override.</li>
 *   <li>{@code unset key:<name>} — drops the override; value falls back
 *       to env var / built-in default.</li>
 * </ul>
 *
 * <p>Guild-only and gated on {@link Permission#ADMINISTRATOR} (the guild
 * owner passes implicitly). Discord also hides the command from non-admins
 * up-front via {@link DefaultMemberPermissions}; the in-handler check is the
 * load-bearing one because the default member permissions can be overridden
 * server-side.
 */
public final class ConfigModule implements Module {

    @Override public String name() { return "config"; }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new ConfigHandler(ctx.runtimeConfig()));
    }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData keyOpt = new OptionData(OptionType.STRING, ConfigHandler.OPT_KEY,
                "Configuration key.", true, true); // required, autocomplete
        OptionData valueOpt = new OptionData(OptionType.STRING, ConfigHandler.OPT_VALUE,
                "New value (validated against the key's type).", true, false);

        return List.of(
                Commands.slash(ConfigHandler.CMD_NAME, "Per-server configuration overrides.")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                        .addSubcommands(
                                new SubcommandData(ConfigHandler.SUB_LIST,
                                        "Show every config key, its current value, and where it came from."),
                                new SubcommandData(ConfigHandler.SUB_GET,
                                        "Show one config key's current value.")
                                        .addOptions(keyOpt),
                                new SubcommandData(ConfigHandler.SUB_SET,
                                        "Set a per-server override for a config key.")
                                        .addOptions(keyOpt, valueOpt),
                                new SubcommandData(ConfigHandler.SUB_UNSET,
                                        "Remove the per-server override; value falls back to env / default.")
                                        .addOptions(keyOpt)));
    }
}
