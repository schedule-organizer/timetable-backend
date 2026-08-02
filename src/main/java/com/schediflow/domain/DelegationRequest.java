package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * A teacher's request to swap or hand over lessons, pending a moderator decision.
 * The lessons it covers are held in {@link DelegationRequestLesson}.
 */
@Entity
@Table(name = "delegation_requests")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DelegationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    /** References {@code teachers.id}. */
    @Column(name = "target_teacher_id", nullable = false)
    private Long targetTeacherId;

    @Column(length = 500)
    private String reason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(Long requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public Long getTargetTeacherId() {
        return targetTeacherId;
    }

    public void setTargetTeacherId(Long targetTeacherId) {
        this.targetTeacherId = targetTeacherId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(Long decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
