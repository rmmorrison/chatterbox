package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.Set;

/**
 * "Shouting back" feature: when a user posts an all-caps message in a guild
 * channel, the bot stores it (per channel) and replies with a random
 * previously-stored shout from the same channel.
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
        return List.of(new ShoutListener(new ShoutDetector(), new ShoutRepository(ctx.database())));
    }
}
