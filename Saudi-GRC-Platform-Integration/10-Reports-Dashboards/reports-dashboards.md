# 11 — Reports and Dashboards
> **HISTORIC — point-in-time snapshot (all files 2026-05-11). Not a statement of current state.**
> ⚠ **This is PACK A of two generations, and neither supersedes the other** — finding **F2-135**. PACK B (`docs/Saudi-GRC-Platform-Integration/`, 2026-05-18) covers 16 shared sections more deeply, but **dropped sections 16–18 of this pack entirely** (AI features, frontend requirements, Excel workbook — 1,010 lines found only here) and reused the number `16` for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Report catalogue. Every report is generated via the reporting module
(`/api/v1/reporting/*`) with bilingual templates and ExportMenu support.

**Shared:**
- Formats: PDF + XLSX + PPTX where useful; CSV always available.
- Bilingual: every template has EN + AR variants; `lang` query param.
- Audit: every export writes to `audit.events` (`verb=export`).
- Digital signature: where required, the export pipeline pauses until a
  signing transaction completes.

---

## 1. Board Risk Report
- **Audience**: Board, Audit Committee, CRO
- **Purpose**: quarterly enterprise risk snapshot for governance review
- **Sections**: cover + executive summary + heatmap + top-10 risks +
  appetite-breach feed + KRI dashboard + acceptances + CAPA + emerging-risk
  watchlist
- **KPIs/KRIs**: total risks, critical risks, appetite breaches, KRI
  breaches, # acceptances, average treatment age
- **Charts**: heatmap (5x5), risks by category bar, trend line per
  appetite band
- **Tables**: top-10 risks, recent acceptances, open CAPAs, emerging
  risks
- **Filters**: organisation, quarter, category
- **Formats**: PDF + PPTX + XLSX
- **Approval**: Risk Mgr → CRO → Board
- **Signature**: ✓ Chairman
- **Path**: Board Pack module, `/api/v1/reporting/board-pack-pdf`

## 2. Executive GRC Dashboard
- **Audience**: CxOs, CEO
- **Purpose**: live snapshot across all GRC modules
- **Surfaces**: `/dashboard` widget grid
- **KPIs**: critical risks open, compliance %, control coverage, open
  incidents, BCM readiness, supplier-resilience score, PDPL DSR SLA
- **Filters**: organisation, time window
- **Formats**: live + PDF snapshot
- **Signature**: not required for view; required for snapshot export

## 3. BCM Readiness Report
- **Audience**: BCM Manager, COO, Risk Mgr
- **Purpose**: programme readiness summary
- **Sections**: critical-services count + BIA completeness + BCP
  coverage + DR-test status + exercise programme + open CAPAs
- **KPIs**: critical-services with current BIA %, plans with current
  test %, average RTO target vs actual
- **Charts**: BIA freshness donut, DR-test status by tier
- **Tables**: critical services without BIA / BCP / DRP
- **Formats**: PDF + XLSX
- **Signature**: optional (BCM Mgr)

## 4. Critical Services Continuity Report
- **Audience**: COO, Service Owners
- **Purpose**: per-service continuity posture
- **Sections**: service header → BIA → BCP → DRP → exercises → gaps
- **Filters**: service, criticality
- **Formats**: PDF
- **Signature**: optional

## 5. DR Readiness Report
- **Audience**: CIO, BCM Mgr
- **Purpose**: DR plans + test outcomes + RTO/RPO trends
- **Sections**: per-plan card + last-test outcome + next-test calendar +
  RTO target vs actual + gaps
- **Charts**: test outcomes timeline, RTO trend per system
- **Formats**: PDF + XLSX

## 6. Crisis Exercise Report
- **Audience**: Crisis Mgr, BCM Mgr, Exec
- **Purpose**: after-action from a crisis exercise
- **Sections**: scenario + timeline + participants + observations +
  CAPAs
- **Formats**: PDF
- **Signature**: BCM Mgr

## 7. Supplier Resilience Report
- **Audience**: COO, Vendor Risk Mgr, BCM
- **Purpose**: supplier portfolio resilience posture
- **Sections**: critical vendors + alternates + exit plans + tests +
  fourth-party concentration
- **KPIs**: % critical vendors with valid alternate, % with current exit
  plan, % tested in last 12m
- **Filters**: criticality, tier, country
- **Formats**: PDF + XLSX

## 8. NCA Compliance Report
- **Audience**: CISO, NCA Compliance Owner, Internal Audit
- **Purpose**: NCA control coverage and maturity
- **Sections**: per-framework heatmap + maturity bars + gap list + CAPA
  pipeline
- **KPIs**: implementation %, average maturity per domain
- **Filters**: framework (ECC/CSCC/CCC/OTCC/SAMA)
- **Formats**: PDF + XLSX
- **Signature**: ✓ CISO for filing

## 9. PDPL Compliance Report
- **Audience**: DPO, Privacy Mgr, Exec
- **Purpose**: PDPL article-by-article coverage
- **Sections**: ROPA freshness + notices + consent rollup + DSR SLA +
  breach register + CBT + disposal + processor DD + DSA
- **KPIs**: ROPA reviewed-last-90d %, DSR within-SLA %, notification
  within 72h %, current processor assessments %
- **Filters**: lawful-basis, data category
- **Formats**: PDF + XLSX

## 10. Regulatory Compliance Report
- **Audience**: Compliance Officer, Internal Audit
- **Purpose**: cross-framework compliance posture
- **Sections**: per-framework summary + control coverage + open gaps
- **Filters**: framework, regulator
- **Formats**: PDF + XLSX

## 11. Corrective Action Status Report
- **Audience**: CAPA owners, managers
- **Purpose**: open / overdue / closed CAPAs
- **Sections**: open by severity + overdue + by origin + verification
  queue
- **KPIs**: open count, average age, overdue %
- **Formats**: PDF + XLSX

## 12. Risk Appetite Breach Report
- **Audience**: Risk Committee, CRO
- **Purpose**: above-appetite exposures
- **Sections**: appetite statement + breach feed + acceptances
- **Formats**: PDF

## 13. Cyber Incident Report
- **Audience**: CISO, SOC, regulator
- **Purpose**: per-incident or roll-up
- **Sections**: timeline + classification + impacted assets + IOCs +
  containment + recovery + lessons learned + CAPAs
- **Formats**: PDF (per incident) + XLSX (roll-up)
- **Signature**: required at regulator-notification step

## 14. Data Breach Report
- **Audience**: DPO, CISO, SDAIA liaison
- **Purpose**: PDPL Art. 20 breach notification + post-event report
- **Sections**: scope + affected subjects + categories + containment +
  notification record
- **Signature**: ✓ DPO

## 15. Audit Evidence Package
- **Audience**: Internal + External Auditors
- **Purpose**: per-engagement evidence zip
- **Sections**: cover + scope + linked evidence + signed attestations +
  chain-of-custody verification report
- **Formats**: ZIP (PDF + supporting files)

## 16. Management Review Report
- **Audience**: Executive, Board
- **Purpose**: ISO clause 9.3 management review (BCM + ISMS + privacy)
- **Sections**: status of actions from prior review + KPIs/KRIs +
  audit-findings status + non-conformity + improvements
- **Formats**: PDF + PPTX
- **Signature**: ✓ Exec
