package ca.ryanmorrison.chatterbox.features.shortener;

import java.time.OffsetDateTime;

record ShortenedUrl(
        long id,
        String token,
        String url,
        long createdBy,
        OffsetDateTime createdAt,
        OpenGraphMetadata metadata) {}
