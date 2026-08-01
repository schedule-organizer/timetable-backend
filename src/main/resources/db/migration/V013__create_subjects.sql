-- V013__create_subjects.sql — subjects resource (RES-02) + minimal class_subject_hours for delete guard (RES-06 extends)

CREATE TABLE subjects (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                 VARCHAR(200) NOT NULL,
    code                 VARCHAR(50)  NOT NULL,
    color                VARCHAR(7)   NOT NULL,
    difficulty_level     INTEGER      CHECK (difficulty_level IS NULL OR (difficulty_level >= 1 AND difficulty_level <= 5)),
    required_room_type   VARCHAR(32)  CHECK (required_room_type IS NULL OR required_room_type IN ('CLASSROOM', 'LAB', 'GYM', 'AUDITORIUM', 'OTHER')),
    max_per_day          INTEGER      CHECK (max_per_day IS NULL OR max_per_day >= 1),
    spread_pattern       VARCHAR(32)  NOT NULL CHECK (spread_pattern IN ('SPREAD', 'CLUSTER', 'ANY')),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subjects_tenant ON subjects(tenant_id);
CREATE INDEX idx_subjects_tenant_active_code ON subjects(tenant_id, is_active, code);

-- Minimal allocation row; RES-06 adds validation, FK to school_classes, and API.
CREATE TABLE class_subject_hours (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    class_id             BIGINT       NOT NULL,
    subject_id           BIGINT       NOT NULL REFERENCES subjects(id),
    periods_per_cycle    INTEGER      NOT NULL DEFAULT 1 CHECK (periods_per_cycle >= 0),
    spread_pattern       VARCHAR(50)
);

CREATE INDEX idx_csh_subject ON class_subject_hours(subject_id);
CREATE UNIQUE INDEX uq_csh_tenant_class_subject ON class_subject_hours(tenant_id, class_id, subject_id);
