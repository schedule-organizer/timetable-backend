# Deferred work

## Deferred from: implementation of Epic 7 COVER-01…COVER-07 (2026-08-02)

- **NOTIF-01 should adopt `WebSocketEventPublisher` rather than rebuild it** — COVER-07 had to build the STOMP stack because Epic 8 is `backlog`. `WebSocketConfig`, `StompAuthChannelInterceptor`, `WebSocketDestinations` and `WebSocketEventPublisher` already implement the destination contract, JWT CONNECT auth and per-subscription authorization. NOTIF-01 should extend these, not duplicate them.
- **Epic 7 is not exercisable end to end until Epic 6 lands** — nothing creates `lessons`, so cover, delegation and overrides all operate on rows that only tests insert. The V015 schema is sufficient, so no rework is expected, but there is no real-data path yet.
- **No write API for temporary-schedule lesson overrides** — COVER-05's AC only requires that overrides be stored separately, and no Epic 7 story defines how one is authored. `temporary_schedule_lessons` is created and cleared by COVER-06 but can only be populated by direct SQL. Needs a follow-up story, most naturally alongside SCHED-02's lesson grid.
- **Temporary schedules are not yet applied when reading a timetable** — the overlay is stored and expired correctly, but no read path merges overrides over base lessons for a date in range. That belongs with SCHED-02.
- **"Only one active temporary schedule per timetable" is service-enforced only** — a partial unique index on `(base_timetable_id) WHERE status = 'ACTIVE'` would enforce it in the database, but is not portable to the H2 instance the tests use. Consider adding it in a Postgres-only migration.
- **Delegation `SWAP` semantics were invented** — the stories never define what a swap exchanges. COVER-04 trades the target's lesson in the same slot, or moves the lesson when the slot is empty. Confirm with product before this reaches users.
- **No notification when a delegation request is submitted** — `DELEGATION_UPDATE` only fires on a decision, per the ACs. The target teacher currently learns nothing until a moderator acts.
- **No listing endpoint for delegation requests** — a moderator can decide a request by id but has no way to discover pending ones through the API. `findByTenantIdAndStatusOrderByIdAsc` exists unused, ready for it.
- **Cover assignments cannot be read back or removed** — COVER-01 specifies only `POST`; there is no GET or DELETE, so cover cannot be cancelled once arranged.

## Deferred from: implementation of Epic 5 RES-07…RES-11 (2026-08-01)

- **FR34 has no write path for teacher preferences** — RES-10 reads `teacher_preferences`, and V022 creates the table, but no story defines an endpoint for creating or updating preferences, so they can only be set with direct SQL. Already flagged as a gap in the 2026-03-29 implementation-readiness report. Needs a follow-up story before the availability view is usable in production.
- **Solver does not yet consume forbidden slots** — `ForbiddenSlotService.findAllForSolver` exposes the rows, but the solver's `UnavailablePeriodPenalty` fact is keyed on a period slot with no entity, and the solver `Lesson` has no room or class reference. Entity-scoped hard unavailability cannot be expressed until SCHED-02/03 fills out the planning model.
- **`Lesson.teachingGroupId` is never populated** — added by RES-08 for the "Option block groups must share a period" constraint. SCHED-02/03 must set it when building `TimetableSolution` from persisted rows, otherwise the constraint stays inert on real data.
- **Teaching groups have no lesson-assignment guard on delete** — RES-07's AC says groups with lesson assignments cannot be hard deleted. It is satisfied by never hard deleting, but once `lessons` gains a `teaching_group_id` column (SCHED-02/03) consider whether deactivation should also be blocked or warn.
- **Endpoint-test auth boilerplate copied again** — `createModUser` / `inviteAndComplete` / `loginAndGetToken` are duplicated into `TeachingGroupEndpointTest`, `OptionBlockEndpointTest`, `ForbiddenSlotEndpointTest`, `TeacherAvailabilityEndpointTest` and `CsvImportEndpointTest`. Extends the same duplication already noted for CONFIG-10; a shared base fixture would now pay for itself.
- **CSV import has no rate limiting** — `POST /api/v1/import/{entityType}` accepts up to 1000 rows per call with no per-tenant throttle. Same system-wide gap noted for the holiday import endpoint.

## Deferred from: code review of story 5.res-06.md (2026-04-04)

- **`class_subject_hours.periods_per_cycle` allows zero at DB** — V013 `CHECK (periods_per_cycle >= 0)`; RES-06 API uses `@Positive`. Tighten the DB constraint when convenient for consistency with RES-02 deferred note.
- **Tenant alignment on `class_subject_hours` vs `school_classes`** — FK on `class_id` only; no composite constraint that `tenant_id` matches the parent class row. Service uses `TenantContext`; optional hardening via schema later.

## Deferred from: code review of story 5.res-05.md (2026-04-04)

- **Populate `TeacherSubjectQualification` when assembling `TimetableSolution`** — Constraint and solver tests are in place; no production path builds the solution yet. When timetabling is integrated, map persisted `teacher_qualifications` (via teacher `user_id` + `subject_id`) into `teacherSubjectQualifications` so the solver enforces the same rules as the API.

## Deferred from: code review of story 5.res-04.md (2026-04-02)

- **`lenient()` on `HolidayImportServiceTest` conflict stub** — Shared `@BeforeEach` stub marked lenient so early-return tests do not fail on unnecessary stubbing; slightly weaker Mockito strictness for that class.

## Deferred from: code review of 4.hol-07 + 5.res-03 (2026-04-01)

- **Per-date holiday import conflict queries** — `HolidayImportService` calls `findPublishedLessonHolidayConflicts` once per distinct newly inserted date. Fine for normal feed sizes; consolidate if volume or latency becomes an issue.

## Deferred from: code review of story 5.res-02.md (2026-04-01)

- **Active subject code uniqueness at service layer only** — Same trade-off as `V012` rooms: no partial unique index because H2 test mode does not support it; concurrent creates could theoretically duplicate active codes until a DB constraint or `DataIntegrityViolation` handling is added.
- **`class_subject_hours.periods_per_cycle` allows zero** — `CHECK (periods_per_cycle >= 0)` permits `0`; may be tightened when RES-06 defines valid allocations.
- **`class_subject_hours` tenant vs subject alignment** — There is no constraint that `tenant_id` on `class_subject_hours` matches `subjects.tenant_id` for the referenced `subject_id`; RES-06 should tighten allocation integrity when the feature is built out.

## Deferred from: code review of 5.res-01.md (2026-04-01)

- **TenantContext.getTenantId() can return null** — TenantFilter silently skips setting context if the `tenantId` claim is absent or non-numeric; all services call getTenantId() without a null guard, resulting in silent empty results rather than an error. Systemic issue; not introduced by RES-01.
- **equipmentTags has no @Size bound** — No constraint on list length or individual element length; arbitrarily large payloads accepted and stored. Revisit when input limits are standardised across all DTOs.
- **@Transactional(readOnly=true) missing on list() and getById()** — Read methods run without a readOnly transaction hint; minor performance/correctness gap consistent with other services in the project.
- **Soft delete doesn't check lesson references** — Spec notes "rooms referenced by lessons cannot be hard deleted"; no Lesson entity exists yet. Wire referential guard when Lesson CRUD is implemented (Epic 6).
- **Inactive room name reuse could complicate future reactivation** — By design, soft-deleted room names can be reused immediately. If reactivation is ever added, two active rooms with the same name would exist without triggering the conflict check. Revisit if reactivation is scoped.

## Deferred from: code review of 4.hol-05.md (2026-03-31)

- **Generic constraint name “Holiday slot must be free”** — `UnavailablePeriodPenalty` may apply to non-holiday unavailability later; constraint label could be renamed when that scope expands.

## Deferred from: code review of 4.hol-04.md (2026-03-31)

- **V010 `source` default for legacy rows** — Rows that existed before `source` was added are stored as `MANUAL` after migration. Historically import-only rows stay `MANUAL` until re-import or a one-time backfill; acceptable unless product requires accurate `IMPORTED` for old data.

## Deferred from: code review of 4.hol-02.md (2026-03-31)

- **No rate-limiting on `POST /api/v1/holidays/import`** — Any ADMIN or MOD can fan out unlimited Calendarific calls; system-wide rate-limiting gap not introduced by this story.
- **Empty holiday feed returns 200 with all-zero counts** — Indistinguishable from a wrong country/year; design choice, acceptable for now.
- **Hibernate `tenantFilter` redundancy in `findByHolidayCalendarIdAndTenantIdAndDate`** — Pre-existing pattern across all repositories; if the filter is active it adds a redundant `AND tenant_id = ?` clause.
- **`BadRequestException` thrown from `CalendarificHolidayFeedClient`** — Minor layering concern; HTTP semantics leaking into integration adapter. Behavior is correct.
- **`region` blank-string guard duplicated in service and client** — Both sides independently check `isBlank()`; behavior is consistent but guard could be centralised.
- **`@Transactional` spans outbound HTTP call in `importPublicHolidays`** — Holds a DB connection open during the Calendarific HTTP call (up to 5s). Negligible at expected ADMIN/MOD import frequency; revisit if load warrants splitting fetch from persist.

## Deferred from: code review of story 3.config-10.md (2026-03-30)

- **Deduplicate `createModUser` across endpoint tests** — Same invite/register/role-promotion flow is copied into `AcademicYearEndpointTest`, `TermEndpointTest`, `BellScheduleEndpointTest`, and `TenantSettingsEndpointTest`; consider a shared test helper or base fixture when touching this area again.

## Deferred from: code review of story 3.config-09.md (2026-03-30)

- **Sprint status `3-config-01` / `3-config-02` as `done`** — Deferred during code review (choice **0**); verify both stories are actually complete and fix `sprint-status.yaml` if not.

- **Seed idempotency vs. partial tenant state** — `InstitutionSeedService` only checks academic year count. If years were deleted but other seeded artefacts remained, behaviour may be inconsistent; revisit if admins can create such states.

- **Rate-limit props in tests** — Several endpoint tests set `app.ratelimit.max-requests=500` to avoid 429 flakes; deferred until global test isolation or rate-limit configuration is improved.

## Deferred from: code review of story 3.config-05.md (2026-03-30)

- **Rate-limit / 429 flakiness in unrelated endpoint tests** — `TenantSettingsEndpointTest`, `LogoutEndpointTest`, `TermEndpointTest` (setUp hitting 429). Not introduced by CONFIG-05; track separately when stabilising CI.

## Deferred from: code review of story 3.config-01.md (2026-03-29)

## Deferred from: code review of story 3.config-04.md (2026-03-29)

- **No optimistic locking on settings read-modify-write** — Two concurrent ADMIN/MOD PUTs read the same snapshot, merge independently, and last writer silently wins. Resolve with `@Version` on `Tenant` or `SELECT FOR UPDATE` if concurrent admin edits become a real-world scenario.
- **Settings blob has no server-side size limit** — Callers can persist arbitrarily large JSON. Enforce a max payload size at the controller or via Spring's `spring.servlet.multipart.max-request-size` / a request body size filter.
- **getSettings() lacks `@Transactional(readOnly=true)`** — Non-transactional reads may observe uncommitted state under low isolation levels. Add `@Transactional(readOnly=true)` when stricter read consistency is required.
- **No schema validation beyond timezone** — `locale`, `terminology`, `constraintDefaults` accept any JSON type without validation. Add field-level type checks if downstream code relies on specific shapes.
- **validateTimezone re-validates pre-existing invalid DB data** — If an invalid timezone was somehow stored, any subsequent PUT (even one not touching timezone) will fail with BadRequest. Add a data migration or startup check if data integrity cannot be guaranteed.
- **Required top-level settings keys not enforced** — The settings blob has no mandatory-key constraint; GET can return an empty `{}`. Add initialisation defaults in InstitutionSeedService if the front-end requires a populated settings object.

- **Concurrent `is_active` races** — Only one active year per tenant is enforced in the service layer, not with a database constraint. Two concurrent “activate” requests could theoretically both pass `deactivateCurrentActive` before either commits. Left as an explicit tradeoff per acceptance criteria; resolve later with a partial unique index or serializable transactions if needed.
