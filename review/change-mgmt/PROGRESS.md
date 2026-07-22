# Change Management audit — PROGRESS

**Status:** Review COMPLETE (read-and-test only; no product code changed). Deliverable = `../../CHANGE_MANAGEMENT_MODULE_REVIEW_AND_REMEDIATION_PLAN.md`.
**Date:** 2026-07-21 · **Stack:** backend :8084 (PID 30388), Vite :5173 (PID 19624), TSS_GRC / org 1 (15 change rows).

## What was reviewed
- Backend `module/change_mgmt/` — controller (161 LoC), service (423 LoC), 5 entities, 5 repos, all read in full.
- FE `tss-grc-ui-v2/src/pages/changes/ChangesPage.tsx` (710 LoC) + `mockChanges.ts` + `lib/api.ts changeApi`.
- DB schema/constraints (`cm_db_inspect.py`), F8 rules (`V146`), reporting integration (board pack only).

## Findings (3C / 3H / 4M / 4L) — see plan §3
- **CM-C1** No SoD; `WORKFLOW/WRITE` granted to 54 roles incl VIEWER/EXTERNAL_AUDITOR → viewer POST 201 (live).
- **CM-C2** CAB approval decorative — 0-vote approve 200, REJECT ignored 200, self-approve 201, approver_id NULL (live).
- **CM-C3** `PUT {status}` bypasses guard → DRAFT→CLOSED 200; guarded transition 400 (live).
- **CM-H1** No @Valid / no CHECK — junk enums persist 200 (live); Arabic title unenforced.
- **CM-H2** Only CREATE+TRANSITION audited; `update()` status change unaudited (live).
- **CM-H3** Changes Export button downloads the RISK register (code); no change exporter exists.
- **CM-M1** Demo fallback fabricates data for authenticated users on empty/error (code).
- **CM-M2** Task hard-delete (no is_deleted col) + unaudited (live/schema).
- **CM-M3** Approver/comment identity not captured (approver_id NULL; comment 'You'/'Unknown').
- **CM-M4** Task SLA façade — dueAt never set by FE, no @Scheduled.
- **CM-L1** STANDARD not pre-approved (single state map). **CM-L2** dead in-page create branch.
- **CM-L3** linksForEntity org-unscoped query (post-filtered). **CM-L4** votes accepted outside window.

## Strengths
Real 14-state ITIL model + guarded transition(); F8 automation genuinely wired (AR-96..100); tenant isolation via require(); rich cross-module links; bilingual/realtime/board-pack/analytics present.

## Live-probe notes
- All probes go through the Vite proxy (:5173) as admin. Test rows tagged `[REVIEW-TEST]`, cleaned via CHARINDEX (children first). DB restored to 15 rows after each run — verified.
- One deferred check: cross-tenant 404 (P9) — blocked by the platform **login rate-limiter** (IP-keyed, empty-body 401) after rapid repeated logins + STOMP broker auth traffic. Unrelated to CM; browser unaffected. Tenant isolation reported code-verified (require() org filter on every path). To re-run cm_probe4 later: space out logins or cache one token.

## Remediation (NOT started) — plan §5
CM-1 governance/SoD/quorum · CM-2 lifecycle integrity + validation · CM-3 audit + soft-delete tasks · CM-4 change-register export · CM-5 task SLA scheduler · CM-6 data honesty · CM-7 polish. Next free migration **V300** (V1→V299 packaged).
