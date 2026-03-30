package com.schediflow.domain;

import jakarta.persistence.*;

import java.time.LocalTime;

/**
 * A named time slot within a bell schedule.
 * Accessed only via its parent BellSchedule — tenant isolation guaranteed by parent lookup.
 */
@Entity
@Table(name = "schedule_periods")
public class SchedulePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bell_schedule_id", nullable = false)
    private Long bellScheduleId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_break", nullable = false)
    private boolean isBreak;

    @Column(name = "is_lunch", nullable = false)
    private boolean isLunch;

    @Column(nullable = false)
    private Integer ordinal;

    public Long getId() { return id; }

    public Long getBellScheduleId() { return bellScheduleId; }
    public void setBellScheduleId(Long bellScheduleId) { this.bellScheduleId = bellScheduleId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public boolean isBreak() { return isBreak; }
    public void setBreak(boolean isBreak) { this.isBreak = isBreak; }

    public boolean isLunch() { return isLunch; }
    public void setLunch(boolean isLunch) { this.isLunch = isLunch; }

    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }
}
