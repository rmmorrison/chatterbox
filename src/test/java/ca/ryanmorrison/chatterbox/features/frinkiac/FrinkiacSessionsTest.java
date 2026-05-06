package ca.ryanmorrison.chatterbox.features.frinkiac;

import ca.ryanmorrison.chatterbox.features.frinkiac.dto.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrinkiacSessionsTest {

    private static SearchResult hit(long id, long ts) {
        return new SearchResult(id, "S01E01", ts, "line " + id, "Title");
    }

    @Test
    void createReturnsSessionWithFirstHit() {
        FrinkiacSessions store = new FrinkiacSessions();
        UUID token = store.create(1L, 2L, "donuts",
                List.of(hit(10, 1000), hit(11, 2000), hit(12, 3000)));
        var s = store.get(token).orElseThrow();
        assertEquals(0, s.index());
        assertEquals(10L, s.current().id());
        assertEquals("donuts", s.query);
    }

    @Test
    void setIndexClampsAtBoundaries() {
        FrinkiacSessions store = new FrinkiacSessions();
        UUID token = store.create(1L, 2L, "q", List.of(hit(10, 1000), hit(11, 2000)));
        store.setIndex(token, -5);
        assertEquals(0, store.get(token).orElseThrow().index());
        store.setIndex(token, 99);
        assertEquals(1, store.get(token).orElseThrow().index());
    }

    @Test
    void putCaptionPersistsPerFrame() {
        FrinkiacSessions store = new FrinkiacSessions();
        UUID token = store.create(1L, 2L, "q", List.of(hit(10, 1000), hit(11, 2000)));
        store.putCaption(token, 10L, "edited!");
        var s = store.get(token).orElseThrow();
        assertEquals("edited!", s.captionFor(10L).orElseThrow());
        assertTrue(s.captionFor(11L).isEmpty());
    }

    @Test
    void discardRemovesSession() {
        FrinkiacSessions store = new FrinkiacSessions();
        UUID token = store.create(1L, 2L, "q", List.of(hit(10, 1000)));
        store.discard(token);
        assertTrue(store.get(token).isEmpty());
    }
}
