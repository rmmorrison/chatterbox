CREATE TABLE shortened_urls (
    id          BIGSERIAL    PRIMARY KEY,
    token       TEXT         NOT NULL,
    url         TEXT         NOT NULL,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX shortened_urls_token_uniq ON shortened_urls (token);
CREATE UNIQUE INDEX shortened_urls_url_uniq ON shortened_urls (url);
