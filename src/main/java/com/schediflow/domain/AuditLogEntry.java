package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/** One recorded action (EXPORT-08). */
@Entity
@Table(name = "audit_log")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 2000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    protected void prePersist() {
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
