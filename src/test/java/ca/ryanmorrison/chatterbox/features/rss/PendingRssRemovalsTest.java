package ca.ryanmorrison.chatterbox.features.rss;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingRssRemovalsTest {

    @Test
    void stashConsumeRoundTrip() {
        var store = new PendingRssRemovals();
        UUID token = store.stash(42L, 1L, 7L);
        Optional<PendingRssRemovals.Pending> p = store.consume(token);
        assertTrue(p.isPresent());
        assertEquals(42L, p.get().feedId());
        assertEquals(7L, p.get().requestedBy());
        // second consume returns empty
        assertTrue(store.consume(token).isEmpty());
    }

    @Test
    void discardRemovesEntry() {
        var store = new PendingRssRemovals();
        UUID token = store.stash(42L, 1L, 7L);
        store.discard(token);
        assertTrue(store.consume(token).isEmpty());
    }

    @Test
    void unknownTokenYieldsEmpty() {
        var store = new PendingRssRemovals();
        assertTrue(store.consume(UUID.randomUUID()).isEmpty());
    }
}
