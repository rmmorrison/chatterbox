package ca.ryanmorrison.chatterbox.features.autoreply;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.Set;

/**
 * Channel-scoped automated replies. Moderators (Manage Messages) configure
 * regex → response rules with {@code /autoreply add|edit|delete}; the
 * listener scans incoming guild messages and posts the response of the first
 * matching rule.
 *
 * <p>Requires the {@code MESSAGE_CONTENT} privileged intent.
 */
public final class AutoReplyModule implements Module {

    @Override public String name() { return "autoreply"; }

    @Override
    public Set<GatewayIntent> intents() {
        return Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
    }

    @Override
    public List<String> migrationLocations() {
        return List.of("db/migration/autoreply");
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        var repo = new AutoReplyRepository(ctx.database());
        var matcher = new AutoReplyMatcher(repo);
        var pending = new PendingOverrideStore();
        return List.of(
                new AutoReplyListener(matcher),
                new AutoReplyHandler(repo, matcher, pending));
    }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(
                Commands.slash(AutoReplyHandler.CMD_NAME, "Manage automated replies in this channel.")
                        .setContexts(InteractionContextType.GUILD)
                        .addSubcommands(
                                new SubcommandData("add",    "Create a new automated reply."),
                                new SubcommandData("edit",   "Edit an existing automated reply."),
                                new SubcommandData("delete", "Delete an automated reply.")));
    }
}
