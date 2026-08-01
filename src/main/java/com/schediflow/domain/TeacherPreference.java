package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * A teacher's soft weekly preference for one period on one weekday.
 */
@Entity
@Table(name = "teacher_preferences")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TeacherPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "schedule_period_id", nullable = false)
    private Long schedulePeriodId;

    @Column(name = "preference_type", nullable = false, length = 32)
    private String preferenceType;

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

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Long getSchedulePeriodId() {
        return schedulePeriodId;
    }

    public void setSchedulePeriodId(Long schedulePeriodId) {
        this.schedulePeriodId = schedulePeriodId;
    }

    public String getPreferenceType() {
        return preferenceType;
    }

    public void setPreferenceType(String preferenceType) {
        this.preferenceType = preferenceType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
