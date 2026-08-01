package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * A cover teacher standing in for a lesson's usual teacher.
 *
 * <p>The lesson itself is not modified — {@code originalTeacherUserId} records who was on the lesson
 * when cover was arranged, so the original teacher stays on record even if the lesson is later
 * reassigned.</p>
 */
@Entity
@Table(name = "cover_assignments")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CoverAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    /** References {@code teachers.id}, not the user id. */
    @Column(name = "cover_teacher_id", nullable = false)
    private Long coverTeacherId;

    /** References {@code users.id}, matching {@code lessons.teacher_user_id}. */
    @Column(name = "original_teacher_user_id", nullable = false)
    private Long originalTeacherUserId;

    @Column(length = 500)
    private String reason;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @PrePersist
    protected void prePersist() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now();
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

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    public Long getCoverTeacherId() {
        return coverTeacherId;
    }

    public void setCoverTeacherId(Long coverTeacherId) {
        this.coverTeacherId = coverTeacherId;
    }

    public Long getOriginalTeacherUserId() {
        return originalTeacherUserId;
    }

    public void setOriginalTeacherUserId(Long originalTeacherUserId) {
        this.originalTeacherUserId = originalTeacherUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }
}
