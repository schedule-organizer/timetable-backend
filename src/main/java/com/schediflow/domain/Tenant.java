package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * CONFIG-04's settings blob. The column is {@code jsonb}; without an explicit JSON type code
     * Hibernate binds this as {@code varchar}, which H2's compatibility mode accepts and PostgreSQL
     * rejects outright ("column settings is of type jsonb but expression is of type character
     * varying") — taking registration and every settings update with it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
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
