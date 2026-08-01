-- V023__create_cover_assignments.sql — cover teacher overlaid on a lesson (COVER-01)

-- The lesson's own teacher_user_id is never mutated: cover is an overlay, and
-- original_teacher_user_id snapshots who was originally on the lesson at assignment time.
CREATE TABLE cover_assignments (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                BIGINT    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lesson_id                BIGINT    NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    cover_teacher_id         BIGINT    NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    original_teacher_user_id BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason                   VARCHAR(500),
    assigned_by              BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (lesson_id)
);

CREATE INDEX idx_cover_assignments_tenant ON cover_assignments(tenant_id);
CREATE INDEX idx_cover_assignments_cover_teacher ON cover_assignments(cover_teacher_id);
