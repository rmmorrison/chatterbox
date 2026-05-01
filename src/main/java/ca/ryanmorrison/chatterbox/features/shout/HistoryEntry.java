package ca.ryanmorrison.chatterbox.features.shout;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * A single shout-history record joined with its originating shout. Consumed
 * by {@link ShoutHistoryView} to render the slash-command embed.
 *
 * @param historyId   primary key in {@code shout_history}; used as the
 *                    pagination cursor.
 * @param shoutId     primary key in {@code shouts}; used by moderation
 *                    actions (delete / restore).
 * @param content     the shout text the bot emitted.
 * @param authorId    Discord user ID of the original author.
 * @param authoredAt  when the original message was first written.
 * @param deletion    present iff the shout has been soft-deleted by a
 *                    moderator. Non-moderator history queries filter these
 *                    out, so a non-moderator never observes a present value.
 */
record HistoryEntry(
        long historyId,
        long shoutId,
        String content,
        long authorId,
        OffsetDateTime authoredAt,
        Optional<Deletion> deletion) {

    /** Audit info captured when a moderator soft-deletes a shout. */
    record Deletion(long deletedBy, OffsetDateTime deletedAt) {}
}
