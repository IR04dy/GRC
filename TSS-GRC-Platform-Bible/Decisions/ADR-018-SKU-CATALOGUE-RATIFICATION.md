# ADR-018 — One Ratified SKU Catalogue: 15 Products, a Mandatory Base, and Content on Its Own Clock

**Status:** Accepted — closes **F2-35**, unblocks **W2-0** and **F2-04** (all of Wave 2), opens **F2-57**
**Date:** 2026-07-30
**Relates to:** `ADR-013` (per-module SKUs on one deployment — this ADR supplies the catalogue that one
assumed), `ADR-009` (module registry), `ADR-010` (permission-only authorisation), `ADR-011` (schema
ownership), `ADR-017` D-1 (board governance and access governance are paid)
**Decision owner:** Business. Decided by the engineer on the owner's 2026-07-30 instruction to
resolve the remaining owner-blocked items **"based on the best practice and the market leaders and
the suitable to Saudi market"** — the same delegation that produced ADR-017, discharged to the same
standard: every SKU anchored to a named regulator instrument and a named comparator, or explicitly
noted as having none.
**Artefact:** [`Product/SKU_CATALOGUE.md`](../Product/SKU_CATALOGUE.md) is the ratified list. This ADR
is the reasoning; that file is the authority.

---

## Context

ADR-013 decided *that* the platform sells per-module SKUs on one deployment. It did not decide *which*
SKUs — and three documents then answered that question differently, without any of them knowing they
were in a fight:

| | Source | Answer | Grain |
|---|---|---|---|
| **A** | `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6 | Base + **13** | Derived from separability tiers |
| **B** | `Product/PRODUCT_STRATEGY_PACKAGING_AND_SALES.md` | **18** packages | Derived from buyer outcomes |
| **C** | `LicensingService.KNOWN_MODULES` | **20** codes, self-described as "the source of truth" | Derived from whatever migration added a code |

The disagreement was not cosmetic. **W2-0's grandfather backfill and F2-04's annotation sweep both
consume the catalogue, and its content differs about threefold by grain** — so both were unwritable.
Worse, one conflict was commercial: A and §6c placed committees, board packs, signatures and access
governance in the **free** mandatory Base; B sold `GOVERNANCE` and `ACCESS_GOV` as **paid**. And
**seven packages appeared in no catalogue at all**, including `ndi` at 7,717 LoC and 127 endpoints —
the SDAIA/NDMO differentiator, unsold because nobody noticed it was homeless.

---

## Decision

**One catalogue: a mandatory Base Platform, 15 product SKUs, and regulatory content packs on an
independent clock.** Full contents, per-SKU regulator anchor, comparator and readiness debt in
[`Product/SKU_CATALOGUE.md`](../Product/SKU_CATALOGUE.md). Seven binding rules travel with it (R1–R7
there); the three that carry the most weight:

- **R1 — total and disjoint.** Every package on disk belongs to exactly one of {Base, a SKU,
  explicitly unassigned}, checked against the filesystem. F2-35 existed because seven packages were
  unhomed; a rule that permits homelessness recreates it.
- **R3 — no retirement without a backfill.** A code is retired only in a migration that grants its
  successor to every organisation holding the predecessor, with a `THROW` anti-vacuity check.
- **R4 — Base is not licensable.** No licence row, no `@RequireModule`, no tier. This is what makes
  the existing `EVIDENCE`/`REPORTING`/`AUDIT_LOG`/`PLATFORM_ADMIN` rows harmless instead of wrong.

---

## Why this grain, on market and Saudi evidence

**Leaders sell 6–9 products and package them into few offers.** ServiceNow IRM: Policy *and*
Compliance Management (one product), Risk Management, Audit Management, Vendor Risk, BCM, Privacy,
Operational Resilience — with SecOps as a separate product and the Now Platform mandatory beneath.
Archer: eight solution areas over the Archer Platform, with regulatory content on subscription.
MetricStream: seven product lines. OneTrust: four clouds. Diligent sells board governance as its own
product, per director seat. SailPoint and Saviynt sell identity governance as its own product, per
identity, and never bundle it free.

Three consequences follow, and each overrides one of A, B or C:

1. **B's four splits are rejected.** `POLICY` apart from `COMPLIANCE` is a grain no leader sells — a
   policy with no obligation to satisfy is a document manager, and compliance without policy cannot
   evidence. Same for `REGULATORY` (ServiceNow puts regulatory change *inside* Policy & Compliance)
   and for `SAMA_RESILIENCE`, which B assembled out of outsourcing + operational resilience +
   recovery planning and then had to mark "optional TPRM/BCM". **Five of B's 18 carry an
   "optional X" dependency — that is the over-splitting tell**, and it is a sales problem disguised
   as a catalogue.
2. **B's three additions are adopted, two of them because ADR-017 D-1 already forced it.**
   `BOARD` and `ACCESS_GOV` are **paid** — decided in ADR-017 on the grounds that board governance is
   a standalone category everywhere and access governance is never bundled free, and that both are
   *regulator-mandated in KSA* (CMA CG + SAMA CG for banks; NCA ECC 2-2 + SAMA CSF 3.3.5), which is a
   stronger commercial position than these products enjoy elsewhere. So A's Tier-K definition is
   **amended**: committees, board packs and access governance leave the free Base. `DATA_AI_GOV` is
   adopted and *expanded* — it is where the homeless `ndi` goes.
3. **A's SKU grain wins where it isn't overridden**, because A derived its list from measured
   coupling and A's comparators per row survive scrutiny. The result is arithmetically A's 13, minus
   Financial-Crime (merged into `ETHICS`), plus the `SOCOPS` split, plus `BOARD` and `ACCESS_GOV`:
   **15**.

**Saudi fit is what decides the two hardest calls.**

- **`SOCOPS` splits from `CYBER`.** ServiceNow sells SecOps separately from IRM, the buyers differ
  (CISO-governance vs SOC-operations), and the regulator instruments differ (NCA ECC 2-x governance
  vs SAMA CSF 3.3 operations). Decisive tiebreak: **`SOCOPS` is the only code the platform actually
  enforces** — 22 live `@RequireModule` annotations and licence rows in all three tenants. Merging it
  into `CYBER` would break the one working entitlement boundary to satisfy a paper preference.
- **`SAUDI_FS` stays whole and stays a SKU with no comparator.** `sharia`, `openbanking`, `rrp`,
  `cpty` sell together because they share one buyer: a SAMA-regulated institution. `cpty` moves here
  from A's Third-Party grouping, because counterparty credit exposure is a treasury/credit-risk
  instrument under SAMA's large-exposure rules, not a vendor-management one.
- **Fifteen SKUs, three bundles.** The catalogue's §6 defines Financial Institution / Government &
  Public Entity / Listed Company, because in KSA the *regulator* determines the shopping list: SAMA
  drives one basket, NCA + SDAIA/NDMO a different one, CMA a third. This is the ServiceNow
  Standard/Professional/Enterprise pattern applied to Saudi buying centres, and it is why a
  15-product catalogue is not a 15-decision sale.

---

## What measurement changed about the decision

Three things were checked rather than reasoned, and two of them changed an answer.

1. **`governance` is not the committee engine.** Both A and B implied it was. It is
   `governance/pivot` (risk × control × finding) plus `governance/threelines` (IIA Three Lines role
   mapping) — 13 files, no sellable surface. The committee engine lives in **`admin/committee`**, 51
   files inside a kernel package. Had this gone unchecked, `BOARD` would have been sold as a package
   that contains none of what it sells.
2. **Two SKUs have boundaries that are not package boundaries.** `BOARD` = `board_pack` +
   `admin/committee`; `ACCESS_GOV` = `iam/governance` and **no top-level package at all**. That is a
   direct consequence of ADR-017 D-1 and it was previously unrecorded. `@RequireModule` is a
   class/method annotation, so it expresses this perfectly — but **F2-01's module registry, keyed on
   package, cannot**, and neither can ADR-011's schema-ownership rule. Filed as **F2-57**; R6 states
   the constraint so F2-01 is not designed wrong.
3. **Exactly one code retirement needs a backfill, and it is measured, not guessed.** Of the five
   codes merging into a sibling SKU, four are safe because org 1 already holds both sides
   (`POLICY`→`COMPLIANCE`, `REGULATORY`→`COMPLIANCE`, `SCM`→`TPRM`, `INCIDENT`→`SOCOPS`). One is not:
   **`NDI`→`DATA_AI_GOV`, org 1 holds the predecessor and no successor.** W2-0 must grant it in the
   same migration. Asserting "renames are safe" would have blacked out the platform's largest
   unsold module for its only tenant that has it.

---

## Consequences

**Unblocked.**
- **W2-0** (grandfather backfill) can be written: 15 codes, the §5 mapping, one measured backfill,
  and R3/R4 as the migration's own assertions.
- **F2-04** (the `@RequireModule` sweep) has a target list. 22 of ~2,160 endpoints are gated today.
- **F2-01** (module registry) has its SKU column defined for all 58 packages — and, via R6, knows it
  must key on entitlement unit rather than package.
- **F2-53** dissolves as a catalogue problem: `knowledge` is Base, which is why `KB` was never a
  catalogued module. It remains a permissions problem.

**Newly visible, and worse than before it was named.**
- **`reporting.BoardPackService` and the two `board-pack.*` export endpoints are `BOARD` content
  inside a Base package.** F2-08a's toolkit lift must separate them or `BOARD` is unenforceable at
  its single most visible surface — the thing the buyer opens.
- **`SAUDI_FS`, the SKU with no competitor, has zero tests across all four packages.** Ratifying it
  as sellable makes that a launch blocker instead of a curiosity.
- **`datagov` is recorded as unassigned**, not filed under Base. F2-01 must fail on it until it is
  deleted or filled. Quietly filing packages is how the other seven went missing.

**Costs accepted.**
- `AUDIT_LC` remains a commercial code that reads like a package name (R2 forbids the cosmetic
  rename — renaming un-licenses every tenant holding it). Display names carry the shelf name.
- 15 SKUs is more than most comparators carry, and the bundles are load-bearing rather than
  decorative. If a real deal shows buyers cannot navigate it, **consolidate bundles, not SKUs** —
  merging SKUs after tenants hold licence rows costs a migration each time.
- `PDPL` sits outside the Financial Institution bundle. That is the catalogue's most contestable
  sales line and it is flagged as such in place.

**Not decided here.** Every price. Pricing stays owner-only; ADR-013 said so and this ADR does not
reopen it. This file decides what is sellable and what is mandatory, and sets no number.
