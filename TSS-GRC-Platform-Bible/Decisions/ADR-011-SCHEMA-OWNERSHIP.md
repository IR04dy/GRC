# ADR-011 — Schema Ownership Equals Module Ownership

**Status:** Proposed
**Date:** 2026-07-25
**Makes enforceable:** `ADR-003` (data ownership), whose present-tense claim is not yet true.

## Context

50 JPA schemas serve 417 entities, broadly one schema per module. **Nine modules write into a schema they
do not own:**

| Module | Writes into | Entities | Assessment |
|---|---|---:|---|
| `incident` | `cyber` | 2 | Blocks separability — commercially material |
| `integrations` | `platform` | 6 | Integration state inside the kernel schema |
| `controlmap` | `evidence` | 1 | Owns no data of its own |
| `workflow_submission` | `workflow` | 1 | Probably one context split across two packages |
| `knowledge` | `kb` | 1 | Naming only |
| `governance` | `platform` | 1 | Kernel schema as a dumping ground |
| `signature` | `signature` + `platform` | 2 | Straddles two |
| `auth`, `attachment` | `platform` | 1 each | Acceptable — these *are* kernel |

A module that writes into a peer's schema cannot be migrated, backed up, exported, air-gapped, restored
or licensed independently. "Buy Incident Management" today implicitly ships `cyber`'s tables — and a
Saudi bank's DBA reviewing that purchase will ask why.

## Decision

Every module owns exactly one schema, named after its registry `code` (ADR-009). Only kernel packages
write to `platform`. No entity declares a schema owned by another module.

Where a foreign schema reflects a genuine single bounded context split across two packages
(`workflow_submission`/`workflow`, `controlmap`/`evidence`), **merge the packages** rather than move the
tables — the coupling is telling the truth about the boundary. Where the module is genuinely separate
(`incident`), move the tables: create the schema, `ALTER SCHEMA … TRANSFER`, and keep a view at the old
name for one release with a documented rollback recipe.

`incident` requires a product decision first: **is Incident Management a separate SKU, or part of Cyber?**
Decide by commercial intent, not code convenience. Merging into `cyber` is a legitimate answer — incident
response belongs to cyber operations in most GRC products.

## Alternatives

- **Accept and document:** tenable only under a suite-only licence model. If per-module sale is dropped,
  this ADR drops to hygiene.
- **Per-tenant database for every customer:** does not address ownership, and multiplies operational cost.
- **Schema-per-SKU rather than per-module:** simpler licensing story, but loses the module-level ownership
  that makes boundaries testable.

## Consequences

Schema moves are the highest-risk migration class in this codebase; each needs a compatibility view and a
rehearsed rollback. The enforcement test, by contrast, is nearly free and stops new violations
immediately — so it lands first, independent of any move.

## Proof

A test asserting every `@Entity`'s `@Table(schema=…)` equals its module's registry schema; per-module
schema dump/restore rehearsed for one Tier-A module end to end.
