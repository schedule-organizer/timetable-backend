package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Represents a single holiday or school-break date belonging to a {@link HolidayCalendar}.
 */
@Entity
@Table(name = "holiday_dates")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class HolidayDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_calendar_id", nullable = false)
    private Long holidayCalendarId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HolidayType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }

    public Long getHolidayCalendarId() { return holidayCalendarId; }
    public void setHolidayCalendarId(Long holidayCalendarId) { this.holidayCalendarId = holidayCalendarId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public HolidayType getType() { return type; }
    public void setType(HolidayType type) { this.type = type; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
