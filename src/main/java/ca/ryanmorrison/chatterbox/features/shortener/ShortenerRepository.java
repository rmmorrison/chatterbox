package ca.ryanmorrison.chatterbox.features.shortener;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.IntegrityConstraintViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHORTENED_URLS;

/**
 * jOOQ-backed access for the {@code shortened_urls} table.
 *
 * <p>Concurrency notes:
 * <ul>
 *   <li>{@code token} is unique — {@link #insert} surfaces an empty Optional on
 *       collision so the caller can retry with a fresh token.</li>
 *   <li>{@code url} is unique — global dedup is enforced at the DB level.
 *       Callers should {@link #findByUrl} first; if two requests race past
 *       that check, the loser also gets an empty Optional and can re-read.</li>
 * </ul>
 */
final class ShortenerRepository {

    private final DSLContext dsl;

    ShortenerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    Optional<ShortenedUrl> findByToken(String token) {
        return dsl.selectFrom(SHORTENED_URLS)
                .where(SHORTENED_URLS.TOKEN.eq(token))
                .fetchOptional()
                .map(ShortenerRepository::toShortenedUrl);
    }

    Optional<ShortenedUrl> findByUrl(String url) {
        return dsl.selectFrom(SHORTENED_URLS)
                .where(SHORTENED_URLS.URL.eq(url))
                .fetchOptional()
                .map(ShortenerRepository::toShortenedUrl);
    }

    Optional<ShortenedUrl> insert(String token, String url, long createdBy, OffsetDateTime createdAt) {
        try {
            Long id = dsl.insertInto(SHORTENED_URLS)
                    .columns(SHORTENED_URLS.TOKEN, SHORTENED_URLS.URL,
                             SHORTENED_URLS.CREATED_BY, SHORTENED_URLS.CREATED_AT)
                    .values(token, url, createdBy, createdAt)
                    .returning(SHORTENED_URLS.ID)
                    .fetchOne(SHORTENED_URLS.ID);
            if (id == null) return Optional.empty();
            return dsl.selectFrom(SHORTENED_URLS).where(SHORTENED_URLS.ID.eq(id))
                    .fetchOptional()
                    .map(ShortenerRepository::toShortenedUrl);
        } catch (IntegrityConstraintViolationException e) {
            return Optional.empty();
        }
    }

    private static ShortenedUrl toShortenedUrl(Record r) {
        return new ShortenedUrl(
                r.get(SHORTENED_URLS.ID),
                r.get(SHORTENED_URLS.TOKEN),
                r.get(SHORTENED_URLS.URL),
                r.get(SHORTENED_URLS.CREATED_BY),
                r.get(SHORTENED_URLS.CREATED_AT));
    }
}
