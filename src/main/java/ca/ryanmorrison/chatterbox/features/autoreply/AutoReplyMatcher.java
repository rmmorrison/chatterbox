package ca.ryanmorrison.chatterbox.features.autoreply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Thread-safe per-channel cache of compiled regex rules. Compiles each rule's
 * {@link Pattern} once on first read and re-uses it across messages. Writers
 * (the slash-command handler) call {@link #invalidate(long)} after every
 * add/edit/delete so the next match-check re-loads from the database.
 *
 * <p>Match evaluation runs against a {@link WatchdogCharSequence} so a
 * pathological pattern can't pin a CPU forever — see
 * {@link #DEFAULT_TIMEOUT_MILLIS}.
 */
final class AutoReplyMatcher {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyMatcher.class);

    static final long DEFAULT_TIMEOUT_MILLIS = 100L;

    /**
     * Ceiling on cached channels. The cache previously grew an entry for every
     * channel that ever received a message — including the overwhelming
     * majority with no rules at all — and {@link #invalidate(long)} only fires
     * on a write, so deleted channels stayed forever.
     */
    static final int MAX_CACHED_CHANNELS = 1_000;

    private final AutoReplyRepository repo;
    private final long timeoutMillis;
    private final ConcurrentHashMap<Long, List<CompiledRule>> cache = new ConcurrentHashMap<>();

    /**
     * Bumped by every {@link #invalidate(long)} so a load that began before a
     * write can't publish its stale compilation. See {@link #rulesFor}.
     */
    private final AtomicLong invalidations = new AtomicLong();

    AutoReplyMatcher(AutoReplyRepository repo) {
        this(repo, DEFAULT_TIMEOUT_MILLIS);
    }

    AutoReplyMatcher(AutoReplyRepository repo, long timeoutMillis) {
        this.repo = repo;
        this.timeoutMillis = timeoutMillis;
    }

    /** Returns the response of the first rule whose pattern matches, or empty. */
    Optional<String> firstMatch(long channelId, String content) {
        List<CompiledRule> rules = rulesFor(channelId);
        // One budget for the whole message, not one per rule. Per-rule
        // deadlines meant N pathological rules cost N x timeout on a JDA
        // gateway thread for every message, and nothing caps rules per channel.
        long deadline = WatchdogCharSequence.deadlineFrom(timeoutMillis);
        for (CompiledRule rule : rules) {
            try {
                var input = WatchdogCharSequence.wrapUntil(content, deadline);
                if (rule.pattern().matcher(input).find()) {
                    return Optional.of(rule.response());
                }
            } catch (WatchdogCharSequence.RegexTimeoutException e) {
                // The budget covers the message, so it's spent — stop rather
                // than letting the remaining rules each start a fresh match
                // that will immediately time out too.
                log.warn("Regex budget exhausted at rule {} in channel {}; skipping the rest.",
                        rule.id(), channelId);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Drops the cached compilation for {@code channelId}, forcing a refresh on next match. */
    void invalidate(long channelId) {
        // Bump before removing so a load in flight can't republish stale rules.
        invalidations.incrementAndGet();
        cache.remove(channelId);
    }

    /**
     * Cached rules for {@code channelId}, loading outside the map's lock.
     *
     * <p>The load used to sit inside {@code computeIfAbsent}, which holds a
     * ConcurrentHashMap bin lock for the duration — a database round-trip on
     * the message hot path, blocking every other channel in the same bin.
     */
    private List<CompiledRule> rulesFor(long channelId) {
        List<CompiledRule> cached = cache.get(channelId);
        if (cached != null) return cached;

        long observed = invalidations.get();
        List<CompiledRule> loaded;
        try {
            loaded = loadAndCompile(channelId);
        } catch (RuntimeException e) {
            // A database blip must not take down message handling; skip
            // auto-replies for this message and try again on the next one.
            log.warn("Couldn't load auto-reply rules for channel {}: {}", channelId, e.toString());
            return List.of();
        }

        evictIfFull();
        cache.compute(channelId, (k, existing) -> {
            if (existing != null) return existing;
            if (invalidations.get() != observed) return null;
            return loaded;
        });
        return loaded;
    }

    /**
     * Keeps the cache under {@link #MAX_CACHED_CHANNELS}. Eviction order is
     * arbitrary — ConcurrentHashMap has none to offer — which is fine because
     * a dropped entry costs one re-load and nothing depends on which one goes.
     */
    private void evictIfFull() {
        if (cache.size() < MAX_CACHED_CHANNELS) return;
        var it = cache.keySet().iterator();
        while (cache.size() >= MAX_CACHED_CHANNELS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private List<CompiledRule> loadAndCompile(long channelId) {
        return repo.listByChannel(channelId).stream()
                .flatMap(rule -> tryCompile(rule).stream())
                .toList();
    }

    private static Optional<CompiledRule> tryCompile(AutoReplyRule rule) {
        try {
            return Optional.of(new CompiledRule(rule.id(), Pattern.compile(rule.pattern()), rule.response()));
        } catch (PatternSyntaxException e) {
            // Shouldn't happen — patterns are validated on write — but a stored row
            // may pre-date a stricter validator, or have been hand-edited. Skip it.
            log.warn("Skipping rule {} with invalid pattern: {}", rule.id(), e.getMessage());
            return Optional.empty();
        }
    }

    record CompiledRule(long id, Pattern pattern, String response) {}
}
