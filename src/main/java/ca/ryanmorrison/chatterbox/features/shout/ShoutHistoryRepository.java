package ca.ryanmorrison.chatterbox.features.shout;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUT_HISTORY;

/**
 * Persists and paginates the bot's emission history. Pagination is keyed on
 * {@code shout_history.id} (monotonically increasing); within a single
 * channel, larger id == more recent.
 *
 * <p>History rows are tied to {@link ShoutRepository#tryInsert} via an FK
 * with {@code ON DELETE CASCADE}, so any code path that hard-deletes a shout
 * — delete event, edit-disqualifies, edit-collision, bulk delete — naturally
 * removes the corresponding history rows too. Soft deletion ({@code
 * deleted_at IS NOT NULL}) is independent: non-moderators see those entries
 * filtered out by {@code includeDeleted = false}, while moderators pass
 * {@code true} to see them.
 */
final class ShoutHistoryRepository {

    private final DSLContext dsl;

    ShoutHistoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Records that the bot emitted {@code shoutId} into {@code channelId}. */
    void record(long channelId, long shoutId) {
        dsl.insertInto(SHOUT_HISTORY)
                .columns(SHOUT_HISTORY.CHANNEL_ID, SHOUT_HISTORY.SHOUT_ID)
                .values(channelId, shoutId)
                .execute();
    }

    Optional<HistoryEntry> findLatest(long channelId, boolean includeDeleted) {
        return fetchOne(
                channelMatch(channelId, includeDeleted),
                SHOUT_HISTORY.ID.desc());
    }

    Optional<HistoryEntry> findOlder(long channelId, long currentHistoryId, boolean includeDeleted) {
        return fetchOne(
                channelMatch(channelId, includeDeleted).and(SHOUT_HISTORY.ID.lt(currentHistoryId)),
                SHOUT_HISTORY.ID.desc());
    }

    Optional<HistoryEntry> findNewer(long channelId, long currentHistoryId, boolean includeDeleted) {
        return fetchOne(
                channelMatch(channelId, includeDeleted).and(SHOUT_HISTORY.ID.gt(currentHistoryId)),
                SHOUT_HISTORY.ID.asc());
    }

    Optional<HistoryEntry> findById(long channelId, long historyId, boolean includeDeleted) {
        return fetchOne(
                channelMatch(channelId, includeDeleted).and(SHOUT_HISTORY.ID.eq(historyId)),
                SHOUT_HISTORY.ID.desc());
    }

    /**
     * Position from the most-recent end (1 = newest), and total channel count.
     * Both numbers are scoped to what the viewer can see, so a moderator's
     * "of N" can be larger than a non-moderator's for the same channel.
     */
    Position position(long channelId, long historyId, boolean includeDeleted) {
        Condition base = channelMatch(channelId, includeDeleted);
        int total = countWhere(base);
        int rank = countWhere(base.and(SHOUT_HISTORY.ID.ge(historyId)));
        return new Position(rank, total);
    }

    record Position(int rank, int total) {}

    private static Condition channelMatch(long channelId, boolean includeDeleted) {
        Condition c = SHOUT_HISTORY.CHANNEL_ID.eq(channelId);
        return includeDeleted ? c : c.and(SHOUTS.DELETED_AT.isNull());
    }

    private int countWhere(Condition where) {
        Integer n = dsl.select(DSL.count())
                .from(SHOUT_HISTORY)
                .join(SHOUTS).on(SHOUTS.ID.eq(SHOUT_HISTORY.SHOUT_ID))
                .where(where)
                .fetchOne(0, Integer.class);
        return n == null ? 0 : n;
    }

    private Optional<HistoryEntry> fetchOne(Condition where, SortField<Long> orderBy) {
        Record record = dsl.select(
                        SHOUT_HISTORY.ID,
                        SHOUT_HISTORY.SHOUT_ID,
                        SHOUTS.MESSAGE_ID,
                        SHOUTS.CONTENT,
                        SHOUTS.AUTHOR_ID,
                        SHOUTS.AUTHORED_AT,
                        SHOUTS.DELETED_AT,
                        SHOUTS.DELETED_BY)
                .from(SHOUT_HISTORY)
                .join(SHOUTS).on(SHOUTS.ID.eq(SHOUT_HISTORY.SHOUT_ID))
                .where(where)
                .orderBy(orderBy)
                .limit(1)
                .fetchOne();
        if (record == null) return Optional.empty();

        OffsetDateTime deletedAt = record.get(SHOUTS.DELETED_AT);
        Long deletedBy = record.get(SHOUTS.DELETED_BY);
        Optional<HistoryEntry.Deletion> deletion = (deletedAt != null && deletedBy != null)
                ? Optional.of(new HistoryEntry.Deletion(deletedBy, deletedAt))
                : Optional.empty();

        return Optional.of(new HistoryEntry(
                record.get(SHOUT_HISTORY.ID),
                record.get(SHOUT_HISTORY.SHOUT_ID),
                record.get(SHOUTS.MESSAGE_ID),
                record.get(SHOUTS.CONTENT),
                record.get(SHOUTS.AUTHOR_ID),
                record.get(SHOUTS.AUTHORED_AT),
                deletion));
    }
}
