# Product Strategy, Packaging and Sales Readiness

## Positioning

**Category:** Saudi assurance and GRC operating platform  
**Primary buyers:** government, regulated financial institutions, critical infrastructure and large Saudi enterprises  
**Economic buyer:** CEO, Board/Audit Committee, CRO, CISO, Chief Compliance Officer, DPO or BCM executive  
**Core promise:** faster regulatory readiness with defensible Arabic/English evidence and less duplicated assurance work

## Commercial architecture

### Required platform kernel

Identity, tenant/org model, permissions, audit ledger, evidence metadata, workflow runtime, notification, configuration, localization, reporting framework, integration gateway, licensing and administration.

The kernel must not include commercial domain screens or silently require a commercial package.

### Sellable packages — ⚠ SUPERSEDED

> **This list is history. The catalogue is [`SKU_CATALOGUE.md`](SKU_CATALOGUE.md), ratified
> 2026-07-30 by [ADR-018](../Decisions/ADR-018-SKU-CATALOGUE-RATIFICATION.md) (finding F2-35) —
> 15 SKUs, not 18.**
>
> **What this list got right, and won on:** `ACCESS_GOV` and `GOVERNANCE`→`BOARD` are **paid**, against
> two architecture documents that put them in the free Base (ADR-017 D-1 settled it this way);
> `DATA_AI_GOV` exists; `SOCOPS` sells apart from cyber governance; `cpty` belongs with Saudi FS; and
> content packs version independently of code — carried forward verbatim as rule R7.
>
> **What was rejected:** four splits. `POLICY` apart from `COMPLIANCE` is a grain no leader sells
> (ServiceNow ships Policy *and* Compliance as one product); `REGULATORY` folds in with it;
> `SAMA_RESILIENCE` was assembled from outsourcing + operational resilience + recovery planning and
> then had to declare "optional TPRM/BCM". **Five of the 18 rows below carry an "optional X"
> dependency — that is the over-splitting tell**, and a catalogue whose rows need each other is a
> sales problem, not a product line. The ratified answer keeps the sellable surface at 15 and moves
> navigability into **three buyer bundles** (Financial Institution / Government & Public Entity /
> Listed Company), which is how ServiceNow and Archer sell many products as few offers.
>
> The kernel list above, the packaging rules, the pricing hypothesis, the demo architecture and the
> sales blockers are **not** superseded.

| Package | Code | Buyer outcome | Required dependency |
|---|---|---|---|
| Enterprise Risk | `ERM` | risk appetite, register, KRI, treatment and acceptance | Kernel |
| Compliance & Controls | `COMPLIANCE` | obligations, common controls, assessments and issues | Kernel |
| Policy Management | `POLICY` | controlled policy lifecycle and attestations | Kernel |
| Internal Audit | `AUDIT` | universe, plan, engagements, findings and committee reporting | Kernel |
| Change Enablement | `CHANGE` | risk-based change, CAB/ECAB and post-implementation review | Kernel; optional Governance |
| Cyber GRC | `CYBER_GRC` | NCA/SAMA cyber governance, vulnerabilities and assurance | Kernel; optional Compliance |
| Security Operations Governance | `SOCOPS` | security event, playbook and assurance operations | Kernel; optional Cyber GRC |
| BCM & Crisis | `BCM` | BIA, strategy, BCP, exercises, crisis and recovery | Kernel |
| Privacy & Data Protection | `PRIVACY` | PDPL operations, RoPA, DPIA, DSR, breach and transfer | Kernel |
| Third-Party Risk | `TPRM` | onboarding, assessment, monitoring and offboarding | Kernel |
| SAMA Outsourcing & Resilience | `SAMA_RESILIENCE` | outsourcing, cloud, operational resilience and recovery planning | Kernel; optional TPRM/BCM |
| ESG & Sustainable Finance | `ESG` | disclosure, GHG, climate and green-finance assurance | Kernel |
| Access Governance | `ACCESS_GOV` | requests, SoD, recertification and break-glass | Kernel |
| Governance & Committees | `GOVERNANCE` | meetings, decisions, actions, board packs and signatures | Kernel |
| Ethics, Speak-up & Fraud | `ETHICS` | confidential intake, investigations, COI and fraud cases | Kernel |
| Regulatory Affairs | `REGULATORY` | horizon scanning, inspections, filings and regulator correspondence | Kernel; optional Compliance |
| Saudi Financial Services | `SAUDI_FS` | Sharia, AML/sanctions, open banking, recovery planning and counterparty risk | Kernel; selectable sub-packs |
| Data & AI Governance | `DATA_AI_GOV` | data classification, AI inventory, validation, drift and oversight | Kernel |

### Regulatory content packs

NCA ECC, SAMA CSF, SAMA BCM/outsourcing, PDPL, DGA, CMA, ISO and sector packs are versioned content—not code modules. Customers can activate several packs against common controls.

## Packaging rules

- A package is denied server-side when unlicensed.
- UI routes are generated from the entitlement manifest.
- Permissions are namespaced by package and shipped with role templates.
- Optional integrations degrade gracefully.
- Cross-package references use stable public IDs and read contracts.
- Disabling a package preserves data but blocks operation.
- Removal requires export, dependency analysis and retention handling.
- Package version and content-pack version are independently visible.

## Pricing readiness

The platform is not pricing-ready until cost-to-serve and buyer evidence are available. Recommended hypothesis:

- annual platform fee for kernel, environments and support;
- package subscription based on organisation scale and risk surface, not page count;
- regulatory content subscription with update service;
- implementation packages with fixed outcome milestones;
- premium connectors, managed controls and AI consumption;
- no per-seat penalty for occasional business control owners.

This must be validated against Saudi procurement behaviour and lighthouse customers.

## Sales demonstration architecture

Do not demonstrate menus. Demonstrate five stories:

1. NCA control → reusable evidence → deficiency → CAPA → executive posture.
2. SAMA material outsourcing → due diligence → approval → monitoring → exit test.
3. PDPL breach → assessment → clock → notification pack → lessons learned.
4. Enterprise risk → KRI breach → treatment → committee decision → board view.
5. Arabic executive dashboard → drill-down to signed decision and immutable evidence.

## Current sales blockers

- unproven package enforcement outside SOC Ops;
- no independently witnessed Saudi customer acceptance;
- incomplete accessibility evidence;
- current decision-integrity findings;
- production performance, DR and restore evidence unavailable;
- inconsistent UX depth and mock/demo residues in historical assessments;
- no retained benchmark against buyer implementation time.
