package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/**
 * Junction row linking a {@link TeachingGroup} to one of its member school classes.
 */
@Entity
@Table(name = "teaching_group_classes")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TeachingGroupClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "teaching_group_id", nullable = false)
    private Long teachingGroupId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTeachingGroupId() {
        return teachingGroupId;
    }

    public void setTeachingGroupId(Long teachingGroupId) {
        this.teachingGroupId = teachingGroupId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }
}
