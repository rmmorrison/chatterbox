package ca.ryanmorrison.chatterbox.features.autoreply;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingOverrideStoreTest {

    private final PendingOverrideStore store = new PendingOverrideStore();

    @Test
    void stashAndConsumeRoundTrip() {
        UUID token = store.stash(11L, 22L, "p", "r", "d");
        var pending = store.consume(token).orElseThrow();
        assertEquals(11L, pending.ruleId());
        assertEquals(22L, pending.channelId());
        assertEquals("p", pending.pattern());
        assertEquals("r", pending.response());
        assertEquals("d", pending.description());
    }

    @Test
    void consumeRemovesEntry() {
        UUID token = store.stash(1L, 2L, "p", "r", "d");
        assertTrue(store.consume(token).isPresent());
        assertTrue(store.consume(token).isEmpty(), "second consume should miss");
    }

    @Test
    void discardRemovesEntry() {
        UUID token = store.stash(1L, 2L, "p", "r", "d");
        store.discard(token);
        assertTrue(store.consume(token).isEmpty());
    }

    @Test
    void unknownTokenReturnsEmpty() {
        assertTrue(store.consume(UUID.randomUUID()).isEmpty());
    }
}
