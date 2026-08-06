# 17 · Vendor BCP Attestation Procedure

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** Vendor Mgr + BCM Manager · **Cadence:** Annual campaign (Q2 default) · **Backing entity:** `scm.vendor_assessments`, `scm.vendors`, `scm.exit_plans`

---

## 1 · Purpose
Verify that every critical vendor has, exercises, and maintains a Business Continuity Plan that meets the bank's standards. SAMA Outsourcing Framework §5 requirement. Without this, a vendor outage = bank outage.

## 2 · Scope

### 2.1 In-scope vendors (critical = TIER_1 + TIER_2)
- Hosting + cloud (AWS, Azure, STC Cloud)
- Core banking SI (Temenos / T24 support)
- Payment processors (Way4, Ericsson Money, SADAD operator)
- Card-personalisation bureau (HSM operator)
- Security operations (Crowdstrike, Splunk MSSP)
- Identity (Microsoft, Okta)
- Treasury (Murex support)
- KYC/AML engines (Actimize)
- Document management (OpenText)
- Telco backbone (STC, Mobily — for OTP delivery)

### 2.2 Out-of-scope
- Office-supplies vendors
- Hospitality / catering
- Non-critical SaaS (< TIER_2)

## 3 · Annual campaign workflow

| Phase | Timing | Owner | Action |
|---|---|---|---|
| **Plan** | Q1 | Vendor Mgr | Identify critical-vendor list + add new vendors |
| **Launch** | Q2 W1 | Vendor Mgr | Email each vendor: attestation questionnaire + due date (Q2 W6) |
| **Reminder** | Q2 W3 | Vendor Mgr (auto) | Reminder to non-responders |
| **Escalate** | Q2 W5 | Vendor Mgr | Phone-escalate non-responders + vendor's account executive |
| **Score** | Q2 W7 | BCM Mgr + Vendor Mgr | Score each response against rubric (§4) |
| **Review** | Q2 W8 | BCM Mgr | Meeting per critical vendor — discuss gaps |
| **Decision** | Q3 W1 | CRO | Risk acceptance / mitigation / exit decision per gap |
| **Report** | Q3 W2 | BCM Mgr | Board RC report on vendor BCP posture |

## 4 · Scoring rubric (per vendor — out of 100)

| Domain | Weight | Sample scoring |
|---|---|---|
| **BCP existence + currency** | 15 | 15 = signed BCP < 12 months; 0 = no BCP |
| **BIA per service** | 10 | 10 = BIA covers our service; 0 = generic |
| **RTO/RPO commit** | 15 | 15 = ≤ our requirement; 0 = > our requirement or undefined |
| **DR site** | 10 | 10 = hot site tested in last 12 months; 0 = no DR or untested |
| **Exercise programme** | 10 | 10 = quarterly + recent results shared; 0 = no exercises |
| **Incident response** | 10 | 10 = documented IRP + notification SLA matches ours; 0 = ad-hoc |
| **Sub-processor / 4th-party BCP** | 5 | 5 = sub-processors attested; 0 = no visibility |
| **Cyber maturity** | 10 | 10 = SOC 2 Type II or ISO 27001 + last cyber test < 12 months; 0 = none |
| **Insurance** | 5 | 5 = BI insurance with named-insured coverage; 0 = none |
| **Notification SLA** | 5 | 5 = ≤ 1h notification; 0 = > 24h or vague |
| **Cooperation in our exercises** | 5 | 5 = vendor participates in our full-scale annually; 0 = refuses |

### Score bands
| Score | Band | Action |
|---|---|---|
| 80-100 | **Strong** | Annual attestation only |
| 60-79 | **Adequate** | Annual attestation + targeted improvement plan |
| 40-59 | **Weak** | Quarterly attestation + risk acceptance OR mitigation |
| 0-39 | **Unacceptable** | Exit-plan invocation OR critical-vendor demotion |

## 5 · Standard attestation questionnaire

The questionnaire goes to each vendor:

```
Section A — Company + Contact
  A.1 Legal entity, address, jurisdiction
  A.2 Account team contacts (sales, support, security)
  A.3 BCM contact (named individual)

Section B — Plans
  B.1 Do you maintain a documented BCP covering the services provided to us? (Y/N)
  B.2 Date of last full BCP refresh (DD/MM/YYYY)
  B.3 Date of last exercise (DD/MM/YYYY)
  B.4 Exercise type (tabletop / functional / full-scale)
  B.5 Exercise results (PASS / PASS_WITH_OBSERVATIONS / FAIL)
  B.6 Provide BCP executive summary (PDF, max 5 pages)

Section C — Recovery
  C.1 Stated RTO for our service
  C.2 Stated RPO for our service
  C.3 DR site location
  C.4 DR site type (hot/warm/cold)
  C.5 DR site last tested (DD/MM/YYYY)
  C.6 Failover process: automatic / manual / human-decided

Section D — Sub-processors
  D.1 List of 4th parties touching our data
  D.2 Their BCP attestation status
  D.3 Geographic location of sub-processors

Section E — Cyber
  E.1 SOC 2 Type II / ISO 27001 certification (provide audit report)
  E.2 Last pentest date + scope
  E.3 Last cyber incident affecting our service (if any)
  E.4 Cyber incident notification SLA to us

Section F — Insurance
  F.1 Business-interruption insurance — coverage limit
  F.2 Cyber insurance — coverage limit
  F.3 Named-insured: us included? Y/N

Section G — Notifications
  G.1 SLA for incident notification to us (hours)
  G.2 Channels (email, phone, portal)
  G.3 Escalation path

Section H — Cooperation
  H.1 Will you participate in our annual full-scale exercise? Y/N
  H.2 Will you allow on-site BCM audit by us / our auditor? Y/N

Section I — Attestation
  Signed by [Vendor's CRO / equivalent]: ☐
  Date: ___
```

## 6 · Exit plan invocation criteria
A critical vendor scoring < 40, OR materially failing to deliver, triggers exit-plan activation per `scm.exit_plans`. Procedure includes:
- 90-day notice (where contract allows)
- Data extraction + migration plan
- Alternate-vendor onboarding (already pre-qualified)
- Customer-facing comms if visible

## 7 · Multi-tenant variations
- **TSS Bank (org=1)**: full scope per above
- **Aramco (org=2, energy)**: emphasis on OT vendors (Honeywell, GE), pipeline contractors
- **STC Bank (org=3, telco-banking)**: emphasis on payment switch, telecom backbone, HSM provider

## 8 · Storage + audit trail
- Each vendor's attestation in `scm.vendor_assessments`
- Annual campaign archived as a project
- IA reviews sample of vendor attestations during BCM audit engagements
