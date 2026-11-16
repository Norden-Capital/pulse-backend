-- Users (public register, no roles) and one-time auth tokens (email verify + password reset).

CREATE TABLE users (
    id                UUID         PRIMARY KEY,
    email             VARCHAR(320) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    last_login_at     TIMESTAMPTZ,
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_email_lower_ck CHECK (email = lower(email)),
    CONSTRAINT users_status_ck CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);

-- Only the SHA-256 hash of a token is stored, never the token itself.
CREATE TABLE auth_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT auth_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT auth_tokens_purpose_ck CHECK (purpose IN ('VERIFY', 'RESET'))
);

CREATE INDEX auth_tokens_user_idx ON auth_tokens (user_id);
