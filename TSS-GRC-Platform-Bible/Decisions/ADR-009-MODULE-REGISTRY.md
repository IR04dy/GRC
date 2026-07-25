# ADR-009 — Module Registry as a Build Artefact

**Status:** Proposed
**Date:** 2026-07-25
**Supersedes:** nothing. **Depends on:** `Modules/SOURCE_MODULE_INVENTORY.md` (verified accurate).

## Context

The platform has **58** backend module packages. `Modules/SOURCE_MODULE_INVENTORY.md` documents all 58
accurately — its per-module file counts reproduce exactly under independent measurement. Yet
`CLAUDE.md`, `PLATFORM_ARCHITECTURE_PACK.md` and every remediation plan still operate on **24**, so 34
packages fall outside the scope of every cross-cutting programme: the permission catalogue, the test
bar, boundary enforcement, i18n coverage and the datasheet.

The inventory is correct and **nothing consumes it**. `module/datagov/` — one 18-line `package-info.java`,
no code — is listed as a capability. An accurate document that no control reads is not governance.

## Decision

Promote the inventory from prose to `module-registry.yaml`, a **build artefact**. Each entry declares:
`code`, `package`, `owner`, `sku`, `maturity` (`PLANNED│ALPHA│BETA│GA`), `separability_tier` (A/B/C/K),
`schema`, `permission_prefix`, `test_floor`, `fe_route_root`, `docs`.

CI fails when: a `module/*` package has no registry entry; a registry entry names a package that does not
exist; a declared `test_floor` is unmet; a declared `schema` disagrees with the module's `@Table` values;
or any datasheet or instruction file states a module count that disagrees with the registry.

The datasheet, licence matrix, module scorecard and frontend navigation are **generated from** the
registry — never hand-maintained.

## Alternatives

- **Keep the Markdown inventory:** rejected. An accurate Markdown inventory already exists and did not
  prevent a 40% understatement of the product. Prose is not a control.
- **Derive the list at runtime from Spring bean scanning:** rejected. It describes what exists, not what
  is intended, so it can never fail a build.
- **Consolidate 58 → ~20 commercial packages first:** attractive, and `SOURCE_MODULE_INVENTORY.md`
  already proposes target owners — but a large refactor that does not itself create the missing control.
  Do it *after* the registry, using the registry to track it.

## Consequences

One mandatory step per new module, and failing builds during adoption — which is the point: an invisible
omission becomes a cheap, immediate, visible failure. Every commercial artefact becomes true by
construction.

## Proof

Registry ↔ filesystem parity test; `datagov` either deleted or marked `PLANNED` and absent from
generated capability lists; the generated datasheet lists 57 implemented modules; no document states a
module count that the registry contradicts.
