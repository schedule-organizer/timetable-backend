# Story CONFIG-05 — Public Settings Endpoint
**Epic:** Epic 3 — Institution Configuration | **Points:** 1 SP | **Status:** Not Started

## Description
`GET /api/v1/settings/public` — unauthenticated endpoint returning locale/timezone for login page

## Acceptance Criteria
- [ ] Unauthenticated endpoint (no JWT required)
- [ ] Tenant identified by subdomain or `tenantSlug` query param
- [ ] Returns only: locale, timezone, institutionName
- [ ] Returns 404 if tenant slug not found or tenant is inactive
- [ ] Response cached (e.g., 5 min TTL) to avoid DB hit on every login page load

## Technical Notes
Must be excluded from JWT filter and multi-tenancy filter. Tenant resolved by slug, not from JWT.
