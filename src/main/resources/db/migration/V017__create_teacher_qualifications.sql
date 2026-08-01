-- V017__create_teacher_qualifications.sql — teacher × subject qualifications (RES-05)

CREATE TABLE teacher_qualifications (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    teacher_id           BIGINT       NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    subject_id           BIGINT       NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    periods_per_cycle    INTEGER,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, teacher_id, subject_id)
);

CREATE INDEX idx_teacher_qualifications_tenant ON teacher_qualifications(tenant_id);
CREATE INDEX idx_teacher_qualifications_teacher ON teacher_qualifications(teacher_id);
CREATE INDEX idx_teacher_qualifications_subject ON teacher_qualifications(subject_id);
