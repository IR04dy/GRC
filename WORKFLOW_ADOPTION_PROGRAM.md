# Workflow Engine Adoption Program — migrating module actions onto the engine

The engine (WF-1…WF-10, on master) is the platform request/actor/routing/SLA/lifecycle backbone.
This program migrates the modules' generic "Send / Request / Assign / Follow-up / Escalate /
Report" buttons to route through it, so each becomes a real, tracked, routed `WorkflowRequest`
anchored to its source record — the audit's core "disconnected screens → workflow-driven system"
fix. Delivered in **verified slices** (not a blind mass-rewrite). Branch `feat/workflow-adoption`.

## The adoption mechanism (built — reused by every module)

`components/workflow/RaiseRequestButton.tsx` — a drop-in, engine-routed action. A module places
`<RaiseRequestButton sourceModule="X" [sourceRecordType sourceRecordId defaultTitleEn defaultRequestType label onCreated] />`
on any page/record; it opens the full composer (title EN/AR, request type, routing
SEQUENTIAL/PARALLEL·ALL·ANY/BROADCAST_ACK, due, priority, remind-before, escalation role,
description) + the WF-8 `WorkflowActorSelector` (with owner **suggestions** for the module and
saved **distribution lists**), then create→send. The created request is anchored via the engine's
existing `sourceModule / sourceRecordType / sourceRecordId` columns (verified live: the anchor
round-trips and persists). No backend change needed — the engine API is already generic.

## Adoption status by module

| Module | Page (injection point) | Status |
|---|---|---|
| **Risk / ERM** | `emerging-risk/EmergingRiskPage.tsx` (header) — `sourceModule=RISK` | ✅ Slice 1 |
| **Cyber** | `cyber/CyberPage.tsx` (header) — `sourceModule=CYBER` | ✅ Slice 1 |
| **Compliance / Policy** | `compliance/CompliancePage.tsx` (header) — `sourceModule=COMPLIANCE` | ✅ Slice 1 |
| **Privacy / PDPL** | `privacy/PrivacyPage.tsx` (header) — `sourceModule=PRIVACY` | ✅ Slice 1 |
| **BCM** | `bcm/BcmPage.tsx` (`actions={` @ ~L480) — `sourceModule=BCM` | ⏳ Slice 2 |
| **SOC Ops** | `socops/SocOpsDetailPage.tsx` / `SocOpsEventsPage.tsx` — `sourceModule=SOCOPS` | ⏳ Slice 2 |
| **Third-Party / TPRM** | `tprm/TprmPage.tsx` (`actions={` @ ~L199) — `sourceModule=TPRM` | ⏳ Slice 2 |
| **Supply Chain / SCM** | `scm/ScmPage.tsx` (`actions={` @ ~L767) — `sourceModule=SCM` | ⏳ Slice 2 |
| **ESG / Sustainability** | `esg/EsgPage.tsx` (`actions={` @ ~L190) — `sourceModule=ESG` | ⏳ Slice 2 |
| **Evidence** | evidence workspace/detail — `sourceModule=EVIDENCE` (note: evidence already has its own real request lifecycle via WF-4 bridge; adoption is the generic entry) | ⏳ Slice 3 |
| **NDI / SDAIA** | datagov/NDI pages — `sourceModule=NDI` | ⏳ Slice 3 |
| **Incident** | incident register/detail — `sourceModule=INCIDENT` | ⏳ Slice 3 |
| **Internal Audit** | audit engagement pages — `sourceModule=AUDIT` | ⏳ Slice 3 |
| **Reporting** | report distribution — `sourceModule=REPORTING` (REPORT_DISTRIBUTION type) | ⏳ Slice 3 |

## Slice 1 (this batch) — verified

Shared `RaiseRequestButton` + wired into Risk/Cyber/Compliance/Privacy page headers. tsc clean;
all 5 FE modules Vite-transform 200; live anchor round-trip proven (CYBER/CyberIncident/#4321
persisted + returned). No backend change → full suite unchanged (2214/0/0 from WF-10). Ratchet
unaffected (no new endpoints).

## Remaining slices — mechanical

Slices 2–3 are the same one-line insertion per page (import + `<RaiseRequestButton sourceModule=…/>`
in the header `actions`), using the injection points listed above. Where a page has a per-record
detail view, pass `sourceRecordType`/`sourceRecordId` so the request anchors to that record and the
detail page can show its linked requests. No further engine or DB work is required.

## Non-goals / notes

- This ADDS an engine-routed entry point to each module; it does not rip out module-specific
  buttons that already do real work (e.g. evidence review, BCM exercise runs) — those were
  hardened in their own remediation loops and remain.
- Outbound email for EXTERNAL recipients remains deferred (no SMTP/dispatch bean platform-wide).
