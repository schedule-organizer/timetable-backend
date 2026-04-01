-- V015__create_lessons.sql — minimal persisted lessons for HOL-07 holiday vs published timetable checks (SCHED-02 expands)

CREATE TABLE lessons (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    timetable_id        BIGINT       NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    subject_id          BIGINT       NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    class_id            BIGINT       NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    teacher_user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    schedule_period_id  BIGINT       NOT NULL REFERENCES schedule_periods(id) ON DELETE CASCADE,
    scheduled_date      DATE         NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lessons_tenant ON lessons(tenant_id);
CREATE INDEX idx_lessons_timetable_date ON lessons(timetable_id, scheduled_date);
