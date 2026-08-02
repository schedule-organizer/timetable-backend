package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/** One asynchronous solver run over a timetable (SCHED-03/04/05). */
@Entity
@Table(name = "solver_jobs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SolverJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;

    @Column(nullable = false, length = 16)
    private String status = SolverJobStatus.QUEUED.name();

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds;

    @Column(name = "hard_violations")
    private Integer hardViolations;

    @Column(name = "soft_score")
    private Integer softScore;

    @Column(name = "score_breakdown", length = 4000)
    private String scoreBreakdown;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "scope_description", length = 500)
    private String scopeDescription;

    @Column(name = "eligible_lessons")
    private Integer eligibleLessons;

    @Column(name = "frozen_lessons")
    private Integer frozenLessons;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getTimetableId() { return timetableId; }
    public void setTimetableId(Long timetableId) { this.timetableId = timetableId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Integer getHardViolations() { return hardViolations; }
    public void setHardViolations(Integer hardViolations) { this.hardViolations = hardViolations; }

    public Integer getSoftScore() { return softScore; }
    public void setSoftScore(Integer softScore) { this.softScore = softScore; }

    public String getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(String scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getScopeDescription() { return scopeDescription; }
    public void setScopeDescription(String scopeDescription) { this.scopeDescription = scopeDescription; }

    public Integer getEligibleLessons() { return eligibleLessons; }
    public void setEligibleLessons(Integer eligibleLessons) { this.eligibleLessons = eligibleLessons; }

    public Integer getFrozenLessons() { return frozenLessons; }
    public void setFrozenLessons(Integer frozenLessons) { this.frozenLessons = frozenLessons; }

    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
