package ca.ryanmorrison.chatterbox.features.rss;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure tests for {@link RssScheduler#resumeDelayMinutes}. */
class RssSchedulerResumeTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-02T12:00:00Z");

    @Test
    void resumeReturnsRemainderOfInterval() {
        // refreshed 37 minutes ago, 60-minute interval → 23 minutes left.
        OffsetDateTime last = NOW.minusMinutes(37);
        assertEquals(23, RssScheduler.resumeDelayMinutes(last, 60, NOW));
    }

    @Test
    void resumeAtIntervalBoundaryFiresImmediately() {
        OffsetDateTime last = NOW.minusMinutes(60);
        assertEquals(0, RssScheduler.resumeDelayMinutes(last, 60, NOW));
    }

    @Test
    void resumePastDueFiresImmediately() {
        OffsetDateTime last = NOW.minusHours(48);
        assertEquals(0, RssScheduler.resumeDelayMinutes(last, 60, NOW));
    }

    @Test
    void resumeJustAfterLastSyncWaitsAlmostFullInterval() {
        OffsetDateTime last = NOW.minusSeconds(5);
        assertEquals(60, RssScheduler.resumeDelayMinutes(last, 60, NOW));
    }

    @Test
    void resumeWithFutureTimestampWaitsFullInterval() {
        // Clock skew safety net: a timestamp ahead of "now" shouldn't yield
        // a negative or wrap-around delay.
        OffsetDateTime last = NOW.plusMinutes(10);
        assertEquals(60, RssScheduler.resumeDelayMinutes(last, 60, NOW));
    }

    @Test
    void resumeRespectsShortInterval() {
        OffsetDateTime last = NOW.minusMinutes(7);
        assertEquals(8, RssScheduler.resumeDelayMinutes(last, 15, NOW));
    }
}
