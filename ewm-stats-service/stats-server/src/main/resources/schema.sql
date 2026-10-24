CREATE TABLE IF NOT EXISTS hits
(
    id       BIGSERIAL PRIMARY KEY,
    app      VARCHAR(255) NOT NULL,
    uri      VARCHAR(512) NOT NULL,
    ip       VARCHAR(45)  NOT NULL,
    hit_time TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hits_hit_time ON hits (hit_time);
CREATE INDEX IF NOT EXISTS idx_hits_uri_hit_time ON hits (uri, hit_time);
