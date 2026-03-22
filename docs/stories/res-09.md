# Story RES-09 — Forbidden Slots
**Epic:** Epic 5 — Resource Management | **Points:** 3 SP | **Status:** Not Started

## Description
`POST/DELETE /api/v1/forbidden-slots` — entity-type-agnostic forbidden slot creation for teachers, rooms, and classes; recurring and date-specific variants

## Acceptance Criteria
- [ ] `POST` creates forbidden slot (entityType: TEACHER/ROOM/CLASS, entityId, dayOfWeek or specificDate, periodId, isRecurring)
- [ ] `DELETE /api/v1/forbidden-slots/{id}` removes a forbidden slot
- [ ] `GET /api/v1/forbidden-slots?entityType=&entityId=` lists slots for an entity
- [ ] Returns 404 if entityId not found in tenant
- [ ] Recurring slots apply every week; specific-date slots apply once
- [ ] `V00X__create_forbidden_slots.sql` Flyway migration included

## Technical Notes
Polymorphic reference via `entity_type` + `entity_id`. Solver reads via `ForbiddenSlotService`.
