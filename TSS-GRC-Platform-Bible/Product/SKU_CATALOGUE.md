# SKU Catalogue — RATIFIED

> **This file is the single source of truth for what the TSS GRC Platform sells.**
> Ratified 2026-07-30 by [ADR-018](../Decisions/ADR-018-SKU-CATALOGUE-RATIFICATION.md), closing
> finding **F2-35**. It supersedes three earlier lists that disagreed with each other:
>
> | Superseded | What it was | Status |
> |---|---|---|
> | `Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md` §6 | Base + 13, derived from the separability tiers | **Superseded** — grain adopted, contents corrected |
> | `Product/PRODUCT_STRATEGY_PACKAGING_AND_SALES.md` §"Sellable packages" | 18 packages | **Superseded** — 4 splits rejected, 3 additions adopted |
> | `LicensingService.KNOWN_MODULES` | 20 code-shaped strings, self-described as "the source of truth" | **Demoted** — it is a *legacy code registry*, not a catalogue. §5 maps every code to a destination |
>
> Nothing may add a SKU, rename a code or re-home a package by editing another document.
> Amend **this** file and the ADR, together, in one commit.

---

## 1. Shape of the commercial model

Three axes, which is what every comparable vendor actually does — one platform fee, a set of
product subscriptions, and content sold on its own clock.

| Axis | What it is | Count | Comparator precedent |
|---|---|---|---|
| **Base Platform** | Mandatory. Identity, RBAC, entitlement, evidence, audit ledger, workflow, approvals, e-signature, exports, common-control library, dashboards, integrations, knowledge base, admin. **Never sold standalone and never represented by a licence row** | 1 | ServiceNow *Now Platform*; Archer *Platform* |
| **Product SKUs** | A buyer outcome with a named regulator and a named comparator. Server-denied when unlicensed | **15** | ServiceNow IRM products; Archer solution areas; MetricStream product lines |
| **Regulatory content packs** | NCA ECC, SAMA CSF, SAMA BCM, SAMA Outsourcing, PDPL/SDAIA, NDMO, CMA CG, IFRS S1/S2, ISO — **versioned content, not code**. Several activate against one common control set | n | Archer content subscriptions; ServiceNow Authority Documents; MetricStream content |

**Fifteen SKUs is deliberate, and 15 is not a sales problem — bundles are the answer to that.**
The platform spans what four vendor categories cover separately (IRM, SecOps, board governance,
identity governance) plus a Saudi-only regulatory set with no comparator. Leaders sell many
products and few *offers*: ServiceNow packages its IRM products into Standard/Professional/
Enterprise, Archer sells solution-area bundles. §6 does the same with three bundles aligned to
the three Saudi buying centres.

---

## 2. The binding rules

| # | Rule | Why it is a rule and not a preference |
|---|---|---|
| **R1** | **Every package on disk belongs to exactly one of {Base, a SKU, explicitly unassigned}.** Total and disjoint, checked against the filesystem — not asserted | An unhomed package is either unsold revenue or dead code. F2-35 existed because 7 packages were unhomed, one of them 7,717 LoC |
| **R2** | **A module code is an opaque identifier with a display name. Codes are never renamed for cosmetics** | `AUDIT_LC` is an ugly commercial code. Renaming it silently un-licenses every tenant holding it. Display names live here; codes live forever |
| **R3** | **A code is retired only in a migration that grants its successor to every organisation holding the predecessor**, with a `THROW` anti-vacuity check | Retiring a held code without a backfill is a customer blackout. §5 measures exactly one such case today (`NDI` → `DATA_AI_GOV`, org 1) |
| **R4** | **Base is not licensable.** No `organisation_licenses` row, no `@RequireModule`, no tier | The existing `EVIDENCE`/`REPORTING`/`AUDIT_LOG`/`PLATFORM_ADMIN` rows are the symptom of pricing the platform's own plumbing. Base being row-less makes over-entitlement structurally impossible |
| **R5** | **A SKU exists only with a named regulator instrument *and* a named comparator (or an explicit "no comparator exists")** | This is what stops the catalogue re-inflating. Two of B's 18 failed this test; one (`SAUDI_FS`) passes it by having no comparator on purpose, which is the moat |
| **R6** | **The entitlement unit is not always a package.** `BOARD` and `ACCESS_GOV` are sub-packages of kernel packages. The module registry (F2-01) must key on *entitlement unit*, not package | Forced by ADR-017 D-1 making both paid. `@RequireModule` is a class/method annotation so it expresses this fine; a package-keyed registry cannot. See **F2-57** |
| **R7** | **Regulatory content is not a SKU.** Content packs version independently of the code that reads them | A customer who renews content must not be forced through a platform upgrade, and vice versa. Already correct in the superseded Product doc — carried forward unchanged |

---

## 3. The 15 product SKUs

Readiness is stated because a SKU cannot be sold on a promise. "Debt" is what blocks *standalone*
sale, not what blocks the feature working.

| # | Code | Name (shelf) | Packages | Saudi regulator anchor | Comparator | Readiness debt |
|---|---|---|---|---|---|---|
| 1 | `RISK` | Enterprise & Operational Risk | `risk`, `risk_lc`, `ccm` | SAMA operational-risk expectations; NCA ECC 1-3 risk management; CMA CG risk oversight | Archer Enterprise & Operational Risk · MetricStream ERM · ServiceNow Risk Management | Ready — tests strongest on the platform |
| 2 | `COMPLIANCE` | Compliance, Policy & Regulatory Affairs | `compliance`, `policy`, `nca_ecc`, `regchange`, `reginspect`, `regreporting` | NCA ECC (all domains); SAMA compliance-function rules; CMA disclosure | **ServiceNow Policy *and* Compliance Management — one product** · MetricStream · OneTrust GRC | `controlmap` foreign schema (now Base); `nca_ecc` thin tests |
| 3 | `AUDIT_LC` | Internal Audit | `audit_lc`, `audit_eng`, `audit_committee` | SAMA internal-audit requirements; CMA CG audit committee; IIA Standards | **AuditBoard's core** · ServiceNow Audit Management · Archer Audit | `audit_committee` has 8 peer imports — the SKU's separability debt; it also overlaps `BOARD` (see note ⓐ) |
| 4 | `CYBER` | Cyber Governance & Assurance | `cyber`, `cyber_lc` | **NCA ECC 2-x**; SAMA CSF domains 1–2; NDMO security controls | ServiceNow IRM (cyber) · Archer IT & Security Risk | Ready |
| 5 | `SOCOPS` | Security Operations Governance | `socops`, `incident` | SAMA CSF 3.3 (operations); NCA ECC 2-13 event management | ServiceNow **SecOps** — a separate product there too | Ready. **The only SKU already wired**: 22 `@RequireModule` annotations, licence rows in all 3 tenants |
| 6 | `BCM` | Resilience & Crisis | `bcm`, `bcm_lc`, `opres` | **SAMA BCM Framework**; NCEMA 7000; NCA ECC 2-14 | Fusion · Archer Business Resiliency · ServiceNow BCM + ORM | `bcm` strong (395 tests); `opres` 0 tests |
| 7 | `PDPL` | Privacy & Data Protection | `privacy`, `privacy_lc`, `data_officer`, `dataclass` | **PDPL + SDAIA implementing regulations**; NDMO data-classification policy | **OneTrust's core** · Securiti | `privacy` ready; `privacy_lc` is orphaned tables (do not seed); `data_officer`/`dataclass` 0 tests |
| 8 | `TPRM` | Third-Party, Outsourcing & Supply Chain | `tprm`, `scm`, `outsourcing` | **SAMA Rules on Outsourcing**; SAMA Cloud Computing Regulatory Framework; NCA ECC 4-1 | OneTrust TPRM · Archer Third Party Governance · Prevalent | `tprm` ready; `outsourcing` 0 tests |
| 9 | `ESG` | Sustainability & Green Finance | `sust` | CMA sustainability disclosure guidance; Tadawul ESG; IFRS S1/S2; ICMA GBP / Green Sukuk | Workiva · Diligent ESG · OneTrust ESG — **no Arabic-first comparator** | Ready (200 tests); 2 peer deps |
| 10 | `ETHICS` | Ethics, Conduct & Financial Crime | `ethics`, `consumer`, `aml`, `fraud` | **Nazaha** whistleblower protection; KSA AML Law + SAMA AML/CTF Rules; SAMA Consumer Protection Principles | NAVEX · OneTrust Ethics · (AML *governance*, not transaction monitoring — not NICE/Oracle FCCM) | `ethics` ready; `fraud` 0 tests |
| 11 | `SAUDI_FS` ⭐ | Saudi Financial Services | `sharia`, `openbanking`, `rrp`, `cpty` | **SAMA Sharia Governance Framework**; SAMA Open Banking Framework; SAMA recovery & resolution; SAMA counterparty / large exposures | **No comparator exists.** This is the moat | **All four have 0 tests** — the highest-margin, least-contested SKU is the least warranted. That is the register's most important sentence |
| 12 | `DATA_AI_GOV` | Data & AI Governance | `ndi`, `ai` | **SDAIA/NDMO** National Data Management policies + NDI assessment; SDAIA AI Ethics Principles; PDPL Art. on automated processing | Collibra / Alation (data) · Credo AI / watsonx.governance (AI) — emerging category | `ndi` is 7,717 LoC / 127 endpoints and **appeared in no earlier catalogue**. `ai` needs the export-toolkit lift (F2-08a) |
| 13 | `BOARD` | Board & Committee Governance | `board_pack` **+ carve-out `admin/committee`** (51 files) | **CMA Corporate Governance Regulations** (committees, minutes, resolutions); SAMA Principles of CG for Banks | **Diligent Boards · Nasdaq Boardvantage · Computershare** — a standalone category everywhere, priced per director seat | **Paid by [ADR-017](../Decisions/ADR-017-OWNER-DELEGATED-DECISIONS.md) D-1.** Boundary is not a package boundary (R6) |
| 14 | `ACCESS_GOV` | Access Governance | **carve-out `iam/governance`** (22 files) — no top-level package | **NCA ECC 2-2**; SAMA CSF 3.3.5 | **SailPoint · Saviynt · Omada** — never bundled free, priced per identity | **Paid by ADR-017 D-1.** Boundary is not a package boundary (R6) |
| 15 | `CHANGE` | Change Governance & CAB | `change_mgmt` | NCA ECC 2-7 change management; SAMA CSF 3.3.9 | ServiceNow Change Management — **but we compete on governance, not CMDB or release pipelines** (ADR-017 D-4) | Extraction in progress (S5/CE-1) |

ⓐ **The audit-committee overlap is deliberate, not an oversight.** `audit_committee` sells with
Internal Audit because its buyer is the Chief Audit Executive; the *generic* committee engine
(`admin/committee`) sells with `BOARD` because its buyer is the Board Secretary. A tenant buying
`AUDIT_LC` without `BOARD` gets the audit-committee dashboard reading the committee engine that
Base hosts. If that split ever produces a 403, the fix is a read contract, not a re-home.

---

## 4. Base Platform — the 18 packages that are never a SKU

`platform` · `iam` *(less `iam/governance`)* · `auth` · `admin` *(less `admin/committee`)* ·
`evidence` · `attachment` · `signature` · `workflow` · `workflow_submission` · `approvals` ·
`intake` · `capa` · `dashboard` · `reporting` · `integrations` · `knowledge` · `controlmap` ·
`governance`

Four of these were argued over and are decided here:

| Package | Decision | Reason |
|---|---|---|
| `controlmap` | **Base** (retires the `CONTROL_LIB` code) | Every regulated SKU maps to the same controls. Putting the common-control library inside `COMPLIANCE` means a bank buying only `CYBER` cannot map a control. Archer puts the control library in the platform for the same reason |
| `reporting` | **Base** — the export toolkit is platform | But ⚠ **`reporting.BoardPackService` and the two `board-pack.*` export endpoints are `BOARD` content sitting in a Base package.** F2-08a's toolkit lift must separate them, or `BOARD` is unenforceable at its most visible surface |
| `knowledge` | **Base** | Every SKU needs help and policy KB. ServiceNow Knowledge Management is platform. ⚠ Also fixes F2-53: `KB` was never a catalogued module, which is why the knowledge base is admin-only |
| `governance` | **Base** | It is `governance/pivot` (risk × control × finding) and `governance/threelines` (IIA Three Lines role mapping) — 13 files, no sellable surface. A cross-SKU read view must not be paywalled, or a tenant cannot see combined assurance over what they bought |

**Unassigned — 1 package.** `datagov` contains one file (`package-info.java`) and no code. It is
the placeholder `DATA_AI_GOV` should have filled. **Delete it or fill it**; F2-01's registry check
must fail on it until then. It is recorded as unassigned rather than quietly filed under Base,
because "quietly filed" is how the other seven went missing.

---

## 5. Legacy code → ratified destination (all 20 accounted for)

`LicensingService.KNOWN_MODULES` and the live `platform.organisation_licenses` rows agree on the
same 20 codes. Measured 2026-07-30: **org 1 holds all 20; orgs 2 and 3 hold `SOCOPS` only.**

| Legacy code | Destination | Class | Retirement safety (measured) |
|---|---|---|---|
| `RISK` | `RISK` | carried forward | — |
| `COMPLIANCE` | `COMPLIANCE` | carried forward | — |
| `AUDIT_LC` | `AUDIT_LC` | carried forward (ugly code kept per R2) | — |
| `CYBER` | `CYBER` | carried forward | — |
| `SOCOPS` | `SOCOPS` | carried forward | **must not change** — 23 live annotations, 3 tenants |
| `BCM` | `BCM` | carried forward | — |
| `PDPL` | `PDPL` | carried forward (also the better Saudi-facing name) | — |
| `TPRM` | `TPRM` | carried forward | — |
| `POLICY` | `COMPLIANCE` | merged into sibling SKU | org 1 holds both → safe |
| `REGULATORY` | `COMPLIANCE` | merged into sibling SKU | org 1 holds both → safe |
| `SCM` | `TPRM` | merged into sibling SKU | org 1 holds both → safe |
| `INCIDENT` | `SOCOPS` | merged into sibling SKU (ADR-013 D-4 held; the SKU it joins is `SOCOPS`) | org 1 holds both → safe |
| `NDI` | `DATA_AI_GOV` | renamed | ⚠ **org 1 holds `NDI` and no successor — W2-0 must backfill `DATA_AI_GOV` for org 1 in the same migration (R3)** |
| `EVIDENCE` | Base | de-licensed | Base is mandatory → widening, safe by construction |
| `REPORTING` | Base | de-licensed | as above |
| `AUDIT_LOG` | Base | de-licensed | as above |
| `PLATFORM_ADMIN` | Base | de-licensed | as above |
| `CONTROL_LIB` | Base | de-licensed | as above |
| `POSTURE` | Base | de-licensed | as above |
| `AUTOMATION` | Base | de-licensed | as above |

**Net-new codes with no predecessor** (W2-0 grants them under grant-all-then-tighten):
`ESG`, `ETHICS`, `SAUDI_FS`, `DATA_AI_GOV`, `BOARD`, `ACCESS_GOV`, `CHANGE`.

8 carried forward + 5 merged/renamed + 7 de-licensed = **20** ✔ · 8 + 7 new = **15 SKUs** ✔

---

## 6. Buyer bundles — three offers over fifteen SKUs

Named for the three Saudi buying centres, because the regulator determines the shopping list.
A bundle is a discounted offer, not a new entitlement primitive: it grants exactly its SKUs.

| Bundle | Buyer | Base + SKUs |
|---|---|---|
| **Financial Institution** | SAMA-regulated bank, finco, insurer | `RISK` `COMPLIANCE` `AUDIT_LC` `CYBER` `SOCOPS` `BCM` `TPRM` `ETHICS` `SAUDI_FS` `BOARD` `CHANGE` |
| **Government & Public Entity** | NCA + SDAIA/NDMO regulated | `COMPLIANCE` `CYBER` `SOCOPS` `BCM` `PDPL` `DATA_AI_GOV` `ACCESS_GOV` `CHANGE` |
| **Listed Company** | CMA-listed, Tadawul | `RISK` `COMPLIANCE` `AUDIT_LC` `BOARD` `ESG` `ETHICS` `PDPL` |

⚠ `PDPL` is in two bundles and not in Financial Institution — banks buy it, but as an add-on,
because SAMA's data expectations arrive through CSF and outsourcing rather than through PDPL
operations. That is a sales judgement and it is the line most likely to need revising against a
real deal. Revise it here, not in a deck.

**Pricing is out of scope and stays owner-only.** This file decides *what is sellable* and *what
is mandatory*; it sets no number. The pricing hypothesis in the superseded Product doc (platform
fee + package subscription scaled to organisation size, content subscription, no per-seat penalty
for occasional control owners) survives ratification unchanged and still needs validating against
Saudi procurement behaviour.

---

## 7. What this catalogue does not claim

- **Not that 15 SKUs are all sellable today.** Five carry 0-test packages; `SAUDI_FS` — the one
  with no competitor — carries four. Readiness is in §3 per row, and it is the honest reason a
  price list is not a launch plan.
- **Not that the code enforces it.** Entitlement is wired to `socops` only: **22 of ~2,160
  endpoints**. F2-04 is the sweep; W2-0 is the backfill that must precede it, because
  `@RequireModule` is fail-closed and orgs 2/3 hold one licence row each.
- **Not that the boundaries are clean.** `audit_committee` imports 8 peers; `incident` writes into
  the `cyber` schema; `workflow_submission` writes into `workflow`; `reporting` holds `BOARD`
  content. Each is a named finding, not a surprise.
- **Not a per-tenant grant model.** F2-56 measured that roles are effectively single-tenant —
  tenant 1 reaches 59 roles, tenants 2 and 3 reach 12. A SKU a tenant can buy but has no role to
  operate is not sold. **Read F2-56 before writing W2-0.**
