-- V033__create_institution_templates.sql — reusable institution setup templates (TMPL-01)
--
-- configuration_json uses JSONB, matching tenants.settings from V001. H2 runs in PostgreSQL
-- compatibility mode for tests, and the column is read and written as text either way.

CREATE TABLE institution_templates (
    id                 BIGSERIAL    PRIMARY KEY,
    -- NULL for built-ins, which every institution can see; set for a tenant's own templates.
    tenant_id          BIGINT       REFERENCES tenants(id) ON DELETE CASCADE,
    name               VARCHAR(200) NOT NULL,
    description        VARCHAR(1000),
    institution_type   VARCHAR(64)  NOT NULL,
    configuration_json JSONB        NOT NULL DEFAULT '{}',
    is_built_in        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- A built-in belongs to nobody; a custom template must belong to someone.
    CONSTRAINT chk_institution_templates_ownership
        CHECK ((is_built_in = TRUE AND tenant_id IS NULL)
            OR (is_built_in = FALSE AND tenant_id IS NOT NULL))
);

CREATE INDEX idx_institution_templates_tenant ON institution_templates(tenant_id);
CREATE INDEX idx_institution_templates_builtin ON institution_templates(is_built_in);
