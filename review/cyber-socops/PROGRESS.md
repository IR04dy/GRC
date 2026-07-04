# Cyber + SOC Ops — Review Progress

Review-only deep audit. One phase per fire. Read this first every fire.

| Phase | What | Artifact | Status |
|---|---|---|---|
| P0 | Expert reference model (market task inventory) | `00-reference-model.md` | ✅ DONE |
| P1 | Coverage matrix (REAL/PARTIAL/FAÇADE/MISSING, both ways) | `01-coverage-matrix.md` | ✅ DONE |
| P2 | W1 governance (controls, assess→gap→CAPA, policy+evidence) — seed S1/S2 | `11-wave-governance.md` | ✅ DONE |
| P3 | W2 incident response E2E (cyber 9-step + socops triage + IR playbook) | `12-wave-incident.md` | ✅ DONE |
| P4 | W3 vulnerabilities (registry, 6-step, SLA engine, exceptions, ASM, shadow-IT) | `13-wave-vuln.md` | ✅ DONE |
| P5 | W4 SOC ingest & ops (ingest roles, forensics, threat intel) | tested → merged plan | ✅ DONE |
| P6 | W5 socops bands + schedulers (proof) | tested → merged plan | ✅ DONE (schedulers dormant) |
| P7 | W6 cross-cutting (exports, i18n, RBAC, tenant, bridges) | tested → merged plan | ✅ DONE |
| P8 | Consolidate → **merged** remediation plan (Desktop-command structure) | `../../CYBER_SOC_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` | ✅ DONE |

**REVIEW COMPLETE (2026-07-04).** Merged plan at repo root: `CYBER_SOC_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (35 sections + backlog + test cases, every finding validated). Verdict: both modules Partially-usable → Needs major remediation. 3 Critical / 8 High / ~9 Medium. Review-only — NOT committed (awaiting explicit order). Next: implement backlog §33 order, one epic per fire.

### W4–W6 key validated findings (folded into merged plan)
- **SOC roles do not exist** (Critical): iam.roles has 32 roles, ZERO of CISO/SECURITY_ANALYST/FORENSICS_LEAD/IR_LEAD/*_INGEST → socops is PLATFORM_ADMIN-only; SIEM/scanner/DLP webhooks need god creds.
- **ARCH-1 escalated to Critical**: `cyber.incidents` is live — owned by `module/incident`, read by DashboardService KPIs + CyberPdplBridgeService (PDPL breach) — a DIFFERENT table from the Cyber workspace's `cyber_incidents`. Cyber-workspace incidents never reach the dashboard or PDPL linkage.
- **SCHED-1 confirmed** (High): @EnableScheduling on, no guard, yet vuln-SLA scheduler stamped nothing in 6 min → dormant (request-scoped-bean landmine suspect). socops sweep @Scheduled hourly — same risk.
- **Exports MISSING** (High): no /export, no POI/OpenPDF in cyber or socops (all probes 404/405).
- **Forensics** (Medium): rich court-grade fields (md5/sha256/sha512, legal hold) but NO custody-ledger table — `current_custodian` is a single mutable field, not an append-only chain.
- i18n good (0 missing titleAr on controls/incidents/CAPA) except CyberEvidence (no titleAr column).

## Environment (verified this run 2026-07-04)
- Backend :8084 UP (health 200). Frontend :5173 UP (200) — DURABLE, never restart.
- Auth endpoint = `POST /api/auth/login` (NOT `/api/v1/`). Token field = `accessToken`.
- admin/Admin@12345 roles: PLATFORM_ADMIN + CYBER_READ/WRITE/DELETE + EVIDENCE/COMPLIANCE/BCM/SCM/SUST/NDI/RISK grants. org_id=1.
- Users in iam.users: admin, auditor, compliance, ranalyst, rmanager, so_officer, viewer (all org 1).
- DB: SQL Server TSS_GRC via pyodbc. cyber schema = 18 tables, socops schema = 20 tables.
- Harness: `scratchpad/rev/h.py` — `api(method,path,body,tok)` + `db(sql)`. Reuse it.

## Running counts (through W3)
- FAÇADE 2 (A4 cyber evidence, C2 playbook-invoke) · MISSING 1 (A3 policy in cyber) · PARTIAL 8 · REAL-logic-but-dormant 1 (vuln-SLA engine) · REAL fully-proven 0
- High findings 4: RBAC WRITE-gated reads · auditor has zero CYBER access · ARCH-1 duplicate incident tables (cyber_incidents 24 vs incidents 20) · SCHED-1 vuln-SLA escalation scheduler dormant (last_escalation_at NULL, last recompute 2026-06-27)
- Medium ~8: 500 on empty evidence body · no FK/enum validation on evidence · CyberEvidence no titleAr · no auto-CAPA from incident lessons · no regulatory notification timers on incidents · exceptions not time-boxed · stale SLA seed · POST /asm/shadow-it 500 on bad input
- Positives: real seed data everywhere; F7 audit + F8 realtime fire on transitions; SOC events have SLA clock; vuln-SLA formula correct + NCA-ECC-aligned; tenant scoping (SEC-CYB-001) holds; socops lists gated (auditor 403)

## Seed findings — RESOLVED in W1 (both CONFIRMED real)
- S1 ✅ CONFIRMED — cyber evidence create has no file upload (UI 0 file inputs, API 404/405, schema = file_name string only).
- S2 ✅ CONFIRMED — cyber has no evidence-request workflow; the real one (`/api/v1/evidence/requests`, n=15) + attachments API + FE `AttachmentsField` all exist but are unwired. Fix = integration, not new build.

## Notes for next fire (P3 / W2 — incident response)
- Test: cyber 9-step incident lifecycle (`/incidents/{id}/start-response`), regulatory notification timers (NCA/SAMA/PDPL), evidence attach on incident, auto-CAPA from lessons.
- socops security-event triage→resolve→**promote to incident?**; IR playbook clone→invoke (does invoke spawn assigned/clocked tasks or just clone rows?).
- Confirm ARCH-1: are `cyber.cyber_incidents` (24) and `cyber.incidents` (20) two live incident systems?
- ⚠️ Cleanup test rows with **CHARINDEX** literal match, NEVER `LIKE '[REVIEW-TEST]%'` (bracket = char class). Verify live counts vs baseline after.
- Harness ready: `scratchpad/rev/h.py`. Baseline counts: catalogue.md + P1 doc.
