# 06 · Risk Assessment Methodology

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** CRO + BCM Manager · **Cadence:** Annual + on-trigger · **Backing entity:** `risk.risks` (source_module=BCM), `bcm.bcm_risks`

---

## 1 · Purpose
Identify, evaluate, and treat business-continuity risks consistently so the BCM programme can prioritise investment and exercise selection.

## 2 · Threat catalogue (Saudi-banking edition)

### 2.1 Cyber
- Ransomware (Saudi-targeted variants)
- DDoS during peak periods (Hajj, Ramadan, year-end)
- Supply-chain attack (npm, vendor token compromise)
- OT/SCADA attack (refinery, ATM switch, pipeline)
- DNS poisoning, BGP hijack
- Insider threat (privileged abuse, data exfil)
- Social engineering / spear phishing
- SS7/SIGTRAN attack on OTP delivery
- SIM-swap fraud
- Card-skimmer + ATM physical tampering

### 2.2 Physical
- Eastern Province sandstorm
- Riyadh extreme heat day → branch ops + cooling failure
- Coastal flooding (Eastern Province)
- Fire / smoke at HQ or DC
- Construction-related power outage
- Hajj / Eid mass-gathering disruption
- Earthquake (very low likelihood, KSA)

### 2.3 Technology
- Core banking outage (T24)
- Payment switch outage (Way4, Ericsson Money)
- Database corruption
- Storage subsystem failure
- Network backbone failure
- Vendor SaaS outage (Salesforce, SAP, ServiceNow)
- Cloud regional outage (AWS me-south-1, Azure-KSA)

### 2.4 Supply chain / vendor
- Critical-vendor bankruptcy
- Vendor security breach (4th-party)
- Vendor performance degradation
- Single-source supplier monopoly
- Sanctions affecting vendor
- Vendor BCP failure

### 2.5 Regulatory / legal
- SAMA fine for major incident notification miss
- PDPL Article 20 breach notification miss (72h SDAIA)
- NCA control finding requiring remediation
- CMA disclosure failure
- ZATCA e-invoicing non-compliance
- Cross-border data restriction change

### 2.6 People
- Pandemic absenteeism
- Strike / industrial action (rare in KSA but possible vendor-side)
- Key-person departure
- Skills shortage (cybersecurity, Arabic-fluent OT engineers)

### 2.7 Climate (TCFD-aligned)
- Acute physical: heatwave, sandstorm
- Chronic physical: sea-level rise (Eastern Province coastal)
- Transition: carbon price impact on financed emissions
- Regulatory: CMA Art.91-94 disclosure compliance

## 3 · Scoring scale

### 3.1 Likelihood (1-5)
| Score | Meaning | Annual probability |
|---|---|---|
| 5 | Almost certain | > 90% |
| 4 | Likely | 60-90% |
| 3 | Possible | 30-60% |
| 2 | Unlikely | 10-30% |
| 1 | Rare | < 10% |

### 3.2 Impact (1-5)
| Score | BCM impact |
|---|---|
| 5 | TIER_1 process unavailable > MTPD, > 100k customers, SAR 100M+ |
| 4 | TIER_1 degraded or TIER_2 outage > MTPD, SAR 10-100M |
| 3 | TIER_2 degraded or contained TIER_1 incident, SAR 1-10M |
| 2 | TIER_3 outage, SAR 0.1-1M |
| 1 | Negligible operational impact |

### 3.3 Inherent vs residual
Score both:
- **Inherent** — likelihood × impact assuming current controls FAIL
- **Residual** — likelihood × impact with current controls WORKING

Treatment effectiveness = (inherent_score − residual_score) / inherent_score × 100%.

## 4 · Risk-treatment options

| Option | When to use |
|---|---|
| **Avoid** | Stop doing the activity (rare for critical processes) |
| **Mitigate** | Add/improve controls (most common) |
| **Transfer** | Insurance, vendor liability clauses, outsourcing |
| **Accept** | Cost of mitigation > residual loss expectancy; documented risk acceptance |

## 5 · Risk-appetite alignment

Each top-10 BCM risk is mapped to a `risk.risk_appetite_statements` row. If residual score breaches appetite, immediate escalation to Risk Committee.

## 6 · KRI thresholds (sample BCM-specific)

| KRI | Threshold | Action |
|---|---|---|
| DR-test failure rate (% over last 4 quarters) | > 15% | Open CAPA + Risk Committee review |
| Critical patches > 30 days overdue | > 0 | Immediate remediation + CISO sign-off |
| BCP version age | > 12 months | Mandatory refresh within 30 days |
| Critical-vendor BCP attestation gap | > 0 | Vendor engagement within 14 days |
| Mean RTO actual vs target (last 4 exercises) | > 110% | Strategy review + CAPA |
| Crisis-comms tree failed contacts | > 5% | Refresh contact database + re-test |
| Time since last full-scale exercise | > 24 months | Schedule + budget within 30 days |

## 7 · Scenario library

Each top-10 risk gets at least one exercise scenario in `bcm.exercise_scenarios`. Scenarios should:
- Be specific (named systems, time of day, season)
- Be realistic (probability-weighted, not "worst case ever")
- Test multiple processes (cascade)
- Stress regulator-notification SLAs (force SAMA 6h drill)

## 8 · Quarterly heatmap

Generated automatically by `BcmReadinessScore` service. Plots inherent × residual on 5×5 grid, coloured by risk_level. Distributed to Risk Committee + Board.

## 9 · Continuous improvement

After every exercise + real incident, the BCM-risk register is reviewed. New threats identified → add to catalogue. Treatments that failed → re-evaluate residual scores.
