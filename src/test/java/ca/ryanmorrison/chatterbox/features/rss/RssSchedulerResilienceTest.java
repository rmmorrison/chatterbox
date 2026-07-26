package ca.ryanmorrison.chatterbox.features.rss;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The scheduler drives ticks through {@code scheduleAtFixedRate}, which
 * <em>permanently cancels</em> a task the first time its runnable throws. So
 * "tick doesn't propagate" is exactly equivalent to "the feed keeps
 * refreshing" — a single transient DB error used to silently retire a feed
 * until the process restarted.
 *
 * <p>Each test pokes a different unguarded call site; before the fix only the
 * fetch itself was inside a try/catch.
 */
class RssSchedulerResilienceTest {

    private static final long FEED_ID = 7L;

    private static Feed feed() {
        return new Feed(FEED_ID, 100L, 200L, "https://example.com/feed.xml", "Example",
                300L, 60, Optional.empty(), Optional.empty(), Optional.empty(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    private static RssScheduler scheduler(RssRepository repo) {
        return new RssScheduler(repo, mock(RssFetcher.class));
    }

    @Test
    void tickSurvivesFailureLoadingTheFeed() {
        RssRepository repo = mock(RssRepository.class);
        when(repo.findById(FEED_ID))
                .thenThrow(new DataAccessException("connection pool exhausted"));

        assertDoesNotThrow(() -> scheduler(repo).tick(FEED_ID));
    }

    @Test
    void tickSurvivesFailureStampingTheRefreshTime() throws Exception {
        RssRepository repo = mock(RssRepository.class);
        when(repo.findById(FEED_ID)).thenReturn(Optional.of(feed()));
        doThrow(new DataAccessException("database is locked"))
                .when(repo).touchRefreshedAt(anyLong(), any(OffsetDateTime.class));

        RssFetcher fetcher = mock(RssFetcher.class);
        when(fetcher.fetch(any())).thenThrow(new RssFetcher.FetchException("unreachable"));

        var scheduler = new RssScheduler(repo, fetcher);
        assertDoesNotThrow(() -> scheduler.tick(FEED_ID));
    }

    @Test
    void tickSurvivesAnErrorNotJustAnException() {
        RssRepository repo = mock(RssRepository.class);
        when(repo.findById(FEED_ID)).thenThrow(new StackOverflowError("deep"));

        assertDoesNotThrow(() -> scheduler(repo).tick(FEED_ID));
    }
}
