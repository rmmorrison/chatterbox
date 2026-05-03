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
 * <p>Concurrency / dedup notes:
 * <ul>
 *   <li>{@code token} is unconditionally unique — even soft-deleted rows hold
 *       their token reservation, so a reissued short link can never collide
 *       with a deleted one (the moderator's intent: no token reuse).</li>
 *   <li>{@code url} uniqueness is partial: only enforced for live rows
 *       ({@code WHERE deleted_at IS NULL}). After a delete, the same URL can
 *       be shortened again, producing a fresh token in a new row.</li>
 * </ul>
 *
 * <p>Lookups exclude soft-deleted rows by default. Methods with the
 * {@code IncludingDeleted} suffix are provided for the redirect handler so
 * tombstoned tokens can return {@code 410 Gone} rather than {@code 404}.
 */
final class ShortenerRepository {

    private final DSLContext dsl;

    ShortenerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    Optional<ShortenedUrl> findByToken(String token) {
        return dsl.selectFrom(SHORTENED_URLS)
                .where(SHORTENED_URLS.TOKEN.eq(token))
                .and(SHORTENED_URLS.DELETED_AT.isNull())
                .fetchOptional()
                .map(ShortenerRepository::toShortenedUrl);
    }

    Optional<ShortenedUrl> findByTokenIncludingDeleted(String token) {
        return dsl.selectFrom(SHORTENED_URLS)
                .where(SHORTENED_URLS.TOKEN.eq(token))
                .fetchOptional()
                .map(ShortenerRepository::toShortenedUrl);
    }

    Optional<ShortenedUrl> findByIdIncludingDeleted(long id) {
        return dsl.selectFrom(SHORTENED_URLS)
                .where(SHORTENED_URLS.ID.eq(id))
                .fetchOptional()
                .map(ShortenerRepository::toShortenedUrl);
    }

    Optional<ShortenedUrl> findByUrl(String url) {
        return dsl.selectFrom(SHORTENED_URLS)
                .where(SHORTENED_URLS.URL.eq(url))
                .and(SHORTENED_URLS.DELETED_AT.isNull())
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

    /**
     * Idempotent: a second call against an already-deleted row is a no-op,
     * preserving the original deleter and timestamp.
     */
    int softDelete(long id, long deletedBy, OffsetDateTime deletedAt) {
        return dsl.update(SHORTENED_URLS)
                .set(SHORTENED_URLS.DELETED_AT, deletedAt)
                .set(SHORTENED_URLS.DELETED_BY, deletedBy)
                .where(SHORTENED_URLS.ID.eq(id))
                .and(SHORTENED_URLS.DELETED_AT.isNull())
                .execute();
    }

    private static ShortenedUrl toShortenedUrl(Record r) {
        return new ShortenedUrl(
                r.get(SHORTENED_URLS.ID),
                r.get(SHORTENED_URLS.TOKEN),
                r.get(SHORTENED_URLS.URL),
                r.get(SHORTENED_URLS.CREATED_BY),
                r.get(SHORTENED_URLS.CREATED_AT),
                Optional.ofNullable(r.get(SHORTENED_URLS.DELETED_AT)),
                Optional.ofNullable(r.get(SHORTENED_URLS.DELETED_BY)));
    }
}
