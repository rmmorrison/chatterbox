package ca.ryanmorrison.chatterbox.features.rss;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * A persisted RSS/Atom feed subscription scoped to a single channel.
 *
 * <p>{@code lastItemId} and {@code lastItemPublishedAt} are the markers used
 * to detect new items between refreshes. Both are empty until the first
 * successful refresh establishes a baseline (no items posted on that first
 * tick — see {@link RssScheduler}).
 */
record Feed(
        long id,
        long guildId,
        long channelId,
        String url,
        String title,
        long addedBy,
        int refreshMinutes,
        Optional<String> lastItemId,
        Optional<OffsetDateTime> lastItemPublishedAt,
        Optional<OffsetDateTime> lastRefreshedAt,
        OffsetDateTime createdAt) {
}
