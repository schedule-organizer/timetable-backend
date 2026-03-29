-- V007__create_bell_schedules.sql — CONFIG-03 (Epic 3)

CREATE TABLE bell_schedules (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bell_schedules_tenant ON bell_schedules(tenant_id);

CREATE TABLE schedule_periods (
    id               BIGSERIAL    PRIMARY KEY,
    bell_schedule_id BIGINT       NOT NULL REFERENCES bell_schedules(id) ON DELETE CASCADE,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    is_break         BOOLEAN      NOT NULL DEFAULT FALSE,
    is_lunch         BOOLEAN      NOT NULL DEFAULT FALSE,
    ordinal          INTEGER      NOT NULL,
    UNIQUE (bell_schedule_id, ordinal)
);

CREATE INDEX idx_schedule_periods_bell_schedule ON schedule_periods(bell_schedule_id);
CREATE INDEX idx_schedule_periods_tenant ON schedule_periods(tenant_id);
