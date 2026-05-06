package ca.ryanmorrison.chatterbox.features.frinkiac;

import ca.ryanmorrison.chatterbox.features.frinkiac.dto.SearchResult;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory holding tank for in-flight {@code /frinkiac} interactions.
 *
 * <p>A session is created when the slash command produces hits; subsequent
 * navigation, edit, and post buttons reference it by UUID. Per-frame caption
 * edits are kept in {@code captionOverrides} keyed by the {@link SearchResult}'s
 * {@code id}, so flipping back and forth with prev/next preserves what the user
 * typed.
 *
 * <p>Entries expire after a few minutes; abandoned prompts are reaped on the
 * next access so a forgotten interaction doesn't leak.
 */
final class FrinkiacSessions {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    UUID create(long requestedBy, long channelId, String query, List<SearchResult> hits) {
        evictExpired();
        UUID token = UUID.randomUUID();
        sessions.put(token, new Session(requestedBy, channelId, query, hits,
                /* index = */ 0, new HashMap<>(), Instant.now()));
        return token;
    }

    Optional<Session> get(UUID token) {
        evictExpired();
        return Optional.ofNullable(sessions.get(token));
    }

    void setIndex(UUID token, int index) {
        sessions.computeIfPresent(token, (k, s) -> s.withIndex(index));
    }

    void putCaption(UUID token, long resultId, String caption) {
        sessions.computeIfPresent(token, (k, s) -> {
            s.captionOverrides.put(resultId, caption);
            return s;
        });
    }

    void discard(UUID token) {
        sessions.remove(token);
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        sessions.values().removeIf(s -> s.createdAt.isBefore(cutoff));
    }

    /**
     * Mutable session state. {@code captionOverrides} is mutated in-place;
     * {@code index} is replaced via {@link #withIndex(int)} to keep the record
     * itself effectively-immutable for snapshot reads from {@link #get(UUID)}.
     */
    static final class Session {
        final long requestedBy;
        final long channelId;
        final String query;
        final List<SearchResult> hits;
        private volatile int index;
        final Map<Long, String> captionOverrides;
        final Instant createdAt;

        Session(long requestedBy, long channelId, String query, List<SearchResult> hits,
                int index, Map<Long, String> captionOverrides, Instant createdAt) {
            this.requestedBy = requestedBy;
            this.channelId = channelId;
            this.query = query;
            this.hits = hits;
            this.index = index;
            this.captionOverrides = captionOverrides;
            this.createdAt = createdAt;
        }

        int index() { return index; }

        Session withIndex(int newIndex) {
            this.index = Math.max(0, Math.min(newIndex, hits.size() - 1));
            return this;
        }

        SearchResult current() { return hits.get(index); }

        Optional<String> captionFor(long resultId) {
            return Optional.ofNullable(captionOverrides.get(resultId));
        }
    }
}
