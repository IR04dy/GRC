# TSS GRC Platform — BCM Manager & GRC SME Gap Analysis + Plan
**Author perspective:** Saudi BCM Manager (ISO 22301 + SAMA BCF) + GRC SME
**Date:** 23 May 2026
**Purpose:** Catalog real gaps a day-in-life BCM Manager and a GRC subject-matter expert would identify in the current platform, then turn them into a prioritised, sprint-sized plan that can be picked up cold.

---

## 0 · Existing baseline (what's already shipped)

For honesty about the starting point. The platform is already deep — gaps below are *additive*, not replacements.

| Module | Coverage today |
|---|---|
| **BCM** | 38 tables, 14 frontend pages: BIA wizard (time-band × impact-dimension matrix), BCP/DRP plan editors with section + version diff, Crisis Room, AAR detail page, Compliance mapping (ISO 22301/SAMA/DGA/NRC), Readiness Score, Reports framework, Dependency Graph, Library, Remediation, Settings, Exercises, Recovery Operations, Comms Tree, Plan Acknowledgements, Plan Review Attestations, Peak Seasons, Lessons Learned, Critical Processes + Resources + Dependencies, Management Reviews, Governance Records |
| **Risk** | 50 risks (across 3 tenants), 14 tables — register, treatments, KRIs (definitions + readings + breaches), assessments, attestations, scenarios, appetite statements, controls, risk_lc.acceptance_requests / kri_escalation_log / kri_breaches / notification_outbox |
| **PDPL/Privacy** | Consents (35), DSRs (15), breaches (16), DPIA, RoPA, cross-border transfers, privacy notices, disposal certificates, privacy_lc.dpia_assessments, cyber_breach_bridge_log |
| **Cyber** | 45 assets, 12 incidents, framework controls (NCA ECC/CSCC/CCC/OTCC + SAMA + ISO 27001/22301 + PCI DSS), 4 cyber_lc tables (incidents, threat_intel, vuln_exceptions, root_cause_analyses), evidence, sub_controls |
| **Compliance** | 12 regulatory changes (SAMA/NCA/SDAIA/CMA/ZATCA/MOH), assessments, audit_engagements, CAPA workflow |
| **Audit (audit_lc)** | 2 annual plans (2026 + 2027), 12 working papers, 20 findings, 12 CAPA tasks, engagements, full lifecycle from plan → wp → finding → capa → approval |
| **ESG/Sustainability** | 12 material topics, 10 climate risks (TCFD), GHG (Scope 1/2/3), SGI commitments, CMA Art.91-94 disclosures, TCFD matrix, materiality matrix, evidence, settings, organisation_settings, sustainability_report |
| **NDI** | 15 tables — full DGA cycle: cycles, framework, maturity, compliance, opex KPIs, governance domains, evidence, gaps, improvement plans, submission packages, reports, settings, audit log |
| **TPRM/SCM** | 12 tables — vendors, onboarding (6 stages), assessments, questionnaires, SLAs, exit plans, supplier_incidents, alternates, fourth-party exposures |
| **Ethics** | 10 cases, AML alerts, KYC subjects, disclosures, screening hits, case_actions, case_links |
| **Workflow** | 20 workflow definitions, action_log, instances, submissions, board_pack.packs (8 across 4 committees) |
| **Approvals** | 12 pending/decided across PDPL DSR, CBT, DPIA, risk treatment, CAPA closure, vendor onboard, BCP, board pack, ESG annex, audit finding remediation |
| **Calendar** | Event + iCal export + week view + recurring + reminder ladder (Saudi-workday-aware) |
| **Notifications** | Inbox (Track B), email channel, mass notification |
| **Search** | Cmd-K global palette (Track D) |
| **Platform** | JWT auth + refresh, module licensing (FREE/PRO/ENTERPRISE), automation rules engine + 75 seed rules, audit log + interceptor, RBAC, tenant context, RFC-7807 ProblemDetail responses, mass-assignment AOP backstop (Sprint-6), Saved Views, dashboards (BCM/SCM/ERM/PDPL/NCA/Cyber) |

**Multi-tenant:** TSS (org=1, Saudi Bank, ~400 rows) · Aramco (org=2, energy + trading, 28 rows) · STC Bank (org=3, telco-banking, 28 rows).

---

## 1 · As a Saudi BCM Manager — what I still need to do my job

Day-in-life of a Saudi-bank BCM Manager working under SAMA BCF + NCA OTCC + ISO 22301:

### 1.1 Strategic / annual planning gaps

| # | Gap | Why it matters |
|---|---|---|
| BCM-P0-1 | **No multi-year BCM strategic roadmap view** | I plan in 3-year cycles (Vision 2030-aligned). Need a roadmap canvas showing which BIAs are due in which quarter, which exercises planned, which CAPA milestones, all in one Gantt. Today the calendar shows individual events but no strategic-horizon view. |
| BCM-P0-2 | **No ISO 22301 §4-10 maturity self-assessment** | Auditors keep asking "where are you on §8.4 'Plan implementation'?" I need a 0-5 maturity rubric per clause, tracked year-over-year, with evidence-link buttons. Compliance Mapping page covers framework alignment but not maturity scoring. |
| BCM-P0-3 | **No annual BCM cycle automation** | The current `bcm.assessment_cycles` table tracks the cycle but doesn't auto-trigger annual review reminders for each BIA / BCP / contact-tree-test 30 days before due. I'd love a "BCM Manager inbox" showing what's coming up by week. |
| BCM-P0-4 | **No board pack BCM section auto-generation** | I draft the BCM section of every quarterly board pack manually. Should auto-stitch: top 5 critical processes by MTPD, last 5 exercise results, open CAPA count, last AAR summary, regulator-notification log. |

### 1.2 BIA / process modelling gaps

| # | Gap | Why it matters |
|---|---|---|
| BCM-P1-5 | **Process-dependency impact propagation** | If Process X fails, which downstream processes go down at hour 2, 4, 8, 24? Today `bcm.process_dependencies` stores edges but the dependency graph page doesn't simulate cascading-failure scenarios. Critical for justifying redundant capacity to the CFO. |
| BCM-P1-6 | **Peak-season heat-map overlay** | Hajj week, Ramadan settlement, year-end close, Eid bonus payouts — these dramatically change MTPD tolerance. `bcm.peak_seasons` table exists but isn't overlaid on BIA timelines / exercise calendar. |
| BCM-P1-7 | **RTO/RPO actual-vs-target trending** | After every exercise + real incident I record actuals. Need a per-process chart showing target vs actual over time so I can spot regression. Today `bcm.recovery_operations.rto_actual_hours` is captured but no trend chart. |
| BCM-P1-8 | **Quantitative BIA — financial impact at hour N** | Beyond the qualitative impact dimensions, I need a financial-impact-by-time-band model (loss of revenue + regulatory fines + reputational at hour 1/4/24/72). Drives the CFO sign-off on RTO targets. |
| BCM-P1-9 | **Continuity-strategy ROI calculator** | When proposing a new DR site, I have to justify cost vs reduction in MTPD risk-adjusted financial impact. No calculator exists. |

### 1.3 Crisis response & comms gaps

| # | Gap | Why it matters |
|---|---|---|
| BCM-P0-10 | **Regulator-specific reporting workflow** | SAMA wants major-incident notification within **6 hours** (Cyber Framework v1.3). NRC wants critical-infrastructure event notification within **72 hours**. SDAIA wants PDPL Article 20 breach within **72 hours**. Today `bcm.crisis_regulatory_notifications` captures the record but doesn't run the countdown timer, pre-fill the regulator-specific form, or remind me at T-1h. |
| BCM-P1-11 | **Stakeholder communication templates by audience** | I need bilingual templates per audience (board, regulator, employee, customer, media, partner-bank) per crisis severity. `bcm.crisis_comms_templates` exists with 5 rows — need 20+ across audiences. |
| BCM-P1-12 | **Mass-notification channel integration** | When I declare a crisis, I want 1-click cascade to SMS + WhatsApp + email + Teams + intranet banner. Today the comms tree records contacts but doesn't push. |
| BCM-P1-13 | **Mobile-first Crisis Room** | I'm running incidents from my phone at 2am. Crisis Room page is desktop-optimised. Need a mobile-first view with big buttons: declare, page L1, log decision, post sitrep. |

### 1.4 Exercise / drill gaps

| # | Gap | Why it matters |
|---|---|---|
| BCM-P1-14 | **Exercise scenario library** | I need 50+ pre-built scenarios across cyber/operational/physical/vendor categories, each with objectives + injects + measures + suggested attendees. `bcm.exercise_scenarios` exists but is sparse. |
| BCM-P1-15 | **Inject-driven exercise execution** | During a live exercise I need an inject timeline: at minute 30, escalate to severity HIGH; at 45, primary site loses power; at 60, SAMA calls. No execution support today. |
| BCM-P1-16 | **Cross-team observer dashboard** | During a tabletop, observers want a live view of decisions made, comms sent, and time-to-decision metrics. Crisis Room is for the team running the exercise, not for evaluators. |

### 1.5 Vendor / supply-chain BCM gaps

| # | Gap | Why it matters |
|---|---|---|
| BCM-P1-17 | **Critical-vendor BCP attestation campaign** | Annual ask: every critical vendor must show their BCP. Need a campaign workflow that emails 50 vendors, collects PDFs, tracks completion, flags non-responders. TPRM has `vendor_assessments` but not BCP-specific. |
| BCM-P1-18 | **Vendor MTPD inheritance** | If T24 has RTO 2h and depends on STC Cloud, STC Cloud's recovery must be ≤ 2h. Today there's no propagation check from `bcm.process_dependencies` → vendor SLAs. |
| BCM-P2-19 | **Fourth-party exposure mapping** | Supply chain attack vector. `scm.fourth_party_exposures` has rows but no visualisation showing which fourth-parties touch critical processes. |

### 1.6 Saudi regulatory specifics

| # | Gap | Why it matters |
|---|---|---|
| BCM-P0-20 | **SAMA BCF (Business Continuity Framework) clause-level mapping** | `bcm.compliance_mappings` covers ISO 22301 / SAMA / DGA / NRC at framework level. Need clause-by-clause mapping to SAMA BCF's 8 chapters + 47 clauses, with evidence-attachment, gap status, and quarterly attestation. |
| BCM-P0-21 | **NCA OTCC ot-bcm controls** | OT environments (refinery DCS, pipeline SCADA, ATM switches) have different recovery semantics from IT. NCA OTCC chapter 7 mandates OT-specific BCM controls. Today no separate OT-BCM track. |
| BCM-P1-22 | **NRC (National Risk Center) critical-infrastructure coordination workflow** | I'm on the NRC critical-sector list. When a sector-wide incident happens, I need to share sanitised situation reports with NRC + receive cross-sector intelligence. No workflow today. |
| BCM-P1-23 | **CMA Article 41 operational-resilience disclosure** | Listed companies must disclose operational-resilience posture in annual report. Need a CMA-Article-41-shaped extract from BCM data. |
| BCM-P1-24 | **CITC telecom-banking interop dependencies** | STC Pay, Apple Pay, SADAD all depend on telco SS7/SIGTRAN. Need a dependency layer for telecom infrastructure with CITC engagement workflow. |

### 1.7 Day-2 operational gaps

| # | Gap | Why it matters |
|---|---|---|
| BCM-P1-25 | **Offline-capable plan PDFs** | When systems are down I can't open the web UI. Need a generate-once button that produces a self-contained PDF of every BCP + crisis tree + decision rights, watermarked, dated, ready for the binder on every leader's desk. |
| BCM-P1-26 | **RACI visualisation per plan** | "Who decides?" should be one click. Today plan sections list owners textually but no RACI matrix. |
| BCM-P2-27 | **Insurance policy linkage** | Business-interruption insurance policies map to processes. When I increase RTO target, BI coverage requirement changes. No linkage today. |
| BCM-P2-28 | **Lessons-learned knowledge base** | After every AAR I capture lessons but they live as text in `bcm.lessons_learned`. Need searchable, tagged, cross-referenceable with similar-incident detection. |

---

## 2 · As a GRC SME — platform-wide gaps

### 2.1 Risk

| # | Gap |
|---|---|
| GRC-RISK-P0-1 | **Quantitative risk analysis (FAIR or ALE)** — current risk_score is qualitative likelihood × impact (1-5 × 1-5). Need an optional FAIR-style monte-carlo lane for top-tier risks. |
| GRC-RISK-P1-2 | **Bow-tie diagrams** — visualise top-10 risks with threat sources on the left, consequences on the right, preventive + reactive controls on the bowtie strings. |
| GRC-RISK-P1-3 | **Risk-control-finding traceability** — pivot view: pick a risk → see every control mapped to it → see every audit finding against those controls → see open CAPAs. The data joins exist; the visualisation doesn't. |
| GRC-RISK-P1-4 | **Three-lines-of-defense view** — which risk is 1LoD-owned, 2LoD-monitored, 3LoD-assured? Today implicit in roles, not surfaced. |
| GRC-RISK-P2-5 | **Group risk consolidation** — for holding companies running multiple licensed entities. |

### 2.2 Compliance

| # | Gap |
|---|---|
| GRC-COMP-P0-6 | **Regulatory horizon scanning** — auto-ingest SAMA / NCA / CMA / SDAIA circulars (RSS / scraping / partner feed) → triage → assign to owners. Today `compliance.regulatory_changes` is hand-curated. |
| GRC-COMP-P1-7 | **Obligation register tied to controls** — every clause from every framework distilled to obligations, each linked to the control(s) that fulfil it. |
| GRC-COMP-P1-8 | **Annual attestation campaigns** — owners sign off "control X operated effectively in Q4". Workflow + reminders + report. |
| GRC-COMP-P2-9 | **Multi-jurisdiction support** — regional banks operate KSA + UAE + Egypt + Bahrain. Need a jurisdiction column on every obligation. |

### 2.3 Privacy / PDPL

| # | Gap |
|---|---|
| GRC-PDPL-P0-10 | **Data flow visualisation** — Mermaid/Sankey-style diagram showing where each data category flows (system → system → vendor → cross-border). Today data points are captured per record but no holistic view. |
| GRC-PDPL-P1-11 | **Sub-processor tracking** — each main processor's sub-processors with consent-flow inheritance. PDPL Article 24 alignment. |
| GRC-PDPL-P1-12 | **AI/ML model registry with privacy + bias assessment** — every model that touches personal data needs DPIA + bias testing + drift monitoring. |
| GRC-PDPL-P2-13 | **Cookie/tracker consent for digital properties** — separate from API consents. |

### 2.4 Cyber

| # | Gap |
|---|---|
| GRC-CYB-P0-14 | **Threat intelligence ingestion** — MISP / ISAC / vendor feeds → enriched indicator-of-compromise list → match against asset inventory. |
| GRC-CYB-P1-15 | **Attack surface management** — discover external-facing assets (subdomains, certs, exposed endpoints) and reconcile with cyber.cyber_assets. |
| GRC-CYB-P1-16 | **Risk-based vulnerability SLAs** — Today vuln SLAs are fixed by CVSS. Should layer asset criticality + exploit-in-the-wild + business-impact to risk-rank. |
| GRC-CYB-P2-17 | **Pentest scheduling + finding triage** — annual external pentest cycle + remediation tracking. |

### 2.5 Audit

| # | Gap |
|---|---|
| GRC-AUD-P1-18 | **Continuous control monitoring** — instead of point-in-time audits, ingest control evidence (ServiceNow change tickets, AD lockout logs, CyberArk PAM attestations) continuously and alert on deviations. |
| GRC-AUD-P1-19 | **Audit committee dashboard (board-of-directors-grade)** — separate from working dashboards; large fonts, no jargon, exportable PDF for the board pack. |
| GRC-AUD-P2-20 | **External auditor liaison portal** — read-only secure workspace for external auditors to request evidence, file working papers, submit findings. |

### 2.6 ESG / Sustainability

| # | Gap |
|---|---|
| GRC-ESG-P1-21 | **Scope 3 financed-emissions calculator** — for banks. Portfolio-level GHG based on borrower industry weights. |
| GRC-ESG-P1-22 | **Climate scenario stress testing** — TCFD requires 1.5°C and 4°C scenario analysis. Need scenario inputs + portfolio impact outputs. |
| GRC-ESG-P2-23 | **CMA / Tadawul integration** — auto-publish sustainability report to Tadawul filing system once approved. |

### 2.7 Cross-cutting platform

| # | Gap |
|---|---|
| GRC-PLAT-P0-24 | **AI-powered control mapping** — when a new framework arrives (NCA CSCC v2.0), suggest which existing controls likely satisfy each clause. AI controls already partially exist (module/ai/) — need to extend to mapping. |
| GRC-PLAT-P0-25 | **Tenant-write-guard activation** — Sprint-6 staging activation (task #207 still pending). |
| GRC-PLAT-P1-26 | **Saudi-native UX micro-polish** — verify every Arabic string is Saudi-native (not Modern Standard Arabic literal translation) per persistent user constraint. |
| GRC-PLAT-P1-27 | **Comprehensive integration suite** — ServiceNow ITSM, Splunk SIEM, Crowdstrike, Tenable, ServiceNow IRM bridge. Even read-only mirroring would be huge. |
| GRC-PLAT-P1-28 | **End-user training + walk-through skin** — embedded contextual help (Pendo-style) with role-specific tours. |
| GRC-PLAT-P2-29 | **Mobile native app** — for incident response + approvals. |
| GRC-PLAT-P2-30 | **Public-sector SAML SSO** — Saudi NAFATH integration is partial (login page exists). Need full SP-initiated + IdP-initiated flow + role mapping. |

---

## 3 · Prioritised roadmap

### P0 — Must-have to call the platform "BCM-Manager-complete" (next 2 sprints)
1. **BCM-P0-1** Multi-year BCM strategic roadmap (Gantt)
2. **BCM-P0-2** ISO 22301 §4-10 maturity self-assessment
3. **BCM-P0-3** Annual BCM cycle automation (review reminders + inbox)
4. **BCM-P0-4** Board pack BCM section auto-generation
5. **BCM-P0-10** Regulator-specific reporting workflow (SAMA 6h, NRC 72h, SDAIA 72h)
6. **BCM-P0-20** SAMA BCF clause-level mapping
7. **BCM-P0-21** NCA OTCC OT-BCM track
8. **GRC-RISK-P0-1** FAIR / quantitative risk lane (opt-in per risk)
9. **GRC-COMP-P0-6** Regulatory horizon scanning (RSS + partner feed)
10. **GRC-PDPL-P0-10** Data flow visualisation
11. **GRC-CYB-P0-14** Threat intelligence ingestion
12. **GRC-PLAT-P0-24** AI-powered control mapping
13. **GRC-PLAT-P0-25** Sprint-6 tenant-write-guard activation (operator gate, task #207)

### P1 — Strongly desirable (next 2-3 sprints after P0)
- BIA quantitative impact-by-time-band, dependency cascade simulator, RTO/RPO trending (BCM-P1-5, BCM-P1-7, BCM-P1-8, BCM-P1-9)
- Crisis comms templates × 20 audiences + mass-notification cascade + mobile crisis room (BCM-P1-11, BCM-P1-12, BCM-P1-13)
- Exercise scenario library (50+) + inject-driven execution + observer dashboard (BCM-P1-14..16)
- Critical-vendor BCP attestation campaign + MTPD inheritance check (BCM-P1-17, BCM-P1-18)
- NRC coordination + CMA Art.41 + CITC interop (BCM-P1-22..24)
- Offline-capable plan PDFs + RACI matrix (BCM-P1-25, BCM-P1-26)
- Risk bow-tie + 3LoD + risk-control-finding pivot (GRC-RISK-P1-2..4)
- Obligation register + attestation campaigns (GRC-COMP-P1-7, GRC-COMP-P1-8)
- Sub-processor tracking + AI/ML model registry (GRC-PDPL-P1-11, GRC-PDPL-P1-12)
- Attack surface mgmt + risk-based vuln SLAs (GRC-CYB-P1-15, GRC-CYB-P1-16)
- Continuous control monitoring + audit committee dashboard (GRC-AUD-P1-18, GRC-AUD-P1-19)
- Scope-3 financed-emissions + climate scenarios (GRC-ESG-P1-21, GRC-ESG-P1-22)
- Saudi-native UX audit + ITSM/SIEM integrations + embedded help (GRC-PLAT-P1-26..28)

### P2 — Nice-to-have (later)
- Fourth-party exposure viz, insurance linkage, lessons-learned KB (BCM-P2-19, P2-27, P2-28)
- Group risk consolidation, multi-jurisdiction, cookie consent (GRC-RISK-P2-5, GRC-COMP-P2-9, GRC-PDPL-P2-13)
- Pentest tracker, external auditor portal (GRC-CYB-P2-17, GRC-AUD-P2-20)
- Tadawul filing integration, mobile native app, NAFATH SSO completion (GRC-ESG-P2-23, GRC-PLAT-P2-29, GRC-PLAT-P2-30)

---

## 4 · Sprint-sized work plan

Each row is a contained pull-request-sized chunk. Estimates assume one person × ~1 day per PR.

### Sprint 7 — P0 BCM block (1.5 weeks)

| Slice | What ships |
|---|---|
| 7-A | `bcm.strategic_roadmap` table + `/api/bcm/roadmap` + Roadmap Gantt page; lays out next 3 fiscal years with milestones per process |
| 7-B | `bcm.maturity_assessments` table seeded with ISO 22301 §4-10 (47 sub-clauses); MaturityWizard page with 0-5 score + evidence link per clause |
| 7-C | `bcm.annual_review_schedule` + Spring scheduler that emits review-due reminders 30/14/7/1 days before each annual review date; BCM Manager inbox tab on `/bcm` |
| 7-D | `BoardPackBcmSection` aggregator service + button on board pack wizard "Pull BCM section" |
| 7-E | SAMA 6h / NRC 72h / SDAIA 72h regulator countdown timers on `bcm.crisis_regulatory_notifications` + pre-filled regulator forms |
| 7-F | `bcm.compliance_mappings` extended with SAMA BCF chapters × clauses (47 rows), CRUD on compliance-mapping page already covers display |
| 7-G | NCA OTCC OT-BCM seed: new `bcm.ot_continuity_plans` table + page; OT process inventory; ICS/SCADA-specific recovery procedures |

### Sprint 8 — P0 GRC block (1 week)

| Slice | What ships |
|---|---|
| 8-A | `risk.fair_analysis` table (opt-in per risk) + FAIR wizard (TEF, LEF, SLEF, primary loss + secondary loss inputs) + monte-carlo aggregate endpoint |
| 8-B | Regulatory horizon scanner — scheduled job ingests RSS feeds from SAMA/NCA/CMA/SDAIA; new `compliance.reg_change_inbox` with triage workflow |
| 8-C | Privacy data-flow viz — `privacy.data_flow_nodes` + `data_flow_edges` tables; React-flow-based diagram editor; per-category overlay (PII, sensitive PII, financial, health) |
| 8-D | Threat intelligence — Spring HTTP client for MISP / OTX feeds; ingest into `cyber.threat_indicators`; match-against-assets cron |
| 8-E | AI control-mapping — extend `module/ai` with control-suggestion endpoint; when new regulatory_change arrives, suggest existing controls via embedding similarity |
| 8-F | Sprint-6 tenant-write-guard activation steps documented + production-readiness checklist (operator action — task #207) |

### Sprint 9-10 — P1 BCM block

| Slice | What ships |
|---|---|
| 9-A | Dependency cascade simulator — given process X failure, compute downstream impact at hours 1/4/8/24 with confidence bands |
| 9-B | RTO/RPO trending — time-series chart of actual vs target per process across last N exercises + incidents |
| 9-C | Quantitative BIA financial-impact module — per process, per time-band, per loss type (revenue / fine / reputational); CFO sign-off workflow |
| 9-D | Continuity-strategy ROI calculator — cost of strategy vs avoided MTPD financial impact |
| 9-E | Crisis comms template expansion: 20 templates × 7 audiences × bilingual; mass-notification cascade (Email + SMS + WhatsApp + Teams) |
| 9-F | Mobile-first Crisis Room — separate route `/crisis/mobile`, large buttons, swipeable timeline, voice-to-text decision logging |
| 10-A | Exercise scenario library — 50+ pre-built scenarios across cyber / operational / physical / vendor / regulatory |
| 10-B | Inject-driven exercise execution — `bcm.exercise_injects` table + injection timeline + facilitator console |
| 10-C | Observer dashboard during exercises — live view of decisions, comms, time-to-decision metrics |
| 10-D | Vendor BCP attestation campaign workflow + non-responder escalation |
| 10-E | Vendor MTPD inheritance check — flag when vendor SLA exceeds dependent process RTO |
| 10-F | Offline plan PDF generator — single-button "Print every plan for the binder", watermarked, dated, includes RACI |

### Sprint 11 — P1 GRC block

(Risk bow-tie, 3LoD view, obligation register, attestation campaigns, sub-processors, AI/ML registry, attack-surface mgmt, vuln risk-based SLA, continuous control monitoring, audit committee dashboard, Scope-3 calc, climate scenarios)

### Sprint 12 — Integrations + polish

(ServiceNow, Splunk, Crowdstrike, embedded help, Saudi-native Arabic audit pass)

---

## 5 · Saudi regulatory alignment matrix (BCM-specific)

| Regulation | Status | Gap |
|---|---|---|
| **SAMA Cyber Security Framework v1.3 §3.5 BCM** | Mapped at framework level | Clause-level mapping (Slice 7-F) |
| **SAMA Business Continuity Framework (BCF)** | Listed but not clause-mapped | 47 sub-clauses to map (Slice 7-F) |
| **SAMA Outsourcing Framework §5 (vendor BCP)** | Partial via TPRM | Critical-vendor BCP attestation campaign (Slice 10-D) |
| **SAMA Cyber Major-Incident 6h reporting** | Workflow record exists | Countdown timer + pre-fill (Slice 7-E) |
| **NCA ECC-2** | Mapped, framework controls table populated | OK |
| **NCA CSCC v1.1** | Mapped | OK |
| **NCA OTCC ot-bcm chapter 7** | Listed | OT-BCM track (Slice 7-G) |
| **NRC critical-infrastructure 72h notification** | Workflow record exists | Countdown timer + sanitised sitrep workflow (Slice 7-E, BCM-P1-22) |
| **SDAIA PDPL Article 20 breach 72h SDAIA** | Implemented end-to-end | OK |
| **SDAIA PDPL Article 21 subject notification** | Implemented | OK |
| **SDAIA PDPL Article 29 cross-border** | Implemented | OK |
| **CMA Sustainability Articles 91-94** | Implemented | OK |
| **CMA Article 41 operational resilience disclosure** | Not yet | Extract workflow (BCM-P1-23) |
| **CMA Corporate Governance Code** | Compliance record exists | Board attestation surface |
| **ZATCA E-Invoicing Phase 4** | Compliance record exists | No direct workflow integration needed (out of GRC scope) |
| **CITC Telecom-banking dependencies** | Not modelled | Vendor MTPD inheritance for telco SLAs (Slice 10-E) |
| **MOH Patient Privacy** | Compliance record exists | Healthcare-tenant page (out of scope for Saudi-bank tenant) |

---

## 6 · Open questions for the user

Before sprint-7 kick-off, please confirm:

1. **Sprint priorities**: pick top 3 P0 slices to start with. Suggested: 7-A (roadmap), 7-E (regulator timers), 7-G (NCA OTCC).
2. **Multi-tenant scope**: should all P0 work cover Aramco (energy) + STC (telco-banking) themes, or TSS Bank only first?
3. **Mass-notification channel preference**: Aljal SMS gateway? Twilio? Local provider (STC, Mobily)?
4. **Threat-intel source**: MISP self-hosted? OTX? Saudi-CERT feed? Commercial (CrowdStrike, Recorded Future)?
5. **Sprint cadence**: 1-week or 2-week sprints? Current pace ~7-15 PRs/sprint.
6. **NAFATH SSO completion**: needed in P0 / P1 / P2?
7. **External-auditor portal** (P2): KPMG/EY/PwC use specific tools — do we integrate or build standalone?

---

## 7 · How to use this document

- This file is the single source of truth for the next 6-12 sprints of BCM/GRC work.
- Each slice is sized to be one PR.
- When a slice ships, mark it ✅ here + capture in STATUS.md.
- Slice numbering is stable; future contributors can reference "Slice 7-A" without ambiguity.
- Re-prioritisation is welcome — edit Section 3 and adjust Section 4 accordingly.

---

**Compiled by:** Acting as Saudi BCM Manager + GRC SME, against the platform state at HEAD = commit `32d9a85` (post-PR #147, 23 May 2026).
