# 08 · BCP Template (Business Continuity Plan)

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** BCP Owner (per critical process) · **Cadence:** Annual + on-trigger · **Backing entity:** `bcm.bcp_plans`, `bcm.bcp_sections`, `bcm.bcp_versions`

> One BCP per critical process. Fill every section. Print to PDF for the binder.

---

## SECTION 1 · Identification

| Field | Value |
|---|---|
| Plan Code | `BCP-[FILL]` |
| Plan Name (EN) | [FILL] |
| Plan Name (AR) | [FILL] |
| Process Covered | [Link to BIA-XXX] |
| Plan Owner | [FILL] |
| Plan Owner Backup | [FILL] |
| Activation Authority | [FILL — usually CRO + BCM Manager] |
| Effective Date | [FILL] |
| Next Review | [FILL — annually] |
| Version | 1.0 |
| Classification | RESTRICTED / CONFIDENTIAL |

## SECTION 2 · Activation criteria

This plan activates when ANY of the following triggers:
- [FILL — e.g. T24 unavailable > 30 min]
- [FILL — e.g. SADAD payment switch error rate > 5%]
- [FILL]

Activation is **declared** by [Authority] via [channel: incident system, phone, in-person] using the **declaration form** in Section 9.

## SECTION 3 · Severity classification

| Trigger condition | Declare severity | Approver |
|---|---|---|
| [FILL] | LOW | BCM Manager |
| [FILL] | MEDIUM | BCM Manager + CRO |
| [FILL] | HIGH | CRO + CEO |
| [FILL] | CRITICAL | CEO + Board Chair |

## SECTION 4 · Crisis Team — Roles & Contacts

Pulled from `bcm.communication_contacts` filtered by this plan.

| Role | Primary | Backup | Phone | Email |
|---|---|---|---|---|
| Crisis Lead | [FILL] | [FILL] | +966-50-100-0001 | ceo@... |
| BCM Manager | [FILL] | [FILL] | | |
| Plan Owner | [FILL] | [FILL] | | |
| IT Ops Lead | [FILL] | [FILL] | | |
| Comms Lead | [FILL] | [FILL] | | |
| DPO (if data involved) | [FILL] | [FILL] | | |
| Vendor TAM (if vendor-related) | [FILL] | [FILL] | | |

L1 contacts test cadence: **monthly**. Last test result + date: see plan acknowledgement log.

## SECTION 5 · Recovery procedure — step-by-step

### 5.1 First 15 minutes — declare + assess
1. Plan Owner confirms trigger condition
2. Notify BCM Manager + CRO (mandatory)
3. Convene Crisis Team via [Teams / Zoom / war room]
4. Declare severity (Section 3)
5. Open `crisis_events` record in platform — auto-creates situation-report cadence

### 5.2 First hour — stabilise
1. Acknowledge incident to incident-management workflow
2. Notify Crisis Steering Committee if severity HIGH+
3. Assess customer-facing impact (Comms Lead)
4. Decide internal-comms cascade (HR Lead)
5. Pre-arm regulator notification (DPO/GC) — see Section 7

### 5.3 First 4 hours — recover
For each recovery action, specify [FILL]:
- Action 1: [e.g. Failover T24 to DR site]
  - Owner: [FILL]
  - Pre-conditions: [DR site warm, network reachable, replication lag < 5 min]
  - Steps: [link to runbook]
  - Success criteria: [transactions resume, monitoring green]
  - Estimated duration: [FILL h]
- Action 2: [FILL]
- ...

### 5.4 After recovery — verify + comms
1. Verify success criteria (Plan Owner + IT Ops)
2. Notify Crisis Team of recovery
3. External comms (Comms Lead) — customer-facing message
4. Regulator notification within SLA — Section 7
5. Begin AAR within 48h — link to `bcm.crisis_aar` template

## SECTION 6 · Resource requirements

### 6.1 Workforce
| Role | Steady-state | Recovery min | Where |
|---|---|---|---|
| [FILL] | [#] | [#] | [Location] |

### 6.2 Technology
| System | Steady-state | Recovery state | DR mechanism |
|---|---|---|---|
| [FILL] | Production | DR site / cloud | [FILL] |

### 6.3 Information
- Vital records (list)
- Backup location + recovery procedure

### 6.4 Premises
- Primary site
- Alternate / DR site
- Activation procedure

### 6.5 Vendors
| Vendor | Service | Contact | Activation procedure |
|---|---|---|---|
| [FILL] | [FILL] | [FILL] | [FILL] |

## SECTION 7 · Regulator notification SLAs

| Regulator | Trigger | Deadline | Form |
|---|---|---|---|
| SAMA | Major cyber incident, payment-switch outage | **6 hours** (SAMA CSF v1.3) | [16-regulator-notification-runbooks.md#sama] |
| NCA | Cyber incident affecting critical systems | per ECC-2 / CSCC | [16-...md#nca] |
| NRC | Critical-infrastructure event | **72 hours** | [16-...md#nrc] |
| SDAIA | PDPL Art. 20 breach (personal data) | **72 hours** | [16-...md#sdaia] |
| CMA | Material event affecting listed company | **same day** (CMA Listing Rules) | [16-...md#cma] |

Failure to meet SAMA 6h = fine + remediation order.

## SECTION 8 · Crisis communications (cross-reference Doc 11)

| Audience | When to communicate | Template |
|---|---|---|
| Internal staff | Within 1h of declaration | [Link template] |
| Customers (impacted) | Within 2h | [Link template] |
| Customers (general) | Within 4h via status page | [Link template] |
| Board + ExCo | Within 30 min | [Link template] |
| Regulator | Per SLA — Section 7 | [Link template] |
| Media | Comms Lead decides — usually only HIGH+ | [Link template] |

## SECTION 9 · Declaration form

```
BCP ACTIVATION — [Plan Code]

Date/Time of declaration (Asia/Riyadh): _________________
Trigger condition: _________________
Severity declared: ☐ LOW  ☐ MEDIUM  ☐ HIGH  ☐ CRITICAL
Declared by: _________________ (name + role)
Authority verified: ☐ Yes  ☐ No

Initial Crisis Team members notified:
☐ BCM Manager   ☐ CRO   ☐ Plan Owner   ☐ IT Ops Lead   ☐ Comms Lead

Estimated impact:
  Process throughput: ___% of normal
  Customer reach: _____ accounts/customers
  Financial exposure: SAR _____
  Regulatory implication: ☐ None ☐ SAMA ☐ NCA ☐ NRC ☐ SDAIA ☐ CMA

Action plan reference: _________________

Signature: _________________
```

## SECTION 10 · Plan maintenance

| Trigger | Action |
|---|---|
| Annual (Q1 default) | Full review by Plan Owner + BCM Manager |
| New product launch | Re-evaluate impact + dependencies |
| Vendor change | Update Section 6.5 |
| Major incident or exercise | Capture lessons → update procedure |
| Regulator circular | Re-validate Section 7 SLAs |

## SECTION 11 · Acknowledgements

Every named role signs acknowledgement annually via `bcm.plan_acknowledgements`.

## SECTION 12 · Change log

| Version | Date | Author | Summary |
|---|---|---|---|
| 1.0 | [FILL] | [FILL] | Initial plan |
