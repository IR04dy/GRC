# 07 — Control Library Import Template

Excel template for bulk-importing **controls** into the unified
library (`platform.controls`) and their citation mappings into
`platform.control_mappings`. Two sheets per workbook.

## Sheet 1 — Controls

| # | Column | Type | Required | Notes |
|---|---|---|---|---|
| 1 | `control_code` | string(60) | ✅ | unique per tenant |
| 2 | `version` | string(20) | optional | defaults `1.0` |
| 3 | `domain` | enum | ✅ | RISK / CYBER / BCM / PRIVACY / TPRM / COMPLIANCE / GENERAL |
| 4 | `objective_code` | string(60) | optional | must exist in `control_objectives` |
| 5 | `title_en` | string(255) | ✅ | |
| 6 | `title_ar` | string(255) | optional | recommended |
| 7 | `description_en` | text | optional | |
| 8 | `description_ar` | text | optional | |
| 9 | `control_type` | enum | optional (default PREVENTIVE) | PREVENTIVE / DETECTIVE / CORRECTIVE / DETERRENT / RECOVERY |
| 10 | `automation_level` | enum | optional (default MANUAL) | MANUAL / SEMI / AUTOMATED |
| 11 | `frequency` | enum | optional | CONTINUOUS / DAILY / WEEKLY / MONTHLY / QUARTERLY / ANNUAL / ON_EVENT |
| 12 | `owner_user_username` | string(120) | optional | must exist in `iam.users` |
| 13 | `status` | enum | optional (default ACTIVE) | DRAFT / ACTIVE / RETIRED |
| 14 | `notes` | text | optional | free |

## Sheet 2 — Control mappings

| # | Column | Type | Required | Notes |
|---|---|---|---|---|
| 1 | `control_code` | string(60) | ✅ | resolves to control in sheet 1 |
| 2 | `authority_document_code` | string(60) | ✅ | e.g. `NCA-ECC-V2` |
| 3 | `citation_code` | string(80) | ✅ | e.g. `2-1-3` |
| 4 | `coverage` | enum | ✅ | FULL / PARTIAL / COMPENSATING |
| 5 | `rationale_en` | text | ✅ | non-blank; discourages drive-by mapping |
| 6 | `rationale_ar` | text | optional | |
| 7 | `reviewer_user_username` | string(120) | optional | who attested the mapping |

## Sample (Sheet 1 — Controls)

```
control_code  version  domain   title_en                                      control_type  frequency
C-IAM-014     1.0      CYBER    Privileged access reviewed quarterly          DETECTIVE     QUARTERLY
C-BCM-003     1.0      BCM      Critical service BIA refreshed annually       PREVENTIVE    ANNUAL
C-PRIV-002    1.0      PRIVACY  PIA required for high-risk processing         PREVENTIVE    ON_EVENT
```

## Sample (Sheet 2 — Mappings)

```
control_code  authority_document_code  citation_code  coverage  rationale_en
C-IAM-014     NCA-ECC-V2               2-2            FULL      Quarterly review satisfies ECC 2-2 IAM
C-IAM-014     ISO-27001                A.9.2.5        FULL      Equivalent control under ISO 27001 Annex A
C-BCM-003     ISO-22301                8.2            FULL      BIA clause coverage
C-BCM-003     SAMA-BCM-FW              3.1            PARTIAL   Covers strategic BIA only; SAMA expects ops-level too
C-PRIV-002    PDPL-2023                Art-25         FULL      DPIA requirement
C-PRIV-002    ISO-27701                7.2            FULL      Privacy impact assessment clause
```

## Import endpoint

```
POST /api/v1/controls/import
Content-Type: multipart/form-data
Body: file=<workbook.xlsx>
```

Response:

```json
{
  "controls":  { "inserted": 47, "updated": 3, "failed": 0, "errors": [] },
  "mappings":  { "inserted": 112, "updated": 0, "failed": 2,
                 "errors": ["row 14: citation NCA-ECC-V2 / 9-9-9 not found",
                            "row 38: rationale_en required"] }
}
```

## Behaviour

- All rows in **one transaction** — partial imports do not commit.
  The endpoint either applies the whole workbook or rejects it.
- Existing controls (matched by `control_code`+`version`) are
  **updated**; new rows are inserted.
- Mappings are inserted or updated by `(control_id, citation_id)`
  uniqueness; existing mappings keep their `last_reviewed_at`
  unless a non-blank `rationale_en` is provided.
- The audit log (F7) records one `CUSTOM` row per imported control
  + one per mapping with `sourceModule = 'controls.import'`.

## Excel validation

Workbook ships with:
- Data-validation dropdowns on every enum column.
- A pivot of citation codes by authority document in a hidden
  sheet (for VLOOKUP into Sheet 2 column C).
- A "preflight" sheet that lists rows missing required fields, so
  QA can clean up before upload.

---

**See also:** `07_Regulatory_Mapping_Matrix.md`,
`module/platform/controls`, V92/V93/V94 migrations.
