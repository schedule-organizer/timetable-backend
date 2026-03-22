# Story RES-08 — Option Blocks CRUD
**Epic:** Epic 5 — Resource Management | **Points:** 4 SP | **Status:** Not Started

## Description
CRUD `/api/v1/option-blocks` — option block containers that enforce simultaneous scheduling of member groups; Flyway migration

## Acceptance Criteria
- [ ] CRUD for option blocks (name, description, memberGroupIds[])
- [ ] All member teaching groups scheduled at the same period (enforced by solver)
- [ ] `V00X__create_option_blocks.sql` Flyway migration included
- [ ] Member groups must all be of type OPTION_BLOCK
- [ ] Returns 404 if any memberGroupId not found in tenant
- [ ] Minimum 2 member groups per block (validation)

## Technical Notes
`option_blocks` and `option_block_groups` junction tables.
