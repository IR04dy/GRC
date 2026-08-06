# 09 · DRP Template (Disaster Recovery Plan)

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** IT Ops Lead / OT Lead · **Backing entity:** `bcm.dr_plans`

> Two variants: **IT-DRP** (general IT systems) and **OT-DRP** (refinery DCS, pipeline SCADA, ATM switch, payment switch). Use the variant headers below.

---

## SECTION 1 · Identification

| Field | Value |
|---|---|
| DRP Code | `DRP-[FILL]` |
| Variant | ☐ IT  ☐ OT |
| System covered | [FILL] |
| Linked BCP(s) | [Link to BCP-XXX] |
| Linked BIA(s) | [Link to BIA-XXX] |
| RTO target | [from BIA] |
| RPO target | [from BIA] |
| Owner | [FILL] |
| Effective Date | [FILL] |
| Next Review | [FILL annually] |

## SECTION 2 · Architecture overview

### 2.1 Primary site
- Location: [FILL]
- Components: [FILL]
- Network topology: [FILL diagram link]

### 2.2 DR / failover target
- Location: [FILL — Riyadh DR / Jeddah DR / AWS Bahrain / Vendor cloud]
- Mode: ☐ Active-active  ☐ Active-passive (hot)  ☐ Warm  ☐ Cold
- Replication: ☐ Sync  ☐ Async (lag: [FILL])
- Failover trigger: ☐ Manual  ☐ Automatic (criteria: [FILL])
- Failback procedure: [FILL link]

### 2.3 Dependencies (this DRP needs)
| Dependency | Type | DR-ready? |
|---|---|---|
| Identity (AD, IAM) | Auth | ☐ |
| Network connectivity (MPLS, VPN, internet) | Network | ☐ |
| DNS | Network | ☐ |
| Storage (SAN, S3) | Data | ☐ |
| Backup vault | Data | ☐ |
| Monitoring (Splunk, etc.) | Observability | ☐ |
| Key vault (HSM, KMS) | Crypto | ☐ |

## SECTION 3 · Recovery procedure

### 3.1 Pre-conditions (verified by check-list)
- [ ] DR site health-check green
- [ ] Replication lag < threshold ([FILL])
- [ ] DR credentials available
- [ ] Network reachability confirmed
- [ ] Vendor support engaged (if applicable)
- [ ] Change advisory bypass authorised (emergency CAB)

### 3.2 Steps (numbered, atomic)
1. [FILL — e.g. "Stop application services on primary"]
   - Command / runbook link: [FILL]
   - Expected duration: [FILL]
   - Success criteria: [FILL]
2. [FILL]
3. [FILL]
...

### 3.3 Validation
After failover, validate:
- [ ] Synthetic transaction succeeds
- [ ] User login (sample of 5)
- [ ] Monitoring dashboards green
- [ ] Critical integrations (SADAD, MADA, etc.) responsive
- [ ] Data integrity check (sample query against control set)

### 3.4 Failback (after primary site recovered)
1. [FILL]
2. [FILL]

## SECTION 4 · OT-DRP specifics (delete this section if IT variant)

### 4.1 Safety-first principle
Any DR action that could endanger personnel or environment **must** be aborted. OT recovery prioritises **safe shutdown** over throughput.

### 4.2 Process-control state
- Current operating state at failover: [Auto / Manual / Safe shutdown]
- Hand-off to manual operation procedure: [FILL]
- Manual operator competency requirements: [FILL]

### 4.3 Field-controller autonomy
- Local PLC runs even if DCS unavailable: ☐ Yes  ☐ No
- Local-control capability duration: [FILL hours]
- Reconnection procedure: [FILL]

### 4.4 NCA OTCC alignment
This DRP demonstrates compliance with NCA OTCC Chapter 7 (OT continuity):
- §7.1 Risk-based identification of critical OT assets
- §7.2 OT-specific recovery procedures
- §7.3 OT exercise programme
- §7.4 OT vendor coordination

### 4.5 Refinery / pipeline specifics (Aramco-tier)
- DCS pair status: [FILL — Honeywell Experion / GE iFix / etc.]
- Historian (OSIsoft PI) status: [FILL]
- Pipeline SCADA control room location: [FILL]
- Manual operation throughput: [FILL % of normal]

## SECTION 5 · Resource requirements

### 5.1 People
| Role | Required during DR | On-call SLA |
|---|---|---|
| DR Coordinator | 24/7 | 15 min |
| Senior DBA | 12h shifts | 30 min |
| Network Engineer | On-call | 30 min |
| Application Owner | On-call | 30 min |
| OT Engineer (if OT) | 24/7 | 15 min |

### 5.2 Infrastructure budget
- Annual recurring cost: SAR [FILL]
- Per-failover cost (cloud burst, vendor support): SAR [FILL]

## SECTION 6 · Exercise + test schedule

| Test type | Cadence | Last test | Result |
|---|---|---|---|
| Replication monitoring | Continuous | [Date] | ☐ Pass ☐ Fail |
| Tabletop walkthrough | Quarterly | [Date] | [Result] |
| Restore-from-backup sample | Quarterly | [Date] | [Result] |
| Full failover drill | Annually | [Date] | [Result] |
| Failback drill | Annually | [Date] | [Result] |

OT-DRP additional:
| OT-specific test | Cadence | Last test | Result |
|---|---|---|---|
| Manual operation drill | Bi-annual | | |
| Safe-shutdown drill | Annual | | |
| Field-controller autonomy verification | Annual | | |

## SECTION 7 · Vendor coordination

| Vendor | Service | Escalation contact | Activation procedure |
|---|---|---|---|
| [FILL — e.g. AWS Enterprise Support] | Cloud burst capacity | [FILL] | [FILL — TAM ticket priority CRIT] |
| [FILL — e.g. Honeywell] | DCS support | [FILL] | [FILL] |

## SECTION 8 · Acknowledgements
Owners sign annually via `bcm.plan_acknowledgements`.

## SECTION 9 · Change log
| Version | Date | Author | Summary |
|---|---|---|---|
| 1.0 | [FILL] | [FILL] | Initial DRP |
