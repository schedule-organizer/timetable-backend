# Story CONFIG-09 — Institution Seed Defaults
**Epic:** Epic 3 — Institution Configuration | **Points:** 4 SP | **Status:** Not Started

## Description
Seed service: on first institution creation, apply sensible defaults (5-day cycle, 8 periods, standard terminology)

## Acceptance Criteria
- [ ] `InstitutionSeedService` triggered automatically after successful institution registration (AUTH-01)
- [ ] Creates default `AcademicYear` for current year
- [ ] Creates default `BellSchedule` with 8 periods (45 min each), lunch at period 5
- [ ] Sets default `settings`: 5-day week cycle, standard English terminology
- [ ] Seed is idempotent (safe to call multiple times)
- [ ] All seed data scoped to the new tenant

## Technical Notes
Inject `InstitutionSeedService` into registration flow and call post-commit.
