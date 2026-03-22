# SchediFlow Backend — Development Epics
**Version 1.0 | March 2026 | Internal Engineering Document**

> **Source documents:**
> - `SchediFlow_MRD_v1.0` — market requirements & business context
> - `schediflow-backend_Architecture_v1.2` — technical specification

---

## Overview

SchediFlow is a multi-tenant SaaS platform for automated school timetable management, targeting small-to-medium educational institutions (10–200 teaching staff) currently underserved by spreadsheets and expensive legacy tools.

These epics cover the **schediflow-backend** only (Spring Boot 3 / Java 21 / PostgreSQL 16 / Timefold Solver). Each epic maps to a cohesive slice of backend capability. Stories are sized in story points (Fibonacci).

**MVP scope:** Epics 1–9. Epic 10 (Setup Templates) is post-MVP but low risk to develop in parallel.

**Total estimated points:** ~220 SP

---

## Epic Summary

| # | Epic | Points | MVP? |
|---|---|---|---|
| 1 | Foundation & Infrastructure | 21 | ✅ |
| 2 | Authentication & User Management | 26 | ✅ |
| 3 | Institution Configuration | 18 | ✅ |
| 4 | Holiday & Vacation Calendar | 17 | ✅ |
| 5 | Resource Management | 35 | ✅ |
| 6 | Timetable & Scheduling Engine | 35 | ✅ |
| 7 | Cover, Delegation & Temporary Schedules | 25 | ✅ |
| 8 | Notifications | 8 | ✅ |
| 9 | Export & Reporting | 25 | ✅ |
| 10 | Setup Templates | 15 | Post-MVP |

---

## Epic 1 — Foundation & Infrastructure

**Goal:** Establish a production-grade Spring Boot application skeleton with multi-tenancy, security scaffolding, error handling, and Docker orchestration. Every subsequent epic depends on this foundation.

**Market driver:** Speed to market. A robust foundation prevents compounding technical debt that would slow later feature delivery. The target market (SME schools) expects a reliable cloud tool; a flaky foundation undermines trust.

| Story | Description | Points |
|---|---|---|
| FOUND-01 | Initialize schediflow-backend: Spring Boot 3, Java 21, Maven, core dependencies (Spring Web, Security, Data JPA, Validation, Actuator, MapStruct, Springdoc) | 2 |
| FOUND-03 | `docker-compose.yml`: postgres + backend + frontend + mailhog services, named volumes, healthchecks | 3 |
| FOUND-04 | Flyway: `V001__create_tenants_users.sql` — tenants and users tables with indexes | 2 |
| FOUND-05 | Spring Security: `JwtTokenProvider`, `JwtAuthenticationFilter`, token validation, BCrypt password encoding | 5 |
| FOUND-06 | Multi-tenancy: `TenantContext` ThreadLocal, Hibernate `@Filter` on all tenant-scoped entities, `TenantFilter`, JWT extraction | 5 |
| FOUND-07 | Springdoc OpenAPI 3: Swagger UI at `/api-docs`, JWT bearer auth scheme, all controllers annotated | 1 |
| FOUND-08 | CORS configuration scoped to known frontend origin | 1 |
| FOUND-09 | `GlobalExceptionHandler`: consistent JSON error envelope (`status`, `code`, `message`, `details`, `timestamp`) for all error types | 2 |

**Total: 21 SP**

---

## Epic 2 — Authentication & User Management

**Goal:** Implement the full authentication lifecycle — institution self-registration, email/password login, JWT access + refresh token flow, teacher invitation via email, and user profile management.

**Market driver:** The primary persona (Timetabler / Scheduling Coordinator) is non-technical. Onboarding must be frictionless. The invitation-based teacher registration model reflects how schools actually operate: administrators manage staff onboarding, not the staff themselves. OTP and SSO are post-MVP.

| Story | Description | Points |
|---|---|---|
| AUTH-01 | `POST /api/v1/auth/register` — institution self-registration: creates `Tenant` + first `Admin` user, returns JWT | 3 |
| AUTH-02 | `POST /api/v1/auth/login` — email/password login, returns JWT access token (15 min) + `HttpOnly` refresh cookie (7 days) | 3 |
| AUTH-03 | `POST /api/v1/auth/refresh` — validates refresh cookie, returns new access token | 2 |
| AUTH-04 | `POST /api/v1/auth/logout` — invalidates (deletes) refresh token server-side | 1 |
| AUTH-05 | `POST /api/v1/users/invite` — create `PENDING_REGISTRATION` user, generate single-use 72h token, send invite email via `SmtpEmailService` | 5 |
| AUTH-06 | `POST /api/v1/auth/complete-registration` — consume invitation token, set password, activate user account | 4 |
| AUTH-07 | `GET/PUT /api/v1/users/me` — read and update authenticated user's own profile | 3 |
| AUTH-08 | `GET /api/v1/users` — paginated user list with role/status filters (Admin, Mod only) | 2 |
| AUTH-09 | `PUT /api/v1/users/{id}/role` — role change (Admin only), cannot demote self | 2 |
| AUTH-10 | `DELETE /api/v1/users/{id}` — soft deactivate (sets `status=INACTIVE`); does not delete records | 1 |

**Total: 26 SP**

**Security notes:** Rate-limit `/auth/**` at 10 req/min/IP. Registration tokens are single-use and expire after 72h. Passwords hashed with BCrypt cost factor 12.

---

## Epic 3 — Institution Configuration

**Goal:** Allow administrators to configure the structural building blocks of their institution: academic years, terms, bell schedules with periods, and global settings. These entities are prerequisites for resource and scheduling data.

**Market driver:** SchediFlow's configurable terminology (periods, classes, years) and flexible bell schedule support directly addresses the pain point that existing tools force institutions to adapt to the software rather than vice versa. Schools in different countries use different terminology and structures.

| Story | Description | Points |
|---|---|---|
| CONFIG-01 | CRUD `/api/v1/academic-years` — create/read/update/delete academic years; only one can be `is_active`; Flyway migration | 3 |
| CONFIG-02 | CRUD `/api/v1/terms` — terms scoped to academic year; include `ordinal` ordering; Flyway migration | 3 |
| CONFIG-03 | CRUD `/api/v1/bell-schedules` + nested periods — bell schedule with named periods, times, break/lunch flags; one default per tenant; Flyway migration | 4 |
| CONFIG-04 | `GET/PUT /api/v1/settings` — institution settings JSONB blob (locale, timezone, terminology, constraint defaults); Admin only | 3 |
| CONFIG-05 | `GET /api/v1/settings/public` — unauthenticated endpoint returning locale/timezone for login page | 1 |
| CONFIG-09 | Seed service: on first institution creation, apply sensible defaults (5-day cycle, 8 periods, standard terminology) | 4 |

**Total: 18 SP**

---

## Epic 4 — Holiday & Vacation Calendar

**Goal:** Maintain a per-academic-year holiday calendar that feeds into the scheduling engine as globally forbidden slots. Supports both public holiday import and manual overrides.

**Market driver:** Holiday-aware scheduling is a baseline expectation. Schools cannot publish a timetable that schedules lessons on public holidays. The import-from-feed feature reduces administrator setup time, directly addressing the "2–4 weeks of manual work" problem identified in the MRD.

| Story | Description | Points |
|---|---|---|
| HOL-01 | CRUD `/api/v1/holidays` — holiday calendars per academic year; `holiday_calendars` and `holiday_dates` tables; Flyway migration | 3 |
| HOL-02 | `POST /api/v1/holidays/import` — fetch public holiday dates from external feed (Calendarific free tier, TD-06) by country/region; persist as `holiday_dates`; idempotent on re-import | 5 |
| HOL-03 | Manual holiday date CRUD — add, edit, delete individual dates within a calendar | 2 |
| HOL-04 | `GET /api/v1/holidays?academicYearId=...` — return all dates (imported + manual) for a given academic year | 1 |
| HOL-05 | Solver integration: `HolidayService` loads holiday dates and converts them to globally forbidden period slots before each solver run | 3 |
| HOL-07 | Conflict detection: `ConflictDetectionService` warns when a published lesson falls on a newly added holiday date | 3 |

**Total: 17 SP**

---

## Epic 5 — Resource Management

**Goal:** Full CRUD lifecycle for all scheduling resources: rooms, subjects, school classes, teachers (with qualifications and availability), teaching groups, option blocks, and forbidden slots. These are the inputs the scheduling engine needs.

**Market driver:** This epic directly addresses the "knowledge silo" problem — all scheduling inputs are captured in a structured, queryable system rather than in one administrator's head or a spreadsheet. The CSV bulk import story is critical for schools migrating from existing tools.

| Story | Description | Points |
|---|---|---|
| RES-01 | CRUD `/api/v1/rooms` — room type, capacity, equipment tags, building/floor; Flyway migration | 3 |
| RES-02 | CRUD `/api/v1/subjects` — difficulty level, color, required room type, spread rules, max per day; Flyway migration | 3 |
| RES-03 | CRUD `/api/v1/classes` — school classes with year level, homeroom, capacity; Flyway migration | 3 |
| RES-04 | CRUD `/api/v1/teachers` — teacher profiles linked to user accounts; workload caps, max consecutive periods; Flyway migration | 3 |
| RES-05 | `POST/DELETE /api/v1/teachers/{id}/qualifications` — add/remove subject qualifications with optional periods-per-cycle allocation | 2 |
| RES-06 | `GET/PUT /api/v1/classes/{id}/subject-hours` — weekly hours matrix (class × subject → periods per cycle + spread pattern) | 3 |
| RES-07 | CRUD `/api/v1/teaching-groups` — groups linking teachers, subjects, and classes; support SET, MIXED, OPTION_BLOCK types; Flyway migration | 4 |
| RES-08 | CRUD `/api/v1/option-blocks` — option block containers that enforce simultaneous scheduling of member groups; Flyway migration | 4 |
| RES-09 | `POST/DELETE /api/v1/forbidden-slots` — entity-type-agnostic forbidden slot creation for teachers, rooms, and classes; recurring and date-specific variants | 3 |
| RES-10 | `GET /api/v1/teachers/{id}/availability` — returns forbidden slots + soft preferences for a teacher | 2 |
| RES-11 | CSV bulk import endpoint — parse and upsert Rooms, Classes, Teachers via multipart upload; return row-level error report | 8 |

**Total: 35 SP**

---

## Epic 6 — Timetable & Scheduling Engine

**Goal:** The core product capability. Implement timetable lifecycle management, async solver execution via Timefold, real-time progress via WebSocket, and manual lesson manipulation (move, pin, swap) with live conflict detection.

**Market driver:** This is SchediFlow's primary differentiator. Timetablers currently spend 2–4 weeks on manual scheduling. The generator, in combination with manual override capability, directly solves this. The quality score and real-time progress give confidence to non-expert users that the algorithm is working.

| Story | Description | Points |
|---|---|---|
| SCHED-01 | CRUD `/api/v1/timetables` — create/list/get/delete timetables per term; status lifecycle (DRAFT → PUBLISHED → ARCHIVED); Flyway migration | 2 |
| SCHED-02 | `GET /api/v1/timetables/{id}/lessons` — full lesson list for grid rendering, includes room, teacher, period, pin status, conflict flag | 3 |
| SCHED-03 | `POST /api/v1/engine/run` — async solver job: load problem from DB → build `TimetableSolution` → call `SolverManager.solveAndListen` → return `jobId` immediately; sensitivity and mode parameters | 8 |
| SCHED-04 | `GET /api/v1/engine/jobs/{id}` and `GET /api/v1/engine/jobs` — poll job status, quality score, score breakdown, error message; job history per timetable | 2 |
| SCHED-05 | `POST /api/v1/engine/jobs/{id}/cancel` — cancel a running solver job; update status to CANCELLED | 2 |
| SCHED-06 | WebSocket: `SOLVER_PROGRESS` and `SOLVER_COMPLETE` events on `/topic/solver/{jobId}/progress` and `/topic/solver/{jobId}/complete`; includes percent complete, current score, hard violations | 4 |
| SCHED-07 | `POST /api/v1/timetables/{id}/publish` — validate zero hard violations, set status PUBLISHED, record `published_at`; `@Scheduled` job for future-dated publish | 3 |
| SCHED-08 | `PATCH /api/v1/lessons/{id}` — move lesson to new period/room via drag-and-drop; run conflict detection; broadcast WebSocket event | 3 |
| SCHED-09 | `POST/DELETE /api/v1/lessons/{id}/pin` — pin/unpin a lesson card; pinned cards excluded from solver via `@PinningFilter` | 2 |
| SCHED-10 | `POST /api/v1/lessons/{id}/swap` — atomic swap of two lesson cards with conflict validation on both sides | 3 |
| SCHED-11 | `ConflictDetectionService` — real-time constraint checks: teacher double-booking, room double-booking, class double-booking, room capacity, forbidden slots | 5 |
| SCHED-12 | WebSocket: broadcast `LESSON_UPDATED` to `/topic/timetable/{timetableId}` on every lesson mutation (move, pin, swap, solver result) | 2 |

**Total: 39 SP**

**Timefold notes:** In-process JVM solver (no cloud dependency). Thread pool isolated via `AsyncConfig`. Solver timeout enforced per subscription tier: Starter 30s, Professional 2 min, Enterprise 10 min.

---

## Epic 7 — Cover, Delegation & Temporary Schedules

**Goal:** Day-to-day operational scheduling: assign cover teachers for absent staff, handle teacher-to-teacher swap/handover requests, and manage exceptional-week temporary schedules.

**Market driver:** The MRD identifies absence management as the "Absence Management Gap" — a distinct problem from initial timetable creation. Cover is currently assigned via phone calls and WhatsApp. Digitising this workflow reduces administrative burden and creates an audit trail, which is a key selling point for institutional buyers.

| Story | Description | Points |
|---|---|---|
| COVER-01 | `POST /api/v1/cover` — assign cover teacher to a lesson; validate qualification for subject and no timetable conflict; Flyway migration | 4 |
| COVER-02 | `GET /api/v1/cover/candidates?lessonId=...` — return qualified, available teachers sorted by workload gap; respects forbidden slots | 4 |
| COVER-03 | `POST /api/v1/delegation` — teacher submits SWAP or HANDOVER request for one or more lessons; Flyway migration | 3 |
| COVER-04 | `PATCH /api/v1/delegation/{id}` — moderator approves or rejects delegation request; on approval, atomically reassigns lessons | 3 |
| COVER-05 | CRUD `/api/v1/temporary-schedules` — create named temporary schedule overlaying a base timetable for a date range; Flyway migration | 5 |
| COVER-06 | `@Scheduled` job: auto-expire temporary schedules at `end_date`; revert lessons to base timetable | 3 |
| COVER-07 | WebSocket: `COVER_ASSIGNED` event to `/topic/tenant/{tenantId}/notifications`; `DELEGATION_UPDATE` event to requesting and receiving teacher queues | 3 |

**Total: 25 SP**

---

## Epic 8 — Notifications

**Goal:** Deliver real-time WebSocket events and transactional emails for key lifecycle events across the platform.

**Market driver:** Teachers and administrators expect immediate notification of changes that affect their schedules. This is standard in modern SaaS and directly supports SchediFlow's "teacher visibility" differentiator versus the current reality of printed timetable sheets posted on walls.

| Story | Description | Points |
|---|---|---|
| NOTIF-01 | `WebSocketEventPublisher` — typed event publisher for all STOMP topics; `WebSocketConfig` with SockJS fallback at `ws://{host}/ws` | 4 |
| NOTIF-02 | `TIMETABLE_PUBLISHED` WebSocket event to `/topic/tenant/{tenantId}/notifications` on publish; personal queue for targeted user notifications | 4 |
| NOTIF-03 | `SmtpEmailService` (via MailHog for MVP): transactional emails for teacher invite, cover assignment, delegation status change, and timetable published | 4 |

**Total: 12 SP**

**Post-MVP:** Replace `SmtpEmailService` with `SesEmailService` (AWS SES). Add Twilio OTP for 2FA. `StorageService` swaps from local volume to S3 via interface change only.

---

## Epic 9 — Export & Reporting

**Goal:** Enable timetable export in PDF, CSV, and iCal formats, plus utilization and audit reports for administrators.

**Market driver:** Schools need to distribute timetables to students, parents, and teachers via multiple channels. PDF export supports traditional physical distribution. iCal export enables integration with personal calendars, directly addressing the student and teacher visibility gap. The audit log meets governance and accountability expectations of institutional buyers.

| Story | Description | Points |
|---|---|---|
| EXPORT-01 | `GET /api/v1/timetables/{id}/export/pdf` — generate printable PDF timetable grid; class, teacher, and room views; library TBD (TD-05: Flying Saucer vs headless Chrome) | 5 |
| EXPORT-02 | `GET /api/v1/timetables/{id}/export/csv` — CSV export of all lessons with teacher, room, period columns; Admin/Mod only | 3 |
| EXPORT-03 | `GET /api/v1/timetables/{id}/export/ical?userId=...` — personal `.ics` file scoped to a teacher or student's lessons | 4 |
| EXPORT-05 | Teacher utilization report: periods assigned vs capacity, gap count, subject distribution; JSON response | 4 |
| EXPORT-06 | Room utilization report: occupancy percentage per period, by room type; JSON response | 3 |
| EXPORT-07 | Subject coverage report: actual vs required periods per class; flags under/over-scheduled subjects | 3 |
| EXPORT-08 | `GET /api/v1/audit-log` — paginated audit log with filters (actor, entity type, date range); `@Audited` AOP auto-populates log from annotated service methods | 3 |

**Total: 25 SP**

---

## Epic 10 — Setup Templates (Post-MVP)

**Goal:** Provide built-in and custom setup templates that pre-populate bell schedules, constraint defaults, and terminology for common institution types. Reduces onboarding time from hours to minutes.

**Market driver:** Templates directly address adoption friction. The target market persona (non-expert Timetabler) benefits from a guided starting point. Templates are also a sales asset — a demo that starts from a relevant template converts better than a blank canvas.

| Story | Description | Points |
|---|---|---|
| TMPL-01 | Template data model + Flyway migration; `institution_templates` table with embedded configuration JSONB | 3 |
| TMPL-02 | Seed: 5 built-in templates — Primary School, Secondary School, High School / Sixth Form, Language School, Vocational Centre | 5 |
| TMPL-03 | `POST /api/v1/institutions/apply-template` — apply template settings and bell schedule to tenant; idempotent | 4 |
| TMPL-04 | `POST /api/v1/templates` — save current institution configuration as a reusable custom template | 3 |

**Total: 15 SP**

---

## Development Sequence (Recommended)

```
Sprint 1–2   Epic 1 (Foundation) + Epic 2 (Auth)
Sprint 3     Epic 3 (Institution Config) + Epic 4 (Holiday Calendar)
Sprint 4–5   Epic 5 (Resource Management)
Sprint 6–7   Epic 6 (Scheduling Engine)  ← highest value, highest risk
Sprint 8     Epic 7 (Cover & Delegation) + Epic 8 (Notifications)
Sprint 9     Epic 9 (Export & Reporting)
Post-MVP     Epic 10 (Setup Templates)
```

Epics 1 and 2 are strict prerequisites. Epics 3–5 can overlap once the Foundation is stable. Epic 6 should begin as soon as Resource Management entities exist in the database — the solver needs all domain objects.

---

## Open Technical Decisions Affecting Epics

| ID | Decision | Affects Epic | Recommendation |
|---|---|---|---|
| TD-01 | Solver isolation: in-process vs. microservice | 6 | In-process for MVP; extract to dedicated pod at K8s migration |
| TD-02 | WebSocket scaling: node-local vs. Redis pub/sub | 6, 7, 8 | Node-local for MVP; Redis before scaling beyond 1 backend replica |
| TD-04 | File storage: local volume vs. S3 | 9 | `StorageService` interface defined now; local volume for MVP |
| TD-05 | PDF export library: Flying Saucer vs. headless Chrome | 9 | Flying Saucer for simple grid; headless Chrome if pixel-perfect required |
| TD-06 | Holiday data source: government APIs vs. Calendarific | 4 | Calendarific free tier for MVP |

---

*Backend epics only. See `schediflow-frontend — Architecture & Development Specification` for frontend epics.*
