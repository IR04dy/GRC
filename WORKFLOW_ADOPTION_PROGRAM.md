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
| **BCM** | `bcm/BcmPage.tsx` (`actions={` @ ~L480) — `sourceModule=BCM` | ✅ Slice 2 |
| **SOC Ops** | `socops/SocOpsDetailPage.tsx` / `SocOpsEventsPage.tsx` — `sourceModule=SOCOPS` | ✅ Slice 2 |
| **Third-Party / TPRM** | `tprm/TprmPage.tsx` (`actions={` @ ~L199) — `sourceModule=TPRM` | ✅ Slice 2 |
| **Supply Chain / SCM** | `scm/ScmPage.tsx` (`actions={` @ ~L767) — `sourceModule=SCM` | ✅ Slice 2 |
| **ESG / Sustainability** | `esg/EsgPage.tsx` (`actions={` @ ~L190) — `sourceModule=ESG` | ✅ Slice 2 |
| **Evidence** | evidence workspace/detail — `sourceModule=EVIDENCE` (note: evidence already has its own real request lifecycle via WF-4 bridge; adoption is the generic entry) | ✅ Slice 3 |
| **NDI / SDAIA** | datagov/NDI pages — `sourceModule=NDI` | ✅ Slice 3 |
| **Incident** | incident register/detail — `sourceModule=INCIDENT` | ✅ Slice 3 |
| **Internal Audit** | audit engagement pages — `sourceModule=AUDIT` | ✅ Slice 3 |
| **Reporting** | report distribution — `sourceModule=REPORTING` (REPORT_DISTRIBUTION type) | ✅ Slice 3 |

## Slice 1 (this batch) — verified

Shared `RaiseRequestButton` + wired into Risk/Cyber/Compliance/Privacy page headers. tsc clean;
all 5 FE modules Vite-transform 200; live anchor round-trip proven (CYBER/CyberIncident/#4321
persisted + returned). No backend change → full suite unchanged (2214/0/0 from WF-10). Ratchet
unaffected (no new endpoints).

## Slice 2 — verified

Wired `RaiseRequestButton` into BCM (`BcmPage`), SOC Ops (`SocOpsEventsPage` header **and**
`SocOpsDetailPage` — the detail one anchored to the specific record via
`sourceRecordType={config.entityType} sourceRecordId={row.id}`), TPRM (`TprmPage`), SCM (`ScmPage`),
ESG (`EsgPage`). Fragment-pattern headers got a one-line insert; ESG/SOC-events single-element
`actions` were wrapped in a fragment. tsc clean; all pages Vite-transform 200.

## Slice 3 — verified

Wired the remaining modules: Evidence (`EvidencePage`), NDI/SDAIA (`NdiPage` — header gained an
`actions` slot), Incident (`IncidentsPage`), Internal Audit (`AuditWorkspacePage` — header gained an
`actions` slot), Reporting (`ReportsCentrePage` — button added into the existing actions row,
`defaultRequestType="REPORT_DISTRIBUTION"`). tsc clean; all pages Vite-transform 200.

## Program complete — all 14 modules on the engine

Every module in the table now raises real, tracked, routed `WorkflowRequest`s through the platform
engine. Verification (2026-07-06): `npx tsc --noEmit` exit 0; all 11 edited pages + the shared
component Vite-transform 200; **live end-to-end round-trip proven for a brand-new `sourceModule`
value** (`INCIDENT` / `SecurityIncident` / #90210 → create 200 → send 200 → `SENT`, anchor persisted
in `workflow.requests`, then cleaned up). Confirmed the backend stores `sourceModule` as a free
`@NotBlank @Size(max=40)` string with **no enum/whitelist**, so every module string is accepted.
No Java changed → backend suite unchanged (2214/0/0); ratchet unaffected (no new endpoints).

## Slice 4 — live engine-requests panels (replacing the mock in-flight façade)

RaiseRequestButton lets a module *create* real engine requests; slice 4 closes the loop so a module
also *sees* its real in-flight requests live. Added an optional `sourceModule` filter to
`GET /api/v1/workflows/requests` (repository finders + service overload + controller param;
org-scoped, case-insensitive, exclusive — no new endpoint, ratchet unaffected) and a new
`components/workflows/ModuleEngineRequestsPanel.tsx` that fetches real requests for a module and
lists code/title/status/priority/due + escalation, deep-linking to `/workflows/requests/:id`.

Wired into **all 7 modules that rendered the mock `ActiveWorkflowsPanel`** (Cyber proof + BCM,
Compliance, Evidence, Privacy, Risk, TPRM) — the live panel sits **above** the mock one in each
Workflows tab (additive; the mock is retained as an illustrative cross-module lifecycle view, not
ripped out). The other 7 modules had no in-flight-workflows tab / mock façade to replace.

Verified: backend `list_filtersBySourceModule` test + authz ratchet green (41/0/0); rebuilt +
restarted :8084; **live 7/7** (CYBER request present under `?sourceModule=CYBER`, absent under RISK,
case-insensitive, cleaned up); tsc exit 0; all edited pages Vite-transform 200. Commits: proof
`f87146e1`, fan-out `19523ae1`.

## Non-goals / notes

- This ADDS an engine-routed entry point to each module; it does not rip out module-specific
  buttons that already do real work (e.g. evidence review, BCM exercise runs) — those were
  hardened in their own remediation loops and remain.
- Outbound email for EXTERNAL recipients remains deferred (no SMTP/dispatch bean platform-wide).
