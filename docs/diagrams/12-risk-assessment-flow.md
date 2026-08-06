# Risk Assessment Business Flow
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the end-to-end business flow for the Enterprise Risk Management module's risk assessment process.

---

## Risk Lifecycle Flow

```mermaid
flowchart TD
    A([Start]) --> B[Identify Risk Event or Source]
    B --> C{Source?}
    C -->|Manual Entry| D[Create Risk Record - DRAFT]
    C -->|Cyber Incident Escalation| E[Auto-create from Cyber Module]
    C -->|BCM BIA Finding| F[Auto-create from BCM Module]
    C -->|Privacy Breach| G[Auto-create from Privacy Module]
    C -->|Compliance Gap| H[Auto-create from Compliance Module]
    E & F & G & H --> D

    D --> I[Assign Risk Owner]
    I --> J[Categorize Risk\nType and Category]
    J --> K[Assess Inherent Risk\nLikelihood × Impact = Score]
    K --> L{Risk Level?}
    L -->|CRITICAL| M[CRITICAL: Immediate escalation to CRO/CISO]
    L -->|HIGH| N[HIGH: Review within 5 days]
    L -->|MEDIUM| O[MEDIUM: Review within 30 days]
    L -->|LOW| P[LOW: Annual review]

    M & N & O & P --> Q[Identify Existing Controls\nLink from Control Library]
    Q --> R[Assess Residual Risk\nAfter Controls Applied]
    R --> S{Residual Risk Acceptable?}
    S -->|No - Treatment Needed| T[Define Treatment Strategy\nACCEPT / MITIGATE / TRANSFER / AVOID]
    S -->|Yes - Acceptable| U[Set Status: ACCEPTED]

    T --> V[Create Treatment Plans\nOwner + Target Date]
    V --> W[Submit Risk for Approval]
    W --> X{Workflow Approval}
    X -->|Approved| Y[Risk Status: OPEN]
    X -->|Rejected| D

    Y --> Z[Monitor Risk\nKRI Tracking]
    Z --> AA{KRI Breach?}
    AA -->|Yes| AB[Trigger Alert to Risk Owner]
    AB --> AC[Reassess Risk]
    AC --> K
    AA -->|No| AD{Review Date Reached?}
    AD -->|Yes| AE[Periodic Review\nUpdate Assessment]
    AE --> K
    AD -->|No| Z

    U --> Z

    Z --> AF{Treatment Completed?}
    AF -->|Yes| AG[Verify Effectiveness\nRe-assess Residual]
    AG --> AH{Risk Closed?}
    AH -->|Yes| AI[Close Risk\nDocument Lessons Learned]
    AH -->|No| Z
    AI --> AJ([End])
```

---

## Risk Scoring Matrix Visual

```mermaid
quadrantChart
    title Risk Heat Map (5×5 Matrix)
    x-axis Low Impact --> High Impact
    y-axis Low Likelihood --> High Likelihood
    quadrant-1 Critical Zone
    quadrant-2 High Zone
    quadrant-3 Medium Zone
    quadrant-4 Low Zone
```

---

## Risk Cross-Module Linkage Flow

```mermaid
flowchart LR
    subgraph Sources
        CI[Cyber Incident\nseverity=HIGH]
        PB[Privacy Breach\nPDPL reportable]
        BC[BCM BIA\nfinancial impact=MAJOR]
        CF[Compliance Finding\nseverity=CRITICAL]
    end

    subgraph ERM[Enterprise Risk Register]
        R[Unified Risk Record\nsource_module tracked]
    end

    CI -->|linked_risk_id| R
    PB -->|linked_risk_id| R
    BC -->|linked_risk_ids JSON| R
    CF -->|creates risk entry| R

    R --> UV[Unified Risk View\nDashboard]
```
