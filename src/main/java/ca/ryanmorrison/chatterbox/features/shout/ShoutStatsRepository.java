package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ReplayedShout;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShoutSummary;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShouterCount;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUT_HISTORY;

/**
 * jOOQ-backed read-only access for the {@code /shout-stats} command. All
 * queries filter soft-deleted shouts ({@code deleted_at IS NOT NULL}) so the
 * stats reflect what's actually browseable.
 *
 * <p>Each helper runs its own SQL — there's no transaction wrapping the
 * eight queries together. A shout authored mid-call could appear in some
 * counts and not others, but the inconsistency window is sub-second and
 * the rendered embed is best-effort anyway.
 */
final class ShoutStatsRepository {

    private final DSLContext dsl;

    ShoutStatsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    int countLive(long channelId) {
        return dsl.fetchCount(SHOUTS,
                SHOUTS.CHANNEL_ID.eq(channelId)
                        .and(SHOUTS.DELETED_AT.isNull()));
    }

    int countDistinctShouters(long channelId) {
        Integer n = dsl.select(DSL.countDistinct(SHOUTS.AUTHOR_ID))
                .from(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.DELETED_AT.isNull())
                .fetchOne(0, Integer.class);
        return n == null ? 0 : n;
    }

    int countLiveSince(long channelId, OffsetDateTime since) {
        return dsl.fetchCount(SHOUTS,
                SHOUTS.CHANNEL_ID.eq(channelId)
                        .and(SHOUTS.DELETED_AT.isNull())
                        .and(SHOUTS.AUTHORED_AT.ge(since)));
    }

    List<ShouterCount> topShouters(long channelId, int limit) {
        var countField = DSL.count().as("c");
        return dsl.select(SHOUTS.AUTHOR_ID, countField)
                .from(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.DELETED_AT.isNull())
                .groupBy(SHOUTS.AUTHOR_ID)
                .orderBy(countField.desc(), SHOUTS.AUTHOR_ID.asc())
                .limit(limit)
                .fetch()
                .map(r -> new ShouterCount(r.get(SHOUTS.AUTHOR_ID), r.get(countField)));
    }

    Optional<ShoutSummary> oldest(long channelId) {
        return dsl.selectFrom(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.DELETED_AT.isNull())
                .orderBy(SHOUTS.AUTHORED_AT.asc(), SHOUTS.ID.asc())
                .limit(1)
                .fetchOptional()
                .map(ShoutStatsRepository::toSummary);
    }

    Optional<ShoutSummary> newest(long channelId) {
        return dsl.selectFrom(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.DELETED_AT.isNull())
                .orderBy(SHOUTS.AUTHORED_AT.desc(), SHOUTS.ID.desc())
                .limit(1)
                .fetchOptional()
                .map(ShoutStatsRepository::toSummary);
    }

    /**
     * Longest shout by character count of {@code content}. Ties broken by
     * earliest {@code authored_at} so the result is deterministic.
     */
    Optional<ShoutSummary> longest(long channelId) {
        var lenField = DSL.charLength(SHOUTS.CONTENT);
        return dsl.selectFrom(SHOUTS)
                .where(SHOUTS.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.DELETED_AT.isNull())
                .orderBy(lenField.desc(), SHOUTS.AUTHORED_AT.asc(), SHOUTS.ID.asc())
                .limit(1)
                .fetchOptional()
                .map(ShoutStatsRepository::toSummary);
    }

    /**
     * The most-replayed live shout in this channel: groups {@code shout_history}
     * rows by their referenced shout, joins back to {@code shouts} for the
     * fields the embed needs. Returns empty when no live shout has ever been
     * replayed (history table empty for the channel, or every replayed shout
     * has since been soft-deleted).
     */
    Optional<ReplayedShout> mostReplayed(long channelId) {
        var replayCount = DSL.count().as("replay_count");
        return dsl.select(
                        SHOUTS.ID,
                        SHOUTS.MESSAGE_ID,
                        SHOUTS.AUTHOR_ID,
                        SHOUTS.CONTENT,
                        SHOUTS.AUTHORED_AT,
                        replayCount)
                .from(SHOUT_HISTORY)
                .join(SHOUTS).on(SHOUTS.ID.eq(SHOUT_HISTORY.SHOUT_ID))
                .where(SHOUT_HISTORY.CHANNEL_ID.eq(channelId))
                .and(SHOUTS.DELETED_AT.isNull())
                .groupBy(SHOUTS.ID, SHOUTS.MESSAGE_ID, SHOUTS.AUTHOR_ID, SHOUTS.CONTENT, SHOUTS.AUTHORED_AT)
                .orderBy(replayCount.desc(), SHOUTS.ID.asc())
                .limit(1)
                .fetchOptional()
                .map(r -> new ReplayedShout(
                        new ShoutSummary(
                                r.get(SHOUTS.ID),
                                r.get(SHOUTS.MESSAGE_ID),
                                r.get(SHOUTS.AUTHOR_ID),
                                r.get(SHOUTS.CONTENT),
                                r.get(SHOUTS.AUTHORED_AT)),
                        r.get(replayCount)));
    }

    /** Loads every stat used by {@link ShoutStatsView} in one place. */
    ShoutStats loadAll(long channelId, OffsetDateTime now, int topShouterLimit) {
        return new ShoutStats(
                countLive(channelId),
                countDistinctShouters(channelId),
                countLiveSince(channelId, now.minusDays(7)),
                topShouters(channelId, topShouterLimit),
                oldest(channelId),
                newest(channelId),
                longest(channelId),
                mostReplayed(channelId));
    }

    private static ShoutSummary toSummary(Record r) {
        return new ShoutSummary(
                r.get(SHOUTS.ID),
                r.get(SHOUTS.MESSAGE_ID),
                r.get(SHOUTS.AUTHOR_ID),
                r.get(SHOUTS.CONTENT),
                r.get(SHOUTS.AUTHORED_AT));
    }
}
