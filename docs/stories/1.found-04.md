# Story FOUND-04 — Flyway — Tenants & Users Migration
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 2 SP | **Status:** Not Started

## Description
Flyway: `V001__create_tenants_users.sql` — tenants and users tables with indexes

## Acceptance Criteria
- [ ] `V001__create_tenants_users.sql` creates `tenants` table (id, name, slug, status, settings JSONB, created_at)
- [ ] `V001__create_tenants_users.sql` creates `users` table (id, tenant_id FK, email, password_hash, role, status, created_at)
- [ ] Indexes on `users.email` (unique), `users.tenant_id`, `tenants.slug` (unique)
- [ ] Flyway runs automatically on application start
- [ ] Migrations are repeatable-safe (no data loss on re-run with clean DB)

## Technical Notes
Use `bigserial` for IDs. tenant_id FK has ON DELETE CASCADE.
