package ca.ryanmorrison.chatterbox.features.autoreply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

    private final AutoReplyRepository repo;
    private final long timeoutMillis;
    private final ConcurrentHashMap<Long, List<CompiledRule>> cache = new ConcurrentHashMap<>();

    AutoReplyMatcher(AutoReplyRepository repo) {
        this(repo, DEFAULT_TIMEOUT_MILLIS);
    }

    AutoReplyMatcher(AutoReplyRepository repo, long timeoutMillis) {
        this.repo = repo;
        this.timeoutMillis = timeoutMillis;
    }

    /** Returns the response of the first rule whose pattern matches, or empty. */
    Optional<String> firstMatch(long channelId, String content) {
        List<CompiledRule> rules = cache.computeIfAbsent(channelId, this::loadAndCompile);
        for (CompiledRule rule : rules) {
            try {
                var input = WatchdogCharSequence.wrap(content, timeoutMillis);
                if (rule.pattern().matcher(input).find()) {
                    return Optional.of(rule.response());
                }
            } catch (WatchdogCharSequence.RegexTimeoutException e) {
                log.warn("Regex timeout for rule {} in channel {}; skipping.", rule.id(), channelId);
            }
        }
        return Optional.empty();
    }

    /** Drops the cached compilation for {@code channelId}, forcing a refresh on next match. */
    void invalidate(long channelId) {
        cache.remove(channelId);
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
