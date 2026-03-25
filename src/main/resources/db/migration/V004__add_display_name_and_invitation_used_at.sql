-- V004__add_display_name_and_invitation_used_at.sql
-- Adds optional display name to users (AUTH-06).
-- Adds used_at to invitation_tokens for single-use enforcement (AUTH-06).

ALTER TABLE users
    ADD COLUMN display_name VARCHAR(255);

ALTER TABLE invitation_tokens
    ADD COLUMN used_at TIMESTAMP WITH TIME ZONE;
