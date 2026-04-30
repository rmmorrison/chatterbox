package ca.ryanmorrison.chatterbox.features.ping;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.hooks.EventListener;

import java.util.List;

/**
 * Trivial example module: replies to {@code /ping} with the gateway latency.
 * Demonstrates a module with no database and no special intents.
 */
public final class PingModule extends ListenerAdapter implements Module {

    private static final String COMMAND = "ping";

    @Override public String name() { return "ping"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(Commands.slash(COMMAND, "Replies with the bot's gateway latency."));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;
        long latency = event.getJDA().getGatewayPing();
        event.reply("pong! (" + latency + " ms)").setEphemeral(true).queue();
    }
}
