# 16 · Regulator Notification Runbooks

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** GC + DPO + BCM Manager · **Backing entity:** `bcm.crisis_regulatory_notifications`

> Every regulator has its own deadline, form, channel, and follow-up cadence. Run the right runbook the moment a notifiable event is suspected.

---

## SAMA — Cyber Major Incident (6 hours)

**Authority:** SAMA Cyber Security Framework v1.3 §6 Major Incident Reporting
**Deadline:** Initial notification within **6 hours** of awareness; follow-up sitrep every 24h until closure; closure report within 7 days.
**Channel:** SAMA Cyber Incident Reporting Portal + email `cir@sama.gov.sa` + phone `+966-11-463-2000`
**Form:** SAMA-CIR-Initial-Notification form (PDF template stored on intranet)

### Trigger criteria (any of)
- Confirmed unauthorised access to TIER_1 system
- Customer data exfiltration confirmed
- Service unavailability > 30 min on TIER_1 system
- Ransomware confirmed
- Successful insider exfiltration
- Cyber-induced financial loss > SAR 1M
- Press/media exposure of cyber event

### T+0 to T+6h (initial notification)
1. **T+0** — CISO/SOC confirms incident is notifiable
2. **T+15min** — Notify BCM Manager + CRO + GC + DPO (parallel)
3. **T+30min** — Convene Crisis Steering Committee
4. **T+1h** — Initial impact assessment, severity declared
5. **T+2h** — Draft SAMA notification (English; Arabic optional but recommended)
6. **T+3h** — Internal review (GC + CRO)
7. **T+5h** — CEO sign-off
8. **T+6h** — SUBMIT via portal + email + phone confirmation

### Initial notification content (mandatory fields)
- Institution name + SAMA license number
- Incident type (cyber, fraud, operational)
- Severity (LOW/MEDIUM/HIGH/CRITICAL)
- Time of discovery (Asia/Riyadh)
- Time of suspected initial breach (Asia/Riyadh)
- Systems / services affected
- Customer impact estimate
- Financial impact estimate
- Containment actions taken
- Recovery actions planned + ETA
- BCM activation (yes/no, which plan)
- Next sitrep timing (within 24h)

### Sitrep cadence (after initial)
- Every 24h until closure, OR more frequently if material change
- Final closure report within 7 days of all-clear
- AAR shared with SAMA on request

### Common pitfalls
- Late assessment of "notifiable" classification — when in doubt, notify
- English-only notification when AR helps clarity
- Sitreps too long — keep to 1 page
- Forgetting to copy follow-up channels (SAMA Risk Sup, SAMA Cyber Unit)

---

## NCA — Cyber Incident (per ECC-2 + CSCC + OTCC)

**Authority:** NCA Essential Cybersecurity Controls (ECC-2) §1.7 + Critical-Systems Controls + OT Controls
**Deadline:** Per incident-class — typically immediate for critical-systems; 24-72h for others
**Channel:** NCA SOC hotline `+966-11-282-3000`; email `soc@nca.gov.sa`; NCA portal
**Form:** NCA-IRT-Notification form

### Trigger criteria
- Cyber incident affecting NCA-classified critical system
- Cyber incident affecting OT (refinery DCS, pipeline SCADA, ATM switch, payment switch)
- Cyber incident affecting cloud-hosted critical workload
- Penetration of perimeter defences with intent

### Procedure
Mirrors SAMA workflow but distinct submission. Often SAMA + NCA notifications run in parallel for banking-cyber events.

OTCC variant: include OT-specific fields (safety status, manual operation engaged, field-controller autonomy state).

---

## SDAIA — PDPL Article 20 / 21 (72 hours / immediate)

**Authority:** Saudi PDPL Articles 20 (notify SDAIA), 21 (notify data subjects)
**Deadline:**
- SDAIA: **72 hours** of awareness (Art. 20)
- Data subjects: "without undue delay" — interpret as 72h unless higher harm warrants faster (Art. 21)
**Channel:** SDAIA breach reporting portal `privacy@sdaia.gov.sa`
**Form:** SDAIA-PDPL-Breach-Notification

### Trigger criteria
- Confirmed personal-data exposure
- Personal-data unavailability for > 24h
- Unauthorised personal-data alteration
- Loss of personal-data (e.g. lost USB, lost laptop with unencrypted PII)

### T+0 to T+72h
1. **T+0** — DPO + CISO confirm personal-data breach
2. **T+1h** — Engage GC + CRO + CEO awareness
3. **T+12h** — Initial scope determination (categories, subjects, sensitivity)
4. **T+24h** — Containment + impact assessment
5. **T+48h** — Draft SDAIA notification
6. **T+60h** — Internal review + CEO sign-off
7. **T+72h** — SUBMIT

### Subject notification (Art. 21)
- If breach likely to result in serious harm to subjects → notify subjects directly
- Bilingual (EN + AR)
- Plain-language description of: what data, what risk, what to do
- Free credit-monitoring or equivalent offered where applicable

### Common pitfalls
- Late personal-data classification (e.g. classifying PII as "non-personal")
- Missing the 72h window because of internal-approval delays
- Forgetting to notify subjects when they should be notified
- Subject notification too jargon-heavy

---

## NRC — National Risk Center Critical Infrastructure (72 hours)

**Authority:** NRC critical-sector protocols (banking is on the critical-infrastructure list)
**Deadline:** **72 hours** for sector-wide events
**Channel:** NRC direct contact (per pre-arranged agreement)
**Form:** NRC-CI-Event-Report

### Trigger criteria
- Sector-wide cyber attack indicator
- Cascading impact across multiple banks / sectors
- Vendor compromise affecting > 2 critical-sector firms
- Cross-border attack vector with sustained activity

### Sanitised sitrep format
NRC needs sanitised information (no customer data, no PII, no internally-sensitive system details) to coordinate sector response.

---

## CMA — Material Event Disclosure (same day)

**Authority:** CMA Listing Rules + Capital Market Authority disclosure rules
**Deadline:** Same business day for material events
**Channel:** Tadawul filing system
**Form:** Material Event Disclosure form

### Trigger criteria
- Material operational disruption affecting profitability
- Major cyber incident with reasonable likelihood of impacting share price
- Customer-data breach of material scale
- Regulatory action against the company

### Procedure
- GC + IR coordinate
- CEO + Chairman sign-off
- Bilingual (EN + AR)
- File via Tadawul before next business day

---

## Cross-runbook coordination

Many events trigger multiple regulators in parallel:

| Event | SAMA 6h | NCA | SDAIA 72h | NRC 72h | CMA same-day |
|---|---|---|---|---|---|
| TIER_1 ransomware with data exfil | ✓ | ✓ | ✓ | maybe | ✓ |
| Payment-switch DDoS | ✓ | ✓ | - | maybe | maybe |
| Insider data exfil | ✓ | maybe | ✓ | - | maybe |
| OT incident (refinery) | maybe (banking-side only) | ✓ (OTCC) | - | ✓ | maybe |
| PDPL-only breach (no operational impact) | - | - | ✓ | - | maybe |

**Coordination rule**: BCM Manager runs a parallel notification tracker so no SLA misses. The platform's `bcm.crisis_regulatory_notifications` table is the source of truth.

---

## Countdown timer (Slice 7-E target)

After Slice 7-E ships, every regulator-notification record will:
- Compute deadline from `discovered_at` + regulator SLA
- Display countdown badge on Crisis Room page
- Email/SMS BCM Manager at T-1h
- Pre-fill regulator-specific form
- Block "All-clear" closure until notification confirmed sent

## Distribution log

Track who notified whom + when in the platform. Audit-grade.
