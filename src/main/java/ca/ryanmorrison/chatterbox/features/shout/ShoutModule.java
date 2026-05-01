package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.Set;

/**
 * "Shouting back" feature: when a user posts an all-caps message in a guild
 * channel, the bot stores it (per channel) and replies with a random
 * previously-stored shout from the same channel.
 *
 * <p>Also exposes {@code /shout-history} (ephemeral, guild-only) so users can
 * page back through emissions in the channel.
 *
 * <p>Requires the {@code MESSAGE_CONTENT} privileged intent. Enable it on the
 * bot's application page in the Discord Developer Portal.
 */
public final class ShoutModule implements Module {

    @Override public String name() { return "shout"; }

    @Override
    public Set<GatewayIntent> intents() {
        return Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
    }

    @Override
    public List<String> migrationLocations() {
        return List.of("db/migration/shout");
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        var shouts = new ShoutRepository(ctx.database());
        var history = new ShoutHistoryRepository(ctx.database());
        return List.of(
                new ShoutListener(new ShoutDetector(), shouts, history),
                new ShoutHistoryHandler(shouts, history));
    }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(
                Commands.slash(ShoutHistoryView.CMD_NAME, "Browse the bot's shout history for this channel.")
                        .setContexts(InteractionContextType.GUILD));
    }
}
