# 01 · BCM Programme Charter
**Document owner:** CRO / BCM Manager · **Approver:** Board Risk Committee · **Cadence:** Annual

---

## 1 · Policy statement
[FILL — Organisation name] commits to maintaining business continuity capability that allows critical activities to recover within their Maximum Tolerable Period of Disruption (MTPD), in compliance with **SAMA Business Continuity Framework (BCF)**, **NCA ECC-2 / CSCC / OTCC**, **SDAIA PDPL Articles 20-21 + 29**, **NRC critical-infrastructure protocols**, and **ISO 22301:2019**.

### Bilingual statement
**EN:** "We protect customers, employees, shareholders, and the Kingdom's financial stability by maintaining the resilience of critical activities under all foreseeable disruption scenarios."

**AR:** "نحمي العملاء والموظفين والمساهمين والاستقرار المالي للمملكة من خلال الحفاظ على مرونة الأنشطة الحرجة في جميع سيناريوهات الاضطراب المتوقعة."

---

## 2 · Scope

| In scope | Out of scope |
|---|---|
| All Saudi-domiciled entities + branches | Non-banking subsidiaries with separate BCM programmes (covered by reciprocal MOU) |
| All critical processes (TIER_1 + TIER_2 per BIA) | Non-critical / discretionary activities |
| Production IT + OT systems | Lab / sandbox environments |
| Outsourced critical processes (per SAMA Outsourcing Framework) | Non-critical vendor services |
| Cross-border data flows (PDPL Art. 29) | n/a |

---

## 3 · Objectives (3-year horizon)

1. **Recovery commitment** — every TIER_1 process achieves RTO ≤ 4h, RPO ≤ 1h, MTPD ≤ 24h by FY27.
2. **Exercise coverage** — every BCP exercised at least once per year; every TIER_1 plan tested in full-scale exercise every 2 years.
3. **Regulator readiness** — 100% of SAMA / NCA / NRC / SDAIA notifications submitted within their respective deadlines (6h / SAMA, 72h / SDAIA, 72h / NRC).
4. **Vendor resilience** — 100% of critical vendors hold attested BCP by Q4 FY26.
5. **Maturity progression** — advance from current ISO 22301 maturity level X to level X+1 each fiscal year (track in `bcm.maturity_assessments`).

---

## 4 · Governance structure

```
                    ┌──────────────────────┐
                    │  Board Risk Committee │  ← quarterly oversight
                    └───────────┬──────────┘
                                │
                ┌───────────────┴───────────────┐
                │                               │
        ┌───────▼──────────┐         ┌──────────▼──────────┐
        │  Crisis Steering │         │  BCM Steering Cmte  │
        │  Committee (CEO+) │         │  (CRO chairs)      │
        └───────┬──────────┘         └──────────┬──────────┘
                │                               │
                │ (activates during crisis)     │ (runs programme)
                │                               │
                └───────────────┬───────────────┘
                                │
                    ┌───────────▼──────────┐
                    │   BCM Manager + Team │  ← day-to-day execution
                    └───────────┬──────────┘
                                │
                ┌───────────────┴────────────────────┐
                │                                    │
        ┌───────▼──────────┐                ┌────────▼─────────┐
        │ BIA Owners       │                │ Plan Owners      │
        │ (per process)    │                │ (per BCP/DRP)    │
        └──────────────────┘                └──────────────────┘
```

- **Board Risk Committee** — quarterly review of BCM dashboard, exercise outcomes, top BCM risks, regulator-notification log.
- **Crisis Steering Committee** — convenes within 15 min of crisis declaration; CEO chairs; quorum 60%.
- **BCM Steering Committee** — monthly, CRO chairs, decides on plan changes, exercise schedule, budget.
- **BCM Manager** — owns the programme.

---

## 5 · Investment & resourcing

| Item | Annual budget (indicative) |
|---|---|
| BCM team headcount (4 FTE — BCM Manager, BCP Coordinator, Exercise Lead, BIA Analyst) | SAR 2.4M |
| DR site lease + maintenance | SAR 8M |
| Exercise programme (12 exercises/yr + 1 full-scale) | SAR 1.2M |
| Tooling (GRC platform + mass notification + workplace recovery seats) | SAR 0.9M |
| Training + awareness | SAR 0.4M |
| External BCM review (biennial) | SAR 0.6M (every 2 years) |

Board re-approves budget annually in Q3 for the following fiscal year.

---

## 6 · Linkage to other programmes

- **Risk Management** — BCM risks feed into the enterprise risk register; KRIs cross-reference (see `risk_lc.kri_breaches`).
- **Cybersecurity** — Crisis Response Plan integrated with IR Plan for cyber incidents.
- **TPRM** — critical-vendor BCP attestation feeds vendor risk score.
- **Compliance** — every regulator-notification event creates a compliance record.
- **Internal Audit** — IA includes BCM in 3-year audit universe (audit_lc.annual_plans).
- **ESG** — climate-resilience overlap with TCFD physical-risk register.

---

## 7 · Approval log

| Version | Date | Approver | Notes |
|---|---|---|---|
| 1.0 | [FILL] | Board Risk Committee | Initial approval |
| 1.x | [FILL] | Board Risk Committee | Annual refresh |

---

## 8 · Distribution

| Audience | Access route |
|---|---|
| Board, ExCo | PDF in board pack |
| BCM team, plan owners | This repository + GRC platform |
| External auditors | Read-only via auditor portal |
| Regulators (on request) | PDF, sanitised version |
