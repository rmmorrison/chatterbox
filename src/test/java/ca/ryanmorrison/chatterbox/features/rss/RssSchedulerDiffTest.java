package ca.ryanmorrison.chatterbox.features.rss;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the diff logic in {@link RssScheduler}. */
class RssSchedulerDiffTest {

    private static SyndEntry entry(String id, OffsetDateTime when) {
        var e = new SyndEntryImpl();
        e.setUri(id);
        e.setLink(id);
        if (when != null) e.setPublishedDate(Date.from(when.toInstant()));
        return e;
    }

    @Test
    void firstRefreshHasNoMarkers_handledByCallerNotByNewSince() {
        // newSince expects the caller to short-circuit on first refresh; here we
        // just confirm that with no markers, "all items" is returned (capped).
        var e1 = entry("a", OffsetDateTime.now(ZoneOffset.UTC));
        List<SyndEntry> result = RssScheduler.newSince(List.of(e1), Optional.empty(), Optional.empty());
        assertEquals(1, result.size());
    }

    @Test
    void stopsAtMarker() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        var newer1 = entry("c", t0.plusMinutes(20));
        var newer2 = entry("b", t0.plusMinutes(10));
        var marker = entry("a", t0);
        List<SyndEntry> result = RssScheduler.newSince(
                List.of(newer1, newer2, marker),
                Optional.of("a"), Optional.of(t0));
        assertEquals(2, result.size());
        assertEquals("c", RssScheduler.entryId(result.get(0)));
        assertEquals("b", RssScheduler.entryId(result.get(1)));
    }

    @Test
    void noMarkerInWindowFallsBackToDateFloor() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        var newer = entry("c", t0.plusMinutes(20));
        var olderButRecentEnough = entry("b", t0.plusMinutes(5));
        var stale = entry("a", t0.minusMinutes(5));
        List<SyndEntry> result = RssScheduler.newSince(
                List.of(newer, olderButRecentEnough, stale),
                Optional.of("missing-marker"), Optional.of(t0));
        assertEquals(2, result.size(), "only items strictly after the date floor");
        assertEquals("c", RssScheduler.entryId(result.get(0)));
        assertEquals("b", RssScheduler.entryId(result.get(1)));
    }

    @Test
    void noMarkerNoDateFloorReturnsOnlyMostRecent() {
        // Defensive — shouldn't actually be reachable in production.
        var e1 = entry("a", OffsetDateTime.now(ZoneOffset.UTC));
        var e2 = entry("b", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        List<SyndEntry> result = RssScheduler.newSince(
                List.of(e1, e2),
                Optional.of("missing"), Optional.empty());
        assertEquals(1, result.size());
        assertEquals("a", RssScheduler.entryId(result.get(0)));
    }

    @Test
    void resultIsCapped() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<SyndEntry> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(entry("item-" + i, now.plusMinutes(50 - i)));
        }
        // Marker not present → should hit the cap before falling through.
        List<SyndEntry> result = RssScheduler.newSince(many, Optional.of("not-here"),
                Optional.of(now.minusYears(1)));
        assertTrue(result.size() <= RssScheduler.MAX_NEW_PER_TICK);
    }

    @Test
    void sortNewestFirst() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var a = entry("a", now.minusHours(2));
        var b = entry("b", now);
        var c = entry("c", now.minusHours(1));
        List<SyndEntry> sorted = RssScheduler.sortNewestFirst(List.of(a, b, c));
        assertEquals("b", RssScheduler.entryId(sorted.get(0)));
        assertEquals("c", RssScheduler.entryId(sorted.get(1)));
        assertEquals("a", RssScheduler.entryId(sorted.get(2)));
    }

    @Test
    void entryIdPrefersUriThenLinkThenTitle() {
        var e = new SyndEntryImpl();
        e.setTitle("the title");
        assertEquals("the title", RssScheduler.entryId(e));
        e.setLink("http://x");
        assertEquals("http://x", RssScheduler.entryId(e));
        e.setUri("urn:1");
        assertEquals("urn:1", RssScheduler.entryId(e));
    }
}
