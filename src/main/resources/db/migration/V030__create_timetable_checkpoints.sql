-- V030__create_timetable_checkpoints.sql — named snapshots of a timetable (SCHED-13, FR26/FR27)
--
-- Design choice: the snapshot is a NORMALISED COPY of the lesson rows, not a JSON blob.
--   * H2 (tests) and PostgreSQL (production) disagree on JSON handling, and the rest of this
--     schema is relational; a blob would be the only opaque column in the database.
--   * Restore becomes an ordinary delete-and-reinsert instead of parse-then-map.
--   * Foreign keys still apply, so a checkpoint cannot outlive the subjects or rooms it names.
-- The cost is one row per lesson per checkpoint, bounded by the retention limit below.

CREATE TABLE timetable_checkpoints (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    timetable_id        BIGINT       NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    lesson_count        INTEGER      NOT NULL DEFAULT 0,
    created_by_user_id  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timetable_checkpoints_tenant ON timetable_checkpoints(tenant_id);
CREATE INDEX idx_timetable_checkpoints_timetable ON timetable_checkpoints(timetable_id, id);

CREATE TABLE timetable_checkpoint_lessons (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    checkpoint_id       BIGINT    NOT NULL REFERENCES timetable_checkpoints(id) ON DELETE CASCADE,
    subject_id          BIGINT    NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    class_id            BIGINT    NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    teacher_user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id             BIGINT    REFERENCES rooms(id) ON DELETE SET NULL,
    schedule_period_id  BIGINT    NOT NULL REFERENCES schedule_periods(id) ON DELETE CASCADE,
    scheduled_date      DATE      NOT NULL,
    is_pinned           BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_timetable_checkpoint_lessons_checkpoint ON timetable_checkpoint_lessons(checkpoint_id);
