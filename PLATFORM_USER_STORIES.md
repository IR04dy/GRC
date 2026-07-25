# TSS GRC Platform — User-Story Register (per business process)
### سجل قصص المستخدم لكل عملية

> **Companion to** `PLATFORM_ARCHITECTURE_PACK.md` **§5** — one story set per business process (BP-0 … BP-10) plus platform-wide stories. Written against the as-built system on 2026-07-23 (branch `2d5b6a85`, suite 2,316/0/0).
>
> **Format.** Every story: `As a <persona>, I want <capability>, so that <value>` + **acceptance criteria that are server-enforced** (the 400/403/404/409 gates a UI cannot bypass) — which makes each AC directly executable as a live probe (the house verification doctrine, chronicle §14).
>
> **Status legend:** ✅ **Shipped** (implemented + verified) · ✅\* Shipped in a grade-B module (functional; module not yet deep-reviewed — pack §13.1) · 🔶 **Planned** (speced in `CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`, CE-x) · ⬜ **Backlog** (pack §14, BL-x / not yet speced).
>
> Supersedes the V1-era `docs/Saudi-GRC-Platform-Integration/11-Implementation-Backlog/11_User_Stories.md` (115-line persona sketch) as the current story register.

**Personas** (dev logins in brackets where seeded): Platform Admin [`admin`] · CISO / second approver [`so_officer`] · GRC/Compliance Officer [`compliance`] · Risk Manager [`rmanager`] / Risk Analyst [`ranalyst`] · Auditor [`auditor`] · Viewer [`viewer`] · Change Requester / Change Manager / Implementer · CAB Chair / Secretary / Member · Committee Secretary / Chair / Member / Signatory · Policy Author / Owner · Employee (attestor) · SOC Analyst / SOC Lead · Evidence Uploader / Verifier · IAM Requester / Approver / Campaign Reviewer · DPO / Data Subject · Vendor Owner / TPRM Analyst · Sustainability Officer / GHG Verifier / Green-Finance Officer · Assurance Provider.

---

## BP-0 — Workflow engine (the shared pattern, 14 adopting modules)

| ID | User story | Acceptance criteria (server-enforced) | Status |
|---|---|---|---|
| US-WF-01 | As a **module user**, I want to raise a workflow request from my record (risk, finding, DSR, …), so that approvals follow one platform-wide process. | Request row in `workflow.requests` carries `source_module`/`source_record_id`; record-scoped panel shows it; org-scoped. | ✅ |
| US-WF-02 | As an **approver**, I want a single approvals inbox with step routing, so that nothing waits in a module I never open. | Steps advance by completion rule (threshold/all); acting on another org's request → 404/403. | ✅ |
| US-WF-03 | As an **initiator**, I want SLA reminders and escalation tiers, so that stalled requests surface themselves. | `due_date` + reminder cadence + `escalation_tiers` fire scheduler-side with org-explicit context; audit rows written off-request safely. | ✅ |
| US-WF-04 | As a **GRC admin**, I want request types/branching configurable per tenant, so that the engine fits each org's process. | Branch rules/routing mode persisted per request; invalid transition → 4xx ProblemDetail naming the field. | ✅ |

## BP-1 — Change enablement (ITIL v4)

| ID | User story | Acceptance criteria (server-enforced) | Status |
|---|---|---|---|
| US-CH-01 | As a **Change Requester**, I want to draft a change with type (STANDARD/NORMAL/EMERGENCY), category, risk and bilingual titles, so that it is classified from birth. | `titleAr` mandatory (client-gated, backend-enforced); create lands in DRAFT. | ✅ |
| US-CH-02 | As a **Change Manager**, I want the lifecycle to be a guarded state machine, so that nobody can jump a change to CLOSED. | 13-state transition table; illegal jump → 400/409; plain `PUT` cannot change `status` (CM-C3). | ✅ |
| US-CH-03 | As a **Change Manager**, I want the authorisation matrix to resolve the gate per type×risk, so that authority is proportionate to risk. | `GET /{id}/authorisation` returns AUTO/CAB/ECAB + `min_approvals` from `change_authorisation_matrix` (V306). | ✅ |
| US-CH-04 | As a **CAB Member**, I want to record my vote while the change is under assessment, so that decisions precede approval. | Votes accepted only in IMPACT_ASSESSED/CAB_PENDING (409 otherwise); a recorded REJECT blocks CAB_APPROVED (rubber-stamp guard). | ✅ |
| US-CH-05 | As a **Change Manager**, I want to refer a change to the CAB/ECAB committee, so that the committee engine is the system of record. | Referral resolves committee via per-tenant `committee_gate_map`; requester + change owner force-RECUSED on the vote; CAB_APPROVED gated on resolution=APPROVED + MINUTES_APPROVED + voteFor ≥ minApprovals (else 409); non-referred changes keep the legacy path byte-identically. | ✅ |
| US-CH-06 | As a **change reader**, I want the committee's decision (outcome, tally, quorum, meeting status) on the change detail, so that provenance is visible without committee-admin access. | `GET /{id}/committee` enriched view guarded by CHANGE READ only. | ✅ |
| US-CH-07 | As an **Implementer**, I want tasks with owners and due dates, and overdue tasks flagged, so that implementation is tracked. | `CHANGE_TASK_OVERDUE` event + audit-marker dedup + `analytics().overdueTasks` KPI (CH-5 scheduler). | ✅ |
| US-CH-08 | As a **Change Manager**, I want freeze windows and schedule-conflict detection, so that changes don't collide with protected periods. | `GET /{id}/conflicts` reports freeze/overlap hits (V307); calendar view exposes the schedule. | ✅ |
| US-CH-09 | As a **Reviewer**, I want closure blocked until a post-implementation review is recorded, so that lessons are never skipped. | POST_REVIEW→CLOSED without PIR summary → 409 (CH-11). | ✅ |
| US-CH-10 | As an **Auditor**, I want the change register exported (PDF/XLSX/DOCX, RTL Arabic), so that I can sample offline. | EXPORT-gated endpoints return valid binaries; export history row written (CH-4). | ✅ |
| US-CH-11 | As a **CAB Secretary**, I want the agenda auto-built from changes awaiting authorisation (plus ratifications and overdue PIRs), so that no eligible change is forgotten. | Candidate query scope-filtered, risk-ordered, idempotent rebuild; late additions marked. | 🔶 CE-4 |
| US-CH-12 | As an **ECAB Chair**, I want every emergency change ratified retrospectively within an SLA, so that emergency is a path, not a bypass. | Emergency change cannot reach CLOSED without a ratification decision; SLA breach flagged, not hidden. | 🔶 CE-6 |
| US-CH-13 | As a **CAB Chair**, I want to approve **with conditions**, so that risk mitigations are enforceable. | Open conditions block SCHEDULED→IMPLEMENTING (409) until satisfied with evidence; approvals expire (`effective_to`). | 🔶 CE-5 |
| US-CH-14 | As a **Change Manager**, I want standard changes backed by a pre-approved model catalogue, so that "standard" is a record, not a claim. | STANDARD without a live pre-approved model routes as NORMAL (fail-closed). | 🔶 CE-2 |
| US-CH-15 | As a **CISO**, I want unauthorised changes detectable and reconciled, so that implemented ≠ authorised is visible. | Unauthorised register with match/escalate lifecycle. | 🔶 CE-11 |

## BP-2 — Policy document control

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-PO-01 | As a **Policy Author**, I want every new policy to start as DRAFT regardless of what the client posts, so that the lifecycle can't be bypassed at create time. | POST with `status:"PUBLISHED"` still lands DRAFT (PM-C2); version uniqueness per code enforced. | ✅ |
| US-PO-02 | As a **Policy Author**, I want an inline content editor (and/or file upload with hash), so that policies are real documents, not metadata shells. | APPROVED blocked without content/document (CH-10 doc-gate). | ✅ |
| US-PO-03 | As a **Policy Owner**, I want approval segregated from authorship, so that no one approves their own policy. | REVIEW→APPROVED by the creator → 409 (CH-7 SoD, principal-based). | ✅ |
| US-PO-04 | As a **Compliance Officer**, I want to refer a policy to the governance committee, so that approval carries quorum + minutes provenance. | Referral + gate: APPROVED requires quorate committee APPROVED resolution with MINUTES_APPROVED (Track D S5); CH-7 + doc-gate still apply independently. | ✅ |
| US-PO-05 | As an **Employee**, I want to attest to a published policy (or decline with a reason), so that acknowledgement is auditable. | Attest stamps the authenticated principal; decline requires a reason; per-user rows. | ✅ |
| US-PO-06 | As a **Policy Owner**, I want exceptions/waivers with expiry, so that deviations are time-boxed. | Exception lifecycle with expiry sweep (multi-tenant scheduler). | ✅ |
| US-PO-07 | As a **Compliance Officer**, I want the register filterable by framework/standard/regulation with per-policy chips, so that I can answer "show me all PDPL policies" instantly. | Filter + chips read the canonical `document_framework_mappings` (many-to-many); COMPLIANCE READ only. | ✅ |
| US-PO-08 | As a **Policy Owner**, I want version compare and controlled new versions, so that changes between revisions are explainable. | Diff endpoint lists field-level changes; latest-version resolution is numeric-safe (PM-H2 fix). | ✅ |
| US-PO-09 | As an **Auditor**, I want a per-policy evidence pack export, so that policy governance is provable offline. | Evidence-package endpoint returns the pack; EXPORT-gated. | ✅ |
| US-PO-10 | As a **Compliance Officer**, I want attestation **campaigns** (audience, reminders, escalation), so that acknowledgement reaches everyone, not just volunteers. | Campaign object with audience resolution + reminder/escalation scheduler. | ⬜ backlog |

## BP-3 — Risk management (ERM/KRI)

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-RK-01 | As a **Risk Owner**, I want to register risks with inherent scoring and bilingual names, so that the register is regulator-presentable. | Arabic mandatory; org-scoped register. | ✅ |
| US-RK-02 | As a **Risk Manager**, I want control links to feed residual risk, so that control effectiveness is not cosmetic. | Residual derives from linked-control effectiveness (E-programme); dangling links cleaned. | ✅ |
| US-RK-03 | As a **Risk Acceptor**, I want risk acceptance to be four-eyes, so that owners cannot accept their own risk. | Self-accept → 409; second user (`so_officer`) required. | ✅ |
| US-RK-04 | As a **Risk Analyst**, I want KRIs (definitions → readings → breaches) wired to workflow, so that threshold breaches become actioned work. | Breach creates a workflow request; KRI consolidation is canonical (legacy table dropped, E-E). | ✅ |
| US-RK-05 | As a **CISO**, I want FAIR quantification on selected risks, so that top risks carry loss-exposure figures. | FAIR params persisted; computed loss exposure returned (not client-side only). | ✅ |
| US-RK-06 | As a **Risk Manager**, I want an emerging-risk radar, so that horizon risks are tracked before they enter the register. | Emerging-risk records promotable to full risks. | ✅ |
| US-RK-07 | As a **Board member**, I want risk board-packs and register exports (RTL), so that governance reporting is one click. | Export endpoints EXPORT-gated, RTL-correct, history-logged. | ✅ |

## BP-4 — Incident & SOC operations

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-SO-01 | As a **SOC Analyst**, I want alert triage gated by a TRIAGE permission, so that only trained analysts disposition alerts. | `hasPermission('SOCOPS','TRIAGE')` on triage endpoints (the catalogue's only TRIAGE action). | ✅ |
| US-SO-02 | As a **SOC Lead**, I want cases/incidents with lifecycle, comms log and shift handover, so that response is continuous across shifts. | Incident state machine + comms records; shift objects. | ✅ |
| US-SO-03 | As a **SOC Analyst**, I want playbooks attached to case types, so that response steps are consistent. | Playbook linkage on cases. | ✅ |
| US-SO-04 | As a **Change Manager**, I want failed/rolled-back changes to raise linked incidents automatically, so that failure enters the incident process. | F8 rule on `CHANGE_FAILED` auto-creates/links incident (CH-13). | ✅ |
| US-SO-05 | As a **CISO**, I want vulnerability SLAs tracked with breach visibility, so that remediation debt is measurable. | Vuln-SLA scheduler flags breaches per tenant. | ✅ |

## BP-5 — Evidence lifecycle

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-EV-01 | As a **Control Owner**, I want evidence requests per control/obligation, so that collection is planned, not ad hoc. | Request objects with due dates; org-scoped. | ✅ |
| US-EV-02 | As an **Evidence Uploader**, I want uploads hash-stamped (SHA-256), so that integrity is provable later. | Hash stored at upload; verify view exposes it. | ✅ |
| US-EV-03 | As an **Evidence Verifier**, I want four-eyes verification, so that no one verifies their own upload. | Self-verify → 409; verifier identity stamped. | ✅ |
| US-EV-04 | As a **GHG Verifier**, I want verification to require linked evidence, so that "verified" means traceable. | GHG verify without evidence → 409 (Rule 2); green-finance arms included (GF-2). | ✅ |
| US-EV-05 | As an **Assurance Provider**, I want an ISAE 3410 lineage view grading records by evidence state, so that assurance readiness is measurable. | Lineage view includes projects/credits; readiness % moves with linkage. | ✅ |

## BP-6 — Committee governance

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-CM-01 | As a **Committee Secretary**, I want meetings with attendance capture and deterministic quorum, so that decisions are procedurally valid. | Quorum computed server-side from attendance (CH-14a); meeting states SCHEDULED→…→MINUTES_APPROVED. | ✅ |
| US-CM-02 | As a **Committee Member**, I want per-resolution votes (FOR/AGAINST/ABSTAIN) with COI recusal, so that decisions are attributable. | COI → forced RECUSED; referral requester/owner force-recused; tallies exclude recused (CH-14b). | ✅ |
| US-CM-03 | As a **Chair/Signatory**, I want approved minutes e-signed with a content hash, so that minutes are tamper-evident. | `SignatureService.sign("committee_meeting", id, APPROVE, sha256-anchored statement)` on MINUTES_APPROVED (Track D S4); signature trail queryable. | ✅ |
| US-CM-04 | As a **Secretary**, I want action items with owners and due dates from each meeting, so that follow-ups don't evaporate. | Action-item register + scheduled sweep. | ✅ |
| US-CM-05 | As a **Member**, I want a voting panel showing each member's vote per resolution, so that the record is transparent. | Votes modal reads per-member votes (Track C FE). | ✅ |
| US-CM-06 | As a **CAB Chair**, I want the CAB constituted as a chartered body (ToR, roles, quorum policy, member terms), so that the board itself is governed. | CabProfile over the committee engine; chair/secretary roles; term-expiry surfacing. | 🔶 CE-3 |

## BP-7 — Access governance (IAM)

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-IA-01 | As an **IAM Requester**, I want access requests routed maker-checker, so that no one grants themselves access. | Self-decide → 409; approver identity stamped. | ✅ |
| US-IA-02 | As a **CISO**, I want an SoD engine that flags toxic role combinations, so that conflicts are detected, not assumed. | Violations register with disposition workflow; simulation on request. | ✅ |
| US-IA-03 | As a **Campaign Reviewer**, I want recertification campaigns, so that access is periodically re-justified. | Self-certify → 409; campaign progress tracked. | ✅ |
| US-IA-04 | As an **Admin**, I want break-glass grants time-boxed with independent review, so that emergency access is controlled. | Self-review → 409; expiry sweep revokes on schedule. *(Known defect DF-1: the sweep's TX bug is being fixed in a parallel session — AC re-verify after that lands.)* | ✅ |
| US-IA-05 | As a **Platform Admin**, I want the permission catalogue (module × action) administrable per role, so that RBAC is data, not code. | 99-permission catalogue; grants per 57 roles; ratchet keeps new writes guarded. | ✅ |

## BP-8 — Privacy (PDPL)

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-PR-01 | As a **DPO**, I want a RoPA register per processing activity, so that Article-30-style records exist. | RoPA CRUD org-scoped, bilingual. | ✅\* |
| US-PR-02 | As a **DPO**, I want DPIAs anchored to frameworks, so that assessments map to obligations. | DPIA ↔ framework anchor rows. | ✅\* |
| US-PR-03 | As a **Data Subject**, I want my request (DSR) tracked to a statutory clock, so that fulfilment is provable. | DSR lifecycle with due-date tracking. | ✅\* |
| US-PR-04 | As a **DPO**, I want a breach register with 72-hour notification tracking, so that regulator deadlines are visible. | Breach records carry notification clocks. | ✅\* |
| US-PR-05 | As a **DPO**, I want subprocessor lifecycle management, so that transfers ride on assessed processors. | Subprocessor state machine (service-defined). | ✅\* |

## BP-9 — Third-party & outsourcing (SAMA)

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-TP-01 | As a **Vendor Owner**, I want a vendor register with risk tiering, so that third parties are classified. | Vendor records org-scoped, bilingual. | ✅\* |
| US-TP-02 | As a **TPRM Analyst**, I want due-diligence assessments per vendor, so that onboarding is evidence-based. | Assessment objects linked to vendors. | ✅\* |
| US-TP-03 | As a **Compliance Officer**, I want SAMA outsourcing arrangements with materiality and non-objection tracking, so that regulated outsourcing is demonstrable. | Arrangement lifecycle (service-defined states). | ✅\* |
| US-TP-04 | As a **Sustainability Officer**, I want vendor ESG monitoring feeding CAPAs, so that supplier ESG gaps become actions. | Vendor-ESG source type whitelisted in CAPA (`ck_ca_source`). | ✅ |
| US-TP-05 | As a **Vendor Owner**, I want exit plans per critical vendor, so that termination is survivable. | Exit-plan records per arrangement. | ✅\* |

## BP-10 — ESG / GHG / green finance

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-ES-01 | As a **Data Owner**, I want metric definitions and readings, so that ESG data has a governed home. | Readings persist against definitions (defect "26 defs / 0 readings" closed by the programmes). | ✅ |
| US-ES-02 | As a **Sustainability Officer**, I want 15-category Scope 1/2/3 GHG computation, so that the inventory is protocol-complete. | Category computations server-side; CBAM embedded-factor helper persists its data basis (V299). | ✅ |
| US-ES-03 | As a **GHG Verifier**, I want verification blocked without evidence, so that assured figures are traceable. | Verify → 409 without evidence link (Rule 2). | ✅ |
| US-ES-04 | As a **Sustainability Officer**, I want the CAPA loop closable in-product (edit, evidence, complete, verify, close), so that findings drive real remediation. | Complete requires `evidenceRef` (Rule B); Verify stamps the principal (SoD vs owner); manual CAR/PAR creation; action_type CHECK (V297). | ✅ |
| US-ES-05 | As a **Green-Finance Officer**, I want instruments (Sukuk) with ISIN/listing/prospectus and Hijri dual dates, so that issuance records match Saudi market practice. | `prospectus_ref` persisted (REG-8); Hijri rendering via shared helper. | ✅ |
| US-ES-06 | As a **CFO**, I want use-of-proceeds allocations approved four-eyes, so that green claims are controlled. | Allocation approve by the requester → 409. | ✅ |
| US-ES-07 | As a **Portfolio Manager**, I want portfolio green-impact rollups (project-deduplicated) and GAR, so that portfolio claims don't double-count. | Rollup dedups by project (GF-1: 60/60→500 not 600, pro-rata proven). | ✅ |
| US-ES-08 | As a **Sustainability Officer**, I want carbon credits with retirement and a tenant-configurable offset cap, so that offsets stay within policy. | Retirement lifecycle; offset cap enforced (default 10%). | ✅ |
| US-ES-09 | As an **Assurance Provider**, I want IFRS S1/S2 + CMA disclosure packs and assurance lineage, so that reporting season is export-driven. | Disclosure exports RTL-correct; lineage readiness % (US-EV-05). | ✅ |

## Platform-wide stories

| ID | User story | Acceptance criteria | Status |
|---|---|---|---|
| US-PL-01 | As an **Arabic-speaking user**, I want every screen and export RTL-correct in Arabic, so that Arabic is a first-class language, not a translation. | Bilingual columns mandatory; 22 export generators RTL-conformant (Amiri, shaped, mirrored). | ✅ |
| US-PL-02 | As a **user**, I want failures to name the offending field, so that I can fix input without guessing. | RFC-7807 ProblemDetail + `problemMessage()` fold into toasts. | ✅ |
| US-PL-03 | As a **Platform Admin**, I want tenant isolation everywhere, so that org A can never read org B. | Org-scoped queries + prod tenant write-guard; cross-tenant probes return 404/zero rows. | ✅ |
| US-PL-04 | As an **Auditor**, I want a mutation audit ledger + export history, so that "who did what when" is answerable. | AuditLogService rows on mutations; export history register. | ✅ |
| US-PL-05 | As a **user**, I want global search and a command palette over canonical data, so that navigation is instant and truthful. | Policy search hydrated from canonical register (CH-9), mock fallback only when API unavailable. | ✅ |
| US-PL-06 | As a **GRC admin**, I want F8 automation rules per tenant (211 active), so that events drive actions without code changes. | Rules seeded per environment; engine executes afterCommit (failures can't poison business commits). | ✅ |
| US-PL-07 | As an **operator**, I want email notifications, so that approvals reach people outside the app. | SMTP relay wired via `external.properties`. | ⬜ BL-B2 |
| US-PL-08 | As a **user**, I want the SPA to update itself after a deploy, so that I never see a stale cached shell. | Service-worker update strategy (skipWaiting/versioning). | ⬜ BL-C3 |

---

## Traceability & how to use this register

1. **Assessment:** pick any ✅ story and execute its AC as a live probe (two users for SoD rows; expect the 4xx/409s — *a 409 on an SoD row is the story passing*). Grade-B rows (✅\*) pass functionally but their modules await deep review (pack §13.1) — probe before relying on them contractually.
2. **Planning:** 🔶 rows are the CE programme (sized + phased in the CE plan §0/§15); ⬜ rows live in the pack's consolidated backlog §14.
3. **Change control:** when a slice ships, flip its row in the same commit (the §0-tracker discipline).

*Prepared 2026-07-23. Counts: **84 stories — 66 ✅ shipped / 9 ✅\* shipped-pending-review / 6 🔶 planned (CE) / 3 ⬜ backlog** (plus the DF-1 re-verify note on US-IA-04).*
