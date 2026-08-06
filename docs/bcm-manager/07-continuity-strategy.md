# 07 · Continuity Strategy

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** BCM Manager · **Approver:** Board Risk Committee · **Backing entity:** `bcm.continuity_strategies`

---

## 1 · Purpose
Translate BIA outputs into the strategic choices that close the gap between RTO target and current capability.

## 2 · Strategy options per resource type

### 2.1 Workplace
| Option | Cost | Recovery | Best for |
|---|---|---|---|
| Reciprocal arrangement (sister branch) | Low | Hours | Branch + back-office |
| Workplace recovery contract (e.g. Sungard equivalent) | Mid | Hours | Trading desks |
| Permanent secondary site | High | Minutes | Treasury, dealing |
| Work-from-home | Low | Same day | Most office work |
| Mobile workforce | Mid | Hours | Field staff |

### 2.2 Technology — IT
| Option | Cost | RTO | RPO | Best for |
|---|---|---|---|---|
| Cold site | Low | Days | Days | Non-critical |
| Warm site | Mid | Hours | Hours | TIER_2 |
| Hot site (active-passive) | High | Minutes | Minutes | TIER_1 |
| Active-active (multi-region) | Very high | Seconds | Seconds | Customer-facing TIER_1 |
| Cloud failover (AWS Bahrain) | Mid | Minutes | Minutes | Variable load |
| Vendor SaaS DR | Vendor-dependent | Per vendor SLA | Per vendor SLA | SaaS |

### 2.3 Technology — OT (refinery, SCADA, ATM switch)
OT has different semantics from IT:
- Replicating an entire DCS is rarely feasible
- Strategies focus on **graceful degradation**, **safe shutdown**, **manual operation**
- NCA OTCC Chapter 7 mandates OT-specific BCP

| OT strategy | Description |
|---|---|
| Local autonomy | Field controllers continue locally if DCS link drops |
| Safe shutdown | Pre-configured shutdown sequence on detected anomaly |
| Manual operation | Operator runs by panel; reduced throughput |
| Geographic redundancy | DCS pair in two locations (rare, capital-intensive) |
| Vendor depot | Cold-spare PLCs + I/O cards at vendor depot |

### 2.4 Information & records
- Vital records inventory (annual)
- Encrypted at rest + in transit (always)
- Backup tiers: hot replication + daily snapshot + weekly archive + immutable (ransomware-proof)
- Backup vault off-site (Jeddah → Riyadh or AWS Bahrain)
- Restore tested quarterly (sample)

### 2.5 Suppliers & vendors
- Critical-vendor BCP attestation (annual)
- Alternate-vendor pre-qualification for TIER_1 dependencies
- Vendor exit playbook activation criteria
- Fourth-party exposure mapping (SCM page)

### 2.6 People
- Cross-training (no single point of skill)
- Succession plans for L1 + L2 roles
- Surge capacity (contract + retired-employee call-back)
- Remote-work enablement
- Mental health + welfare during prolonged incident

## 3 · Strategy ROI evaluation

For each TIER_1 process where current strategy doesn't meet RTO target:

```
                Annual Loss Expectancy (no strategy)
                = Likelihood × Financial Impact at MTPD
                ─────────────────────────────────────
                          minus
                ─────────────────────────────────────
                Annualised cost of strategy
                + Annualised residual loss expectancy
```

If ALE reduction > strategy cost → invest. Otherwise document risk acceptance.

## 4 · Saudi-specific strategy considerations

### 4.1 Peak-season capacity
SADAD / MADA / Same-day settlement spike during Hajj + Ramadan. Strategy must include burst capacity (cloud + temporary staff + extended hours).

### 4.2 Eastern Province coastal sites
Sea-level rise + sandstorm frequency increasing. Long-term: relocate critical workloads to Riyadh / Jeddah inland.

### 4.3 OT in refining + transmission
NCA OTCC Chapter 7 applies. OT BCM = different document set (`bcm.ot_continuity_plans` once Slice 7-G ships).

### 4.4 Cross-border DR
AWS me-south-1 is in Bahrain. Routing data outside KSA needs SDAIA PDPL Art. 29 compliance — SCC or adequacy decision + DPIA addendum.

### 4.5 Vision 2030 alignment
Public-sector BCM via DGA aligns to Vision 2030. Saudi banks supporting government services need DGA-aligned BCM as well.

## 5 · Strategy approval

Each strategy proposal includes:
1. Current state (gap from BIA)
2. Options analysed (do-nothing + at least 2 alternatives)
3. Recommendation + rationale
4. Cost (capital + operating)
5. Risk-acceptance position if not adopted
6. Implementation roadmap (3-year max)

Board Risk Committee approves at annual review. Multi-year roadmap tracked in `bcm.strategic_roadmap` (Slice 7-A).

## 6 · Strategy refresh triggers
- Annual review
- BIA changes (new MTPD, new dependency)
- New product / M&A
- Vendor change (critical TIER)
- Major incident reveals gap
- Regulator circular (SAMA / NCA / NRC)
- Climate event (e.g. coastal site assessment)
