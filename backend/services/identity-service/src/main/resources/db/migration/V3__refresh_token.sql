-- Refresh tokens (FR-AUTH-09..11, D-37): one row per login, not rotated. Refresh moves expires_at
-- forward (sliding 7 days), logout deletes the row, and expired rows are deleted at login.
-- Only the SHA-256 of the token is stored: a leaked table cannot be used to log in.

CREATE TABLE refresh_token (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    user_id     bigint      NOT NULL,
    token_hash  char(64)    NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

-- The cleanup at login deletes by expiry; disabling or deleting a user would look rows up by user.
CREATE INDEX ix_refresh_token_expires ON refresh_token (expires_at);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
