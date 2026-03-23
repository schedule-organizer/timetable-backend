package com.schediflow.config;

import com.schediflow.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Automatically enables the Hibernate "tenantFilter" before any repository call.
 * The filter restricts all queries to rows matching the current request's tenant_id.
 * No-op when TenantContext is empty (public/unauthenticated routes).
 */
@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.schediflow.repository..*(..))")
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        }
    }
}
