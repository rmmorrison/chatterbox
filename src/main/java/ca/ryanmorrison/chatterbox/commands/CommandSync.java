package ca.ryanmorrison.chatterbox.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Synchronises slash commands at startup and whenever the bot joins a new
 * guild. Always uses the full-replacement {@code updateCommands()} call, and
 * always clears the inactive scope, so switching {@code CHATTERBOX_DEV_MODE}
 * never leaves duplicate commands behind.
 *
 * <p>Dev mode  → register all commands per-guild, clear globals.
 * <br>Prod mode → register all commands globally, clear per-guild.
 */
public final class CommandSync extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandSync.class);

    private final List<SlashCommandData> commands;
    private final boolean devMode;

    public CommandSync(List<SlashCommandData> commands, boolean devMode) {
        this.commands = List.copyOf(commands);
        this.devMode = devMode;
    }

    /** Run a full sync across every guild and the global scope. */
    public void syncAll(JDA jda) {
        if (devMode) {
            log.info("Dev mode: registering {} command(s) per-guild and clearing globals.", commands.size());
            jda.updateCommands().queue(
                    ok -> log.debug("Cleared {} global command(s).", commands.size()),
                    err -> log.warn("Failed to clear global commands: {}", err.toString()));
            for (Guild g : jda.getGuilds()) {
                pushToGuild(g);
            }
        } else {
            log.info("Prod mode: registering {} command(s) globally and clearing guild overrides.", commands.size());
            jda.updateCommands().addCommands(commands).queue(
                    ok -> log.debug("Pushed {} global command(s).", commands.size()),
                    err -> log.warn("Failed to push global commands: {}", err.toString()));
            for (Guild g : jda.getGuilds()) {
                clearGuild(g);
            }
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        if (devMode) {
            pushToGuild(event.getGuild());
        } else {
            clearGuild(event.getGuild());
        }
    }

    private void pushToGuild(Guild g) {
        g.updateCommands().addCommands(commands).queue(
                ok -> log.debug("Pushed {} command(s) to guild {}.", commands.size(), g.getId()),
                err -> log.warn("Failed to push commands to guild {}: {}", g.getId(), err.toString()));
    }

    private void clearGuild(Guild g) {
        g.updateCommands().queue(
                ok -> log.debug("Cleared command overrides on guild {}.", g.getId()),
                err -> log.warn("Failed to clear commands on guild {}: {}", g.getId(), err.toString()));
    }
}
