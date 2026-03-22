# Story FOUND-05 — JWT Security Infrastructure
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 5 SP | **Status:** Not Started

## Description
Spring Security: `JwtTokenProvider`, `JwtAuthenticationFilter`, token validation, BCrypt password encoding

## Acceptance Criteria
- [ ] `JwtTokenProvider` generates and validates JWT access tokens (15 min expiry)
- [ ] `JwtAuthenticationFilter` extracts Bearer token from `Authorization` header and sets `SecurityContext`
- [ ] Invalid/expired tokens return 401 with standard error envelope
- [ ] BCrypt password encoder bean configured with cost factor 12
- [ ] Public endpoints (auth routes, actuator/health) accessible without token
- [ ] Spring Security HTTP security config disables sessions (STATELESS)

## Technical Notes
Use `io.jsonwebtoken:jjwt` library. JWT secret loaded from environment variable, not hardcoded.
