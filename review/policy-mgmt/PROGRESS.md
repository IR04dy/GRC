# Policy Management audit — evidence & PROGRESS

**Status:** Review COMPLETE (read-and-test only; no product code changed). Findings folded into `../../CHANGE_AND_POLICY_MODULE_REVIEW_AND_REMEDIATION_PLAN.md` (the combined Change+Policy deliverable per the command spec).
**Date:** 2026-07-21 · verification = code (2 parallel deep-read agents) + DB (pyodbc). **Live API probing was blocked** by a backend auth outage (all accounts empty-body 401, lockout counters 0) — unrelated to the module; live re-run recipe below.

## Reproducible DB facts (SQL Server TSS_GRC, org 1)
- **Two policy backends:** `policy.documents` (51 rows; canonical, `/api/policy`) and `compliance.policies` (17 rows; legacy, `/api/v1/compliance/policies`, "write lifecycle retired in C-4"). Plus FE `/policy-templates` = localStorage only.
- **`policy` schema HAS 5 CHECK constraints** (documents.status ∈ DRAFT/REVIEW/APPROVED/PUBLISHED/EXPIRED/ARCHIVED; review_cadence ∈ AD_HOC/ANNUAL/BIENNIAL/TRIENNIAL; exceptions.status 6-state; exceptions.risk_level LOW/MEDIUM/HIGH/CRITICAL; attestations.status PENDING/ATTESTED/DECLINED/EXPIRED). **`compliance.policies` has 0 CHECK constraints.**
- **41 of 51 `policy.documents` have no `body_url`** → most policies are metadata shells (no document behind them).
- **`compliance.policy_framework_mappings` has NO `organisation_id` column** → coverage()/BoardPackService count it without an org filter (cross-tenant count leak, PM-M4).
- **RBAC:** `COMPLIANCE/WRITE` held by 13 governance roles (COMPLIANCE_OFFICER, LEGAL_REVIEWER, DPO, GRC_ADMINISTRATOR…), NOT `viewer` (viewer = COMPLIANCE READ+EXPORT). No dedicated `POLICY` permission module.
- **F8 automation dormant in dev:** only `AR-15` (POLICY_PUBLISHED) is wired; V141's AR-71..75 (policy) and V146's AR-96..100 (change) are absent from `platform.automation_rules` (dev carries a different 175-rule seed). Code DOES emit the events.

## Key code-verified findings (file:line in the combined plan §14–§24)
PM-C1 duplicate backends; PM-C2 create-status bypass (both stacks); PM-C3 façade SoD (`PolicyManagementService:354-360`, `createdBy` never populated); PM-C4 `/policies/:id` local-flip façade + mock (`PolicyDetailPage.tsx:61-65,46-55`); PM-H1 no editor / 41-of-51 empty; PM-H2 no version compare + string-max latest bug (`PolicyDocumentRepository:27-28`); PM-H3 attestation per-user (no campaign); PM-M1 attest endpoints on COMPLIANCE READ (`PolicyController:210,216,228`); PM-M2 compliance stack entirely unaudited; PM-M4 mapping tenant leak; PM-M5 `PolicyDocument.frameworkId` dead column.

## Live re-run recipe (once auth is healthy)
Log in ONCE (space out logins — the auth path was throttling), cache the token, reuse it. Probe as `compliance` (has COMPLIANCE/WRITE) + a read-only user:
- create policy with `status:"PUBLISHED"` → expect it to bypass (PM-C2);
- approve a policy where actor == owner → expect NO SoD block (PM-C3);
- `POST /api/policy/attestations` as a COMPLIANCE-READ-only user → expect 201 (PM-M1);
- `newVersion` past v9 → check latest-version resolution (PM-H2);
- tag test rows `[REVIEW-TEST]`, clean with CHARINDEX (children first).
