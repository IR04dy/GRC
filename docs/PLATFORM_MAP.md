# TSS GRC Platform Map

> Generated 2026-07-13 at commit `40b955e7` by `.tools/gen_platform_map.py` —
> **do not edit by hand**; re-run the generator after structural changes.
> Graph-database twin: [`PLATFORM_MAP.cypher`](PLATFORM_MAP.cypher) (Neo4j 5, MERGE-idempotent).
> Structural facts are code-detected; descriptions, regulator anchors and screen
> ownership are curated in the generator.

## At a glance

| Metric | Count |
|---|---:|
| Backend modules (`module/*`) | 58 — 22 canonical + 36 auxiliary/V1-era |
| REST controllers | 252 |
| API base paths (`@RequestMapping`) | 224 |
| DB schemas referenced by entities | 50 |
| Scheduler classes (`@Scheduled`) | 32 |
| Cross-module import edges | 130 |
| Frontend routes (screens) | 328 (0 unmapped to a module) |

## Topology

```
Browser (React SPA, tss-grc-ui-v2, :5173 dev / nginx :80 prod)
   │  same-origin /api/** + /ws
   ▼
Spring Boot modular monolith (:8084 dev / :8090 prod)
   ├─ security: JWT filter → TenantContext (org per request) → hasPermission guards
   ├─ 58 modules (controller / dto / entity / repository / service)
   ├─ shared services: workflow engine · evidence · exports · audit ledger ·
   │                   notifications · auto-CAPA
   └─ schedulers (org-explicit, off-request-safe)
   ▼
SQL Server TSS_GRC — 50+ schemas, Flyway V1..V296 (OFF in dev), soft deletes,
every query filtered by organisation_id
```

## Shared platform services

| Service | Provider | Consumed by (detected) |
|---|---|---|
| **WorkflowEngine** — Generic request engine — consumed via REST + record-anchored FE panels (detected from ModuleEngineRequestsPanel imports) | `workflow` | 16: `audit_lc`, `bcm`, `compliance`, `cyber`, `dashboard`, `evidence`, `incident`, `ndi`, `policy`, `privacy`, `reporting`, `risk`, `scm`, `socops`, `sust`, `tprm` |
| **EvidenceSubsystem** — SHA-256 uploads, four-eyes review, typed links, requests w/ SLA | `evidence` | 7: `bcm`, `controlmap`, `cyber`, `platform`, `reporting`, `sust`, `workflow` |
| **ExportStack** — PdfTheme+Amiri RTL, XlsxStyler, TabularExportService, ExportNaming, export audit | `reporting` | 8: `admin`, `ai`, `bcm`, `cyber`, `iam`, `ndi`, `socops`, `sust` |
| **AuditLedger** — platform.audit_log — before/after JSON, every export logged | `platform` | 41: `admin`, `ai`, `aml`, `approvals`, `audit_committee`, `audit_lc`, `auth`, `bcm`, `capa`, `ccm`, `change_mgmt`, `compliance`, `consumer`, `cpty`, `cyber`, `cyber_lc`, `ethics`, `evidence`, `fraud`, `governance`, `iam`, `incident`, `integrations`, `openbanking`, `opres`, `policy`, `privacy`, `privacy_lc`, `regchange`, `reginspect`, `regreporting`, `reporting`, `risk`, `risk_lc`, `rrp`, `sharia`, `socops`, `sust`, `tprm`, `workflow`, `workflow_submission` |
| **Notifications** — In-app notifications: notifyUser + broadcastToRole (SMTP pending relay) | `platform` | 6: `cyber`, `evidence`, `iam`, `socops`, `sust`, `workflow` |
| **AutoCapaEngine** — sust.corrective_action — idempotent (source_type, source_ref) auto-CAPAs for 10+ gap types; internal to sust (ck_ca_source whitelist gates new sources) | `sust` | — (provider-internal) |

## External integrations

| Integration | Owning module | Notes |
|---|---|---|
| Splunk | `integrations` | SIEM HEC ingest (HMAC fail-closed; stub+live) |
| CrowdStrike | `integrations` | EDR detections pull (stub+live) |
| Tenable | `integrations` | Vulnerability import (stub+live) |
| ServiceNow | `integrations` | ITSM ticket bridge (stub+live) |
| SMTP relay | `platform` | Outbound mail — configured via external.properties, off until a relay exists |
| LDAP / AD | `auth` | Directory auth + role mapping (optional) |
| Nafath SSO | `auth` | Saudi national SSO (screen present) |
| Sentry | `platform` | Error monitoring (optional DSN) |

## Regulator / framework anchors

| Regulator / framework | Modules |
|---|---|
| NCA ECC | `cyber`, `nca_ecc`, `iam` |
| SAMA CSF | `cyber`, `iam` |
| SAMA OpRes | `bcm`, `opres` |
| SAMA Outsourcing | `tprm`, `outsourcing` |
| PDPL / SDAIA | `privacy`, `datagov`, `cyber`, `iam` |
| CMA | `sust` |
| Tadawul ESG | `sust` |
| SOCPA IFRS S1/S2 | `sust` |
| GHG Protocol | `sust` |
| ISAE 3410 | `sust` |
| TCFD / IFRS S2 | `sust` |
| Saudi Green Initiative | `sust` |
| ICMA GBP / CCE | `sust` |
| RVCMC | `sust` |
| NCEC | `sust` |
| EU CBAM | `sust` |
| PCAF | `sust` |
| NCEMA | `bcm` |
| MWAN / SEEC | `sust` |

## Modules

Canonical modules first (the CLAUDE.md set), then auxiliary/V1-era aggregates.

### Canonical

#### `admin` — Committees (catalog/papers/actions), branding, licensing, org administration

- **Controllers:** 7 · **Schedulers:** 1
- **API bases (7):** `/api/admin/committees`, `/api/v1/committees/action-items`, `/api/v1/committees/calendar`, `/api/v1/committees/catalog`, `/api/v1/committees/papers`, `/api/v1/committees/registry`, `/api/v1/committees/resolutions`
- **DB schemas:** `admin`
- **Depends on:** `platform`, `reporting`
- **Uses services:** `AuditLedger`, `ExportStack`
- **Screens (10):** `/admin`, `/admin/help-topics`, `/admin/licensing`, `/committees`, `/committees/:committeeId/members`, `/committees/catalog`, `/committees/catalog/:templateCode`, `/committees/catalog/forms/:formCode`, `/onboarding`, `/settings`

#### `ai` — AI suggestions — control mapper, drafting aids

- **Controllers:** 5
- **API bases (5):** `/api/v1/ai`, `/api/v1/ai-lifecycle`, `/api/v1/ai/models`, `/api/v1/ai/models/{externalId}/audits`, `/api/v1/ai/models/{externalId}/drift-observations`
- **DB schemas:** `ai`, `ai_governance`
- **Depends on:** `intake`, `platform`, `reporting`
- **Uses services:** `AuditLedger`, `ExportStack`
- **Screens (8):** `/ai-lifecycle/dashboard`, `/ai-lifecycle/recertifications`, `/ai-lifecycle/retrainings`, `/ai-lifecycle/validations`, `/ai/models`, `/ai/models/:externalId`, `/ai/suggestions`, `/expert`

#### `auth` — JWT auth: login/refresh cookie, sessions, lockout, SSO/Nafath

- **Controllers:** 2
- **API bases (2):** `/api/auth`, `/api/v1/auth/sessions`
- **DB schemas:** `platform`
- **Depends on:** `iam`, `platform`
- **Uses services:** `AuditLedger`
- **Screens (4):** `/account/sessions`, `/login`, `/sso`, `/sso/nafath`

#### `bcm` — Business continuity: BIA, BCP/DR, exercises, crisis, NCEMA/OpRes packets

- **Controllers:** 37 · **Schedulers:** 1
- **API bases (27):** `/api/bcm/dr-plans`, `/api/v1/bcm`, `/api/v1/bcm/aars`, `/api/v1/bcm/bia-assessments`, `/api/v1/bcm/bia/quantitative`, `/api/v1/bcm/board-pack-section`, `/api/v1/bcm/comms`, `/api/v1/bcm/comms/v2` … +19 more
- **DB schemas:** `bcm`
- **Depends on:** `admin`, `capa`, `cyber`, `evidence`, `platform`, `reporting`, `tprm`
- **Uses services:** `AuditLedger`, `EvidenceSubsystem`, `ExportStack`, `WorkflowEngine`
- **Screens (37):** `/bcm`, `/bcm-dependency`, `/bcm-dependency/legacy`, `/bcm/aars/:id`, `/bcm/bia`, `/bcm/bia-quantitative`, `/bcm/comms/dispatch`, `/bcm/comms/templates`, `/bcm/comms/templates/:id`, `/bcm/compliance` … +27 more

#### `compliance` — Obligations, frameworks/controls, findings+CAPA, posture, horizon

- **Controllers:** 8 · **Schedulers:** 1
- **API bases (5):** `/api/v1/compliance`, `/api/v1/compliance/obligation-attestations`, `/api/v1/compliance/obligation-campaigns`, `/api/v1/compliance/obligations`, `/api/v1/compliance/regulatory-horizon`
- **DB schemas:** `compliance`
- **Depends on:** `attachment`, `platform`, `privacy`, `risk`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (16):** `/authorities`, `/authority/:code`, `/compliance`, `/compliance/obligation-campaigns`, `/compliance/obligation-campaigns/:id`, `/compliance/obligations`, `/compliance/obligations/:id`, `/compliance/posture`, `/controls/:id`, `/controls/ai-mapper` … +6 more

#### `cyber` — NCA ECC, assets, vulnerabilities+SLA, threat intel, 9-step incidents, cloud

- **Controllers:** 7 · **Schedulers:** 3
- **API bases (7):** `/api/v1/asm`, `/api/v1/cyber`, `/api/v1/cyber/cloud-services`, `/api/v1/cyber/export`, `/api/v1/cyber/policies`, `/api/v1/cyber/threat-intel`, `/api/v1/cyber/vuln-sla`
- **DB schemas:** `cyber`
- **Depends on:** `evidence`, `incident`, `platform`, `policy`, `reporting`
- **Uses services:** `AuditLedger`, `EvidenceSubsystem`, `ExportStack`, `Notifications`, `WorkflowEngine`
- **Screens (12):** `/assets`, `/cyber`, `/cyber-pdpl-bridge`, `/cyber/attack-surface`, `/cyber/attack-surface/assets/:externalId`, `/cyber/cloud-services`, `/cyber/dashboard`, `/cyber/threat-intel`, `/cyber/vuln-sla/dashboard`, `/cyber/vuln-sla/policies` … +2 more

#### `dashboard` — Executive dashboard aggregates

- **Controllers:** 1
- **API bases (1):** `/api/v1/dashboard`
- **Depends on:** `compliance`, `iam`, `incident`, `risk`, `workflow`
- **Uses services:** `WorkflowEngine`
- **Screens (2):** `/`, `/dashboard`

#### `datagov` — Data governance: retention, disposal, catalog, lineage, quality

- **Controllers:** 0

#### `evidence` — Evidence subsystem: SHA-256 items, four-eyes review, requests, typed links

- **Controllers:** 7 · **Schedulers:** 2
- **API bases (7):** `/api/evidence/chain`, `/api/v1/evidence`, `/api/v1/evidence/expert`, `/api/v1/evidence/frameworks`, `/api/v1/evidence/items`, `/api/v1/evidence/requests`, `/api/v1/evidence/reviews`
- **DB schemas:** `evidence`
- **Depends on:** `iam`, `platform`
- **Uses services:** `AuditLedger`, `Notifications`, `WorkflowEngine`
- **Screens (3):** `/evidence`, `/evidence-chain`, `/file-upload`

#### `iam` — Users/roles/permission matrix + access governance (maker-checker, SoD, recert, break-glass)

- **Controllers:** 4 · **Schedulers:** 4
- **API bases (3):** `/api/v1/admin/roles`, `/api/v1/admin/users`, `/api/v1/iam/governance`
- **DB schemas:** `iam`
- **Depends on:** `platform`, `reporting`
- **Uses services:** `AuditLedger`, `ExportStack`, `Notifications`
- **Screens (1):** `/access-governance`

#### `incident` — Platform incidents + RCA

- **Controllers:** 2
- **API bases (2):** `/api/incident/rca`, `/api/v1/incidents`
- **DB schemas:** `cyber`
- **Depends on:** `capa`, `platform`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (3):** `/incidents`, `/issues`, `/rca`

#### `intake` — Public intake portal + triage inbox

- **Controllers:** 1
- **API bases (1):** `/api/v1/intake`
- **DB schemas:** `intake`
- **Screens (4):** `/intake`, `/intake-inbox`, `/intake/:kind`, `/portal/track`

#### `ndi` — NDI governance + scheduled reports

- **Controllers:** 14 · **Schedulers:** 1
- **API bases (14):** `/api/ndi/audit`, `/api/ndi/compliance`, `/api/ndi/cycles`, `/api/ndi/dashboard`, `/api/ndi/domains`, `/api/ndi/evidence`, `/api/ndi/framework`, `/api/ndi/gaps` … +6 more
- **DB schemas:** `ndi`
- **Depends on:** `platform`, `reporting`, `risk`
- **Uses services:** `ExportStack`, `WorkflowEngine`
- **Screens (1):** `/ndi`

#### `platform` — Platform services: audit ledger, notifications, brand theme, org settings

- **Controllers:** 19 · **Schedulers:** 2
- **API bases (17):** `/api/platform/saved-views`, `/api/v1`, `/api/v1/audit-log`, `/api/v1/automation`, `/api/v1/calendar`, `/api/v1/controls/ai`, `/api/v1/departments`, `/api/v1/files` … +9 more
- **DB schemas:** `platform`
- **Depends on:** `audit_lc`, `bcm`, `capa`, `evidence`, `iam`, `ndi`, `privacy`, `privacy_lc`, `risk`
- **Uses services:** `EvidenceSubsystem`
- **Screens (10):** `*`, `/audit-log`, `/calendar`, `/communications`, `/lifecycle-overview`, `/notifications`, `/notifications/channels`, `/notifications/dispatch`, `/notifications/outbox`, `/portal/trust-center`

#### `privacy` — PDPL: RoPA, DPIA, DSR (30-day), breach (72-hour), consent, transfers

- **Controllers:** 12
- **API bases (12):** `/api/privacy/cross-border-transfers`, `/api/privacy/cyber-bridge`, `/api/privacy/data-sharing`, `/api/privacy/disposal-certificates`, `/api/privacy/notices`, `/api/privacy/processor-assessments`, `/api/privacy/ropa-workflows`, `/api/v1/privacy` … +4 more
- **DB schemas:** `privacy`
- **Depends on:** `platform`, `tprm`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (18):** `/data-lifecycle`, `/data-sharing`, `/dpias/:id`, `/pdpl`, `/pdpl-extensions`, `/portal/dsr`, `/privacy`, `/privacy/breaches/:id`, `/privacy/consents/:id`, `/privacy/dashboard` … +8 more

#### `reporting` — Export engine: branded PDF (Amiri RTL), XLSX/DOCX/PPTX, packets, export history

- **Controllers:** 2 · **Schedulers:** 1
- **API bases (2):** `/api/v1/reporting`, `/api/v1/reporting/schedules`
- **DB schemas:** `reporting`
- **Depends on:** `change_mgmt`, `compliance`, `ethics`, `evidence`, `platform`, `risk`, `sust`
- **Uses services:** `AuditLedger`, `EvidenceSubsystem`, `WorkflowEngine`
- **Screens (3):** `/export-history`, `/reports/centre`, `/reports/schedules`

#### `risk` — ERM: register, appetite, KRI, treatments, acceptance, FAIR, scenarios

- **Controllers:** 14 · **Schedulers:** 2
- **API bases (7):** `/api/v1/risk`, `/api/v1/risk/bowtie`, `/api/v1/risk/categories`, `/api/v1/risk/fair`, `/api/v1/risk/kri`, `/api/v1/risk/reports`, `/api/v1/risk/risks`
- **DB schemas:** `risk`
- **Depends on:** `admin`, `iam`, `platform`, `policy`, `risk_lc`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (11):** `/emerging-risk`, `/risk`, `/risk-scenarios`, `/risk/:id`, `/risk/bowtie`, `/risk/bowtie/:externalId`, `/risk/dashboard`, `/risk/fair`, `/risk/kri/:externalId`, `/risk/kri/admin` … +1 more

#### `scm` — Supply chain: fourth-party map, contract clauses, vendor portal

- **Controllers:** 5
- **API bases (5):** `/api/scm/contract-clauses`, `/api/scm/fourth-party`, `/api/scm/resilience`, `/api/scm/resilience/tests`, `/api/v1/scm`
- **DB schemas:** `scm`
- **Depends on:** `tprm`
- **Uses services:** `WorkflowEngine`
- **Screens (7):** `/buyer-gap`, `/contract-clauses`, `/fourth-party`, `/portal/vendor`, `/scm`, `/scm/dashboard`, `/supplier-resilience`

#### `socops` — SOC operations: 20 aggregates — SIEM events, DLP, forensics custody, reviews

- **Controllers:** 22 · **Schedulers:** 1
- **API bases (22):** `/api/v1/socops`, `/api/v1/socops/appsec`, `/api/v1/socops/awareness`, `/api/v1/socops/brand-monitoring`, `/api/v1/socops/crypto-assets`, `/api/v1/socops/cyber-insurance`, `/api/v1/socops/detections`, `/api/v1/socops/dlp` … +14 more
- **DB schemas:** `socops`
- **Depends on:** `cyber`, `incident`, `platform`, `reporting`
- **Uses services:** `AuditLedger`, `ExportStack`, `Notifications`, `WorkflowEngine`
- **Screens (23):** `/socops`, `/socops/:resource/:id`, `/socops/appsec`, `/socops/awareness`, `/socops/brand-monitoring`, `/socops/crypto-assets`, `/socops/cyber-insurance`, `/socops/dashboard`, `/socops/detections`, `/socops/dlp` … +13 more

#### `sust` — Sustainability/ESG: GHG (15 Scope-3 cats), disclosures, green finance/portfolio, SGI, horizon, auto-CAPA

- **Controllers:** 21 · **Schedulers:** 3
- **API bases (17):** `/api/sustainability/climate-risks`, `/api/sustainability/cma-disclosures`, `/api/sustainability/corrective-actions`, `/api/sustainability/dashboard`, `/api/sustainability/evidence`, `/api/sustainability/evidence-links`, `/api/sustainability/ghg`, `/api/sustainability/ghg/factors` … +9 more
- **DB schemas:** `sust`
- **Depends on:** `evidence`, `iam`, `platform`, `reporting`, `risk`, `tprm`
- **Uses services:** `AuditLedger`, `EvidenceSubsystem`, `ExportStack`, `Notifications`, `WorkflowEngine`
- **Screens (5):** `/esg`, `/esg/climate-report`, `/esg/financed-emissions`, `/esg/tcfd-scenarios`, `/green-finance`

#### `tprm` — Third-party risk: vendors, assessments, outsourcing, BCP campaigns

- **Controllers:** 6
- **API bases (5):** `/api/tprm/cyber-assessments`, `/api/v1/tprm`, `/api/v1/tprm/bcp-attestations`, `/api/v1/tprm/bcp-campaigns`, `/api/v1/tprm/outsourcing`
- **DB schemas:** `tprm`
- **Depends on:** `bcm`, `capa`, `platform`, `privacy`, `scm`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (8):** `/tprm`, `/tprm/attestations/:token/submit`, `/tprm/bcp-campaigns`, `/tprm/bcp-campaigns/:id`, `/tprm/outsourcing`, `/tprm/outsourcing/:id`, `/tprm/outsourcing/admin/sama-pending`, `/vendors/:id`

#### `workflow` — Workflow request engine: approvals/reviews/acks/broadcasts, SLA+escalation, EXTERNAL tokens

- **Controllers:** 5 · **Schedulers:** 3
- **API bases (5):** `/api/public/workflow-requests`, `/api/v1/workflow`, `/api/v1/workflow/chains`, `/api/v1/workflows/recipient-lists`, `/api/v1/workflows/requests`
- **DB schemas:** `workflow`
- **Depends on:** `admin`, `evidence`, `iam`, `platform`
- **Uses services:** `AuditLedger`, `EvidenceSubsystem`, `Notifications`
- **Screens (4):** `/approvals`, `/attestations`, `/public/workflow-requests/:token`, `/workflow-versions`

### Auxiliary / V1-era

#### `aml` — Anti-money-laundering sanctions screening (auxiliary)

- **Controllers:** 1 · **Schedulers:** 1
- **API bases (1):** `/api/v1/aml/sanctions`
- **DB schemas:** `aml`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (6):** `/aml/sanctions/checks`, `/aml/sanctions/checks/:id`, `/aml/sanctions/dashboard`, `/aml/sanctions/lists`, `/aml/sanctions/lists/:id`, `/aml/sanctions/screen`

#### `approvals` — Legacy approval-chain endpoints (superseded by the workflow engine)

- **Controllers:** 1
- **API bases (1):** `/api/approvals/requests`
- **DB schemas:** `approvals`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`

#### `attachment` — Generic file-attachment endpoints (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/attachments`
- **DB schemas:** `platform`

#### `audit_committee` — Audit-committee dashboard aggregate

- **Controllers:** 1
- **API bases (1):** `/api/v1/audit-committee/dashboard`
- **DB schemas:** `audit_committee`
- **Depends on:** `ai`, `capa`, `ccm`, `compliance`, `governance`, `incident`, `platform`, `risk`, `sust`, `tprm`
- **Uses services:** `AuditLedger`
- **Screens (2):** `/audit-committee/dashboard`, `/audit-committee/snapshots/:id`

#### `audit_eng` — Internal-audit engagements (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/audit-eng/engagements`
- **DB schemas:** `audit_eng`

#### `audit_lc` — Internal-audit lifecycle screens aggregate

- **Controllers:** 3
- **API bases (3):** `/api/audit-lc`, `/api/audit-lc/findings`, `/api/v1/ia`
- **DB schemas:** `audit_lc`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (7):** `/audit-lifecycle`, `/audit-workspace`, `/audit-workspace/engagement/:id`, `/audit/ia/engagements/:id`, `/audit/ia/plans`, `/audit/ia/plans/:id`, `/audit/ia/universe`

#### `bcm_lc` — BCM lifecycle screens aggregate

- **Controllers:** 1
- **API bases (1):** `/api/bcm-lc/invocations`
- **DB schemas:** `bcm_lc`
- **Screens (1):** `/bcm-lifecycle`

#### `board_pack` — Executive board-pack export

- **Controllers:** 1
- **API bases (1):** `/api/board-pack/packs`
- **DB schemas:** `board_pack`
- **Screens (1):** `/board-pack`

#### `capa` — Platform CAPA tasks (global register; distinct from sust auto-CAPA)

- **Controllers:** 1
- **API bases (1):** `/api/capa/tasks`
- **DB schemas:** `capa`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (2):** `/capa`, `/capa/:id`

#### `ccm` — Continuous control monitoring (locked-down SQL runner)

- **Controllers:** 1 · **Schedulers:** 1
- **API bases (1):** `/api/v1/ccm`
- **DB schemas:** `ccm`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (3):** `/ccm/dashboard`, `/ccm/tests`, `/ccm/tests/:externalId`

#### `change_mgmt` — Change-management register (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/changes`
- **DB schemas:** `change_mgmt`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (1):** `/changes`

#### `consumer` — Consumer-protection complaints (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/complaints`
- **DB schemas:** `consumer`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (4):** `/complaints`, `/complaints/:id`, `/public/complaint`, `/public/track-complaint`

#### `controlmap` — Control crosswalk / framework mapping

- **Controllers:** 1
- **API bases (1):** `/api/v1/control-links`
- **DB schemas:** `evidence`
- **Depends on:** `evidence`
- **Uses services:** `EvidenceSubsystem`
- **Screens (1):** `/control-crosswalk`

#### `cpty` — Counterparty register (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/cpty`
- **DB schemas:** `cpty`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (3):** `/cpty/counterparties`, `/cpty/counterparties/:id`, `/cpty/dashboard`

#### `cyber_lc` — Cyber lifecycle screens aggregate

- **Controllers:** 1
- **API bases (1):** `/api/cyber-lc/vulnerabilities`
- **DB schemas:** `cyber_lc`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (1):** `/cyber-lifecycle`

#### `data_officer` — Data-officer privacy console

- **Controllers:** 1
- **API bases (1):** `/api/v1/data-officer`
- **DB schemas:** `data_officer`
- **Depends on:** `platform`
- **Screens (11):** `/data-officer/breaches`, `/data-officer/catalog`, `/data-officer/consents`, `/data-officer/dashboard`, `/data-officer/dpia`, `/data-officer/dsr`, `/data-officer/ethics`, `/data-officer/ndr`, `/data-officer/notices`, `/data-officer/quality` … +1 more

#### `dataclass` — Data classification register (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/dataclass`
- **DB schemas:** `dataclass`
- **Depends on:** `platform`
- **Screens (3):** `/dataclass/assets`, `/dataclass/dashboard`, `/dataclass/flows`

#### `ethics` — Ethics: whistleblower, conflicts of interest (auxiliary)

- **Controllers:** 7
- **API bases (7):** `/api/v1/coi`, `/api/v1/ethics/aml-alerts`, `/api/v1/ethics/cases`, `/api/v1/ethics/disclosures`, `/api/v1/ethics/kyc`, `/api/v1/ethics/whistleblower`, `/api/v1/ethics/whistleblower/retaliation`
- **DB schemas:** `ethics`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (9):** `/ethics`, `/ethics/coi`, `/ethics/coi/campaigns/:id`, `/ethics/whistleblower/cases`, `/ethics/whistleblower/cases/:id`, `/ethics/whistleblower/retaliation`, `/ethics/whistleblower/status/:token`, `/ethics/whistleblower/submit`, `/portal/speakup`

#### `fraud` — Fraud register (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/fraud`
- **DB schemas:** `fraud`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (4):** `/fraud/cases`, `/fraud/cases/:id`, `/fraud/cases/new`, `/fraud/dashboard`

#### `governance` — Governance charters/bodies (auxiliary)

- **Controllers:** 2
- **API bases (2):** `/api/v1/governance/pivot`, `/api/v1/governance/three-lines`
- **DB schemas:** `platform`
- **Depends on:** `audit_lc`, `capa`, `ccm`, `iam`, `platform`, `policy`, `privacy_lc`, `risk`
- **Uses services:** `AuditLedger`
- **Screens (4):** `/governance/pivot/control/:controlId`, `/governance/pivot/finding/:findingId`, `/governance/pivot/risk/:riskId`, `/governance/three-lines`

#### `integrations` — SIEM/EDR/VM/ITSM connectors: Splunk, CrowdStrike, Tenable, ServiceNow

- **Controllers:** 6 · **Schedulers:** 4
- **API bases (6):** `/api/v1/integrations/crowdstrike`, `/api/v1/integrations/servicenow`, `/api/v1/integrations/servicenow/webhook`, `/api/v1/integrations/splunk`, `/api/v1/integrations/splunk/webhook`, `/api/v1/integrations/tenable`
- **DB schemas:** `platform`
- **Depends on:** `cyber`, `platform`, `socops`
- **Uses services:** `AuditLedger`
- **Screens (2):** `/connectors`, `/connectors/:key`

#### `knowledge` — Knowledge base

- **Controllers:** 1
- **API bases (1):** `/api/kb/articles`
- **DB schemas:** `kb`
- **Screens (4):** `/knowledge-base`, `/knowledge-base/:slug`, `/knowledge-base/:slug/edit`, `/knowledge-base/new`

#### `nca_ecc` — NCA ECC self-assessment filings + export

- **Controllers:** 1
- **API bases (1):** `/api/nca-ecc/filings`
- **DB schemas:** `nca_ecc`
- **Screens (2):** `/nca-ecc-export`, `/nca-ecc/filings`

#### `openbanking` — Open-banking register (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/openbanking`
- **DB schemas:** `openbanking`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (5):** `/openbanking/consents`, `/openbanking/dashboard`, `/openbanking/incidents`, `/openbanking/tpps`, `/openbanking/tpps/:id`

#### `opres` — SAMA operational-resilience packet

- **Controllers:** 1
- **API bases (1):** `/api/v1/opres`
- **DB schemas:** `opres`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (3):** `/opres/assessments`, `/opres/assessments/:id`, `/opres/dashboard`

#### `outsourcing` — SAMA outsourcing register (admin approval queue)

- **Controllers:** 1
- **API bases (1):** `/api/v1/outsourcing`
- **DB schemas:** `outsourcing`
- **Depends on:** `platform`
- **Screens (3):** `/outsourcing/arrangements`, `/outsourcing/cloud-services`, `/outsourcing/dashboard`

#### `policy` — Policy documents: versioned files, review cycle, SoD approvals

- **Controllers:** 1 · **Schedulers:** 1
- **API bases (1):** `/api/policy`
- **DB schemas:** `policy`
- **Depends on:** `attachment`, `compliance`, `platform`
- **Uses services:** `AuditLedger`, `WorkflowEngine`
- **Screens (4):** `/policies/:id`, `/policy`, `/policy-management`, `/policy-templates`

#### `privacy_lc` — Privacy lifecycle screens aggregate (orphaned *_lc tables landmine)

- **Controllers:** 1
- **API bases (1):** `/api/privacy-lc/dsr-cases`
- **DB schemas:** `privacy_lc`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (1):** `/privacy-lifecycle`

#### `regchange` — Regulatory-change register

- **Controllers:** 1
- **API bases (1):** `/api/regchange/changes`
- **DB schemas:** `regchange`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (1):** `/regulatory-change`

#### `reginspect` — Regulatory inspections (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/reginspect`
- **DB schemas:** `reginspect`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (3):** `/reginspect/dashboard`, `/reginspect/inspections`, `/reginspect/inspections/:id`

#### `regreporting` — Regulatory reporting calendar (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/regreporting`
- **DB schemas:** `regreporting`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (4):** `/regreporting/dashboard`, `/regreporting/filings`, `/regreporting/filings/:id`, `/regreporting/obligations`

#### `risk_lc` — Risk lifecycle screens aggregate

- **Controllers:** 1
- **API bases (1):** `/api/risk-lc/treatment-plans`
- **DB schemas:** `risk_lc`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (1):** `/risk-lifecycle`

#### `rrp` — Recovery & resolution planning (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/rrp`
- **DB schemas:** `rrp`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (3):** `/rrp/cycles`, `/rrp/cycles/:id`, `/rrp/dashboard`

#### `sharia` — Shariah governance (auxiliary)

- **Controllers:** 1
- **API bases (1):** `/api/v1/sharia`
- **DB schemas:** `sharia`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (6):** `/sharia/audit-findings`, `/sharia/dashboard`, `/sharia/nc-events`, `/sharia/products`, `/sharia/resolutions`, `/sharia/scholars`

#### `signature` — E-signature requests

- **Controllers:** 2
- **API bases (2):** `/api/signature/transactions`, `/api/v1/signatures`
- **DB schemas:** `platform`, `signature`
- **Depends on:** `iam`
- **Screens (1):** `/signatures`

#### `workflow_submission` — Workflow submissions + structured request forms

- **Controllers:** 1
- **API bases (1):** `/api/v1/workflow-submissions`
- **DB schemas:** `workflow`
- **Depends on:** `platform`
- **Uses services:** `AuditLedger`
- **Screens (32):** `/workflows`, `/workflows/bcm/exercise`, `/workflows/chains`, `/workflows/chains/:id`, `/workflows/chains/pending`, `/workflows/chains/templates`, `/workflows/compliance/control-test`, `/workflows/erm/risk-acceptance`, `/workflows/erm/risk-acceptance/:riskId`, `/workflows/erm/risk-identification` … +22 more

## DB schema census

`admin`, `ai`, `ai_governance`, `aml`, `approvals`, `audit_committee`, `audit_eng`, `audit_lc`, `bcm`, `bcm_lc`, `board_pack`, `capa`, `ccm`, `change_mgmt`, `compliance`, `consumer`, `cpty`, `cyber`, `cyber_lc`, `data_officer`, `dataclass`, `ethics`, `evidence`, `fraud`, `iam`, `intake`, `kb`, `nca_ecc`, `ndi`, `openbanking`, `opres`, `outsourcing`, `platform`, `policy`, `privacy`, `privacy_lc`, `regchange`, `reginspect`, `regreporting`, `reporting`, `risk`, `risk_lc`, `rrp`, `scm`, `sharia`, `signature`, `socops`, `sust`, `tprm`, `workflow`

## Querying the graph

```cypher
// which modules depend on the workflow engine?
MATCH (m:Module)-[:USES]->(s:Service {id:'WorkflowEngine'}) RETURN m.name;
// blast radius: what breaks if the evidence module changes?
MATCH (m:Module)-[:DEPENDS_ON]->(e:Module {name:'evidence'}) RETURN m.name;
// every screen + API surface a regulator audit would touch
MATCH (m:Module)-[:ADDRESSES]->(r:Regulator {name:'NCA ECC'})
OPTIONAL MATCH (sc:Screen)-[:SERVED_BY]->(m) OPTIONAL MATCH (m)-[:EXPOSES]->(a:ApiBase)
RETURN m.name, collect(DISTINCT sc.path), collect(DISTINCT a.path);
```

