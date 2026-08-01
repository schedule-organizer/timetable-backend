-- V022__create_teacher_preferences.sql — soft weekly availability preferences per teacher (RES-10)

CREATE TABLE teacher_preferences (
    id                 BIGSERIAL   PRIMARY KEY,
    tenant_id          BIGINT      NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    teacher_id         BIGINT      NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    day_of_week        INTEGER     NOT NULL,
    schedule_period_id BIGINT      NOT NULL REFERENCES schedule_periods(id) ON DELETE CASCADE,
    preference_type    VARCHAR(32) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_teacher_preferences_type
        CHECK (preference_type IN ('PREFERRED_FREE', 'PREFERRED_TEACHING')),
    CONSTRAINT chk_teacher_preferences_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7),
    UNIQUE (tenant_id, teacher_id, day_of_week, schedule_period_id)
);

CREATE INDEX idx_teacher_preferences_tenant ON teacher_preferences(tenant_id);
CREATE INDEX idx_teacher_preferences_teacher ON teacher_preferences(teacher_id);
