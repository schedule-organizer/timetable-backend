# Epic 1 — Foundation & Infrastructure
**Status:** Not Started | **MVP:** Yes | **Total Points:** 21 SP

## Goal
Establish a production-grade Spring Boot application skeleton with multi-tenancy, security scaffolding, error handling, and Docker orchestration. Every subsequent epic depends on this foundation.

## Market Driver
Speed to market. A robust foundation prevents compounding technical debt that would slow later feature delivery. The target market (SME schools) expects a reliable cloud tool; a flaky foundation undermines trust.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| FOUND-01 | Initialize schediflow-backend: Spring Boot 3, Java 21, Maven, core dependencies (Spring Web, Security, Data JPA, Validation, Actuator, MapStruct, Springdoc) | 2 | Not Started |
| FOUND-03 | `docker-compose.yml`: postgres + backend + frontend + mailhog services, named volumes, healthchecks | 3 | Not Started |
| FOUND-04 | Flyway: `V001__create_tenants_users.sql` — tenants and users tables with indexes | 2 | Not Started |
| FOUND-05 | Spring Security: `JwtTokenProvider`, `JwtAuthenticationFilter`, token validation, BCrypt password encoding | 5 | Not Started |
| FOUND-06 | Multi-tenancy: `TenantContext` ThreadLocal, Hibernate `@Filter` on all tenant-scoped entities, `TenantFilter`, JWT extraction | 5 | Not Started |
| FOUND-07 | Springdoc OpenAPI 3: Swagger UI at `/api-docs`, JWT bearer auth scheme, all controllers annotated | 1 | Not Started |
| FOUND-08 | CORS configuration scoped to known frontend origin | 1 | Not Started |
| FOUND-09 | `GlobalExceptionHandler`: consistent JSON error envelope (`status`, `code`, `message`, `details`, `timestamp`) for all error types | 2 | Not Started |

## Notes
This epic is a strict prerequisite for all other epics. No other epic can begin until the foundation is stable. The tech stack is Spring Boot 3 / Java 21 / PostgreSQL 16 / Timefold Solver. Multi-tenancy via Hibernate `@Filter` ensures all tenant-scoped queries are automatically filtered by `tenant_id` without requiring manual query modifications in every repository.
