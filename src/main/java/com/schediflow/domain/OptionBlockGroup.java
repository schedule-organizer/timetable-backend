package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/**
 * Junction row placing a {@link TeachingGroup} inside an {@link OptionBlock}.
 * A group belongs to at most one block (enforced by a unique constraint on the column).
 */
@Entity
@Table(name = "option_block_groups")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class OptionBlockGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "option_block_id", nullable = false)
    private Long optionBlockId;

    @Column(name = "teaching_group_id", nullable = false)
    private Long teachingGroupId;

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getOptionBlockId() {
        return optionBlockId;
    }

    public void setOptionBlockId(Long optionBlockId) {
        this.optionBlockId = optionBlockId;
    }

    public Long getTeachingGroupId() {
        return teachingGroupId;
    }

    public void setTeachingGroupId(Long teachingGroupId) {
        this.teachingGroupId = teachingGroupId;
    }
}
