# BCM Manager Document Library — Saudi Bank Edition
> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> **For:** the BCM Manager (and the team) running ISO 22301 + SAMA BCF + NCA OTCC + NRC-aligned business continuity at a Saudi bank / TASI-listed company.
> **What this is:** the operational document set you need to actually do the job — templates, runbooks, methodologies, procedures. Not theory; not abstract guidance.
> **How to use:** every file is self-contained and can be exported to PDF for the binder. Cross-references between files use clickable links.

---

## 0 · Programme foundation

| Doc | Purpose |
|---|---|
| [01-bcm-programme-charter.md](01-bcm-programme-charter.md) | Board-approved BCM policy + scope + objectives |
| [02-roles-and-raci.md](02-roles-and-raci.md) | RACI matrix for every BCM activity (BIA, plans, exercises, crisis) |
| [03-glossary-and-acronyms.md](03-glossary-and-acronyms.md) | Bilingual BCM terminology — Saudi regulatory vocabulary |

## 1 · Business Impact Analysis (BIA)

| Doc | Purpose |
|---|---|
| [04-bia-methodology.md](04-bia-methodology.md) | Time-band × impact-dimension methodology, scoring scale, review cycle |
| [05-bia-template.md](05-bia-template.md) | Per-process BIA form (fill-in-the-blanks) |

## 2 · Risk & Strategy

| Doc | Purpose |
|---|---|
| [06-risk-assessment-methodology.md](06-risk-assessment-methodology.md) | Threat catalogue + likelihood/impact scoring + KRI thresholds |
| [07-continuity-strategy.md](07-continuity-strategy.md) | Strategy selection (hot/warm/cold/manual workaround) + cost-benefit |

## 3 · Plans & Procedures

| Doc | Purpose |
|---|---|
| [08-bcp-template.md](08-bcp-template.md) | Business Continuity Plan template — one per critical process |
| [09-drp-template.md](09-drp-template.md) | Disaster Recovery Plan template — IT + OT variants |
| [10-incident-response-plan.md](10-incident-response-plan.md) | IRP — declares crisis, escalates, integrates with cyber IR |
| [11-crisis-communications-plan.md](11-crisis-communications-plan.md) | Audience matrix + bilingual templates per scenario |
| [12-pandemic-public-health-plan.md](12-pandemic-public-health-plan.md) | Post-COVID lessons + WFH + supply-chain pandemic risk |

## 4 · Exercises & Testing

| Doc | Purpose |
|---|---|
| [13-exercise-strategy.md](13-exercise-strategy.md) | Multi-year exercise programme + scenario library |
| [14-exercise-templates.md](14-exercise-templates.md) | Tabletop, walkthrough, functional, full-scale templates |

## 5 · Operations & Reporting

| Doc | Purpose |
|---|---|
| [15-annual-bcm-review.md](15-annual-bcm-review.md) | Annual management review procedure + agenda |
| [16-regulator-notification-runbooks.md](16-regulator-notification-runbooks.md) | SAMA 6h, NCA, NRC 72h, SDAIA 72h, CMA — pre-filled forms + countdown SOPs |
| [17-vendor-bcp-attestation.md](17-vendor-bcp-attestation.md) | Critical-vendor BCP campaign procedure + scoring rubric |
| [18-kpi-dashboard.md](18-kpi-dashboard.md) | BCM KPI catalogue + board-pack content |
| [19-lessons-learned-template.md](19-lessons-learned-template.md) | After-Action Review form + knowledge-base tagging |

---

## How this set maps to the platform

| Document | Backing platform entity |
|---|---|
| 01 Programme Charter | `bcm.settings`, `bcm.governance_records` |
| 02 RACI | Cross-cutting — referenced from every plan |
| 04/05 BIA | `bcm.bia_records`, `bcm.bia_assessments`, BIA Wizard page |
| 06 Risk | `risk.risks` (source_module=BCM), `bcm.bcm_risks` |
| 07 Strategy | `bcm.continuity_strategies` |
| 08 BCP | `bcm.bcp_plans`, `bcm.bcp_sections`, `bcm.bcp_versions` |
| 09 DRP | `bcm.dr_plans` |
| 10 IRP | `bcm.crisis_events`, `bcm.crisis_decisions`, `bcm.crisis_situation_reports` |
| 11 Crisis Comms | `bcm.crisis_comms_*` + `bcm.communication_contacts` |
| 13/14 Exercises | `bcm.bcm_exercises`, `bcm.exercise_scenarios` |
| 15 Annual Review | `bcm.management_reviews`, `bcm.assessment_cycles` |
| 16 Regulator | `bcm.crisis_regulatory_notifications`, `compliance.regulatory_changes` |
| 17 Vendor | `scm.vendor_assessments`, `scm.exit_plans`, `scm.fourth_party_exposures` |
| 18 KPIs | `bcm_dashboard` view + BcmReadinessScore service |
| 19 Lessons | `bcm.lessons_learned`, `bcm.crisis_aar` |

---

## Compliance footprint

This document library is aligned to:

- **SAMA Business Continuity Framework (BCF)** — chapters 1-8 + 3 SAMA-specific addenda (Hajj/Ramadan, cross-border, outsourcing). 47 clauses seeded in `bcm.compliance_mappings` via Slice 7-F.
- **SAMA Cyber Security Framework v1.3 §3.5** — BCM controls
- **NCA Essential Cybersecurity Controls (ECC-2)** — §1.7 BCM controls
- **NCA OT Cybersecurity Controls (OTCC) Chapter 7** — OT-BCM
- **NCA Cloud Cybersecurity Controls (CCC)** — §5 continuity
- **NRC (National Risk Center)** — critical-infrastructure coordination
- **ISO 22301:2019** — clauses 4-10
- **ISO 22318:2021** — supply-chain continuity
- **CMA Article 41** — operational-resilience disclosure
- **SDAIA PDPL Article 20-21** — breach notification within 72h
- **NCEMA** — national crisis classification + escalation

---

## Maintenance cycle

| Cadence | Action |
|---|---|
| **Annually** (Q4) | Full document refresh, version-bump, board re-approval |
| **Quarterly** | Spot-check changed processes, update BCPs, refresh exercise calendar |
| **On trigger** | New product launch, M&A, regulator circular, major incident, exercise outcome |
| **Per release** | When this repo updates (e.g. new platform feature), refresh the `docs/bcm-manager/` cross-references |

---

## Conventions

- Headings in English, body bilingual where audience-relevant
- Every template has `[FILL]` placeholders for the BCM team to complete
- Saudi-native Arabic (not literal MSA translation) per the project's persistent constraint
- Dates in Hijri + Gregorian where regulator-facing
- Currency in SAR; time-of-day in Asia/Riyadh
- Hard deadlines (SAMA 6h, NCA, NRC 72h, SDAIA 72h) are highlighted in every relevant doc

---

**Last refresh:** 23 May 2026
**Owner:** BCM Manager + GRC Lead
**Approver:** Board Risk Committee
