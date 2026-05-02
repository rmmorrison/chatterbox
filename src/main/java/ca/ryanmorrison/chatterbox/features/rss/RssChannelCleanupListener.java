package ca.ryanmorrison.chatterbox.features.rss;

import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * When a guild channel is deleted, prune any RSS feeds attached to it and
 * cancel their scheduled refreshes. Keeps the feed table free of orphans.
 */
final class RssChannelCleanupListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RssChannelCleanupListener.class);

    private final RssRepository repo;
    private final RssScheduler scheduler;

    RssChannelCleanupListener(RssRepository repo, RssScheduler scheduler) {
        this.repo = repo;
        this.scheduler = scheduler;
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        long channelId = event.getChannel().getIdLong();
        List<Feed> feeds = repo.listByChannel(channelId);
        if (feeds.isEmpty()) return;
        for (Feed f : feeds) scheduler.cancel(f.id());
        int removed = repo.deleteByChannel(channelId);
        log.info("Pruned {} RSS feed(s) after channel {} was deleted.", removed, channelId);
    }
}
