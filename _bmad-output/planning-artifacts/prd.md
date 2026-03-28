---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete']
inputDocuments:
  - '_bmad-output/project-context.md'
  - '_bmad-output/planning-artifacts/mrd/SchediFlow_MRD_v1.0.md'
  - '_bmad-output/planning-artifacts/architecture/schediflow-backend_Architecture_v1.2.md'
workflowType: 'prd'
briefCount: 0
researchCount: 0
brainstormingCount: 0
projectDocsCount: 3
classification:
  projectType: saas_b2b
  domain: edtech
  complexity: medium-high
  projectContext: brownfield
---

# Product Requirements Document — SchediFlow Backend

**Author:** Arthur
**Date:** 2026-03-29

## Executive Summary

SchediFlow is a multi-tenant SaaS platform that eliminates the weeks-long manual effort of school timetable creation. Timetabling moderators — typically teachers or administrators with no scheduling expertise — configure their institution once and use a guided, iterative workflow to generate, review, and refine conflict-free timetables. The system handles constraint satisfaction automatically while keeping the moderator in control at every stage: drafts are preserved, checkpoints allow safe iteration, and manual overrides are always available.

The platform serves the entire school community. Teachers receive schedules that respect their availability preferences and maximize free periods where constraints allow. Students benefit from difficulty-aware subject sequencing that optimizes cognitive load across the day. Admins gain operational visibility and reduce institutional dependency on a single scheduling expert.

### What Makes This Special

SchediFlow's core insight is that timetabling is an **iterative process, not a one-shot generation event**. Competitors produce a schedule and stop. SchediFlow treats scheduling as a workflow: draft → checkpoint → refine → publish, with the ability to return to any saved state. This, combined with difficulty-aware scheduling (unique in the market) and structured absence/cover workflows as first-class features, positions SchediFlow as the only tool that serves moderators, teachers, and students equally.

The **generator sensitivity dial** — a progressive constraint-relaxation mechanism — ensures the engine never simply "fails". When a fully constrained schedule is not possible, the system guides the moderator through targeted trade-offs rather than returning an error.

## Project Classification

| Field | Value |
|---|---|
| **Project Type** | SaaS B2B — multi-tenant platform with REST API backend |
| **Domain** | EdTech — K-12 and post-secondary school timetabling |
| **Complexity** | Medium-High (constraint satisfaction engine, multi-tenancy, community-wide access model) |
| **Project Context** | Brownfield — Epics 1 (Foundation) and 2 (Auth) implemented; Epic 3 (Institution Configuration) in progress; Epics 4–10 planned |

## Success Criteria

### User Success

- **Time to first published timetable:** < 2 hours median from institution setup to first published schedule
- **Generator acceptance rate:** > 85% of generated schedules accepted without a full manual redo
- **Moderator NPS:** > 45
- **Teacher NPS:** > 35
- **30-day activation rate:** > 60% of trial institutions publish their first timetable within 30 days
- **Iterative confidence:** Moderators can draft, checkpoint, edit, and roll back without data loss

### Business Success

| Horizon | Metric | Target |
|---|---|---|
| Year 1 | Paying institutions | 100+ |
| Year 1 | ARR | $100,000+ |
| Year 1 | Annual churn rate | < 15% |
| Year 1 | Gross margin | > 75% |
| Year 1 | CAC | < $500 |
| Year 3 | Paying institutions | 1,000+ |
| Year 3 | ARR | $1.2M+ |
| Year 3 | Annual churn rate | < 10% |
| Year 3 | Net Revenue Retention | > 110% |
| Year 3 | Active markets | 5+ countries |

### Technical Success

- All REST API endpoints < 200ms at p95 under normal load
- Scheduling engine generates a valid timetable for a 60-teacher, 40-class institution in < 60 seconds
- 99.9% monthly uptime (< 44 minutes downtime/month)
- Zero cross-tenant data leakage — enforced at the Hibernate filter layer
- All service logic covered by unit tests; all API endpoints covered by integration tests against H2

### Measurable Outcomes

- A moderator who previously spent 2–4 weeks on manual scheduling completes the same task in < 1 week using SchediFlow
- Teacher free-day preferences honored in > 70% of cases where constraints allow
- Difficulty-sequenced student schedules: hard subjects not clustered at day-end in > 80% of generated timetables

## Product Scope & Roadmap

### MVP Strategy

**Approach:** Experience MVP — deliver the complete timetabling workflow end-to-end before adding operational or platform features.

**Target:** One pilot school in Armenia. Success = moderator configures institution, generates a valid timetable, refines it, and publishes it — replacing the manual process entirely.

**Resource:** Solo developer.

### Phase 1 — MVP

| Epic | Scope |
|---|---|
| **Epic 1 — Foundation & Infrastructure** | Spring Boot, Flyway, multi-tenant architecture, security baseline |
| **Epic 2 — Auth & User Management** | Institution registration, login, JWT auth, role-based access — extended to include MODERATOR role |
| **Epic 3 — Institution Configuration** | Terms, bell schedules, cycles, configurable terminology, difficulty scales |
| **Epic 5 — Resource Management** | Teachers, rooms, subjects, teaching groups |
| **Epic 6 — Scheduling Engine** | Timefold Solver integration, constraint compilation, sensitivity dial, checkpoint/rollback, publish |

### Phase 2 — Growth (operational daily use)

- **Epic 4 — Holiday & Vacation Calendar:** Public holiday API, term date management, automatic schedule exclusions
- **Epic 7 — Cover, Delegation & Temporary Schedules:** Absence workflows, lesson swaps, exceptional-week schedules with rollback
- **Epic 8 — Notifications:** Teacher alerts for schedule changes and cover assignments

### Phase 3 — Expansion (platform & reach)

- **Epic 9 — Export & Reporting:** PDF/iCal export, workload and room utilization reports
- **Epic 10 — Setup Templates:** Pre-built configuration templates; template marketplace foundation
- Subscription tier enforcement (Free Trial → Starter → Professional → Enterprise)
- AI Scheduling Assistant — natural language constraint input
- Student & Parent roles

## User Journeys

### Journey 1: The Moderator — First Timetable (Success Path)

**Meet Sarah.** She's a deputy principal at a 60-teacher secondary school. Every February she inherits "the spreadsheet" — a color-coded Excel file left by her predecessor, incomprehensible to anyone else. Last year it took her three weeks and she still had two teacher conflicts on the first day of term.

This year the school is trying SchediFlow.

Sarah registers the institution and works through the configuration wizard: two terms, a five-day cycle, eight periods per day, bell schedule loaded. She imports teachers and assigns their subjects and availability — three teachers marked unavailable on Fridays. Science labs marked as subject-restricted. It takes her an afternoon.

She hits **Generate**. The engine runs for 28 seconds. A valid draft appears.

She spots a double-booking on Wednesday period 3. She clicks in, manually reassigns the slot, saves a checkpoint. She refines over two days, saving checkpoints as she goes. When she accidentally makes things worse, she rolls back in one click.

On day three she publishes. Teachers are notified automatically. No phone calls, no printed sheets.

**Capabilities revealed:** Institution setup, resource import, constraint configuration, schedule generation, conflict visualization, manual override, checkpoint/rollback, publish.

---

### Journey 2: The Moderator — Constraint Conflict (Edge Case)

**Meet David.** He runs timetabling at a language school with rolling 6-week terms and tight teacher availability windows. His school uses "Session" instead of "Period".

He hits Generate. The engine returns: *"No valid schedule found under current constraints."*

In SchediFlow, the **sensitivity dial** appears. It shows David which soft constraints are hardest to satisfy — three teachers' Friday preferences are creating an impossible combination. At sensitivity level 2, the engine finds a valid schedule with two violations, both highlighted.

David reviews them, decides they're acceptable, and publishes with a note to the affected teachers.

**Capabilities revealed:** Sensitivity dial, constraint violation report, progressive relaxation, custom terminology support.

---

### Journey 3: The Teacher — Living with the Timetable

**Meet Marcus.** He teaches maths and physics. He's never seen SchediFlow — his principal signed up. He gets an email: "Your timetable for Term 2 is ready."

He logs in, sees his personal weekly view, notices he has Fridays free — exactly what he'd requested. He exports it to Google Calendar. Two weeks in, a colleague is sick. SchediFlow sends Marcus a notification: he's assigned cover for Period 4 on Thursday. He acknowledges from his phone. No WhatsApp chaos.

**Capabilities revealed:** Personal timetable view, calendar export (iCal), push/email notifications, cover acknowledgment (Growth tier).

---

### Journey 4: The Admin — Visibility and Oversight

**Meet Dr. Chen.** She runs a 45-teacher primary school. She logs into SchediFlow with her ADMIN role and sees teacher workload distribution — one teacher has 28 periods per week while the average is 22. She raises it with the moderator, who adjusts constraints and regenerates.

**Capabilities revealed:** Admin view, workload summary, role-based view differentiation.

---

### Journey 5: The Student — Knowing Where to Be *(Post-MVP)*

**Meet Priya.** She's Year 10. She logs in, sees her personal timetable with lighter subjects (Art, PE) scheduled later in the day. She doesn't know the algorithm did this — she just notices school feels less exhausting. She exports the schedule to her iPhone calendar.

**Capabilities revealed:** Student timetable view, difficulty-aware sequencing, calendar export. *(Student role is Post-MVP.)*

---

### Journey Requirements Summary

| Capability Area | Revealed By | Phase |
|---|---|---|
| Institution setup & configuration | Journey 1 | MVP |
| Resource import (teachers, rooms, subjects) | Journey 1 | MVP |
| Schedule generation with conflict detection | Journeys 1, 2 | MVP |
| Manual override & checkpoint/rollback | Journeys 1, 2 | MVP |
| Generator sensitivity dial & constraint reporting | Journey 2 | MVP |
| Custom terminology support | Journey 2 | MVP |
| Personal timetable views (role-based) | Journeys 3, 4 | MVP |
| Push/email notifications | Journey 3 | Growth |
| Cover assignment workflows | Journey 3 | Growth |
| Admin workload summary | Journey 4 | MVP |
| Difficulty-aware subject sequencing | Journey 5 | MVP (engine) |
| Calendar export (iCal) | Journeys 3, 5 | Vision |

## Domain-Specific Requirements

### Scheduling Engine Constraints

- **Constraint classification:** All scheduling rules are categorized as hard (never violate) or soft (relax via sensitivity dial). No mixed-category constraints.
- **Reproducibility:** A saved configuration + sensitivity level must always produce a deterministic, inspectable result. The engine must not operate as a black box.
- **Partial regeneration scope:** Re-generating for a subset of resources must not silently alter unrelated schedule slots. Scope of change is always explicit.
- **Solver timeout:** Timefold respects a configurable time cap. On timeout, the best partial solution found is returned with a violations report — never a silent failure.
- **Checkpoint safety:** Checkpoint/rollback is implemented before the scheduling engine is exposed in production. Moderators must not lose manually refined schedules due to accidental regeneration.

### Compliance & Regulatory

No data privacy regulations apply to the initial Armenia deployment (GDPR, FERPA, COPPA deferred). Revisit before expanding to UK, EU, US, or Australian markets.

## Innovation & Novel Patterns

### Detected Innovation Areas

**1. Generator Sensitivity Dial**
No competing timetabling tool exposes the constraint satisfaction process to the user in a controllable way. The sensitivity dial replaces an opaque failure with a guided, stepwise negotiation — a novel UX pattern for constraint-satisfaction software.

**2. Difficulty-Aware Timetabling**
Subject difficulty ratings are used as a soft constraint objective — distributing cognitively demanding subjects earlier in the day, preventing difficulty clustering at day-end. First-in-class feature for school timetabling software; requires no additional moderator input beyond initial difficulty configuration.

**3. Checkpoint/Rollback as a Scheduling Primitive**
SchediFlow treats timetabling as an iterative workflow: draft → refine → checkpoint → continue → restore. This changes the risk profile of manual edits and makes the tool approachable for non-expert schedulers.

### Validation Signals

| Innovation | Signal |
|---|---|
| Sensitivity dial | Generator acceptance rate > 85% |
| Difficulty-aware scheduling | Difficulty sequencing honored in > 80% of generated schedules |
| Checkpoint/rollback | < 5% of moderator sessions result in complete schedule abandonment |

### Innovation Risks

- **Sensitivity dial UX:** Over-relaxation produces poor schedules. Mitigation: label which constraints relax at each level; preview trade-offs before committing.
- **Difficulty rating subjectivity:** Inconsistent ratings degrade the optimization signal. Mitigation: default scale with pre-assigned ratings for common subjects; per-institution override.
- **Checkpoint storage growth:** Mitigation: configurable retention limit (e.g., last 10 checkpoints per timetable); auto-expiry after one term.

## SaaS B2B Specific Requirements

### Tenant Model

- **Isolation:** Shared PostgreSQL schema, row-level isolation via `tenant_id` on all tenant-scoped entities
- **Enforcement:** Hibernate `@Filter(name = "tenantFilter")` activated by `TenantFilterAspect` on every request — never bypassed in service code
- **Tenant identity:** Extracted from JWT (`tenantId` claim) — never accepted from client request body or query params
- **Lifecycle:** Institution self-registers → ADMIN account created → institution configured → users invited

### RBAC Matrix

| Permission | ADMIN | MODERATOR | TEACHER |
|---|---|---|---|
| Institution configuration (terms, bell schedule, cycles, domain values) | ✅ | ❌ | ❌ |
| Resource management (rooms, subjects, teaching groups) | ✅ | ❌ | ❌ |
| User management (invite, deactivate, assign roles) | ✅ | ❌ | ❌ |
| Generate / run scheduling engine | ✅ | ✅ | ❌ |
| Adjust schedule slots manually | ✅ | ✅ | ❌ |
| Create / restore checkpoints | ✅ | ✅ | ❌ |
| Publish timetable | ✅ | ✅ | ❌ |
| View all teacher schedules | ✅ | ✅ | ❌ |
| View personal schedule | ✅ | ✅ | ✅ |
| Declare availability preferences | ✅ | ✅ | ✅ |
| TEACHER may hold MODERATOR as an additional role | — | — | ✅ |

### API Conventions

- All endpoints versioned under `/api/v1/` — breaking changes require a new version prefix
- List endpoints return pagination envelope: `{ content, page, size, totalElements, totalPages }`
- Error responses use standard envelope: `{ status, code, message, details, timestamp }`
- Action endpoints (non-CRUD) use `POST` with a verb path segment (e.g. `/generate`, `/publish`, `/checkpoint`, `/restore`)
- JWT payload: `sub` (userId), `tenantId`, `role`, `email` — all access control decisions server-side via `@PreAuthorize`
- Dual-role users (TEACHER + MODERATOR): effective role in JWT; role elevation via dedicated endpoint if needed

### Implementation Notes

- MODERATOR is a new role not yet in the codebase (current: ADMIN/TEACHER/STUDENT). A Flyway migration is required to add it to the role enum and update existing role checks.
- STUDENT and PARENT roles are reserved in the role enum now to prevent a future breaking migration, even though they carry no permissions in MVP.
- Subscription tier enforcement is deferred post-MVP. All institutions have full feature access at launch.

## Functional Requirements

### Institution Management

- **FR1:** An institution can self-register and create an initial ADMIN account
- **FR2:** ADMIN can configure institution-level settings including name and custom terminology (e.g., rename "Period" to "Session")
- **FR3:** ADMIN can define subject difficulty scale labels and values used by the scheduling engine
- **FR4:** ADMIN can create and manage academic years as containers for terms
- **FR5:** ADMIN can create and manage academic terms with defined start and end dates within an academic year
- **FR6:** ADMIN can define bell schedules specifying the number of periods per day and the start/end time of each period
- **FR7:** ADMIN can define scheduling cycles (e.g., 5-day week, 10-day fortnight) that determine how the bell schedule repeats

### User & Access Management

- **FR8:** ADMIN can invite users to the institution by email
- **FR9:** ADMIN can assign roles (ADMIN, MODERATOR, TEACHER) to institution users
- **FR10:** ADMIN can grant a TEACHER user the additional MODERATOR role (dual role)
- **FR11:** ADMIN can deactivate institution users
- **FR12:** Users can authenticate via email and password
- **FR13:** The system issues a JWT containing the user's identity, tenant, and role — all access control decisions are made server-side

### Resource Management

- **FR14:** ADMIN can create, update, and deactivate teacher profiles with associated subjects and qualifications
- **FR15:** ADMIN can create, update, and deactivate rooms with type classification and capacity
- **FR16:** ADMIN can create, update, and deactivate subjects with assigned difficulty ratings
- **FR17:** ADMIN can create teaching groups (classes/sets), assign subjects to them, and assign teachers to deliver those subjects

### Schedule Generation & Engine

- **FR18:** MODERATOR can initiate a schedule generation run for a selected academic term
- **FR19:** The system generates a timetable satisfying all hard constraints (no double-booking, no room over-capacity, no forbidden slots) and optimizing soft constraints (teacher preferences, difficulty sequencing)
- **FR20:** MODERATOR can view a violations report listing which soft constraints were not honored
- **FR21:** MODERATOR can adjust a sensitivity dial to progressively relax soft constraints when no fully valid schedule can be found
- **FR22:** When no fully valid schedule exists at the current sensitivity level, the system returns the best partial solution found with a violations report — never a silent failure
- **FR23:** The system enforces a configurable generation time cap and returns the best solution found within that cap
- **FR24:** MODERATOR can trigger a targeted re-generation scoped to a subset of resources without affecting unrelated schedule slots

### Schedule Editing & Checkpoints

- **FR25:** MODERATOR can manually reassign any lesson slot to a different period, room, or teacher
- **FR26:** MODERATOR can save a named checkpoint of the current schedule state at any point
- **FR27:** MODERATOR can restore the schedule to any previously saved checkpoint
- **FR28:** MODERATOR can publish a schedule, making it visible to all institution users with the appropriate role
- **FR29:** The system tracks each schedule version's status (draft or published)

### Timetable Viewing

- **FR30:** ADMIN and MODERATOR can view the complete institution timetable for a term, filterable by teacher, room, or class
- **FR31:** TEACHER can view their personal timetable for the current and upcoming term
- **FR32:** ADMIN and MODERATOR can view a teacher workload summary (periods assigned per teacher per week)
- **FR33:** All timetable views are scoped to the user's institution — no cross-tenant data is accessible

### Availability & Preferences

- **FR34:** TEACHER can declare preferred availability (preferred free days, preferred lighter days)
- **FR35:** TEACHER can mark specific time slots as forbidden (hard unavailability)
- **FR36:** The scheduling engine treats availability preferences as soft constraints and forbidden slots as hard constraints — forbidden slots are never violated regardless of sensitivity level

## Non-Functional Requirements

### Performance

Performance targets are also documented in Technical Success Criteria. These are the binding engineering constraints:

- API endpoints: < 200ms response at p95 under normal single-institution load
- Scheduling engine: valid timetable for 60 teachers / 40 classes in < 60 seconds
- Solver timeout: configurable cap enforced; best partial solution returned on timeout — never hangs
- All list endpoints paginated — no unbounded result sets

### Security

- Passwords hashed with BCrypt cost factor 12 — never stored or logged in plaintext
- Access tokens (JWT) expire after 15 minutes; refresh tokens expire after 7 days, stored in HttpOnly cookie
- All production traffic served over HTTPS — no HTTP fallback
- `tenant_id` never accepted from client input — always derived from authenticated JWT
- Role enforcement server-side via `@PreAuthorize` — client-supplied role claims are validated, not trusted

### Reliability

- 99.9% monthly uptime target (< 44 minutes downtime/month)
- All write operations (schedule edits, checkpoints) are transactional — no partial state on failure
- Checkpoint restore operations always succeed if the checkpoint record exists

### Data Integrity

- Flyway owns all schema changes — Hibernate `ddl-auto` is permanently set to `none`
- Existing Flyway migration files are never modified — all changes are additive new migrations
- All tenant-scoped entities carry the Hibernate tenant filter — absence is a deployment blocker
- Entity relationships enforced via database foreign key constraints, not application-layer validation alone
