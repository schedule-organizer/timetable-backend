# Story RES-01 — Rooms CRUD
**Epic:** Epic 5 — Resource Management | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/rooms` — room type, capacity, equipment tags, building/floor; Flyway migration

## Acceptance Criteria
- [ ] CRUD for rooms (name, type, capacity, equipmentTags[], building, floor)
- [ ] `V00X__create_rooms.sql` Flyway migration included
- [ ] Room types: CLASSROOM, LAB, GYM, AUDITORIUM, OTHER
- [ ] Returns 409 if room name already exists in tenant
- [ ] Soft delete (isActive flag) — rooms referenced by lessons cannot be hard deleted
- [ ] Admin/Mod only for write; all roles read

## Technical Notes
`rooms` table with tenant_id FK and Hibernate @Filter.
