# Incident Management Flow
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the incident management flow covering cybersecurity incidents, privacy breaches, and BCM incidents, with cross-module linkage.

---

## Cyber Incident Lifecycle

```mermaid
flowchart TD
    A([Incident Detected]) --> B{Detection Source}
    B -->|SIEM Alert| C[Auto-reported Incident]
    B -->|User Report| D[Manual Incident Creation]
    B -->|Vulnerability Scan| E[Vulnerability-triggered Incident]
    C & D & E --> F[Incident Created - REPORTED]

    F --> G[Initial Triage\nAssign Severity & Type]
    G --> H{Severity?}
    H -->|CRITICAL| I[Immediate: Escalate to CISO\nNotify Management]
    H -->|HIGH| J[Within 1 hour: Notify CISO]
    H -->|MEDIUM| K[Within 4 hours: Notify team]
    H -->|LOW| L[Standard processing]

    I & J & K & L --> M[Assign Incident Responder]
    M --> N[Status: IN_INVESTIGATION]
    N --> O[Investigate\nCollect Evidence + Attachments]
    O --> P[Identify Affected Assets\nLink to cyber.assets]

    P --> Q{Data Breach?}
    Q -->|Yes| R[Create Privacy Breach Record\nprivacy.breach_records]
    Q -->|No| S[Continue investigation]
    R --> S

    S --> T[Contain Incident]
    T --> U[Status: CONTAINED]
    U --> V[Eradicate Threat]
    V --> W[Status: ERADICATED]
    W --> X[Recover Systems]
    X --> Y[Status: RECOVERED]

    Y --> Z{Elevate to Enterprise Risk?}
    Z -->|Yes - High/Critical| AA[Create/Link Risk Record\nrisk.risks - source_module=CYBER]
    Z -->|No| AB[Post-Incident Review]
    AA --> AB

    AB --> AC[Document Root Cause]
    AC --> AD[Document Lessons Learned]
    AD --> AE[Implement Corrective Actions]
    AE --> AF[Status: CLOSED]
    AF --> AG([End])
```

---

## Privacy Breach Notification Flow

```mermaid
flowchart TD
    A[Privacy Breach Detected] --> B[Create Breach Record\nprivacy.breach_records]
    B --> C[Notify DPO]
    C --> D{Requires Regulatory Notification?}
    D -->|Yes - PDPL threshold| E[72-hour clock starts]
    D -->|No| F[Internal investigation only]

    E --> G{Within 72 hours}
    G -->|Notification sent| H[notified_authority_at populated]
    G -->|Overdue| I[ALERT: Regulatory deadline breach]
    H --> J{Data Subjects Need Notification?}
    J -->|Yes| K[Notify affected data subjects]
    J -->|No| L[Continue investigation]
    K & L --> M[Link to Cyber Incident if applicable]
    M --> N[Link to Risk Register]
    N --> O[Closure + Documentation]
```

---

## BCM Incident Flow

```mermaid
flowchart TD
    A([Disruptive Event Occurs]) --> B[BCM Incident Declared\nbcm.bcm_incidents]
    B --> C[Activate BCM Plan\nbcm.bcm_plans - activated_plan_id]
    C --> D[Incident Commander Assigned]
    D --> E[Execute Recovery Strategies]
    E --> F{RTO Achievable?}
    F -->|Yes| G[Execute Recovery]
    F -->|No| H[Escalate - Crisis Management]
    H --> I[Executive Crisis Team Activated]
    I --> G
    G --> J[Monitor Recovery Progress]
    J --> K{Systems Restored?}
    K -->|No| J
    K -->|Yes| L[BCM Incident Resolved]
    L --> M[Link to Enterprise Risk Record]
    M --> N[Post-Incident Review\nUpdate BCP/BIA]
    N --> O([End])
```
