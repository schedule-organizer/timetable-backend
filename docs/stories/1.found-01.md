# Story FOUND-01 — Initialize Spring Boot Project
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 2 SP | **Status:** Not Started

## Description
Initialize schediflow-backend: Spring Boot 3, Java 21, Maven, core dependencies (Spring Web, Security, Data JPA, Validation, Actuator, MapStruct, Springdoc)

## Acceptance Criteria
- [ ] Maven project compiles with Java 21 source/target
- [ ] Spring Boot 3 application starts successfully
- [ ] Dependencies included: Spring Web, Spring Security, Spring Data JPA, Spring Validation, Spring Actuator, MapStruct, Springdoc OpenAPI
- [ ] `/actuator/health` returns UP
- [ ] `.gitignore` excludes build artifacts and IDE files

## Technical Notes
Use Spring Initializr structure. Maven wrapper (`mvnw`) included. Base package: `com.schediflow`.
