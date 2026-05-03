package ca.ryanmorrison.chatterbox.features.shortener;

import java.time.OffsetDateTime;
import java.util.Optional;

record ShortenedUrl(
        long id,
        String token,
        String url,
        long createdBy,
        OffsetDateTime createdAt,
        Optional<OffsetDateTime> deletedAt,
        Optional<Long> deletedBy) {

    boolean isDeleted() {
        return deletedAt.isPresent();
    }
}
