CREATE TABLE events
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
        CONSTRAINT idx_event_name UNIQUE
);

CREATE TABLE users
(
    id          BIGSERIAL PRIMARY KEY,
    telegram_id VARCHAR(255) NOT NULL
        CONSTRAINT user_tg_id UNIQUE,
    name        VARCHAR(255) NOT NULL,
    event_id    BIGINT,
    role        VARCHAR(255) NOT NULL,

    CONSTRAINT fk_event_users_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE SET NULL
);
