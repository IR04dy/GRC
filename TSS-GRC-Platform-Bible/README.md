# TSS GRC Platform Bible

**Permanent product and engineering reference**  
Version: 1.0 assessment baseline  
Baseline date: 2026-07-25  
Repository assessed: `D:\AYMAN\GRC`  
Status: **Qualified — transformation required before an unqualified enterprise-market opinion**

## Purpose

This repository is the governing reference for turning TSS GRC into the strongest Arabic-first enterprise GRC platform for Saudi Arabia. It is deliberately stricter than a feature catalogue. It separates:

- what the product demonstrably does;
- what documents claim it does;
- what the target product must do;
- what remains unknown and requires evidence.

It is intended for Product, UX, Architecture, Engineering, QA, Security, Sales, Consulting, customer acceptance committees, regulators and investors.

## Independent opinion

TSS GRC has exceptional functional breadth and unusually deep Saudi-domain ambition. It is not yet a coherent enterprise product at the level of ServiceNow IRM, Archer, MetricStream, OneTrust or AuditBoard. The largest obstacle is not missing screens. It is **trust**:

1. several high-consequence decisions are not cryptographically or logically bound to the authenticated decision-maker;
2. commercial module licensing is not consistently enforced server-side;
3. module boundaries are conventions rather than executable architecture;
4. the UI exposes the organisation of the codebase instead of consistently organising work around user outcomes;
5. documentation frequently marks existence as completion without proving operability, control integrity or customer value.

The transformation strategy is therefore **assure, simplify, package, then scale**. A microservices rewrite is explicitly rejected.

## Truth labels

Every material statement should use one of these labels:

| Label | Meaning |
|---|---|
| `VERIFIED` | Directly demonstrated by source, automated test or live probe |
| `EVIDENCE-BACKED` | Supported by repository artefacts but not independently executed |
| `TARGET` | Approved future-state design |
| `HYPOTHESIS` | Product assumption requiring customer or market validation |
| `UNKNOWN` | Evidence requested but unavailable |
| `REJECTED` | Considered and deliberately not adopted |

No feature may be called “complete”, “compliant”, “secure”, “market-ready” or “independent” solely because a controller, page, migration or test exists.

## Repository map

Start with the two consolidated governing documents:

- [Independent Enterprise Product Audit](TSS_GRC_PLATFORM_AUDIT_REPORT.md)
- [Transformation Implementation Plan](TSS_GRC_PLATFORM_IMPLEMENTATION_PLAN.md)

Those two are the **strategic frame** — verdict, phases, councils, release gates, metrics. Two
companion documents at the repository root are the **execution layer** and are kept deliberately separate:

- [`UNIFIED_IMPLEMENTATION_PLAN.md`](../UNIFIED_IMPLEMENTATION_PLAN.md) — the execution tracker: concrete
  items with verified status, migration ledger and live-probe results.
- [`TSS_GRC_PLATFORM_ASSESSMENT.md`](../TSS_GRC_PLATFORM_ASSESSMENT.md) — the measured evidence base: 24
  findings in the 18-field format, each with a command that re-verifies it.

⚠ The reconciliation between the two sets is **provisional and unconfirmed**, so nothing is stamped
superseded. Their complementarity is evidenced rather than assumed: Phase 0 of the plan above found
caller-supplied actor identity in **change approvals**, the root-level review found the same defect in
**committee votes**, and neither found the other's instance.

| Area | Governing documents |
|---|---|
| Governance | [Evidence and maintenance standard](Governance/EVIDENCE_AND_MAINTENANCE_STANDARD.md), [assessment method](Governance/ASSESSMENT_METHOD.md) |
| Executive | [Executive verdict](Executive/EXECUTIVE_VERDICT.md), [acceptance opinion](Executive/GOVERNMENT_ACCEPTANCE_OPINION.md) |
| Product | [Product constitution](Product/PRODUCT_CONSTITUTION.md), [DNA and philosophy](Product/PRODUCT_DNA_AND_PHILOSOPHY.md), [strategy and packaging](Product/PRODUCT_STRATEGY_PACKAGING_AND_SALES.md) |
| Architecture | [Solution architecture](Architecture/SOLUTION_ARCHITECTURE.md), [principles](Architecture/ARCHITECTURE_PRINCIPLES.md), [module independence](Architecture/MODULE_INDEPENDENCE_AND_PACKAGING.md) |
| UX | [UX architecture](UX/UX_ARCHITECTURE.md), [Arabic design system](UX/ARABIC_AND_SAUDI_UX_STANDARD.md), [journeys](UX/PERSONA_JOURNEYS.md) |
| Modules | [PDS catalogue](Modules/PRODUCT_DESIGN_SPECIFICATION.md), [source-module inventory](Modules/SOURCE_MODULE_INVENTORY.md) |
| Processes | [Business-process design](Process/BUSINESS_PROCESS_DESIGN.md), [state machines](Process/STATE_MACHINE_CATALOGUE.md) |
| Data | [Data architecture](Data/DATA_ARCHITECTURE.md) |
| Integration | [Integration architecture](Integration/INTEGRATION_ARCHITECTURE.md) |
| Security | [Security architecture](Security/SECURITY_ARCHITECTURE.md), [abuse cases](Security/ABUSE_CASE_CATALOGUE.md) |
| AI | [AI transformation](AI/AI_TRANSFORMATION.md) |
| Saudi | [Saudi readiness](Saudi/SAUDI_MARKET_AND_REGULATORY_READINESS.md) |
| Decisions | [ADR index](Decisions/README.md), [rejected ideas](Decisions/REJECTED_IDEAS.md), [lessons learned](Decisions/LESSONS_LEARNED.md) |
| Assessment | [Finding register](Registers/FINDINGS_REGISTER.md), [debt register](Registers/TECHNICAL_DEBT_REGISTER.md), [decision register](Registers/DECISION_REGISTER.md) |
| Roadmap | [Implementation roadmap](Roadmap/IMPLEMENTATION_ROADMAP.md), [future vision](Roadmap/FUTURE_VISION.md) |
| Backlog | [Product backlog](Backlog/PRODUCT_BACKLOG.md) |
| Visuals | [Heatmaps](Heatmaps/PLATFORM_HEATMAPS.md), [diagrams](Diagrams/PLATFORM_DIAGRAMS.md) |
| Appendices | **[Measured baseline](Appendix/MEASURED_BASELINE.md)** — the evidence base, with [reproduction scripts](Appendix/scripts/) |

## Independent review pass — 2026-07-25

A second, independent pass re-derived every checkable claim in this Bible from source and added the
documents below. It is deliberately separated from the v1.0 corpus so a reader can see what is *measured*
versus what is *asserted*.

| Document | What it is |
|---|---|
| **[Measured baseline](Appendix/MEASURED_BASELINE.md)** | The evidence base every other new document cites — 58 modules, 186,285 LoC, coupling graph, guard styles, entitlement adoption, i18n and UX metrics. Reproducible via `Appendix/scripts/`. |
| **[Findings register V2](Registers/FINDINGS_REGISTER_V2.md)** | 22 findings in the required 18-field format (ID → Root Cause → four Impacts → Recommendation → Alternatives → Trade-offs → Effort → Priority → ROI → References), each anchored to a baseline section. Supersedes `FINDINGS_REGISTER.md` on measured facts. |
| **[Module separability & SKU model](Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md)** | Per-module separability tier (A/B/C/K) for all 58, derived from the coupling graph, plus a 14-SKU catalogue. Supersedes `MODULE_INDEPENDENCE_AND_PACKAGING.md` and `Heatmaps/MODULE_INDEPENDENCE_MATRIX.md` on measured facts. |
| **[UX redesign blueprint](UX/UX_REDESIGN_BLUEPRINT.md)** | Corrects the "complete UX redesign" recommendation — measurement shows a real design system at 99% adoption. Redesigns the *information architecture* instead: 12 workspaces, route reconciliation, Arabic convergence, truthful-UI principle. |
| **[Independent acceptance verdict](Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md)** | Three separate verdicts — government acceptance committee, enterprise customer, investor due diligence — with a 13-dimension scorecard and four non-negotiable conditions. |
| **[Bible validation log](Governance/BIBLE_VALIDATION_LOG.md)** | Per-document verdict on this corpus: VERIFIED / SOUND / UNVERIFIED / PARTLY CONTRADICTED / CONTRADICTED, with the specific check applied and what was *not* checked. |
| **[Documentation register & audit support](Appendix/DOCUMENTATION_REGISTER_AND_AUDIT_SUPPORT.md)** | **Which of the 331 tracked documents govern and which are historic** — 8 corpora, 6 corpus-level defects, a 6-tier reading path, a **re-verification command for every finding**, an acceptance-committee sampling guide, and what evidence exists vs is genuinely absent. Machine-readable: [`DOCUMENT_REGISTER.csv`](Appendix/DOCUMENT_REGISTER.csv). |
| **ADR-009 … ADR-012** | [Module registry](Decisions/ADR-009-MODULE-REGISTRY.md) · [permission-only authorisation](Decisions/ADR-010-PERMISSION-ONLY-AUTHORISATION.md) · [schema ownership](Decisions/ADR-011-SCHEMA-OWNERSHIP.md) · [boundary enforcement and ports](Decisions/ADR-012-BOUNDARY-ENFORCEMENT-AND-PORTS.md) — the four rules this pass introduces, each enforced by a test rather than a document. |

**Three headline results of the validation pass:**

1. **The v1.0 corpus is more rigorous than its packaging suggested.** `ADR-001`'s "734 cross-module
   imports / 14 bidirectional pairs" and every per-module file count in `SOURCE_MODULE_INVENTORY.md`
   reproduced **exactly** under independent measurement. `ADR-002`'s claim that licensing infrastructure
   exists was also correct — the second pass initially disputed it and was wrong.
2. **Its single most valuable sentence was true and unproven.** `EXECUTIVE_VERDICT.md` warned that
   "governance decisions can be authored under caller-supplied identities." That has now been located and
   confirmed in code: committee votes are recorded under a request-body name (F2-21), which makes
   recusal, quorum and segregation of duties bypassable. It is the highest-severity finding in either pass.
3. **Two v1.0 recommendations should not be actioned as written** — a complete UX redesign (contradicted
   by 99% design-system adoption) and building an entitlement layer (one already exists, wired to 8.6%
   of controllers).

**Every ADR in this Bible has a "Proof" section naming tests that mostly do not exist.** Turning those
Proof sections into CI tests is the highest-leverage governance action available, because the decisions
themselves are already right.

## Baseline verification

The initial assessment executed:

- backend: 2,316 tests, 0 failures, 0 errors;
- frontend: 49 tests, 0 failures;
- TypeScript compilation and Vite production build: passed;
- source inventory: 58 top-level backend module directories, 252 controllers, 423 repositories, 313 services and more than 300 routed frontend pages;
- dependency scan: 734 direct cross-module Java imports across 136 directed pairs, including 14 bidirectional pairs;
- licensing scan: 22 `@RequireModule`-guarded controllers, all in SOC Ops at baseline;
- local runtime check: backend `:8084` and frontend `:5173` were not running, so live two-principal and browser acceptance probes remain open.

Passing tests demonstrate build health. They do not prove product completeness, regulatory compliance, usable workflows or effective segregation of duties.

## Mandatory maintenance rule

Every release that changes a workflow, permission, entity, API, commercial entitlement or regulatory mapping must update this Bible in the same verified slice. Architecture decisions require an ADR. Findings are never silently deleted: they are closed with evidence and retained in history.

