package ca.ryanmorrison.chatterbox.features.rss;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory holding tank for the two-step "select feed → confirm" remove flow.
 * Stashes the chosen feed id keyed by a fresh UUID; the confirm button hands
 * back the UUID and we apply the deletion. Entries expire after a few minutes
 * so an abandoned prompt doesn't leak.
 */
final class PendingRssRemovals {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    UUID stash(long feedId, long channelId, long requestedBy) {
        evictExpired();
        UUID token = UUID.randomUUID();
        pending.put(token, new Pending(feedId, channelId, requestedBy, Instant.now()));
        return token;
    }

    Optional<Pending> consume(UUID token) {
        evictExpired();
        return Optional.ofNullable(pending.remove(token));
    }

    void discard(UUID token) {
        pending.remove(token);
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        pending.values().removeIf(p -> p.createdAt().isBefore(cutoff));
    }

    record Pending(long feedId, long channelId, long requestedBy, Instant createdAt) {}
}
