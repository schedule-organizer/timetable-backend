# Epic 3 — Institution Configuration
**Status:** Not Started | **MVP:** Yes | **Total Points:** 18 SP

## Goal
Allow administrators to configure the structural building blocks of their institution: academic years, terms, bell schedules with periods, and global settings. These entities are prerequisites for resource and scheduling data.

## Market Driver
SchediFlow's configurable terminology (periods, classes, years) and flexible bell schedule support directly addresses the pain point that existing tools force institutions to adapt to the software rather than vice versa. Schools in different countries use different terminology and structures.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| CONFIG-01 | CRUD `/api/v1/academic-years` — create/read/update/delete academic years; only one can be `is_active`; Flyway migration | 3 | Not Started |
| CONFIG-02 | CRUD `/api/v1/terms` — terms scoped to academic year; include `ordinal` ordering; Flyway migration | 3 | Not Started |
| CONFIG-03 | CRUD `/api/v1/bell-schedules` + nested periods — bell schedule with named periods, times, break/lunch flags; one default per tenant; Flyway migration | 4 | Not Started |
| CONFIG-04 | `GET/PUT /api/v1/settings` — institution settings JSONB blob (locale, timezone, terminology, constraint defaults); Admin only | 3 | Not Started |
| CONFIG-05 | `GET /api/v1/settings/public` — unauthenticated endpoint returning locale/timezone for login page | 1 | Not Started |
| CONFIG-09 | Seed service: on first institution creation, apply sensible defaults (5-day cycle, 8 periods, standard terminology) | 4 | Not Started |

## Notes
Prerequisites: Epic 1 (Foundation). CONFIG-09 is triggered during AUTH-01 institution registration.
