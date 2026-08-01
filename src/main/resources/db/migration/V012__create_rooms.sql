-- V012__create_rooms.sql — rooms resource table (RES-01)

CREATE TABLE rooms (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    -- P1: CHECK constraint ensures only valid enum values are stored at the DB level
    type             VARCHAR(32)  NOT NULL CHECK (type IN ('CLASSROOM', 'LAB', 'GYM', 'AUDITORIUM', 'OTHER')),
    capacity         INTEGER,
    equipment_tags   TEXT,
    building         VARCHAR(200),
    floor            VARCHAR(100),
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rooms_tenant ON rooms(tenant_id);
-- D2: name uniqueness is enforced at the service layer (active rooms only).
-- A full UNIQUE(tenant_id, name) constraint is omitted intentionally: soft-deleted rooms
-- retain their original name so that the same name can be reused after deletion.
-- A partial unique index (WHERE is_active = true) would be correct for PostgreSQL but is
-- not supported by H2 in test mode, so service-layer enforcement is used instead.
CREATE INDEX idx_rooms_tenant_active_name ON rooms(tenant_id, is_active, name);
