# Unified Risk View — Data Aggregation Diagram

## Purpose
Shows how risk data from all five GRC modules aggregates into the Unified Risk View, which is the key executive dashboard feature.

---

## Data Flow to Unified Risk View

```mermaid
flowchart TD
    subgraph Modules["GRC Module Risk Sources"]
        ERM[ERM Module\nEnterprise Risks\nManually entered\nrisk.risks source=ERM]
        CYB[Cybersecurity Module\nHigh/Critical Incidents\nElevated to risk register\nrisk.risks source=CYBER]
        BCM[BCM Module\nBusiness Impact Analysis\nProcess risk impacts\nrisk.risks source=BCM]
        PRV[Privacy Module\nPersonal Data Breaches\nPDPL regulatory risk\nrisk.risks source=PRIVACY]
        CMP[Compliance Module\nCritical/High Findings\nRegulatory non-compliance\nrisk.risks source=COMPLIANCE]
    end

    subgraph RiskRegister["risk.risks Table"]
        RR[Unified Risk Register\nAll risks — all sources\nInherent + Residual scores\nOwner + Treatment tracked]
    end

    subgraph View["Reporting Layer"]
        UV[reporting.vw_unified_risk_summary\nSQL Server View\nJoins users, orgs, categories]
        CACHE[Application Cache\nCaffeine — 5 min TTL]
    end

    subgraph Dashboard["Executive Dashboard"]
        HM[Risk Heatmap\n5×5 Matrix\nChart.js]
        RBL[Risk by Level\nCRITICAL / HIGH / MEDIUM / LOW\nDonut Chart]
        RBS[Risk by Source Module\nBar Chart]
        TOP[Top 10 Open Risks\nTable]
        TREND[Risk Score Trend\nLine Chart — last 12 months]
    end

    ERM -->|Direct create/update| RR
    CYB -->|linked_risk_id FK| RR
    BCM -->|linked_risk_ids JSON| RR
    PRV -->|linked_risk_id FK| RR
    CMP -->|creates entry| RR

    RR --> UV
    UV --> CACHE
    CACHE --> HM
    CACHE --> RBL
    CACHE --> RBS
    CACHE --> TOP
    CACHE --> TREND
```

---

## Risk Aggregation Logic

```mermaid
flowchart LR
    subgraph Filters["Dashboard Filters"]
        F1[Organization]
        F2[Business Unit]
        F3[Date Range]
        F4[Risk Level]
        F5[Source Module]
        F6[Status]
    end

    subgraph Query["vw_unified_risk_summary Query"]
        Q[SELECT with WHERE clauses\nApplies all active filters\nPaginated + cached]
    end

    subgraph Output["Dashboard Widgets"]
        O1[Heatmap: risks grouped by likelihood + impact]
        O2[KPI Cards: counts by level]
        O3[Source breakdown: ERM vs Cyber vs BCM etc.]
        O4[Owner workload: risks per owner]
        O5[Treatment status: accept/mitigate/transfer/avoid]
    end

    F1 & F2 & F3 & F4 & F5 & F6 --> Q
    Q --> O1 & O2 & O3 & O4 & O5
```

---

## Cross-Module Risk Chain

```mermaid
flowchart LR
    A[Cyber: Data breach detected\ncyber.incidents] -->|CRITICAL severity| B[Risk auto-elevated\nrisk.risks source=CYBER]
    B --> C[Privacy: Breach recorded\nprivacy.breach_records]
    C -->|linked_risk_id| B
    B --> D[Compliance: PDPL finding\ncompliance.findings]
    D -->|creates| E[Compliance Risk Entry\nrisk.risks source=COMPLIANCE]
    B & E --> F[Unified Risk View\nShows full chain]
```
