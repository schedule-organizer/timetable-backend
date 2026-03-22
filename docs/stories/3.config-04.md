# Story CONFIG-04 — Institution Settings
**Epic:** Epic 3 — Institution Configuration | **Points:** 3 SP | **Status:** Not Started

## Description
`GET/PUT /api/v1/settings` — institution settings JSONB blob (locale, timezone, terminology, constraint defaults); Admin only

## Acceptance Criteria
- [ ] `GET` returns current settings JSONB (locale, timezone, terminology map, constraint defaults)
- [ ] `PUT` merges provided fields into existing settings (partial update)
- [ ] Admin only for write; all authenticated roles can read
- [ ] Settings include terminology overrides (e.g., "class" → "form group")
- [ ] Timezone validated against IANA timezone database
- [ ] Settings stored as JSONB in `tenants.settings` column

## Technical Notes
Merge strategy: deep merge, not replace. Validate IANA timezone on write.
