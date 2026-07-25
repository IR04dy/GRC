# ADR-012 — Boundary Enforcement and Consumer-Owned Ports

**Status:** Proposed
**Date:** 2026-07-25
**Makes enforceable:** `ADR-001` (whose Proof section requires "zero forbidden imports" — a test that
does not exist).

## Context

**734** direct cross-module imports and **14** bidirectional pairs (both figures independently confirmed
against `ADR-001`). Nothing in CI forbids any module importing any other, so the modular monolith is a
package *convention*, and an unenforced convention is a preference.

Measurement separates two problems that look identical in the graph:

**(a) A misplaced kernel utility.** Eight modules import `reporting` — `bcm`, `sust`, `iam`, `socops`,
`ndi`, `cyber`, `ai`, `admin`. Inspection of all 25 inbound edges shows they import **only** export
utilities: `PdfTheme`, `PdfFonts`, `XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExport*`. None
imports reporting *logic*. This also manufactures the apparent `socops → reporting` cycle.

**(b) A genuine god-consumer.** `reporting` (5,107 LoC, 2 entities) imports 8 peers over 74 edges,
reaching directly into their **repositories and entities** — `RiskRepository`, `Risk`, `KriDefinition`,
`KriMeasurement`, and equivalents in `compliance` and `change_mgmt`. `audit_committee` (1,581 LoC) imports
10 modules; `governance` imports 8.

Excluding the kernel, only **5** genuine peer cycles exist: `bcm↔tprm`, `compliance↔policy`,
`privacy↔tprm`, `reporting↔sust`, `scm↔tprm`.

## Decision

**Three rules.**

1. `module → platform` (kernel) is always permitted.
2. `module → module` only through a **port interface owned by the consumer**, with the adapter placed
   **outside both modules** (`integration/<consumer>/`), so neither module depends on the other — the
   dependency inverts onto an interface the consumer owns.
3. **No cycles** between non-kernel modules.

**Enforced as a ratchet, not a gate.** Record today's per-module edge counts; CI fails only on an
*increase*. This is the mechanism that took authorisation coverage from 62 to 21 in one programme: no
big-bang refactor, and the graph improves with every touch.

**Sequence — cheapest first.** Promote the export toolkit to `module/platform/export` before anything
else. It is a compiler-verified package move of six classes, with no behaviour or schema change, and it
removes all 25 inbound edges to `reporting`, deletes the apparent cycle, and moves `ai` to kernel-only
(separability Tier A). Roughly one day improves eight modules. Only then invert `reporting` with
`ReportDataPort` per source, then `audit_committee`, then `governance`.

## Alternatives

- **Big-bang boundary gate at zero violations:** rejected. 734 edges cannot be cut at once; the test would
  be permanently red and therefore ignored.
- **Java Module System / plugin classloaders:** deferred, per `ADR-001`, until package contracts are
  stable. Enforcement by test achieves the same discipline today at no runtime risk.
- **Extract `reporting` into a separate service over a read model:** the right long-term answer and a
  large one. The port inversion is step one toward it regardless, and the existing `DomainEvent`
  infrastructure makes an event-fed read model a natural follow-on.
- **Allow peer imports of DTOs but not entities/repositories:** a pragmatic middle rule worth considering
  as an interim ratchet target — it captures most of the benefit for much less work.

## Consequences

Ports add interfaces and files, and the toolkit promotion touches imports in eight modules in a single
commit — wide but shallow, with the compiler proving completeness. The ratchet form means the rule is
adoptable on day one without pausing feature work.

## Proof

Ratcheted boundary test green and monotonically decreasing; zero inbound edges to `reporting` after the
toolkit promotion; zero non-kernel cycles; `ChangeModuleBoundaryTest` (the CE-1 pilot) generalised to all
58 packages.
