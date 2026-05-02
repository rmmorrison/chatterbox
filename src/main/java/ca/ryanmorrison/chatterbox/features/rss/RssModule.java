package ca.ryanmorrison.chatterbox.features.rss;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import ca.ryanmorrison.chatterbox.module.ModuleContext;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

/**
 * Per-channel RSS/Atom feed subscriptions. Anyone in a guild may
 * {@code /rss add} a feed; only the original adder (or a moderator with
 * Manage Messages) may {@code /rss remove} it. Each feed is polled on its
 * own configurable cadence and new items are posted to the channel as
 * embeds.
 */
public final class RssModule implements Module {

    private RssScheduler scheduler;

    @Override public String name() { return "rss"; }

    @Override
    public List<String> migrationLocations() {
        return List.of("db/migration/rss");
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        var repo = new RssRepository(ctx.database());
        var fetcher = new RssFetcher();
        this.scheduler = new RssScheduler(repo, fetcher);
        var pending = new PendingRssRemovals();
        return List.of(
                new RssHandler(repo, fetcher, scheduler, pending),
                new RssChannelCleanupListener(repo, scheduler));
    }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(
                Commands.slash(RssHandler.CMD_NAME, "Manage RSS/Atom feeds in this channel.")
                        .setContexts(InteractionContextType.GUILD)
                        .addSubcommands(
                                new SubcommandData(RssHandler.SUB_ADD,
                                        "Add an RSS or Atom feed to this channel.")
                                        .addOptions(
                                                new OptionData(OptionType.STRING, RssHandler.OPT_URL,
                                                        "Feed URL (http or https).", true),
                                                new OptionData(OptionType.INTEGER, RssHandler.OPT_REFRESH,
                                                        "Refresh interval in minutes ("
                                                                + RssHandler.MIN_REFRESH_MINUTES + "–"
                                                                + RssHandler.MAX_REFRESH_MINUTES + "). Default "
                                                                + RssHandler.DEFAULT_REFRESH_MINUTES + ".",
                                                        false)
                                                        .setRequiredRange(RssHandler.MIN_REFRESH_MINUTES,
                                                                RssHandler.MAX_REFRESH_MINUTES)),
                                new SubcommandData(RssHandler.SUB_REMOVE,
                                        "Remove an RSS feed from this channel.")));
    }

    @Override
    public void onStart(ModuleContext ctx) {
        if (scheduler != null) scheduler.start(ctx.jda());
    }

    @Override
    public void onStop() {
        if (scheduler != null) scheduler.stop();
    }
}
