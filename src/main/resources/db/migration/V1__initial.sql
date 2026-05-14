CREATE TABLE skins (
    id              BIGSERIAL    NOT NULL,
    texture_id      VARCHAR(255) NOT NULL,
    model           VARCHAR(10)  NOT NULL,
    legacy          BOOLEAN      NOT NULL,
    unique_owners   BIGINT       NOT NULL,
    first_seen      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (texture_id)
);

CREATE TABLE capes (
    id              BIGSERIAL    NOT NULL,
    name            VARCHAR(255),
    texture_id      VARCHAR(255) NOT NULL,
    unique_owners   BIGINT       NOT NULL,
    first_seen      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name),
    UNIQUE (texture_id)
);

CREATE TABLE players (
    id              UUID         NOT NULL,
    username        VARCHAR(255) NOT NULL,
    legacy_account  BOOLEAN      NOT NULL,
    submitted_uuids BIGINT       NOT NULL,
    skin_id         BIGINT       NOT NULL REFERENCES skins (id),
    cape_id         BIGINT                REFERENCES capes (id),
    last_updated    TIMESTAMPTZ  NOT NULL,
    first_seen      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE skin_change_events (
    id          BIGSERIAL   NOT NULL,
    player_id   UUID        NOT NULL,
    skin_id     BIGINT      NOT NULL REFERENCES skins (id),
    timestamp   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE cape_change_events (
    id          BIGSERIAL   NOT NULL,
    player_id   UUID        NOT NULL,
    cape_id     BIGINT      NOT NULL REFERENCES capes (id),
    timestamp   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE username_change_events (
    id                BIGSERIAL    NOT NULL,
    player_id         UUID         NOT NULL,
    new_username      VARCHAR(255) NOT NULL,
    previous_username VARCHAR(255),
    timestamp         TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_players_username_lower ON players (LOWER(username) varchar_pattern_ops);

CREATE SEQUENCE skins_seq INCREMENT BY 50;
CREATE SEQUENCE capes_seq INCREMENT BY 50;
CREATE SEQUENCE skin_change_events_seq INCREMENT BY 50;
CREATE SEQUENCE cape_change_events_seq INCREMENT BY 50;
CREATE SEQUENCE username_change_events_seq INCREMENT BY 50;
