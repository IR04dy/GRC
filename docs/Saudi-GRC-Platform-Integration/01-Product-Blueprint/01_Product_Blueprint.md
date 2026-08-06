# 01 — Product Blueprint
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Vision

> "One platform that connects Saudi regulatory obligations to real
> assets, services, data, suppliers, incidents, risks, controls,
> evidence, and executive decisions."

A bilingual (Arabic primary / English secondary), multi-tenant,
modular-monolith GRC operating platform for Saudi Arabia and the
wider GCC. Where most GRC tools are checklists glued to dashboards,
this platform is a **workflow + evidence + integration** engine:
risks lead to controls lead to evidence lead to attestations and
audits, all visible on one cross-cutting Compliance Posture Hub.

## Saudi market positioning

- **Bilingual first.** Every domain entity has `*_en` and `*_ar`
  columns, every UI string is keyed in EN+AR, and the AR
  translations follow Saudi-native phrasing per the
  `MEMORY.md` style guide rather than literal translations.
- **Hijri date support** — `HijriDatePicker.tsx` + dual
  `effective_date_g` / `effective_date_h` columns on Authority
  Documents.
- **Domestic regulatory canon** — NCA ECC v2 / CSCC / CCC / OTCC /
  DCC / TCC; PDPL 2023 (SDAIA); SAMA BCM / Cyber / Outsourcing /
  Op-Risk frameworks; DGA Digital Maturity + Interoperability;
  NRC national risks. All seeded as 🟦 placeholders ready for the
  official text.
- **International overlays** — ISO 22301 / 27001 / 27701 / 31000
  and SOC 2 sit alongside, so a Saudi org serving multi-national
  clients can report on both.

## Target customers

| Segment | Primary modules | Why |
|---|---|---|
| Government entities | DGA, NDI, BCM, Cyber (ECC) | DGA digital-maturity returns + SDAIA NDI cycle |
| Financial institutions (banks/insurers/payment providers) | SAMA suite, Risk, BCM, TPRM, AML adjuncts | SAMA frameworks are non-negotiable |
| Healthcare | PDPL, Cyber, BCM, Incident | High-consequence privacy + continuity |
| Telecom operators | Cyber (TCC), BCM, TPRM | NCA TCC + outage discipline |
| Critical national systems operators (energy, water, transport) | Cyber (OTCC + CSCC), BCM, NRC | OT-specific controls + national-risk linkage |
| Large enterprises | All | Cross-domain integration is the differentiator |

## Regulatory coverage matrix

See `07-Regulatory-Mapping/07_Regulatory_Mapping_Matrix.md` for the
full citation table. Headline coverage:

| Family | Documents | Status |
|---|---|---|
| NCA | ECC v2 · CSCC · CCC · OTCC · DCC · TCC | 🟦 placeholder catalog |
| SDAIA | PDPL 2023 | 🟦 placeholder + ✅ ROPA / DSR / PIA / breach modules built |
| SAMA | BCM FW · Cyber FW · Outsourcing · Op-Risk | 🟦 placeholder catalog |
| DGA | Digital Maturity · Interoperability | 🟦 placeholder |
| NRC | National Risks | 🟦 placeholder |
| ISO | 22301 · 27001 · 27701 · 31000 | 🟦 overlay |
| AICPA | SOC 2 | 🟦 overlay |

## Core modules (35 in code)

`Risk · BCM · SCM · Cyber · Privacy · TPRM · Policy · NDI ·
Compliance · Audit-LC · Incident · Crisis · CAPA · Evidence ·
Approvals · Workflow · Reporting · Dashboard · Admin · IAM ·
Platform · Audit Engagements · Knowledge Base · Ethics · ESG ·
Sustainability · Intake · Change Mgmt · Cyber-LC · BCM-LC ·
Privacy-LC · Risk-LC · Regulatory Change · Board Pack · Signature ·
Authority Documents (F1) · Control Library (F2) · Audit Log (F7) ·
Automation Rules (F8) · Licensing (F9)`

## Cross-module integration logic (the differentiator)

Every record is linkable to every other relevant record. The
**spine** built in Framework Intersection F1-F6 makes this explicit:

```
Authority Document → Citation → Control → Risk + Process + Asset + Evidence
                                  ↓
                          Assessment → Finding → CAPA → Audit Trail
```

Concrete linkages enforced today:
- A NCA citation can be satisfied by N controls of mixed domains
  (Cyber + BCM + Privacy + …) via `platform.control_mappings`.
- A risk can be mitigated by N controls via
  `platform.risk_control_links` (the Risk-Control Matrix auditors
  expect).
- A control gets per-cycle assessments in
  `platform.control_assessments`, each carrying evidence + finding +
  CAPA pointers.
- A CAPA cascades back to the gap, which cascades back to the
  assessment, which cascades back to the citation it was meant to
  cover — all queryable via correlation_id in `platform.audit_log`.

## Licensing model (F9)

Each tenant is entitled to a subset of modules via
`platform.organisation_licenses`. Tiers: FREE / PRO / ENTERPRISE.
The JWT carries the `enabled_modules` claim; the frontend hides
unlicensed sidebar entries; the `LicenseGuard` aspect blocks
controllers that the tenant isn't entitled to.

## Differentiators (vs. global IRM leaders)

| Feature | Us | ServiceNow IRM | Archer | OneTrust |
|---|---|---|---|---|
| Saudi regulator catalog | ✅ NCA + SAMA + SDAIA + DGA + NRC seeded | partial | partial | partial |
| Arabic primary + Hijri | ✅ built-in | adapter | adapter | adapter |
| Domain-spine + regulator-overlay architecture | ✅ F1-F6 | ✅ | ✅ | partial |
| Compliance Posture Hub (one page, many lenses) | ✅ F6 | ✅ | ✅ | ✅ |
| Automation Rules engine (rules as data) | ✅ F8 (20 seeded) | ✅ | ✅ | ✅ |
| Continuous Controls Monitoring | 🔜 | ✅ | ✅ | partial |
| AI assistance | 🔜 deferred | ✅ | partial | ✅ |
| KSA data residency by default | ✅ | tenant-config | tenant-config | tenant-config |

## Product maturity roadmap

| Quarter | Theme | Anchor slices |
|---|---|---|
| 2025 H2 | Foundation + ERM + BCM core | Sprint 1 |
| 2026 Q1 | TPRM, Privacy, NDI cycle | Sprint 2 |
| 2026 Q2 | Framework Intersection F1-F6 | this PR series |
| 2026 Q3 | Audit log + Automation + Licensing (F7-F9) | this PR series |
| 2026 Q4 | AI assistance + Continuous Controls Monitoring connectors | 🔜 |
| 2027 H1 | Customer-facing SaaS GA + partner program | 🔜 |

## Commercial packaging

- **FREE** — Authority Documents catalog (read-only), Risk register
  (single user), Evidence repository (≤ 100 items). Sufficient for
  a self-assessment-only SME.
- **PRO** — All core domain modules (Risk / BCM / SCM / Cyber /
  PDPL / TPRM / Policy / Incident / Compliance / Audit-LC / NDI),
  Reporting + Dashboards, Workflow + Approvals + Signature,
  Automation Rules engine. Sufficient for a typical mid-market KSA
  enterprise.
- **ENTERPRISE** — Everything above + Control Library + Posture
  Hub + Authority Documents editing + multi-org / parent-child
  hierarchies + custom Automation Rules + premium support + AI
  assistance (when GA).

## Implementation assumptions

See `16-Assumptions/16_Open_Assumptions_and_Dependencies.md`.
Headline items:

- The Saudi Authentication options (Nafath / Absher) are out of
  scope today; OAuth/SSO is in.
- Continuous Controls Monitoring connectors (AWS / Azure / M365
  posture telemetry) are a future track, not in the spine.
- The 20 seeded automation rules ship with **stub action handlers**
  (audit row only) for CREATE_RECORD / NOTIFY / ESCALATE / FLAG —
  concrete side-effects light up as each consuming module wires its
  own handler.

---

**See also:** `02-Module-Architecture/02_Module_Architecture.md`,
`docs/grc/framework-intersection-plan.md`, `STATUS.md`.
