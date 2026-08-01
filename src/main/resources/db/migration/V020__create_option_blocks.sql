-- V020__create_option_blocks.sql — option blocks and their member teaching groups (RES-08)

CREATE TABLE option_blocks (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_option_blocks_tenant ON option_blocks(tenant_id);

-- A teaching group runs in at most one option block, so the FK column is unique.
CREATE TABLE option_block_groups (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    option_block_id   BIGINT    NOT NULL REFERENCES option_blocks(id) ON DELETE CASCADE,
    teaching_group_id BIGINT    NOT NULL REFERENCES teaching_groups(id) ON DELETE CASCADE,
    UNIQUE (teaching_group_id)
);

CREATE INDEX idx_option_block_groups_tenant ON option_block_groups(tenant_id);
CREATE INDEX idx_option_block_groups_block ON option_block_groups(option_block_id);
