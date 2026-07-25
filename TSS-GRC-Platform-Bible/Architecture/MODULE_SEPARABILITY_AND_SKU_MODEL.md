# Module Separability & SKU Model

**Purpose.** Answer one question with measured evidence: *can each module be sold, deployed and
administered separately — and if not, exactly what stands in the way?*

**Date:** 2026-07-25 · **Evidence:** `Appendix/MEASURED_BASELINE.md` · **Supersedes on measured facts:**
`Architecture/MODULE_INDEPENDENCE_AND_PACKAGING.md`, `Heatmaps/MODULE_INDEPENDENCE_MATRIX.md`.

---

## 1. Verdict up front

**The architecture is far more separable than the platform's own documents assume, and the blockers are
not where anyone expected them.**

| | |
|---|---|
| **Sellable standalone today** | **10 modules · 15,975 LoC** — kernel-only dependencies, tests present |
| **Sellable after *non-architectural* work** | **21 modules · 52,713 LoC** — dependencies already clean; missing **tests** and **permission catalogue**, not decoupling |
| **Genuinely coupled** | **13 modules · 78,311 LoC** — includes the four largest revenue modules |
| **Base platform (never a standalone SKU)** | **13 modules · 39,268 LoC** |

**The single most important finding in this document:** the **Saudi-differentiating modules are
architecturally the cleanest on the platform.** `sharia`, `openbanking`, `outsourcing`, `rrp`, `cpty`,
`regreporting`, `reginspect`, `fraud`, `opres`, `dataclass`, `nca_ecc` all have **zero peer-module
dependencies** — they need only the kernel. What blocks selling them is **zero tests** (F2-03) and
**role-name guards instead of catalogued permissions** (F2-05). Both are additive work with no
refactoring risk.

> **Strategic consequence.** The fastest route to a modular product line is *not* to decouple the big
> modules. It is to add tests and permission catalogues to eleven already-decoupled Saudi modules —
> turning the platform's differentiators into its first sellable SKUs, in the order of a quarter's work,
> with no architectural risk at all.

And one cheap architectural win compounds it: **promoting the export toolkit out of `reporting` into the
kernel (≈1 day) improves the separability of eight further modules, including `bcm` and `sust`** — see §5.

---

## 2. Method

Separability is scored on four measured axes, not opinion:

| Axis | Measure | Why it decides sellability |
|---|---|---|
| **Code coupling** | Distinct **non-kernel** peer modules imported | A module that imports a peer cannot compile, deploy or version without it |
| **Data ownership** | Does every `@Entity` write to the module's own schema? | Foreign-schema entities make independent migration, backup, export and residency impossible |
| **Administrability** | Are permissions catalogued (`hasPermission`) or hard-coded roles (`hasAnyRole`)? | A buyer who cannot define their own roles cannot administer what they bought |
| **Assurance** | Tests present, proportionate to write surface | Nothing may be sold standalone on a promise; a standalone SKU carries its own warranty |

**Kernel** (the base platform every SKU includes): `platform`, `iam`, `auth`, `admin`, `evidence`,
`attachment`, `signature`, `workflow`, `approvals`, `capa`, `intake`, `dashboard`, `integrations`.
Depending on the kernel is correct and is not counted against a module.

**Tiers.** **A** — sellable now. **B** — clean dependencies, needs tests and/or permissions.
**C** — genuine coupling or foreign schema; needs architectural work. **K** — kernel.

---

## 3. The separability matrix

### Tier A — sellable standalone today (10 modules)

| Module | LoC | Endpoints | Tests | Fan-in | Auth style | Note |
|---|---:|---:|---:|---:|---|---|
| `ethics` | 4,841 | 72 | 83 | 1 | `COMPLIANCE` | Whistleblowing / conduct — a **complete standalone product**; OneTrust sells this as a separate SKU |
| `audit_lc` | 3,177 | 50 | 58 | 2 | `AUDIT` | Internal-audit lifecycle — AuditBoard's core product category |
| `aml` | 2,165 | 14 | 47 | 0 | ⚠ ROLE | AML/sanctions — zero fan-in, fully isolated |
| `ccm` | 2,031 | 10 | 39 | 2 | ⚠ ROLE | Continuous control monitoring |
| `consumer` | 1,420 | 11 | 20 | 0 | ⚠ ROLE | Consumer-protection (SAMA) |
| `risk_lc` | 584 | 5 | 6 | 1 | `RISK` | Lifecycle add-on to Risk |
| `cyber_lc` | 506 | 5 | 11 | 0 | `CYBER` | Lifecycle add-on to Cyber |
| `regchange` | 444 | 5 | 6 | 0 | `COMPLIANCE` | Regulatory-change tracking |
| `privacy_lc` | 416 | 5 | 10 | 2 | `PRIVACY` | Lifecycle add-on to Privacy |
| `bcm_lc` | 391 | 5 | 6 | 0 | `BCM` | Lifecycle add-on to BCM |

Caveat: the five `*_lc` modules are **lifecycle add-ons**, commercially part of their parent SKU rather
than products. The genuinely standalone Tier-A products are **`ethics`, `audit_lc`, `aml`, `ccm`,
`consumer`** — and three of the five still need the permission conversion (F2-05) before a customer can
administer them.

### Tier B — clean dependencies; blocked on tests and/or permissions (21 modules)

**Group B1 — Saudi/financial-sector modules with ZERO peer dependencies and ZERO tests.**
*This group is the commercial opportunity.*

| Module | LoC | Endpoints | Tests | Auth | Blocker | Regulatory anchor |
|---|---:|---:|---:|---|---|---|
| `data_officer` | 2,373 | 37 | **0** | `PRIVACY` | tests | PDPL data-officer workspace |
| `sharia` | 2,234 | 21 | **0** | ⚠ ROLE | tests + perms | Sharia governance — **no competitor equivalent** |
| `openbanking` | 1,913 | 18 | **0** | ⚠ ROLE | tests + perms | SAMA Open Banking |
| `rrp` | 1,910 | 17 | **0** | ⚠ ROLE | tests + perms | SAMA recovery & resolution |
| `cpty` | 1,688 | 14 | **0** | ⚠ ROLE | tests + perms | Counterparty / credit concentration |
| `outsourcing` | 1,655 | 17 | **0** | ⚠ ROLE | tests + perms | **SAMA outsourcing** |
| `regreporting` | 1,628 | 17 | **0** | ⚠ ROLE | tests + perms | Regulatory reporting |
| `fraud` | 1,301 | 10 | **0** | ⚠ ROLE | tests + perms | Fraud risk |
| `reginspect` | 1,160 | 10 | **0** | ⚠ ROLE | tests + perms | Regulator inspections |
| `dataclass` | 988 | 13 | **0** | ⚠ ROLE | tests + perms | NDMO/SDAIA data classification |
| `opres` | 950 | 9 | **0** | ⚠ ROLE | tests + perms | Operational resilience |
| `nca_ecc` | 368 | 6 | **0** | `COMPLIANCE`,`CYBER` | tests | **NCA ECC** |
| `audit_eng` | 354 | 5 | **0** | `AUDIT` | tests | Audit engagements |
| `board_pack` | 353 | 5 | **0** | ⚠ ROLE | tests + perms | Board reporting |

**Total: 18,875 LoC · 199 endpoints · 0 tests.** No decoupling required for any of them.

**Group B2 — larger modules with 1–2 peer dependencies.**

| Module | LoC | Endpoints | Tests | Peer deps | Assessment |
|---|---:|---:|---:|---|---|
| `risk` | 8,955 | 101 | 147 | `policy`, `risk_lc` | Well tested; the `policy` edge is the only real question |
| `ndi` | 7,717 | 127 | **11** | `reporting`*, `risk` | *`reporting` edge is export-toolkit only (§5) → effectively 1 peer. **Assurance is the blocker** (F2-12) |
| `privacy` | 5,804 | 99 | 79 | `tprm` | PDPL suite; thin coverage for 62 writes |
| `scm` | 3,808 | 66 | **15** | `tprm` | SAMA supply-chain; 45 writes, 15 tests |
| `ai` | 3,758 | 35 | 48 | `reporting`* | *Export-toolkit only → **becomes Tier A** after §5 |
| `change_mgmt` | 2,022 | 30 | 36 | `incident` | Extraction already planned (CE-1) |
| `policy` | 1,774 | 27 | 39 | `compliance` | Fan-in 5 — widely depended upon |

### Tier C — genuine coupling or foreign schema (13 modules)

| Module | LoC | Peer deps | Real blocker | Fix |
|---|---:|---|---|---|
| `bcm` | 25,130 | `reporting`*, `tprm`, `cyber` | *Toolkit-only for `reporting` → 2 real peers | §5 then two ports |
| `sust` | 16,704 | `reporting`*, `risk`, `tprm` | Same → 2 real peers | §5 then two ports |
| `cyber` | 7,269 | `reporting`*, `policy`, `incident` | `incident` shares its schema | §5 + F2-06 decision |
| `socops` | 6,787 | `incident`, `reporting`*, `cyber` | *Cycle is toolkit-only | §5 removes the cycle |
| `compliance` | 5,659 | `policy`, `risk`, `privacy` | Genuine domain coupling | Ports |
| `tprm` | 5,399 | `scm`, `privacy`, `bcm` | Genuine; fan-in 5 | Ports |
| `reporting` | 5,107 | **6 peers, 74 edges** | God-consumer of peer internals | Port inversion (F2-08b) |
| `governance` | 1,857 | 5 peers | Aggregator + writes to `platform` schema | Ports + schema |
| `audit_committee` | 1,581 | **8 peers** | Highest fan-out per LoC | Ports |
| `incident` | 1,585 | — | **Writes into `cyber` schema** | F2-06 decision: separate SKU or merge into `cyber` |
| `workflow_submission` | 494 | — | Writes into `workflow` schema | Merge into `workflow` |
| `controlmap` | 384 | — | Writes into `evidence` schema | Merge into `evidence` or `compliance` |
| `knowledge` | 355 | — | Writes into `kb` schema | Rename schema |

### Tier K — base platform (13 modules · 39,268 LoC)

`platform` (fan-in **45** — correctly the kernel), `integrations`, `workflow`, `admin`, `iam` (fan-in 9),
`evidence` (fan-in 7), `auth`, `signature`, `intake`, `attachment`, `approvals`, `capa` (fan-in 6),
`dashboard`. **Every SKU includes this.** It is the licensable base, and it is where entitlement,
identity, evidence, audit ledger, workflow and export belong.

⚠ Three kernel modules need attention as kernel: `integrations` (7,568 LoC, **ROLE** guards, 6 entities
in the `platform` schema), `attachment` (0 tests), `dashboard` (0 tests, 0 entities).

---

## 4. What actually blocks per-module sale — ranked by modules unblocked per unit of effort

| Rank | Blocker | Modules affected | Effort | Architectural risk |
|---:|---|---:|---|---|
| **1** | **Entitlement subsystem exists but is wired to 8.6% of controllers** (F2-04) | **All 58** | **S–M (days)** | **Very low — the mechanism is built, proven on `socops`, and fail-closed** |
| **2** | **Zero tests** (F2-03) | 19 (14 in Tier B1) | L, parallelisable | **None** — purely additive |
| **3** | **Role-name guards** (F2-05) | 21 | S per module | Low — behaviour-preserving refactor |
| **4** | **Export toolkit in `reporting`** (F2-08a) | **8** | **S (~1 day)** | **Very low** — package move |
| **5** | Foreign schemas (F2-06) | 4 material | M per module | Medium — schema migration |
| **6** | God-consumer coupling (F2-08b) | 6 | L | Medium |

**Read this table as the plan.** Ranks 1–4 unblock the entire Tier A + Tier B set — **31 modules,
68,688 LoC — with essentially no architectural risk.** Ranks 5–6 are needed only for the specific Tier-C
modules a customer actually wants standalone.

---

## 5. The one-day win: promote the export toolkit

Eight modules (`bcm`, `sust`, `iam`, `socops`, `ndi`, `cyber`, `ai`, `admin`) import `reporting`.
Every one of the 25 inbound edges imports **only** export utilities — `PdfTheme`, `PdfFonts`,
`XlsxStyler`, `ExportNaming`, `RegisterExport`, `TabularExportRequest`, `TabularExportService`.
None imports reporting *logic*.

```
module/reporting/service/{PdfTheme,PdfFonts,XlsxStyler,ExportNaming,RegisterExport,TabularExport*}
        →  module/platform/export/
```

| Effect | Value |
|---|---|
| Inbound edges to `reporting` removed | **25 → 0** |
| Apparent `socops → reporting` cycle | **eliminated** |
| `ai` | Tier B → **Tier A** (kernel-only) |
| `bcm`, `sust`, `cyber`, `socops` | 3 peer deps → **2** |
| `ndi` | 2 → **1** |
| Behaviour / schema change | **none** — pure package move, compiler-verified |

Bilingual RTL export quality (shaping, mirroring, the bundled Amiri font) is a genuine platform asset
used by 22 generators. It belongs in the kernel on merit, not only for graph hygiene.

---

## 6. Proposed SKU catalogue

Derived from the tiers, not invented. Every SKU includes the **Base Platform**.

| SKU | Modules | Status | Comparator |
|---|---|---|---|
| **Base Platform** *(mandatory)* | Tier K: identity, RBAC, entitlement, evidence, audit ledger, workflow, committees, exports, branding, admin | Ships today | ServiceNow Now Platform; Archer Platform |
| **Risk Management** | `risk`, `risk_lc`, `ccm` | Ready (tests strong) | Archer ERM; MetricStream ERM |
| **Compliance & Policy** | `compliance`, `policy`, `controlmap`, `regchange`, `nca_ecc` | Near-ready; `controlmap` schema, `nca_ecc` tests | MetricStream; OneTrust GRC |
| **Internal Audit** | `audit_lc`, `audit_eng`, `audit_committee` | `audit_lc` ready; `audit_committee` coupled | **AuditBoard's core** |
| **Cyber & SOC** | `cyber`, `cyber_lc`, `socops`, `incident` | Coupled set — decide `incident`/`cyber` (F2-06) | ServiceNow SecOps |
| **Resilience (BCM)** | `bcm`, `bcm_lc`, `opres` | `bcm` strong (395 tests); `opres` 0 tests | Fusion; Archer BC&IT |
| **Privacy (PDPL)** | `privacy`, `privacy_lc`, `data_officer`, `dataclass` | `privacy` ready; other three 0 tests | **OneTrust's core** |
| **Third-Party & Supply Chain** | `tprm`, `scm`, `outsourcing`, `cpty` | `tprm` ready; `outsourcing`/`cpty` 0 tests | OneTrust TPRM; Archer |
| **Sustainability & Green Finance** | `sust` | Strong (200 tests); 2 peer deps | No direct Arabic comparator |
| **Ethics & Conduct** | `ethics`, `consumer` | **Ready today** | OneTrust Ethics |
| **Financial-Crime** | `aml`, `fraud` | `aml` ready; `fraud` 0 tests | Specialist vendors |
| **Saudi Financial-Sector Regulatory** ⭐ | `openbanking`, `rrp`, `regreporting`, `reginspect`, `sharia` | **All 0 tests — and all architecturally clean** | **No comparator exists** |
| **AI Governance** | `ai` | Tier A after §5 | Emerging category |
| **Change Enablement** | `change_mgmt` | Extraction in progress (CE-1) | ServiceNow ITSM |

⭐ **The highest-margin, least-contested SKU is the one with zero tests.** That is the register's
central commercial message: the moat exists in code and is unproven in CI.

---

## 7. Target architecture for separability

Four rules, each enforced by a test rather than a document — because an unenforced rule is what produced
the current state:

| # | Rule | Enforcement | ADR |
|---|---|---|---|
| **R1** | A module may import the **kernel** freely and a **peer only through a port interface it owns**. No cycles. | Ratcheted boundary test over all 58 packages | **ADR-012** |
| **R2** | Every `@Entity` writes to its own module's schema; only kernel packages write to `platform`. | Test comparing `@Table(schema=…)` against the module registry | **ADR-011** |
| **R3** | Every write endpoint is guarded by `hasPermission(MODULE, ACTION)`. `hasAnyRole` is forbidden in `module/**`. | Tightness ratchet (extends the coverage ratchet at 21) | **ADR-010** |
| **R4** | A module exists only if it is in the **module registry** with an owner, SKU, maturity tier, schema, permission prefix and test floor. | CI check: registry ↔ filesystem must agree | **ADR-009** |

### Reference shape for a separable module

```
module/<name>/
  api/          — DTOs + controller: the published contract
  domain/       — entities, state machine (no Spring, no JPA in the state machine)
  service/      — application logic
  repository/   — persistence, own schema only
  spi/          — port INTERFACES this module needs from peers (owned here)
  package-info.java — registry: owner, SKU, tier, schema, permission prefix
integration/<name>/ — ADAPTERS implementing another module's ports (outside both modules)
```

Adapters live **outside** both modules, so neither depends on the other — the dependency inverts onto
an interface the consumer owns. This is the shape the `change_mgmt` extraction (CE-1) is piloting; it
should become the platform-wide standard **only after** the pilot proves it, not before.

---

## 8. What this does *not* claim

- **Separable ≠ independently deployable.** Every tier here is about a *modular monolith*: one process,
  one database, clean internal boundaries. Independent *deployment* additionally requires the schema
  work (F2-06) and a service boundary — out of scope for this document and not currently justified.
- **Tier A does not mean production-ready.** It means *architecturally* separable with tests present.
  `aml`, `ccm` and `consumer` still carry role-name guards, so a buyer could not administer them.
- **The tiering is structural, not functional.** No claim is made about whether any module's *features*
  are complete or competitive; that is `Modules/PRODUCT_DESIGN_SPECIFICATION.md`'s job, and Baseline §8
  states what was not measured.
