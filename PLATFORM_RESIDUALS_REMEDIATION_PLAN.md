# Platform Residuals — Implementation Tracker

Scope: the three residual items recommended after the workflow-engine loop closed
(2026-07-06), authorized by the user's "implement all the recommended from this point".
Branch `feat/rbac-catalogue` (off master @ `fbb23244`). Same loop contract as prior
trackers: no merge without an explicit "merge"; test rows tagged `[REVIEW-TEST]`,
cleaned with `CHARINDEX`.

| # | Item | Status | Receipts |
|---|---|---|---|
| R-1 | **Apply V261** — CY-B one-time backfill of pre-existing `/cyber` workspace incidents into the canonical `cyber.incidents` register | ✅ DONE (dev) | INSERT-only + fill-NULL-link UPDATE verified before running. 24 unlinked workspace incidents → 24 register rows (20→44), all reverse links filled, 0 unlinked left; status/NIST mapping spot-checked; **idempotency re-run 0/0**. Register API `GET /api/v1/cyber/incidents` → 200, `totalElements=24` (org 1). No repo change (file was already committed in the cyber loop). |
| R-2 | **RBAC catalogue completion (V272)** — every `hasPermission()` pair catalogued + persona grant matrix | ✅ DONE | Inventory: 24 modules referenced in code; **32 used pairs missing** from `iam.permissions` (INCIDENT, CAPA, TPRM, REGULATORY, CONTROL_LIB, AUDIT, AUDIT_LOG, INTAKE, PLATFORM, AUTOMATION, IAM, WORKFLOW approve/delete, …) → those endpoints were admin-only; **RISK_READ/WRITE/DELETE + PRIVACY_READ/WRITE granted to NOBODY**; CYBER_WRITE/DELETE admin-only. V272: +32 catalogue rows (34→66) + **+292 persona grants (353→645)** across the 55 seeded roles (idempotent re-run 0/0). Live behavioral proof (fresh logins): rmanager list risks 200 + **valid-body create 201 → delete 204** (probe cleaned, 0 residue); viewer read 200 but **valid-body create 403** (least-privilege); auditor read-sweep 200 across RISK/PRIVACY/TPRM/AUDIT_LOG/regulatory; so_officer (CISO) passes CYBER write gate, denied PRIVACY (403); viewer denied INTAKE/AUDIT_LOG/cyber-write (403). ⚠ Probe-design lesson: `@Valid` 400 fires BEFORE `@PreAuthorize` — a 400 on `POST {}` does NOT prove the authz gate; write-gate proofs must use a valid body. |
| R-3 | **MSSQLSERVER startup → Manual** — stop the reboot port-1433 steal from SQLEXPRESS | ✅ DONE | `Set-Service MSSQLSERVER -StartupType Manual` (was Automatic); verified StartType=Manual, service Stopped, `MSSQL$SQLEXPRESS` Running on 1433. Reversible with `Set-Service MSSQLSERVER -StartupType Automatic`. |
| R-2b | **Intake schema bootstrap (dev)** — discovered en route: `GET /api/v1/intake` returned the friendly 503 "module not bootstrapped" for EVERYONE incl. admin because the restored dev DB never had V11 | ✅ DONE (dev) | Applied `V11__intake_records.sql` (pure DDL: `intake.intake_records` + `intake_events` + indexes). Post-bootstrap: compliance triager inbox → 200, viewer → 403. No repo change (V11 shipped long ago). |

**Migration state after this loop:** V269–V272 applied to dev; **V261 now applied**; V11 (intake) bootstrapped into dev. Next free migration = **V273**.

**Explicitly out of scope (not recommendations, per the closing report of the workflow loop):**
generic conditional-routing DSL and EXTERNAL-recipient delivery portal (deferred by design in
`WORKFLOW_LOGIC_AND_LIFECYCLE_REMEDIATION_PLAN.md`), MFA (TOTP) and temporal tables (V1
wishlist, never scheduled).
