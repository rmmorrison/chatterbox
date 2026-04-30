package ca.ryanmorrison.chatterbox.features.shout;

import java.time.OffsetDateTime;

/**
 * A previously stored shout chosen as a peer to echo back into the channel.
 * Carries enough data for {@link ShoutHistoryRepository} to record the
 * emission and link it to the original shout.
 */
record Peer(long shoutId, String content, long authorId, OffsetDateTime authoredAt) {}
