# 08 — RBAC Matrix
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Conventions

- **Authentication.** Stateless JWT (`@PreAuthorize("isAuthenticated()")`).
- **Authorisation.** Module + action level via
  `@PreAuthorize("hasPermission('MODULE','ACTION')")` resolved by
  `ModulePermissionEvaluator`.
- **Tenant scope.** Every read filters by
  `TenantContext.getCurrentTenant()`; cross-tenant reads return
  404 / 400.
- **Licensing.** Module access also gated by F9
  (`platform.organisation_licenses`) — even a permitted role can be
  denied if the tenant doesn't license the module.
- **Roles** live in `iam.roles`; permissions in `iam.permissions`;
  the join is `iam.role_permissions`. A user can hold N roles.

## Actions (columns)

`V` = View · `C` = Create · `E` = Edit · `A` = Approve · `D` =
Delete/Archive · `X` = Export · `S` = Sign · `R` = Report

## Role × Module matrix

(✅ = full, 🟡 = scoped to records they own or are responsible for,
— = no access)

| Role / Module | Risk | BCM | SCM | Cyber | PDPL | TPRM | Policy | NDI | Compl. | Audit-LC | Incident | Evidence | Reports | F1 Reg | F2 Ctl | F6 Posture | F7 Audit | F8 Auto | F9 Lic |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **System Administrator** | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | VCEAD X | V X | V X | V X | VCEAD |
| **GRC Administrator** | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCEAD R | VCE | VCE | V R | V R | VCE | — |
| **Risk Manager** | VCEAD R | V | V | V | V | V | V | V | V | V | V | V | VR | V | V | V | V | — | — |
| **Risk Owner** | 🟡 VCE | — | — | — | — | — | V | — | — | — | V | V | V | — | — | V | V | — | — |
| **Control Owner** | V | V | V | V | V | V | V | V | V | V | V | V | V | V | 🟡 VCE | V | V | — | — |
| **Evidence Owner** | V | V | V | V | V | V | V | V | V | V | V | 🟡 VCE | V | — | — | — | V | — | — |
| **BCM Manager** | V | VCEAD R | V | V | — | V | — | — | — | — | V | V | VR | V | V | V | V | — | — |
| **BIA Owner** | V | 🟡 VCE | V | — | — | — | — | — | — | — | — | V | — | — | — | — | V | — | — |
| **Service Owner** | V | 🟡 V | V | V | — | V | V | — | — | — | V | V | — | — | — | — | V | — | — |
| **DR Owner** | V | 🟡 VCE | V | V | — | V | — | — | — | — | V | V | — | — | — | — | V | — | — |
| **Crisis Manager** | V | VCE | V | V | V | — | — | — | — | — | VCEA | V | VR | — | — | V | V | — | — |
| **Supplier Manager** | V | V | VCEAD | V | — | VCEAD | — | — | — | — | V | V | VR | — | — | V | V | — | — |
| **Procurement Owner** | V | V | 🟡 VCE | — | — | V | — | — | — | — | — | V | — | — | — | — | V | — | — |
| **Cybersecurity Manager** | V | V | V | VCEAD R | V | V | V | V | V | V | VCEA | V | VR | V | V | V | V | — | — |
| **NCA Compliance Owner** | V | V | V | 🟡 VCEA | V | V | V | — | V | V | V | V | VR | V | 🟡 VCE | V | V | — | — |
| **Asset Owner** | — | V | — | 🟡 VCE | — | — | — | — | — | — | V | V | — | — | — | — | V | — | — |
| **Vulnerability Manager** | — | — | — | 🟡 VCEA | — | — | — | — | — | — | VA | V | V | — | — | — | V | — | — |
| **Data Protection Officer** | V | V | V | V | VCEAD R | V | V | — | V | V | V | V | VR | V | V | V | V | — | — |
| **Privacy Manager** | V | V | V | V | 🟡 VCEA | V | V | — | V | V | V | V | VR | V | V | V | V | — | — |
| **Data Owner** | — | — | — | — | 🟡 VCE | — | — | — | — | — | — | V | — | — | — | — | V | — | — |
| **Legal Reviewer** | V | V | V | V | V | V | VCEA | V | V | V | V | V | VR | V | — | V | V | — | — |
| **Compliance Officer** | V | V | V | V | V | V | V | V | VCEAD R | V | V | V | VR | V | V | V | V | V | — |
| **Internal Auditor** | V X | V X | V X | V X | V X | V X | V X | V X | V X | VCEAD R | V X | V X | VR X | V | V | V | V X | V | — |
| **Executive Reviewer** | V R | V R | V R | V R | V R | V R | V R | V R | V R | V R | V R | V | VR | V | — | V | V | — | — |
| **Board Secretary** | V S | V S | V S | V S | V S | V S | V S | V S | V S | V S | V S | V | VR S | — | — | V | V | — | — |
| **External Auditor** | V | V | V | V | V | V | V | V | V | V | V | V | V | V | V | V | V | — | — |
| **Supplier User** | — | — | 🟡 V | — | — | 🟡 V | — | — | — | — | 🟡 V | 🟡 V | — | — | — | — | 🟡 V | — | — |
| **Regulator Viewer** | V | V | V | V | V | V | V | V | V | V | V | V | V | V | V | V | V | — | — |

## Notes

- **Approver vs. signer.** "Approve" (A) is a workflow transition;
  "Sign" (S) is the digital-signature step (e.g. board reports).
  Many transitions are A but only some require S.
- **Scope qualifier 🟡.** For roles marked 🟡, access is further
  filtered to records the user owns or is assigned to. Row-level
  security is enforced in the service layer (every read includes
  an owner / assignee filter).
- **Approver matrix.** See `08_Approval_Matrix.md` for the per-
  workflow approver mapping (who can move a record from `REVIEW`
  to `APPROVED`).

## Segregation of duties

The following pairs trigger a SoD warning if assigned to the same
user (enforced at role-assignment time in `module/iam`):

- Risk Owner + Risk Acceptance Approver (Risk Committee)
- Control Owner + Internal Auditor for the same control
- Evidence Owner + Evidence Approver for the same item
- Incident Manager + Incident Closer (separate review)

## Field-level redactions

`confidentiality = SECRET` or `TOP_SECRET` on evidence and policy
docs restricts the field-level body (file URL, decrypted content)
to a "Cleared" sub-role. Metadata still appears.

## Test the matrix

The matrix is enforced by `@PreAuthorize` + service-layer scope
filters. Per-role smoke tests live in
`tss-grc-backend/src/test/java/.../iam/...` (integration-style).

---

**See also:** `08_Approval_Matrix.md`, `module/iam`,
`security/ModulePermissionEvaluator.java`.
