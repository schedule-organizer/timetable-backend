package com.schediflow.domain;

import com.schediflow.domain.converter.StringListConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A physical room (classroom, lab, gym, etc.) within a tenant's institution.
 */
@Entity
@Table(name = "rooms")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column
    private Integer capacity;

    @Convert(converter = StringListConverter.class)
    @Column(name = "equipment_tags")
    private List<String> equipmentTags;

    @Column(length = 200)
    private String building;

    @Column(length = 100)
    private String floor;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public List<String> getEquipmentTags() {
        return equipmentTags;
    }

    public void setEquipmentTags(List<String> equipmentTags) {
        this.equipmentTags = equipmentTags;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
