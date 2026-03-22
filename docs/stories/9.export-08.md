# Story EXPORT-08 — Audit Log
**Epic:** Epic 9 — Export & Reporting | **Points:** 3 SP | **Status:** Not Started

## Description
`GET /api/v1/audit-log` — paginated audit log with filters (actor, entity type, date range); `@Audited` AOP auto-populates log from annotated service methods

## Acceptance Criteria
- [ ] `GET /api/v1/audit-log` returns paginated audit entries (actor, action, entityType, entityId, timestamp, details)
- [ ] Supports filters: actorId, entityType, startDate, endDate
- [ ] `@Audited` annotation on service methods automatically logs calls via AOP `@Around` advice
- [ ] Audit entries scoped to tenant
- [ ] Admin only
- [ ] `V00X__create_audit_log.sql` Flyway migration included

## Technical Notes
`audit_log` table. AOP advice captures method name, args (sanitised), actor from SecurityContext.
