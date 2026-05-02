package ca.ryanmorrison.chatterbox.features.rss;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.IntegrityConstraintViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.RSS_FEEDS;

/**
 * jOOQ-backed access for the {@code rss_feeds} table.
 *
 * <p>Concurrency note: the {@code (channel_id, url)} unique constraint is the
 * source of truth for "no duplicate feed in a channel". Two simultaneous adds
 * with the same URL are race-safe — one wins, the other surfaces an empty
 * {@link Optional} from {@link #insert} for the caller to translate.
 */
final class RssRepository {

    private final DSLContext dsl;

    RssRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    Optional<Feed> insert(long guildId, long channelId, String url, String title,
                          long addedBy, int refreshMinutes) {
        try {
            Long id = dsl.insertInto(RSS_FEEDS)
                    .columns(RSS_FEEDS.GUILD_ID, RSS_FEEDS.CHANNEL_ID, RSS_FEEDS.URL,
                             RSS_FEEDS.TITLE, RSS_FEEDS.ADDED_BY, RSS_FEEDS.REFRESH_MINUTES)
                    .values(guildId, channelId, url, title, addedBy, refreshMinutes)
                    .returning(RSS_FEEDS.ID)
                    .fetchOne(RSS_FEEDS.ID);
            if (id == null) return Optional.empty();
            return findById(id);
        } catch (IntegrityConstraintViolationException e) {
            return Optional.empty();
        }
    }

    int deleteById(long id) {
        return dsl.deleteFrom(RSS_FEEDS).where(RSS_FEEDS.ID.eq(id)).execute();
    }

    int deleteByChannel(long channelId) {
        return dsl.deleteFrom(RSS_FEEDS).where(RSS_FEEDS.CHANNEL_ID.eq(channelId)).execute();
    }

    Optional<Feed> findById(long id) {
        return dsl.selectFrom(RSS_FEEDS).where(RSS_FEEDS.ID.eq(id))
                .fetchOptional()
                .map(RssRepository::toFeed);
    }

    /** All feeds in the channel, ordered by creation time so the picker UI is stable. */
    List<Feed> listByChannel(long channelId) {
        return dsl.selectFrom(RSS_FEEDS)
                .where(RSS_FEEDS.CHANNEL_ID.eq(channelId))
                .orderBy(RSS_FEEDS.CREATED_AT.asc(), RSS_FEEDS.ID.asc())
                .fetch()
                .map(RssRepository::toFeed);
    }

    int countByChannel(long channelId) {
        return dsl.fetchCount(RSS_FEEDS, RSS_FEEDS.CHANNEL_ID.eq(channelId));
    }

    /** All feeds across all channels. Used at startup to seed the scheduler. */
    List<Feed> listAll() {
        return dsl.selectFrom(RSS_FEEDS)
                .orderBy(RSS_FEEDS.ID.asc())
                .fetch()
                .map(RssRepository::toFeed);
    }

    /**
     * Updates the diff markers after a refresh. The {@code lastRefreshedAt}
     * stamp is always written; the item id / pub date are only updated when
     * the latest item is non-null (so a temporary fetch failure doesn't
     * clobber a known marker).
     */
    int updateMarkers(long id, String lastItemId, OffsetDateTime lastItemPublishedAt,
                      OffsetDateTime lastRefreshedAt) {
        return dsl.update(RSS_FEEDS)
                .set(RSS_FEEDS.LAST_ITEM_ID, lastItemId)
                .set(RSS_FEEDS.LAST_ITEM_PUBLISHED_AT, lastItemPublishedAt)
                .set(RSS_FEEDS.LAST_REFRESHED_AT, lastRefreshedAt)
                .where(RSS_FEEDS.ID.eq(id))
                .execute();
    }

    /** Records a refresh attempt without touching the diff markers. */
    int touchRefreshedAt(long id, OffsetDateTime lastRefreshedAt) {
        return dsl.update(RSS_FEEDS)
                .set(RSS_FEEDS.LAST_REFRESHED_AT, lastRefreshedAt)
                .where(RSS_FEEDS.ID.eq(id))
                .execute();
    }

    private static Feed toFeed(Record r) {
        return new Feed(
                r.get(RSS_FEEDS.ID),
                r.get(RSS_FEEDS.GUILD_ID),
                r.get(RSS_FEEDS.CHANNEL_ID),
                r.get(RSS_FEEDS.URL),
                r.get(RSS_FEEDS.TITLE),
                r.get(RSS_FEEDS.ADDED_BY),
                r.get(RSS_FEEDS.REFRESH_MINUTES),
                Optional.ofNullable(r.get(RSS_FEEDS.LAST_ITEM_ID)),
                Optional.ofNullable(r.get(RSS_FEEDS.LAST_ITEM_PUBLISHED_AT)),
                Optional.ofNullable(r.get(RSS_FEEDS.LAST_REFRESHED_AT)),
                r.get(RSS_FEEDS.CREATED_AT));
    }
}
