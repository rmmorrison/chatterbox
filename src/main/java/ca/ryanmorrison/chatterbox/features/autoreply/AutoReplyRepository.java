package ca.ryanmorrison.chatterbox.features.autoreply;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.IntegrityConstraintViolationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.AUTO_REPLIES;

/**
 * jOOQ-backed access for the {@code auto_replies} table.
 *
 * <p>Concurrency note: duplicate detection relies on the
 * {@code (channel_id, pattern)} unique constraint. Two simultaneous adds with
 * identical patterns in the same channel are race-safe — one wins, the other
 * raises {@link org.jooq.exception.IntegrityConstraintViolationException}
 * which the caller surfaces as the existing-rule prompt.
 */
final class AutoReplyRepository {

    private final DSLContext dsl;

    AutoReplyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts and returns the generated id, or empty when
     * {@code (channel_id, pattern)} already exists. Catches the unique
     * constraint violation rather than relying on dialect-specific
     * {@code ON CONFLICT} semantics — SQLite's {@code INSERT OR IGNORE}
     * with {@code RETURNING} doesn't reliably distinguish the conflict case.
     */
    Optional<Long> insert(long channelId, String pattern, String response, String description, long createdBy) {
        try {
            Long id = dsl.insertInto(AUTO_REPLIES)
                    .columns(AUTO_REPLIES.CHANNEL_ID, AUTO_REPLIES.PATTERN, AUTO_REPLIES.RESPONSE,
                             AUTO_REPLIES.DESCRIPTION, AUTO_REPLIES.CREATED_BY)
                    .values(channelId, pattern, response, description, createdBy)
                    .returning(AUTO_REPLIES.ID)
                    .fetchOne(AUTO_REPLIES.ID);
            return Optional.ofNullable(id);
        } catch (IntegrityConstraintViolationException e) {
            return Optional.empty();
        }
    }

    /**
     * Updates the rule, stamping the editor and edited-at. Returns the number
     * of rows updated (0 if the row vanished, 1 normally).
     */
    int update(long id, String pattern, String response, String description, long editedBy) {
        return dsl.update(AUTO_REPLIES)
                .set(AUTO_REPLIES.PATTERN, pattern)
                .set(AUTO_REPLIES.RESPONSE, response)
                .set(AUTO_REPLIES.DESCRIPTION, description)
                .set(AUTO_REPLIES.EDITED_BY, editedBy)
                .set(AUTO_REPLIES.EDITED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(AUTO_REPLIES.ID.eq(id))
                .execute();
    }

    int deleteById(long id) {
        return dsl.deleteFrom(AUTO_REPLIES).where(AUTO_REPLIES.ID.eq(id)).execute();
    }

    Optional<AutoReplyRule> findById(long id) {
        return dsl.selectFrom(AUTO_REPLIES).where(AUTO_REPLIES.ID.eq(id))
                .fetchOptional()
                .map(AutoReplyRepository::toRule);
    }

    Optional<AutoReplyRule> findByChannelAndPattern(long channelId, String pattern) {
        return dsl.selectFrom(AUTO_REPLIES)
                .where(AUTO_REPLIES.CHANNEL_ID.eq(channelId))
                .and(AUTO_REPLIES.PATTERN.eq(pattern))
                .fetchOptional()
                .map(AutoReplyRepository::toRule);
    }

    /** All rules for the channel, ordered by id ascending so match precedence is stable. */
    List<AutoReplyRule> listByChannel(long channelId) {
        return dsl.selectFrom(AUTO_REPLIES)
                .where(AUTO_REPLIES.CHANNEL_ID.eq(channelId))
                .orderBy(AUTO_REPLIES.ID.asc())
                .fetch()
                .map(AutoReplyRepository::toRule);
    }

    /**
     * The {@code limit} most recently created or edited rules for the channel,
     * for surfacing in a Discord string-select (capped at 25 by Discord).
     * Most-recently-touched first.
     */
    List<AutoReplyRule> listRecentByChannel(long channelId, int limit) {
        var touchedAt = org.jooq.impl.DSL.coalesce(AUTO_REPLIES.EDITED_AT, AUTO_REPLIES.CREATED_AT);
        return dsl.selectFrom(AUTO_REPLIES)
                .where(AUTO_REPLIES.CHANNEL_ID.eq(channelId))
                .orderBy(touchedAt.desc(), AUTO_REPLIES.ID.desc())
                .limit(limit)
                .fetch()
                .map(AutoReplyRepository::toRule);
    }

    int countByChannel(long channelId) {
        return dsl.fetchCount(AUTO_REPLIES, AUTO_REPLIES.CHANNEL_ID.eq(channelId));
    }

    private static AutoReplyRule toRule(Record r) {
        Long editedBy = r.get(AUTO_REPLIES.EDITED_BY);
        OffsetDateTime editedAt = r.get(AUTO_REPLIES.EDITED_AT);
        Optional<AutoReplyRule.EditAudit> edit = (editedBy != null && editedAt != null)
                ? Optional.of(new AutoReplyRule.EditAudit(editedBy, editedAt))
                : Optional.empty();
        return new AutoReplyRule(
                r.get(AUTO_REPLIES.ID),
                r.get(AUTO_REPLIES.CHANNEL_ID),
                r.get(AUTO_REPLIES.PATTERN),
                r.get(AUTO_REPLIES.RESPONSE),
                r.get(AUTO_REPLIES.DESCRIPTION),
                r.get(AUTO_REPLIES.CREATED_BY),
                r.get(AUTO_REPLIES.CREATED_AT),
                edit);
    }
}
