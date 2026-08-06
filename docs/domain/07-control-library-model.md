# Control Library Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the shared control library model that serves as the backbone for mapping security and risk controls across all GRC modules — ERM, Cybersecurity, Compliance, and BCM.

---

## Design Philosophy

The control library is a **shared platform entity** (in the `platform` schema), not owned by any single module. This enables:
- A single control to be assessed against ISO 27001, NCA ECC, and PDPL simultaneously
- Risk treatments in ERM to reference the same control assessed in Compliance
- Cyber control assessments to feed into the Unified Risk View
- Elimination of duplicate control entries across teams

---

## Entity Model

### platform.control_frameworks

Pre-loaded frameworks for initial deployment:

| Code | Name | Version | Issuing Body |
|------|------|---------|-------------|
| NCA_ECC | Essential Cybersecurity Controls | 2.0 | National Cybersecurity Authority (NCA) |
| NCA_CSCC | Critical Systems Cybersecurity Controls | 1.0 | NCA |
| ISO_27001 | Information Security Management System | 2022 | ISO/IEC |
| ISO_27701 | Privacy Information Management | 2019 | ISO/IEC |
| ISO_22301 | Business Continuity Management | 2019 | ISO |
| ISO_31000 | Risk Management | 2018 | ISO |
| PDPL | Personal Data Protection Law | 2023 | SDAIA |

### platform.control_framework_domains

Hierarchical domain structure within each framework:

```
ISO_27001
├── A.5  Organizational Controls (37 controls)
├── A.6  People Controls (8 controls)
├── A.7  Physical Controls (14 controls)
├── A.8  Technological Controls (34 controls)

NCA_ECC
├── 1.0  Cybersecurity Governance
├── 2.0  Cybersecurity Defense
├── 3.0  Cybersecurity Resilience
├── 4.0  Third-Party & Cloud Cybersecurity
└── 5.0  Industrial Control Systems Security
```

### platform.controls

Individual control entries. Controls are either:
- **Pre-loaded**: Seeded from frameworks at deployment (read-only baseline)
- **Custom**: Created by the organization

Key control fields:
```sql
code                  -- Organization-assigned code (e.g., 'CTRL-0042')
title_en / title_ar   -- Control title in both languages
description_en/_ar    -- Detailed description
control_type          -- PREVENTIVE, DETECTIVE, CORRECTIVE, DIRECTIVE
control_category      -- Access Control, Cryptography, Physical, etc.
implementation_status -- NOT_IMPLEMENTED, PARTIALLY_IMPLEMENTED, FULLY_IMPLEMENTED
effectiveness         -- INEFFECTIVE, PARTIALLY_EFFECTIVE, EFFECTIVE, HIGHLY_EFFECTIVE
owner_user_id         -- Responsible owner
review_frequency      -- MONTHLY, QUARTERLY, ANNUALLY
```

### platform.control_framework_mappings

Links controls to framework references (many-to-many):

```
Control CTRL-0042 → ISO 27001 "A.8.3 Information Access Restriction"
                  → NCA ECC "2.4.1 Access Control"
                  → PDPL "Article 21 - Data Access Controls"
```

One control can satisfy requirements across multiple frameworks simultaneously.

---

## Cross-Module Control References

| Module | Reference Table | Usage |
|--------|----------------|-------|
| ERM | `risk.risk_controls` | Controls that mitigate specific risks |
| Cybersecurity | `cyber.cyber_controls` | Controls assessed per asset or cyber assessment |
| Compliance | `compliance.assessment_controls` | Controls evaluated in compliance assessment |
| BCM | BCP plans reference controls | Recovery-related controls |

---

## Control Effectiveness Scoring

| Effectiveness | Score | Description |
|--------------|-------|-------------|
| HIGHLY_EFFECTIVE | 4 | Control fully implemented and verified effective |
| EFFECTIVE | 3 | Control implemented and generally effective |
| PARTIALLY_EFFECTIVE | 2 | Control partially implemented or with gaps |
| INEFFECTIVE | 1 | Control exists but not working |
| NOT_ASSESSED | 0 | Control not yet evaluated |

---

## Control Assessment Workflow

```
Control created/assigned
    → Assigned to owner
    → Owner completes assessment (effectiveness rating + evidence)
    → Evidence attached (platform.attachments)
    → Submitted for review → workflow approval
    → Next review date set
    → Status updated
```

---

## Pre-loaded Control Library

At deployment, the following are seeded:
- **NCA ECC 2.0**: Full control set (~114 sub-controls)
- **ISO 27001:2022 Annex A**: Full 93 controls
- **PDPL**: Key compliance requirements as controls

These are read-only reference entries. Organizations create their own implementations by:
1. Copying the framework reference to their organization control list, OR
2. Creating assessments that directly reference the framework controls
