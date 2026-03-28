# Epic 5 — Resource Management
**Status:** Not Started | **MVP:** Yes | **Total Points:** 35 SP

## Goal
Full CRUD lifecycle for all scheduling resources: rooms, subjects, school classes, teachers (with qualifications and availability), teaching groups, option blocks, and forbidden slots. These are the inputs the scheduling engine needs.

## Market Driver
This epic directly addresses the "knowledge silo" problem — all scheduling inputs are captured in a structured, queryable system rather than in one administrator's head or a spreadsheet. The CSV bulk import story is critical for schools migrating from existing tools.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| RES-01 | CRUD `/api/v1/rooms` — room type, capacity, equipment tags, building/floor; Flyway migration | 3 | Not Started |
| RES-02 | CRUD `/api/v1/subjects` — difficulty level, color, required room type, spread rules, max per day; Flyway migration | 3 | Not Started |
| RES-03 | CRUD `/api/v1/classes` — school classes with year level, homeroom, capacity; Flyway migration | 3 | Not Started |
| RES-04 | CRUD `/api/v1/teachers` — teacher profiles linked to user accounts; workload caps, max consecutive periods; Flyway migration | 3 | Not Started |
| RES-05 | `POST/DELETE /api/v1/teachers/{id}/qualifications` — add/remove subject qualifications with optional periods-per-cycle allocation | 2 | Not Started |
| RES-06 | `GET/PUT /api/v1/classes/{id}/subject-hours` — weekly hours matrix (class × subject → periods per cycle + spread pattern) | 3 | Not Started |
| RES-07 | CRUD `/api/v1/teaching-groups` — groups linking teachers, subjects, and classes; support SET, MIXED, OPTION_BLOCK types; Flyway migration | 4 | Not Started |
| RES-08 | CRUD `/api/v1/option-blocks` — option block containers that enforce simultaneous scheduling of member groups; Flyway migration | 4 | Not Started |
| RES-09 | `POST/DELETE /api/v1/forbidden-slots` — entity-type-agnostic forbidden slot creation for teachers, rooms, and classes; recurring and date-specific variants | 3 | Not Started |
| RES-10 | `GET /api/v1/teachers/{id}/availability` — returns forbidden slots + soft preferences for a teacher | 2 | Not Started |
| RES-11 | CSV bulk import endpoint — parse and upsert Rooms, Classes, Teachers via multipart upload; return row-level error report | 8 | Not Started |

## Notes
Prerequisites: Epics 1–3. Epic 6 (Scheduling Engine) should begin as soon as these resource entities exist in the database.
