# Story FOUND-03 — Docker Compose Orchestration
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 3 SP | **Status:** Not Started

## Description
`docker-compose.yml`: postgres + backend + frontend + mailhog services, named volumes, healthchecks

## Acceptance Criteria
- [ ] `docker-compose.yml` defines services: `postgres`, `backend`, `frontend`, `mailhog`
- [ ] Named volumes for postgres data persistence
- [ ] Healthchecks configured for postgres and backend
- [ ] Backend service depends_on postgres with healthy condition
- [ ] MailHog UI accessible at port 8025
- [ ] Environment variables externalised via `.env.example`

## Technical Notes
PostgreSQL 16. Backend waits for DB healthcheck before starting.
