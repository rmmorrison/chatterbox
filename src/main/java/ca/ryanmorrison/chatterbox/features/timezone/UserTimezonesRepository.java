package ca.ryanmorrison.chatterbox.features.timezone;

import org.jooq.DSLContext;

import java.time.OffsetDateTime;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.USER_TIMEZONES;

/**
 * jOOQ-backed access for the {@code user_timezones} table.
 *
 * <p>Stores one row per Discord user — their preferred IANA zone string —
 * keyed by user id, not by {@code (user_id, guild_id)}: a person's
 * timezone follows them, not the server they're posting from. {@link #put}
 * is a true upsert so {@code /timezone set} on an already-set user just
 * replaces the value.
 */
public final class UserTimezonesRepository {

    private final DSLContext dsl;

    public UserTimezonesRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Stored zone id (IANA name or offset string), or empty if the user has none set. */
    public Optional<String> find(long userId) {
        return dsl.select(USER_TIMEZONES.ZONE_ID)
                .from(USER_TIMEZONES)
                .where(USER_TIMEZONES.USER_ID.eq(userId))
                .fetchOptional(USER_TIMEZONES.ZONE_ID);
    }

    /** Insert-or-update the zone for {@code userId}. */
    public void put(long userId, String zoneId, OffsetDateTime updatedAt) {
        dsl.insertInto(USER_TIMEZONES)
                .columns(USER_TIMEZONES.USER_ID, USER_TIMEZONES.ZONE_ID, USER_TIMEZONES.UPDATED_AT)
                .values(userId, zoneId, updatedAt)
                .onConflict(USER_TIMEZONES.USER_ID)
                .doUpdate()
                .set(USER_TIMEZONES.ZONE_ID, zoneId)
                .set(USER_TIMEZONES.UPDATED_AT, updatedAt)
                .execute();
    }

    /** Returns {@code true} if a row was actually deleted. */
    public boolean delete(long userId) {
        return dsl.deleteFrom(USER_TIMEZONES)
                .where(USER_TIMEZONES.USER_ID.eq(userId))
                .execute() > 0;
    }
}
