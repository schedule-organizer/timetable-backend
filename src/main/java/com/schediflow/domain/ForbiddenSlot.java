package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Hard unavailability for a teacher, room or class: the referenced entity may not be scheduled in
 * this period, either every week on {@code dayOfWeek} (recurring) or once on {@code specificDate}.
 *
 * <p>{@code entityId} is polymorphic — its meaning depends on {@link #entityType}, so it carries no
 * foreign key. The service resolves it against the right table before persisting.</p>
 */
@Entity
@Table(name = "forbidden_slots")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ForbiddenSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "entity_type", nullable = false, length = 16)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "schedule_period_id", nullable = false)
    private Long schedulePeriodId;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

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

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalDate getSpecificDate() {
        return specificDate;
    }

    public void setSpecificDate(LocalDate specificDate) {
        this.specificDate = specificDate;
    }

    public Long getSchedulePeriodId() {
        return schedulePeriodId;
    }

    public void setSchedulePeriodId(Long schedulePeriodId) {
        this.schedulePeriodId = schedulePeriodId;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
