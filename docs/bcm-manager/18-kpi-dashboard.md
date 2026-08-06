# 18 · BCM KPI Dashboard

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** BCM Manager · **Backing entity:** `BcmReadinessScore` service, `bcm_dashboard` view, `risk_lc.kri_breaches`

> The numbers the BCM Manager + CRO live by. Refresh monthly; report quarterly to Board RC.

---

## 1 · KPI catalogue

### 1.1 Programme health
| KPI | Target | Current threshold | Source |
|---|---|---|---|
| % BIAs current (< 12 months) | ≥ 95% | < 90% = red | `bcm.bia_records` |
| % BCPs current (< 12 months) | ≥ 95% | < 90% = red | `bcm.bcp_plans` |
| % critical processes with approved BIA | 100% | < 100% = red | `bcm.critical_processes` ⋈ `bia_records` |
| % critical processes with active BCP | 100% | < 100% = red | `bcm.critical_processes` ⋈ `bcp_plans` |
| % L1 contacts tested in last 30 days | ≥ 95% | < 90% = red | `bcm.communication_contacts.last_tested_date` |
| Maturity score (ISO 22301) | ≥ 3.5 / 5.0 | < 3.0 = red | `bcm.maturity_assessments` (Slice 7-B target) |

### 1.2 Exercise programme
| KPI | Target | Current threshold |
|---|---|---|
| Exercises completed per year | ≥ 12 | < 8 = red |
| % TIER_1 processes exercised | 100% | < 100% = red |
| Avg exercise score | ≥ 80% | < 70% = red |
| Lessons → CAPA conversion | ≥ 95% | < 80% = red |
| CAPA closure within target | ≥ 85% | < 70% = red |
| Time since last full-scale exercise | ≤ 18 months | > 24 months = red |

### 1.3 Recovery performance
| KPI | Target | Current threshold |
|---|---|---|
| Avg RTO actual vs target (last 4 exercises) | ≤ 110% | > 130% = red |
| Avg RPO actual vs target | ≤ 110% | > 130% = red |
| Successful failovers / total attempts | 100% | < 90% = red |
| Mean time to declare crisis | ≤ 30 min from awareness | > 60 min = red |
| Mean time to first regulator notification | ≤ 80% of SLA | > 90% = red |

### 1.4 Vendor / supply chain
| KPI | Target | Current threshold |
|---|---|---|
| % critical vendors with current BCP attestation | 100% | < 90% = red |
| % critical vendors scored ≥ 60 | ≥ 95% | < 85% = red |
| # critical vendors in `Weak` (40-59) band | 0 | > 2 = red |
| # critical vendors with exit-plan invocation | 0 (steady state) | > 0 escalate |

### 1.5 Regulator posture
| KPI | Target | Current threshold |
|---|---|---|
| % regulator notifications submitted within SLA | 100% | < 100% = red |
| % SAMA 6h notifications within 4h (cushion) | ≥ 80% | < 70% = red |
| # SAMA-actionable findings (last 12 months) | 0 | > 0 escalate |
| # NCA findings | 0 | > 0 escalate |
| # SDAIA findings | 0 | > 0 escalate |

### 1.6 BCM-specific KRIs (already in `risk_lc.kri_breaches`)
- DR-test failure rate
- Critical-patches overdue
- BCP plan age
- Critical-vendor security-review expiry
- Production incidents/month
- DR-test failure rate (%)
- Risk acceptances > 90 days unresolved

## 2 · Board-pack BCM section template

Quarterly. Auto-generated via `BoardPackBcmSection` (Slice 7-D target).

### Section structure (1 slide)
1. **Headline number** — overall BCM readiness % (composite of §1.1)
2. **Top 5 risks** — from risk register, residual score ranked
3. **Exercise summary** — count + pass rate this quarter
4. **CAPA status** — open / overdue
5. **Regulator log** — notifications submitted this quarter
6. **Key milestones next quarter** — top 3 from strategic roadmap

## 3 · Monthly BCM Manager dashboard (1 page)

| Quadrant | Content |
|---|---|
| Top-left | Programme health KPIs §1.1 |
| Top-right | Active crisis events + open CAPAs |
| Bottom-left | Upcoming exercises (next 30/60/90 days) |
| Bottom-right | Vendor BCP attestation status |

## 4 · Quarterly RC report (5 slides)

1. Programme health + maturity
2. Risk register changes
3. Exercise outcomes
4. Real incidents + AAR themes
5. Decisions requested

## 5 · Annual board report (15 slides)

Comprehensive — see §3 of 15-annual-bcm-review.md.

## 6 · Real-time vs lagging
- **Real-time KPIs** (dashboard): active crisis events, open CAPAs, expiring BCPs
- **Lagging KPIs** (monthly): exercise pass rate, RTO/RPO trends
- **Strategic KPIs** (annual): maturity progression, strategic-roadmap delivery

## 7 · Saudi-specific KPI considerations
- Hajj/Ramadan peak-season recovery success (separate from steady-state)
- Cross-border data-transfer compliance rate (PDPL Art. 29)
- Vision 2030 BCM-related programme contribution (if applicable)
