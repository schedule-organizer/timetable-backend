-- V016__create_teachers.sql — teacher profiles linked to users (RES-04)

CREATE TABLE teachers (
    id                         BIGSERIAL    PRIMARY KEY,
    tenant_id                  BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id                    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name               VARCHAR(200) NOT NULL,
    max_periods_per_day        INTEGER,
    max_consecutive_periods    INTEGER,
    workload_cap               INTEGER,
    is_active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_teachers_tenant ON teachers(tenant_id);
CREATE INDEX idx_teachers_user ON teachers(user_id);
