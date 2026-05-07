package ca.ryanmorrison.chatterbox.features.config;

import ca.ryanmorrison.chatterbox.common.permissions.Permissions;
import ca.ryanmorrison.chatterbox.config.runtime.ConfigKey;
import ca.ryanmorrison.chatterbox.config.runtime.ConfigSource;
import ca.ryanmorrison.chatterbox.config.runtime.ConfigType;
import ca.ryanmorrison.chatterbox.config.runtime.RuntimeConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

/**
 * Handles {@code /config} slash interactions. All replies are ephemeral —
 * config inspection and mutation are admin-ops actions, not chat content.
 *
 * <p>{@link Permissions#requireAdministrator(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback)}
 * is re-checked on every interaction so an admin who loses the role mid-flow
 * is denied as soon as they click again. The slash command itself is also
 * marked guild-only via {@code InteractionContextType.GUILD} on the command
 * data, so DM invocations are rejected before reaching us.
 */
final class ConfigHandler extends ListenerAdapter {

    static final String CMD_NAME    = "config";
    static final String SUB_LIST    = "list";
    static final String SUB_GET     = "get";
    static final String SUB_SET     = "set";
    static final String SUB_UNSET   = "unset";
    static final String OPT_KEY     = "key";
    static final String OPT_VALUE   = "value";

    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;
    private static final Color EMBED_COLOR = new Color(0x4F46E5);

    private final RuntimeConfig runtimeConfig;

    ConfigHandler(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!CMD_NAME.equals(event.getName())) return;
        if (!Permissions.requireAdministrator(event)) return;
        if (event.getGuild() == null) return; // belt-and-braces; setContexts already handles this

        String sub = event.getSubcommandName();
        if (sub == null) return;
        switch (sub) {
            case SUB_LIST  -> handleList(event);
            case SUB_GET   -> handleGet(event);
            case SUB_SET   -> handleSet(event);
            case SUB_UNSET -> handleUnset(event);
            default        -> {}
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        List<ConfigKey<?>> keys = runtimeConfig.registry().all();
        if (keys.isEmpty()) {
            event.reply("No runtime-overridable config keys are registered.")
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle("Runtime configuration");
        for (ConfigKey<?> key : keys) {
            eb.addField(key.key(), formatKeyRow(guildId, key), false);
        }
        eb.setFooter("Use /config set to override · /config unset to revert · "
                + "lookup order: server override → env var → default");
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private <T> String formatKeyRow(long guildId, ConfigKey<T> key) {
        RuntimeConfig.Resolved<T> resolved = runtimeConfig.resolve(guildId, key);
        return "**`" + resolved.rawValue() + "`** _(" + resolved.source().label() + ")_\n"
                + key.description();
    }

    private void handleGet(SlashCommandInteractionEvent event) {
        Optional<ConfigKey<?>> keyOpt = lookupKey(event);
        if (keyOpt.isEmpty()) return;
        event.replyEmbeds(buildGetEmbed(event.getGuild().getIdLong(), keyOpt.get()))
                .setEphemeral(true).queue();
    }

    private <T> MessageEmbed buildGetEmbed(long guildId, ConfigKey<T> key) {
        RuntimeConfig.Resolved<T> resolved = runtimeConfig.resolve(guildId, key);
        return new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle(key.key())
                .setDescription(key.description())
                .addField("Effective value",
                        "`" + resolved.rawValue() + "` _(" + resolved.source().label() + ")_", false)
                .addField("Type", key.type().label(), true)
                .addField("Default", "`" + key.defaultRaw() + "`", true)
                .addField("Environment variable", "`" + key.envVar() + "`", true)
                .build();
    }

    private void handleSet(SlashCommandInteractionEvent event) {
        Optional<ConfigKey<?>> keyOpt = lookupKey(event);
        if (keyOpt.isEmpty()) return;
        ConfigKey<?> key = keyOpt.get();

        String rawValue = stringOption(event, OPT_VALUE);
        if (rawValue == null || rawValue.isBlank()) {
            event.reply("Value can't be empty. Use `/config unset` to drop the override instead.")
                    .setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        try {
            runtimeConfig.set(guildId, key, rawValue, event.getUser().getIdLong());
        } catch (ConfigType.InvalidValueException e) {
            event.reply("`" + key.key() + "` " + e.getMessage()).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(buildGetEmbed(guildId, key)).setEphemeral(true).queue();
    }

    private void handleUnset(SlashCommandInteractionEvent event) {
        Optional<ConfigKey<?>> keyOpt = lookupKey(event);
        if (keyOpt.isEmpty()) return;
        ConfigKey<?> key = keyOpt.get();

        long guildId = event.getGuild().getIdLong();
        boolean removed = runtimeConfig.unset(guildId, key);
        if (!removed) {
            event.reply("`" + key.key() + "` had no server override set.")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(buildGetEmbed(guildId, key)).setEphemeral(true).queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!CMD_NAME.equals(event.getName())) return;
        if (!OPT_KEY.equals(event.getFocusedOption().getName())) return;

        String prefix = event.getFocusedOption().getValue().toLowerCase(java.util.Locale.ROOT);
        List<Command.Choice> choices = runtimeConfig.registry().all().stream()
                .map(ConfigKey::key)
                .filter(k -> prefix.isEmpty() || k.toLowerCase(java.util.Locale.ROOT).contains(prefix))
                .limit(MAX_AUTOCOMPLETE_CHOICES)
                .map(k -> new Command.Choice(k, k))
                .toList();
        event.replyChoices(choices).queue();
    }

    /**
     * Pulls the {@code key} option, validates it against the registry, and
     * replies with an error if it's missing or unknown. Returns empty in the
     * error case so callers just early-return without sending their own reply.
     */
    private Optional<ConfigKey<?>> lookupKey(SlashCommandInteractionEvent event) {
        String name = stringOption(event, OPT_KEY);
        if (name == null || name.isBlank()) {
            event.reply("Key is required.").setEphemeral(true).queue();
            return Optional.empty();
        }
        Optional<ConfigKey<?>> key = runtimeConfig.registry().find(name.trim());
        if (key.isEmpty()) {
            event.reply("Unknown config key `" + name + "`. Try `/config list` to see what's available.")
                    .setEphemeral(true).queue();
            return Optional.empty();
        }
        return key;
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        var opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    /** Color used for /config embeds. Package-private for tests. */
    static Color embedColor() { return EMBED_COLOR; }
}
