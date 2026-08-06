# 11 · Crisis Communications Plan

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** Comms Lead + BCM Manager · **Backing entity:** `bcm.crisis_comms_templates`, `bcm.crisis_comms_audiences`, `bcm.crisis_comms_distributions`, `bcm.communication_contacts`

---

## 1 · Audience matrix

| Audience | Channel | When | Who drafts | Who approves | Bilingual |
|---|---|---|---|---|---|
| **Board + ExCo** | Email + phone | Within 30 min | BCM Mgr | CRO | EN only |
| **Internal staff (all)** | Email + intranet + Teams | Within 1h | HR Lead | CEO | EN + AR |
| **Internal — impacted team** | Direct (phone + Teams) | Immediately | Plan Owner | BCM Mgr | EN + AR |
| **Customers (impacted)** | App push + SMS + email + IVR | Within 2h | Comms Lead | CEO | EN + AR |
| **Customers (general)** | Status page + social media | Within 4h | Comms Lead | CEO | EN + AR |
| **Regulator — SAMA** | SAMA cyber inbox + phone | Within 6h (CSF v1.3) | DPO/GC | CEO | EN (some AR) |
| **Regulator — NCA** | NCA SOC hotline + portal | Per ECC-2 | CISO/DPO | CRO | EN (some AR) |
| **Regulator — NRC** | NRC critical-infra channel | Within 72h | CRO/BCM Mgr | CEO | EN |
| **Regulator — SDAIA** | SDAIA breach portal | Within 72h (Art. 20) | DPO | CEO | EN + AR |
| **Regulator — CMA** | Tadawul filing | Same day (material) | GC | CEO + Chairman | EN + AR |
| **Media** | Press release | Comms Lead decides — usually HIGH+ only | Comms Lead | CEO | EN + AR |
| **Partner banks (RTGS, SADAD)** | Direct call + SAMA group | If shared system impact | BCM Mgr | CRO | EN + AR |
| **Vendor TAMs (impacted)** | TAM portal + phone | Immediate (operational) | IT Ops Lead | BCM Mgr | EN |
| **Shareholders** | IR portal | Aligned with CMA | IR + GC | CEO + Chairman | EN + AR |

## 2 · Severity-driven communication

| Severity | What ships externally |
|---|---|
| **CRITICAL** | All audiences |
| **HIGH** | Internal + impacted customers + applicable regulators |
| **MEDIUM** | Internal + ExCo only (unless PDPL/CMA triggers) |
| **LOW** | Affected team only |

## 3 · Template inventory (target: 20+ templates)

Existing in DB (`bcm.crisis_comms_templates`): 5 rows. Plan target: 20.

### 3.1 Internal templates
- Initial declaration to staff (bilingual)
- Sitrep update to ExCo (every 2h)
- All-clear to staff (bilingual)
- WFH mobilisation message (bilingual)
- After-action thanks message

### 3.2 Customer templates
- Service interruption notice — short (push notification)
- Service interruption notice — full (email + IVR)
- Partial service degradation
- Recovery in progress update
- Service restored confirmation
- Compensation / goodwill offer (if applicable)

### 3.3 Regulator templates
- SAMA major-incident initial notification (Form per CSF v1.3)
- SAMA follow-up sitrep (every 2h)
- SAMA closure report (within 7 days)
- NCA incident notification (ECC-2 / CSCC / OTCC variants)
- NRC critical-infra event report
- SDAIA PDPL Art. 20 breach notification
- SDAIA Art. 21 subject notification (when subjects > threshold)
- CMA material event disclosure

### 3.4 Media template
- Press release — service interruption
- Press release — recovery + apology

## 4 · Tone-of-voice principles

| Principle | EN | AR |
|---|---|---|
| Empathy first | "We know this is frustrating" | "نتفهّم أنّ هذا الموقف يسبّب لكم الإزعاج" |
| Honest about impact | "Currently 70% of our customers cannot..." | "حالياً، 70% من عملائنا غير قادرين على..." |
| Specific on resolution | "Our team is restoring service; ETA 14:00 Riyadh time" | "فريقنا يعمل على استعادة الخدمة، الوقت المتوقّع 14:00 بتوقيت الرياض" |
| Apologise sincerely | "We are sorry. We are accountable." | "نعتذر بصدق. تقع المسؤولية علينا" |
| Provide next update | "Next update at [time]" | "التحديث التالي في [الوقت]" |

Avoid Modern-Standard-Arabic formality where Saudi-native colloquial is more effective for customer comms.

## 5 · Mass-notification cascade

Future state (Sprint 9-10): one-click cascade to Email + SMS + WhatsApp + Teams + intranet banner + status page.

Current state: Email channel only (see `mass_notification` track).

## 6 · Comms-tree test

- Monthly: each L1 + L2 contact reached + acknowledges within 15 min
- Quarterly: simulate full cascade (without sending customer-facing comms)
- Result recorded in `bcm.communication_contacts.last_test_result`

## 7 · Pre-approved spokespersons

| Topic | Spokesperson | Backup |
|---|---|---|
| Banking system outage | CEO | COO |
| Cyber incident | CISO + Comms Lead | CRO + Comms Lead |
| Data breach | DPO + Comms Lead | GC + Comms Lead |
| Regulator action | GC | CRO |
| ESG / sustainability | Sustainability Lead | CSR Lead |

Only pre-approved spokespersons engage media. Others say "I'll have someone come back to you".

## 8 · Saudi-specific considerations

- Friday/Saturday weekend — comms cascade still operates 24/7
- Hajj period — increased customer-volume × peak-stress; pre-draft Hajj-specific templates
- Ramadan — Suhoor/Iftar timing for non-urgent comms
- National Day, Founding Day, Eids — bank holiday + reduced staffing; comms-tree must work
- Arabic-first for customer-facing — English is acceptable but Arabic is primary

## 9 · Common pitfalls
- Sending the wrong template (e.g. PDPL Art. 21 when only Art. 20 is needed)
- Missing the SAMA 6h window because of approval delays
- Inconsistent customer messaging across channels (app push says X, IVR says Y)
- Forgetting partner banks → shared-system blowback
- Posting on social media before customer-facing channels (looks like denial)
- Promising specific recovery times you can't meet — say "by end of business day" if ETA uncertain
