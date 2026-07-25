# ADR-013 — Per-Module SKUs on One Deployment, Enforced by Entitlement

**Status:** Accepted — closes **D-3**, and dissolves **D-4** and **D-6** as corollaries
**Date:** 2026-07-25
**Relates to:** `ADR-001` (modular monolith), `ADR-002` (kernel + installable packages), `ADR-009` (module
registry as build artefact), `ADR-012` (boundary enforcement)
**Decision owner:** Business. Recorded here as the leader-aligned default on the owner's instruction to
adopt best practice rather than wait; the *enforcement model* is decided, **pricing is explicitly not**.

## Context

The question as filed was "per-module SKUs or suite-only?". Measurement shows that framing was wrong:
**the platform already made this decision in code and then failed to apply it.**

What exists today, verified in the running system:

| Component | State |
|---|---|
| `@RequireModule("CODE")` annotation + `LicenseGuardAspect` | Real. Method- and class-level, Spring-`@Transactional`-style resolution, **fail-closed** when no tenant is in scope |
| `LicensingService` | Real. Caffeine-cached, validity-window aware (`valid_from`/`valid_to`), tier-validated (`FREE`/`PRO`/`ENTERPRISE`), audited, cache-evicting on admin patch |
| `platform.organisation_licenses` | Real. 22 rows. `module_code`, `tier`, `seat_count`, `valid_from`, `valid_to`, `enabled` |
| JWT | Stamps `enabledModulesFor(org)` at login so the SPA hides unlicensed navigation without a round trip |
| **Applied to** | **22 controllers — all of them in `socops`.** One package of 58 |

So of ~2,160 endpoints, roughly **22 are entitlement-gated and the rest are free with any login**. The
mechanism is not missing; the *coverage* is missing. Three further measured facts constrain the decision:

1. **The SKU catalogue is a hard-coded `Set.of`.** `LicensingService.KNOWN_MODULES` holds 20 codes and its
   own comment calls itself "the source of truth". Adding a SKU therefore requires a recompile and a
   redeploy — which contradicts `ADR-009` and makes per-tenant commercial packaging a developer task.
2. **The 20 codes do not map onto the 58 packages.** `PDPL`, `POSTURE`, `AUDIT_LC`, `SOCOPS` are licence
   codes; `privacy`, `privacy_lc`, `data_officer`, `dataclass` are packages. Nothing reconciles the two,
   so "which module did the customer buy" currently has no answerable definition.
3. **Orgs 2 and 3 hold exactly one licence row each (`SOCOPS`).** Org 1 holds 20. Enforcing
   `@RequireModule` broadly *today* would black out two of three tenants — the guard is fail-closed and a
   missing row means "not licensed", not "not restricted".

`seat_count` is NULL on all 22 rows and nothing reads it.

## Decision

**Per-module SKUs, one deployment, one database, entitlement enforced at the module boundary by the
mechanism that already exists.** Not suite-only. Not separate deployments per module.

Six parts:

1. **A mandatory Base Platform SKU.** Tier K (identity, RBAC, entitlement, evidence, audit ledger,
   workflow, committees, exports, branding, admin) is always licensed and never separately saleable. No
   business SKU functions without it.
2. **The SKU catalogue moves from `Set.of` into the module registry** (`ADR-009`), as data, with the
   package→SKU mapping explicit. A SKU is then a commercial view over a set of packages — which is what
   dissolves D-4 (below).
3. **`@RequireModule` is applied at the class level to every business controller**, module by module,
   with the ratchet counting *un-entitled business endpoints* downward. Kernel controllers are not
   annotated: they are in the mandatory SKU, so a guard there could only ever produce a false negative.
4. **A grandfather backfill migration first.** Every existing tenant receives an enabled licence row for
   every SKU it currently uses, before any annotation lands. Sequence is not optional — reversed, it is
   an outage.
5. **Entitlement is enforced regardless of role.** No bypass. See D-6 below.
6. **Seats are recorded but not enforced** until a decision exists on what a seat means here. Recording
   an unenforced number is honest; enforcing an undefined one is not.

## Why this, with reference to the comparators

Every comparator in this market sells per-use-case on a shared kernel, and the convergence is not
fashion — it follows from how GRC is bought.

| Vendor | Packaging | What it confirms |
|---|---|---|
| **ServiceNow** | Now Platform + separately-licensed IRM applications (Policy & Compliance, Risk, Audit, Vendor Risk, BCM, Privacy) | Per-application licensing, **one instance**. Entitlement by subscription + plugin activation, not deployment |
| **Archer** | Archer platform + use-case packs (IT & Security Risk, Regulatory & Corporate Compliance, Audit, Third Party, Business Resiliency) | "Use case", not "package", is the unit of sale — a commercial view over shared objects |
| **MetricStream** | M7 platform + product lines (CyberGRC, ESG, Third-Party, Internal Audit, Policy & Compliance) | Same shape again, independently arrived at |
| **OneTrust** | Modules (Privacy Automation, Consent, GRC & Security Assurance, ESG, Ethics) licensed individually on a single tenant | Land-and-expand from one pain — the Saudi PDPL entry motion exactly |
| **Microsoft Purview** | Per-workload SKUs bundled into E5 Compliance; shared substrate; per-user assignment | Bundling and per-module coexist; a bundle is a discount, not an architecture |
| **Salesforce** | Per-cloud SKUs plus permission-set licences gating features inside one org | Entitlement is a *feature-level* runtime check in a shared runtime — the `@RequireModule` shape |
| **Atlassian** | Genuinely separate products (Jira, Confluence, JSM) — but a shared cloud site for identity and billing | The apparent counterexample proves the rule: even the most product-separated leader keeps a common kernel |

Three reasons this is right *for this product specifically*:

- **The cross-module link is the differentiator.** A risk linked to a control linked to evidence linked to
  a policy linked to a committee resolution is what this platform has and a point solution does not.
  Separate deployments destroy exactly that, so the modularity must be commercial and logical, never
  physical.
- **Saudi buyers enter through one obligation.** PDPL, NCA ECC, SAMA CSF, or CMA/TCFD disclosure — one
  mandate, one budget line, one department. Suite-only pricing loses the entry and therefore the
  expansion.
- **One runtime is the TCO argument for the mid-market.** The regional alternative to this platform is
  three point solutions and a spreadsheet reconciling them.

## Corollaries — D-4 and D-6 are answered by this, not separately

**D-4 ("is `incident` a separate SKU or part of Cyber?") dissolves.** A SKU is a commercial view over a
set of packages, so the question stops being architectural. `incident` joins the **Cyber & SOC** SKU;
the package stays where it is and does not move schemas. Precedent: ServiceNow sells Security Incident
Response inside SecOps while it remains a distinct application. Package boundaries answer to `ADR-011`
and `ADR-012`; SKU boundaries answer to the buyer. Conflating them is what made this look like a
blocking decision. **The `cyber`-schema migration (F2-06b) is therefore not gated on D-4** and can be
judged purely on `ADR-011`.

**D-6 ("does `PLATFORM_ADMIN` bypass entitlement?") answers: no, never — and the question dissolves too.**
Entitlement is a *commercial* control, not a security control, and the two must not share a bypass. The
plan's earlier recommendation ("no for business modules, yes for platform administration") reaches the
right outcome by the wrong route: platform administration lives in the **mandatory Base Platform SKU**,
which is always licensed, so there is nothing there to bypass. A single rule — no role bypasses
entitlement — is simpler, is what Salesforce does (a sysadmin cannot use an unlicensed feature), and
avoids the failure mode where a tenant super-admin quietly unlocks unpaid modules and the vendor
discovers it at renewal. `PLATFORM_ADMIN`'s authorisation bypass in `ModulePermissionEvaluator` is
unaffected; it is a different question.

## Alternatives considered

- **Suite-only.** Rejected. Simplest to build and it is what the code does today by omission, but it
  forfeits the land-and-expand motion every comparator runs, and it wastes an entitlement subsystem that
  is already built, cached, audited and fail-closed. It would also make the strongest asset —
  the Saudi financial-sector pack, which has no comparator — unsellable on its own.
- **Separate deployments per module.** Rejected, firmly. No comparator does it. It multiplies operations
  by the number of modules sold, and it destroys the cross-module graph that is the differentiator.
  `ADR-001` already rejected service extraction on the same evidence.
- **Entitlement in the JWT only, no server-side guard.** Rejected. The JWT claim is already used to hide
  navigation, which is a UX affordance; treating it as the enforcement point makes the licence
  client-side and therefore advisory. Keep both: claim for UX, aspect for truth.
- **Per-endpoint entitlement annotations.** Rejected as the default. Class-level on the controller is one
  decision per file instead of one per method, and the aspect already resolves method-over-class for the
  rare exception.

## Trade-offs accepted

- Applying `@RequireModule` across 57 packages is mechanical but wide, and every miss is a *false open*
  (revenue leak) while every over-application is a *false closed* (customer outage). The grandfather
  backfill plus a downward ratchet is the mitigation; the ordering risk is real and stated.
- Moving `KNOWN_MODULES` into data removes a compile-time check. The registry CI check (`ADR-009`) has to
  replace it, or unknown codes become a runtime surprise.
- A shared runtime means a noisy-neighbour tenant affects licensees of unrelated modules. Accepted: it is
  the same trade every comparator except Atlassian makes.

## Consequences

- Unblocks **F2-04**, **F2-05** and **F2-06** — a third of Wave 2, which is why this was the priority
  decision.
- Adds work not previously scoped: the grandfather backfill migration, the registry catalogue move, and
  an entitlement-coverage ratchet. Roughly the size of the annotation sweep itself.
- The SKU catalogue in `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6 becomes **ratified** rather
  than proposed, with `incident` fixed inside Cyber & SOC.
- **Reversal cost:** low while only `socops` is annotated; rising with each module swept. If the business
  chooses suite-only later, the annotations become inert by giving every tenant every licence row — one
  `INSERT`, no code change. Worth stating plainly: this decision is cheap to unwind, which is a reason to
  take it now rather than keep a third of the plan blocked.

## Proof obligations

1. An **entitlement-coverage ratchet**: count of business-module controllers without `@RequireModule`,
   monotonically decreasing, with the enumerated floor recorded the way the authorisation ratchet is.
2. A **registry ↔ licence-catalogue test**: every SKU code in the registry is grantable and every
   grantable code maps to at least one package.
3. A **grandfather test**: for each existing tenant, every SKU it has data in is licensed — run *before*
   the first annotation lands.
4. A **live probe**: a tenant without a licence receives `ModuleDisabledException` (not 403, not 500) and
   the SPA hides that module's navigation for the same login.
