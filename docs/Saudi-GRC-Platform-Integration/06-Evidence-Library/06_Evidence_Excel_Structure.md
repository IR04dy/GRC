# 06 — Evidence Excel Structure
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Bulk-import template for `evidence.evidence_items`. The platform
exposes a POST `/api/v1/evidence/import` endpoint that accepts the
sheet below.

## Workbook structure

One workbook, one sheet, header row 1, data starts row 2.

| # | Column | Type | Required | Dropdown values | Validation |
|---|---|---|---|---|---|
| 1 | `code` | string(40) | ✅ | — | unique per tenant |
| 2 | `title_en` | string(255) | ✅ | — | non-blank |
| 3 | `title_ar` | string(255) | optional | — | recommended for Saudi-native UI |
| 4 | `evidence_type` | enum | ✅ | POLICY / BCP / DRP / ASSESSMENT / ATTESTATION / LOG / SCREENSHOT / REPORT / OTHER | — |
| 5 | `confidentiality` | enum | ✅ | PUBLIC / INTERNAL / CONFIDENTIAL / SECRET / TOP_SECRET | — |
| 6 | `framework_requirement` | string(120) | optional | — | NCA citation code, ISO clause, PDPL article |
| 7 | `owner_user_username` | string(120) | ✅ | — | must match `iam.users.username` |
| 8 | `version` | string(20) | optional | — | semver-style (1.0, 1.1) |
| 9 | `effective_date` | date (ISO) | optional | — | YYYY-MM-DD |
| 10 | `review_date` | date | optional | — | next review |
| 11 | `expiry_date` | date | optional | — | |
| 12 | `status` | enum | optional (defaults DRAFT) | DRAFT / PENDING / ACTIVE / SUPERSEDED / EXPIRED / ARCHIVED | — |
| 13 | `storage_uri` | string(500) | optional | — | s3://, https:// |
| 14 | `mime_type` | string(60) | optional | — | application/pdf, … |
| 15 | `digital_signature_required` | bool | optional | TRUE / FALSE | flips status to PENDING until signed |
| 16 | `retention_until` | date | optional | — | |
| 17 | `notes` | string | optional | — | free text |

## Example row

```
code             title_en                          evidence_type confidentiality owner_user_username version effective_date
EV-BCP-CORE-001  Core Banking BCP                  BCP           CONFIDENTIAL    bcm_manager        1.0     2026-01-01
```

## Server-side processing

1. Validate header row matches the schema.
2. For each row: validate enums, FKs (owner exists, citation code
   resolves if provided).
3. Insert row into `evidence.evidence_items` (transaction scoped to
   tenant from JWT).
4. Insert `evidence.chain_of_custody` event = `IMPORTED`.
5. If `digital_signature_required = TRUE`, status stays PENDING +
   `signature.digital_signature_transactions` row is created.
6. Return `{ inserted: N, updated: 0, failed: M, errors: [...] }`.

## Bulk update / supersede

To replace an existing item:
- Set `code` to the same code, bump `version`.
- Set `status = SUPERSEDED` on the old row OR let the import set
  `parent_evidence_id` automatically based on code+version match.

## Excel data validation

The template ships with dropdown validations for `evidence_type`,
`confidentiality`, `status` cells via Excel data validation lists.
Date columns use ISO format with a worksheet-level data type guard.

---

**See also:** `06_Evidence_Library.md`, `module/evidence`.
