package ca.ryanmorrison.chatterbox.features.shout;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Aggregated stats for a single channel, computed once and rendered into the
 * {@code /shout stats} embed. All counts and lookups exclude soft-deleted
 * shouts so the figures match what users can browse via {@code /shout history}.
 *
 * @param totalShouts        live shouts in the channel
 * @param distinctShouters   unique authors with at least one live shout
 * @param shoutsLast7Days    live shouts authored in the trailing seven days
 * @param topShouters        top contributors, descending by count; capped by
 *                           the caller (typically 3)
 * @param oldest             earliest live shout, by {@code authored_at}
 * @param newest             latest live shout, by {@code authored_at}
 * @param longest            live shout with the most characters of {@code content}
 * @param mostReplayed       shout with the most {@code shout_history} rows
 *                           pointing at it; empty if no live shout has been
 *                           replayed yet
 */
record ShoutStats(
        int totalShouts,
        int distinctShouters,
        int shoutsLast7Days,
        List<ShouterCount> topShouters,
        Optional<ShoutSummary> oldest,
        Optional<ShoutSummary> newest,
        Optional<ShoutSummary> longest,
        Optional<ReplayedShout> mostReplayed) {

    boolean isEmpty() {
        return totalShouts == 0;
    }

    /** Single user's contribution count for the leaderboard. */
    record ShouterCount(long userId, int count) {}

    /** The shout fields needed to render a row in the embed. */
    record ShoutSummary(long shoutId, long messageId, long authorId,
                        String content, OffsetDateTime authoredAt) {}

    /** A shout plus how many times the bot has re-emitted it in this channel. */
    record ReplayedShout(ShoutSummary shout, int replayCount) {}
}
