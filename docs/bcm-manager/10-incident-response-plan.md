# 10 · Incident Response Plan (IRP)
**Document owner:** BCM Manager + CISO · **Backing entity:** `bcm.crisis_events`, `cyber.cyber_incidents`

> Integrated with cyber IR. Same plan for both worlds — different sub-procedures.

---

## 1 · Scope
This IRP applies to any incident with **business-continuity implications**:
- Cyber incidents affecting TIER_1 systems
- Operational incidents (outages, degradations) exceeding RTO
- Vendor-driven incidents (critical vendor down)
- Physical incidents (site evacuation, utility loss)
- Regulatory incidents (PDPL breach, SAMA-reportable cyber)

## 2 · Incident lifecycle

```
DETECT  →  DECLARE  →  RESPOND  →  RECOVER  →  REVIEW
  ↓         ↓           ↓           ↓           ↓
SOC/Ops  Plan      Crisis Team  Plan Owner   AAR
          Owner    + IT Ops     + Comms      writer
                   + Comms
```

### 2.1 Detect
Sources:
- SOC / SIEM (Splunk) alert
- ITSM (ServiceNow) ticket trending
- Customer complaint surge
- Regulator inquiry
- Internal employee report
- Vendor notification
- Press / social-media mention

### 2.2 Declare
Authority to declare: BCM Manager + CRO (HIGH+); CEO (CRITICAL).
Form: [Section 9 of 08-bcp-template.md](08-bcp-template.md#section-9-·-declaration-form)

### 2.3 Respond
1. Convene Crisis Team within 15 min of HIGH+ declaration
2. Open situation log (`bcm.crisis_situation_reports`)
3. Internal cascade per [11-crisis-communications-plan.md](11-crisis-communications-plan.md)
4. Activate relevant BCP / DRP
5. Pre-arm regulator notification
6. Decision log (`bcm.crisis_decisions`) — every key decision time-stamped

### 2.4 Recover
- Plan Owner executes BCP recovery procedure
- IT Ops executes DRP (if technical)
- Recovery validated per BCP §5.4
- All-clear declared by same authority that declared

### 2.5 Review
- AAR within 48h of all-clear (long enough to be reflective, short enough to be accurate)
- CAPA opened for every "what failed" in `bcm.corrective_actions`
- Lessons-learned recorded (`bcm.lessons_learned`)
- AAR distributed to Risk Committee + relevant boards

## 3 · Severity matrix → response intensity

| Severity | Crisis Team | Comms | Regulator | Sitrep cadence |
|---|---|---|---|---|
| CRITICAL | CEO chairs in person | All audiences | All per SLA | Every 1h |
| HIGH | CRO chairs | Internal + impacted customer + regulator | Per applicable SLA | Every 2h |
| MEDIUM | BCM Mgr chairs | Internal + ExCo | Only if PDPL/CMA | Every 4h |
| LOW | Plan Owner runs | Affected team only | None | Daily |

## 4 · Special incident types

### 4.1 Cyber incident → BCM bridge
When SOC declares a cyber incident affecting a TIER_1 system, automatic BCM evaluation:
- Within 15 min: assess BC implication
- If unrecoverable in < RTO: declare BC crisis in parallel
- SAMA 6h notification countdown starts at first awareness

`cyber_breach_bridge_log` records the bridge events.

### 4.2 PDPL breach → BCM + DPO
Personal-data breach automatically engages DPO:
- DPO assesses notification obligation (PDPL Art. 20-21)
- BCM Manager assesses business-continuity impact
- SDAIA 72h countdown starts at confirmation

### 4.3 OT incident (refinery / pipeline / ATM switch)
- Safety officer + OT Lead engaged first
- BCM/IT response coordinates after safety confirmed
- NCA OTCC reportable per Chapter 7
- Manual operation may run in parallel with recovery

### 4.4 Vendor outage
- Vendor TAM contacted immediately
- Vendor SLA assessed for breach
- If exit-plan invocation criteria met → SCM team activates alternate

## 5 · Integration with platform

| Event | Platform action |
|---|---|
| Crisis declared | `bcm.crisis_events` row created |
| Severity set | Auto-creates sitrep cadence reminder |
| Decision logged | `bcm.crisis_decisions` |
| Regulator notification due | `bcm.crisis_regulatory_notifications` countdown timer (Slice 7-E target) |
| BCP activated | `bcm.bcp_plans.activation_status = ACTIVE` |
| All-clear | Triggers AAR workflow + CAPA scaffolding |

## 6 · Multi-incident handling
During a CRITICAL incident a second MEDIUM event may occur. The Crisis Team:
- Logs both incidents
- Assigns separate sub-leads
- BCM Manager arbitrates resource conflicts
- Severity escalation rules still apply per-incident

## 7 · Cross-tenant / cross-sector
For sector-wide events (SAMA pushing all banks to fail over to backup network):
- NRC coordination per critical-infrastructure protocols
- Sanitised sitreps shared with NRC
- Cross-bank coordination via SAMA-led call

## 8 · Common pitfalls
- Late declaration (stalling regulator SLA countdown)
- Insufficient L1 contacts available outside business hours
- Sitreps too long → exec attention fatigues; keep to 1 page
- Decisions not logged → AAR forensics gap
- All-clear declared prematurely (residual issues emerge)
- Comms cascade has gaps (HR not notified, customer-facing apps not updated)
