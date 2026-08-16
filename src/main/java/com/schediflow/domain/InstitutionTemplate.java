package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * A reusable institution setup: bell schedule, settings, terminology and constraint defaults
 * (TMPL-01).
 *
 * <p>Deliberately <b>not</b> tenant-filtered. Built-ins have {@code tenantId = null} and are visible
 * to everyone, so the usual {@code tenantFilter} would hide them; visibility is decided in the
 * service instead.</p>
 */
@Entity
@Table(name = "institution_templates")
public class InstitutionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for a built-in. */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "institution_type", nullable = false, length = 64)
    private String institutionType;

    /**
     * V033 declares this {@code jsonb}; the mapping previously said {@code text}, which H2's
     * compatibility mode tolerated and PostgreSQL rejects on write. Same defect as
     * {@link Tenant#getSettings()}, and it takes TMPL-04 (save a custom template) with it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", nullable = false, columnDefinition = "jsonb")
    private String configurationJson;

    @Column(name = "is_built_in", nullable = false)
    private boolean builtIn;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInstitutionType() { return institutionType; }
    public void setInstitutionType(String institutionType) { this.institutionType = institutionType; }
    public String getConfigurationJson() { return configurationJson; }
    public void setConfigurationJson(String configurationJson) { this.configurationJson = configurationJson; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
