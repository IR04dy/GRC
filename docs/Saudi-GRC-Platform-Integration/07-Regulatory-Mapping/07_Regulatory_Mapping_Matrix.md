# 07 — Regulatory Mapping Matrix

The matrix is **live data**, not hand-maintained tables. It lives
in `platform.authority_documents` + `platform.authority_document_citations`
+ `platform.control_mappings` (Framework Intersection F1+F2).

To export the live matrix:

```
GET /api/v1/regulatory/authority-documents
GET /api/v1/regulatory/authority-documents/:id/citations
GET /api/v1/regulatory/authority-documents/:id/coverage
GET /api/v1/regulatory/posture
```

This document captures the **catalog** of Authority Documents the
platform tracks, with placeholder marker per the implementation
prompt's no-overclaim rule.

## Catalog

| Code | Family | Issuer | Version | Title (EN) | Title (AR) | Status | Placeholder |
|---|---|---|---|---|---|---|---|
| `NCA-ECC-V2` | NCA Cyber | NCA | 2.0 | Essential Cybersecurity Controls v2 | الضوابط الأساسية للأمن السيبراني | ACTIVE | 🟦 |
| `NCA-CSCC-V1` | NCA Cyber | NCA | 1.0 | Critical Systems Cybersecurity Controls | ضوابط الأمن السيبراني للأنظمة الحساسة | ACTIVE | 🟦 |
| `NCA-CCC-V1` | NCA Cyber | NCA | 1.0 | Cloud Cybersecurity Controls | ضوابط الأمن السيبراني للحوسبة السحابية | ACTIVE | 🟦 |
| `NCA-OTCC-V1` | NCA Cyber | NCA | 1.0 | Operational Technology Cybersecurity Controls | ضوابط الأمن السيبراني للتقنيات التشغيلية | ACTIVE | 🟦 |
| `NCA-DCC-V1` | NCA Cyber | NCA | 1.0 | Data Cybersecurity Controls | ضوابط أمن البيانات | ACTIVE | 🟦 |
| `NCA-TCC-V1` | NCA Cyber | NCA | 1.0 | Telecom Cybersecurity Controls | ضوابط الأمن السيبراني للاتصالات | ACTIVE | 🟦 |
| `PDPL-2023` | Privacy | SDAIA | 2023 | Personal Data Protection Law | نظام حماية البيانات الشخصية | ACTIVE | 🟦 |
| `SAMA-BCM-FW` | SAMA | SAMA | 1.0 | SAMA Business Continuity Management Framework | إطار إدارة استمرارية الأعمال | ACTIVE | 🟦 |
| `SAMA-CYBER-FW` | SAMA | SAMA | 1.0 | SAMA Cyber Security Framework | إطار الأمن السيبراني | ACTIVE | 🟦 |
| `SAMA-OUTSOURCING` | SAMA | SAMA | 1.0 | SAMA Outsourcing Regulation | لائحة التعهيد | ACTIVE | 🟦 |
| `SAMA-OP-RISK` | SAMA | SAMA | 1.0 | SAMA Operational Risk Framework | إطار المخاطر التشغيلية | ACTIVE | 🟦 |
| `DGA-DIGITAL-MATURITY` | Digital-Gov | DGA | 1.0 | DGA Digital Maturity Model | نموذج النضج الرقمي | ACTIVE | 🟦 |
| `DGA-INTEROPERABILITY` | Digital-Gov | DGA | 1.0 | DGA Interoperability Framework | إطار التشغيل البيني | ACTIVE | 🟦 |
| `NRC-NATIONAL-RISKS` | Resilience | NRC | 1.0 | National Risk Catalog | الكتالوج الوطني للمخاطر | ACTIVE | 🟦 |
| `ISO-22301` | BCM | ISO | 2019 | ISO 22301:2019 BCMS | الأيزو 22301 | ACTIVE | 🟦 |
| `ISO-27001` | Cyber | ISO | 2022 | ISO/IEC 27001:2022 ISMS | الأيزو 27001 | ACTIVE | 🟦 |
| `ISO-27701` | Privacy | ISO | 2019 | ISO/IEC 27701:2019 PIMS | الأيزو 27701 | ACTIVE | 🟦 |
| `ISO-31000` | Risk | ISO | 2018 | ISO 31000:2018 Risk Management | الأيزو 31000 | ACTIVE | 🟦 |
| `SOC2-2017` | Cyber | AICPA | 2017 | SOC 2 Trust Services Criteria | معايير الخدمات الموثوقة | ACTIVE | 🟦 |

🟦 = `is_placeholder = 1` in `platform.authority_documents`. The
banner in `/regulatory/library` and `/compliance/posture` warns the
user until QA imports the official text + flips the flag.

## Sample citations (seeded for NCA-ECC-V2)

| Citation | Title (EN) | Title (AR) |
|---|---|---|
| 1 | Cybersecurity Governance | حوكمة الأمن السيبراني |
| 1-1 | Cybersecurity Strategy | استراتيجية الأمن السيبراني |
| 1-2 | Cybersecurity Policies and Procedures | سياسات وإجراءات الأمن السيبراني |
| 2 | Cybersecurity Defense | الدفاع السيبراني |
| 2-1 | Asset Management | إدارة الأصول |
| 2-2 | Identity and Access Management | إدارة الهويات والصلاحيات |
| 3 | Cybersecurity Resilience | صمود الأمن السيبراني |
| 4 | Third-Party and Cloud Cybersecurity | الأمن السيبراني للأطراف الخارجية |
| 5 | Industrial Control Systems Cybersecurity | الأمن السيبراني للأنظمة الصناعية |

Real catalogs load via the V92 + future official-text import
migrations. Until then, citation codes follow the regulator's
numbering convention, titles are placeholder-flagged.

## Placeholder ID convention (per prompt §11)

Where official IDs are not yet provided, citation codes follow the
regulator's own numbering. We do **not** invent IDs. Placeholders
are flagged at the document level via `is_placeholder = 1` so any
report reads explicitly "placeholder — pending official import."

## Control coverage (live)

Per F1+F2, the platform reports per-document coverage at
`/api/v1/regulatory/authority-documents/:id/coverage` and
`/compliance/posture`. As of the F4 migration, all cyber +
compliance controls are part of `platform.controls` and mapping
breadth grows as QA adds rows.

## Gap process

Each citation with zero mapped controls appears in the gap list on
the F5 alignment tab + the F6 posture drill-down. From there a user
can:

1. Open the citation detail panel
2. Click "Map an existing control" or "Create new control"
3. The control library at `/controls/library` lets them add the
   mapping with a mandatory rationale (per GRC-expert review R6 —
   no drive-by mapping).

## Compliance status enum (per assessment row)

`platform.control_assessments.effectiveness` ∈
`EFFECTIVE / NEEDS_IMPROVEMENT / INEFFECTIVE / NOT_TESTED`.

`platform.control_mappings.coverage` ∈ `FULL / PARTIAL / COMPENSATING`.

---

**See also:** `07_Control_Library_Import_Template.md`,
`docs/grc/framework-intersection-plan.md`,
`tss-grc-backend/.../V91__authority_documents_catalog.sql`.
