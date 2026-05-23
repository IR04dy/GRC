# 15 · Annual BCM Review Procedure
**Document owner:** BCM Manager · **Approver:** Board Risk Committee · **Backing entity:** `bcm.management_reviews`, `bcm.assessment_cycles`

---

## 1 · Cadence

Q3 of each fiscal year. Closes the loop on the prior year + sets next-year programme. ISO 22301 §9.3 management-review requirement.

## 2 · Inputs

| Input | Source |
|---|---|
| Year's exercise outcomes | `bcm.bcm_exercises` |
| Real incidents + AARs | `bcm.crisis_aar`, `bcm.crisis_events` |
| KRI breach history | `risk_lc.kri_breaches` |
| BIA refresh status | `bcm.bia_records` (count of BIAs by review-due status) |
| BCP refresh status | `bcm.bcp_plans` (count by review-due + version age) |
| CAPA pipeline | `bcm.corrective_actions`, `audit_lc.capa_tasks` |
| Vendor BCP attestation status | `scm.vendor_assessments` (BCP attestation domain) |
| Regulator-notification log | `bcm.crisis_regulatory_notifications` |
| Internal audit BCM findings | `audit_lc.findings` (filter: BCM-tagged) |
| External BCM review findings | (biennial — included in alternate years) |
| Maturity assessment score | `bcm.maturity_assessments` (Slice 7-B target) |
| Strategic roadmap progress | `bcm.strategic_roadmap` (Slice 7-A target) |
| Budget actuals vs plan | Finance |

## 3 · Agenda (4h management meeting)

| Time | Topic | Output |
|---|---|---|
| 0:00-0:15 | Year in review — what went well, what didn't | Narrative summary |
| 0:15-0:45 | Exercise outcomes + lessons | Top 5 themes |
| 0:45-1:15 | Real incidents + AAR consolidation | Incident map + heatmap |
| 1:15-1:45 | Risk register changes — new BCM risks | Risk register Δ |
| 1:45-2:15 | Maturity score vs prior year | ISO 22301 maturity chart |
| 2:15-2:45 | Programme objectives next year | Top 5 objectives |
| 2:45-3:15 | Strategy gaps + investment recommendations | Investment list |
| 3:15-3:45 | Regulator posture (SAMA, NCA, SDAIA, NRC, CMA) | Compliance heatmap |
| 3:45-4:00 | Decisions + sign-off | Decisions log |

## 4 · Decisions to capture

- Programme objectives for next FY
- Maturity-progression target
- Top-10 BCM risk register changes
- Strategic investment proposals (carry to budget)
- Exercise calendar next FY
- Regulatory posture changes (e.g. new SAMA circular response)
- Personnel / RACI changes
- Plan templates / methodology changes
- Vendor BCP campaign decisions

## 5 · Outputs

| Output | Audience | Format | Storage |
|---|---|---|---|
| Annual review minutes | BCM Steering + CRO | PDF, signed | `bcm.management_reviews` |
| Updated 3-year strategic roadmap | Board RC | Gantt + narrative | `bcm.strategic_roadmap` |
| Next FY objectives | All BCM stakeholders | List | Settings doc |
| Investment proposals | CFO | Costed list | Budget folder |
| Board report | Board Risk Committee | Slide deck | Board pack |

## 6 · Pre-review checklist

T-30 days:
- [ ] All BIAs reviewed
- [ ] All BCPs review status pulled
- [ ] All exercise outcomes captured
- [ ] All AARs filed
- [ ] All open CAPAs aged
- [ ] Vendor BCP attestation refresh complete
- [ ] Maturity assessment scored

T-14 days:
- [ ] Inputs document compiled (all sources above)
- [ ] Distributed to attendees

T-7 days:
- [ ] Attendee preparation calls
- [ ] Investment proposals drafted

## 7 · Post-review

T+7 days:
- [ ] Minutes circulated and acknowledged
- [ ] Decisions logged in platform
- [ ] CAPA owners assigned for any items requiring action

T+30 days:
- [ ] Next-FY objectives published to all BCM stakeholders
- [ ] Budget submission for next FY (with CFO)

## 8 · Approval

Annual review minutes signed by:
- BCM Manager (drafted)
- CRO (reviewed)
- CEO (approved)
- Board Risk Committee Chair (endorsed)
