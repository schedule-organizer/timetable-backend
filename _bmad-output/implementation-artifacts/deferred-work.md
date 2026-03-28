# Deferred work

## Deferred from: code review of story 3.config-01.md (2026-03-29)

- **Concurrent `is_active` races** — Only one active year per tenant is enforced in the service layer, not with a database constraint. Two concurrent “activate” requests could theoretically both pass `deactivateCurrentActive` before either commits. Left as an explicit tradeoff per acceptance criteria; resolve later with a partial unique index or serializable transactions if needed.
