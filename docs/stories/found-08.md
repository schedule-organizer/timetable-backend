# Story FOUND-08 — CORS Configuration
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 1 SP | **Status:** Not Started

## Description
CORS configuration scoped to known frontend origin

## Acceptance Criteria
- [ ] CORS allows requests only from configured frontend origin (env var `ALLOWED_ORIGINS`)
- [ ] Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- [ ] Allowed headers: Authorization, Content-Type
- [ ] Credentials allowed (for HttpOnly cookie refresh token)
- [ ] OPTIONS preflight requests return 200
- [ ] Requests from unlisted origins are blocked

## Technical Notes
Configure via `WebMvcConfigurer.addCorsMappings` or `CorsConfigurationSource` bean in Security config.
