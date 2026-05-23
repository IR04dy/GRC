# 19 · Lessons Learned / AAR Template
**Document owner:** BCM Manager · **Cadence:** Within 48h of all-clear OR within 1 week of exercise · **Backing entity:** `bcm.lessons_learned`, `bcm.crisis_aar`

> Every real incident + every exercise produces an AAR. Capture once, distil patterns over time. The library of lessons is one of the BCM programme's most valuable assets.

---

## A · Incident / exercise summary

| Field | Value |
|---|---|
| Report code | `AAR-[FILL]` |
| Type | ☐ Real incident  ☐ Exercise (TT/WT/FN/FS/SIM/CT) |
| Linked event ID | `bcm.crisis_events.id = [FILL]` OR `bcm.bcm_exercises.id = [FILL]` |
| Date / time start (Asia/Riyadh) | [FILL] |
| Date / time all-clear | [FILL] |
| Total duration | [FILL] |
| Severity declared | [LOW/MEDIUM/HIGH/CRITICAL] |
| Plans activated | [BCP-XXX, DRP-XXX] |
| Crisis Team Lead | [FILL] |
| AAR author | [FILL] |
| AAR status | [DRAFT / UNDER_REVIEW / APPROVED / PUBLISHED] |

## B · Executive summary (EN, 5-7 lines)
[FILL]

## C · Executive summary (AR, 5-7 lines)
[FILL]

## D · Timeline (key events)

| Time (Asia/Riyadh) | Event | Decision | Actor |
|---|---|---|---|
| HH:MM | [Awareness] | n/a | SOC analyst |
| HH:MM | [Declaration] | Severity HIGH declared | CRO |
| HH:MM | [Comms cascade started] | L1 + L2 notified | BCM Mgr |
| HH:MM | [BCP activated] | T24 failover initiated | IT Ops |
| HH:MM | [Regulator notification] | SAMA 6h notification sent at T+3:45 | DPO + GC |
| HH:MM | [Recovery validated] | All synthetic transactions passing | Plan Owner |
| HH:MM | [All-clear] | Crisis closed | CEO |

## E · Decisions log

| Time | Decision | Made by | Rationale | Outcome |
|---|---|---|---|---|
| HH:MM | [FILL] | [Role] | [FILL] | [FILL] |

## F · Comms log

| Time | Audience | Channel | Sent by | Message gist |
|---|---|---|---|---|
| HH:MM | Internal staff | Email + Teams | HR Lead | Initial declaration |
| HH:MM | Impacted customers | App push + SMS | Comms Lead | Service interruption notice |
| HH:MM | SAMA | Email + phone | GC + DPO | Initial 6h notification |

## G · What worked well
- [FILL — Be specific. "Comms tree reached all L1 in 8 minutes vs 15-min target."]
- [FILL]
- [FILL]

## H · What failed
- [FILL — Be honest. "Failover took 4h vs 2h target — replication lag from yesterday's patch."]
- [FILL]
- [FILL]

## I · Root-cause analysis

For each item in §H:

| Item | Immediate cause | Underlying cause | Systemic cause |
|---|---|---|---|
| [FILL gap] | [Direct trigger] | [Why it happened] | [Why our system allowed it] |

## J · Recommendations

| ID | Recommendation | Owner | Target date | CAPA ID |
|---|---|---|---|---|
| R-1 | [FILL — specific action] | [FILL role] | [Date] | `[capa_id]` |
| R-2 | [FILL] | [FILL] | [Date] | |
| R-3 | [FILL] | [FILL] | [Date] | |

Each row creates a `bcm.corrective_actions` row.

## K · Plan / procedure updates needed

| Document | Section | Change | Owner |
|---|---|---|---|
| BCP-XXX | §5.3 | [FILL — specific edit] | Plan Owner |
| DRP-YYY | §3.2 | [FILL] | IT Ops |

## L · Lessons for the knowledge base

Capture for cross-referencing future incidents:

| Tag | Lesson | Re-usable? |
|---|---|---|
| `cyber.ransomware` | [FILL] | Yes |
| `payment.peak-season` | [FILL] | Yes |
| `vendor.failover` | [FILL] | Yes |

Stored in `bcm.lessons_learned` with tag index for similar-incident detection.

## M · Regulator engagement summary

| Regulator | Notification time | Follow-up cadence | Closure submitted | Findings |
|---|---|---|---|---|
| SAMA | T+5:30 | Every 24h | Yes T+6d | None / [FILL] |
| NCA | T+18h | n/a | Yes T+3d | [FILL] |
| SDAIA | T+72h-2h | n/a | Yes T+5d | [FILL] |
| NRC | n/a | n/a | n/a | n/a |
| CMA | Same day | n/a | n/a | n/a |

## N · Financial impact

| Category | SAR |
|---|---|
| Direct revenue loss | [FILL] |
| Recovery cost (vendor + overtime + travel) | [FILL] |
| Customer compensation | [FILL] |
| Regulatory fine (if any) | [FILL] |
| Legal / consulting | [FILL] |
| **Total** | **[FILL]** |

## O · Approvals

| Role | Name | Signed | Date |
|---|---|---|---|
| AAR Author | [FILL] | ☐ | [FILL] |
| BCM Manager | [FILL] | ☐ | [FILL] |
| Plan Owner | [FILL] | ☐ | [FILL] |
| CRO | [FILL] | ☐ | [FILL] |
| CEO (if CRITICAL) | [FILL] | ☐ | [FILL] |

## P · Distribution

| Audience | Format | Distributed |
|---|---|---|
| BCM team | This document + raw timeline | ☐ |
| Crisis Team | Document | ☐ |
| Risk Committee | Slide summary | ☐ |
| Board RC (CRITICAL only) | Slide summary | ☐ |
| Internal audit | Read-only | ☐ |
| External regulator (on request) | Sanitised PDF | ☐ |

## Q · Status flow

DRAFT → UNDER_REVIEW (RACI reviewers) → APPROVED (CRO/CEO sign) → PUBLISHED (distributed).
Backing: `bcm.crisis_aar.status` with state-machine validation.
