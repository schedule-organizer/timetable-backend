package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * Teacher profile for a tenant user: workload limits and display label for scheduling.
 */
@Entity
@Table(name = "teachers")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "max_periods_per_day")
    private Integer maxPeriodsPerDay;

    @Column(name = "max_consecutive_periods")
    private Integer maxConsecutivePeriods;

    @Column(name = "workload_cap")
    private Integer workloadCap;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getMaxPeriodsPerDay() {
        return maxPeriodsPerDay;
    }

    public void setMaxPeriodsPerDay(Integer maxPeriodsPerDay) {
        this.maxPeriodsPerDay = maxPeriodsPerDay;
    }

    public Integer getMaxConsecutivePeriods() {
        return maxConsecutivePeriods;
    }

    public void setMaxConsecutivePeriods(Integer maxConsecutivePeriods) {
        this.maxConsecutivePeriods = maxConsecutivePeriods;
    }

    public Integer getWorkloadCap() {
        return workloadCap;
    }

    public void setWorkloadCap(Integer workloadCap) {
        this.workloadCap = workloadCap;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
