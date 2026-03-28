-- V006__create_terms.sql — CONFIG-02 (Epic 3)

CREATE TABLE terms (
    id                BIGSERIAL    PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    academic_year_id  BIGINT       NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    name              VARCHAR(200) NOT NULL,
    ordinal           INTEGER      NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (academic_year_id, ordinal)
);

CREATE INDEX idx_terms_tenant ON terms(tenant_id);
CREATE INDEX idx_terms_academic_year ON terms(academic_year_id);
