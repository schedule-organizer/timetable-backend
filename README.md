# SchediFlow Backend

Multi-tenant SaaS backend for automated school timetable management.
Built with **Spring Boot 3 / Java 21 / PostgreSQL 16 / Timefold Solver**.

This repo pins Java 21 via `.java-version` and `.sdkmanrc`, so running `sdk env install` (or `sdk env`/`sdk env install; sdk env` in your shell) automatically sets `JAVA_HOME` and Maven in place before you build.

---

## Running locally

PostgreSQL 16 is the only supported database — there is no embedded fallback. Run the services in
Docker and the application from your IDE, where you get a debugger and hot restart.
[`docker-compose.services.yml`](docker-compose.services.yml) contains the dependencies and nothing
else — PostgreSQL, MailHog, and Adminer behind a `tools` profile:

```bash
docker compose -f docker-compose.services.yml up -d
./mvnw spring-boot:run
```

Swagger is at http://localhost:8080/swagger-ui.html.

Add the `demo` profile to seed a full 21-class school — 30 teachers, 231 teaching groups, a
curriculum that fills every slot — so there is something real to look at:

```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
# sign in as admin@demo-school.demo / DemoPassw0rd!
```

The dataset's shape lives in [`application-demo.yml`](src/main/resources/application-demo.yml);
change grades, cycle length, curriculum or staffing there and it validates your edit on startup.

### The whole stack in Docker

[`docker-compose.yml`](docker-compose.yml) adds the backend container on top. It `extends` the
service definitions above rather than repeating them, so ports and credentials cannot drift between
the two files, and both share one Compose project — the same containers and the same `postgres_data`
volume, whichever file you use.

```bash
docker compose up -d                      # postgres + mailhog + backend
docker compose --profile tools up -d      # + adminer (DB browser) on :8081
docker compose --profile frontend up -d   # + frontend (needs ../schediflow-frontend)
```

Everything has a working default, so no `.env` is required to start; copy `.env.example` to `.env`
to change secrets before this is reachable by anyone else.

| File | Contains | Use when |
|---|---|---|
| `docker-compose.services.yml` | PostgreSQL, MailHog, Adminer | Developing — app runs from your IDE |
| `docker-compose.yml` | The above, plus backend and frontend | Running the assembled stack |

| Service | URL | Notes |
|---|---|---|
| Backend | http://localhost:8080 | Swagger at `/swagger-ui.html`, health at `/actuator/health` |
| MailHog | http://localhost:8025 | Every email the app sends |
| Adminer | http://localhost:8081 | `tools` profile; server `postgres` |
| PostgreSQL | `localhost:5432` | `schediflow` / `schediflow` |

### Tests

```bash
./mvnw test
```

The suite runs against a real PostgreSQL 16 that Testcontainers starts and stops for you — Docker
must be running, but there is nothing to set up and no compose stack to start first. Each run gets a
clean database; within a run, one container serves every test class.

Tests used to run on H2 in PostgreSQL compatibility mode. It was faster and needed no Docker, and it
let two `jsonb` columns mapped as `varchar` reach the main branch — breaking registration and custom
templates on the real database while the suite stayed green. The speed was not worth it.

---

## Documentation

### Product

| Document | Description |
|---|---|
| [Market Requirements Document](docs/mrd/SchediFlow_MRD_v1.0.md) | Business context, target market, personas, and product goals |

### Architecture

| Document | Description |
|---|---|
| [Backend Architecture v1.2](docs/architecture/schediflow-backend_Architecture_v1.2.md) | Technical specification: stack, domain model, API design, solver integration |

### Planning

| Document | Description |
|---|---|
| [Epics Overview](docs/epics/SchediFlow_Epics_v1.0.md) | All 10 epics with story tables, point estimates, and development sequence |

---

## Epics

| # | Epic | Points | MVP |
|---|---|---|---|
| [1](docs/epics/epic-1-foundation-infrastructure.md) | Foundation & Infrastructure | 21 SP | ✅ |
| [2](docs/epics/epic-2-authentication-user-management.md) | Authentication & User Management | 26 SP | ✅ |
| [3](docs/epics/epic-3-institution-configuration.md) | Institution Configuration | 18 SP | ✅ |
| [4](docs/epics/epic-4-holiday-vacation-calendar.md) | Holiday & Vacation Calendar | 17 SP | ✅ |
| [5](docs/epics/epic-5-resource-management.md) | Resource Management | 35 SP | ✅ |
| [6](docs/epics/epic-6-timetable-scheduling-engine.md) | Timetable & Scheduling Engine | 39 SP | ✅ |
| [7](docs/epics/epic-7-cover-delegation-temporary-schedules.md) | Cover, Delegation & Temporary Schedules | 25 SP | ✅ |
| [8](docs/epics/epic-8-notifications.md) | Notifications | 12 SP | ✅ |
| [9](docs/epics/epic-9-export-reporting.md) | Export & Reporting | 25 SP | ✅ |
| [10](docs/epics/epic-10-setup-templates.md) | Setup Templates | 15 SP | Post-MVP |

**MVP total: ~218 SP**

---

## Stories

Stories are in [`docs/stories/`](docs/stories/) — one file per story, prefixed with epic number for sequencing.

**Start here:** [1.found-01.md](docs/stories/1.found-01.md)

### Epic 1 — Foundation & Infrastructure
- [1.found-01](docs/stories/1.found-01.md) — Initialize Spring Boot Project (2 SP)
- [1.found-03](docs/stories/1.found-03.md) — Docker Compose Orchestration (3 SP)
- [1.found-04](docs/stories/1.found-04.md) — Flyway: Tenants & Users Migration (2 SP)
- [1.found-05](docs/stories/1.found-05.md) — JWT Security Infrastructure (5 SP)
- [1.found-06](docs/stories/1.found-06.md) — Multi-Tenancy with Hibernate Filter (5 SP)
- [1.found-07](docs/stories/1.found-07.md) — Springdoc OpenAPI / Swagger UI (1 SP)
- [1.found-08](docs/stories/1.found-08.md) — CORS Configuration (1 SP)
- [1.found-09](docs/stories/1.found-09.md) — Global Exception Handler (2 SP)

### Epic 2 — Authentication & User Management
- [2.auth-01](docs/stories/2.auth-01.md) — Institution Self-Registration (3 SP)
- [2.auth-02](docs/stories/2.auth-02.md) — Email/Password Login (3 SP)
- [2.auth-03](docs/stories/2.auth-03.md) — Token Refresh (2 SP)
- [2.auth-04](docs/stories/2.auth-04.md) — Logout (1 SP)
- [2.auth-05](docs/stories/2.auth-05.md) — Teacher Invitation (5 SP)
- [2.auth-06](docs/stories/2.auth-06.md) — Complete Registration via Invite (4 SP)
- [2.auth-07](docs/stories/2.auth-07.md) — Own Profile Read/Update (3 SP)
- [2.auth-08](docs/stories/2.auth-08.md) — Paginated User List (2 SP)
- [2.auth-09](docs/stories/2.auth-09.md) — Role Change (2 SP)
- [2.auth-10](docs/stories/2.auth-10.md) — Soft Deactivate User (1 SP)

### Epic 3 — Institution Configuration
- [3.config-01](docs/stories/3.config-01.md) — Academic Years CRUD (3 SP)
- [3.config-02](docs/stories/3.config-02.md) — Terms CRUD (3 SP)
- [3.config-03](docs/stories/3.config-03.md) — Bell Schedules & Periods CRUD (4 SP)
- [3.config-04](docs/stories/3.config-04.md) — Institution Settings (3 SP)
- [3.config-05](docs/stories/3.config-05.md) — Public Settings Endpoint (1 SP)
- [3.config-09](docs/stories/3.config-09.md) — Institution Seed Defaults (4 SP)

### Epic 4 — Holiday & Vacation Calendar
- [4.hol-01](docs/stories/4.hol-01.md) — Holiday Calendar CRUD (3 SP)
- [4.hol-02](docs/stories/4.hol-02.md) — Public Holiday Import (5 SP)
- [4.hol-03](docs/stories/4.hol-03.md) — Manual Holiday Date CRUD (2 SP)
- [4.hol-04](docs/stories/4.hol-04.md) — List Holiday Dates by Academic Year (1 SP)
- [4.hol-05](docs/stories/4.hol-05.md) — Solver Holiday Integration (3 SP)
- [4.hol-07](docs/stories/4.hol-07.md) — Holiday Conflict Detection (3 SP)

### Epic 5 — Resource Management
- [5.res-01](docs/stories/5.res-01.md) — Rooms CRUD (3 SP)
- [5.res-02](docs/stories/5.res-02.md) — Subjects CRUD (3 SP)
- [5.res-03](docs/stories/5.res-03.md) — School Classes CRUD (3 SP)
- [5.res-04](docs/stories/5.res-04.md) — Teacher Profiles CRUD (3 SP)
- [5.res-05](docs/stories/5.res-05.md) — Teacher Qualifications (2 SP)
- [5.res-06](docs/stories/5.res-06.md) — Class Subject Hours Matrix (3 SP)
- [5.res-07](docs/stories/5.res-07.md) — Teaching Groups CRUD (4 SP)
- [5.res-08](docs/stories/5.res-08.md) — Option Blocks CRUD (4 SP)
- [5.res-09](docs/stories/5.res-09.md) — Forbidden Slots (3 SP)
- [5.res-10](docs/stories/5.res-10.md) — Teacher Availability View (2 SP)
- [5.res-11](docs/stories/5.res-11.md) — CSV Bulk Import (8 SP)

### Epic 6 — Timetable & Scheduling Engine
- [6.sched-01](docs/stories/6.sched-01.md) — Timetable CRUD & Lifecycle (2 SP)
- [6.sched-02](docs/stories/6.sched-02.md) — Timetable Lesson Grid (3 SP)
- [6.sched-03](docs/stories/6.sched-03.md) — Async Solver Engine (8 SP)
- [6.sched-04](docs/stories/6.sched-04.md) — Solver Job Status Polling (2 SP)
- [6.sched-05](docs/stories/6.sched-05.md) — Cancel Solver Job (2 SP)
- [6.sched-06](docs/stories/6.sched-06.md) — Solver WebSocket Progress Events (4 SP)
- [6.sched-07](docs/stories/6.sched-07.md) — Publish Timetable (3 SP)
- [6.sched-08](docs/stories/6.sched-08.md) — Move Lesson (Drag & Drop) (3 SP)
- [6.sched-09](docs/stories/6.sched-09.md) — Pin/Unpin Lesson (2 SP)
- [6.sched-10](docs/stories/6.sched-10.md) — Atomic Lesson Swap (3 SP)
- [6.sched-11](docs/stories/6.sched-11.md) — Conflict Detection Service (5 SP)
- [6.sched-12](docs/stories/6.sched-12.md) — Lesson Updated WebSocket Broadcast (2 SP)
- [6.sched-13](docs/stories/6.sched-13.md) — Schedule Checkpoints, Save & Restore (5 SP)
- [6.sched-14](docs/stories/6.sched-14.md) — Targeted Partial Regeneration (5 SP)
- [6.sched-17](docs/stories/6.sched-17.md) — Lesson Generation from Curriculum (8 SP) _(ready for dev)_
- [6.sched-18](docs/stories/6.sched-18.md) — Planning Model & Core Clash Constraints (8 SP) _(ready for dev)_

### Epic 7 — Cover, Delegation & Temporary Schedules
- [7.cover-01](docs/stories/7.cover-01.md) — Assign Cover Teacher (4 SP)
- [7.cover-02](docs/stories/7.cover-02.md) — Cover Candidate Suggestions (4 SP)
- [7.cover-03](docs/stories/7.cover-03.md) — Submit Delegation Request (3 SP)
- [7.cover-04](docs/stories/7.cover-04.md) — Approve/Reject Delegation (3 SP)
- [7.cover-05](docs/stories/7.cover-05.md) — Temporary Schedules CRUD (5 SP)
- [7.cover-06](docs/stories/7.cover-06.md) — Auto-Expire Temporary Schedules (3 SP)
- [7.cover-07](docs/stories/7.cover-07.md) — Cover & Delegation WebSocket Events (3 SP)

### Epic 8 — Notifications
- [8.notif-01](docs/stories/8.notif-01.md) — WebSocket Infrastructure (4 SP)
- [8.notif-02](docs/stories/8.notif-02.md) — Timetable Published Notification (4 SP)
- [8.notif-03](docs/stories/8.notif-03.md) — Transactional Email Service (4 SP)

### Epic 9 — Export & Reporting
- [9.export-01](docs/stories/9.export-01.md) — PDF Timetable Export (5 SP)
- [9.export-02](docs/stories/9.export-02.md) — CSV Timetable Export (3 SP)
- [9.export-03](docs/stories/9.export-03.md) — iCal Personal Schedule Export (4 SP)
- [9.export-05](docs/stories/9.export-05.md) — Teacher Utilization Report (4 SP)
- [9.export-06](docs/stories/9.export-06.md) — Room Utilization Report (3 SP)
- [9.export-07](docs/stories/9.export-07.md) — Subject Coverage Report (3 SP)
- [9.export-08](docs/stories/9.export-08.md) — Audit Log (3 SP)

### Epic 10 — Setup Templates _(Post-MVP)_
- [10.tmpl-01](docs/stories/10.tmpl-01.md) — Template Data Model (3 SP)
- [10.tmpl-02](docs/stories/10.tmpl-02.md) — Built-in Template Seeding (5 SP)
- [10.tmpl-03](docs/stories/10.tmpl-03.md) — Apply Template to Institution (4 SP)
- [10.tmpl-04](docs/stories/10.tmpl-04.md) — Save Custom Template (3 SP)

---

## Development Sequence

```
Sprint 1–2   Epic 1 (Foundation) + Epic 2 (Auth)
Sprint 3     Epic 3 (Institution Config) + Epic 4 (Holiday Calendar)
Sprint 4–5   Epic 5 (Resource Management)
Sprint 6–7   Epic 6 (Scheduling Engine)   ← highest value, highest risk
Sprint 8     Epic 7 (Cover & Delegation) + Epic 8 (Notifications)
Sprint 9     Epic 9 (Export & Reporting)
Post-MVP     Epic 10 (Setup Templates)
```

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Solver | Timefold Solver (in-process JVM) |
| Auth | JWT (access 15 min) + HttpOnly refresh cookie (7 days) |
| API Docs | Springdoc OpenAPI 3 / Swagger UI |
| Real-time | Spring WebSocket + STOMP |
| Email (MVP) | SMTP via MailHog |
| Containerisation | Docker Compose |
