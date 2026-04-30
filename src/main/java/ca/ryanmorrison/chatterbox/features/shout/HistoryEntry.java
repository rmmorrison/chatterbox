package ca.ryanmorrison.chatterbox.features.shout;

import java.time.OffsetDateTime;

/**
 * A single shout-history record joined with its originating shout. Consumed
 * by {@link ShoutHistoryView} to render the slash-command embed.
 *
 * @param historyId   primary key in {@code shout_history}; used as the
 *                    pagination cursor.
 * @param content     the shout text the bot emitted.
 * @param authorId    Discord user ID of the original author.
 * @param authoredAt  when the original message was first written.
 */
record HistoryEntry(long historyId, String content, long authorId, OffsetDateTime authoredAt) {}
