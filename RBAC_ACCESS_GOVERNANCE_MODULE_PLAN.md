# RBAC & Access-Governance Module — Design Plan (Saudi-first)

**Author stance:** GRC subject-matter expert · **Status:** PLAN (no code yet) · **Date:** 2026-07-09
**Scope:** per-module RBAC design for all 24 permission-bearing modules **plus a new Access-Governance
module** to manage roles, permissions, assignments, segregation of duties, recertification and
regulator evidence — Arabic-first, for the Saudi market (SAMA / NCA / PDPL-regulated entities).

---

## 1. Executive summary

The platform already *enforces* RBAC well (deny-by-default `hasPermission('MODULE','ACTION')`
via `ModulePermissionEvaluator`, a 66-permission × 24-module catalogue, 46 seeded persona roles
per tenant, an authorization ratchet test that blocks unguarded write endpoints). What it cannot
do today is *govern* that RBAC: the catalogue and grants are migration-seeded (V272) and there is
**no runtime management surface** — `RoleController` is read-only, role assignment is a single
string on the user form, and there is no approval workflow, no time-bounding, no segregation-of-
duties (SoD) control, no periodic access review, and no access-review evidence for a SAMA/NCA
inspection.

For the primary target market this is not a nice-to-have. **SAMA CSF 3.3.5, NCA ECC subdomain 2-2,
and PDPL security obligations all require demonstrable access governance** — least privilege,
maker-checker on sensitive changes, SoD, periodic recertification with evidence, and auditable
grant history. This plan designs that capability as a first-class module, reusing the platform's
existing workflow engine (maker-checker), audit ledger (immutable grant history), and export stack
(branded XLSX/PDF/ZIP evidence packs).

---

## 2. Regulatory drivers (why each capability exists)

| # | Requirement (Saudi anchor) | Capability this plan delivers |
|---|---|---|
| R1 | **NCA ECC — subdomain 2-2 (Identity & Access Management):** least privilege & need-to-know, SoD, periodic review of identities and access rights | Per-module least-privilege matrix (§5), SoD rules engine (§6.4), recertification campaigns (§6.5) |
| R2 | **SAMA Cyber Security Framework 3.3.5:** user access lifecycle (joiner–mover–leaver), privileged-access control, periodic access review | Time-bound assignments + JML hooks (§6.3), privileged-role flag + tighter review cadence (§6.5), break-glass with post-hoc review (§6.6) |
| R3 | **PDPL + Implementing Regulations (data security, Art. 19 sphere):** access to personal data restricted, reviewable, and evidenced; DPO oversight | PRIVACY-module grants surfaced in DPO review scope; access-review evidence pack (§6.7) |
| R4 | **SAMA / NCA onsite inspection practice:** regulator gets scoped, read-only, time-boxed access; entity produces access evidence on request | `REGULATOR_VIEWER` persona hard-locked read-only (SoD rule), time-boxed via validity window; one-click evidence ZIP (§6.7) |
| R5 | **Saudi banking operating practice: maker-checker (4-eyes)** on sensitive administrative changes | Access requests + approval before any grant becomes effective (§6.2); no self-approval (runtime SoD) |
| R6 | **ISO 27001:2022 A.5.15/5.16/5.18/8.2** (the platform's cross-mapped framework) | Same capabilities, mapped in the module's control-crosswalk metadata |
| R7 | **Bilingual/RTL obligation of the market** | All governance UI + role metadata AR/EN (roles already carry `display_name_ar`); Hijri + Gregorian stamps on certifications |

---

## 3. As-built inventory (verified against code + dev DB, 2026-07-09)

**Enforcement (strong):**
- `ModulePermissionEvaluator` — `hasPermission('MODULE','ACTION')` → authority `MODULE_ACTION`;
  `PLATFORM_ADMIN` bypass; deny-by-default.
- `ControllerAuthorizationRatchetTest` — baseline 62; any new unguarded write endpoint fails the build.
- Catalogue (dev DB): **66 permissions · 24 modules · 5 actions** (READ / WRITE / APPROVE / DELETE / TRIAGE).
- **46 roles per tenant** (bilingual `display_name_en/ar`, `is_system_role`, org-scoped), V272 persona grant matrix.
- Tables: `iam.users`, `iam.roles`, `iam.permissions`, `iam.role_permissions`, `iam.user_roles`.

**Governance (the gap):**
- `RoleController` = **GET only**. No role CRUD, no grant/revoke API, no matrix editor.
- `iam.user_roles` = `(user_id, role_id, assigned_at, assigned_by)` — **no validity window, no
  justification, no approval state, no source**.
- No access requests / maker-checker; no SoD rules; no recertification; no break-glass; no
  per-user effective-access view; no access analytics; no access evidence export.
- FE `AdminPage` has users/roles tabs, but the roles tab is a read-only catalogue.
- Useful adjacencies to reuse: workflow engine (14-module adoption), `platform.audit_log`
  (old/new-value JSON), export stack (`packet.xlsx/.docx`, RegulatorPackageService ZIP),
  `socops.iam_jml_event` (JML review windows), `LdapRoleMapper` (AD-integrated deployments).

**Known landmines to respect (from project history):**
- `@Valid` 400 fires **before** `@PreAuthorize` — write-guard probes need a valid body.
- Schedulers must guard `RequestContextHolder.getRequestAttributes()==null` before audit writes
  (off-request landmine) — the expiry sweep and campaign scheduler must use the safe path.
- Forward-only migrations; dev DB is Python/pyodbc-managed (Flyway off in dev). Next free: **V280**.

---

## 4. Design principles

1. **Deny-by-default stays.** The evaluator model is unchanged; governance manages *data*, not the enforcement path.
2. **Module × action is the permission unit.** No per-record ACLs in this phase (see §10 roadmap).
3. **Standard action set per module:** `READ · WRITE · APPROVE · DELETE` (+ `TRIAGE` where a queue
   exists: INCIDENT, INTAKE, CYBER/SOC). Proposed addition: **`EXPORT`** as a distinct action so
   tenants can let analysts read but not exfiltrate registers (the export audit interceptor already
   logs every download; EXPORT-gating is the preventive complement). Rollout is phased (§9 P3) —
   default grant = same as READ so nothing breaks.
4. **Every grant change is maker-checker'd and auditable** (who asked, why, who approved, when it
   expires) — `platform.audit_log` with before/after JSON is the single history ledger.
5. **Persona templates, tenant freedom.** The 46 Saudi personas ship as templates; tenants clone
   and tune. `is_system_role` rows are non-deletable.
6. **Arabic-first UI**, Hijri+Gregorian on attestations, regulator-facing evidence bilingual.

---

## 5. Per-module RBAC design (the matrix)

### 5.1 Persona clusters (46 roles → 8 clusters)

| Cluster | Roles (seeded) | Line of defence |
|---|---|---|
| **ADMIN** | GRC_ADMINISTRATOR (PLATFORM_ADMIN = evaluator bypass, not grantable) | — |
| **MODULE MANAGERS** (function owners) | CISO, CYBER_MANAGER, VULN_MANAGER, IR_LEAD, APPSEC_LEAD, FORENSICS_LEAD, OT_LEAD, PRIVACY_MANAGER, CRISIS_MANAGER, SUPPLIER_MANAGER, SUSTAINABILITY_OFFICER, NCA_COMPLIANCE, IAM_LEAD, MA_LEAD | 2nd |
| **RECORD OWNERS** | RISK_OWNER, CONTROL_OWNER, ASSET_OWNER, DATA_OWNER, EVIDENCE_OWNER, BIA_OWNER, DR_OWNER, SERVICE_OWNER, PROCUREMENT_OWNER, SUPPLIER_USER | 1st |
| **ANALYSTS** | SECURITY_ANALYST, DLP_REVIEWER, BRAND_MONITORING | 1st/2nd |
| **ASSURANCE** | AUDITOR, EXTERNAL_AUDITOR | 3rd |
| **EXECUTIVE / OVERSIGHT** | CFO, FINANCE_LEAD, HR_LEAD, LEGAL_COUNSEL, LEGAL_REVIEWER, EXECUTIVE_REVIEWER, BOARD_SECRETARY | oversight |
| **REGULATOR** | REGULATOR_VIEWER | external |
| **SERVICE (machine)** | SIEM_INGEST, TI_INGEST, SCANNER_INGEST, DLP_INGEST, SBOM_INGEST, JML_INGEST, APPSEC_INGEST, BRAND_INGEST | non-interactive |

### 5.2 Module × cluster grant matrix (R=READ W=WRITE A=APPROVE D=DELETE T=TRIAGE)

Default least-privilege targets; V272 is the baseline, this matrix is the governed target the
console must be able to express. Deviations become tenant decisions recorded via maker-checker.

| Module | ADMIN | MANAGERS (owning fn) | RECORD OWNERS | ANALYSTS | ASSURANCE | EXEC | REGULATOR | SERVICE |
|---|---|---|---|---|---|---|---|---|
| **RISK** | RWAD | R W A (CISO/mgr of domain) | R W (RISK_OWNER own scope) | R | R | R | R | — |
| **COMPLIANCE** | RWAD | R W A (NCA_COMPLIANCE) | R W (CONTROL_OWNER) | R | R | R | R | — |
| **CONTROL_LIB** | RWAD | R W (NCA_COMPLIANCE, CISO) | R (owners) | R | R | R | R | — |
| **REGULATORY** | RWAD | R W A (NCA_COMPLIANCE, LEGAL) | R | R | R | R | R | — |
| **CYBER** | RWAD | R W A (CISO, CYBER_MANAGER, VULN_MANAGER) | R W (ASSET_OWNER assets) | R W T (SECURITY_ANALYST) | R | R | R | W (SIEM/TI/SCANNER/SBOM single-purpose) |
| **INCIDENT** | RWAD T | R W A T (IR_LEAD, CISO) | R (impacted owners) | R W T | R | R | R (post-closure) | W (SIEM_INGEST create-only) |
| **INTAKE** | RWAD T | R W T (module managers) | R (own submissions) | R T | R | R | — | W (ingest connectors) |
| **BCM** | RWAD | R W A (CRISIS_MANAGER, BIA/DR leads) | R W (BIA_OWNER, DR_OWNER, SERVICE_OWNER) | R | R | R | R | — |
| **TPRM** | RWAD | R W A (SUPPLIER_MANAGER, PROCUREMENT_OWNER approve) | R W (SUPPLIER_USER own vendor) | R | R | R | R | — |
| **SCM** | RWAD | R W A (SUPPLIER_MANAGER) | R W (PROCUREMENT_OWNER) | R | R | R | R | W (SBOM_INGEST) |
| **PRIVACY** | RWAD | R W A (PRIVACY_MANAGER = DPO) | R W (DATA_OWNER own activities) | R | R | R (LEGAL R W) | R | — |
| **EVIDENCE** | RWAD | R W A (module managers approve) | R W (EVIDENCE_OWNER upload own) | R | R (**never W**) | R | R (indexed only) | — |
| **CAPA** | RWAD | R W A (owning manager approves closure) | R W (assigned owner) | R W | R | R | R | — |
| **AUDIT** | RWAD | R (auditee managers: own engagements) | R (own findings) | R | **R W A (AUDITOR)**, R (EXTERNAL_AUDITOR) | R | R | — |
| **AUDIT_LOG** | R | R (CISO, IAM_LEAD) | — | R (SECURITY_ANALYST) | R (AUDITOR) | — | R (scoped) | — |
| **NDI** | RWAD | R W A (NCA_COMPLIANCE, CISO) | R | R | R | R | R | — |
| **SUST** | RWAD | R W A (SUSTAINABILITY_OFFICER) | R W (DATA_OWNER metrics) | R | R | R (CFO R) | R | — |
| **REPORTING** | RWAD | R W (managers run/export own domain) | R | R | R | R | R | — |
| **WORKFLOW** | RWAD | R W A (managers approve in-domain) | R W (raise) | R W | R | R A (EXECUTIVE_REVIEWER) | — | — |
| **AUTOMATION** | RWAD | R W (CISO, IAM_LEAD) | — | R | R | — | — | — |
| **ADMIN** | RWAD | R (BOARD_SECRETARY committees W) | — | — | R | R | — | — |
| **PLATFORM** | RWAD | R | — | — | R | R | — | — |
| **IAM** *(extended by this plan)* | R W A D | R (IAM_LEAD **R W**, approvals by GRC_ADMINISTRATOR) | — | — | R | — | — | W (JML_INGEST events only) |
| **PLATFORM_ADMIN** | catalogued, granted to nobody (bypass documentation) | — | — | — | — | — | — | — |

**Matrix rules the console enforces:**
- ASSURANCE and REGULATOR clusters are **read-only by construction** — any W/A/D grant to them is a
  blocking SoD violation (override requires GRC_ADMINISTRATOR + documented justification).
- SERVICE roles get **exactly one module's WRITE** and are flagged non-interactive (no UI login path).
- APPROVE is never granted without READ; DELETE is never granted without WRITE.
- `PRIVACY` W/A grants are surfaced to the DPO (PRIVACY_MANAGER) in every review campaign (PDPL R3).

### 5.3 Privileged-role register

Flag as **privileged** (quarterly review, R2): GRC_ADMINISTRATOR, IAM_LEAD, CISO, anyone holding
IAM WRITE/APPROVE, AUTOMATION WRITE, PLATFORM WRITE, or DELETE on any register. Everything else:
semi-annual review.

---

## 6. The Access-Governance module (new)

Backend `module/iam/governance/` (controller/dto/entity/repository/service per house layout);
FE section **"Access Governance"** beside the Audit Log in the platform sidebar.

### 6.1 Role & matrix management (P0)
- Role CRUD (bilingual names/descriptions; `is_system_role` templates clonable, not deletable).
- **Permission-matrix editor**: module × action grid per role (checkboxes = `iam.role_permissions`);
  side-by-side compare of two roles; diff-vs-template view.
- **Effective-access view**: per user, the resolved module × action set + which role delivers each
  grant (answers the auditor's "who can approve risks?" in one screen, exportable — §6.7).
- All writes audited with before/after JSON; guarded `IAM WRITE` (mutations) / `IAM APPROVE`
  (activations) — requires cataloguing `IAM_WRITE`, `IAM_APPROVE`, `IAM_DELETE` (today IAM has READ only).

### 6.2 Access requests — maker-checker (P1)
- `iam.access_requests`: requester, beneficiary, role, validity window, business justification,
  status (PENDING → APPROVED/REJECTED → PROVISIONED/EXPIRED/REVOKED), approver, decided_at.
- Grant becomes effective **only after approval**; approver ≠ requester ≠ beneficiary (runtime SoD);
  auto-provision on approve, auto-audit both steps.
- Approval routes through the existing workflow engine (module key `IAM`) so SLAs/escalation/
  recipient lists are inherited; direct-grant path (GRC_ADMINISTRATOR) still requires a second
  approver unless the tenant disables 4-eyes (config, default ON for Saudi profile).

### 6.3 Assignment lifecycle & JML (P1)
- Extend `iam.user_roles`: `valid_from`, `valid_to`, `justification`, `approved_by`, `status`,
  `source` (ADMIN / REQUEST / JML / BREAK_GLASS), `is_deleted` — forward-only V280.
- **Expiry sweep** (scheduler, off-request-safe): auto-revoke past `valid_to`, notify owner + IAM_LEAD.
- **JML hooks**: consume `socops.iam_jml_event` — LEAVER → revoke all + evidence entry; MOVER →
  open a targeted mini-review of the user's grants.

### 6.4 Segregation of duties (P2)
- `iam.sod_rules`: pairs of conflicting grants (`module_a/action_a` × `module_b/action_b`) +
  severity + bilingual rationale. Starter rule-pack shipped as data:
  - SOD-01 RISK W × RISK A (proposer ≠ approver) · SOD-02 EVIDENCE W × EVIDENCE A
  - SOD-03 COMPLIANCE W × COMPLIANCE A (policy author ≠ approver) · SOD-04 CAPA W × CAPA A
  - SOD-05 TPRM W × TPRM A (vendor onboarding) · SOD-06 IAM W/A × AUDIT R W A (admin ≠ auditor)
  - SOD-07 any W/A/D held by ASSURANCE or REGULATOR cluster · SOD-08 SERVICE role + any second module
  - Runtime rules: no self-approval anywhere; no self-grant.
- **Preventive check** at grant time (block or require override w/ justification) + **detective
  scan** (nightly + on-demand) writing `iam.sod_violations` with disposition workflow
  (ACCEPTED_RISK w/ expiry / REMEDIATED).

### 6.5 Access recertification campaigns (P2)
- `iam.access_review_campaigns` (+`_items`): scope = privileged-only / module / all; reviewer =
  role owner or line manager; cadence presets **quarterly (privileged)** and **semi-annual (all)** —
  R1/R2 defaults; Hijri + Gregorian window stamps.
- Item decisions: CERTIFY / REVOKE / MODIFY (revocations auto-execute); non-response past SLA
  escalates via workflow engine; campaign closure blocked until 100 % dispositioned or exception-
  approved by GRC_ADMINISTRATOR.

### 6.6 Break-glass (P3)
- Pre-provisioned emergency role per tenant (e.g. `EMERGENCY_RESPONDER`, max validity 24 h),
  activation needs stated incident ref, fires CRITICAL audit event + notification to CISO+IAM_LEAD,
  auto-expires, and mandatorily opens a post-hoc review item.

### 6.7 Access evidence & analytics (P2/P3)
- **Evidence pack** (one click, reusing the export stack): branded ZIP —
  `README.pdf` (PdfTheme + BrandTheme) + `Role_Catalogue.xlsx` + `Permission_Matrix.xlsx` +
  `User_Assignments.xlsx` (incl. validity/justification) + `SoD_Violations.xlsx` +
  `Recertification_Evidence.xlsx` + `manifest.json` (SHA-256) — the artefact handed to a SAMA/NCA
  inspector. AR/EN via the existing `lang` mechanics; `ExportNaming` filename; auto-logged EXPORT.
- **Analytics dashboard**: privileged-holder count, dormant grants (no login ≥90 d), grants
  expiring ≤30 d, open SoD violations, campaign completion %, top over-privileged users.

### 6.8 UI map (FE, bilingual/RTL)
```
Access Governance
├─ Roles              (catalogue · clone-from-template · bilingual editor)
├─ Permission Matrix  (role × module × action grid · compare/diff)
├─ User Access        (per-user effective access · assignments w/ validity)
├─ Access Requests    (my requests · approvals queue — maker-checker)
├─ Review Campaigns   (create/run/attest · Hijri+Gregorian stamps)
├─ SoD                (rules · violations · dispositions)
├─ Break-glass        (activate · history)
└─ Analytics          (KPIs above · evidence-pack export button)
```

---

## 7. Data model & migrations (forward-only; dev via pyodbc, prod via Flyway)

| Mig | Content |
|---|---|
| **V280** | `iam.user_roles` + lifecycle columns (`valid_from/valid_to/justification/approved_by/status/source/is_deleted`); catalogue `IAM_WRITE/APPROVE/DELETE`; grant IAM R W to IAM_LEAD, A to GRC_ADMINISTRATOR (idempotent NOT EXISTS) |
| **V281** | `iam.access_requests` |
| **V282** | `iam.access_review_campaigns`, `iam.access_review_items` |
| **V283** | `iam.sod_rules`, `iam.sod_violations` + starter rule-pack seed |
| **V284** | `iam.break_glass_events`; privileged flag on `iam.roles` (`is_privileged`) |

All tables: BaseEntity conventions (`organisation_id`, `is_deleted`, audit columns, ROWVERSION),
org-scoped finders only. Grant history is **not** a new table — `platform.audit_log`
(actionType `ACCESS_GRANT`/`ACCESS_REVOKE`/`SOD_OVERRIDE`/`BREAK_GLASS`, old/new JSON) is the ledger.

## 8. API surface (new, all org-scoped; guards per endpoint)

```
/api/v1/iam/governance
  GET    /roles · POST /roles · PUT /roles/{id} · DELETE /roles/{id}         IAM WRITE (DELETE: IAM DELETE)
  GET    /roles/{id}/matrix · PUT /roles/{id}/matrix                          IAM READ / IAM WRITE
  GET    /users/{id}/effective-access                                         IAM READ
  POST   /assignments · DELETE /assignments/{id}                              IAM WRITE (auto-request if 4-eyes on)
  POST   /requests · GET /requests · POST /requests/{id}/approve|reject       auth’d / IAM APPROVE
  GET|POST /campaigns · POST /campaigns/{id}/items/{itemId}/decide            IAM READ|WRITE / reviewer-scoped
  GET|POST /sod/rules · GET /sod/violations · POST /sod/violations/{id}/disposition   IAM READ|WRITE|APPROVE
  POST   /break-glass · GET /break-glass                                      dedicated BREAK_GLASS permission
  GET    /evidence-package.zip                                                IAM READ + AUDIT_LOG READ
  GET    /analytics                                                           IAM READ
```
Ratchet compliance: every write endpoint above carries a real `hasPermission` guard (baseline 62
must not rise). Write-probe tests must send `@Valid`-satisfying bodies (landmine).

## 9. Phased delivery & verification gates

| Phase | Scope | Verify gate |
|---|---|---|
| **P0 Foundation** | V280 · role CRUD + matrix API · matrix/roles/effective-access UI · grant audit | `mvn test` (ratchet ≤62) · live probes: negative (RISK_OWNER blocked from IAM WRITE) + positive · FE tsc/build · audit rows w/ before/after JSON |
| **P1 Lifecycle** | requests + maker-checker · time-bound assignments · expiry sweep · JML hooks | live: request→approve→provision→expiry auto-revoke; self-approval blocked; scheduler off-request-safe |
| **P2 Assurance** | SoD rules+scan+preventive check · recertification campaigns · evidence pack | seeded SoD conflict detected + blocked; campaign end-to-end incl. auto-revoke; ZIP validated (manifest SHA-256, AR/EN) |
| **P3 Advanced** | break-glass · analytics dashboard · EXPORT action rollout | break-glass expiry + mandatory review; EXPORT gating proven on one register with default-parity grants |

Per-slice: commit on `feat/rbac-governance-*`; **no merge to master without explicit "merge"**;
tag test rows `[REVIEW-TEST]`, clean with CHARINDEX; destructive DB ops require explicit user OK.

## 10. Explicit non-goals / roadmap
- **Record-level ACLs / ABAC** (own-records-only enforcement is today service-layer logic) — later.
- **External IdP federation** (Azure AD / Nafath SSO) — `LdapRoleMapper` remains the AD hook; full
  SSO is a separate initiative.
- **PLATFORM_ADMIN bypass** stays (documented, catalogued, granted to nobody) — but every bypass
  use is already audit-visible; analytics will surface its usage count.

## 11. Open decisions for the owner
1. Adopt **EXPORT** as a 6th standard action (recommended; default grants mirror READ so behaviour
   is unchanged until a tenant tightens it)?
2. 4-eyes default **ON** for all tenants, or ON only for the Saudi-regulated profile?
3. Review cadences: keep quarterly-privileged / semi-annual-all defaults, or per-tenant?
4. Break-glass max validity (proposed 24 h) and who may activate (IR_LEAD + CISO)?
