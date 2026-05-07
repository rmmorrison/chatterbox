package ca.ryanmorrison.chatterbox.config.runtime;

import org.jooq.DSLContext;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.RUNTIME_CONFIG;

/**
 * jOOQ-backed access for the {@code runtime_config} table.
 *
 * <p>Stores raw string values keyed by {@code (guild_id, key)}; the typed
 * interpretation lives in {@link ConfigKey}/{@link ConfigType}, on the read
 * side. {@link #put} is a true upsert so {@code /config set} on an
 * already-overridden key just replaces the value without a separate
 * "exists?" round-trip.
 */
public final class RuntimeConfigRepository {

    private final DSLContext dsl;

    public RuntimeConfigRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Raw stored value for {@code (guildId, key)}, or empty if none. */
    public Optional<String> findValue(long guildId, String key) {
        return dsl.select(RUNTIME_CONFIG.VALUE)
                .from(RUNTIME_CONFIG)
                .where(RUNTIME_CONFIG.GUILD_ID.eq(guildId))
                .and(RUNTIME_CONFIG.KEY.eq(key))
                .fetchOptional(RUNTIME_CONFIG.VALUE);
    }

    /** Every override for {@code guildId} as a {key → raw value} map; empty if none. */
    public Map<String, String> findAllForGuild(long guildId) {
        Map<String, String> out = new HashMap<>();
        dsl.select(RUNTIME_CONFIG.KEY, RUNTIME_CONFIG.VALUE)
                .from(RUNTIME_CONFIG)
                .where(RUNTIME_CONFIG.GUILD_ID.eq(guildId))
                .fetch()
                .forEach(r -> out.put(r.value1(), r.value2()));
        return out;
    }

    /** Insert-or-update the override row for {@code (guildId, key)}. */
    public void put(long guildId, String key, String value, long updatedBy, OffsetDateTime updatedAt) {
        dsl.insertInto(RUNTIME_CONFIG)
                .columns(RUNTIME_CONFIG.GUILD_ID, RUNTIME_CONFIG.KEY, RUNTIME_CONFIG.VALUE,
                         RUNTIME_CONFIG.UPDATED_BY, RUNTIME_CONFIG.UPDATED_AT)
                .values(guildId, key, value, updatedBy, updatedAt)
                .onConflict(RUNTIME_CONFIG.GUILD_ID, RUNTIME_CONFIG.KEY)
                .doUpdate()
                .set(RUNTIME_CONFIG.VALUE, value)
                .set(RUNTIME_CONFIG.UPDATED_BY, updatedBy)
                .set(RUNTIME_CONFIG.UPDATED_AT, updatedAt)
                .execute();
    }

    /** Returns {@code true} if a row was actually deleted. */
    public boolean delete(long guildId, String key) {
        return dsl.deleteFrom(RUNTIME_CONFIG)
                .where(RUNTIME_CONFIG.GUILD_ID.eq(guildId))
                .and(RUNTIME_CONFIG.KEY.eq(key))
                .execute() > 0;
    }
}
