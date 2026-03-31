-- V011__create_timetables.sql — minimal timetable row for solver / holiday integration (SCHED-01 expands this)

CREATE TABLE timetables (
    id                 BIGSERIAL    PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    term_id            BIGINT       NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    bell_schedule_id   BIGINT       NOT NULL REFERENCES bell_schedules(id) ON DELETE CASCADE,
    name               VARCHAR(200) NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timetables_tenant ON timetables(tenant_id);
CREATE INDEX idx_timetables_term ON timetables(term_id);
