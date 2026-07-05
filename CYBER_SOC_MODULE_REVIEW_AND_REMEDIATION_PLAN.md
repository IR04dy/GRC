# Cybersecurity + SOC Operations Module Review and Remediation Plan

> **Merged deliverable.** This file consolidates two things the user asked to merge:
> 1. The **executed review** I ran against the live stack (:8084 API + SQL Server `TSS_GRC` + :5173 UI) — full artifacts under [`review/cyber-socops/`](review/cyber-socops/) (reference model, coverage matrix, and per-wave receipts).
> 2. The **deliverable structure** mandated by `CYBER_SOC_Module_Review_Command_Loop_Claude_Code.md` (Desktop) — the 35 sections + backlog + test cases below.
>
> **Every finding carries a VALIDATION verdict** (CONFIRMED = reproduced live with a receipt; PLAUSIBLE = strong evidence, one confirming step noted) and was adversarially judged before landing here. Review-only — **no product code was changed.** Test data was tagged `[REVIEW-TEST]` and removed; one accidental seed soft-delete was fully restored (see §4).

**Date:** 2026-07-04 · **Reviewer role:** senior Cyber-GRC + SOC architect / NCA-ISO-aware / Saudi-market / QA · **Method:** LOOP 0–19 + Ultra Workflow Organizer, live harsh testing via `scratchpad/rev/h.py`.

---

## §Implementation Status (branch `feat/cyber-soc-remediation`)
Implement-loop tracker — one epic per fire, §33 order. Each fire: read this table, do the next `[ ]` epic, validate live, tick it, commit on the feature branch. Do **not** merge to master without explicit approval.

| Order | Epic | Sev | Status | Migration | Validated (live) |
|---|---|---|---|---|---|
| 1 | **SOC-A** seed + grant SOC role model | Crit | ✅ DONE | V259 | so_officer (non-admin) events 403→200, triage 403→200; DELETE appsec still 403 (least-priv); 26/26 socops roles present |
| 2 | CY-A wire evidence to real subsystem (upload+request+review) | Crit | ✅ DONE | — (FE wiring; endpoints existed) | multipart upload to CYBER_EVIDENCE→201; request OPEN→…→REJECTED→resubmit→APPROVED all 200 w/ dept+due+control link; CyberPage Attachments + Request modals; tsc clean; live Vite serves it |
| 3 | CY-B consolidate incident model (dashboard/PDPL ↔ cyber workspace) | Crit | ✅ DONE | V260 (+V261 backfill, **not yet applied to dev**) | 19/19 live: /cyber incident auto-registers a canonical `cyber.incidents` mirror; dashboard `openIncidents` 9→10 (DB source **+ live `/dashboard/stats`**); PDPL bridge accepted the workspace-origin incident → auto-created `data_breach`; status-sync DETECTION→…→CLOSED; delete→mirror soft-deleted; baselines restored; 20/20 unit tests green |
| 4 | CY-D fix schedulers (vuln-SLA dormant + socops sweep) | High | ✅ DONE | — (config enable + notify wiring; no schema) | live, NO manual POST: on boot the vuln-SLA scheduler auto-escalated 7 past-due findings → BREACHED + `last_escalation_at` (14:04:05), wrote 7 `ESCALATION_BREACH` audits, delivered 7 `VULN_SLA_BREACHED` notifications to `SECURITY_ANALYST`/`so_officer` (ties to SOC-A); socops sweep auto-fired `SECURITY_EVENT_SLA_BREACHED` for a breached event; 21/21 unit tests |
| 5 | SOC-C event→incident promotion | High | ✅ DONE | — (`incident_id` column already existed) | 15/15 live: escalate a security event → creates a `cyber.incidents` register incident (OPEN/CYBER, carries SIEM context source/host/ip/user/time) + sets `incident_id` + status ESCALATED; dashboard `openIncidents` 9→10 (DB + live API — builds on CY-B); idempotent (no duplicate on re-escalate); FALSE_POSITIVE → **409**; FE Escalate button + linked-incident indicator (tsc clean, Vite serves it) |
| 6 | CY-C incident regulatory timers + auto-CAPA | High | ✅ DONE | — (CAPA table + regulator notified-at columns already existed) | 17/17 live: HIGH incident `/regulatory-templates` shows a **running NCA clock** (24h deadline + live hoursRemaining); closing WITH lessons auto-raises a **CORRECTIVE CAPA** (origin=incident, owner←assignee, due=+30d, severity carried, lessons in body) — capa +1; closing WITHOUT lessons raises none; 28/28 unit tests. *Note: the deadline/countdown are computed (regulatoryTemplates), not new columns; a dedicated overdue-regulatory-deadline escalation sweep is deferred to the §24 shared-clock primitive.* |
| 7 | SOC-B playbook invoke → runnable tasks | High | ✅ DONE | V262 | 30/30 live + 5/5 unit: invoking the 9-step IR-PHISHING-01 creates a **RUN** + one **clocked, owner-assigned task per step** (owner←`responsibleRole` w/ fallback, `dueAt`=cumulative from start, strictly increasing, all PENDING); completing tasks advances `completedTasks` and the **last completion flips the run COMPLETED** (+`completedAt`); idempotent re-complete; legacy `invocation_count` bump + `IR_PLAYBOOK_INVOKED` F8 event preserved (AR-119/128) + `IR_PLAYBOOK_RUN_START`/`RUN_COMPLETED` audits + `SECURITY_ANALYST` broadcast; unknown playbook/run/task→404; SECURITY_ANALYST (so_officer) can list runs; baselines restored; FE run/task panel on playbook detail (tsc clean, Vite serves it) |
| 8 | CY-E CAPA routing (owner/due) + reminder/escalation scheduler | High | ✅ DONE | V263 | 17/17 live + 6/6 unit: gap→CAPA now **routes** an owner (reviewer→assessor→CISO), a **risk-weighted due date** (CRIT +7 / HIGH +14 / MED +30 / LOW +60) and a priority onto the `cyber_action_plans` row (all were null before); a new config-gated `CyberActionPlanSlaScheduler` **reminds** pre-due (`SECURITY_ANALYST`) and **escalates overdue plans once** (`escalated_at` guard) → `CISO` broadcast + `CYBER_CAPA_ESCALATED` audit + `CYBER_ACTION_PLAN_OVERDUE` F8 event + `reminder_count`; live: a backdated plan auto-escalated in ~50s, once-only; FE action-plans table flags overdue (red) + an "escalated" badge (tsc clean); baselines restored |
| 9 | CY-G RBAC read-gates + auditor access | High | ✅ DONE | V264 | 15/15 live + ratchet 1/1: cyber controllers already gated GET=READ / mutation=WRITE correctly, but **only PLATFORM_ADMIN held `CYBER_READ`** (module was admin-only). V264 grants `CYBER_READ` (read-only; WRITE/DELETE stay admin/operator) to AUDITOR, INTERNAL_AUDITOR, EXTERNAL_AUDITOR, REGULATOR_VIEWER, VIEWER, CISO, SECURITY_ANALYST, CYBER_MANAGER. Live: auditor now GETs controls/action-plans/assessments/vulns/dashboard → **200 (was 403)** + pages lists; auditor POST → **403** (least-priv held); SECURITY_ANALYST/CISO read cyber → 200. socops (role-gated) reads widened to include AUDITOR on brand-monitoring, cyber-insurance renewals-due, seasonal-overlay active-now, playbook templates. **Dev seed align:** the `auditor` acct held only global INTERNAL_AUDITOR; granted it the org-1 AUDITOR role (additive) so socops `hasAnyRole('AUDITOR')` gates work — prod assigns AUDITOR to auditor personas. |
| 10 | CY-H input validation (400 not 500, FK/enum) | Med | ✅ DONE | V265 | 24/24 live + 70/70 cyber+asm unit: cyber controllers bind bodies straight to JPA entities (no DTO), so malformed bodies used to 500 (NOT-NULL) or silently persist (no FK/enum). New `CyberInputValidation` (requireText/requireId/checkEnum) called from every cyber create + `ShadowItService`. Live: evidence `{}`→**400 (was 500)**, phantom `controlId`→**400 (was 200-persisted)**, bad `status`→**400 (was 200)**; shadow-it bad `discoveredVia`/`dataClassification`→**400 (was 500, VAL-3)**; all 9 entity-bound cyber creates `{}`→**400**; assessment/sub-control phantom `control_id`→**400**; happy-path evidence/shadow-it/assessment still 200/201 (assessment `gap_identified` now honours its DB default vs sending explicit NULL). **V265** adds DB defence-in-depth: FK `cyber_evidence.control_id→cyber_controls` + CHECK on `status` (WITH NOCHECK — grandfathers legacy rows, enforces future writes). GlobalExceptionHandler already maps IllegalArgumentException/MethodArgumentNotValid/HttpMessageNotReadable→400. 3 [REVIEW-TEST] junk evidence rows cleaned (CHARINDEX). |
| 11 | CY-F exports (Excel + Arabic PDF) | High | ✅ DONE | — (read-only; no schema) | 22/22 live + 74/74 cyber+asm unit: new shared `RegisterExport` engine (column-driven POI XLSX + OpenPDF, reuses `PdfTheme`/`PdfFonts` → Arabic renders via bundled Noto/IDENTITY_H). `CyberExportService` exports 6 registers (controls, vulnerabilities, assessments, incidents, action-plans, evidence) × {XLSX, PDF} via `GET /api/v1/cyber/export?register=&format=&lang=` (READ-gated); `SocOpsExportService` exports the security-event register via `GET /api/v1/socops/events/export`. **Acceptance:** vuln register→Arabic PDF (`lang=ar`, `%PDF`, 51KB — receipt `receipt_vuln_ar.pdf`) + control register→Excel (`PK` zip, 7.5KB — `receipt_controls.xlsx`); all 6 registers valid magic bytes + correct content-types (XLSX 4.7–7.5KB, PDF 45–55KB); AUDITOR can pull (READ, via CY-G); bad register/format → **400** (CY-H `checkEnum` tie-in), not 500. FE: Excel + PDF buttons on the exportable CyberPage tabs (PDF follows UI locale), reusing the `downloadFile` helper; tsc clean. *Board-pack cyber section deferred (register exports meet the acceptance; a BoardPack cyber section is a separate `reporting` integration).* |
| 12 | CY-I policy lifecycle in cyber | Med | ✅ DONE | — (reuses live `policy.documents`; no schema) | 19/19 live + 81/81 cyber+asm unit + tsc clean: no rebuild of a policy engine — the cyber workspace is **wired to the shared `module/policy` engine** via a thin CYBER-gated `CyberPolicyController` at `/api/v1/cyber/policies`, scoped to the cyber category space {CYBER, INFORMATION_SECURITY, INCIDENT_RESPONSE}. Live: create (category forced CYBER, DRAFT, v1.0) → **upload a file** (attachment on `PolicyDocument` via the generic `/api/v1/attachments`, listed) → lifecycle **DRAFT→REVIEW→APPROVED→PUBLISHED** (publishedAt stamped, effective-date-gated) → version + nextReviewDate → **new-version** v2.0 (DRAFT); category scoping (non-cyber policy id → **404** through the cyber surface); RBAC (AUDITOR/SEC_ANALYST read 200 via CY-G; AUDITOR create **403**); bad body → **400**. FE: a **Policies tab** (`CyberPoliciesPanel`) — list + create form (staged attachment uploads after save) + lifecycle transition buttons + versioned `AttachmentsField`. Test rows cleaned (CHARINDEX on `title_en`). |
| 13 | SOC-D forensics custody ledger | Med | ✅ DONE | V266 | 18/18 live + 9/9 socops unit: custody was a single mutable `current_custodian` (overwritten each transfer). **V266** adds an **append-only** `socops.forensics_custody_event` ledger + an **INSTEAD OF UPDATE,DELETE trigger** (`trg_fce_append_only`) making it tamper-evident even against direct SQL. `transfer-custody` now **appends** a TRANSFER row (from→to, location, sha256 at handoff, actor, seq) and moves the artefact's current-holder pointer; creating an artefact seeds a **CAPTURE** row (seq 1). New `GET /{id}/custody-chain`. Live: create → CAPTURE(seq1) → 2 transfers → chain=[CAPTURE, TRANSFER, TRANSFER] ordered, from→to chained (A→B→C), current holder = latest (C), actor recorded; **UPDATE + DELETE on the ledger both blocked by the trigger**; AUDITOR reads chain (200) but can't transfer (403); PLATFORM_ADMIN can't transfer (403 — not a forensics handler role); blank newCustodian → **400**. FE: a chain-of-custody timeline (`ForensicsCustodyPanel`) on the artefact detail. Test rows cleaned (disable trigger → delete → re-enable). |
| 14 | SOC-E threat-intel action + detection library | Med | ✅ DONE | V267 | 26/26 live + 17/17 socops unit (6 DetectionRule + 7 ThreatIntelAction + 4 ForensicsCustody regression) + tsc clean: threat-intel `action` was a lone boolean flip — now it **appends a `socops.threat_intel_action` row that links the IOC to a concrete platform target** (ASSET→`cyber.cyber_assets` / VULNERABILITY→`cyber.vulnerabilities` / DETECTION→the new library), target existence tenant-validated (phantom id → **400 not 500**, target type w/o id → 400, unknown actionType → 400), resolved human label captured (live: BLOCK→ASSET "Core Banking System", PATCH→VULNERABILITY "CVE-2022-3602", LINK_DETECTION→"DET-RT-1 · …"); legacy `{actioned,actionNotes}` payload still 200 (REVIEWED, backward-compat); `GET /{id}/actions` history. New **detection-rule library** `socops.detection_rule` (`GET/POST/PUT/DELETE /api/v1/socops/detections`) — every rule requires a **MITRE ATT&CK technique** (blank → 400), enum-checked type/status (bad type → 400), duplicate rule_code → 400 (not a unique-constraint 500), `?technique=` filter (live: T1566 returns it, T9999 excludes); create defaults DRAFT + F8 `DETECTION_RULE_CREATED`. RBAC: AUDITOR reads detections/actions → 200, AUDITOR create/action → **403** (least-priv). **V267** creates both tables (FK `threat_intel_action→threat_intel`, CHECKs on action/target-type/detection-type/status, `uq_dr_org_code`). FE: **Detection library** page (list + MITRE-filter + create form, `/socops/detections` + sidebar) + **IOC actions panel** (action + target-link timeline) on the TI detail. [REVIEW-TEST] rows cleaned (CHARINDEX; FK-safe order — both tables back to 0). |
| 15 | CY-J evidence expiry sweep + titleAr | Med | ✅ DONE | V268 | 16/16 live + 2/2 unit + tsc clean: `cyber_evidence` had no `title_ar` (English-only island) and its `expiry_date` was never swept. **V268** adds `title_ar` + `stale_since` + `re_request_id`. New config-gated `CyberEvidenceExpiryScheduler` (mirrors CY-E/vuln-SLA; `grc.cyber-evidence-expiry.scheduler-enabled`, dev 60s / prod 1h) flips expired CURRENT/PENDING evidence → **STALE** (stamps `staleSince`) and **auto-raises a re-request** through the shared `module/evidence` subsystem anchored `CYBER_EVIDENCE` (CY-A's anchor) so it lands in the evidence-request queue + the evidence's own request panel; records the new request id on `reRequestId`; audits `CYBER_EVIDENCE_EXPIRED` + notifies SECURITY_ANALYST + F8 event. Manual trigger `POST /api/v1/cyber/evidence/run-expiry-sweep` (WRITE-gated). Live: created evidence w/ titleAr (persists + round-trips); expired-dated row → STALE + staleSince + reRequestId=45 + an OPEN CYBER_EVIDENCE-anchored re-request (due +14d); future-dated row stayed CURRENT; **idempotent** (2nd sweep expired=0, no duplicate re-request); AUDITOR + SECURITY_ANALYST (READ-only) sweep → **403**, AUDITOR GET evidence → 200; 5 `CYBER_EVIDENCE_EXPIRED` audit rows. FE: `title_ar` field on the evidence form + bilingual display + a "re-requested" chip + STALE badge/tooltip + a "Run expiry sweep" button. **Dev clock note:** like CY-D/CY-E, the dev scheduler also flipped 4 genuinely-past-expiry seed evidence rows (Jan 2026 / Apr 2025 …) → STALE + re-request — the clock operating on real overdue data, left as-is. [REVIEW-TEST] rows cleaned (CHARINDEX). |

**Next fire:** — **ALL 15 EPICS SHIPPED.** SOC-A → CY-A → CY-B → CY-D → SOC-C → CY-C → SOC-B → CY-E → CY-G → CY-H → CY-F → CY-I → SOC-D → SOC-E → CY-J are all ✅ DONE on `feat/cyber-soc-remediation`. 12 merged to master @ `287b9fdd`; **SOC-D + SOC-E + CY-J** committed on the branch, **unmerged** (awaiting explicit "merge"). Full `mvn test` suite run as the final gate (see below). No further epic work — the loop is complete unless re-fired. **⚠ Fresh-session build trap (hit 2026-07-05):** a new shell defaults `JAVA_HOME` to JDK 11 → `mvn package` fails *"release version 17 not supported"* and the jar won't run. Pin first: `$env:JAVA_HOME="C:\Program Files\Java\jdk-17"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"`. Use `py` (not `python`) for the pyodbc scripts. **CY-E note:** cyber's gap→CAPA writes `cyber.cyber_action_plans` (its own action-plan aggregate), NOT the shared `capa.tasks`; CY-E routed + clocked *that* table. If a future epic wants one CAPA surface, that's a separate consolidation. **SOC-B shipped:** `invoke` is no longer a counter tick — it instantiates an `ir_playbook_run` + one `ir_playbook_run_task` per step (assigned + clocked); completing tasks advances the run to COMPLETED. New controller `IrPlaybookRunController` at `/api/v1/socops/playbook-runs` (+ `POST /playbooks/{id}/invoke` now returns the run detail, `GET /playbooks/{id}/runs`). **CY-D root-cause correction:** the SCHED-1 "request-scoped-bean landmine" was a *misdiagnosis* — `AuditLogService.currentRequestOrNull()` already guards `RequestContextHolder.getRequestAttributes()==null`, so audit writes are off-thread-safe. The vuln-SLA scheduler was dormant because `grc.vuln-sla.scheduler-enabled` defaults `false` and **no profile (dev or prod) turned it on** (its Javadoc's "prod flips it on" claim was never wired); additionally **no F8 rule existed for `VULN_SLA_BREACHED`**, so even when run the breach reached no human. Fix = enable in dev (60s) + prod (5m) + broadcast breaches to the `SECURITY_ANALYST` role directly. The socops sweep has no enable flag (always runs hourly) — it was never dormant, the review's 6-min window just missed the tick. **⚠ CY-B legacy backfill:** `V261` registers the 24 pre-existing workspace incidents into the register — a **data backfill of real rows**; ready + idempotent but **NOT applied to dev** (awaiting explicit confirmation per the standing destructive-op guard). The forward path (new incidents) is live + validated without it. **Rebuild rule:** stop :8084 before `mvn package`; DB seeds (like SOC-A) need only a fresh login, no rebuild. **Migration numbering:** V260 + V262 + V263 + V264 + V265 + V266 + V267 + V268 applied to dev; next free = V269 (V261 reserved for the CY-B backfill, still unapplied). **CY-J note:** V268 adds `cyber_evidence.title_ar` + `stale_since` + `re_request_id` (plain ADD COLUMN, idempotent). The expiry sweep reuses the shared `EvidenceRequestService` (anchor `entityType=CYBER_EVIDENCE`, matching CY-A) rather than inventing a cyber-local request; `CyberEvidenceExpiryScheduler` is config-gated (`grc.cyber-evidence-expiry.*`, enabled dev 60s + prod 1h). `CyberEvidenceService.updateEvidence` now carries over the sweep-managed `staleSince`/`reRequestId` so a client edit can't wipe them. **SOC-E note:** V267 adds two socops tables — `detection_rule` (ATT&CK-mapped detection library, `mitre_techniques` required, `uq_dr_org_code`) and `threat_intel_action` (FK→`threat_intel`; each row links an actioned IOC to a concrete ASSET/VULNERABILITY/DETECTION target with a captured label). Cross-module: `ThreatIntelService` now injects `CyberAssetRepository`+`VulnerabilityRepository` (by type — the `@Repository("cyberLegacyVulnerabilityRepository")` bean resolves fine) to validate+label link targets; no bean cycle (repos are leaf beans). **SOC-D note:** V266's ledger is append-only enforced by an INSTEAD OF UPDATE,DELETE trigger — to clean test rows you must `DISABLE TRIGGER socops.trg_fce_append_only` first, delete, then re-enable (the acceptance harness does this). **CY-F note:** exports are read-only — no migration. New shared engine `reporting/service/RegisterExport` (column-driven POI XLSX + OpenPDF) is reusable by any module; cyber exports live at `/api/v1/cyber/export`, socops security-events at `/api/v1/socops/events/export`. **CY-H note:** the cyber write surface binds `@RequestBody` straight to JPA entities (no DTO layer), so validation lives in the service (shared `CyberInputValidation` helper) rather than as `@Valid` DTO constraints — the ASM sub-module already uses proper `@Valid` DTOs. V265 (FK `cyber_evidence.control_id` + CHECK `status`) is WITH NOCHECK to stay portable across environments (legacy rows grandfathered, future writes enforced). **CY-I note:** no migration — cyber policies reuse the existing `policy.documents` engine (lifecycle/versioning/attestations/attachments already there), scoped by `category` ∈ {CYBER, INFORMATION_SECURITY, INCIDENT_RESPONSE}. `CyberPolicyController` (`/api/v1/cyber/policies`, CYBER-gated) delegates to `PolicyManagementService`; file upload rides the generic `/api/v1/attachments/PolicyDocument/{id}` subsystem. So next free is still **V266** (first used by SOC-D).

**CY-B direction note:** the plan's parenthetical suggested repointing 3 consumers at the workspace table, but the tables are schema-incompatible and `cyber.incidents` is *already* the system of record (dashboard + KPI trend + PDPL bridge + NCA/PDPL/SAMA/CITC/HRSD regulatory timers + MITRE + RCA all live on it — the very timers CY-C wants). So the lower-risk, non-destructive consolidation is the inverse: keep `cyber.incidents` canonical and make the SOC workspace *register into it* (mirror-on-create + status-sync). CY-C now lands its regulatory clocks on the table workspace incidents already flow to.

---

## 1. Executive Summary

The Cybersecurity and SOC Operations modules are **built on real foundations but operate as disconnected islands** — exactly the concern raised. They are **not empty demos**: 186 endpoints (89 cyber + 97 socops), 38 tables, and genuine Saudi-native seed data (154 controls, 529 sub-controls, 24 incidents, real NCA-ECC-aligned SLA policies). Audit logging (F7) and realtime broadcast (F8) fire on transitions, and tenant scoping holds. **But the layer that makes a GRC/SOC platform *operate* — the clock (due→countdown→reminder→escalation), cross-department routing, file-backed evidence, and cross-module continuity — is largely missing or unwired.**

The single most important finding validates the user's own example precisely: **cyber evidence and policy forms cannot upload a file and cannot open a request workflow — even though the platform already ships a complete, reusable evidence + attachment subsystem one module over.** The fix is *integration*, not new construction.

**Market-readiness verdict:**
- **Cybersecurity module: Partially usable → Needs major remediation.** Strong control library + a genuinely well-engineered vuln-SLA engine, but evidence is a façade, incidents lack regulatory timers/auto-CAPA, and the incident data is split across two tables (one of which the dashboard + PDPL bridge actually use).
- **SOC Operations module: Partially usable → Needs major remediation.** Rich aggregates (events, playbooks, forensics, threat-intel, ingest) with real schemas, but **the entire RBAC references roles that do not exist** (admin-only in practice), playbook `invoke` spawns no work, events never promote to incidents, and there are no exports.
- **Neither module is production-ready for a Saudi bank/government entity today**, but the gap is closable — most fixes are wiring existing capability, not greenfield.

**Islands map (where continuity breaks):**
```
Cyber controls ✔─assess→gap→CAPA(⚠ null owner/due, no clock)   Evidence(cyber) ✖ FAÇADE ── (real evidence/attachments subsystem exists, UNWIRED)
Cyber incident (cyber_incidents) ──✖──> Dashboard KPIs & PDPL breach bridge  (they read a DIFFERENT table: cyber.incidents / module/incident)
SOC event ──✖──> Incident (triage/resolve never set incident_id)
IR playbook invoke ──✖──> tasks (counter only; no run table)
Vuln-SLA engine ✔ correct ── scheduler ✖ dormant (clock doesn't tick)
SOC RBAC ──✖──> real roles (CISO/analyst/ingest roles don't exist → admin-only)
Cyber/SOC ──✖──> exports (no Excel / Arabic PDF anywhere)
```

---

## 2. Module Reviewed
- **Cybersecurity** — `module/cyber` (+ sub-packages `asm`, `vuln_sla`, `threatintel`): 5 controllers, `/api/v1/cyber/*`, the `/cyber` 13-tab workspace, 18 tables.
- **SOC Operations** — `module/socops`: 20 controllers/aggregates in 6 bands, `/api/v1/socops/*`, `/socops/*` pages, 20 tables.

## 3. Input Documents Used
Both mandatory maps were read and used as **claims to verify** (not truth):
- `docs/CYBERSECURITY_MODULE_MAP.md` ✔ — its 13-tab workspace + 5-controller catalogue matched the code; its workflow claims were tested (incident 9-step ✔ exists, vuln-SLA ✔ exists, evidence lifecycle ✖ façade).
- `docs/SOC_OPERATIONS_MODULE_MAP.md` ✔ — its 20-aggregate/6-band model + role-based auth claim matched code; the roles it documents were tested and found **unseeded**.

## 4. Repository Context
- Backend Spring Boot 3.5 / Java 17 on :8084 (dev profile, Flyway OFF, ddl-auto=none). Frontend React/Vite on :5173 (durable). SQL Server `TSS_GRC`, single dev tenant org_id=1.
- Auth: `POST /api/auth/login`, token field `accessToken`; admin/Admin@12345 = PLATFORM_ADMIN + CYBER/EVIDENCE/… grants.
- Harness `scratchpad/rev/h.py` (`api()`+`db()`). Endpoint catalogue: `scratchpad/rev/catalogue.md`.
- **Data-safety note:** an initial cleanup used `LIKE '[REVIEW-TEST]%'`; in SQL Server `[...]` is a character class, which soft-deleted seed rows. Caught immediately, restored via `is_deleted=0` (CHARINDEX literal match), **verified live counts back to baseline** (evidence 12, assessments 21, action-plans 13, incidents 24, SLA findings 11). No data lost. Lesson recorded.

## 5. Existing Module Inventory
| Area | Endpoints | Live data | Verdict |
|---|---|---|---|
| Cyber controls/sub-controls/frameworks | list+CRUD | 154/529/6 | REAL data, PARTIAL process |
| Cyber assessments + convert-gap + action-plans | CRUD + convert + complete | 21/13 | PARTIAL (CAPA born null-owner/due) |
| Cyber evidence | CRUD only | 12 | **FAÇADE** (no upload/request/review) |
| Cyber vulnerabilities + 6-step + exceptions | CRUD + escalate + approve | 20/2 | PARTIAL |
| Vuln-SLA engine (policies/findings/compute/escalation) | full | 4/11 | **REAL logic / dormant scheduler** |
| Cyber incidents (cyber_incidents) + start-response + RCA | CRUD + 9-step | 24/12 | PARTIAL (no timers/auto-CAPA) |
| ASM assets/findings/shadow-IT | CRUD + transitions | 20/14/4 | PARTIAL (create 400/500 on bad input) |
| SOC events + triage/resolve + SIEM ingest | full | 15 | PARTIAL (no promotion) |
| IR playbooks + clone + invoke + steps | full | 37/72 | **invoke = FAÇADE** |
| Forensics + custody + integrity | full | 4 | PARTIAL (no custody ledger) |
| Threat-intel + action | full | 10 | PARTIAL (action = flag) |
| SOC bands: brand, DLP, appsec, sbom, iam-jml, crypto, ot, zt, pentest, insurance, m&a, regulator-exam, awareness, scanner, seasonal | CRUD + state | populated | PARTIAL (breadth good; clock/workflow unproven) |
| Dashboards (cyber/asm/vuln-sla/socops) | 4 | computed | PARTIAL (real numbers; drill-down TBD) |
| Exports (Excel/Arabic PDF) | — | — | **MISSING** |

## 6. Full Real-World Cybersecurity + SOC Task List
The complete first-to-last task inventory (25 processes across governance, assess→gap→CAPA, policy, evidence lifecycle, asset/ASM, vuln+SLA, threat-intel, incident E2E, SOC triage, playbooks, forensics, ingest, brand/M&A/reviews, awareness, dashboards, exports, i18n, RBAC, tenant, bridges) — with actors/RACI, trigger, steps, states, the clock, attachments, approvals, notifications, audit, reports — is in [`review/cyber-socops/00-reference-model.md`](review/cyber-socops/00-reference-model.md). It defines 10 "operating-system" primitives (OS-1 assignment … OS-10 reporting) used as the grading rubric.

## 7. Ideal Cybersecurity Workflow Design
Designed workflows (full spec per workflow) are in the reference model §A–§C and §E. The non-negotiable shape for each: trigger → assign (owner/dept) → **due + countdown + reminders + escalation** → **file-backed evidence** → maker-checker approval → audit → report. Key ones: control assessment→gap→CAPA (auto-raise, routed, clocked), policy lifecycle (upload→version→approve→publish→attest), **evidence request→collect→review→approve→retain/expire** (the seed complaint), vuln-SLA (severity×exposure×criticality with escalation), incident 9-step with **NCA 4h / SAMA / PDPL 72h regulatory timers** + auto-CAPA from lessons.

## 8. Ideal SOC Operations Workflow Design
Designed in the reference model §C–§D: alert intake→triage(TP/FP)→**promote to incident**→investigate→contain/eradicate/recover→close→lessons; IR playbook clone→**invoke spawns assigned, clocked step-tasks**; forensics with **append-only chain-of-custody**; brand takedown with SLA; scheduled reviews (insurance/pentest/privileged-access/cert-expiry) driven by a **live sweep**; machine ingest via **least-privilege service accounts**.

## 9. Current Implementation Findings (validated)
See §11–§24 for the categorized findings. All findings were reproduced live; verdicts marked CONFIRMED carry a receipt in `review/cyber-socops/1N-wave-*.md` + `scratchpad/rev/*_receipts.txt`.

## 10. Add / Improve / Keep / Remove / Defer Decision Log
| # | Item | Decision | Reason |
|---|---|---|---|
| D1 | Cyber evidence island (CyberEvidence) | **Replace** | Duplicates a full subsystem badly; rewire to `module/evidence` + `module/attachment` |
| D2 | Vuln-SLA engine | **Keep + Improve** | Correct, Saudi-native; only the scheduler + seed recompute need fixing |
| D3 | IR playbook invoke | **Replace** | Counter-only; must instantiate step-tasks |
| D4 | SOC role model | **Add** | The referenced roles must be created + granted (they don't exist) |
| D5 | Dual incident tables (cyber_incidents vs incidents) | **Replace/Consolidate** | Two systems; dashboard + PDPL use the other one |
| D6 | Regulatory notification timers on incidents | **Add** | Saudi market mandatory (NCA/SAMA/PDPL clocks) |
| D7 | Exports (Excel/Arabic PDF) | **Add** | Absent; regulators consume PDFs |
| D8 | Control library, frameworks, seed data | **Keep** | Genuinely strong and bilingual |
| D9 | F7 audit + F8 realtime | **Keep** | Working plumbing to build on |
| D10 | Forensics custody | **Improve** | Add append-only ledger table |

## 11. Critical Gaps (validated)
| ID | Finding | Sev | Verdict | Receipt |
|---|---|---|---|---|
| **C-CRIT-1** | **Cyber evidence is a façade** — `POST /cyber/evidence` succeeds with **no file**; no upload/request/review endpoints (404/405); `CyberEvidence` has only a `file_name` string + no `title_ar`; `CyberPage.tsx` has 0 file inputs. The real subsystem (`/api/v1/attachments`, `/api/v1/evidence/requests` [15 live], `/api/v1/evidence/reviews`, FE `AttachmentsField`) exists **unwired**. | Critical | CONFIRMED | W1 |
| **C-CRIT-2** | **SOC RBAC references non-existent roles** — `iam.roles` (32) contains **none** of CISO, SECURITY_ANALYST, FORENSICS_LEAD, IR_LEAD, or any `*_INGEST`. Every socops `@PreAuthorize` falls back to PLATFORM_ADMIN → module is **admin-only**; SIEM/scanner/DLP webhooks can only authenticate as full admin. | Critical | CONFIRMED | W4 |
| **C-CRIT-3** | **Two live incident systems** — Cyber workspace manages `cyber.cyber_incidents` (24), but `DashboardService` KPIs and the **PDPL breach bridge** (`CyberPdplBridgeService`) read/update `cyber.incidents` (20, owned by `module/incident`). Cyber-workspace incidents never reach the dashboard or PDPL linkage. | Critical | CONFIRMED | W2 + code |

## 12. Cybersecurity Control Assessment Gaps
- **CONFIRMED (High):** `convert-gap` creates a CAPA with **`dueDate=null`, `ownerName=null`, status OPEN** — an unassigned, unclocked placeholder. No auto-assignment, no clock. (Receipt: W1 A2.)
- **CONFIRMED (Medium):** no per-control **owner/evidence-owner** enforcement, no SoA/applicability endpoint, no periodic-reassessment scheduler.
- **CONFIRMED (Medium):** control assessment can be marked COMPLIANT with `evidenceCount=0` (no evidence gate).

## 13. Vulnerability Management Gaps
- **CONFIRMED (High) SCHED-1:** vuln-SLA **escalation scheduler dormant** — `@EnableScheduling` is on (no profile/property guard) yet `last_escalation_at` stayed NULL for all 11 findings across a 6-min window while 2 sat BREACHED; last recompute 2026-06-27. The clock doesn't tick autonomously. Suspect: request-scoped-bean landmine that kills scheduler threads. (Receipt: W3.)
- **CONFIRMED (Medium):** seed SLA hours stale (CRITICAL showed 72h until manual recompute; `recompute` correctly reset to 1h→0.25 floor). Formula itself is **correct + NCA-ECC-2-12 aligned** (KEEP).
- **CONFIRMED (Medium):** vuln 6-step `escalate` is a linear index-bump; `OWNER_ASSIGNMENT` sets no owner. Exceptions approve with **no expiry** (not time-boxed).
- **CONFIRMED (Medium) VAL-3:** `POST /asm/shadow-it` returns **500** on bad input (not 400).

## 14. Cyber Incident Management Gaps
- **CONFIRMED (High):** **no regulatory notification timers** — only a `notification_sent` boolean; no NCA 4h / SAMA / PDPL 72h deadline/countdown/escalation columns. Saudi-market blocker.
- **CONFIRMED (High):** **no auto-CAPA** — reaching LESSONS_LEARNED creates 0 action-plans; `lessons_learned` is free text.
- **CONFIRMED (Medium):** `start-response` is a linear status-bump (DETECTION→…→LESSONS_LEARNED) with no per-step tasks, evidence gating, or closure approval. `bcm_handoff` boolean never enforced.
- **CONFIRMED (Critical, see C-CRIT-3):** incident model split; PDPL/BCM linkage binds to the other table.

## 15. SOC Alert and Case Management Gaps
- **CONFIRMED (High):** SOC event triage/resolve **never sets `incident_id`** — no promotion path from a true-positive alert to a managed incident (the SOC spine). FK column exists but is dead. (Receipt: W2 C3.)
- **CONFIRMED (High):** IR playbook **`invoke` is a counter** — increments `invocation_count` + fires an event, spawns **no tasks**; no run/task/instance table exists (only `scanner_run`). Playbook steps (with `responsibleRole`/`estimatedMinutes`) never become work. (Receipt: W2 C2.)
- **CONFIRMED (Medium):** no case/L1-L2-L3 model distinct from events; no shift-handover; MTTA/MTTD/MTTR not computed (only counts on dashboard).

## 16. Detection Engineering and Use Case Gaps
- **PLAUSIBLE (Medium):** threat-intel is data-rich (IOC type/value, MITRE tactics/techniques, TLP, confidence) but `/{id}/action` flips an `actioned` boolean — no linkage to assets/vulns/detections or block action. Detection-rule/use-case library + MITRE mapping as first-class objects are **MISSING** (only threat-intel rows). (Confirm the action side-effect in implementation.)

## 17. Evidence Workflow Gaps
- **CONFIRMED (Critical, C-CRIT-1):** no request, no upload, no reviewer accept/reject, no versioning, no expiry sweep in cyber. The `expiry_date` column is never swept. The reusable subsystem exists and must be wired in.

## 18. Policy/Document Workflow Gaps
- **CONFIRMED (High/Missing):** no `/cyber/policies*` endpoint — cyber has no policy lifecycle (draft→review→approve→publish→attest→review-date). Any "policy" create in the workspace inherits the evidence façade (no file upload). Policy management lives only in the compliance module; cyber is unwired to it.

## 19. Button and Process Testing Results
Representative live results (full tables in wave docs + `scratchpad/rev/*_receipts.txt`):

| Page/Endpoint | Action | Expected | Actual | Pass | Sev |
|---|---|---|---|---|---|
| /cyber evidence | Create with file | upload persisted | 200, **no file field**; upload 404/405 | ✖ | Crit |
| /cyber evidence | Request evidence | opens routed workflow | 404/405 (none) | ✖ | Crit |
| /cyber assessment | convert-gap | routed clocked CAPA | 200 but null owner/due | ✖ | High |
| /cyber incident | start-response ×5 | 9-step w/ timers | linear bump, no timers | ⚠ | High |
| /socops playbook | invoke | spawn step-tasks | counter++ only | ✖ | High |
| /socops event | triage→resolve | promote to incident | status only, incident_id null | ✖ | High |
| /cyber vuln-sla | recompute | formula applied | ✔ correct (1h→0.25 floor) | ✔ | — |
| /cyber vuln-sla | escalation/run | escalate + stamp | ✔ 7 breaches, stamped | ✔ | — |
| (scheduler) | auto-escalate 5min | stamp within interval | ✖ NULL after 6 min | ✖ | High |
| /cyber evidence | POST {} | 400 | **500** | ✖ | Med |
| /cyber evidence | bad controlId+status | 400 | **200 persisted** | ✖ | Med |
| /cyber controls | auditor GET | 200 read | **403** (WRITE-gated + no auditor grant) | ✖ | High |
| /socops events | auditor GET | scoped | 403 (gated ✔) | ✔ | — |
| /cyber incidents/999999 | IDOR | 404 | 404 ✔ | ✔ | — |
| /socops/* mutations | non-admin role | least-privilege | only PLATFORM_ADMIN works (roles missing) | ✖ | Crit |

## 20. Data Model Gaps
- `CyberEvidence`: no binary/attachment link, no `title_ar`, `expiry_date` unswept, no FK on `control_id` (phantom 999999 accepted).
- Dual incident tables (`cyber_incidents` vs `incidents`) with different schemas.
- No `ir_playbook_run`/task table. No forensics `custody_ledger` table (custody = single `current_custodian` field). No `regulatory_notification` timer columns on incidents. No detection-rule/use-case entity.

## 21. API Gaps
- No evidence-request/upload/review endpoints in cyber (exist in `module/evidence`, unwired).
- No `/export` / report endpoints for cyber or socops (Excel/Arabic PDF).
- Read endpoints gated on WRITE (`/cyber/controls`, `/assessments`, `/incidents`); `/all` variants gated on READ — inconsistent.
- No event→incident promotion endpoint; no playbook-run endpoints.

## 22. UI/UX Gaps
- `CyberPage.tsx`: 0 file inputs; create modals imply attachments but offer none. Reusable `AttachmentsField`/`FileUploadField` unused.
- No countdown/reminder/overdue UI affordances (there's no clock behind them).
- Dashboards render real numbers; drill-down to source records unverified.

## 23. Arabic/RTL Gaps
- **Good:** controls/incidents/action-plans fully bilingual (0 missing `title_ar`); vuln-SLA policy bilingual; incident/playbook/forensics/threat-intel carry `title_ar`.
- **Gap:** `CyberEvidence` has **no `title_ar` column** (English-only island) — breaks the bilingual rule.

## 24. Integration Gaps
| Integration | Status |
|---|---|
| Evidence repository / attachments | **Unwired** (exists, cyber ignores it) |
| PDPL breach linkage | **Wrong target** — binds `cyber.incidents`, not the cyber-workspace incident |
| Dashboard KPIs | **Wrong target** — counts `cyber.incidents`, not `cyber_incidents` |
| SOC event → incident | **Missing** (dead FK) |
| Assessment gap → CAPA | Partial (created but not routed/clocked) |
| ERM / risk register linkage | Not evidenced from cyber incidents |
| BCM/crisis handoff | Boolean flag, not enforced |
| F8 event bus | Published widely; consumed by asm/vuln_sla listeners; incident/event consumers thin |
| Notifications / schedulers | Present but **dormant** (SCHED-1) |
| Exports/reports | **Missing** |

## 25. Required Remediation Backlog (epics)
Prefix **CY-** (cyber) / **SOC-** (soc ops). Each = problem → target design → outline (BE/FE/DB, forward-only VNNN) → acceptance (exact live test) → effort.

- **CY-A (Critical, L) — Wire evidence to the real subsystem.** Replace `CyberEvidence` CRUD with `module/evidence` + `/api/v1/attachments`. Add request→assign(dept)→due+countdown→reminders→escalation→upload→review(accept/reject)→link→expiry. *Accept:* create evidence with a real uploaded file (multipart 201), raise a request to another dept that appears in their queue with a due date, reject→resubmit, and see it linked to a control. *Effort L.*
- **SOC-A (Critical, M) — Seed + grant the SOC role model.** Create CISO, SECURITY_ANALYST, L1/L2/L3, FORENSICS_LEAD, IR_LEAD, and least-privilege service roles (SIEM_INGEST, SCANNER_INGEST, DLP_INGEST, APPSEC_INGEST, BRAND_INGEST, JML_INGEST, TI_INGEST) in `iam.roles` + permission grants; issue ingest service accounts. *Accept:* a SECURITY_ANALYST (not admin) can triage; a SIEM_INGEST account can POST `/siem-events` but nothing else. *Effort M.* (forward-only seed migration)
- **CY-B (Critical, M) — Consolidate the incident model.** One incident system: point `module/incident`, `DashboardService`, and `CyberPdplBridgeService` at the cyber-workspace incident (or merge tables). *Accept:* an incident created in `/cyber` appears in the dashboard "open incidents" KPI and can be PDPL-linked. *Effort M.* (data migration + forward-only VNNN)
- **CY-C (High, M) — Incident regulatory timers + auto-CAPA.** Add NCA 4h / SAMA / PDPL 72h deadline+countdown+escalation columns; auto-raise a routed CAPA on LESSONS_LEARNED. *Accept:* a HIGH incident shows a running NCA clock; closing with lessons creates a CAPA with owner+due. *Effort M.*
- **SOC-B (High, M) — Playbook invoke → runnable tasks.** New `ir_playbook_run` + `ir_playbook_run_task` tables; `invoke` instantiates each step as an assigned, clocked task. *Accept:* invoking a 9-step playbook creates 9 tasks with owner+due; completing them advances the run. *Effort M.*
- **SOC-C (High, S) — Event→incident promotion.** Triage "escalate" sets `incident_id` (creating a cyber incident) and carries context. *Accept:* triaging an event as TRUE_POSITIVE→escalate creates/links an incident; dashboard reflects it. *Effort S.*
- **CY-D (High, S) — Fix the schedulers.** Diagnose the dormant vuln-SLA scheduler (request-scoped-bean landmine) + verify the socops hourly sweep; prove both stamp within one interval. *Accept:* leave a breached finding; within one interval `last_escalation_at` is set and a notification is emitted — with **no manual POST**. *Effort S.*
- **CY-E (High, S) — CAPA routing + clock.** `convert-gap` sets owner(dept)+due; add a CAPA reminder/overdue-escalation scheduler. *Accept:* a converted gap yields a CAPA with owner+due that escalates when overdue. *Effort S.*
- **CY-F (High, M) — Exports.** Excel (POI) + Arabic-RTL PDF (OpenPDF) for cyber + socops registers; a board pack section. *Accept:* export the vuln register to Arabic PDF and the control register to Excel. *Effort M.*
- **CY-G (High, S) — RBAC read gates + auditor access.** Change list-read endpoints to require READ (not WRITE); grant AUDITOR read on cyber/socops. *Accept:* AUDITOR reads controls (200); a READ-only user pages lists. *Effort S.*
- **CY-H (Med, S) — Input validation.** DTO validation → 400 (not 500) on empty body; FK check on `control_id`; enum check on `status`; fix `/asm/shadow-it` 500. *Effort S.*
- **CY-I (Med, S) — Policy lifecycle in cyber** (or wire to compliance) with file upload + versioning + approve/publish + review-date. *Effort S–M.*
- **SOC-D (Med, S) — Forensics custody ledger.** Append-only `forensics_custody_event` table; `transfer-custody` appends (who→who, when, hash), never overwrites. *Effort S.*
- **SOC-E (Med, S) — Threat-intel action + detection library.** `action` links IOC→asset/vuln/detection; add a detection-rule/use-case entity with MITRE mapping. *Effort S–M.*
- **CY-J (Med, S) — Evidence expiry sweep + `title_ar`.** Sweep `expiry_date`→Stale + re-request; add `title_ar`. *Effort S.*

## 26. Immediate Fixes (Critical/High first)
Order: **CY-A → SOC-A → CY-B → CY-D → SOC-C → CY-C → SOC-B → CY-E → CY-G → CY-F**.

## 27. Structural Fixes
CY-B (incident consolidation), CY-A (evidence subsystem adoption), SOC-B (playbook run model) are structural — they change data models and must ship with forward-only migrations + data backfill, never by editing applied migrations.

## 28. Data Model Changes Required
New/changed (all **forward-only VNNN**, Flyway-managed in staging/prod; hand-applied in dev): incident regulatory-timer columns; `ir_playbook_run(_task)`; `forensics_custody_event`; detection-rule/use-case; `CyberEvidence.title_ar` (or deprecate in favor of the evidence module); FK + enum constraints on evidence; SOC role + permission seed rows.

## 29. API Changes Required
Evidence request/upload/review routes wired into cyber; event→incident promotion; playbook-run endpoints; `/export` (Excel+PDF) for both modules; read-gate corrections; policy endpoints.

## 30. Workflow Engine Changes Required
Introduce the shared "clock" primitive (due→countdown→reminder→escalation) as a reusable service and apply it to CAPA, evidence requests, incident regulatory timers, vuln-SLA (fix scheduler), and SOC reviews. Reuse the maker-checker approval + `AttachmentService` + notification dispatch that already exist.

## 31. Testing Plan
Per-epic acceptance tests are in §25. Regression: full `mvn test` after each structural epic; live re-run of `scratchpad/rev/w1..w3.py` (evidence upload now passes; convert-gap CAPA has owner/due; scheduler stamps autonomously; playbook invoke creates tasks; event promotes to incident). Add controller-authz ratchet tests for the new SOC roles.

## 32. Acceptance Criteria for Remediation
Each epic lists a concrete live test (§25). Module-level exit: (a) evidence can be requested cross-dept, uploaded, reviewed, expired — with receipts; (b) SOC operable by non-admin roles; (c) one incident system feeding dashboard + PDPL; (d) schedulers proven ticking; (e) exports produced in Arabic PDF + Excel.

## 33. Implementation Order
1. **SOC-A** (unblocks all SOC testing by non-admins) →
2. **CY-A** (the headline user complaint) →
3. **CY-B** (incident consolidation — unblocks dashboard/PDPL) →
4. **CY-D** (schedulers — makes every clock real) →
5. **SOC-C**, **CY-C**, **SOC-B**, **CY-E** (workflow continuity) →
6. **CY-G**, **CY-H**, **CY-F** (RBAC, validation, exports) →
7. **CY-I**, **SOC-D**, **SOC-E**, **CY-J** (depth).

## 34. Risks if Not Fixed
- Audit/regulator failure: evidence can't be produced (no upload/request/expiry) → NCA/SAMA review fails.
- SOC unusable at scale: admin-only; SIEM connectors need god credentials (security risk in itself).
- Incidents invisible to leadership + PDPL: the dashboard and breach process track a different table.
- Missed remediation SLAs: the vuln clock doesn't tick; playbooks don't execute; CAPAs aren't chased.
- No board/regulator reporting (no exports).

## 35. Final Recommendation
- **Cybersecurity module:** **Partially usable — Needs major remediation.** Keep the control library, the vuln-SLA formula, and the audit/realtime plumbing; replace the evidence island, consolidate incidents, add timers/auto-CAPA/exports, and fix the scheduler.
- **SOC Operations module:** **Partially usable — Needs major remediation.** Keep the aggregate breadth + schemas; create the role model (top priority), make playbook invoke and event-promotion real, add the custody ledger and exports.
- **Both are recoverable without a rewrite** — the dominant theme is *wiring existing capability and adding one shared "clock" primitive*, not building from scratch.

---

## Appendix A — Implementation Backlog (user stories + acceptance)
Format per epic. Example (CY-A):
> **As a** GRC officer, **I want to** request evidence from the IT department with a due date and reminders, **so that** I can collect audit-ready proof on time.
> **Given** an open control assessment **When** I raise an evidence request to "Information Technology" with a 7-day due date **Then** it appears in IT's queue with a countdown, sends T-3/T-1 reminders, escalates on overdue, accepts a file upload, supports reject→resubmit, and links the approved file to the control — with an audit entry at each step.
(Remaining epics CY-B…SOC-E follow the same story+Given/When/Then structure — see §25 acceptance lines.)

## Appendix B — Test Cases (IDs, live-verified where run)
Cyber: TC-CY-01 create cycle … TC-CY-08 request evidence (**FAIL** — no endpoint) … TC-CY-09 upload evidence (**FAIL** — no field) … TC-CY-15 mark compliant w/o evidence (**FAIL** — allowed) … TC-CY-24 create vuln (PASS) … TC-CY-28 approve vuln closure … TC-CY-38 close incident w/ RCA (PARTIAL — no auto-CAPA) … TC-CY-41 Arabic (PASS) … TC-CY-43 unauthorized (auditor 403 — **over-restrictive**) … TC-CY-44 audit log (PASS).
SOC: TC-SOC-03 triage alert (PASS) … TC-SOC-08 create case from alert (**FAIL** — no promotion) … TC-SOC-14 execute playbook (**FAIL** — no tasks) … TC-SOC-18 false positive w/ reason (PASS) … TC-SOC-20 escalate to incident (**FAIL**) … TC-SOC-31 export case package (**FAIL** — no export) … TC-SOC-34 unauthorized (gated ✔) … TC-SOC-35 audit log (PASS).
Full receipts: `scratchpad/rev/w1_receipts.txt`, `w2_receipts.txt`, `w3_receipts.txt`; catalogue `scratchpad/rev/catalogue.md`.

## Appendix C — Output metrics
- Endpoints catalogued: **186** (89 cyber + 97 socops); parameterless GETs live-probed: **47/47 = 200**.
- Tables: **38** (18 cyber + 20 socops); roles: **32** (0 of the 11 SOC roles present).
- Workflows reviewed: **25**; buttons/endpoints exercised live: **~40** (create/transition/approve/escalate/triage/resolve/invoke/recompute/escalation-run across both modules).
- Gaps by severity: **Critical 3 · High 8 · Medium ~9 · Low/Cosmetic few.**
- Both maps read + used: ✔ `docs/CYBERSECURITY_MODULE_MAP.md`, ✔ `docs/SOC_OPERATIONS_MODULE_MAP.md`.

## Recommended next command (implement)
> Implement the remediation backlog from `CYBER_SOC_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` in the §33 order, Critical/High first (SOC-A → CY-A → CY-B → CY-D → SOC-C → CY-C → SOC-B → CY-E → CY-G → CY-H → CY-F). Do not remove working features unless marked Replace/Remove. Improve/wire before adding. After each epic run relevant tests + a live re-check via `scratchpad/rev/*.py`, and update this file's status. One epic per fire on a feature branch; merge only on explicit approval.
