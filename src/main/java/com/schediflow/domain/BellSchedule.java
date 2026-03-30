package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * A named bell schedule (set of periods) for a tenant.
 * Only one bell schedule per tenant may be the default — enforced at the service layer.
 */
@Entity
@Table(name = "bell_schedules")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class BellSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    /** Mapped to column is_default. Field named to avoid Java reserved word 'default'. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultSchedule;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isDefaultSchedule() { return defaultSchedule; }
    public void setDefaultSchedule(boolean defaultSchedule) { this.defaultSchedule = defaultSchedule; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
