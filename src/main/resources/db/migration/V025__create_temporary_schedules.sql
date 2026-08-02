-- V025__create_temporary_schedules.sql — date-bounded overlays on a base timetable (COVER-05)

CREATE TABLE temporary_schedules (
    id                BIGSERIAL    PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    base_timetable_id BIGINT       NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    name              VARCHAR(200) NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_temporary_schedules_status
        CHECK (status IN ('ACTIVE', 'EXPIRED')),
    CONSTRAINT chk_temporary_schedules_dates
        CHECK (start_date < end_date)
);

CREATE INDEX idx_temporary_schedules_tenant ON temporary_schedules(tenant_id);
CREATE INDEX idx_temporary_schedules_base ON temporary_schedules(base_timetable_id, status);
CREATE INDEX idx_temporary_schedules_expiry ON temporary_schedules(status, end_date);

-- Overrides live here rather than in `lessons`, so the base timetable is never mutated and
-- expiry (COVER-06) only has to delete these rows for the base schedule to resume.
CREATE TABLE temporary_schedule_lessons (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    temporary_schedule_id BIGINT    NOT NULL REFERENCES temporary_schedules(id) ON DELETE CASCADE,
    subject_id            BIGINT    NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    class_id              BIGINT    NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    teacher_user_id       BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    schedule_period_id    BIGINT    NOT NULL REFERENCES schedule_periods(id) ON DELETE CASCADE,
    scheduled_date        DATE      NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (temporary_schedule_id, class_id, schedule_period_id, scheduled_date)
);

CREATE INDEX idx_temporary_schedule_lessons_tenant ON temporary_schedule_lessons(tenant_id);
CREATE INDEX idx_temporary_schedule_lessons_schedule ON temporary_schedule_lessons(temporary_schedule_id);
