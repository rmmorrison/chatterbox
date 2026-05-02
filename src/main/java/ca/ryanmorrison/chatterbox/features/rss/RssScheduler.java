package ca.ryanmorrison.chatterbox.features.rss;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns a {@link ScheduledExecutorService} that polls each registered feed on
 * its individual {@code refresh_minutes} cadence. One running task per feed;
 * cancellation on remove is immediate.
 *
 * <p>Lifecycle: created in {@link RssModule#listeners} (so the handler can
 * call {@link #scheduleNew} / {@link #cancel}), seeded in
 * {@link RssModule#onStart} once JDA is ready, stopped in
 * {@link RssModule#onStop}.
 */
final class RssScheduler {

    private static final Logger log = LoggerFactory.getLogger(RssScheduler.class);

    private final RssRepository repo;
    private final RssFetcher fetcher;
    private final ScheduledExecutorService exec;
    private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private volatile JDA jda;

    RssScheduler(RssRepository repo, RssFetcher fetcher) {
        this(repo, fetcher, defaultExecutor());
    }

    /** Test seam. */
    RssScheduler(RssRepository repo, RssFetcher fetcher, ScheduledExecutorService exec) {
        this.repo = repo;
        this.fetcher = fetcher;
        this.exec = exec;
    }

    private static ScheduledExecutorService defaultExecutor() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "rss-refresh-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Called from {@link RssModule#onStart} once JDA is ready. */
    void start(JDA jda) {
        this.jda = jda;
        for (Feed feed : repo.listAll()) {
            scheduleExisting(feed);
        }
        log.info("RSS scheduler started with {} feed(s).", tasks.size());
    }

    /** Schedule a freshly-added feed. First tick happens after a full interval. */
    void scheduleNew(Feed feed) {
        schedule(feed, feed.refreshMinutes());
    }

    /**
     * Schedule a feed loaded at startup. Initial delay is jittered (0..N-1
     * minutes) so a cold start doesn't pound every host at once.
     */
    private void scheduleExisting(Feed feed) {
        int jitter = feed.refreshMinutes() <= 1 ? 0
                : ThreadLocalRandom.current().nextInt(feed.refreshMinutes());
        schedule(feed, jitter);
    }

    private void schedule(Feed feed, int initialDelayMinutes) {
        ScheduledFuture<?> task = exec.scheduleAtFixedRate(
                () -> tick(feed.id()),
                initialDelayMinutes,
                feed.refreshMinutes(),
                TimeUnit.MINUTES);
        ScheduledFuture<?> prev = tasks.put(feed.id(), task);
        if (prev != null) prev.cancel(false);
    }

    /** Cancel an existing schedule (e.g. after remove). */
    void cancel(long feedId) {
        ScheduledFuture<?> task = tasks.remove(feedId);
        if (task != null) task.cancel(false);
    }

    /** Cancel and re-load all schedules — used after bulk channel cleanup. */
    void cancelMany(Iterable<Long> feedIds) {
        for (Long id : feedIds) cancel(id);
    }

    void stop() {
        exec.shutdownNow();
        try {
            exec.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- the actual refresh ----

    /** Visible for tests. */
    void tick(long feedId) {
        Optional<Feed> opt = repo.findById(feedId);
        if (opt.isEmpty()) {
            cancel(feedId);
            return;
        }
        Feed feed = opt.get();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SyndFeed syndFeed;
        try {
            syndFeed = fetcher.fetch(feed.url());
        } catch (RssFetcher.FetchException e) {
            log.error("RSS refresh failed for feed {} ({}): {}", feed.id(), feed.url(), e.getMessage());
            repo.touchRefreshedAt(feed.id(), now);
            return;
        } catch (RuntimeException e) {
            log.error("RSS refresh threw unexpectedly for feed {} ({})", feed.id(), feed.url(), e);
            repo.touchRefreshedAt(feed.id(), now);
            return;
        }

        List<SyndEntry> all = RssFetcher.entries(syndFeed);
        List<SyndEntry> sorted = sortNewestFirst(all);
        if (sorted.isEmpty()) {
            repo.touchRefreshedAt(feed.id(), now);
            return;
        }

        // First-ever refresh: silently establish the marker so we don't flood the channel.
        if (feed.lastItemId().isEmpty() && feed.lastItemPublishedAt().isEmpty()) {
            SyndEntry latest = sorted.get(0);
            repo.updateMarkers(feed.id(), entryId(latest), publishedOf(latest), now);
            return;
        }

        List<SyndEntry> fresh = newSince(sorted, feed.lastItemId(), feed.lastItemPublishedAt());
        if (fresh.isEmpty()) {
            repo.touchRefreshedAt(feed.id(), now);
            return;
        }

        SyndEntry latest = fresh.get(0);
        TextChannel channel = jda == null ? null : jda.getTextChannelById(feed.channelId());
        if (channel != null && channel.canTalk()) {
            try {
                RssPublisher.post(channel, feed, latest, fresh.size());
            } catch (RuntimeException e) {
                log.error("Failed to post RSS embed for feed {} into channel {}", feed.id(), feed.channelId(), e);
            }
        } else {
            log.warn("Skipping RSS post for feed {}: channel {} is unavailable.",
                    feed.id(), feed.channelId());
        }
        repo.updateMarkers(feed.id(), entryId(latest), publishedOf(latest), now);
    }

    // ---- diff helpers (pure; tested) ----

    /**
     * Items strictly newer than the stored markers, newest-first.
     *
     * <p>Match strategy: walk the newest-first list collecting items until we
     * hit the previous marker. If the marker isn't present (e.g. it has aged
     * out of the feed window), fall back to a date filter — items with a
     * publish date strictly after {@code lastPublished} only. The result is
     * capped at {@link #MAX_NEW_PER_TICK} so a stale marker can never trigger
     * a flood.
     */
    static List<SyndEntry> newSince(List<SyndEntry> sortedNewestFirst,
                                    Optional<String> lastId,
                                    Optional<OffsetDateTime> lastPublished) {
        List<SyndEntry> out = new ArrayList<>();
        boolean foundMarker = false;
        for (SyndEntry e : sortedNewestFirst) {
            if (lastId.isPresent() && lastId.get().equals(entryId(e))) {
                foundMarker = true;
                break;
            }
            out.add(e);
            if (out.size() >= MAX_NEW_PER_TICK) break;
        }
        if (foundMarker) {
            return out;
        }
        // Marker not found in the returned window — apply the date floor as a
        // safety net so we don't replay the entire feed.
        if (lastPublished.isEmpty()) {
            return out.isEmpty() ? List.of() : List.of(out.get(0));
        }
        OffsetDateTime floor = lastPublished.get();
        List<SyndEntry> filtered = new ArrayList<>();
        for (SyndEntry e : out) {
            OffsetDateTime when = publishedOf(e);
            if (when != null && when.isAfter(floor)) filtered.add(e);
        }
        return filtered;
    }

    static final int MAX_NEW_PER_TICK = 25;

    /**
     * Newest-first sort. Entries without a date are kept in source order at
     * their original position (treated as newest if first in source).
     */
    static List<SyndEntry> sortNewestFirst(List<SyndEntry> entries) {
        List<SyndEntry> copy = new ArrayList<>(entries);
        copy.sort((a, b) -> {
            OffsetDateTime da = publishedOf(a);
            OffsetDateTime db = publishedOf(b);
            if (da == null && db == null) return 0;
            if (da == null) return -1; // unknown date wins (assume "new")
            if (db == null) return 1;
            return db.compareTo(da);
        });
        return copy;
    }

    static String entryId(SyndEntry e) {
        if (e.getUri() != null && !e.getUri().isBlank()) return e.getUri();
        if (e.getLink() != null && !e.getLink().isBlank()) return e.getLink();
        if (e.getTitle() != null && !e.getTitle().isBlank()) return e.getTitle();
        return "";
    }

    static OffsetDateTime publishedOf(SyndEntry e) {
        if (e.getPublishedDate() != null) {
            return e.getPublishedDate().toInstant().atOffset(ZoneOffset.UTC);
        }
        if (e.getUpdatedDate() != null) {
            return e.getUpdatedDate().toInstant().atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
