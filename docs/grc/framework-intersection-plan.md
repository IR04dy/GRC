# Framework Intersection Plan — Domain Spine, Regulatory Overlays
> **HISTORIC — point-in-time snapshot (all files 2026-05-17). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


**Status:** Proposal · Sprint-5 candidate
**Date:** 2026-05-17
**Audience:** Product, architecture, GRC consultants, implementation team

---

## 1. Why this plan exists

The earlier "build a module per regulator" backlog (DGA, SAMA, NRC as
sibling modules to NCA + PDPL + NDI) would duplicate three things the
platform already has:

1. **Risk identification + treatment** — every regulator wants a risk
   register. We already own one (`module.risk`).
2. **Control assessment + evidence** — every framework is "did the
   organisation perform control X and produce evidence Y." Already in
   `module.compliance` + `module.evidence`.
3. **Issue + corrective action tracking** — every regulator's
   non-compliance flows into CAPA. Already in `module.capa`.

Repeating those mechanics inside `module.dga`, `module.sama`,
`module.nrc` would produce 3 mini-GRC platforms inside the GRC
platform. That's the classic "every regulation is its own product"
mistake the IRM leaders (ServiceNow, Archer, OneTrust, MetricStream)
explicitly designed against. Their architecture is always:

> **One control library. Many authority documents. Many citations.
> One Compliance Posture.**

This plan rewrites the backlog to follow that architecture.

---

## 2. Architectural principle

```
          ┌──────────────────────────────────────────────┐
          │           AUTHORITY DOCUMENTS                │
          │  NCA-ECC / NCA-CSCC / NCA-CCC / NCA-OTCC /   │
          │  NCA-DCC / NCA-TCC / PDPL / SAMA-BCM /       │
          │  SAMA-Cyber / SAMA-Outsourcing /             │
          │  SAMA-Op-Risk / DGA / NRC / ISO-22301 /      │
          │  ISO-27001 / ISO-27701 / ISO-31000 / SOC2    │
          └─────────────────┬────────────────────────────┘
                            │  cited by
                            ▼
          ┌──────────────────────────────────────────────┐
          │     UNIFIED CONTROL LIBRARY (the spine)      │
          │  Each control mapped to N citations across   │
          │  many authority documents.                   │
          └─────────────────┬────────────────────────────┘
                            │  implemented by
                            ▼
          ┌──────────────────────────────────────────────┐
          │  DOMAIN MODULES (the operating fabric)       │
          │  Risk · Cyber · BCM · Privacy · TPRM ·       │
          │  Policy · Audit · Incident · Evidence        │
          └──────────────────────────────────────────────┘
```

Every domain module owns its operational primitives (risks, plans,
assessments, incidents). The regulator-specific work is **read-only
overlays + reports** that pivot the same primitives through different
authority lenses.

### The two questions the platform must answer

1. **Top-down (regulator-first):**
   > "For NCA ECC v2 Domain 2 Sub-2.1.3, which controls satisfy it,
   > what is their effectiveness, what evidence backs them, and what
   > is the residual risk?"

2. **Bottom-up (control-first):**
   > "For Control C-IAM-014 (privileged access review), which
   > authority documents does this satisfy, when was it last tested,
   > what gaps were found, what CAPAs are open?"

Both must be one query each. Today the platform answers the first
question per-framework (NCA dashboard, NDI dashboard) but cannot
pivot the same control across regulators. This plan fixes that.

---

## 3. Domain spine — what we keep, what each one owns

| Domain | Module(s) | Owns operationally | Already covered |
|---|---|---|---|
| **Enterprise Risk** | `risk` | Risk register, appetite, KRIs, scenarios, emerging risks, assessments, treatments | ✅ Built (Sprint 1-2) |
| **Cybersecurity** | `cyber` | Assets, vulnerabilities, cyber controls, incident response, OT/ICS | ✅ Built |
| **Business Continuity** | `bcm` | Critical services, BIA, BCP, DRP, exercises, crisis | ✅ Built |
| **Privacy / Data Protection** | `privacy` | ROPA, PIAs, DSRs, consent, cross-border, breaches | ✅ Built |
| **Third-Party Risk** | `tprm` + `scm` | Vendors, criticality, questionnaires, contracts, exit | ✅ Built |
| **Compliance & Audit** | `compliance` + `audit_lc` | Self-assessments, audit engagements, findings, CAPA | ✅ Built |
| **Policy & Attestation** | `policy` | Policies, exceptions, attestations | ✅ Built (Sprint 2) |
| **Incident & Crisis** | `incident` + `bcm.crisis` | Incidents, RCA, CAPA, regulatory notification | ✅ Built |
| **Evidence** | `evidence` | Evidence items, chain of custody, expiry, requests | ✅ Built |
| **Reporting & Dashboards** | `reporting` + `dashboard` | Report runs, schedules, definitions | ✅ Built |
| **Workflow & Approvals** | `workflow` + `approvals` + `signature` | Workflow engine, approval matrix, e-signature | ✅ Built |

**Net new for this plan:** zero domain modules. Everything new is
in the shared overlay layer (sections 4-5).

---

## 4. Authority Documents catalog (new — one table, many rows)

A single platform-level catalog instead of per-regulator modules.

### Schema (V91 — `platform.authority_documents`)

```
authority_documents
  id, code, family, jurisdiction, issuer, version,
  title_en, title_ar, summary_en, summary_ar,
  effective_date, applicability_json (org_type filters),
  is_placeholder, status (ACTIVE | DEPRECATED | DRAFT)

authority_document_citations          -- the "controls" of each regulation
  id, authority_document_id,
  citation_code, parent_citation_code, depth,
  title_en, title_ar, requirement_text_en, requirement_text_ar,
  intent_en, intent_ar, testing_guidance_en, testing_guidance_ar,
  weight, mandatory
```

### Seed catalog (V92)

| Code | Family | Jurisdiction | Notes |
|---|---|---|---|
| `NCA-ECC-V2` | NCA Cyber | KSA | Already partially seeded (V42) |
| `NCA-CSCC-V1` | NCA Cyber | KSA | Already partially seeded |
| `NCA-CCC-V1` | NCA Cyber | KSA | Already partially seeded |
| `NCA-OTCC-V1` | NCA Cyber | KSA | Already partially seeded |
| `NCA-DCC-V1` | NCA Cyber | KSA | **NEW** — ~95 placeholder controls |
| `NCA-TCC-V1` | NCA Cyber | KSA | **NEW** — telecom only |
| `PDPL-2023` | Privacy | KSA | Already in `privacy` module copy — migrate to catalog |
| `SAMA-BCM-FW` | BCM | KSA | **NEW** — banks/insurers/payment-providers |
| `SAMA-CYBER-FW` | Cyber | KSA | **NEW** — same applicability |
| `SAMA-OUTSOURCING` | Third-Party | KSA | **NEW** |
| `SAMA-OP-RISK` | Risk | KSA | **NEW** |
| `DGA-DIGITAL-MATURITY` | Digital-Gov | KSA | **NEW** — gov entities only |
| `DGA-INTEROPERABILITY` | Digital-Gov | KSA | **NEW** |
| `NRC-NATIONAL-RISKS` | Resilience | KSA | **NEW** — cross-sector |
| `ISO-22301` | BCM | International | **NEW** — overlay |
| `ISO-27001` | Cyber | International | **NEW** — overlay |
| `ISO-27701` | Privacy | International | **NEW** — overlay |
| `ISO-31000` | Risk | International | **NEW** — overlay |
| `SOC2` | Cyber | International | **NEW** — for KSA orgs serving US clients |

All NEW rows ship `is_placeholder = 1` until the official text is
imported (existing NDI banner pattern reused).

**Net result:** every regulator the platform claims to support
appears as **rows in two tables**, not as new modules.

---

## 5. Unified Control Library (V93)

The single biggest design decision. Today each module has its own
"controls" (cyber_controls, compliance_controls, ndi_compliance_*).
Consolidate.

```
platform.controls
  id, org_id, control_code, version, domain (RISK | CYBER | BCM | PRIVACY | …),
  title_en, title_ar, description_en, description_ar,
  control_type (PREVENTIVE | DETECTIVE | CORRECTIVE | DETERRENT),
  automation_level (MANUAL | SEMI | AUTOMATED), frequency,
  owner_user_id, status (DRAFT | ACTIVE | RETIRED),
  is_inherited, parent_control_id

platform.control_objectives                 -- group of controls that
  id, org_id, objective_code, title_en, title_ar    -- collectively satisfy

platform.control_mappings                   -- the cross-reference table
  id, control_id, citation_id, coverage (FULL | PARTIAL | COMPENSATING),
  rationale_en, rationale_ar, last_reviewed_at, reviewer_user_id

platform.control_assessments                -- replaces module-specific
  id, control_id, assessment_cycle_id,              -- assessment tables
  effectiveness (EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED),
  evidence_id, assessor, assessment_date, notes_en, notes_ar,
  finding_id, capa_id, workflow_status
```

### Migration path (no big bang)

The platform already has `compliance_controls`, `cyber.cyber_controls`,
`ndi.compliance_controls`, and a Risk-Advanced `controls_inventory`
table. Rather than rewrite, we add `platform.controls` and provide:

- A read-only **view** that UNIONs the existing tables into the
  unified shape until each module is migrated.
- A `legacy_source_module` + `legacy_id` column on `platform.controls`
  so reverse lookup is possible during the migration.
- A migration script per module (V94-V99) that copies + dedupes.

Implementation order: **Cyber first** (largest catalog, sets the
naming convention) → Compliance → NDI → Risk-Advanced → Privacy.

---

## 6. Per-domain "Regulatory Compliance" tab (UX pattern)

Every domain module gets ONE new tab — same look across modules so
users learn it once.

### Risk module (`/risk`) — new tab "Regulatory Alignment"

Shows for the currently-filtered risk universe:

- **ISO 31000 coverage** — % of risks with a populated category,
  appetite, treatment plan, KRI.
- **SAMA Op-Risk coverage** — % conditional on org being a financial
  institution.
- **Citations table** — collapsible tree of each authority's risk
  citations, with the number of platform risks/controls/CAPAs mapped.
- **Gap card** — citations with zero mapped controls → click to open
  a wizard that suggests existing controls or creates a new one.

### BCM module (`/bcm`) — new tab "Regulatory Alignment"
- **ISO 22301 coverage** — clause-by-clause: 4 Context, 5 Leadership,
  6 Planning, 7 Support, 8 Operation, 9 Performance, 10 Improvement.
- **SAMA BCM Framework coverage** — conditional on org type.
- **NRC alignment** — which national risks the org is exposed to and
  the matched internal BCPs/DRPs.
- **DGA continuity** — for gov entities, DGA's continuity-of-service
  requirements.

### Cyber module (`/cyber`) — new tab "Regulatory Alignment"
Already partly exists as the NCA assessment screens. Refactor to:
- Sub-tabs per framework: ECC v2 · CSCC · CCC · OTCC · DCC · TCC ·
  SAMA Cyber · ISO 27001 · SOC2.
- Each sub-tab shows the citation tree + coverage % + open gaps +
  evidence freshness.
- **One control can satisfy many sub-tabs** — that's the whole point.

### Privacy module (`/privacy`) — new tab "Regulatory Alignment"
- **PDPL coverage** — by Article (already structured but not exposed
  as a coverage view).
- **ISO 27701 overlay** — extends ISO 27001 controls with privacy
  extensions.
- **GDPR shadow** (later) — for KSA orgs with EU data subjects.

### TPRM module (`/tprm`) — new tab "Regulatory Alignment"
- **SAMA Outsourcing** — for financial institutions.
- **NCA third-party cyber requirements** — already partly in TPCA.
- **PDPL processor obligations** — for vendors processing personal
  data.

### Audit module (`/audit-lc`) — new tab "Regulatory Alignment"
- Maps each audit engagement to the authority documents in scope.
- Reuse Compliance Hub citations as audit "test universe."

**Net result for users:** every regulator appears where the work
already lives. A risk manager does not learn 5 different "DGA",
"SAMA", "NRC", "NCA" sidebar entries — those views are tabs on
the domain they already use.

---

## 7. Compliance Posture Hub (the cross-cutting view)

One new page: `/compliance/posture`. Implements the two pivots
from §2.

### Layout
- **Left rail** — Authority Document picker (tree: family → document
  → version). Multi-select.
- **Top** — KPI strip: overall coverage %, controls in scope, evidence
  freshness, open findings, open CAPAs, last attestation date.
- **Center heatmap** — rows = authority documents, columns =
  domains; cell = colour-coded coverage. Click → drill into the
  citation tree for that intersection.
- **Right** — Insights panel: most-cited controls (highest leverage),
  least-cited citations (gaps), expiring evidence, overdue
  attestations.

### Drill-down: citation detail panel
- The citation text (EN + AR).
- Mapped platform controls + their effectiveness + their evidence.
- Mapped risks + residual scores.
- Open findings + CAPAs.
- Buttons: "Add control mapping," "Create CAPA," "Request
  evidence," "Generate audit pack for this citation."

This is the screen the CRO / CISO / DPO opens before a regulator
meeting.

### Why one page, not a dashboard per regulator
Per the leaders' methodology: **the posture is one thing, the
audiences are many.** Filter the same page by authority document
to get a "NCA audit prep" view, a "PDPL board update" view, a
"SAMA inspection" view. The data is the same — only the slice
changes. Dashboards per regulator drift apart and get out of sync.

---

## 8. Backlog rewrite — replaces tasks #33–#41

This plan REPLACES the previous slicing. The new sequence:

### Slice F1 — Authority Documents catalog (V91-V92)
- `platform.authority_documents` + `authority_document_citations`.
- Seed all 19 documents from §4 (placeholders flagged).
- Read-only frontend page `/regulatory/library` (move + extend
  existing `RegulatoryLibrary.tsx` if present).
- **No new module; no per-regulator backend.**

### Slice F2 — Unified Control Library (V93)
- `platform.controls`, `control_objectives`, `control_mappings`,
  `control_assessments`.
- UNION view across legacy control tables.
- Migration shim — read both old + new; write to new.
- Frontend: `/controls/library` (new) + control detail page.

### Slice F3 — Migrate Cyber's controls into the unified library (V94)
- Copy `cyber_controls` rows → `platform.controls` with
  `legacy_source_module = 'cyber'`.
- Re-point cyber control-assessment screens to read from the
  unified table via the existing service interface.
- Acceptance: every existing NCA ECC/CSCC/CCC/OTCC screen still
  passes its QA checklist.

### Slice F4 — Migrate Compliance + NDI controls (V95-V96)
Same as F3 for `compliance` and `ndi` modules.

### Slice F5 — Per-domain "Regulatory Alignment" tabs
One per: Risk, BCM, Cyber (refactor existing), Privacy, TPRM, Audit.
Each is a thin React page that reads from the unified control
library filtered by the domain.

### Slice F6 — Compliance Posture Hub
The cross-cutting page from §7.

### Slice F7 — Replace generic Audit Log (still needed)
Carry over from task #33 unchanged — necessary regardless of
this plan.

### Slice F8 — Automation Rules engine (carry over from #37)
Now also drives "if NCA citation X has zero controls mapped, raise
an issue" rules — i.e., the engine reads the unified mapping table.

### Slice F9 — Module licensing (carry over from #35)
Unchanged.

### Slice F10 — Formal deliverable docs (carry over from #40)
Easier because the catalog + control library make the §07
Regulatory Mapping Matrix doc an export, not hand-written.

### Slices we DELETE from the previous plan
- ~~#34 DGA / SAMA / NRC modules~~ — replaced by §4 catalog rows +
  §6 tabs.
- ~~#38 DCC / TCC / ISO seeds~~ — replaced by §4 catalog rows
  (still needed as data, but as catalog rows, not migrations
  building new schemas).

---

## 9. What this avoids vs. the previous plan

| Anti-pattern (previous) | Why it hurts | Fixed by |
|---|---|---|
| Per-regulator backend modules | 3 mini-GRC platforms; controls duplicated 5× | §4 catalog + §5 unified library |
| Per-regulator sidebar entries | UX clutter; users navigate by regulator instead of by work | §6 domain tabs |
| Regulator-specific assessment tables | Same assessment lifecycle reinvented per regulator | §5 `control_assessments` |
| Hardcoded mapping in code comments | Mapping invisible to non-developers | §5 `control_mappings` as data |
| Dashboard per regulator | Drift; audience-confused | §7 single posture hub with filters |
| Re-seeded controls per regulator | Duplicated maintenance | §3 unify; §5 mapping is the only place a regulator-specific link lives |

---

## 10. Risks of THIS plan + mitigations

| Risk | Mitigation |
|---|---|
| Big-bang unification breaks existing NCA / NDI / Compliance screens | Slice F3-F4 migrate one module at a time; UNION view keeps everything readable mid-migration |
| `control_mappings` table grows huge | Indexed on (control_id, citation_id); platform has ≤ 5K controls × ≤ 20 docs ≤ 100K rows — fine for SQL Server |
| Bilingual citations are a huge text import | Catalog stays `is_placeholder = 1` until official text is loaded; existing NDI placeholder pattern reused |
| Compliance Posture Hub becomes a slow page | KPI strip uses materialised aggregates refreshed nightly; heatmap pre-computed per (org, document) pair |
| Domain modules' existing controllers become thin wrappers | That's fine and intentional — the library is the source of truth |
| "Where does ISO 31000 belong?" debates | Use the `family` column on `authority_documents` to classify and don't argue about routes |

---

## 11. Acceptance criteria for the full plan

The plan is "done" when:

1. A risk manager can open the Risk module and see, on one tab,
   the coverage % of ISO 31000 + SAMA Op-Risk against the current
   risk register.
2. A CISO can open the Compliance Posture Hub, pick "NCA ECC v2 +
   ISO 27001," and see a single heatmap with per-domain coverage.
3. A new authority document (e.g., a new SAMA circular) can be
   imported as catalog rows + citations without writing code —
   only data.
4. A single control assessment ticks off citations across multiple
   authority documents because the control is mapped to all of
   them.
5. The frontend has **fewer**, not more, sidebar entries than today.
6. Every QA checklist in `docs/*/QA_CHECKLIST.md` still passes.

---

## 12. Estimated effort

Assuming the same velocity as the NDI 15-slice + Risk-Advanced +
TPRM Questionnaire build-outs (≈ 1 slice / day in this codebase):

| Slice | Days |
|---|---|
| F1 Authority Documents catalog | 1 |
| F2 Unified Control Library | 2 |
| F3 Migrate Cyber | 1.5 |
| F4 Migrate Compliance + NDI | 1.5 |
| F5 Per-domain tabs (6) | 3 |
| F6 Compliance Posture Hub | 2 |
| F7 Generic Audit Log | 1 |
| F8 Automation Rules engine | 2 |
| F9 Module licensing | 1 |
| F10 Formal docs | 2 |
| **Total** | **~17 days** |

vs. the previous "build 3 regulator modules + 5 dashboards + 4 doc
sets" plan: ≈ 25 days, more code, less leverage.

---

# GRC-Expert Review — second pass

Reviewing the plan above through the lens of GRC tooling leaders'
methodology (ServiceNow IRM, Archer, OneTrust, MetricStream,
LogicGate Risk Cloud, AuditBoard, IBM OpenPages, Diligent /
Galvanize, Riskonnect), and against Gartner's IRM Magic Quadrant
evaluation criteria.

## R1. Does the plan match the leaders' "common information model"?

**Leaders' pattern:** every Tier-1 IRM tool implements roughly the
same conceptual model:

```
Authoritative Source → Citation → Control Objective → Control →
Test → Issue → Action → Evidence
```

with Risk and Process as parallel taxonomies that cross-cut Control.

**Plan check:** ✅ §4 (authority documents) + §5 (controls,
objectives, mappings, assessments) reproduces this. The naming
follows ServiceNow's GRC schema almost line-for-line:
- ServiceNow "Authority Document" ↔ our `authority_documents`
- ServiceNow "Citation" ↔ our `authority_document_citations`
- ServiceNow "Control Objective" ↔ our `control_objectives`
- ServiceNow "Control" ↔ our `controls`
- ServiceNow "Profile Type" ↔ our domain modules

**Gap:** the plan is missing an explicit `Risk Statement` ↔
`Control` link table separate from `control_mappings`. Currently
that linkage hides inside the `risk` module's own join tables.
Recommendation: add `platform.risk_control_links` in F2 so the
Risk-to-Control matrix becomes a first-class query (this is what
Archer calls the "Risk-Control Matrix" and what auditors ask for
first).

## R2. Common Information Model coverage

Run the OCEG GRC Capability Model (Red Book v3) practices through
the plan:

| OCEG Practice | Plan coverage |
|---|---|
| Context (P1) | ✅ Org, business unit, process via `organisation` + planned `departments` |
| Culture (P2) | ❌ Not in scope — no employee survey / culture-measurement workflow |
| Strategy (P3) | ⚠ Risk appetite is built; strategic-objective linkage to risk is **not** (missing entity "Objective") |
| Plan (P4) | ✅ Treatment plans + CAPA |
| Identify (P5) | ✅ Risk identification + emerging risk |
| Analyze (P6) | ✅ Risk assessment + heatmap + posture % |
| Respond (P7) | ✅ Treatment + transfer + accept + avoid |
| Monitor (P8) | ✅ KRI engine + dashboards + posture hub |
| Communicate (P9) | ⚠ Board pack exists; structured stakeholder-comms register is partial |
| Assure (P10) | ✅ Audit lifecycle + evidence + attestations |

**Recommendations:**
- Add an `Objective` entity (Sprint F-bonus). Without it, the
  platform cannot answer "which strategic objective is at risk?" —
  the single most common executive question.
- Promote "Communicate" out of board-pack-only: a register of
  stakeholder communications (regulator letters, board memos,
  press statements) tied to incidents/breaches.

## R3. Gartner Magic Quadrant criteria check

Gartner IRM 2024 weighs:

1. **Risk taxonomy depth** — ✅ Strong; ERM has appetite, KRIs,
   scenarios, emerging.
2. **Control library + framework crosswalk** — ⚠ Today: weak;
   after plan: ✅ Strong (this is the plan's main win).
3. **Issue/CAPA management** — ✅ Strong; CAPA is its own module
   with SLA + rollups.
4. **Operational Resilience (BCM + TPRM + cyber + crisis
   integrated)** — ✅ Very strong — KSA platform actually scores
   above many international tools because BCM + TPRM + crisis
   live in the same DB.
5. **Vendor risk** — ✅ Strong (TPRM Questionnaire engine + scoring
   + due diligence).
6. **Privacy** — ✅ Strong (PDPL module is mature).
7. **Audit management** — ✅ Strong (audit_lc + findings + CAPA).
8. **Workflow + approvals + e-signature** — ✅ Strong (workflow +
   approvals + signature modules).
9. **Regulatory change management** — ⚠ `module.regchange` exists;
   underused. Need to wire it to the new `authority_documents`
   catalog so when a citation changes, affected controls auto-flag.
10. **AI for risk + content** — ❌ Not in scope; deferred to F-AI.
11. **Reporting + analytics** — ⚠ Reports module exists; the
    Posture Hub (§7) closes the analytics gap.
12. **APIs + integrations** — ✅ Springdoc OpenAPI on; ✅ REST
    everywhere.

**Net read:** after this plan, the platform sits comfortably in the
"Visionaries" quadrant for KSA. The Leaders quadrant additionally
requires:
- AI-assisted content (deferred — F-AI slice).
- A connector library (SIEM, ITSM, GRC bus) — not in this plan;
  noted as a future track.
- Continuous controls monitoring (CCM) — agent-less integrations
  to AWS/Azure/Entra/M365 for posture telemetry. Out of scope for
  this plan.

## R4. Pareto check — are we building the 20% that delivers 80%?

The plan's high-leverage 20%:
- F1 Authority Documents catalog
- F2 Unified Control Library
- F6 Compliance Posture Hub

The lower-leverage but still necessary:
- F3, F4 migrations (mechanical)
- F5 per-domain tabs (UX polish; users can already get to the data)

**Recommendation:** ship F1 + F2 + F6 first as a "thin slice"
walking-skeleton release that proves the architecture, then F3-F4
migrations + F5 tabs as the second tranche. This is also how
ServiceNow shipped its GRC product in 2014 — Authority
Documents + Citations + a single posture screen, then
domain-by-domain rollout.

## R5. Saudi-specific reality check

A non-negotiable for KSA deployments:

1. **Arabic primary, English secondary** — ✅ schema supports both.
   The plan adds `title_ar`, `requirement_text_ar`, etc.
   throughout. **Watch out:** existing Saudi-native AR feedback
   in `MEMORY.md` says machine translations of NCA / PDPL text
   read poorly; the placeholder banner stays until human-curated
   AR is imported.
2. **Hijri date support** — the platform has `HijriDatePicker.tsx`
   ✅. Confirm the Authority Documents catalog exposes both
   Gregorian + Hijri `effective_date`.
3. **Data residency** — implicit (SQL Server in KSA tenancy). The
   plan doesn't change this.
4. **RTL** — current limited; flag for a separate "RTL hardening"
   sprint; not in this plan.
5. **SOC 2 Type II demand** — KSA enterprises serving Aramco /
   Saudi Aramco / banks now require SOC 2. Including SOC 2 in the
   catalog (§4) is intentional even though it's US-centric.

## R6. Common IRM tool pitfalls — does this plan trip them?

Pitfalls observed across Archer / MetricStream / ServiceNow
deployments at large customers:

| Pitfall | How this plan handles it |
|---|---|
| Over-mapping (one control mapped to 30 citations "for safety") | `coverage` enum (FULL/PARTIAL/COMPENSATING) + `rationale` mandatory — discourages drive-by mapping |
| Stale citations after regulator publishes a new version | `authority_documents.version` + `regchange` module wired to flag affected controls (F8) |
| "Control" used for both technical + procedural + governance items, getting unreadable | `control_type` + `domain` enums; UI filters separate them visually |
| Reports run direct queries against the live tables, melting them | Materialised aggregates for the Posture Hub; reports module uses snapshots |
| Bilingual fields get out of sync (EN updated, AR forgotten) | Add a "translation status" column on citations; QA checklist already enforces |
| Authority documents claimed as "complete" when they're scaffolds | `is_placeholder` flag + UI banner (existing NDI pattern) |
| RBAC blows up — every framework gets its own permissions | Permissions stay at domain + action level (RISK:READ, CYBER:WRITE). Authority-document-level access is read-only by default; only platform admins edit citations |

## R7. What I'd reorder

Given the review, here's the recommended ship order if a GRC
practice lead were running the program:

1. **F1 Authority Documents catalog** (1 day) — minimum data needed
   to talk to regulators.
2. **F2 Unified Control Library** (2 days) — must precede any
   migration; gives the platform its spine.
3. **F6 Compliance Posture Hub** (2 days) — even with only seeded
   placeholders, this is the screen the CRO / CISO opens to make
   the platform real to the C-suite.
4. **F7 Generic Audit Log** (1 day) — non-negotiable audit
   hygiene; runs in parallel with anything.
5. **F8 Automation Rules engine** (2 days) — turns the 20 hard-
   coded rules into a regulator-inspectable artefact.
6. **F3, F4 migrations** (3 days) — internal cleanup; user
   invisible.
7. **F5 per-domain tabs** (3 days) — UX polish.
8. **F9 module licensing** (1 day) — commercial enablement.
9. **F10 formal docs** (2 days) — saleable artefact.

Total ≈ 17 days, same as §12 but reordered for **earliest
end-user value**.

## R8. Final verdict

The plan is sound. The single biggest improvement vs. the previous
"module-per-regulator" backlog is that it follows the same
architectural pattern every IRM leader has converged on, while
respecting Saudi-specific bilingual + placeholder requirements
already proven in NDI.

**Add to the plan:**
- `Objective` entity in F2 (R2).
- `risk_control_links` first-class table in F2 (R1).
- A `translation_status` column on citations (R6).
- A stakeholder-communications register (R2 — OCEG P9 gap).
  Could be a small slice F-comms.

**Defer (next plan):**
- AI features.
- Continuous Controls Monitoring connectors.
- Connector library / GRC integration bus.
- RTL hardening sprint (separate from this plan's scope).

If the team ships F1 + F2 + F6 as a thin-slice MVP this sprint, the
platform crosses the line from "modular GRC toolkit" to "integrated
IRM in the Gartner Visionaries sense." Everything after that is
domain-by-domain consolidation and polish.

---

*End of plan + review.*
