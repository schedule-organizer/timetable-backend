package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A scheduled lesson occurrence on a calendar date. Minimal schema for holiday conflict checks (HOL-07);
 * expanded by SCHED-02 / SCHED-03.
 */
@Entity
@Table(name = "lessons")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "teacher_user_id", nullable = false)
    private Long teacherUserId;

    @Column(name = "schedule_period_id", nullable = false)
    private Long schedulePeriodId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

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

    public Long getTimetableId() {
        return timetableId;
    }

    public void setTimetableId(Long timetableId) {
        this.timetableId = timetableId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public void setTeacherUserId(Long teacherUserId) {
        this.teacherUserId = teacherUserId;
    }

    public Long getSchedulePeriodId() {
        return schedulePeriodId;
    }

    public void setSchedulePeriodId(Long schedulePeriodId) {
        this.schedulePeriodId = schedulePeriodId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
