# Business Goals & Objectives
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the measurable business goals, strategic objectives, key performance indicators (KPIs), and success metrics for the TSS GRC platform. These goals guide prioritization, investment decisions, and progress tracking throughout all phases of the initiative.

---

## Strategic Alignment

The TSS GRC platform directly supports the following strategic themes:

| Strategic Theme | Alignment |
|----------------|-----------|
| **Saudi Vision 2030 Digital Transformation** | Provides digital tooling for governance and compliance modernization across government and private sectors. |
| **NCA Regulatory Compliance** | Enables organizations to achieve and maintain compliance with mandatory NCA frameworks. |
| **Data Sovereignty** | Ensures sensitive governance data remains within KSA borders through on-premises deployment capability. |
| **Market Leadership** | Positions TSS as the definitive GRC platform provider in the Saudi and GCC markets. |

---

## Business Goals

### BG-1: Establish Market Presence in Saudi GRC

**Description:** Launch the TSS GRC platform as the first Arabic-native, regulation-aware, modular GRC solution targeting Saudi-regulated organizations.

**Objectives:**

| ID | Objective | Target | Timeline |
|----|-----------|--------|----------|
| BG-1.1 | Achieve first production deployment | 1 customer live | Month 9 |
| BG-1.2 | Onboard pilot customers | 5 organizations | Month 12 |
| BG-1.3 | Achieve market awareness among target segments | 40% aided brand recall in CISO surveys | Month 18 |
| BG-1.4 | Secure government sector reference customer | 1 government entity live | Month 15 |

---

### BG-2: Generate Recurring Revenue

**Description:** Build a sustainable subscription revenue stream through modular licensing.

**Objectives:**

| ID | Objective | Target | Timeline |
|----|-----------|--------|----------|
| BG-2.1 | Achieve annual recurring revenue (ARR) milestone | SAR 2M ARR | Month 18 |
| BG-2.2 | Attain average modules per customer | 3+ modules | Month 18 |
| BG-2.3 | Achieve net revenue retention | >110% NRR | Month 24 |
| BG-2.4 | Maintain gross margin on SaaS delivery | >70% | Month 18 |

---

### BG-3: Deliver Regulatory Compliance Value

**Description:** Enable customers to measurably improve their compliance posture using the platform.

**Objectives:**

| ID | Objective | Target | Timeline |
|----|-----------|--------|----------|
| BG-3.1 | Reduce time-to-compliance for NCA ECC | 50% reduction vs. manual process | Month 12 |
| BG-3.2 | Automate compliance evidence collection | 60% of evidence auto-linked | Month 15 |
| BG-3.3 | Improve audit cycle time | 40% reduction | Month 18 |
| BG-3.4 | Enable real-time compliance dashboards | <24hr data freshness | Month 12 |

---

### BG-4: Achieve Platform Technical Excellence

**Description:** Build a platform that is performant, reliable, secure, and maintainable.

**Objectives:**

| ID | Objective | Target | Timeline |
|----|-----------|--------|----------|
| BG-4.1 | Platform uptime (SaaS) | 99.5% availability | Month 12 |
| BG-4.2 | API response time (p95) | <500ms | Month 9 |
| BG-4.3 | Security vulnerability resolution | Critical: <24hr, High: <72hr | Ongoing |
| BG-4.4 | Automated test coverage | >80% line coverage | Month 9 |
| BG-4.5 | Support concurrent users per tenant | 200+ concurrent users | Month 12 |

---

### BG-5: Build Partner Ecosystem

**Description:** Develop a partner network of system integrators, consultants, and MSSPs.

**Objectives:**

| ID | Objective | Target | Timeline |
|----|-----------|--------|----------|
| BG-5.1 | Onboard implementation partners | 3 certified partners | Month 15 |
| BG-5.2 | Develop partner training program | Certification program live | Month 12 |
| BG-5.3 | Partner-sourced pipeline | 30% of pipeline from partners | Month 24 |

---

## Key Performance Indicators (KPIs)

### Customer KPIs

| KPI | Definition | Target | Measurement Frequency |
|-----|-----------|--------|----------------------|
| Customer Acquisition | New customers per quarter | 3-5 per quarter (post-launch) | Quarterly |
| Module Adoption Rate | Average modules per customer | 3+ modules | Quarterly |
| Customer Satisfaction (CSAT) | Post-interaction satisfaction score | >4.2 / 5.0 | Quarterly |
| Net Promoter Score (NPS) | Customer likelihood to recommend | >40 | Semi-annually |
| Customer Churn Rate | Annual customer logo churn | <10% | Annually |
| Time to Value | Days from contract to first module live | <45 days | Per deployment |

### Revenue KPIs

| KPI | Definition | Target | Measurement Frequency |
|-----|-----------|--------|----------------------|
| Annual Recurring Revenue (ARR) | Total annualized subscription value | SAR 2M by Month 18 | Monthly |
| Average Contract Value (ACV) | Average annual deal size | SAR 200K+ | Quarterly |
| Net Revenue Retention (NRR) | Revenue retained + expansion from existing customers | >110% | Annually |
| Gross Margin | Revenue minus cost of delivery | >70% | Quarterly |

### Platform KPIs

| KPI | Definition | Target | Measurement Frequency |
|-----|-----------|--------|----------------------|
| Platform Uptime | Percentage availability (SaaS) | 99.5% | Monthly |
| Mean Time to Recovery (MTTR) | Average recovery time from incidents | <2 hours | Per incident |
| API Performance (p95) | 95th percentile response time | <500ms | Daily |
| Security Incidents | Critical/high vulnerabilities in production | 0 unresolved >SLA | Weekly |
| Release Cadence | Production releases per month | 2+ releases/month | Monthly |

### Compliance Value KPIs

| KPI | Definition | Target | Measurement Frequency |
|-----|-----------|--------|----------------------|
| Framework Coverage | Percentage of NCA ECC controls mapped | 100% | Per release |
| Compliance Score Improvement | Average customer compliance score delta | +20% in first 6 months | Semi-annually |
| Audit Finding Resolution | Average days to resolve findings | <30 days | Quarterly |
| Evidence Automation Rate | Percentage of evidence auto-collected | >60% | Quarterly |

---

## Success Metrics by Phase

### Phase 1: Analysis & Architecture (Months 1-3)

| Metric | Target |
|--------|--------|
| All 16 analysis documents completed and approved | 100% |
| Architecture decision records finalized | All critical ADRs documented |
| Technology stack validated with POC | Core stack proven |
| Stakeholder alignment achieved | Sign-off from all key stakeholders |

### Phase 2: Core Platform Build (Months 3-6)

| Metric | Target |
|--------|--------|
| Core infrastructure operational | Auth, multi-tenancy, audit trail functional |
| Administration module complete | User, role, org management live |
| ERM module MVP complete | Risk register, assessment, treatment workflows |
| Automated test suite established | >70% coverage |

### Phase 3: Module Expansion (Months 6-12)

| Metric | Target |
|--------|--------|
| Cybersecurity Compliance module live | NCA ECC fully mapped |
| Reporting & Analytics module live | Dashboards, exports functional |
| First customer deployment | 1 production tenant |
| 5 pilot customers onboarded | Active platform usage |

---

## Goal Dependencies

```
BG-4 (Technical Excellence)
  |
  +--> BG-3 (Compliance Value) --> BG-1 (Market Presence) --> BG-2 (Revenue)
  |                                                              |
  +--> BG-5 (Partner Ecosystem) --------------------------------+
```

Technical excellence is the foundation. Compliance value drives market presence, which drives revenue. The partner ecosystem amplifies both market presence and revenue.

---

## Cross-References

- [Executive Summary](./01-executive-summary.md)
- [Product Vision](./02-product-vision.md)
- [Success Criteria](./07-success-criteria.md)
- [Stakeholder Analysis](./08-stakeholder-analysis.md)
