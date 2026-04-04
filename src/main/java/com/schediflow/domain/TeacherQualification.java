package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * Qualifies a teacher to teach a subject in the tenant, with optional periods-per-cycle hint for scheduling.
 */
@Entity
@Table(name = "teacher_qualifications")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TeacherQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "periods_per_cycle")
    private Integer periodsPerCycle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Integer getPeriodsPerCycle() {
        return periodsPerCycle;
    }

    public void setPeriodsPerCycle(Integer periodsPerCycle) {
        this.periodsPerCycle = periodsPerCycle;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
