-- V003__add_invitation_tokens.sql
-- Stores hashed single-use invitation tokens for teacher on-boarding (AUTH-05).
-- Raw UUID token is sent in the email link; only the SHA-256 hex digest is persisted.

CREATE TABLE invitation_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_invitation_tokens_hash ON invitation_tokens(token_hash);
CREATE INDEX        idx_invitation_tokens_user ON invitation_tokens(user_id);
