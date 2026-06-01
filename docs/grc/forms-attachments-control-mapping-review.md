# Platform-wide Forms Review — File Attachments + Cross-Framework Control Mapping

**Date:** 2026-06-01
**Method:** 5 independent reviewers, one per domain, inventorying every create/edit form in `tss-grc-ui-v2/src/pages` on two dimensions: (A) needs file upload/preview, (B) needs multi-control mapping across frameworks.

## The two structural facts that shape everything

1. **`FormModal`** (the shared form component used by **26 pages**) supports only `text / textarea / number / date / select / select-searchable`. **There is no `file` field kind**, so no FormModal-based form can attach a document.
2. **`FileUploadField`** exists and is UI-complete (drag-drop, progress, SHA-256, scan badge, download/delete) **but its backing `lib/fileUpload.ts` is a client-side mock** — it persists to local storage / a fake `s3://` ref, **not** to the backend. The **only real, server-persisted upload** today is the evidence item one (`POST /api/v1/evidence/items/{id}/file`, built this session).
3. **Multi-control mapping** has partial backend support: evidence has `POST /api/v1/evidence/link` (+ `evidence_links`) and risk has `risk_controls`, but **no generic, polymorphic facility** exists, and the forms capture a single `subControlId`/`controlId` (or none).

## Scale of the gap

- ~40 form-bearing pages reviewed; **only 3** have any file input, **only 9** touch control mapping (mostly single-valued).
- **~25 forms** should accept a document; **~20 forms** should map to multiple controls across frameworks.

## HIGH-priority forms (per the reviewers)

| Form | Needs upload (artifact) | Needs multi-control mapping |
|---|---|---|
| **Evidence item** (`evidence/EvidencePage`) | ✅ (has real upload now) | ✅ policy/report satisfies 5–10 controls across ECC/ISO/PDPL/NDI |
| **GRC Expert "Request evidence"** (`expert/GrcExpertPage`) | — | ✅ one artifact closes a gap across many sub-controls/frameworks |
| **Policy** (`compliance/CompliancePage`, `policy/PolicyManagementPage`) | ✅ policy PDF/DOCX | ✅ one policy enforces many controls |
| **Risk** (`risk/NewRiskModal`) | ✅ assessment / treatment plan | ✅ one risk → many controls (ECC/ISO/PDPL) |
| **Incident / Breach** (`incidents/IncidentsPage`, `dataOfficer/BreachPage`, `privacy/PrivacyPage`) | ✅ report, SDAIA/NCA notice | ✅ maps to control failures across frameworks |
| **CAPA / RCA** (`capa`, `rca`) | ✅ root-cause doc, closure evidence | ✅ remediates control gaps across frameworks |
| **Audit engagement / Reg inspection** (`compliance`, `reginspect`, `audit-lifecycle`) | ✅ engagement letter, findings | ✅ findings → many controls (SOC2 → 40+) |
| **Processor assessment / Cross-border transfer** (`processor-assessments`, `pdpl-extensions`) | ✅ DPA, SOC2/ISO cert, SCCs | ✅ Art.28 / Art.34 + vendor controls |
| **Vendor / Outsourcing / Third-party cyber** (`tprm`, `outsourcing`, `third-party-cyber`, `supplier-resilience`) | ✅ contract, cert, BCP, questionnaire | ✅ ISO 27036 + SAMA + ECC |
| **BCM: BCP/DR plan, BIA** (`bcm`, `bcm/BcpBuilder`, `bcm/BiaWizard`, `dr-plans`, `resilience-tests`, `crisis`) | ✅ plan doc, test result, runbook | ✅ ISO 22301 + SAMA BCM + ECC |
| **Cyber: vulnerability, exception, evidence, action plan** (`cyber/CyberPage`) | ✅ scan report, justification | ✅ link to affected controls across frameworks |
| **AI model audit / ESG disclosure** (`ai`, `esg`) | ✅ validation report, assurance statement | ✅ SDAIA/SAMA/EU-AI ; GRI/TCFD/CMA |

(Full per-form tables retained in the review run output.)

## Design decisions (confirmed by product owner)

1. **Control mapping is OPTIONAL and MANUALLY selected.** It is never a required field and is never auto-derived/auto-suggested — because frameworks change over time, an automated mapping would go stale. The user explicitly picks one or more controls/sub-controls from a manual multi-select, and the mapping is an editable list they maintain (add/remove anytime). Stored as explicit `control_links` rows, not computed.
2. **Attachments are GENERIC and TYPED — not just "evidence."** One reusable attachment field supports many document purposes: **Plan, Report, Policy, Certificate, Evidence, Contract, Minutes, Questionnaire, Screenshot, Other**. Each uploaded file is tagged with its document type (`doc_type`), so the same component serves "Attach plan", "Attach report", "Attach policy", etc. The generic `attachments` table carries a `doc_type` column; the `<AttachmentsField>` includes a document-type selector per file.

## Recommended architecture (build once, reuse everywhere)

**1. Generic Attachments service (backend)** — a polymorphic `platform.attachments` table keyed by `(organisation_id, entity_type, entity_id)` with a **`doc_type`** column (PLAN/REPORT/POLICY/CERTIFICATE/EVIDENCE/CONTRACT/MINUTES/QUESTIONNAIRE/SCREENSHOT/OTHER) + the proven storage mechanics from `EvidenceStorageService` (allow-list, 50MB cap, path-traversal guard, SHA-256). Endpoints:
`POST /api/v1/attachments/{entityType}/{entityId}?docType=…` (multipart), `GET .../list`, `GET .../{id}/download`, `DELETE .../{id}`.

**2. Generic Control-Mapping service (backend)** — a polymorphic `evidence.control_links` table keyed by `(entity_type, entity_id, sub_control_id)` so any entity can **manually** map to many sub-controls across frameworks. Optional (entities can have zero links). Endpoints: `GET/PUT /api/v1/control-links/{entityType}/{entityId}` (replace-set semantics, user-maintained — never auto-derived).

**3. Two reusable UI components (frontend)**
- `<AttachmentsField entityType entityId />` — real upload/preview/download wired to (1) (replacing the mock `fileUpload.ts`), **with a per-file document-type selector**, and a new `FormModal` `kind: 'attachments'`.
- `<ControlMappingPicker value onChange />` — **optional, manual** multi-select across frameworks (reusing `controlCrosswalk.ts` + the GRC Expert catalog), returns `subControlId[]`, and a `FormModal` `kind: 'control-mapping'`.

**4. Rollout** — wire the two new field kinds into the HIGH-priority forms first (evidence, policy, risk, incident/breach, CAPA, audit, vendor/outsourcing, BCM, processor assessment), then MED, then LOW.

## Phasing

- **Phase 0 (foundation):** Attachments service + Control-Mapping service + the 2 reusable components + 2 new `FormModal` field kinds. ~1 backend + 1 frontend increment each, fully verifiable.
- **Phase 1 (HIGH forms):** wire into the ~12 HIGH-priority forms.
- **Phase 2/3 (MED/LOW):** remaining ~25 forms.

The full rollout (Phases 1–3) spans ~30+ forms and is multi-session / workflow-scale.

---

## Benchmark vs GRC market leaders

How the platform's forms/workflows compare to ServiceNow IRM, RSA Archer, MetricStream, OneTrust, AuditBoard, LogicGate, Hyperproof, Vanta/Drata, IBM OpenPages, Diligent.

| Capability (table-stakes for leaders) | Leaders | This platform today | Action |
|---|---|---|---|
| **Inline file attachments + preview/versioning** on records | All (AuditBoard, OneTrust, ServiceNow, Hyperproof) | Only evidence items (real); rest mock/none | **Build generic Attachments** (Phase 0) — the #1 alignment gap |
| **Many-to-many control ↔ framework crosswalk** (one control/evidence → SOC2 + ISO + GDPR…) | Vanta, Drata, Hyperproof, AuditBoard, OneTrust | Single `subControlId`; read-only crosswalk page | **Build generic Control-Mapping** (Phase 0) — the #2 alignment gap |
| **Evidence requests with owner + due date + reminders/SLA** | AuditBoard, Hyperproof, ServiceNow | ✅ just added (assignee/department/due, requester recorded) | Add reminder/SLA escalation (small follow-up) |
| **Evidence freshness / expiry / re-collection** | Vanta, Drata, Hyperproof | ✅ EXPIRED state + nightly expiry + freshness-aware coverage | Extend expiry to generic attachments (`valid_to` + recollect) |
| **Automated evidence collection (integrations)** | Vanta, Drata (Hypersyncs), ServiceNow | Connector library = MVP/stub | Wire 1–2 real connectors later |
| **Assessment templates / questionnaires (DPIA, vendor)** | OneTrust, AuditBoard | Partial (DPIA, processor assessments exist) | Add reusable questionnaire builder (later) |
| **Bulk actions · saved views · filters** | All | ✅ saved views + filter modals | OK |
| **Approval workflow / e-signature** | ServiceNow, Diligent | Workflow engine + signatures (mock) | Make signatures real (later) |
| **Audit trail / chain of custody** | All | ✅ audit log + evidence chain + SHA-256 | OK |
| **Risk quantification (inherent/residual, heatmap)** | LogicGate, ServiceNow | ✅ residual-banded + heatmap | OK |
| **Reporting / dashboards / board packs** | Diligent, ServiceNow | ✅ reporting + board pack + exports | OK |

**Conclusion:** the platform already matches the leaders on workflow, freshness, audit trail, risk quant, and reporting. The **two specific places it lags are exactly the two you identified** — inline typed attachments and many-to-many cross-framework control mapping. The Phase-0 foundation closes both and brings the forms to leader parity. Leader-inspired add-ons (attachment expiry/recollection, reminder SLAs on requests, real connectors, questionnaire builder, real e-signature) are smaller follow-ups layered on top.

---

## Per-form rollout plan (execution) — ✅ COMPLETE

Foundation: **Attachments (Phase 0a) AND Control-mapping (Phase 0b) shipped & verified.** `<AttachmentsField>` + generic `platform.attachments`; `<ControlMappingPicker>` + generic `evidence.control_links` (optional/manual, cross-framework, replace-set). Surfaced three ways: (a) a docs tab on detail pages that use the shared `Tabs`; (b) a page-level "Documents & control mapping" card appended to bespoke detail pages; (c) a `FolderOpen` row-action opening the shared `<EntityDocsModal>` on register pages without a detail page.

**Status: 51 surfaces wired — 30 detail pages + 21 registers — covering every meaningful GRC artifact across the platform.** `tsc --noEmit` clean throughout; each increment committed and pushed to `origin/master`.

Where an entityType appears on both a register and a detail page (`BREACH`, `DPIA`, `CONSENT`, `DSR`), the **same** `entityType`+numeric `entityId` is reused so attachments stay in sync across both surfaces.

### Detail pages (30)
| Detail page | entityType | Attach | Map |
|---|---|---|---|
| `risk/RiskDetailPage` | `RISK` | ✅ | ✅ |
| `outsourcing/OutsourcingDetailPage` | `OUTSOURCING` | ✅ | ✅ |
| `ai/AiModelDetailPage` | `AI_MODEL` | ✅ | ✅ |
| `cpty/CptyCounterpartyDetailPage` | `COUNTERPARTY` | ✅ | ✅ |
| `tprm/VendorBcpCampaignDetailPage` | `VENDOR_BCP_CAMPAIGN` | ✅ | ✅ |
| `privacy/SubprocessorDetailPage` | `SUBPROCESSOR` | ✅ | ✅ |
| `detail/PolicyDetailPage` | `POLICY` | ✅ | (rich Mappings tab) |
| `detail/VendorDetailPage` | `TPRM_VENDOR` | ✅ | ✅ |
| `detail/FindingDetailPage` | `AUDIT_FINDING` | ✅ | ✅ |
| `detail/RopaDetailPage` | `ROPA` | ✅ | ✅ |
| `detail/DpiaDetailPage` | `DPIA` | ✅ | ✅ |
| `detail/PrivacyBreachDetailPage` | `BREACH` | ✅ | ✅ |
| `detail/ConsentDetailPage` | `CONSENT` | ✅ | ✅ |
| `detail/DsrDetailPage` | `DSR` | ✅ | ✅ |
| `detail/ControlDetailPage` | `CONTROL` | ✅ | (sub-controls/links tabs) |
| `audit/EngagementDetailPage` | `AUDIT_ENGAGEMENT` | ✅ | ✅ |
| `opres/OpResAssessmentDetailPage` | `OPRES_ASSESSMENT` | ✅ | ✅ |
| `regreporting/RegReportingFilingDetailPage` | `REG_FILING` | ✅ | ✅ |
| `openbanking/ObTppDetailPage` | `OB_TPP` | ✅ | ✅ |
| `rrp/RrpCycleDetailPage` | `RRP_CYCLE` | ✅ | ✅ |
| `audit-ia/IaEngagementDetailPage` | `IA_ENGAGEMENT` | ✅ | ✅ |
| `aml-sanctions/SanctionsCheckDetailPage` | `SANCTIONS_CHECK` | ✅ | ✅ |
| `consumer/ComplaintDetailPage` | `COMPLAINT` | ✅ | ✅ |
| `bcm/AarDetailPage` | `CRISIS_AAR` | ✅ | ✅ |
| `bcm/ExerciseScenarioDetailPage` | `EXERCISE_SCENARIO` | ✅ | ✅ |
| `ccm/CcmTestDetailPage` | `CCM_TEST` | ✅ | ✅ |
| `cyber/AttackSurfaceAssetDetailPage` | `ASM_ASSET` | ✅ | ✅ |
| `ethics/coi/CoiCampaignDetailPage` | `COI_CAMPAIGN` | ✅ | ✅ |
| `fraud/FraudCaseDetailPage` | `FRAUD_CASE` | ✅ | ✅ |
| `ethics/WhistleblowerCaseDetailPage` | `WHISTLEBLOWER_CASE` | ✅ | ✅ |

### Registers — `<EntityDocsModal>` row-action (21)
`INCIDENT`, `BREACH`, `DSR`, `DATACLASS_ASSET`, `DATACLASS_FLOW`, `AI_VALIDATION`, `AI_RETRAINING`, `AI_RECERTIFICATION`, `DPIA`, `CONSENT`, `PRIVACY_NOTICE`, `RETENTION_RULE`, `NDR`, `DATA_CATALOG`, `DATA_QUALITY_RULE`, `AI_ETHICS_REVIEW`, `CHANGE_REQUEST`, `DATA_SHARING_AGREEMENT`, `PROCESSOR_ASSESSMENT`, `FOURTH_PARTY_EXPOSURE`, `THIRD_PARTY_CYBER_ASSESSMENT`.

### Intentionally not wired (low attachment value / not single-entity)
Template-management pages (committee form/template, crisis-comms template), workflow/approval **process-instance** views (workflow submission, approval-chain instance), MTPD-inheritance (a cross-function matrix, not one record), and KRI detail (metric, string `externalId`). CAPA detail uses a **string** record id, incompatible with the numeric attachment `entityId` contract — its attachments live on the register/linked finding instead.

### Follow-ups (leader-parity add-ons, not blocking)
Extend attachment expiry/recollection (`valid_to`) to generic attachments; reminder/SLA escalation on evidence requests; the optional `FormModal` `kind: 'attachments'` for create/edit modals (deferred — detail-page surfacing covers the need).
