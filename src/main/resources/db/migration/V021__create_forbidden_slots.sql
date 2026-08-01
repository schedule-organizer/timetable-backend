-- V021__create_forbidden_slots.sql — hard unavailability for teachers, rooms and classes (RES-09)

-- entity_id is polymorphic (teachers / rooms / school_classes) so it carries no FK;
-- the service resolves and tenant-checks it per entity_type before insert.
CREATE TABLE forbidden_slots (
    id                 BIGSERIAL   PRIMARY KEY,
    tenant_id          BIGINT      NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entity_type        VARCHAR(16) NOT NULL,
    entity_id          BIGINT      NOT NULL,
    day_of_week        INTEGER,
    specific_date      DATE,
    schedule_period_id BIGINT      NOT NULL REFERENCES schedule_periods(id) ON DELETE CASCADE,
    is_recurring       BOOLEAN     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_forbidden_slots_entity_type
        CHECK (entity_type IN ('TEACHER', 'ROOM', 'CLASS')),
    CONSTRAINT chk_forbidden_slots_day_of_week
        CHECK (day_of_week IS NULL OR (day_of_week BETWEEN 1 AND 7)),
    -- recurring slots repeat on a weekday; one-off slots pin a calendar date. Never both.
    CONSTRAINT chk_forbidden_slots_recurrence
        CHECK ((is_recurring = TRUE  AND day_of_week IS NOT NULL AND specific_date IS NULL)
            OR (is_recurring = FALSE AND day_of_week IS NULL     AND specific_date IS NOT NULL))
);

CREATE INDEX idx_forbidden_slots_tenant ON forbidden_slots(tenant_id);
CREATE INDEX idx_forbidden_slots_entity ON forbidden_slots(tenant_id, entity_type, entity_id);
CREATE INDEX idx_forbidden_slots_period ON forbidden_slots(schedule_period_id);
