# Risk Domain Model

## Purpose
Defines the complete risk domain model for the TSS GRC Platform, covering the risk register, risk assessment methodology, risk treatment, risk appetite, key risk indicators, and cross-module risk aggregation.

---

## Risk Universe

The platform supports risks from multiple sources, all aggregated into a single unified risk register:

| Source | Module | Mechanism |
|--------|--------|-----------|
| Enterprise operational/strategic risks | ERM | Directly created in risk register |
| Cybersecurity incidents | Cyber | Elevated incidents create/link to risks |
| Business continuity impacts | BCM | BIA findings link to risks |
| Privacy breaches | Privacy | Breach records link to risks |
| Compliance gaps | Compliance | Critical findings create risk entries |

---

## Risk Scoring Methodology

### Risk Matrix (5×5)

| | Impact 1 (Negligible) | Impact 2 (Minor) | Impact 3 (Moderate) | Impact 4 (Major) | Impact 5 (Catastrophic) |
|---|---|---|---|---|---|
| **Likelihood 5 (Almost Certain)** | 5 Medium | 10 High | 15 High | 20 Critical | 25 Critical |
| **Likelihood 4 (Likely)** | 4 Low | 8 Medium | 12 High | 16 Critical | 20 Critical |
| **Likelihood 3 (Possible)** | 3 Low | 6 Medium | 9 Medium | 12 High | 15 High |
| **Likelihood 2 (Unlikely)** | 2 Low | 4 Low | 6 Medium | 8 Medium | 10 High |
| **Likelihood 1 (Rare)** | 1 Low | 2 Low | 3 Low | 4 Low | 5 Medium |

### Risk Level Thresholds

| Score | Level | Color |
|-------|-------|-------|
| 20–25 | CRITICAL | Red |
| 12–19 | HIGH | Orange |
| 6–11 | MEDIUM | Amber |
| 1–5 | LOW | Green |

Both inherent (before controls) and residual (after controls) scores are tracked.

---

## Entity Definitions

### risk.risk_categories (Hierarchy)

```
Strategic
Operational
    ├── Process Failure
    ├── People
    └── Technology
Financial
    ├── Market
    ├── Credit
    └── Liquidity
Compliance & Legal
    ├── Regulatory
    └── Contractual
Cyber & Information Security
Reputational
Environmental / ESG
```

### risk.risks (Risk Register)

Key fields beyond standard schema:
- `code`: Auto-generated (e.g., RISK-2025-0042)
- `source_module`: ERM, CYBER, BCM, PRIVACY, COMPLIANCE — tracks origin
- `source_entity_id`: ID of originating record in source module
- `inherent_score`: likelihood × impact (before controls)
- `residual_score`: residual_likelihood × residual_impact (after controls)
- `treatment_strategy`: ACCEPT, MITIGATE, TRANSFER, AVOID, SHARE
- `review_date`: Next scheduled review
- `status`: DRAFT → OPEN → UNDER_TREATMENT → CLOSED / ACCEPTED

### risk.risk_assessments (Assessment History)

Each time a risk is formally assessed, a snapshot is created:
- `assessed_by`, `assessment_date`
- `likelihood`, `impact`, `score` at time of assessment
- `methodology` (qualitative, quantitative, scenario-based)
- `notes`

This provides full assessment history for trend analysis and audit.

### risk.risk_treatments

Specific actions to reduce risk:
- `title`, `description`
- `responsible_user_id`
- `target_date`, `actual_completion_date`
- `status`: PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
- `effectiveness`: post-implementation assessment

### risk.risk_controls (Bridge Table)

Links risks to mitigating controls:
```sql
risk_id       BIGINT FK → risk.risks(id)
control_id    BIGINT FK → platform.controls(id)
effectiveness_rating NVARCHAR(50)  -- How effective is this control for THIS risk
notes         NVARCHAR(MAX)
```

### risk.risk_appetite_statements

Defines the organization's risk tolerance by category:
- `category_id`: Risk category
- `appetite_level`: VERY_LOW, LOW, MODERATE, HIGH, VERY_HIGH
- `threshold_value`: Numeric score threshold (risks above this need immediate action)
- `description_en`, `description_ar`: Plain language statement
- `approved_by`, `approved_at`: Board/exec approval tracking

### risk.risk_indicators (KRIs)

Key Risk Indicators for proactive monitoring:
- `risk_id`: Linked risk
- `name_en`, `name_ar`: KRI name
- `metric`: What is measured
- `threshold_green`, `threshold_amber`, `threshold_red`: Alert thresholds
- `current_value`: Latest measurement
- `last_updated`: When last measured
- `status`: GREEN, AMBER, RED (auto-calculated)

KRI breach triggers notification to risk owner.

---

## Risk Workflow

```
Create Risk (DRAFT)
    → Assign owner
    → Complete risk details + assessment
    → Submit for Review (workflow starts)
    → Risk Manager Review → Approved
    → Status: OPEN
    → Assign Treatment Plans
    → Implement treatments
    → Re-assess residual risk
    → Status: UNDER_TREATMENT
    → Periodic review (based on review_date)
    → Final closure: CLOSED / ACCEPTED
```

---

## Unified Risk View

All risks from all modules are visible in the Unified Risk View, regardless of source:

```sql
SELECT r.*, 'ERM' AS module_badge
FROM risk.risks r WHERE r.source_module = 'ERM'
UNION ALL
-- Risks from cyber incidents are already in risk.risks with source_module = 'CYBER'
-- Same for BCM, Privacy, Compliance
```

The Unified Risk View enables the executive heatmap showing the full organizational risk landscape.
