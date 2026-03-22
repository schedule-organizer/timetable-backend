# Story TMPL-04 — Save Custom Template
**Epic:** Epic 10 — Setup Templates (Post-MVP) | **Points:** 3 SP | **Status:** Not Started

## Description
`POST /api/v1/templates` — save current institution configuration as a reusable custom template

## Acceptance Criteria
- [ ] Accepts: name, description
- [ ] Captures current tenant's: active bell schedule, settings, terminology, constraint defaults
- [ ] Saves as custom template scoped to tenant (`is_built_in = false`)
- [ ] Returns created template with id
- [ ] Custom templates visible in `GET /api/v1/templates` alongside built-ins
- [ ] Admin only
- [ ] Max 10 custom templates per tenant (returns 400 if limit reached)

## Technical Notes
Snapshot of current config at time of save. Changes after save do not affect the template.
