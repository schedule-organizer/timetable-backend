package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * Represents a holiday calendar scoped to a single academic year within a tenant.
 * Only one calendar per academic year per tenant is permitted — enforced at the service
 * layer and via the UNIQUE(tenant_id, academic_year_id) database constraint.
 */
@Entity
@Table(name = "holiday_calendars")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class HolidayCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String region;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long academicYearId) { this.academicYearId = academicYearId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
