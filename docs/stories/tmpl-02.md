# Story TMPL-02 — Built-in Template Seeding
**Epic:** Epic 10 — Setup Templates (Post-MVP) | **Points:** 5 SP | **Status:** Not Started

## Description
Seed: 5 built-in templates — Primary School, Secondary School, High School / Sixth Form, Language School, Vocational Centre

## Acceptance Criteria
- [ ] 5 built-in templates seeded via Flyway data migration or `DataInitializer` component
- [ ] Templates: Primary School (6 periods, 5 days), Secondary School (8 periods, 5 days), High School/Sixth Form (10 periods, 5 days), Language School (6 periods, 5 days with afternoon blocks), Vocational Centre (4 periods + workshops)
- [ ] Each template includes: bell schedule, terminology, constraint defaults
- [ ] Templates available in `GET /api/v1/templates` without authentication (for onboarding UI)
- [ ] Idempotent seeding (safe to run on schema reset)

## Technical Notes
Seeded via `V00X__seed_built_in_templates.sql` Flyway migration.
