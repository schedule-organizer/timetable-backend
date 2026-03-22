# Story FOUND-06 — Multi-Tenancy with Hibernate Filter
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 5 SP | **Status:** Not Started

## Description
Multi-tenancy: `TenantContext` ThreadLocal, Hibernate `@Filter` on all tenant-scoped entities, `TenantFilter`, JWT extraction

## Acceptance Criteria
- [ ] `TenantContext` stores and clears `tenantId` per request thread via ThreadLocal
- [ ] `TenantFilter` (servlet filter) extracts `tenant_id` from validated JWT and sets `TenantContext`
- [ ] Hibernate `@Filter("tenantFilter")` defined and applied to all tenant-scoped entities
- [ ] Filter enabled automatically in repository layer via `EntityManager` interceptor or `@Aspect`
- [ ] Requests without a valid tenant context are rejected with 401/403
- [ ] `TenantContext` is cleared in a `finally` block to prevent thread pool leakage

## Technical Notes
Use `@FilterDef` + `@Filter` annotations. AOP or `HibernateJpaDialect` override for filter activation.
