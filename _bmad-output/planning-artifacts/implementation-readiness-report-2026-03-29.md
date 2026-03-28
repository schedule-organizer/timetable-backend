---
stepsCompleted: ['step-01-document-discovery', 'step-02-prd-analysis', 'step-03-epic-coverage', 'step-04-ux-alignment', 'step-05-epic-quality', 'step-06-final-assessment']
status: complete
documentsInventoried:
  prd: '_bmad-output/planning-artifacts/prd.md'
  architecture: '_bmad-output/planning-artifacts/architecture/schediflow-backend_Architecture_v1.2.md'
  epics:
    - '_bmad-output/planning-artifacts/epics/epic-1-foundation-infrastructure.md'
    - '_bmad-output/planning-artifacts/epics/epic-2-authentication-user-management.md'
    - '_bmad-output/planning-artifacts/epics/epic-3-institution-configuration.md'
    - '_bmad-output/planning-artifacts/epics/epic-4-holiday-vacation-calendar.md'
    - '_bmad-output/planning-artifacts/epics/epic-5-resource-management.md'
    - '_bmad-output/planning-artifacts/epics/epic-6-timetable-scheduling-engine.md'
    - '_bmad-output/planning-artifacts/epics/epic-7-cover-delegation-temporary-schedules.md'
    - '_bmad-output/planning-artifacts/epics/epic-8-notifications.md'
    - '_bmad-output/planning-artifacts/epics/epic-9-export-reporting.md'
    - '_bmad-output/planning-artifacts/epics/epic-10-setup-templates.md'
  ux: null
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-29
**Project:** timetable-backend (SchediFlow)

## PRD Analysis

### Functional Requirements

**Institution Management**
- FR1: An institution can self-register and create an initial ADMIN account
- FR2: ADMIN can configure institution-level settings including name and custom terminology
- FR3: ADMIN can define subject difficulty scale labels and values used by the scheduling engine
- FR4: ADMIN can create and manage academic years as containers for terms
- FR5: ADMIN can create and manage academic terms with defined start and end dates within an academic year
- FR6: ADMIN can define bell schedules specifying the number of periods per day and the start/end time of each period
- FR7: ADMIN can define scheduling cycles (e.g., 5-day week, 10-day fortnight)

**User & Access Management**
- FR8: ADMIN can invite users to the institution by email
- FR9: ADMIN can assign roles (ADMIN, MODERATOR, TEACHER) to institution users
- FR10: ADMIN can grant a TEACHER user the additional MODERATOR role (dual role)
- FR11: ADMIN can deactivate institution users
- FR12: Users can authenticate via email and password
- FR13: The system issues a JWT containing user identity, tenant, and role — all access control decisions server-side

**Resource Management**
- FR14: ADMIN can create, update, and deactivate teacher profiles with associated subjects and qualifications
- FR15: ADMIN can create, update, and deactivate rooms with type classification and capacity
- FR16: ADMIN can create, update, and deactivate subjects with assigned difficulty ratings
- FR17: ADMIN can create teaching groups (classes/sets), assign subjects and teachers

**Schedule Generation & Engine**
- FR18: MODERATOR can initiate a schedule generation run for a selected academic term
- FR19: The system generates a timetable satisfying all hard constraints and optimizing soft constraints
- FR20: MODERATOR can view a violations report listing which soft constraints were not honored
- FR21: MODERATOR can adjust a sensitivity dial to progressively relax soft constraints
- FR22: When no fully valid schedule exists, system returns best partial solution with violations report
- FR23: The system enforces a configurable generation time cap
- FR24: MODERATOR can trigger targeted re-generation scoped to a subset of resources

**Schedule Editing & Checkpoints**
- FR25: MODERATOR can manually reassign any lesson slot to a different period, room, or teacher
- FR26: MODERATOR can save a named checkpoint of the current schedule state
- FR27: MODERATOR can restore the schedule to any previously saved checkpoint
- FR28: MODERATOR can publish a schedule, making it visible to all institution users
- FR29: The system tracks each schedule version's status (draft or published)

**Timetable Viewing**
- FR30: ADMIN and MODERATOR can view the complete institution timetable for a term, filterable by teacher, room, or class
- FR31: TEACHER can view their personal timetable for the current and upcoming term
- FR32: ADMIN and MODERATOR can view a teacher workload summary (periods per teacher per week)
- FR33: All timetable views are scoped to the user's institution — no cross-tenant data accessible

**Availability & Preferences**
- FR34: TEACHER can declare preferred availability (preferred free days, preferred lighter days)
- FR35: TEACHER can mark specific time slots as forbidden (hard unavailability)
- FR36: The scheduling engine treats availability preferences as soft constraints and forbidden slots as hard constraints

**Total FRs: 36**

### Non-Functional Requirements

**Performance**
- NFR1: All REST API endpoints < 200ms at p95 under normal single-institution load
- NFR2: Scheduling engine generates valid timetable for 60 teachers / 40 classes in < 60 seconds
- NFR3: Solver timeout enforced — best partial solution returned on timeout, never hangs
- NFR4: All list endpoints paginated — no unbounded result sets

**Security**
- NFR5: Passwords hashed with BCrypt cost factor 12 — never stored or logged in plaintext
- NFR6: Access tokens (JWT) expire after 15 minutes; refresh tokens expire after 7 days in HttpOnly cookie
- NFR7: All production traffic over HTTPS — no HTTP fallback
- NFR8: `tenant_id` never accepted from client input — always derived from JWT
- NFR9: Role enforcement server-side via `@PreAuthorize`

**Reliability**
- NFR10: 99.9% monthly uptime target
- NFR11: All write operations transactional — no partial state on failure
- NFR12: Checkpoint restore operations always succeed if the checkpoint record exists

**Data Integrity**
- NFR13: Flyway owns all schema changes — Hibernate `ddl-auto` permanently `none`
- NFR14: Existing Flyway migrations never modified — all changes are additive
- NFR15: All tenant-scoped entities carry Hibernate tenant filter — absence is a deployment blocker
- NFR16: Entity relationships enforced via database FK constraints

**Total NFRs: 16**

### Additional Requirements

**Domain Constraints (from Domain-Specific Requirements section):**
- All scheduling rules categorized as hard or soft — no mixed-category constraints
- Saved configuration + sensitivity level must produce a deterministic, reproducible schedule
- Partial regeneration must not silently alter unrelated schedule slots
- Solver respects configurable timeout — returns best partial on timeout

**RBAC Matrix (from SaaS B2B Requirements):**
- ADMIN: full configuration + resource + user management authority
- MODERATOR: schedule generation, editing, checkpoint, publish
- TEACHER: personal schedule view + availability declarations
- TEACHER can hold MODERATOR as dual role
- MODERATOR role is new — not yet in codebase; requires Flyway migration

**Scope Decisions:**
- MVP: Epics 1, 2 (+ MODERATOR role extension), 3, 5, 6
- Growth: Epics 4, 7, 8
- Vision: Epics 9, 10, AI Assistant, Student/Parent roles
- Subscription tiers deferred post-MVP

### PRD Completeness Assessment

The PRD is comprehensive and well-structured. All 36 FRs are testable capability statements. NFRs are specific and measurable. Scope is clearly defined across 3 phases. Key observation: **the MODERATOR role (FR9, FR10) is newly defined in this PRD and is not yet present in the codebase** — this will require a dedicated story in Epic 2.

## Epic Coverage Validation

### Coverage Matrix

| FR | Requirement (summary) | Epic / Story | Status |
|---|---|---|---|
| FR1 | Institution self-registration | AUTH-01 | ✅ Covered |
| FR2 | ADMIN configures name & terminology | CONFIG-04 | ✅ Covered |
| FR3 | ADMIN defines difficulty scale labels | CONFIG-04 (settings JSONB) + RES-02 (subject difficulty field) | ⚠️ Partial — no dedicated difficulty-scale config story |
| FR4 | ADMIN manages academic years | CONFIG-01 | ✅ Covered |
| FR5 | ADMIN manages academic terms | CONFIG-02 | ✅ Covered |
| FR6 | ADMIN defines bell schedules | CONFIG-03 | ✅ Covered |
| FR7 | ADMIN defines scheduling cycles | CONFIG-09 seeds default; no CRUD story | ❌ Gap — no story for cycle management |
| FR8 | ADMIN invites users by email | AUTH-05 | ✅ Covered |
| FR9 | ADMIN assigns roles | AUTH-09 | ✅ Covered |
| FR10 | ADMIN grants TEACHER the MODERATOR role | AUTH-09 (role change) — but MODERATOR role not yet in codebase | ❌ Critical Gap — no story creates MODERATOR role |
| FR11 | ADMIN deactivates users | AUTH-10 | ✅ Covered |
| FR12 | Email/password authentication | AUTH-02 | ✅ Covered |
| FR13 | JWT issuance with tenant/role | AUTH-01, AUTH-02, AUTH-03, FOUND-05 | ✅ Covered |
| FR14 | ADMIN manages teacher profiles | RES-04 | ✅ Covered |
| FR15 | ADMIN manages rooms | RES-01 | ✅ Covered |
| FR16 | ADMIN manages subjects with difficulty | RES-02 | ✅ Covered |
| FR17 | ADMIN creates teaching groups | RES-07 | ✅ Covered |
| FR18 | MODERATOR initiates generation run | SCHED-03 | ✅ Covered |
| FR19 | Engine satisfies hard/soft constraints | SCHED-03, SCHED-11 | ✅ Covered |
| FR20 | MODERATOR views violations report | SCHED-04 (score breakdown in job status) | ⚠️ Partial — violations embedded in job, no dedicated report view |
| FR21 | MODERATOR adjusts sensitivity dial | SCHED-03 (sensitivity parameter) | ✅ Covered |
| FR22 | Best partial solution returned on failure | SCHED-03, SCHED-04 | ✅ Covered |
| FR23 | Configurable generation time cap | SCHED-03 (tier-based timeout in notes) | ✅ Covered |
| FR24 | Targeted partial re-generation | No story covers scoped re-generation | ❌ Gap — no story for partial regeneration |
| FR25 | MODERATOR manually reassigns slots | SCHED-08 (move), SCHED-10 (swap) | ✅ Covered |
| FR26 | MODERATOR saves named checkpoint | No story covers checkpoints/snapshots | ❌ Critical Gap — checkpoints not in any epic |
| FR27 | MODERATOR restores to checkpoint | No story covers checkpoint restore | ❌ Critical Gap — checkpoint restore not in any epic |
| FR28 | MODERATOR publishes schedule | SCHED-07 | ✅ Covered |
| FR29 | System tracks draft/published status | SCHED-01 (DRAFT → PUBLISHED → ARCHIVED lifecycle) | ✅ Covered |
| FR30 | Complete institution timetable view (filterable) | SCHED-02 | ✅ Covered |
| FR31 | TEACHER personal timetable view | No dedicated story for teacher-scoped view | ❌ Gap — no teacher-facing endpoint in Epic 6 |
| FR32 | Teacher workload summary | EXPORT-05 | ⚠️ Scope conflict — in Epic 9 (Vision in PRD, MVP in epic file) |
| FR33 | Tenant isolation for all views | FOUND-06 (Hibernate tenant filter) | ✅ Covered |
| FR34 | TEACHER declares soft availability preferences | RES-09 (forbidden slots — hard only), RES-10 (reads preferences) | ❌ Gap — no story for creating soft preferences |
| FR35 | TEACHER marks forbidden slots | RES-09 | ✅ Covered |
| FR36 | Engine treats preferences as soft, forbidden as hard | SCHED-03 (solver constraints), FOUND-06 | ✅ Covered |

### Missing Requirements

#### Critical Gaps (MVP blockers)

**FR10 / FR26 / FR27 — MODERATOR role + Checkpoint system**

The MODERATOR role (FR9/FR10) does not exist in the codebase or in any planned story. AUTH-09 handles role assignment but the MODERATOR role value is not defined. Additionally, the checkpoint/rollback feature (FR26, FR27) — one of SchediFlow's three core innovations — has **no story in any epic**. This is the most significant gap in the entire plan.

- **Recommendation:** Add story `AUTH-11` to Epic 2: *Add MODERATOR role to role enum, Flyway migration, update RBAC enforcement*
- **Recommendation:** Add stories `SCHED-13` and `SCHED-14` to Epic 6: *Timetable checkpoint save (named snapshots)* and *Checkpoint restore to prior state*

**FR31 — Teacher personal timetable view**

No story provides a teacher-scoped view of their personal schedule. SCHED-02 returns the full institution timetable (ADMIN/MODERATOR). Teachers cannot see their own schedule without a dedicated endpoint.

- **Recommendation:** Add story `SCHED-15` to Epic 6: *`GET /api/v1/timetables/{id}/my-lessons` — teacher-scoped personal view filtered by authenticated user*

#### Standard Gaps

**FR7 — Scheduling cycle CRUD**

CONFIG-09 seeds a default 5-day cycle but there is no story for ADMIN to define, view, or change the cycle structure (number of days in the cycle, cycle name). This is a prerequisite for the scheduling engine.

- **Recommendation:** Add story `CONFIG-06` to Epic 3: *CRUD `/api/v1/cycles` — define cycle length and day labels*

**FR24 — Targeted partial re-generation**

SCHED-03 only supports full schedule regeneration. The PRD requires that regeneration can be scoped to a subset of resources without disturbing other slots.

- **Recommendation:** Add story `SCHED-16` to Epic 6: *`POST /api/v1/engine/run` partial mode — accept resource filter (teacher IDs, room IDs) to scope solver to subset*

**FR34 — Soft availability preferences (creation)**

RES-09 handles hard forbidden slots. RES-10 reads back all preferences. But there is no story for TEACHER to submit soft preferences (preferred free days, preferred lighter periods). The data model and API to *create* soft preferences is missing.

- **Recommendation:** Extend `RES-09` or add `RES-12`: *`POST /api/v1/teachers/{id}/preferences` — submit soft availability preferences (preferred free days, day-weight preferences)*

#### Partial Coverage (Needs Clarification)

**FR3 — Difficulty scale labels**

Subject difficulty is a field on the Subject entity (RES-02), but the configurable scale labels (e.g., "Easy=1, Medium=2, Hard=3") are only mentioned in CONFIG-04's settings JSONB. No dedicated story addresses the difficulty scale configuration UI/API. Low priority but should be confirmed as in scope for CONFIG-04.

**FR20 — Violations report**

The violations report is embedded in job status (SCHED-04). The PRD describes it as a first-class feature the MODERATOR reviews. Whether a dedicated violations view endpoint is needed or the embedded score breakdown suffices should be confirmed.

#### Scope Conflict

**Epics 4, 7, 8, 9 — MVP vs. Growth/Vision**

Epic files for Epics 4, 7, 8, and 9 are marked `MVP: Yes`. The PRD classifies:
- Epic 4 (Holiday Calendar) → Growth
- Epics 7 (Cover/Delegation) and 8 (Notifications) → Growth
- Epic 9 (Export/Reporting) → Vision

The epic files predate the PRD. The PRD is the authoritative scope document. The epic files must be updated to reflect Growth/Vision classification. No new stories needed — just metadata correction.

### Coverage Statistics

| Metric | Count |
|---|---|
| Total PRD FRs | 36 |
| Fully covered | 24 |
| Partially covered | 3 (FR3, FR20, FR32) |
| Not covered (gaps) | 5 (FR7, FR10, FR24, FR26, FR27, FR31, FR34) |
| **Coverage %** | **67% fully covered** |

> Note: FR32 (workload summary) exists in EXPORT-05 but is in a Vision-tier epic. If counted as "exists but wrong tier", full+partial coverage = 30/36 = 83%.

## UX Alignment Assessment

### UX Document Status

Not found — expected. `timetable-backend` is a REST API project. All user-facing UX is the responsibility of the separate `schediflow-frontend` project, which has its own architecture specification (`schediflow-frontend — Architecture & Development Specification`). UX design for the backend is not applicable.

### Alignment Issues

None — backend API contracts (endpoints, request/response shapes, pagination envelopes, error envelopes) are fully specified in the architecture document and consistent with PRD requirements.

### Warnings

- ⚠️ The teacher personal timetable endpoint (FR31 gap identified in Epic Coverage) will require a corresponding frontend view. Once the backend story is added, the frontend spec should be updated to include the teacher-scoped lesson view.
- ⚠️ The checkpoint UI (FR26/FR27 gap) is a first-class product feature. When backend checkpoint stories are added, a frontend design is needed for the checkpoint panel — save, list, and restore interactions.

## Epic Quality Review

### Epic 1 — Foundation & Infrastructure

| Check | Result |
|---|---|
| Delivers user value | ⚠️ Indirect — infrastructure only, no user-facing output. Acceptable as Epic 1 in a brownfield project. |
| Epic independence | ✅ Stands alone |
| Story sizing | ✅ Appropriate (1–5 SP each) |
| Forward dependencies | ✅ None |
| DB tables created when needed | ✅ FOUND-04 creates tenants/users — correct scope |

🟡 **Minor:** FOUND-02 is missing from the story list (gap in numbering between FOUND-01 and FOUND-03). Likely merged or removed. Confirm intentional.

---

### Epic 2 — Authentication & User Management

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes — register, login, invite colleagues |
| Epic independence | ✅ Requires Epic 1 only |
| Story sizing | ✅ Appropriate |
| Forward dependencies | ⚠️ AUTH-05 stubs email (NOTIF-03) — handled correctly with StubEmailService |
| MODERATOR role coverage | ❌ Missing — no story adds MODERATOR to the role enum or updates RBAC |

🔴 **Critical:** The MODERATOR role (FR9/FR10) is central to the PRD's RBAC model. No story in Epic 2 creates this role. AUTH-09 handles role assignment but cannot assign a role that doesn't exist. A new story is required.

---

### Epic 3 — Institution Configuration

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes — ADMIN configures school structure |
| Epic independence | ✅ Requires Epics 1 + 2 |
| Story sizing | ✅ Appropriate |
| Forward dependencies | ✅ None |
| Cycle CRUD coverage | ❌ CONFIG-09 seeds a 5-day cycle default but no story provides CRUD for cycles (FR7) |

🟠 **Major:** Scheduling cycles (the number-of-days structure the bell schedule repeats over) are seeded as a default but cannot be configured by ADMIN. The engine needs this data. Requires new story `CONFIG-06`.

---

### Epic 4 — Holiday & Vacation Calendar

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes |
| Scope classification | ❌ Epic file says `MVP: Yes`; PRD classifies as Growth |
| Forward dependency: HOL-05 | 🔴 Critical — HOL-05 integrates holidays into the solver. This requires Epic 6 to be complete. An epic in the Growth tier references internals of the MVP scheduling engine. |

🔴 **Critical:** HOL-05 (`HolidayService` loads holiday dates into solver run) is a cross-epic forward dependency on Epic 6 (SCHED-03). Epic 4 cannot be fully delivered before Epic 6 exists. HOL-05 should be sequenced *after* Epic 6, or moved into Epic 6 as an integration story.

🟠 **Major:** Epic 4 scope classification must be corrected from `MVP` to `Growth` to match the PRD.

---

### Epic 5 — Resource Management

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes — all scheduling resources managed |
| Epic independence | ✅ Requires Epics 1–3 |
| Story sizing | ✅ Most appropriate; RES-11 (CSV import, 8 SP) is large but justified |
| Soft preference creation | ❌ RES-09 handles hard forbidden slots; no story for creating soft preferences (FR34) |

🟠 **Major:** TEACHER soft availability preferences (preferred free days, preferred lighter periods) are an input the scheduling engine requires for FR19/FR36. RES-09 creates forbidden slots (hard) and RES-10 reads preferences, but there is no CREATE endpoint for soft preferences. Requires new story `RES-12`.

---

### Epic 6 — Timetable & Scheduling Engine

| Check | Result |
|---|---|
| Delivers user value | ✅ Core product value |
| Epic independence | ✅ Requires Epics 1–5 |
| Checkpoint stories | ❌ FR26/FR27 — MISSING entirely |
| Teacher personal view | ❌ FR31 — MISSING |
| Partial re-generation | ❌ FR24 — MISSING |
| Story sizing | ✅ SCHED-03 (8 SP) is large but appropriate for async solver complexity |

🔴 **Critical:** Checkpoint save (FR26) and restore (FR27) are completely absent. These are a core product differentiator. Epic 6 cannot be considered complete without them. Add `SCHED-13` (save named checkpoint) and `SCHED-14` (restore to checkpoint).

🟠 **Major:** No teacher-scoped view endpoint (FR31). SCHED-02 returns the full institution timetable. A teacher calling this endpoint receives all lessons, not just their own. Add `SCHED-15` (`GET /api/v1/timetables/{id}/my-lessons`).

🟡 **Minor:** SCHED-03 timeout is defined as tier-based (Starter 30s, Professional 2min, Enterprise 10min), but subscription tiers are deferred post-MVP. A single default timeout (e.g., 60 seconds) must be defined for MVP to avoid blocking the solver run indefinitely.

---

### Epic 7 — Cover, Delegation & Temporary Schedules

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes |
| Scope classification | ❌ Epic file says `MVP: Yes`; PRD classifies as Growth |
| Cross-epic dependency | 🟠 COVER-07 (WebSocket events) requires NOTIF-01 from Epic 8 |

🟠 **Major:** COVER-07 publishes to the WebSocket infrastructure defined in NOTIF-01 (Epic 8). Since both are Growth-tier, Epic 8 must be implemented before COVER-07 can be delivered. The epic ordering (7 before 8) should be reversed for these stories, or NOTIF-01 should be sequenced as a prerequisite to COVER-07.

---

### Epic 8 — Notifications

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes |
| Scope classification | ❌ Epic file says `MVP: Yes`; PRD classifies as Growth |
| Story sizing | ✅ Appropriate |
| Forward dependencies | ✅ None within the epic |

🟠 **Major:** Scope classification must be corrected from `MVP` to `Growth`.

---

### Epic 9 — Export & Reporting

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes |
| Scope classification | ❌ Epic file says `MVP: Yes`; PRD classifies as Vision |
| Open technical decision | 🟡 EXPORT-01 references TD-05 (PDF library TBD — Flying Saucer vs headless Chrome) — unresolved |
| Security concern | 🟡 EXPORT-03 uses `?userId=...` query param for iCal scoping — should use authenticated user context to prevent unauthorized access to another user's lessons |

🟠 **Major:** Scope classification must be corrected from `MVP` to `Vision`.

🟡 **Minor:** EXPORT-03 design exposes a potential access control gap. Using `?userId=...` allows any authenticated user to export another user's personal calendar. Should resolve to authenticated user's own data, with admin override.

---

### Epic 10 — Setup Templates

| Check | Result |
|---|---|
| Delivers user value | ✅ Yes |
| Scope classification | ✅ Correctly marked `MVP: No` |
| Story sizing | ✅ Appropriate |
| Forward dependencies | ✅ None |

✅ No issues.

---

### Quality Summary

| Severity | Count | Issues |
|---|---|---|
| 🔴 Critical | 3 | FR26/FR27 checkpoints missing; MODERATOR role missing; HOL-05 forward dependency on Epic 6 |
| 🟠 Major | 7 | FR7 cycle CRUD; FR24 partial regen; FR31 teacher view; FR34 soft preferences; 4× scope classification errors (Epics 4,7,8,9); COVER-07/NOTIF-01 ordering |
| 🟡 Minor | 3 | FOUND-02 gap; SCHED-03 MVP timeout undefined; EXPORT-03 userId param security |

### Stories to Add

| New Story | Epic | FR Coverage |
|---|---|---|
| AUTH-11: Add MODERATOR role (Flyway + RBAC update) | Epic 2 | FR9, FR10 |
| CONFIG-06: CRUD `/api/v1/cycles` | Epic 3 | FR7 |
| RES-12: `POST /api/v1/teachers/{id}/preferences` — soft availability | Epic 5 | FR34 |
| SCHED-13: Save named timetable checkpoint | Epic 6 | FR26 |
| SCHED-14: Restore timetable to checkpoint | Epic 6 | FR27 |
| SCHED-15: `GET /api/v1/timetables/{id}/my-lessons` — teacher view | Epic 6 | FR31 |
| SCHED-16: Partial re-generation (resource-scoped solver run) | Epic 6 | FR24 |

## Summary and Recommendations

### Overall Readiness Status

**🟠 NEEDS WORK — Do not start Epic 6 until critical gaps are resolved**

The PRD and architecture are solid. The existing epics (1–5) are well-structured and complete. However, Epic 6 (the core product) is missing stories for three of SchediFlow's most important capabilities, and the MODERATOR role — central to the entire RBAC model — has no implementation story anywhere.

### Critical Issues Requiring Immediate Action

1. **MODERATOR role missing (Epic 2)** — Add `AUTH-11` before starting any authorization-dependent work. Every MODERATOR permission in the RBAC matrix is blocked until this role exists in the codebase.

2. **Checkpoint save/restore missing (Epic 6)** — `FR26` and `FR27` (checkpoint/rollback) are a core product differentiator. No stories exist for this feature. Add `SCHED-13` and `SCHED-14` before calling Epic 6 complete.

3. **HOL-05 forward dependency** — Epic 4's solver integration story (HOL-05) cannot be implemented before Epic 6 is done. Move HOL-05 execution to after SCHED-03 is complete, or re-sequence it as the last story in the Epic 4 + Epic 6 integration phase.

### Recommended Next Steps

1. **Add 7 missing stories** (AUTH-11, CONFIG-06, RES-12, SCHED-13, SCHED-14, SCHED-15, SCHED-16) to their respective epic files before implementing Epic 3+.

2. **Correct epic scope classifications** — Update Epics 4, 7, 8, 9 from `MVP: Yes` to their correct PRD classifications (Growth or Vision). This prevents confusion during sprint planning.

3. **Define MVP solver timeout** — SCHED-03 references tier-based timeouts but tiers are deferred. Set a single default (60 seconds recommended) in the Epic 6 notes.

4. **Fix EXPORT-03 security** — Change `?userId=...` to use authenticated user context; add explicit admin-override scope in the story AC.

5. **Proceed with Epic 3** — Epic 3 (Institution Configuration) is the current in-progress epic. It is safe to continue. Add CONFIG-06 (cycle CRUD) as a new story before closing the epic.

### Issue Summary

| Category | Critical | Major | Minor | Total |
|---|---|---|---|---|
| FR coverage gaps | 3 (FR10, FR26, FR27) | 4 (FR7, FR24, FR31, FR34) | 1 (FR3) | 8 |
| Scope classification errors | 0 | 4 (Epics 4,7,8,9) | 0 | 4 |
| Epic quality issues | 1 (HOL-05 dependency) | 1 (COVER-07 ordering) | 2 | 4 |
| **Total** | **4** | **9** | **3** | **16** |

This assessment identified **16 issues** across 3 categories. The 4 critical issues must be resolved before Epic 6 implementation begins. The 9 major issues should be resolved before their respective epics start. The 3 minor issues are low-risk and can be addressed as encountered.

**The PRD is complete and authoritative. The architecture is sound. The gaps are in epic/story coverage — all are fixable with targeted story additions.**

---
*Assessment completed: 2026-03-29 | Assessor: Claude (BMAD Implementation Readiness workflow)*
