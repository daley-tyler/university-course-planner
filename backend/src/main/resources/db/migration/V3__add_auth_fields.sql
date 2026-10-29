-- =============================================================
--  V3__add_auth_fields.sql
--  Adds CAS authentication support
-- =============================================================

-- =============================================================
-- UPDATE app_user — add CAS identity fields
-- =============================================================

ALTER TABLE app_user
    ADD COLUMN cas_id        TEXT UNIQUE,
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD COLUMN created_at    TIMESTAMPTZ DEFAULT NOW();

CREATE INDEX idx_app_user_cas_id ON app_user (cas_id);

-- =============================================================
-- SPRING SESSION — persist sessions in PostgreSQL
-- =============================================================

CREATE TABLE spring_session (
    primary_id            CHAR(36)     NOT NULL PRIMARY KEY,
    session_id            CHAR(36)     NOT NULL UNIQUE,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100)
);

CREATE INDEX spring_session_ix1 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix2 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL
        REFERENCES spring_session (primary_id) ON DELETE CASCADE,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);

-- =============================================================
-- AUTH EVENT LOG — audit trail for all login/logout events
-- =============================================================

CREATE TABLE auth_event_log (
    id          BIGSERIAL    PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    cas_id      TEXT,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(500),
    occurred_at TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_auth_event_log_cas_id      ON auth_event_log (cas_id);
CREATE INDEX idx_auth_event_log_occurred_at ON auth_event_log (occurred_at);
