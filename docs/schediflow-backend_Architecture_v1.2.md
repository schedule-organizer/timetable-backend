# schediflow-backend — Architecture & Development Specification
**Version 1.2 | March 2026 | Internal Engineering Document**

> **Related documents:**
> - `SchediFlow_PRD_v3.0` — product requirements
> - `schediflow-frontend — Architecture & Development Specification` — frontend counterpart
> - `SchediFlow_MRD_v1.0` — market requirements

---

## Table of Contents

1. [Document Purpose](#1-document-purpose)
2. [System Architecture Overview](#2-system-architecture-overview)
3. [API Contract — Consumed by Frontend](#3-api-contract--consumed-by-frontend)
4. [WebSocket Contract — Real-Time Layer](#4-websocket-contract--real-time-layer)
5. [Authentication & Security](#5-authentication--security)
6. [Technology Stack](#6-technology-stack)
7. [Scheduling Engine — Timefold Solver](#7-scheduling-engine--timefold-solver)
8. [Package Structure](#8-package-structure)
9. [Database Schema](#9-database-schema)
10. [Key Architectural Patterns](#10-key-architectural-patterns)
11. [Deployment](#11-deployment)
12. [Development Epics & Stories — Backend](#13-development-epics--stories--backend)
13. [Additional Recommendations](#13-additional-recommendations)
14. [Open Technical Decisions](#14-open-technical-decisions)

---

## 1. Document Purpose

This document covers the **schediflow-backend** project only. It defines the technology stack, internal architecture, database schema, scheduling engine integration, API and WebSocket contracts, deployment configuration, and backend development stories.

The frontend project is covered in a separate document: `schediflow-frontend — Architecture & Development Specification`.

Sections 2, 3, and 4 (System Overview, API Contract, WebSocket Contract) are **intentionally duplicated** in both documents — they represent the shared contract between the two projects and must stay in sync.

---

## 2. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                              │
│  React 18 + TypeScript + Vite                                    │
│  shadcn/ui + Tailwind CSS + React Query + Zustand               │
│  STOMP WebSocket client                                          │
└──────────────────────┬──────────────────────────────────────────┘
                       │  HTTPS / WSS
┌──────────────────────▼──────────────────────────────────────────┐
│                 schediflow-backend                                │
│  Spring Boot 3.x / Java 21                                       │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │  REST API Layer  │  │ WebSocket/STOMP  │  │  Scheduler     │  │
│  │  (Controllers)   │  │  (Real-time)     │  │  Engine        │  │
│  └────────┬────────┘  └────────┬─────────┘  │  (Timefold)    │  │
│           │                    │             └───────┬────────┘  │
│  ┌────────▼────────────────────▼──────────────────┐ │           │
│  │              Service Layer                      │◄┘           │
│  │  (Business logic, constraint compilation,       │             │
│  │   tenant resolution, RBAC enforcement)          │             │
│  └────────────────────┬────────────────────────────┘            │
│  ┌─────────────────────▼────────────────────────────────────┐   │
│  │  Data Access Layer — Spring Data JPA / Flyway             │   │
│  └─────────────────────┬────────────────────────────────────┘   │
└────────────────────────┼────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│  PostgreSQL 16                                                    │
│  Multi-tenant: shared schema, row-level tenant_id isolation      │
└─────────────────────────────────────────────────────────────────┘
```

### 2.1 Two-Project Repository Structure

```
schediflow/
├── schediflow-backend/          ← this project
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   ├── db/migration/        # Flyway SQL files (V001__, V002__, ...)
│   │   └── application.yml
│   └── docker/
│       └── Dockerfile
│
├── schediflow-frontend/         ← separate project (see frontend spec)
│
└── docker-compose.yml           # orchestrates both projects + postgres + mailhog
```

---

## 3. API Contract — Consumed by Frontend

> **This section is duplicated in the frontend spec.** Changes here must be reflected there.

### 3.1 URL Conventions

```
GET    /api/v1/{resource}              → List (paginated)
GET    /api/v1/{resource}/{id}         → Get one
POST   /api/v1/{resource}              → Create
PUT    /api/v1/{resource}/{id}         → Full update
PATCH  /api/v1/{resource}/{id}         → Partial update
DELETE /api/v1/{resource}/{id}         → Delete (soft where appropriate)

# Nested resources
GET    /api/v1/classes/{classId}/subject-hours
PUT    /api/v1/classes/{classId}/subject-hours/{subjectId}

# Actions (not CRUD)
POST   /api/v1/timetables/{id}/publish
POST   /api/v1/timetables/{id}/archive
POST   /api/v1/lessons/{id}/pin
DELETE /api/v1/lessons/{id}/pin
POST   /api/v1/lessons/{id}/swap       { "targetLessonId": "..." }
POST   /api/v1/engine/run
POST   /api/v1/users/invite
POST   /api/v1/auth/complete-registration
```

### 3.2 Pagination Envelope

All paginated list responses:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 247,
  "totalPages": 13
}
```

### 3.3 Error Envelope

All error responses:
```json
{
  "status": 400,
  "code": "CONSTRAINT_VIOLATION",
  "message": "Teacher is not qualified for subject",
  "details": { "teacherId": "...", "subjectId": "..." },
  "timestamp": "2026-03-21T10:00:00Z"
}
```

### 3.4 Tenant Resolution

`tenant_id` is **never** passed as a query parameter or request body field. It is always extracted from the authenticated JWT token server-side. A user cannot access or modify data from another tenant.

### 3.5 API Versioning

Version in URL path: `/api/v1/`. When breaking changes are required, `/api/v2/` is introduced alongside v1. v1 is deprecated with a published sunset date before removal.

### 3.6 Full Endpoint Reference

| Method | Path | Description | Roles |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Institution self-registration | Public |
| POST | `/api/v1/auth/login` | Login → JWT + refresh token | Public |
| POST | `/api/v1/auth/refresh` | Refresh access token | Public |
| POST | `/api/v1/auth/logout` | Invalidate refresh token | Auth |
| POST | `/api/v1/auth/complete-registration` | Teacher completes invite | Public |
| GET/PUT | `/api/v1/users/me` | Personal profile | All |
| GET | `/api/v1/users` | List users (paginated) | Admin, Mod |
| POST | `/api/v1/users/invite` | Invite teacher | Admin, Mod |
| PUT | `/api/v1/users/{id}/role` | Change role | Admin |
| DELETE | `/api/v1/users/{id}` | Deactivate user | Admin |
| GET/PUT | `/api/v1/settings` | Institution settings | Admin |
| GET | `/api/v1/settings/public` | Public settings (locale etc.) | Public |
| GET | `/api/v1/settings/labels` | Configurable terminology | Auth |
| CRUD | `/api/v1/academic-years` | Academic years | Admin |
| CRUD | `/api/v1/terms` | Terms | Admin |
| CRUD | `/api/v1/bell-schedules` | Bell schedules + periods | Admin |
| CRUD | `/api/v1/rooms` | Rooms | Admin, Mod |
| CRUD | `/api/v1/subjects` | Subjects | Admin, Mod |
| CRUD | `/api/v1/classes` | School classes | Admin, Mod |
| GET/PUT | `/api/v1/classes/{id}/subject-hours` | Hours matrix | Admin, Mod |
| CRUD | `/api/v1/teachers` | Teacher profiles | Admin, Mod |
| POST/DELETE | `/api/v1/teachers/{id}/qualifications` | Subject qualifications | Admin, Mod |
| GET | `/api/v1/teachers/{id}/availability` | Forbidden slots + preferences | Auth |
| CRUD | `/api/v1/teaching-groups` | Teaching groups | Admin, Mod |
| CRUD | `/api/v1/option-blocks` | Option blocks | Admin, Mod |
| POST/DELETE | `/api/v1/forbidden-slots` | Forbidden slots | Admin, Mod |
| CRUD | `/api/v1/timetables` | Timetables | Admin, Mod |
| GET | `/api/v1/timetables/{id}/lessons` | All lessons for grid | Auth |
| POST | `/api/v1/timetables/{id}/publish` | Publish timetable | Admin, Mod |
| POST | `/api/v1/timetables/{id}/archive` | Archive timetable | Admin, Mod |
| PATCH | `/api/v1/lessons/{id}` | Move lesson (drag/drop) | Admin, Mod |
| POST/DELETE | `/api/v1/lessons/{id}/pin` | Pin / unpin card | Admin, Mod |
| POST | `/api/v1/lessons/{id}/swap` | Swap two cards | Admin, Mod |
| POST | `/api/v1/engine/run` | Start solver job | Admin, Mod |
| GET | `/api/v1/engine/jobs/{id}` | Poll job status | Admin, Mod |
| POST | `/api/v1/engine/jobs/{id}/cancel` | Cancel running job | Admin, Mod |
| GET | `/api/v1/engine/jobs` | Job history for timetable | Admin, Mod |
| CRUD | `/api/v1/holidays` | Holiday calendar | Admin |
| POST | `/api/v1/holidays/import` | Import from public feed | Admin |
| POST | `/api/v1/cover` | Assign cover teacher | Admin, Mod |
| GET | `/api/v1/cover/candidates` | Qualified available teachers | Admin, Mod |
| POST | `/api/v1/delegation` | Submit delegation request | Teacher |
| PATCH | `/api/v1/delegation/{id}` | Approve / reject | Admin, Mod |
| CRUD | `/api/v1/temporary-schedules` | Temporary schedules | Admin, Mod |
| GET | `/api/v1/timetables/{id}/export/pdf` | PDF export | Auth |
| GET | `/api/v1/timetables/{id}/export/csv` | CSV export | Admin, Mod |
| GET | `/api/v1/timetables/{id}/export/ical` | Personal .ics export | Teacher, Student |
| GET | `/api/v1/audit-log` | Audit log (paginated) | Admin |

---

## 4. WebSocket Contract — Real-Time Layer

> **This section is duplicated in the frontend spec.** Changes here must be reflected there.

**Protocol:** STOMP over WebSocket, with SockJS fallback.
**Endpoint:** `ws://{host}/ws`

### 4.1 Topics

| Topic | Description | Subscribers |
|---|---|---|
| `/topic/timetable/{timetableId}` | Lesson moved, pinned, conflict changed | All users viewing that timetable |
| `/topic/solver/{jobId}/progress` | Score updates and % complete during engine run | Admin/Mod who triggered the run |
| `/topic/solver/{jobId}/complete` | Final result + quality score | Admin/Mod who triggered the run |
| `/topic/tenant/{tenantId}/notifications` | Cover, delegation, timetable published | All users in the tenant |
| `/user/queue/personal` | User-targeted notifications | Individual user |

### 4.2 Message Payloads

```json
// LESSON_UPDATED
{
  "type": "LESSON_UPDATED",
  "payload": {
    "lessonId": "...",
    "periodSlotId": "...",
    "roomId": "...",
    "isPinned": true,
    "hasConflict": false
  }
}

// SOLVER_PROGRESS
{
  "type": "SOLVER_PROGRESS",
  "payload": {
    "jobId": "...",
    "percentComplete": 47,
    "currentScore": "-3hard/0medium/-12soft",
    "hardViolations": 3,
    "elapsedSeconds": 14
  }
}

// SOLVER_COMPLETE
{
  "type": "SOLVER_COMPLETE",
  "payload": {
    "jobId": "...",
    "status": "COMPLETED",
    "qualityScore": 84,
    "scoreBreakdown": { "hardSatisfaction": 100, "difficultyBalance": 78, ... }
  }
}

// COVER_ASSIGNED
{
  "type": "COVER_ASSIGNED",
  "payload": {
    "lessonId": "...",
    "coverTeacherName": "Ms. Garcia",
    "date": "2026-04-14",
    "subject": "Chemistry"
  }
}

// DELEGATION_UPDATE
{
  "type": "DELEGATION_UPDATE",
  "payload": {
    "delegationId": "...",
    "status": "APPROVED",
    "message": "Approved by Admin"
  }
}

// TIMETABLE_PUBLISHED
{
  "type": "TIMETABLE_PUBLISHED",
  "payload": {
    "timetableId": "...",
    "termName": "Semester 1 2026"
  }
}
```

---

## 5. Authentication & Security

### 5.1 MVP Authentication Flow (No OTP)

Twilio OTP and AWS email integration are explicitly **post-MVP**. MVP uses simple email/password with an invitation token flow.

```
1. Admin/Mod  →  POST /api/v1/users/invite  →  System creates user (PENDING_REGISTRATION)
2. System     →  Sends email with one-time registration link (token valid 72h)
3. Teacher    →  Opens link  →  GET /auth/complete-registration?token=...
4. Teacher    →  POST /api/v1/auth/complete-registration  →  sets password, saves profile
5. Teacher    →  POST /api/v1/auth/login  →  receives JWT access token + refresh token
```

### 5.2 JWT Strategy

| Token | Expiry | Storage |
|---|---|---|
| Access token | 15 minutes | Memory / `Authorization: Bearer` header |
| Refresh token | 7 days | `HttpOnly` cookie (never in localStorage) |

**JWT payload:** `{ sub: userId, tenantId, role, email }`

### 5.3 RBAC Enforcement

- Spring Security method-level: `@PreAuthorize("hasRole('ADMIN')")`
- Custom `@TenantScoped` annotation injects `tenantId` from token into service methods
- Controller layer extracts tenant from JWT — never trusts client-provided tenant ID
- All repository queries are scoped with `tenant_id` filter via a global JPA `@Filter`

### 5.4 Security Checklist (MVP)

- [ ] All endpoints require authentication except `/api/v1/auth/**`, `/api/v1/settings/public`, `/actuator/health`
- [ ] CORS configured to allow only the known frontend origin
- [ ] Rate limiting on `/auth/**` endpoints (max 10 req/min per IP)
- [ ] Engine endpoint: max 3 concurrent solver jobs per tenant
- [ ] Passwords hashed with BCrypt (cost factor 12)
- [ ] Registration tokens expire after 72 hours and are single-use
- [ ] No sensitive data in JWT payload beyond the fields listed above
- [ ] SQL injection prevented by JPA parameterized queries (default)
- [ ] HTTPS enforced — NGINX reverse proxy in Docker Compose (self-signed cert local; real cert prod)

### 5.5 Post-MVP Auth Roadmap

- AWS SES replaces MailHog SMTP for transactional email
- Twilio OTP (SMS) for 2FA on login
- SAML 2.0 / OIDC SSO for Enterprise tier
- All auth events written to audit log

---

## 6. Technology Stack

| Component | Choice | Rationale |
|---|---|---|
| Language | Java 21 | LTS; virtual threads (Project Loom) improve concurrency without reactive complexity |
| Framework | Spring Boot 3.x | Industry standard; excellent Timefold integration; Spring Security; Spring Data JPA |
| ORM | Spring Data JPA + Hibernate | Mature; PostgreSQL dialect; multi-tenancy SPI available for future schema-per-tenant |
| Database | PostgreSQL 16 | JSONB for settings/config blobs; robust; row-level multi-tenancy support |
| WebSocket | Spring WebSocket + STOMP | Matches SockJS/STOMP on the frontend; built-in to Spring Boot |
| Migrations | Flyway | Version-controlled SQL migrations; Spring Boot auto-runs on startup |
| DTO Mapping | MapStruct | Compile-time mapper generation; zero reflection overhead |
| Validation | Jakarta Bean Validation | Declarative `@NotNull`, `@Size`, etc. on DTOs |
| API Docs | Springdoc OpenAPI 3 | Auto-generated Swagger UI at `/api-docs`; contract visible to frontend team |
| Testing | JUnit 5 + Mockito + Testcontainers | Unit, service integration, full DB integration tests |
| Build | Maven | Standard for Spring Boot; all Timefold quickstarts use Maven |
| Scheduling engine | Timefold Solver Community Edition | See Section 7 |

---

## 7. Scheduling Engine — Timefold Solver

### 7.1 Execution Model — Local, In-Process, Free

Timefold Community Edition is a plain Maven dependency. The solver runs **inside the Spring Boot JVM process** on the local machine. There is no cloud component, no Timefold account, no API key, and no internet connection required at runtime.

```xml
<!-- pom.xml — the only change from OptaPlanner is the groupId -->
<dependency>
    <groupId>ai.timefold.solver</groupId>
    <artifactId>timefold-solver-spring-boot-starter</artifactId>
    <version>1.x.x</version>
</dependency>
```

When the engine is triggered:
1. `SolverJobService` loads all scheduling data from PostgreSQL into memory
2. Builds a `TimetableSolution` object
3. Calls `SolverManager.solve(...)` — runs locally on the server's CPU
4. Emits progress events via WebSocket as better solutions are found
5. Persists the final solution to PostgreSQL
6. No network calls at any step

### 7.2 Why Timefold Instead of OptaPlanner

OptaPlanner is **End-of-Life** (Red Hat/IBM). Timefold is its direct continuation by the same team. The annotation API (`@PlanningEntity`, `@PlanningSolution`, `@PlanningVariable`, `@ConstraintProvider`) is ~95% identical — prior OptaPlanner knowledge transfers directly.

| Factor | OptaPlanner | Timefold CE |
|---|---|---|
| Maintenance | ❌ EOL | ✅ Active |
| Cost | Free | Free (Apache 2.0) |
| Runs locally | ✅ | ✅ |
| Java 21 | ⚠️ Partial | ✅ Full |
| Spring Boot 3 | ⚠️ Partial | ✅ First-class |
| Performance | Baseline | ~2× faster |

### 7.3 Domain Model

```java
@PlanningSolution
public class TimetableSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<PeriodSlot> periodSlots;   // day × period combinations

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Room> rooms;

    @PlanningEntityCollectionProperty
    private List<LessonAssignment> lessons;

    @PlanningScore
    private HardMediumSoftScore score;
    // Hard   = hard constraint violations (must be 0 to publish)
    // Medium = unmet weighted soft preferences
    // Soft   = optimization objectives
}

@PlanningEntity
public class LessonAssignment {
    private UUID id;
    private TeachingGroup teachingGroup;
    private Subject subject;
    private Teacher teacher;
    private boolean isPinned;          // pinned → excluded from solver via @PinningFilter

    @PlanningVariable
    private PeriodSlot periodSlot;     // assigned by solver

    @PlanningVariable
    private Room room;                 // assigned by solver
}
```

### 7.4 Constraint Provider

```java
@ConstraintProvider
public class TimetableConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[]{
            // ── HARD (must never be violated) ──────────────────────
            teacherConflict(cf),
            roomConflict(cf),
            classConflict(cf),
            roomCapacityConstraint(cf),
            roomTypeCompatibility(cf),
            teacherForbiddenSlot(cf),
            classForbiddenSlot(cf),
            roomForbiddenSlot(cf),
            holidayExclusion(cf),
            optionBlockSimultaneous(cf),
            spreadCompliance(cf),
            activityQuota(cf),
            // pinned cards: excluded via @PinningFilter, not a constraint

            // ── SOFT (weighted, configurable per institution) ───────
            difficultyDistribution(cf),
            noDifficultyStacking(cf),
            teacherGapMinimization(cf),
            teacherPreference(cf),
            balancedDailyLoad(cf),
            homeroomPreference(cf),
            teacherWorkloadBalance(cf),
        };
    }
}
```

### 7.5 Sensitivity → Constraint Weights

The Generator Sensitivity dial (Strict / Balanced / Lenient / Minimal) maps to constraint weight multipliers applied before the solver run. Hard constraints are never modified regardless of sensitivity.

```java
public SolverConfig buildSolverConfig(ConstraintConfig config, Sensitivity sensitivity) {
    // sensitivity multiplier: STRICT=1.0, BALANCED=0.7, LENIENT=0.4, MINIMAL=0.1
    // applied only to low/medium weight soft constraints
    // high-weight soft constraints remain at full weight in all modes except MINIMAL
}
```

### 7.6 Async Execution & Job Tracking

```java
@Service
public class SolverJobService {

    @Async("solverThreadPool")   // isolated thread pool — prevents blocking request threads
    public CompletableFuture<SolverJob> runSolver(UUID timetableId, SolverRunRequest req) {
        SolverJob job = createJob(timetableId, req);                     // persisted as QUEUED
        TimetableSolution problem = loadProblem(timetableId);            // load from DB
        applyPinningFilter(problem);                                      // lock pinned cards
        applySensitivity(req.getSensitivity(), problem.getConstraints()); // adjust weights

        solverManager.solveAndListen(
            job.getId(),
            problem,
            bestSolution -> onBestSolutionFound(job, bestSolution),      // WebSocket progress
            finalSolution -> onSolverTerminated(job, finalSolution)      // persist + notify
        );
        return CompletableFuture.completedFuture(job);
    }
}
```

### 7.7 Solver Timeout & Resource Limits

Timefold runs until explicitly stopped. Define limits to prevent runaway jobs:

| Subscription Tier | Max solver duration | Max concurrent jobs per tenant |
|---|---|---|
| Starter | 30 seconds | 1 |
| Professional | 2 minutes | 2 |
| Enterprise | 10 minutes | 5 |

Configured via `application.yml` and enforced in `SolverJobService`.

---

## 8. Package Structure

```
com.schediflow
├── api/
│   ├── v1/
│   │   ├── AuthController
│   │   ├── UserController
│   │   ├── SettingsController
│   │   ├── AcademicYearController
│   │   ├── TermController
│   │   ├── BellScheduleController
│   │   ├── RoomController
│   │   ├── SubjectController
│   │   ├── ClassController
│   │   ├── TeacherController
│   │   ├── TeachingGroupController
│   │   ├── OptionBlockController
│   │   ├── ForbiddenSlotController
│   │   ├── TimetableController
│   │   ├── LessonController
│   │   ├── EngineController
│   │   ├── HolidayController
│   │   ├── CoverController
│   │   ├── DelegationController
│   │   ├── TemporaryScheduleController
│   │   ├── ExportController
│   │   └── AuditLogController
│   └── advice/
│       └── GlobalExceptionHandler
│
├── config/
│   ├── SecurityConfig
│   ├── WebSocketConfig
│   ├── OpenApiConfig
│   ├── AsyncConfig           # solver thread pool definition
│   ├── FlywayConfig
│   └── TenantConfig
│
├── domain/                   # JPA entities — never serialized to API responses
│   ├── Tenant
│   ├── User, UserRole
│   ├── AcademicYear, Term
│   ├── BellSchedule, Period
│   ├── Room
│   ├── Subject
│   ├── SchoolClass
│   ├── Student
│   ├── Teacher, TeacherSubjectQualification
│   ├── TeachingGroup, TeachingGroupClass
│   ├── OptionBlock
│   ├── Timetable
│   ├── Lesson
│   ├── ForbiddenSlot
│   ├── CoverAssignment
│   ├── DelegationRequest
│   ├── TemporarySchedule
│   ├── HolidayCalendar, HolidayDate
│   ├── ConstraintConfig
│   ├── SolverJob
│   └── AuditLogEntry
│
├── dto/                      # Request and Response DTOs (MapStruct mappers alongside)
│   ├── request/
│   └── response/
│
├── exception/
│   ├── SchediFlowException   # base exception
│   ├── ResourceNotFoundException
│   ├── ConflictException
│   ├── TenantAccessException
│   └── SolverException
│
├── repository/               # Spring Data JPA repositories
│
├── security/
│   ├── JwtTokenProvider
│   ├── JwtAuthenticationFilter
│   ├── TenantContext         # ThreadLocal tenant holder
│   └── TenantFilter          # populates TenantContext from JWT on each request
│
├── service/                  # Business logic — one service per domain area
│   ├── AuthService
│   ├── UserService
│   ├── TenantService
│   ├── SettingsService
│   ├── AcademicYearService
│   ├── BellScheduleService
│   ├── RoomService
│   ├── SubjectService
│   ├── ClassService
│   ├── TeacherService
│   ├── TeachingGroupService
│   ├── TimetableService
│   ├── LessonService
│   ├── ConflictDetectionService
│   ├── HolidayService
│   ├── CoverService
│   ├── DelegationService
│   ├── TemporaryScheduleService
│   ├── ExportService
│   ├── NotificationService
│   ├── EmailService          # interface → SmtpEmailService (MVP) → SesEmailService (post-MVP)
│   ├── StorageService        # interface → LocalStorageService (MVP) → S3Service (post-MVP)
│   └── AuditService          # populated via @Audited AOP, not called directly
│
├── solver/
│   ├── TimetableSolution     # @PlanningSolution
│   ├── LessonAssignment      # @PlanningEntity
│   ├── PeriodSlot            # problem fact (day + period combination)
│   ├── TimetableConstraintProvider
│   ├── SolverJobService      # async execution, job lifecycle
│   ├── SolverConfigBuilder   # applies sensitivity → constraint weights
│   └── SolverResultMapper    # TimetableSolution → persisted Lessons
│
└── websocket/
    ├── WebSocketEventPublisher   # publishes typed events to STOMP topics
    └── SolverProgressHandler     # receives Timefold progress callbacks, forwards to WebSocket
```

---

## 9. Database Schema

### 9.1 Core Tables

```sql
-- ── TENANTS ──────────────────────────────────────────────────────
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            VARCHAR(63) UNIQUE NOT NULL,
    name            VARCHAR(255) NOT NULL,
    settings        JSONB NOT NULL DEFAULT '{}',
    subscription    VARCHAR(50) NOT NULL DEFAULT 'TRIAL',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── USERS ─────────────────────────────────────────────────────────
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255),
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    avatar_url          VARCHAR(500),
    about_me            TEXT,
    role                VARCHAR(50) NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING_REGISTRATION',
    registration_token  VARCHAR(255),
    token_expires_at    TIMESTAMPTZ,
    profile_data        JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, email)
);

-- ── ACADEMIC STRUCTURE ────────────────────────────────────────────
CREATE TABLE academic_years (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(100) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE terms (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    academic_year_id UUID NOT NULL REFERENCES academic_years(id),
    name             VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    ordinal          INTEGER NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── BELL SCHEDULE ─────────────────────────────────────────────────
CREATE TABLE bell_schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(100) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE periods (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    bell_schedule_id UUID NOT NULL REFERENCES bell_schedules(id),
    ordinal          INTEGER NOT NULL,
    label            VARCHAR(50) NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    is_break         BOOLEAN NOT NULL DEFAULT FALSE,
    is_lunch         BOOLEAN NOT NULL DEFAULT FALSE
);

-- ── ROOMS ─────────────────────────────────────────────────────────
CREATE TABLE rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(20),
    room_type       VARCHAR(100) NOT NULL,
    capacity        INTEGER NOT NULL,
    building        VARCHAR(100),
    floor           VARCHAR(50),
    equipment_tags  TEXT[],
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── SUBJECTS ──────────────────────────────────────────────────────
CREATE TABLE subjects (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    name               VARCHAR(100) NOT NULL,
    code               VARCHAR(20),
    category           VARCHAR(100) NOT NULL,
    difficulty_level   INTEGER NOT NULL DEFAULT 3 CHECK (difficulty_level BETWEEN 1 AND 5),
    color_hex          VARCHAR(7),
    required_room_type VARCHAR(100),
    default_spread     VARCHAR(50),
    max_per_day        INTEGER NOT NULL DEFAULT 1,
    min_gap_days       INTEGER NOT NULL DEFAULT 0,
    department_id      UUID,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── TEACHERS ──────────────────────────────────────────────────────
CREATE TABLE teachers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    user_id                 UUID NOT NULL REFERENCES users(id),
    employee_id             VARCHAR(50),
    max_periods_per_cycle   INTEGER NOT NULL DEFAULT 30,
    max_consecutive_periods INTEGER NOT NULL DEFAULT 4,
    max_gaps_per_day        INTEGER NOT NULL DEFAULT 2,
    department_id           UUID,
    status                  VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE teacher_subject_qualifications (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES tenants(id),
    teacher_id               UUID NOT NULL REFERENCES teachers(id),
    subject_id               UUID NOT NULL REFERENCES subjects(id),
    periods_per_cycle_allocation INTEGER,
    notes                    TEXT,
    UNIQUE (teacher_id, subject_id)
);

-- ── CLASSES ───────────────────────────────────────────────────────
CREATE TABLE school_classes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20),
    year_level  VARCHAR(20),
    homeroom_id UUID REFERENCES rooms(id),
    capacity    INTEGER,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE class_subject_hours (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    class_id          UUID NOT NULL REFERENCES school_classes(id),
    subject_id        UUID NOT NULL REFERENCES subjects(id),
    periods_per_cycle INTEGER NOT NULL,
    spread_pattern    VARCHAR(50),
    UNIQUE (class_id, subject_id)
);

-- ── TEACHING GROUPS & OPTION BLOCKS ──────────────────────────────
CREATE TABLE option_blocks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    term_id          UUID NOT NULL REFERENCES terms(id),
    name             VARCHAR(100) NOT NULL,
    periods_per_cycle INTEGER NOT NULL
);

CREATE TABLE teaching_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    term_id         UUID NOT NULL REFERENCES terms(id),
    subject_id      UUID NOT NULL REFERENCES subjects(id),
    name            VARCHAR(100) NOT NULL,
    group_type      VARCHAR(50) NOT NULL DEFAULT 'SET',
    option_block_id UUID REFERENCES option_blocks(id),
    teacher_id      UUID REFERENCES teachers(id),
    room_id         UUID REFERENCES rooms(id)
);

CREATE TABLE teaching_group_classes (
    teaching_group_id UUID NOT NULL REFERENCES teaching_groups(id),
    class_id          UUID NOT NULL REFERENCES school_classes(id),
    PRIMARY KEY (teaching_group_id, class_id)
);

-- ── TIMETABLES & LESSONS ──────────────────────────────────────────
CREATE TABLE timetables (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    term_id         UUID NOT NULL REFERENCES terms(id),
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    published_at    TIMESTAMPTZ,
    publish_at      TIMESTAMPTZ,
    quality_score   INTEGER,
    score_breakdown JSONB,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE lessons (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    timetable_id      UUID NOT NULL REFERENCES timetables(id),
    teaching_group_id UUID REFERENCES teaching_groups(id),
    class_id          UUID REFERENCES school_classes(id),
    subject_id        UUID NOT NULL REFERENCES subjects(id),
    teacher_id        UUID NOT NULL REFERENCES teachers(id),
    room_id           UUID NOT NULL REFERENCES rooms(id),
    period_id         UUID NOT NULL REFERENCES periods(id),
    cycle_day         INTEGER NOT NULL,
    duration_periods  INTEGER NOT NULL DEFAULT 1,
    is_pinned         BOOLEAN NOT NULL DEFAULT FALSE,
    pin_reason        TEXT,
    has_conflict      BOOLEAN NOT NULL DEFAULT FALSE,
    conflict_details  JSONB
);

-- ── FORBIDDEN SLOTS ───────────────────────────────────────────────
CREATE TABLE forbidden_slots (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    entity_type   VARCHAR(50) NOT NULL,  -- TEACHER, ROOM, CLASS
    entity_id     UUID NOT NULL,
    period_id     UUID REFERENCES periods(id),
    cycle_day     INTEGER,
    is_recurring  BOOLEAN NOT NULL DEFAULT TRUE,
    specific_date DATE,
    reason        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── COVER & DELEGATION ────────────────────────────────────────────
CREATE TABLE cover_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    lesson_id           UUID NOT NULL REFERENCES lessons(id),
    original_teacher_id UUID NOT NULL REFERENCES teachers(id),
    cover_teacher_id    UUID NOT NULL REFERENCES teachers(id),
    start_date          DATE NOT NULL,
    end_date            DATE,
    reason              TEXT,
    status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE delegation_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    requesting_teacher_id UUID NOT NULL REFERENCES teachers(id),
    receiving_teacher_id  UUID NOT NULL REFERENCES teachers(id),
    lesson_ids            UUID[] NOT NULL,
    request_type          VARCHAR(50) NOT NULL,  -- SWAP, HANDOVER
    start_date            DATE NOT NULL,
    end_date              DATE,
    reason                TEXT,
    status                VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    moderator_comment     TEXT,
    reviewed_by           UUID REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── TEMPORARY SCHEDULES ───────────────────────────────────────────
CREATE TABLE temporary_schedules (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    base_timetable_id UUID NOT NULL REFERENCES timetables(id),
    name              VARCHAR(100) NOT NULL,
    start_date        DATE NOT NULL,
    end_date          DATE NOT NULL,
    status            VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by        UUID REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── HOLIDAY CALENDAR ──────────────────────────────────────────────
CREATE TABLE holiday_calendars (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    academic_year_id UUID NOT NULL REFERENCES academic_years(id),
    source_country   VARCHAR(10),
    source_region    VARCHAR(50)
);

CREATE TABLE holiday_dates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    calendar_id UUID NOT NULL REFERENCES holiday_calendars(id),
    date        DATE NOT NULL,
    name        VARCHAR(255) NOT NULL,
    date_type   VARCHAR(50) NOT NULL DEFAULT 'PUBLIC_HOLIDAY',
    is_manual   BOOLEAN NOT NULL DEFAULT FALSE
);

-- ── CONSTRAINT CONFIGURATION ──────────────────────────────────────
CREATE TABLE constraint_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    timetable_id        UUID REFERENCES timetables(id),  -- NULL = institution default
    constraints         JSONB NOT NULL DEFAULT '{}',
    default_sensitivity VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── SOLVER JOB TRACKING ───────────────────────────────────────────
CREATE TABLE solver_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    timetable_id    UUID NOT NULL REFERENCES timetables(id),
    status          VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    sensitivity     VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    mode            VARCHAR(50) NOT NULL DEFAULT 'FULL',
    quality_score   INTEGER,
    score_breakdown JSONB,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── AUDIT LOG ─────────────────────────────────────────────────────
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    actor_id     UUID REFERENCES users(id),
    actor_type   VARCHAR(50) NOT NULL,  -- USER, SYSTEM, AI_AGENT
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    UUID,
    before_state JSONB,
    after_state  JSONB,
    reason       TEXT,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 9.2 Key Indexes

```sql
CREATE INDEX idx_users_tenant              ON users(tenant_id);
CREATE INDEX idx_lessons_timetable         ON lessons(timetable_id);
CREATE INDEX idx_lessons_teacher           ON lessons(teacher_id);
CREATE INDEX idx_lessons_room              ON lessons(room_id);
CREATE INDEX idx_lessons_period_day        ON lessons(period_id, cycle_day);
CREATE INDEX idx_forbidden_slots_entity    ON forbidden_slots(entity_type, entity_id);
CREATE INDEX idx_audit_log_tenant_created  ON audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_solver_jobs_timetable     ON solver_jobs(timetable_id);
CREATE INDEX idx_cover_lesson              ON cover_assignments(lesson_id);
CREATE INDEX idx_delegation_teachers       ON delegation_requests(requesting_teacher_id, receiving_teacher_id);
```

---

## 10. Key Architectural Patterns

**Repository → Service → Controller** — no business logic in controllers, ever.

**DTOs everywhere** — JPA entities are never serialized to API responses. MapStruct mappers convert between domain objects and DTOs at the service boundary.

**`@Audited` AOP** — a custom aspect intercepts all `@Audited`-annotated service methods and writes before/after state to `audit_log`. No manual `auditService.log(...)` calls scattered through service code.

**`EmailService` interface** — `SmtpEmailService` for MVP (MailHog). Swap to `SesEmailService` post-MVP with a single config change.

**`StorageService` interface** — `LocalStorageService` for MVP (filesystem volume). Swap to `S3StorageService` post-MVP.

**`TenantContext` ThreadLocal** — populated by `TenantFilter` from the JWT on every request. All repository queries append `AND tenant_id = :currentTenantId` via a Hibernate `@Filter`.

**Async solver thread pool** — solver runs in a dedicated `ExecutorService` configured in `AsyncConfig`, never on request threads.

**Spring `@Scheduled`** — background jobs for temporary schedule expiry, future-dated timetable publishing, and optional holiday calendar refresh.

---

## 11. Deployment

### 11.1 Docker Compose (Local / MVP)

```yaml
# docker-compose.yml (root of repository)
version: '3.9'

services:

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: schediflow
      POSTGRES_USER: schediflow
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U schediflow"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./schediflow-backend
      dockerfile: docker/Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/schediflow
      SPRING_DATASOURCE_USERNAME: schediflow
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      MAIL_HOST: mailhog
      MAIL_PORT: 1025
      STORAGE_PATH: /app/uploads
    volumes:
      - uploads_data:/app/uploads
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build:
      context: ./schediflow-frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - backend

  mailhog:
    image: mailhog/mailhog
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # Web UI — view all sent emails at http://localhost:8025

volumes:
  postgres_data:
  uploads_data:
```

### 11.2 Backend Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 11.3 Local Development Without Docker

```bash
# Start only infrastructure
docker compose up postgres mailhog

# Run backend
cd schediflow-backend
./mvnw spring-boot:run
# API available at http://localhost:8080
# Swagger UI at http://localhost:8080/api-docs
```

### 11.4 K8s Migration Path (Future)

1. Backend → `Deployment` with `HorizontalPodAutoscaler` on CPU
2. PostgreSQL → managed cloud DB (AWS RDS / GCP Cloud SQL)
3. Secrets → Kubernetes Secrets or HashiCorp Vault
4. Health checks → `/actuator/health` (already provided by Spring Boot Actuator)
5. WebSocket multi-pod → add Redis pub/sub before scaling beyond 1 replica (WebSocket state is currently node-local)
6. File storage → migrate `LocalStorageService` to `S3StorageService`
7. Solver isolation → consider extracting solver to a dedicated `solver-service` pod for resource isolation

---

## 12. Development Epics & Stories — Backend

Stories marked **[BE]** are backend-only. Shared epics list only backend stories here; see the frontend spec for **[FE]** stories.

### EPIC 1 — Foundation & Infrastructure [BE]

| Story | Description | Points |
|---|---|---|
| FOUND-01 | Initialize schediflow-backend: Spring Boot 3, Java 21, Maven, core dependencies | 2 |
| FOUND-03 | docker-compose.yml: postgres + backend + frontend + mailhog + volumes | 3 |
| FOUND-04 | Flyway: V001 migration — tenants + users tables | 2 |
| FOUND-05 | Spring Security: JWT filter, token provider, token validation | 5 |
| FOUND-06 | Multi-tenancy: TenantContext, JPA @Filter, TenantFilter, JWT extraction | 5 |
| FOUND-07 | Springdoc OpenAPI: Swagger UI at /api-docs | 1 |
| FOUND-08 | CORS configuration | 1 |
| FOUND-09 | GlobalExceptionHandler: consistent JSON error shape | 2 |

### EPIC 2 — Authentication & User Management [BE]

| Story | Description | Points |
|---|---|---|
| AUTH-01 | POST /auth/register — institution self-registration | 3 |
| AUTH-02 | POST /auth/login → JWT access + HttpOnly refresh cookie | 3 |
| AUTH-03 | POST /auth/refresh → new access token | 2 |
| AUTH-04 | POST /auth/logout → invalidate refresh token | 1 |
| AUTH-05 | POST /users/invite → token generation + invite email via SmtpEmailService | 5 |
| AUTH-06 | POST /auth/complete-registration → consume token, hash password, save profile | 4 |
| AUTH-07 | GET/PUT /users/me → profile read/update | 3 |
| AUTH-08 | GET /users → paginated list (Admin/Mod) | 2 |
| AUTH-09 | PUT /users/{id}/role | 2 |
| AUTH-10 | DELETE /users/{id} → soft deactivate | 1 |

### EPIC 3 — Institution Configuration [BE]

| Story | Description | Points |
|---|---|---|
| CONFIG-01 | CRUD: Academic Years | 3 |
| CONFIG-02 | CRUD: Terms | 3 |
| CONFIG-03 | CRUD: Bell Schedules + Periods (Flyway migration) | 4 |
| CONFIG-04 | GET/PUT /settings → institution settings JSONB | 3 |
| CONFIG-05 | GET /settings/public → unauthenticated locale/timezone endpoint | 1 |
| CONFIG-09 | Seed: apply Setup Template defaults on first institution creation | 4 |

### EPIC 4 — Holiday & Vacation Calendar [BE]

| Story | Description | Points |
|---|---|---|
| HOL-01 | CRUD: Holiday Calendars per Academic Year (Flyway migration) | 3 |
| HOL-02 | POST /holidays/import — fetch public holiday feed, persist dates | 5 |
| HOL-03 | CRUD: Manual holiday date overrides | 2 |
| HOL-04 | GET /holidays?academicYearId=... | 1 |
| HOL-05 | Solver integration: load holiday dates as globally Forbidden Slots | 3 |
| HOL-07 | Conflict detection: warn if holiday overlaps Published Lesson | 3 |

### EPIC 5 — Resource Management [BE]

| Story | Description | Points |
|---|---|---|
| RES-01 | CRUD: Rooms | 3 |
| RES-02 | CRUD: Subjects | 3 |
| RES-03 | CRUD: School Classes | 3 |
| RES-04 | CRUD: Teachers (linked to user) | 3 |
| RES-05 | POST/DELETE /teachers/{id}/qualifications | 2 |
| RES-06 | GET/PUT /classes/{id}/subject-hours | 3 |
| RES-07 | CRUD: Teaching Groups | 4 |
| RES-08 | CRUD: Option Blocks | 4 |
| RES-09 | POST/DELETE /forbidden-slots | 3 |
| RES-10 | GET /teachers/{id}/availability | 2 |
| RES-11 | CSV bulk import — Rooms, Classes, Teachers, Students | 8 |

### EPIC 6 — Timetable & Scheduling Engine [BE]

| Story | Description | Points |
|---|---|---|
| SCHED-01 | POST /timetables → create timetable for a term | 2 |
| SCHED-02 | GET /timetables/{id}/lessons → full lesson list for grid | 3 |
| SCHED-03 | POST /engine/run → async solver job, returns jobId immediately | 8 |
| SCHED-04 | GET /engine/jobs/{id} → poll status + quality score | 2 |
| SCHED-05 | WebSocket: solver progress events via /topic/solver/{jobId}/progress | 4 |
| SCHED-06 | POST /timetables/{id}/publish | 3 |
| SCHED-07 | PATCH /lessons/{id} → move lesson, validate conflicts | 3 |
| SCHED-08 | POST/DELETE /lessons/{id}/pin | 2 |
| SCHED-09 | POST /lessons/{id}/swap → atomic swap with conflict check | 3 |
| SCHED-10 | ConflictDetectionService: real-time check on lesson move | 5 |
| SCHED-11 | WebSocket: broadcast LESSON_UPDATED to /topic/timetable/{id} | 3 |

### EPIC 7 — Cover, Delegation & Temporary Schedules [BE]

| Story | Description | Points |
|---|---|---|
| COVER-01 | POST /cover → assign cover, validate qualification + availability | 4 |
| COVER-02 | GET /cover/candidates?lessonId=... | 4 |
| COVER-03 | POST /delegation → teacher submits request | 3 |
| COVER-04 | PATCH /delegation/{id} → moderator approve/reject | 3 |
| COVER-05 | CRUD: Temporary Schedules | 5 |
| COVER-06 | @Scheduled job: auto-expire temporary schedules at end_date | 3 |
| COVER-07 | WebSocket: COVER_ASSIGNED, DELEGATION_UPDATE events | 3 |

### EPIC 8 — Notifications [BE]

| Story | Description | Points |
|---|---|---|
| NOTIF-02 | WebSocket: push TIMETABLE_PUBLISHED and other events to tenant topic | 4 |
| NOTIF-03 | SmtpEmailService: send invite, cover, delegation, and timetable published emails | 4 |

### EPIC 9 — Export & Reporting [BE]

| Story | Description | Points |
|---|---|---|
| EXPORT-01 | GET /timetables/{id}/export/pdf — generate PDF via chosen library | 5 |
| EXPORT-02 | GET /timetables/{id}/export/csv | 3 |
| EXPORT-03 | GET /timetables/{id}/export/ical?userId=... → .ics file | 4 |
| EXPORT-05 | Teacher utilization report endpoint | 4 |
| EXPORT-06 | Room utilization report endpoint | 3 |
| EXPORT-07 | Subject coverage report endpoint | 3 |
| EXPORT-08 | Audit log endpoint (paginated, filterable) | 3 |

### EPIC 10 — Setup Templates [BE]

| Story | Description | Points |
|---|---|---|
| TMPL-01 | Template data model + Flyway migration | 3 |
| TMPL-02 | Seed: built-in templates for 5 institution types | 5 |
| TMPL-03 | POST /institutions/apply-template | 4 |
| TMPL-04 | POST /templates → save config as custom template | 3 |

---

## 13. Additional Recommendations

- **`StorageService` / `EmailService` interfaces** — define now so MVP → production swap is a config change, not a rewrite
- **`@Audited` AOP** — annotate service methods once; audit log populates automatically without scattered `auditService.log()` calls
- **`@Scheduled` jobs** — temp schedule expiry, future-dated publish, optional annual holiday refresh
- **Rate limiting** — auth endpoints: 10 req/min/IP; engine endpoint: 3 concurrent jobs/tenant
- **Structured logging** — Logback JSON output + correlation ID filter from day one; saves hours of debugging in production
- **Spring Boot Actuator** — `/actuator/health` is the K8s liveness/readiness probe; enable from day one
- **Solver timeout config** — `timefold.solver.termination.spent-limit` per tier (30s / 2min / 10min)
- **Testcontainers** — spin up a real PostgreSQL for integration tests; do not use H2 in-memory

---

## 14. Open Technical Decisions

| # | Decision | Options | Recommendation | Urgency |
|---|---|---|---|---|
| TD-01 | Solver isolation | In-process JVM vs. separate microservice | In-process for MVP; extract to own pod when moving to K8s | Low |
| TD-02 | WebSocket scaling | Node-local vs. Redis pub/sub | Node-local for MVP; Redis before scaling beyond 1 backend replica | Low |
| TD-03 | Multi-tenancy depth | Row-level (current) vs. schema-per-tenant | Row-level for MVP; revisit only for Enterprise full-isolation requirement | Low |
| TD-04 | File storage backend | Local volume vs. MinIO vs. S3 | Local volume (MVP) → MinIO self-hosted → S3 cloud | Medium |
| TD-05 | PDF export library | iText vs. Flying Saucer vs. headless Chrome | Flying Saucer for simple layouts; headless Chrome if pixel-perfect is required | Medium |
| TD-06 | Holiday data source | Government APIs per country vs. Calendarific API | Calendarific free tier for MVP | Medium |
| TD-07 | Rate limiting implementation | Spring in-memory vs. Redis-backed Bucket4j | In-memory for MVP; Redis-backed before K8s | Low |

---

*This document covers schediflow-backend only. See the frontend specification for schediflow-frontend.*
