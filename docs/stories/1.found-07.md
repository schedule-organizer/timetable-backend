# Story FOUND-07 — Springdoc OpenAPI / Swagger UI
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 1 SP | **Status:** Not Started

## Description
Springdoc OpenAPI 3: Swagger UI at `/api-docs`, JWT bearer auth scheme, all controllers annotated

## Acceptance Criteria
- [ ] Swagger UI accessible at `/api-docs` (or `/swagger-ui.html`)
- [ ] OpenAPI JSON spec available at `/v3/api-docs`
- [ ] JWT Bearer auth scheme configured in OpenAPI security definition
- [ ] API info (title, version, description) populated
- [ ] Existing controllers have `@Tag` and `@Operation` annotations

## Technical Notes
`springdoc-openapi-starter-webmvc-ui` dependency. Swagger UI disabled in production profile via config.
