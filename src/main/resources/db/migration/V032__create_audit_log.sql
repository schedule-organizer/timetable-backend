-- V032__create_audit_log.sql — who did what, captured by @Audited AOP advice (EXPORT-08)

CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    actor_id    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   BIGINT,
    details     VARCHAR(2000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id, id);
CREATE INDEX idx_audit_log_actor ON audit_log(tenant_id, actor_id);
CREATE INDEX idx_audit_log_entity ON audit_log(tenant_id, entity_type);
CREATE INDEX idx_audit_log_time ON audit_log(tenant_id, occurred_at);
