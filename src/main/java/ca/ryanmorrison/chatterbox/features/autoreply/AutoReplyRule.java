package ca.ryanmorrison.chatterbox.features.autoreply;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * A configured auto-reply for a channel.
 *
 * @param edit  audit info captured the last time the rule was modified;
 *              empty if the rule has never been edited.
 */
record AutoReplyRule(
        long id,
        long channelId,
        String pattern,
        String response,
        String description,
        long createdBy,
        OffsetDateTime createdAt,
        Optional<EditAudit> edit) {

    /** Audit info captured when a moderator edits an existing rule. */
    record EditAudit(long editedBy, OffsetDateTime editedAt) {}
}
