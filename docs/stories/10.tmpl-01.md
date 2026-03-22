# Story TMPL-01 — Template Data Model
**Epic:** Epic 10 — Setup Templates (Post-MVP) | **Points:** 3 SP | **Status:** Not Started

## Description
Template data model + Flyway migration; `institution_templates` table with embedded configuration JSONB

## Acceptance Criteria
- [ ] `V00X__create_institution_templates.sql` Flyway migration creates `institution_templates` table
- [ ] Schema: id, name, description, institutionType, configurationJson (JSONB), isBuiltIn, tenantId (nullable for built-ins)
- [ ] JSONB configuration stores: bellSchedule, settings, terminology, constraintDefaults
- [ ] Built-in templates have `tenant_id = NULL` and `is_built_in = true`
- [ ] Custom templates scoped to tenant with `is_built_in = false`
- [ ] API: `GET /api/v1/templates` lists available templates (built-in + tenant's own)

## Technical Notes
Built-ins seeded via TMPL-02. JSONB schema validated on write.
