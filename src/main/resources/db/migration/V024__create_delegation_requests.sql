-- V024__create_delegation_requests.sql — teacher-initiated SWAP / HANDOVER requests (COVER-03)

CREATE TABLE delegation_requests (
    id                   BIGSERIAL   PRIMARY KEY,
    tenant_id            BIGINT      NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type                 VARCHAR(16) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_by_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_teacher_id    BIGINT      NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    reason               VARCHAR(500),
    rejection_reason     VARCHAR(500),
    decided_by           BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    decided_at           TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_delegation_requests_type
        CHECK (type IN ('SWAP', 'HANDOVER')),
    CONSTRAINT chk_delegation_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- a rejection must say why; an approval must not carry a rejection reason
    CONSTRAINT chk_delegation_requests_rejection
        CHECK ((status = 'REJECTED' AND rejection_reason IS NOT NULL)
            OR (status <> 'REJECTED' AND rejection_reason IS NULL))
);

CREATE INDEX idx_delegation_requests_tenant ON delegation_requests(tenant_id);
CREATE INDEX idx_delegation_requests_status ON delegation_requests(tenant_id, status);

CREATE TABLE delegation_request_lessons (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    delegation_request_id BIGINT    NOT NULL REFERENCES delegation_requests(id) ON DELETE CASCADE,
    lesson_id             BIGINT    NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    UNIQUE (delegation_request_id, lesson_id)
);

CREATE INDEX idx_delegation_request_lessons_tenant ON delegation_request_lessons(tenant_id);
CREATE INDEX idx_delegation_request_lessons_lesson ON delegation_request_lessons(lesson_id);
