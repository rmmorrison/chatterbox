package ca.ryanmorrison.chatterbox.features.shout;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

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
    void tryInsert(long channelId, long messageId, String content) {
        dsl.insertInto(SHOUTS)
                .columns(SHOUTS.CHANNEL_ID, SHOUTS.MESSAGE_ID, SHOUTS.CONTENT)
                .values(channelId, messageId, content)
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
     * Picks a random shout from the channel that isn't the message we just
     * received, or empty if no peer exists.
     */
    Optional<String> randomPeer(long channelId, long excludeMessageId) {
        return dsl.select(SHOUTS.CONTENT)
                .from(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.MESSAGE_ID.ne(excludeMessageId))
                .orderBy(DSL.rand())
                .limit(1)
                .fetchOptional(SHOUTS.CONTENT);
    }
}
