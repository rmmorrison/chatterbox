package ca.ryanmorrison.chatterbox.config.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The runtime-config read/write API exposed to feature modules and to the
 * {@code /config} slash command.
 *
 * <h2>Resolution order</h2>
 * For every lookup, in order:
 * <ol>
 *   <li>per-guild override row in {@code runtime_config}</li>
 *   <li>environment variable named by {@link ConfigKey#envVar()}</li>
 *   <li>{@link ConfigKey#defaultRaw()} on the key itself</li>
 * </ol>
 *
 * <h2>Caching</h2>
 * Per-guild overrides are read once per guild and held in an in-process
 * {@link ConcurrentHashMap}. {@code /config set} and {@code /config unset}
 * call {@link #invalidate(long)} after writing, so the next read picks up
 * the change without a stale window. The cache is intentionally
 * unbounded — guilds are few and entries are tiny — but it does mean that
 * if a second bot instance ever shared this database, it wouldn't see the
 * other's changes until restart. Today we run one instance.
 *
 * <h2>Threading</h2>
 * Reads happen from JDA listener threads (potentially many per second on
 * busy channels); writes happen on the slash-command handler thread.
 * The cache map handles concurrency; the per-guild value map is built
 * fresh inside {@link #cacheFor(long)} and never mutated after publication.
 *
 * <p>The load deliberately runs <em>outside</em> the map's lock. Doing it
 * inside {@code computeIfAbsent} held a ConcurrentHashMap bin lock across a
 * database round-trip, so every other guild hashing to the same bin stalled
 * behind it — on SQLite, where the pool is a single connection, for up to
 * Hikari's connection timeout. A publish-if-unchanged counter
 * ({@link #invalidations}) preserves what that lock used to give for free:
 * a snapshot loaded before a concurrent {@code set} is discarded rather than
 * published over the newer value.
 */
public final class RuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfig.class);

    private final ConfigRegistry registry;
    private final RuntimeConfigRepository repository;
    private final Function<String, String> envLookup;
    private final Clock clock;

    /**
     * Per-guild snapshot of override rows. Absence of the key means "not yet
     * loaded" — a ConcurrentHashMap cannot hold null values, so there is no
     * null-means-unloaded state to check for.
     */
    private final ConcurrentHashMap<Long, Snapshot> cache = new ConcurrentHashMap<>();

    /**
     * Bumped by every {@link #invalidate(long)}. A load that started before the
     * bump is not allowed to publish, which is what stops a slow read from
     * resurrecting a value that {@code /config set} just replaced. Global
     * rather than per-guild: writes are rare, so the occasional redundant
     * re-load on an unrelated guild is cheaper than tracking versions per key.
     */
    private final AtomicLong invalidations = new AtomicLong();

    public RuntimeConfig(ConfigRegistry registry,
                         RuntimeConfigRepository repository,
                         Function<String, String> envLookup,
                         Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.envLookup = envLookup;
        this.clock = clock;
    }

    public RuntimeConfig(ConfigRegistry registry, RuntimeConfigRepository repository) {
        this(registry, repository, System::getenv, Clock.systemUTC());
    }

    public ConfigRegistry registry() { return registry; }

    /** Typed effective value of {@code key} for {@code guildId}. */
    public <T> T get(long guildId, ConfigKey<T> key) {
        return resolve(guildId, key).value();
    }

    /** Typed effective value with the {@link ConfigSource} it came from. */
    public <T> Resolved<T> resolve(long guildId, ConfigKey<T> key) {
        String raw = cacheFor(guildId).get(key.key());
        if (raw != null) {
            T parsed = tryParse(raw, key, ConfigSource.GUILD_OVERRIDE);
            if (parsed != null) return new Resolved<>(parsed, ConfigSource.GUILD_OVERRIDE, raw);
        }
        String envRaw = envLookup.apply(key.envVar());
        if (envRaw != null && !envRaw.isBlank()) {
            T parsed = tryParse(envRaw, key, ConfigSource.ENV_VAR);
            if (parsed != null) return new Resolved<>(parsed, ConfigSource.ENV_VAR, envRaw);
        }
        return new Resolved<>(key.defaultValue(), ConfigSource.DEFAULT, key.defaultRaw());
    }

    /** Convenience for {@code get(guildId, key)} when {@code T = Boolean}. */
    public boolean bool(long guildId, ConfigKey<Boolean> key) {
        return get(guildId, key);
    }

    /** Convenience for {@code get(guildId, key)} when {@code T = Integer}. */
    public int integer(long guildId, ConfigKey<Integer> key) {
        return get(guildId, key);
    }

    /**
     * Validates {@code rawValue} and persists it as the override for
     * {@code (guildId, key.key())}. Throws {@link ConfigType.InvalidValueException}
     * with a user-facing message if the value is rejected.
     */
    public void set(long guildId, ConfigKey<?> key, String rawValue, long updatedBy) {
        // Throws on invalid input; caller surfaces the message in the slash reply.
        key.type().parse(rawValue);
        repository.put(guildId, key.key(), rawValue, updatedBy, OffsetDateTime.now(clock));
        invalidate(guildId);
    }

    /** Drops the override row for {@code (guildId, key.key())}. Returns whether one existed. */
    public boolean unset(long guildId, ConfigKey<?> key) {
        boolean removed = repository.delete(guildId, key.key());
        invalidate(guildId);
        return removed;
    }

    /** Drops cached per-guild overrides; next read re-loads. */
    public void invalidate(long guildId) {
        // Bump before removing, not after: a loader that finishes in between
        // would otherwise see an unchanged counter and publish its stale
        // snapshot straight back into the map.
        invalidations.incrementAndGet();
        cache.remove(guildId);
    }

    private Map<String, String> cacheFor(long guildId) {
        Snapshot cached = cache.get(guildId);
        if (cached != null) return cached.values();

        long observed = invalidations.get();
        Map<String, String> loaded;
        try {
            loaded = Map.copyOf(repository.findAllForGuild(guildId));
        } catch (RuntimeException e) {
            // Same tolerance as tryParse below: a database blip on this path
            // would otherwise propagate into a JDA listener thread and kill
            // message handling. Fall through to env vars and defaults, and
            // don't cache the failure.
            log.warn("Couldn't load runtime config for guild {}; using env/defaults: {}",
                    guildId, e.toString());
            return Map.of();
        }

        cache.compute(guildId, (k, existing) -> {
            if (existing != null) return existing;                  // another thread won the race
            if (invalidations.get() != observed) return null;       // invalidated mid-load; drop it
            return new Snapshot(loaded);
        });
        return loaded;
    }

    /** Immutable published snapshot of one guild's override rows. */
    private record Snapshot(Map<String, String> values) {}

    /**
     * Tolerant parse for read paths: a stored value that's somehow invalid
     * (a manual DB edit, a key whose type was tightened) shouldn't crash
     * production message handling. Logs and returns null so the caller
     * falls through to the next source.
     */
    private <T> T tryParse(String raw, ConfigKey<T> key, ConfigSource source) {
        try {
            return key.type().parse(raw);
        } catch (ConfigType.InvalidValueException e) {
            log.warn("Ignoring invalid runtime-config value for key={} source={}: {}",
                    key.key(), source, e.getMessage());
            return null;
        }
    }

    /** A typed value plus the source that produced it. */
    public record Resolved<T>(T value, ConfigSource source, String rawValue) {
        public Optional<String> overrideRaw() {
            return source == ConfigSource.GUILD_OVERRIDE ? Optional.of(rawValue) : Optional.empty();
        }
    }
}
