# Story TMPL-03 — Apply Template to Institution
**Epic:** Epic 10 — Setup Templates (Post-MVP) | **Points:** 4 SP | **Status:** Not Started

## Description
`POST /api/v1/institutions/apply-template` — apply template settings and bell schedule to tenant; idempotent

## Acceptance Criteria
- [ ] Accepts: templateId
- [ ] Applies template's bell schedule (creates `BellSchedule` + periods for tenant)
- [ ] Applies template's settings (locale, timezone, terminology) to tenant settings
- [ ] Applies template's constraint defaults
- [ ] Idempotent: re-applying same template is safe (upserts, no duplicates)
- [ ] Returns preview of changes before applying (with `dryRun=true` query param)
- [ ] Admin only

## Technical Notes
Wraps CONFIG-03 and CONFIG-04 logic. Template application does not overwrite manually customised settings if `preserveExisting=true`.
