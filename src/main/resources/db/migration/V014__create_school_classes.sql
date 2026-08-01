-- V014__create_school_classes.sql — school_classes resource table (RES-03)
-- Note: using school_classes to avoid the reserved keyword 'classes' in some DBs.

CREATE TABLE school_classes (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    year_level   INTEGER,
    homeroom_id  BIGINT       REFERENCES rooms(id),
    capacity     INTEGER,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_school_classes_tenant ON school_classes(tenant_id);
-- D2: name uniqueness enforced at service layer (active classes only).
-- A partial unique index (WHERE is_active = true) is PostgreSQL-only and not supported
-- by H2 in test mode, so service-layer enforcement is used instead.
CREATE INDEX idx_school_classes_tenant_active_name ON school_classes(tenant_id, is_active, name);
