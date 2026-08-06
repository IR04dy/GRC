# 15 — Sample Seed Data
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Sample / demo data is bootstrapped via Flyway seed migrations
(V1 — V97) for tenant `organisation_id = 1`. No real confidential
data is used; everything is Saudi-themed but fictional.

## Personas / tenants

| Tenant | Type | Modules |
|---|---|---|
| 1 (demo) | Saudi enterprise | All — ENTERPRISE per V97 |
| 2 (sample-bank) | Financial institution | + SAMA filter active |
| 3 (sample-gov) | Government entity | + DGA filter active |

## Sample entities (highlights from migrations)

### Saudi government entity

```
Organisation: Demo Gov Entity (org=3)
Critical services: 3 (DigitalServicesPortal, IdentityRenewal, AppointmentBooking)
Cloud services: 2 (AWS-Riyadh, Azure-Jeddah)
Critical supplier: NationalCloud LLC
```

### Financial institution

```
Organisation: Demo Bank (org=2)
Critical services: 4 (Core Banking, Internet Banking, Mobile App, ATM Network)
Critical suppliers: 3 (SWIFT, Visa, Mada)
SAMA assessments seeded: BCM FW + Cyber FW + Outsourcing + Op-Risk
```

### Critical service

```
Code: SVC-BANKING-CORE-01
Name (EN): Core Banking System
Name (AR): النظام البنكي الرئيسي
Criticality: CRITICAL
MTPD: 4h, RTO: 2h, RPO: 15min
Linked BIA: BIA-CORE-2026Q1
Linked BCP: BCP-CORE-V2.0
Linked DRP: DRP-CORE-V1.5
```

### Critical supplier

```
Vendor: NationalCloud LLC
Type: Cloud Provider (IaaS+PaaS)
Criticality: CRITICAL
4th-party dependencies: 2 (regional data center, ISP)
BCP evidence: ACTIVE, expires 2027-03
Risk score: 18 (HIGH)
SAMA Outsourcing assessment: PARTIAL
```

### Cyber supplier

```
Vendor: SaudiSec Solutions
Type: MSSP
Criticality: HIGH
TPCA assessment: APPROVED
Vendor cyber questionnaire (V81): 82% posture / LOW risk
```

### Personal data processing activity

```
Activity: Customer onboarding for retail banking
Legal basis: PDPL Article 6(b) — contract
Data categories: Identity, Financial, Contact, KYC docs
Retention: 10 years post-relationship
Cross-border: No
PIA: PIA-CUST-ONB-2026 — APPROVED
```

### High-risk PIA

```
PIA: PIA-CUST-ONB-2026
Trigger: New AI-assisted underwriting
Risk: HIGH (automated decision-making)
Mitigations: Human-in-the-loop review, opt-out, monthly bias audit
Approval: DPO + Legal — SIGNED
```

### NCA assessment

```
Cycle: ECC-2026-Q1
Framework: NCA-ECC-V2
Controls assessed: 9 (placeholder seeded)
Effective: 4 EFFECTIVE, 3 NEEDS_IMPROVEMENT, 2 NOT_TESTED
Overall score: 65%
```

### Cyber incident

```
Code: INC-CYB-2026-0042
Type: CYBER
Severity: HIGH
Affects personal data: false
States walked: REPORTED → TRIAGE → CONTAINMENT → INVESTIGATION
Root cause: SQL injection in API
CAPA: open, target close 2026-Q3
```

### Personal data breach

```
Code: BREACH-2026-0007
Severity: HIGH
Records affected: ~5,200
72h timer: started, notified at 68h
Regulator notification: SDAIA acknowledged
```

### Supplier disruption

```
Code: INC-SUP-2026-0011
Supplier: NationalCloud LLC
Type: SERVICE_OUTAGE
Severity: CRITICAL
Crisis: activated (CRISIS-2026-0003)
RTO breach: yes (47 min over)
Lessons learned: yes (LL-2026-0008)
```

### Enterprise risk

```
Code: RISK-CYB-0042
Title (EN): Inadequate privileged-access governance
Inherent: L=4, I=5 → 20
Residual: L=3, I=4 → 12
Status: TREATMENT
Owner: alice (Risk Owner)
Treatments: 2 (MITIGATE in progress, ACCEPT planned for stretch goal)
Controls linked: 3 (C-IAM-014, C-IAM-007, C-CYBER-001)
```

### CAPA (illustrative)

```
Code: CAPA-2026-0124
Title: Roll out PAM tool across Tier-1 systems
Owner: Cybersecurity Manager
Due: 2026-09-30
Status: IN_PROGRESS
Progress: 60%
Linked finding: F-NCA-2026-0017
Linked risk: RISK-CYB-0042
```

### Evidence records (sample)

```
EV-BCP-CORE-001  Core Banking BCP             ACTIVE  expires 2027-01-01
EV-PAM-Q1-2026   PAM access review (Q1 2026)  ACTIVE  expires 2026-07-01
EV-DPIA-CUST-26  PIA for AI-assisted underwr. ACTIVE  expires 2027-03-01
EV-DR-EXC-2026   Annual DR exercise report    ACTIVE  expires 2027-04-15
```

### Board dashboard data

```
Top 5 risks shown:
  1. RISK-CYB-0042 (Privileged access) — residual 12
  2. RISK-BCM-0008 (Single cloud region dependency) — residual 16
  3. RISK-SUP-0021 (NationalCloud concentration) — residual 16
  4. RISK-PRIV-0014 (Cross-border data transfers) — residual 10
  5. RISK-OP-0033  (Manual reconciliation errors) — residual 9

GRC health score: 72/100
NCA score: 65%
PDPL score: 78%
Supplier resilience: 71%
Open critical incidents: 1
Overdue CAPAs: 3
```

### NCA compliance dashboard data

```
ECC v2:   65%  (9 of 9 controls assessed)
CSCC:     n/a  (assessment cycle not yet started)
CCC:      72%  (cloud services scored)
OTCC:     n/a  (no OT environment)
```

### PDPL dashboard data

```
Processing activities: 142
High-risk: 18
PIAs completed: 14 / 18
DSRs open: 7 (longest at 22 days, within SLA)
Active consents: 1.2M
Withdrawn consents (last 30d): 4,300
Cross-border transfers active: 6
Breaches (12m): 3 (all notified)
Processors w/o DPA: 0
```

### Supplier resilience dashboard data

```
Critical suppliers: 12
Without valid BCP: 2 (CAPA opened)
SLA breach (last 30d): 1
Concentration risk (Herfindahl): 0.38 (medium)
Single points of failure: 3
4th-party exposure: 7 nested dependencies
```

## How to refresh demo data

```bash
# Reset DB (dev only, destructive):
sqlcmd -S localhost -U sa -P tss.123 -Q "DROP DATABASE TSS_GRC;"
sqlcmd -S localhost -U sa -P tss.123 -Q "CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;"

# Re-apply migrations (each V…sql is idempotent):
mvn flyway:migrate -Dflyway.profile=dev   # OR boot the app, which migrates on startup outside dev
```

## Real customer onboarding

For non-demo tenants, seed migrations run **without the demo
inserts**. The bootstrap creates the tenant + admin user + default
role set + license entitlements only; domain data is loaded via
the import templates (`07_Control_Library_Import_Template.md`,
`06_Evidence_Excel_Structure.md`).

---

**See also:** Flyway migrations V1-V97.
