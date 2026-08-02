-- V028__create_solver_jobs.sql — async solver run tracking (SCHED-03 / 04 / 05)

CREATE TABLE solver_jobs (
    id               BIGSERIAL   PRIMARY KEY,
    tenant_id        BIGINT      NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    timetable_id     BIGINT      NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    status           VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    mode             VARCHAR(16) NOT NULL,
    timeout_seconds  INTEGER     NOT NULL,
    hard_violations  INTEGER,
    soft_score       INTEGER,
    score_breakdown  VARCHAR(4000),
    error_message    VARCHAR(1000),
    requested_by     BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    started_at       TIMESTAMP WITH TIME ZONE,
    completed_at     TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_solver_jobs_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_solver_jobs_mode
        CHECK (mode IN ('FAST', 'BALANCED', 'THOROUGH'))
);

CREATE INDEX idx_solver_jobs_tenant ON solver_jobs(tenant_id);
CREATE INDEX idx_solver_jobs_timetable ON solver_jobs(tenant_id, timetable_id);
CREATE INDEX idx_solver_jobs_active ON solver_jobs(timetable_id, status);
