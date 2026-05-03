CREATE TABLE shortened_urls (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    token       TEXT    NOT NULL,
    url         TEXT    NOT NULL,
    created_by  INTEGER NOT NULL,
    created_at  TEXT    NOT NULL
);

CREATE UNIQUE INDEX shortened_urls_token_uniq ON shortened_urls (token);
CREATE UNIQUE INDEX shortened_urls_url_uniq ON shortened_urls (url);
