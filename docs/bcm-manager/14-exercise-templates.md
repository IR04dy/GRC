# 14 · Exercise Templates

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** Exercise Lead · **Backing entity:** `bcm.bcm_exercises`, `bcm.exercise_scenarios`

> One template per exercise type. Fill in for each instance and archive.

---

## 14.A · Tabletop exercise template

| Field | Value |
|---|---|
| Exercise code | `EX-TT-[FILL]` |
| Date / time | [FILL] |
| Scenario | [Link from §3 of 13-exercise-strategy.md] |
| Duration | 2-4h |
| Objectives | [FILL — specific, measurable] |
| Attendees | Crisis Team + plan owners + observers |
| Facilitator | [FILL] |
| Location | War room / virtual |

### Agenda
1. Briefing (15 min) — scenario set-up
2. Inject 1 (15 min) — first event
3. Inject 2 (30 min) — escalation
4. Inject 3 (30 min) — complication (e.g. regulator inquiry)
5. Decision phase (30 min) — Crisis Team deliberates
6. Wrap + observations (30 min)

### Observation form (per observer)
- Decisions made on time? Yes/No
- Comms cascade complete? Yes/No
- Regulator-notification SLA met? Yes/No
- Plan procedure followed? % deviation
- Recommendations: [FILL]

### Outputs
- AAR within 48h
- Lessons → CAPA in `bcm.corrective_actions`
- Plan refresh if procedure gap

---

## 14.B · Walkthrough exercise template

| Field | Value |
|---|---|
| Exercise code | `EX-WT-[FILL]` |
| Date | [FILL] |
| Duration | 1-2h |
| Plan covered | [BCP-XXX] |
| Walkthrough leader | Plan Owner |

### Procedure
1. Read aloud each step of the plan
2. Identify gaps, ambiguity, outdated references
3. Flag for update

### Output
- Annotated plan with updates needed

---

## 14.C · Functional exercise template

| Field | Value |
|---|---|
| Exercise code | `EX-FN-[FILL]` |
| Date | [FILL] |
| Duration | 4-8h |
| System under test | [FILL — e.g. T24 DR failover] |
| Disruption window | [FILL — pre-approved with CAB] |
| Rollback procedure | [FILL] |

### Procedure
1. Pre-test verification (DR site warm, replication lag low)
2. Failover execution per DRP
3. Validation (synthetic transactions, monitoring)
4. Failback
5. Post-test verification

### Success criteria
- RTO actual ≤ RTO target × 110%
- RPO actual ≤ RPO target
- Zero data loss
- All validation steps pass

---

## 14.D · Full-scale exercise template

| Field | Value |
|---|---|
| Exercise code | `EX-FS-[FILL]` |
| Date | [FILL — 6-month lead time] |
| Duration | 8-24h |
| Scope | End-to-end TIER_1 process |
| Crisis Team activated | Yes (real activation) |
| Customer impact window | [FILL — minimised, communicated] |

### Pre-exercise
- Board approval to disrupt customer-facing services
- Customer-facing comms scheduled
- Regulator courtesy notification
- All Crisis Team availability confirmed
- Rollback authority + procedure

### Exercise
- Real declaration (within boundary of exercise frame)
- Real Crisis Team convene
- Real plan execution
- Real failover (technical) or real activation (operational)
- Real customer-facing comms (clearly marked "exercise")

### Success criteria
- Plan executed per procedure
- All TIER_1 process functions running on DR
- RTO/RPO met
- No customer-data integrity issues
- Crisis Team coordinated effectively

### Post-exercise
- AAR within 1 week (heavier than tabletop)
- Lessons captured at programme level
- Board pack mention next quarter

---

## 14.E · Simulation template (table-based, no live disruption)

| Field | Value |
|---|---|
| Exercise code | `EX-SIM-[FILL]` |
| Tool | Pen + paper / dedicated sim software |
| Scenario | Multi-event / cyber attack tree |
| Duration | 4h |

### Outputs
- Multi-event decision-tree walk-through
- Coordination gaps identified
- Cross-team handoff points tested

---

## 14.F · Comms-tree test template

| Field | Value |
|---|---|
| Exercise code | `EX-CT-[FILL]` |
| Cadence | Monthly |
| Duration | 30 min |
| Scope | All L1 + L2 contacts |

### Procedure
1. BCM Manager initiates cascade (test only — clearly marked)
2. L1 acknowledges within 15 min
3. L1 cascades to their L2 reports
4. L2 acknowledges within 30 min
5. Record success/failure per contact in `bcm.communication_contacts.last_test_result`

### Failure response
- 1 failed contact → check + retry within 24h
- 2+ failed contacts in same test → CAPA + contact-tree refresh
