# Compliance Assessment Flow

## Purpose
Defines the business process for conducting compliance assessments against frameworks like NCA ECC, ISO 27001, ISO 22301, and Saudi PDPL.

---

## Compliance Assessment Process

```mermaid
flowchart TD
    A([Start: Plan Assessment]) --> B[Select Compliance Framework\nNCA ECC / ISO 27001 / PDPL etc.]
    B --> C[Define Scope\nOrganization / BU / Systems]
    C --> D[Create Assessment Record\ncompliance.assessments]
    D --> E[Assign Assessor / Team]
    E --> F[Load Framework Controls\ncompliance.assessment_controls created]
    F --> G[Status: IN_PROGRESS]

    G --> H{For Each Control}
    H --> I[Assess Compliance Status\nCOMPLIANT / PARTIALLY / NON-COMPLIANT / N/A]
    I --> J{Evidence Required?}
    J -->|Yes| K[Upload Evidence\nplatform.attachments + compliance.evidence_items]
    J -->|No| L[Record Assessor Notes]
    K & L --> M[Record Control Score]
    M --> N{More Controls?}
    N -->|Yes| H
    N -->|No| O[Calculate Overall Score\nand Compliance %]

    O --> P[Identify Findings\ncompliance.findings]
    P --> Q{Findings Exist?}
    Q -->|Yes| R[Create Gap Records\ncompliance.gaps]
    Q -->|No| S[Assessment Complete - No Gaps]
    R --> T[Create Remediation Plans\ncompliance.remediation_plans]
    T --> U[Assign Responsible Users + Target Dates]

    U & S --> V[Submit Assessment for Approval]
    V --> W{Workflow Approval}
    W -->|Approved| X[Assessment Status: APPROVED]
    W -->|Rejected - Revisions Needed| G

    X --> Y[Generate Compliance Report\nPDF / Excel]
    Y --> Z[Monitor Remediation Plans\nStatus Tracking]
    Z --> AA{All Remediations Done?}
    AA -->|No| AB[Track Progress\nSend Reminders via Workflow]
    AB --> Z
    AA -->|Yes| AC[Evidence Verified]
    AC --> AD[Assessment Closed / Archived]
    AD --> AE([End: Schedule Next Assessment])
```

---

## Multi-Framework Assessment View

```mermaid
flowchart LR
    subgraph Shared["Shared Control Library"]
        CTRL[platform.controls\nControl CTRL-0042\nAccess Control Policy]
    end

    subgraph Assessments
        A1[NCA ECC Assessment\n2.4.1 Access Control\n→ COMPLIANT]
        A2[ISO 27001 Assessment\nA.9.1 Access Control Policy\n→ PARTIALLY_COMPLIANT]
        A3[PDPL Assessment\nArt. 21 Data Access\n→ COMPLIANT]
    end

    CTRL --> A1
    CTRL --> A2
    CTRL --> A3

    A1 & A2 & A3 --> UV[Unified Control View\nOne control — three framework assessments]
```

---

## Compliance Score Calculation

```mermaid
flowchart LR
    A[Total Controls: 93] --> B
    B[Compliant: 60] --> E
    C[Partially Compliant: 20] --> E
    D[Non-Compliant: 8] --> E
    NA[N/A: 5] --> F[Excluded from calculation]
    E[Applicable Controls: 88] --> G[Score = 60 + 20×0.5 / 88 = 79.5%]
```
