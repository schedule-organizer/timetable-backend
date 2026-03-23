package com.schediflow.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Root aggregate — not tenant-scoped (it IS the tenant).
 * No @Filter applied here.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 63)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private String settings = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
