-- V019__create_teaching_groups.sql — teaching groups and their member classes (RES-07)

CREATE TABLE teaching_groups (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    teacher_id  BIGINT       NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    subject_id  BIGINT       NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_teaching_groups_type CHECK (type IN ('SET', 'MIXED', 'OPTION_BLOCK'))
);

CREATE INDEX idx_teaching_groups_tenant ON teaching_groups(tenant_id);
CREATE INDEX idx_teaching_groups_teacher ON teaching_groups(teacher_id);
CREATE INDEX idx_teaching_groups_subject ON teaching_groups(subject_id);

CREATE TABLE teaching_group_classes (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT    NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    teaching_group_id BIGINT    NOT NULL REFERENCES teaching_groups(id) ON DELETE CASCADE,
    class_id          BIGINT    NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    UNIQUE (teaching_group_id, class_id)
);

CREATE INDEX idx_teaching_group_classes_tenant ON teaching_group_classes(tenant_id);
CREATE INDEX idx_teaching_group_classes_class ON teaching_group_classes(class_id);
