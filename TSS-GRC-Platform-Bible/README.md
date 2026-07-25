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
| Appendices | [Evidence request](Appendix/EVIDENCE_REQUEST.md), [references](Appendix/REFERENCES.md), [traceability](Appendix/TRACEABILITY.md) |

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

