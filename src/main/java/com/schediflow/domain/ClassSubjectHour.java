package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/**
 * Class × subject allocation: periods per cycle and spread pattern for the solver.
 */
@Entity
@Table(name = "class_subject_hours")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ClassSubjectHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "periods_per_cycle", nullable = false)
    private int periodsPerCycle = 1;

    @Column(name = "spread_pattern", nullable = false, length = 50)
    private String spreadPattern;

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public int getPeriodsPerCycle() {
        return periodsPerCycle;
    }

    public void setPeriodsPerCycle(int periodsPerCycle) {
        this.periodsPerCycle = periodsPerCycle;
    }

    public String getSpreadPattern() {
        return spreadPattern;
    }

    public void setSpreadPattern(String spreadPattern) {
        this.spreadPattern = spreadPattern;
    }
}
