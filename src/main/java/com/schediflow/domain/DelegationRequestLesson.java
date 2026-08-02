package com.schediflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/**
 * Junction row naming one lesson covered by a {@link DelegationRequest}.
 */
@Entity
@Table(name = "delegation_request_lessons")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DelegationRequestLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "delegation_request_id", nullable = false)
    private Long delegationRequestId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getDelegationRequestId() {
        return delegationRequestId;
    }

    public void setDelegationRequestId(Long delegationRequestId) {
        this.delegationRequestId = delegationRequestId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }
}
