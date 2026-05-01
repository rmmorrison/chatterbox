package ca.ryanmorrison.chatterbox.features.autoreply;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory holding tank for the two-step "your pattern matches an existing
 * rule, override?" flow. The submitted modal payload is stashed here when we
 * detect a duplicate; the override-confirm button hands back the UUID and
 * we apply the saved payload.
 *
 * <p>Entries expire after a few minutes so an abandoned prompt doesn't leak
 * memory or get applied later if the user rediscovers the buttons.
 */
final class PendingOverrideStore {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    UUID stash(long ruleId, long channelId, String pattern, String response, String description) {
        evictExpired();
        UUID token = UUID.randomUUID();
        pending.put(token, new Pending(ruleId, channelId, pattern, response, description, Instant.now()));
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

    record Pending(long ruleId, long channelId, String pattern, String response, String description, Instant createdAt) {}
}
