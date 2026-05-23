# 04 · BIA Methodology
**Document owner:** BCM Manager · **Cadence:** Annual + on-trigger · **Backing entity:** `bcm.bia_records`, BIA Wizard page

---

## 1 · Purpose
Establish a repeatable, defensible methodology for measuring the impact of disrupting each business process, so RTO/RPO/MTPD targets are evidence-based — not opinion.

## 2 · Inputs
- Process inventory (`bcm.critical_processes`)
- Dependency map (`bcm.process_dependencies`, `bcm.critical_resources`)
- Last 12 months of incidents (`cyber.cyber_incidents`, `bcm.crisis_events`)
- Financial data (revenue per process, cost-of-downtime per hour)
- Regulator commitments (SLA breaches by clause)
- Customer commitments (SLA contracts)
- Peak-season calendar (`bcm.peak_seasons`)

## 3 · Methodology — Time-band × Impact-dimension matrix

### 3.1 Time bands (along the X axis)
| Code | Window |
|---|---|
| T1 | 0-1h |
| T2 | 1-4h |
| T3 | 4-24h |
| T4 | 24-72h |
| T5 | 72h-7d |
| T6 | 7d+ |

### 3.2 Impact dimensions (along the Y axis)
| Code | Dimension | Measure |
|---|---|---|
| D1 | Financial | SAR loss (revenue + fines + recovery cost) |
| D2 | Operational | % critical-process throughput impact |
| D3 | Regulatory | SLA breach severity (none → minor → major → systemic) |
| D4 | Reputational | Media reach × customer churn risk |
| D5 | Health & Safety | Persons exposed × severity |
| D6 | Legal | Litigation + contract-breach exposure (SAR) |

### 3.3 Scoring scale per cell
Per (time-band × dimension) cell, score 1–5:
- **1 (Low)** — recoverable, < SAR 0.1M impact, < 100 customers
- **2 (Minor)** — recoverable, SAR 0.1-1M, < 1k customers
- **3 (Moderate)** — significant, SAR 1-10M, < 10k customers, regulator notification
- **4 (High)** — severe, SAR 10-100M, < 100k customers, regulatory action
- **5 (Critical)** — existential, SAR 100M+, > 100k customers, suspension risk

### 3.4 Overall impact = MAX of cells × 0.6 + AVG × 0.4
Not pure-max (lone outliers don't dominate) and not pure-avg (multiple-moderate hits register).

## 4 · RTO/RPO/MTPD derivation rules

- **MTPD** = first time-band where ANY dimension scores 4 or 5
- **RTO** ≤ 0.5 × MTPD (cushion for back-out)
- **RPO** ≤ 0.1 × MTPD (data freshness)

Examples:
- T24 core banking → MTPD = 4h (financial+regulator hit T2) → RTO ≤ 2h, RPO ≤ 0.4h
- HR payroll → MTPD = 72h (only operational hits at T4) → RTO ≤ 36h, RPO ≤ 7h

## 5 · Dependency rule
A process's RTO is constrained by the **slowest upstream dependency**. If T24 has RTO 2h but Oracle Exadata has RTO 4h, T24's effective RTO is 4h. The BIA review must surface these contradictions.

## 6 · Saudi peak-season adjustment
During Hajj, Ramadan, and Eid bonus payout windows, MTPD compresses by 30-50% for retail-banking + payment processes. The BIA records BOTH the steady-state MTPD and the peak-season MTPD.

## 7 · Review cycle
- **Annual** — every BIA refreshed (Q1 default)
- **On trigger** — new product launch, M&A, new regulation, major incident, vendor change, infrastructure change
- **Cadence enforcement** — annual reminder 30/14/7/1 days before due (Sprint 7-C automation)

## 8 · Approval chain
Process Owner → BCM Manager → CRO → CFO (sign-off on financial RTO commitment) → Board (annual aggregate, not per-process).

## 9 · Storage
- `bcm.bia_records` — one row per (process, version)
- `bcm.bia_assessments` — point-in-time snapshot for audit trail
- Evidence (workshop notes, financial model) attached via `platform.attachments`

## 10 · Common pitfalls (watch for)
- Optimistic RTO claims by process owners — pressure-test in exercises
- Failure to consider dependencies — always run downstream-impact simulation
- Stale BIA — must be refreshed at least annually; > 18 months = automatic CAPA
- Single-point-of-failure systems with TIER_1 dependencies and no DR
- Peak-season blindness — BIA done in March doesn't see Hajj traffic
