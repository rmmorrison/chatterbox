package ca.ryanmorrison.chatterbox.features.shout;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.SortField;

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
 * with {@code ON DELETE CASCADE}, so any code path that removes a shout —
 * delete event, edit-disqualifies, edit-collision, bulk delete — naturally
 * removes the corresponding history rows too.
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

    Optional<HistoryEntry> findLatest(long channelId) {
        return fetchOne(SHOUT_HISTORY.CHANNEL_ID.eq(channelId), SHOUT_HISTORY.ID.desc());
    }

    Optional<HistoryEntry> findOlder(long channelId, long currentHistoryId) {
        return fetchOne(
                SHOUT_HISTORY.CHANNEL_ID.eq(channelId).and(SHOUT_HISTORY.ID.lt(currentHistoryId)),
                SHOUT_HISTORY.ID.desc());
    }

    Optional<HistoryEntry> findNewer(long channelId, long currentHistoryId) {
        return fetchOne(
                SHOUT_HISTORY.CHANNEL_ID.eq(channelId).and(SHOUT_HISTORY.ID.gt(currentHistoryId)),
                SHOUT_HISTORY.ID.asc());
    }

    Optional<HistoryEntry> findById(long channelId, long historyId) {
        return fetchOne(
                SHOUT_HISTORY.CHANNEL_ID.eq(channelId).and(SHOUT_HISTORY.ID.eq(historyId)),
                SHOUT_HISTORY.ID.desc());
    }

    /** Position from the most-recent end (1 = newest), and total channel count. */
    Position position(long channelId, long historyId) {
        int total = dsl.fetchCount(SHOUT_HISTORY, SHOUT_HISTORY.CHANNEL_ID.eq(channelId));
        int rank = dsl.fetchCount(SHOUT_HISTORY,
                SHOUT_HISTORY.CHANNEL_ID.eq(channelId).and(SHOUT_HISTORY.ID.ge(historyId)));
        return new Position(rank, total);
    }

    record Position(int rank, int total) {}

    private Optional<HistoryEntry> fetchOne(Condition where, SortField<Long> orderBy) {
        Record4<Long, String, Long, OffsetDateTime> record =
                dsl.select(SHOUT_HISTORY.ID, SHOUTS.CONTENT, SHOUTS.AUTHOR_ID, SHOUTS.AUTHORED_AT)
                        .from(SHOUT_HISTORY)
                        .join(SHOUTS).on(SHOUTS.ID.eq(SHOUT_HISTORY.SHOUT_ID))
                        .where(where)
                        .orderBy(orderBy)
                        .limit(1)
                        .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(new HistoryEntry(
                record.value1(),
                record.value2(),
                record.value3(),
                record.value4()));
    }
}
