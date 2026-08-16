-- V035__rename_mod_to_moderator.sql — align stored roles with the PRD naming (AUTH-11)
--
-- Additive, per the story: past migrations are left untouched and this corrects the data forward.
-- V001 declared users.role as a plain VARCHAR with no CHECK, so no constraint has to be dropped.
--
-- NOTE: this changes the value carried in the `role` JWT claim. Tokens issued before this
-- migration still say MOD and will no longer match any authority, so every session must be
-- re-authenticated on deploy. Access-token lifetime is 15 minutes, so the window is short.

UPDATE users SET role = 'MODERATOR' WHERE role = 'MOD';
