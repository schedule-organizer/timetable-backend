---
project_name: 'timetable-backend'
user_name: 'Arthur'
date: '2026-03-29'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'code_quality_rules', 'workflow_rules', 'critical_rules']
status: 'complete'
rule_count: 52
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

- Java 21 (LTS — virtual threads available but not yet in use)
- Spring Boot 3.3.5 (parent manages transitive dependency versions)
- PostgreSQL 16 (prod/staging); H2 in-memory with `MODE=PostgreSQL` (dev + all tests)
- MapStruct 1.5.5.Final — must be listed as annotation processor in maven-compiler-plugin
- Springdoc OpenAPI 2.6.0 — Swagger UI at `/swagger-ui.html`, JSON spec at `/v3/api-docs`
- JJWT 0.12.6 — `jjwt-api` (compile) + `jjwt-impl` + `jjwt-jackson` (both runtime)
- Flyway — schema ownership; `ddl-auto: none` — Hibernate must NEVER generate DDL
- Build: Maven (no Gradle)

## Critical Implementation Rules

### Language-Specific Rules

- Java records are used for DTOs (e.g. `RegisterRequest`, `LoginRequest`) — use `record` not `class` for new request/response DTOs
- Service inner records are used for method return types (e.g. `AuthService.LoginResult`, `RegistrationResult`) — prefer this over multi-value returns or out-params
- Entity IDs use `Long` (auto-increment via `GenerationType.IDENTITY`) in the current implementation — the architecture doc shows UUID in DDL but the live code uses `Long`; follow the live code until migrated
- `OffsetDateTime` is used for all timestamps — never `LocalDateTime` or `Date`
- `@PrePersist` sets `createdAt` on entities — do not set it manually in service code
- Constructor injection only — no `@Autowired` field injection; no setter injection
- No Lombok — all getters/setters are written manually
- Package root: `com.schediflow`

### Framework-Specific Rules

**Package Structure**
- Controllers → `com.schediflow.api.v1/` — no business logic, only delegate to service
- Services → `com.schediflow.service/` — all business logic lives here
- Repositories → `com.schediflow.repository/` — Spring Data JPA only, no custom SQL unless via `@Query`
- Entities → `com.schediflow.domain/` — JPA entities NEVER serialized to API responses
- DTOs → `com.schediflow.dto.request/` and `com.schediflow.dto.response/`
- Exceptions → `com.schediflow.exception/` — always extend `SchediFlowException`
- `GlobalExceptionHandler` in `com.schediflow.api.advice/` handles all exceptions — do NOT add `try/catch` in controllers

**API Conventions**
- All endpoints are versioned under `/api/v1/`
- All list endpoints return the pagination envelope: `{ content, page, size, totalElements, totalPages }`
- All error responses use the standard envelope: `{ status, code, message, details, timestamp }`
- Action endpoints (non-CRUD) use `POST` with a verb path segment (e.g. `/publish`, `/archive`, `/pin`)

**Multi-Tenancy (Critical)**
- `tenant_id` is NEVER accepted from the client (no request body field, no query param)
- Always extracted from the JWT via `TenantContext.getTenantId()` in service code
- All tenant-scoped entities must carry `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`
- The `TenantFilterAspect` activates the Hibernate filter — do not enable it manually in services
- `TenantContext.clear()` must always be called in a `finally` block if set manually

**Security**
- Public endpoints: `/api/v1/auth/**`, `/api/v1/settings/public`, `/actuator/health` — all others require auth
- Role enforcement via `@PreAuthorize("hasRole('ADMIN')")` at service or controller level
- Refresh token stored in HttpOnly cookie named `refresh_token`, scoped to path `/api/v1/auth/refresh`
- JWT payload fields: `sub` (userId as Long), `tenantId` (Long), `role` (String), `email` (String)
- Access token expiry: 15 min (`900000 ms`); refresh token expiry: 7 days (`604800000 ms`)
- BCrypt cost factor 12 for password hashing

**Exception Hierarchy**
- Always throw a subclass of `SchediFlowException` — never throw raw `RuntimeException`
- `ConflictException` → 409, `UnauthorizedException` → 401, `ResourceNotFoundException` → 404, `BadRequestException` → 400

### Testing Rules

**Two-Tier Test Strategy**
- **Service tests** (`src/test/java/com/schediflow/service/`) — pure Mockito, `@ExtendWith(MockitoExtension.class)`, NO Spring context loaded
- **Endpoint tests** (`src/test/java/com/schediflow/api/v1/`) — `@SpringBootTest + @AutoConfigureMockMvc`, full Spring context against H2

**Service Test Rules**
- Instantiate the service under test manually in `@BeforeEach` via constructor — do NOT use `@InjectMocks`
- Use `ArgumentCaptor` to verify what was saved/passed to repositories
- Use `assertThatThrownBy(...).isInstanceOf(XException.class)` for exception assertions (AssertJ style)
- Use `inOrder(repo)` to verify ordering of repository calls when sequence matters

**Endpoint Test Rules**
- Annotate with `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` to reset H2 state between tests
- Use `MockMvc` with `.andExpect(jsonPath("$.field").value(...))` for response assertions
- Always verify cookie attributes explicitly: `.andExpect(cookie().httpOnly(...))`, `.andExpect(cookie().path(...))`
- Test naming: `methodName_scenario_expectedOutcome` (e.g. `register_duplicateEmail_returns409`)
- Test class naming: `{Feature}EndpointTest` for endpoint tests, `{Service}Test` for service tests

**General Rules**
- No Testcontainers yet — all tests run against H2; do not add Testcontainers without discussion
- Entity `id` fields (private, no setter) must be set via reflection in tests — use a helper method pattern as seen in `AuthServiceTest`
- Use `verify(repo, never()).save(any())` to assert that side effects did NOT occur

### Code Quality & Style Rules

**Naming Conventions**
- Classes: PascalCase — controllers suffix `Controller`, services suffix `Service`, repositories suffix `Repository`
- Request DTOs: suffix `Request` (e.g. `LoginRequest`); response DTOs: suffix `Response` (e.g. `AuthResponse`)
- Exception classes: suffix `Exception` (e.g. `ConflictException`)
- Test classes: suffix `Test` (never `Spec` or `IT`)
- Flyway migrations: `V{NNN}__{snake_case_description}.sql` (e.g. `V001__create_tenants_users.sql`) — sequential, zero-padded to 3 digits

**Code Organization**
- No business logic in controllers — controllers only validate input, call service, return response
- No `@Transactional` on controllers — only on service methods that need it
- Javadoc on all public controller methods documenting HTTP contract, return codes, and key behaviour
- `@Valid` on all `@RequestBody` parameters that use bean validation
- Section comments (`// ── Happy path ──`) used in test files to group related tests

**Documentation**
- Controller method Javadoc format: describes the operation, `@return` lists all HTTP status codes and conditions
- No inline comments in production code unless logic is genuinely non-obvious
- No TODOs left in committed code

**No Static Analysis Config Present**
- No `.editorconfig`, no Checkstyle, no SpotBugs config found — follow the style visible in existing files

### Development Workflow Rules

**Branch Naming**
- Feature branches: `epic/{n}-{short-description}` (e.g. `epic/2-authentication-user-management`)
- Story branches: implied from epic branch; stories are implemented within the epic branch

**Commit Message Format**
- Conventional Commits style: `type(scope): description`
- Types in use: `feat`, `fix`, `chore`, `docs`
- Scope is the epic/story identifier (e.g. `feat(epic-2): ...`, `feat(auth): AUTH-01 ...`)

**Story & Epic Tracking**
- Stories live in `_bmad-output/implementation-artifacts/stories/`
- Story files are prefixed with epic order: `{epic}.{story}-{slug}.md` (e.g. `3.config-02.md`)
- Planning artifacts live in `_bmad-output/planning-artifacts/`

**Database Migrations**
- New Flyway migrations go in `src/main/resources/db/migration/`
- Naming: `V{next_number}__{description}.sql` — check existing files to find the next sequence number
- Never modify an already-applied migration — always create a new one
- H2 `MODE=PostgreSQL` is used locally but does not support all PG features (e.g. no `gen_random_uuid()` — use `UUID_GENERATE_V4()` or sequence in H2-compatible migrations)

**Local Development**
- Default profile uses H2 — no PostgreSQL setup needed for local dev or tests
- Override datasource via env vars (`SPRING_DATASOURCE_URL`, etc.) for PostgreSQL
- JWT secret defaults to a dev-only base64 value — override `JWT_SECRET` in prod
- CORS allowed origins default to `http://localhost:3000` and `http://localhost:5173`

### Critical Don't-Miss Rules

**Anti-Patterns — Never Do These**
- NEVER serialize a JPA entity directly in an API response — always map through a `*Response` DTO via MapStruct
- NEVER accept `tenant_id` from the client in a request body or query parameter
- NEVER let Hibernate manage DDL (`ddl-auto` must remain `none`) — Flyway owns the schema
- NEVER modify an existing Flyway migration file — create a new one
- NEVER use `@InjectMocks` in service tests — construct services manually to keep dependencies explicit
- NEVER throw raw `RuntimeException` — always use a `SchediFlowException` subclass so the global handler returns a proper error envelope
- NEVER add `try/catch` in controllers — let `GlobalExceptionHandler` handle all exceptions

**Security Edge Cases**
- Login and registration must return the same generic 401 message for wrong email vs wrong password — do NOT distinguish between the two (prevents user enumeration)
- `logout` is idempotent — must return 204 even if the refresh cookie is absent or token not found
- Invitation tokens expire after 72 hours and are single-use — mark as used immediately on consumption, not after other operations complete

**Multi-Tenancy Edge Cases**
- Every new tenant-scoped entity class must have `@Filter(name = "tenantFilter", ...)` — forgetting this silently leaks cross-tenant data
- When enabling the Hibernate filter manually (outside of `TenantFilterAspect`), always use a `try/finally` to call `TenantContext.clear()`

**MapStruct**
- MapStruct processor must be in `annotationProcessorPaths` in `maven-compiler-plugin` — if missing, mappers generate as empty stubs with no compile error

**H2 Compatibility**
- H2 `MODE=PostgreSQL` does not support: `gen_random_uuid()`, `TEXT[]` arrays, or `INET` type — migrations must be written to be H2-compatible or use a separate test migration profile
- `DATABASE_TO_LOWER=TRUE` is set — all identifiers are lowercased; match column names in entities accordingly

**Pagination**
- All paginated responses must use the `PagedResponse` wrapper — do not return raw `Page<T>` from Spring Data directly to the client

---

## Usage Guidelines

**For AI Agents:**
- Read this file before implementing any code
- Follow ALL rules exactly as documented
- When in doubt, prefer the more restrictive option
- Update this file if new patterns emerge

**For Humans:**
- Keep this file lean and focused on agent needs
- Update when technology stack changes
- Review quarterly for outdated rules
- Remove rules that become obvious over time

Last Updated: 2026-03-29
