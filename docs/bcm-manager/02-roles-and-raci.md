# 02 · Roles & RACI

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** BCM Manager · **Cadence:** Annual refresh + on-trigger (org change, M&A)

R = Responsible · A = Accountable · C = Consulted · I = Informed

---

## 1 · Roles

| Role | Title | RACI lead |
|---|---|---|
| **Board RC** | Board Risk Committee | A on policy + budget |
| **CEO** | CEO | A on crisis declarations + regulator-facing posture |
| **CRO** | Chief Risk Officer | A on BCM programme |
| **CISO** | CISO | A on cyber-induced BCM events |
| **COO** | Chief Operating Officer | A on business-process continuity |
| **CFO** | CFO | C on financial-impact validation, A on budget |
| **DPO** | Data Protection Officer | A on PDPL breach notification |
| **GC** | General Counsel | A on regulator filings + legal exposure |
| **BCM Mgr** | BCM Manager | A on day-to-day BCM execution |
| **BIA-Own** | BIA Owner (per process) | R on BIA accuracy + review |
| **BCP-Own** | BCP Owner (per plan) | R on plan currency + acknowledgement |
| **Crisis Lead** | Crisis Steering Committee Chair (CEO during major) | A during crisis |
| **Comms Lead** | Head of Corporate Comms | R on external comms during crisis |
| **HR Lead** | Head of HR | R on staff impacts, mass notification |
| **IT Ops** | Head of IT Operations | R on DRP execution |
| **OT Lead** | Head of OT / Engineering | R on OT continuity (refinery, ATM switch, etc.) |
| **Vendor Mgr** | Head of Vendor Management | R on critical-vendor BCP attestation |
| **IA** | Head of Internal Audit | I on BCM, R on BCM audit engagements |
| **3rd-party** | External BCM Reviewer | C every 3 years (Grant Thornton / PKF) |

---

## 2 · RACI matrix per BCM activity

### 2.1 Programme governance

| Activity | Board RC | CEO | CRO | BCM Mgr | IA |
|---|---|---|---|---|---|
| Annual policy approval | **A** | C | R | C | I |
| Annual budget approval | **A** | C | R | R | I |
| Quarterly dashboard review | **A** | I | C | R | I |
| 3-year strategic roadmap | C | A | **R** | R | I |
| ISO 22301 maturity assessment | I | C | A | **R** | C |

### 2.2 Business Impact Analysis (BIA)

| Activity | CRO | BCM Mgr | BIA Owner | CFO | Process User |
|---|---|---|---|---|---|
| Define BIA methodology | A | **R** | C | C | I |
| Conduct BIA (per process) | A | C | **R** | C | C |
| Set / approve RTO/RPO/MTPD | A | C | R | **R** (sign-off) | C |
| Annual BIA review | A | **R** | R | I | I |
| Dependency mapping | C | **R** | R | I | C |

### 2.3 Risk Assessment

| Activity | CRO | BCM Mgr | Risk Mgmt | CISO | CFO |
|---|---|---|---|---|---|
| Threat catalogue maintenance | A | C | **R** | C | I |
| BCM risk register | A | **R** | C | C | I |
| KRI thresholds set | A | **R** | C | C | C |
| Quarterly heatmap to RC | **A** | R | C | I | I |
| Risk treatment plans | A | R | **R** | C | C |

### 2.4 Plans & Procedures

| Activity | BCM Mgr | BCP Owner | IT Ops | OT Lead | Comms Lead |
|---|---|---|---|---|---|
| BCP template + standards | **A/R** | I | I | I | I |
| Per-process BCP draft | A | **R** | C | C | C |
| Per-process BCP approval | A | R | I | I | I |
| Annual BCP review | A | **R** | I | I | I |
| DRP authorship | A | C | **R** | I | I |
| OT continuity plan | A | I | C | **R** | I |
| Crisis comms plan | A | I | I | I | **R** |
| Plan acknowledgement campaign | **R** | C | I | I | I |

### 2.5 Exercises & Testing

| Activity | BCM Mgr | BCP Owner | IT Ops | Exercise Lead | Observers |
|---|---|---|---|---|---|
| Annual exercise calendar | **A/R** | C | C | R | I |
| Exercise scenario design | A | C | C | **R** | C |
| Exercise execution (tabletop) | A | C | I | **R** | C |
| Exercise execution (DR drill) | A | C | **R** | R | C |
| Exercise execution (full-scale) | **A** | R | R | R | C |
| Lessons learned + CAPA | A | **R** | C | C | I |

### 2.6 Crisis Response (declared crisis)

| Activity | CEO | CRO | BCM Mgr | CISO | DPO | Comms Lead | GC |
|---|---|---|---|---|---|---|---|
| Crisis declaration | **A** | C | R | C | C | I | C |
| Activate Crisis Steering Cmte | A | **R** | R | I | I | I | I |
| Declare severity (LOW/MED/HIGH/CRIT) | A | R | **R** | C | C | I | C |
| BCP activation | A | C | **R** | I | I | C | I |
| Internal comms cascade | A | C | C | I | I | **R** | I |
| Customer comms | A | C | C | I | I | **R** | C |
| Regulator notification (SAMA 6h) | **A** | C | C | C | C | I | R |
| Regulator notification (SDAIA PDPL 72h) | A | C | C | C | **R** | I | C |
| Regulator notification (NRC 72h) | **A** | R | C | C | C | I | C |
| Decision log maintenance | A | C | **R** | C | C | C | C |
| Sitrep cadence (every 2h major) | A | C | **R** | C | C | C | C |
| All-clear declaration | **A** | R | R | C | C | I | I |
| After-Action Review (AAR) | A | R | **R** | C | C | C | C |

### 2.7 Vendor & supply-chain

| Activity | BCM Mgr | Vendor Mgr | CISO | Procurement |
|---|---|---|---|---|
| Critical-vendor BCP attestation campaign | **A** | R | C | C |
| Vendor BCP scoring | A | **R** | C | I |
| Alternate vendor identification | C | **R** | C | A |
| Vendor exit playbook | A | **R** | C | A |
| Fourth-party exposure mapping | A | **R** | C | I |

### 2.8 Reporting

| Activity | BCM Mgr | CRO | CEO | IA | External Reviewer |
|---|---|---|---|---|---|
| Quarterly board pack BCM section | **R** | A | I | I | I |
| Annual management review | R | **A** | C | C | I |
| Regulator filings (SAMA) | R | A | **A** | I | I |
| IA BCM audit engagement | C | A | I | **R** | I |
| External BCM review (biennial) | C | A | I | C | **R** |

---

## 3 · Escalation chain (per severity)

| Severity | Notify L1 | Notify L2 | Notify L3 |
|---|---|---|---|
| **CRITICAL** | CEO + CRO + BCM Mgr | All ExCo + Board Chair | Regulators per SLA |
| **HIGH** | CRO + BCM Mgr + relevant C-suite | ExCo at next morning meeting | Board next quarter |
| **MEDIUM** | BCM Mgr + relevant ExCo | Board next quarter | n/a |
| **LOW** | BCM Mgr + relevant manager | Captured in next monthly steering | n/a |

L1 contacts live in `bcm.communication_contacts` with `escalation_level=1` filter.

---

## 4 · Backup / coverage

Every L1 role has a primary + backup contact in `bcm.communication_contacts`. No single person is irreplaceable. Annual contact-tree test verifies all backups are reachable.

---

## 5 · Acknowledgement

Every named individual signs annual acknowledgement of this RACI via the platform (`bcm.plan_review_attestations`).
