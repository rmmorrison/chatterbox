package ca.ryanmorrison.chatterbox.features.shout;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;

/**
 * jOOQ-backed access for the {@code shouts} table.
 *
 * <p>Concurrency note: insert deduplication and edit-collision resolution rely
 * on the {@code (channel_id, content)} unique constraint plus a transaction,
 * not application-level locking. Two simultaneous shouts with identical
 * content in the same channel are race-safe.
 *
 * <p>Soft deletion: rows with {@code deleted_at IS NOT NULL} are excluded from
 * {@link #randomPeer} entirely (regardless of viewer) and from non-moderator
 * history queries. {@link #softDelete} is idempotent — once a shout is marked
 * deleted, a second click does not overwrite the original deleter or
 * timestamp.
 */
final class ShoutRepository {

    private final DSLContext dsl;

    ShoutRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts a shout, ignoring the row if {@code (channel_id, content)} is
     * already present in this channel.
     */
    void tryInsert(long channelId, long messageId, String content,
                   long authorId, OffsetDateTime authoredAt) {
        dsl.insertInto(SHOUTS)
                .columns(SHOUTS.CHANNEL_ID, SHOUTS.MESSAGE_ID, SHOUTS.CONTENT,
                         SHOUTS.AUTHOR_ID, SHOUTS.AUTHORED_AT)
                .values(channelId, messageId, content, authorId, authoredAt)
                .onDuplicateKeyIgnore()
                .execute();
    }

    Optional<String> findContentByMessageId(long messageId) {
        return dsl.select(SHOUTS.CONTENT)
                .from(SHOUTS)
                .where(SHOUTS.MESSAGE_ID.eq(messageId))
                .fetchOptional(SHOUTS.CONTENT);
    }

    void deleteByMessageId(long messageId) {
        dsl.deleteFrom(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(messageId)).execute();
    }

    void deleteByMessageIds(List<Long> messageIds) {
        if (messageIds.isEmpty()) return;
        dsl.deleteFrom(SHOUTS).where(SHOUTS.MESSAGE_ID.in(messageIds)).execute();
    }

    /**
     * Updates the stored content for {@code messageId}, falling back to a
     * delete when the new content already exists for another message in the
     * same channel. Runs in a single transaction.
     */
    void updateOrDeleteOnCollision(long channelId, long messageId, String newContent) {
        dsl.transaction(cfg -> {
            var ctx = cfg.dsl();
            boolean collision = ctx.fetchExists(
                    ctx.selectOne()
                       .from(SHOUTS)
                       .where(SHOUTS.CHANNEL_ID.eq(channelId))
                       .and(SHOUTS.CONTENT.eq(newContent))
                       .and(SHOUTS.MESSAGE_ID.ne(messageId)));
            if (collision) {
                ctx.deleteFrom(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(messageId)).execute();
            } else {
                ctx.update(SHOUTS).set(SHOUTS.CONTENT, newContent).where(SHOUTS.MESSAGE_ID.eq(messageId)).execute();
            }
        });
    }

    /**
     * Picks a random non-deleted shout from the channel that isn't the message
     * we just received, or empty if no peer exists.
     */
    Optional<Peer> randomPeer(long channelId, long excludeMessageId) {
        var record = dsl.select(SHOUTS.ID, SHOUTS.CONTENT, SHOUTS.AUTHOR_ID, SHOUTS.AUTHORED_AT)
                .from(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.MESSAGE_ID.ne(excludeMessageId))
                .and(SHOUTS.DELETED_AT.isNull())
                .orderBy(DSL.rand())
                .limit(1)
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(new Peer(
                record.get(SHOUTS.ID),
                record.get(SHOUTS.CONTENT),
                record.get(SHOUTS.AUTHOR_ID),
                record.get(SHOUTS.AUTHORED_AT)));
    }

    /**
     * Marks the shout as soft-deleted. No-op when the shout is already
     * deleted, so the original deleter and timestamp are preserved on a
     * fast-clicker race.
     */
    void softDelete(long shoutId, long deletedBy) {
        dsl.update(SHOUTS)
                .set(SHOUTS.DELETED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(SHOUTS.DELETED_BY, deletedBy)
                .where(SHOUTS.ID.eq(shoutId))
                .and(SHOUTS.DELETED_AT.isNull())
                .execute();
    }

    /** Clears the soft-delete flag and the deleter user ID. */
    void restore(long shoutId) {
        dsl.update(SHOUTS)
                .setNull(SHOUTS.DELETED_AT)
                .setNull(SHOUTS.DELETED_BY)
                .where(SHOUTS.ID.eq(shoutId))
                .execute();
    }
}
