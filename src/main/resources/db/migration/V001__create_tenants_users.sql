-- V001__create_tenants_users.sql
-- Creates the tenants and users tables with required indexes.

CREATE TABLE tenants (
    id         BIGSERIAL    PRIMARY KEY,
    slug       VARCHAR(63)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    settings   JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_tenants_slug ON tenants(slug);

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    role          VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING_REGISTRATION',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email  ON users(email);
CREATE INDEX        idx_users_tenant ON users(tenant_id);
