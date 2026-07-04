# P2 / W1 — Governance wave (controls · assess→gap→CAPA · policy · evidence)
### Design-first, then violent live testing. Seed findings S1 + S2 tested first.

Backend :8084, admin JWT. Test rows tagged `[REVIEW-TEST]`, soft-deleted after (see data-safety note).
Receipts file: `scratchpad/rev/w1_receipts.txt`. Grades: REAL / PARTIAL / FAÇADE / MISSING.

---

## A4 — Evidence lifecycle (SEED S1 + S2) → **FAÇADE (confirmed, front-to-back)**

**Designed (market bar):** request → clock (due + reminders + escalation) → fulfil **with file upload**
→ review (accept/reject) → link to control → retain/expire. See P0 §A4.

**What the app does (live receipts):**

| Probe | Result | Meaning |
|---|---|---|
| `POST /cyber/evidence` (JSON, no file) | **200**, row created, `fileName=null` | You create "evidence" that contains no evidence. |
| `POST /cyber/evidence/{id}/file` / `/upload` / `/attachment` | **404 / 404 / 404** | No multipart endpoint on cyber evidence. |
| `POST /cyber/evidence/upload` | **405** | Same. |
| `GET /attachments/CYBER_EVIDENCE/{id}` | **200 `[]`** | The **generic** attachment API accepts the type but cyber never writes to or surfaces it. |
| `POST /cyber/evidence/request`, `/{id}/request`, `GET /evidence/requests` (on cyber) | **405 / 404 / 405** | Cyber has **no** request workflow. |
| `GET /api/v1/evidence/requests` (platform module) | **200, n=15** | A real cross-dept request+comments workflow **exists** — unwired from cyber. |
| `CyberPage.tsx` scan for `type="file"` / FormData / `/attachments` | **0 matches** | The cyber UI has no upload control at all. |
| Platform FE components | `AttachmentsField.tsx`, `FileUploadField.tsx` exist; used by `EvidencePage`, ESG | Reusable upload UI exists — cyber doesn't use it. |

**Data model:** `CyberEvidence` has `file_name` (String), `no titleAr`, `expiry_date` (never swept), and
no link to `attachment`/`evidence_request`/`evidence_review`. It is a standalone CRUD island.

**Verdict: FAÇADE.** Both seed findings are real:
- **S1 confirmed** — no file upload on the cyber evidence (and policy) create forms, at UI, API and schema.
- **S2 confirmed** — "request evidence" opens no workflow in cyber (no assignee/due/countdown/reminder/
  escalation/fulfil→review). The capability fully exists in `module/evidence` + `module/attachment` but
  the cyber module reimplemented a gutted parallel version instead of wiring to it. **This is an
  integration/island defect, not a greenfield build** — remediation = wire cyber to the existing subsystem.

---

## A2 — Assessment → gap → CAPA → **PARTIAL (wiring exists, produces an inert CAPA)**

**Designed:** failed control auto-raises a gap → CAPA with owner + due date + **clock** (reminder,
overdue escalation) → route to owner dept → verify → close.

**Live receipts:**
- `POST /cyber/assessments` (result=NON_COMPLIANT, gapIdentified=true) → **200**.
- `POST /cyber/assessments/{id}/convert-gap` (empty body) → **200**, creates action-plan
  *"Gap Remediation - Assessment 22"*, `sourceType=ASSESSMENT`.
- **But the created CAPA has `dueDate=null`, `ownerName=null`, `workflowStatus=OPEN`** — an unassigned,
  unclocked placeholder. The entity *can* hold due/owner (seed rows do), the convert path just doesn't set them.
- **Clock:** the only `@Scheduled` job in `module/cyber` is `VulnerabilitySlaScheduler`. There is **no
  reminder/escalation scheduler for `cyber_action_plans`**; the dashboard only *counts* `overdueActionPlans`.

**Verdict: PARTIAL.** The gap→CAPA link is real, but the CAPA is born without owner/due and nothing drives
it to closure. Fails OS-2 (clock) and OS-1 (assignment). Market bar (ServiceNow/Archer) auto-assigns +
clocks the CAPA.

---

## A1 — Framework control library → **PARTIAL (strong data, thin process)**
- `/frameworks` (6), `/controls` (154 live), `/sub-controls` (529 live) — rich, real, bilingual library.
- No SoA / per-control applicability endpoint; per-control **owner** + review cadence not exposed.
- **Verdict: PARTIAL** — excellent reference data; the *operating* layer (owner, cadence, SoA) is thin.

## A3 — Policy lifecycle (in cyber) → **MISSING (in cyber)**
- No `/cyber/policies*` endpoint; policy management is the compliance module. The cyber workspace has no
  policy lifecycle (draft→review→approve→publish→attest). If the UI shows a "policy" create with no upload,
  that is the same S1 façade. **Verdict: MISSING in cyber** (exists in compliance; cross-module continuity to assess in W6).

---

## Cross-cutting findings surfaced this wave

| ID | Finding | Severity | Receipt |
|---|---|---|---|
| E4-a | **Read gated on WRITE**: `GET /cyber/controls`, `/assessments`, `/incidents` require `CYBER:WRITE`; only the `/all` variants require READ. A read-only user cannot page the primary lists. | High | catalogue.md authz column |
| E4-b | **AUDITOR has zero cyber access**: `auditor` → `GET /cyber/controls` **403** *and* `/cyber/controls/all` **403**. An auditor cannot even read cyber controls. (socops list also 403 for auditor — that gate is fine.) | High | w1_receipts E4 |
| VAL-1 | **No input validation**: `POST /cyber/evidence {}` → **500** ("unexpected database error") instead of 400. | Medium | w1_receipts VAL |
| VAL-2 | **No referential/enum integrity**: `POST /cyber/evidence` with `controlId=999999` (nonexistent) + `status="NONSENSE"` → **200** persisted. Evidence can point to phantom controls with garbage status. | Medium | w1_receipts VAL |
| E3-1 | `CyberEvidence` has `titleEn` but **no `titleAr`** — breaks OS-8 bilingual rule for an island entity. | Medium | entity read |
| ARCH-1 | **Duplicate incident tables**: `cyber.cyber_incidents` (24) *and* `cyber.incidents` (20); duplicate asset concepts `cyber_assets` (61) vs `attack_surface_asset` (20). Possible island/duplication. | Medium (confirm W2) | DB counts |

**IDOR/tenant:** `GET /cyber/incidents/999999` → **404** (org-scoped via `findByIdAndOrganisationId`). OK (limited — dev has one org; deeper cross-tenant probe deferred to W6).

**Hypothesis refuted (honest):** P1 guessed socops list endpoints were unauthenticated. Live test:
`auditor GET /socops/events` → **403**. socops lists ARE gated (class-level/role). Refuted.

---

## Data-safety note (my own tooling error, fully repaired)
Test-row cleanup initially used `LIKE '[REVIEW-TEST]%'`. In SQL Server `[...]` is a **character class**,
so under CI collation it matched most seed rows and soft-deleted seed `cyber_action_plans` (all 13),
`cyber_evidence` (id1,id4), `control_assessments` (id3). Caught immediately, restored via `is_deleted=0`
using literal `CHARINDEX`, and **verified live counts back to originals 12 / 21 / 13**. Only genuine
`[REVIEW-TEST]` rows remain soft-deleted. Lesson saved to memory. No seed data lost.

## W1 tallies
- **FAÇADE: 1** (A4 evidence) · **MISSING: 1** (A3 policy in cyber) · **PARTIAL: 3** (A1, A2, and the
  library/process split) · plus **6 discrete findings** (2 High RBAC, 4 Medium validation/i18n/arch).
- Highest-value confirmation: **both user seed findings S1 + S2 are real**, and the fix is *integration*
  (wire to the existing evidence/attachment subsystem), not new construction.
