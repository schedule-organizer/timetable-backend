# Deferred work

## Deferred from: code review of story 3.config-01.md (2026-03-29)

## Deferred from: code review of story 3.config-04.md (2026-03-29)

- **No optimistic locking on settings read-modify-write** — Two concurrent ADMIN/MOD PUTs read the same snapshot, merge independently, and last writer silently wins. Resolve with `@Version` on `Tenant` or `SELECT FOR UPDATE` if concurrent admin edits become a real-world scenario.
- **Settings blob has no server-side size limit** — Callers can persist arbitrarily large JSON. Enforce a max payload size at the controller or via Spring's `spring.servlet.multipart.max-request-size` / a request body size filter.
- **getSettings() lacks `@Transactional(readOnly=true)`** — Non-transactional reads may observe uncommitted state under low isolation levels. Add `@Transactional(readOnly=true)` when stricter read consistency is required.
- **No schema validation beyond timezone** — `locale`, `terminology`, `constraintDefaults` accept any JSON type without validation. Add field-level type checks if downstream code relies on specific shapes.
- **validateTimezone re-validates pre-existing invalid DB data** — If an invalid timezone was somehow stored, any subsequent PUT (even one not touching timezone) will fail with BadRequest. Add a data migration or startup check if data integrity cannot be guaranteed.
- **Required top-level settings keys not enforced** — The settings blob has no mandatory-key constraint; GET can return an empty `{}`. Add initialisation defaults in InstitutionSeedService if the front-end requires a populated settings object.

- **Concurrent `is_active` races** — Only one active year per tenant is enforced in the service layer, not with a database constraint. Two concurrent “activate” requests could theoretically both pass `deactivateCurrentActive` before either commits. Left as an explicit tradeoff per acceptance criteria; resolve later with a partial unique index or serializable transactions if needed.
