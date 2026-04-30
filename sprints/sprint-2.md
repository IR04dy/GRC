# Sprint 2 — Domain Modules Expansion
**Duration:** 6 weeks
**Theme:** Activate Cybersecurity, BCM, Privacy, Compliance, and Data Governance modules. All cross-module risk linkages wired. Reporting foundation in place.

> **Architecture note:** All new controllers are `@RestController` returning JSON.
> The `tss-grc-ui/` frontend project gains new HTML pages for each module that call
> the REST API using `api.js`. No Thymeleaf templates are created.

---

## Goals

By the end of Sprint 2, the platform covers the full GRC domain surface area:
- Security incidents and vulnerabilities managed end-to-end (Cyber module)
- Business continuity plans and BIA records tracked (BCM module)
- Personal data processing activities documented; breaches reported (Privacy module)
- Compliance assessments against NCA ECC, ISO 27001, PDPL managed (Compliance module)
- Data assets classified and governed (Data Governance module)
- All modules feed risks into the unified risk register
- Executive dashboard reflects cross-module aggregations
- Excel and PDF exports working for all list views

---

## Deliverables by Layer

### Database (Flyway Migrations)
| Migration File | Description |
|---|---|
| `V2.0.0__cyber_tables.sql` | cyber_assets, vulnerabilities, cyber_incidents, cyber_assessments, threat_intelligence |
| `V2.1.0__bcm_tables.sql` | critical_processes, bia_records, bcm_plans, bcm_exercises, recovery_strategies |
| `V2.2.0__privacy_tables.sql` | data_categories, processing_activities, privacy_assessments, data_breaches, data_subject_requests, consent_records |
| `V2.3.0__compliance_tables.sql` | compliance_assessments, compliance_findings, remediation_plans, evidence_items, audit_engagements |
| `V2.4.0__datagov_tables.sql` | data_assets, data_classifications, data_stewardship, data_quality_rules, data_lineage |
| `V2.5.0__reporting_tables.sql` | report_definitions, report_executions, report_schedules |
| `V2.6.0__sprint2_indexes.sql` | Module-specific indexes |
| `V2.7.0__sprint2_views.sql` | vw_compliance_status, vw_incident_summary, vw_active_controls |
| `R__seed_controls.sql` | ISO 27001:2022, PDPL, ISO 22301, NCA ECC 2.0 controls (repeatable) |

### Backend — Cybersecurity Module
- [ ] Entities: `CyberAsset`, `Vulnerability`, `CyberIncident`, `CyberAssessment`, `ThreatIntelligence`
- [ ] `CyberIncidentService.java` — lifecycle, severity scoring, auto-create linked risk
- [ ] `VulnerabilityService.java` — CVSS scoring, asset linkage, remediation tracking
- [ ] `CyberAssetController.java`, `CyberIncidentController.java`, `VulnerabilityController.java`
- [ ] Event: `IncidentEscalatedEvent` → fires NotificationEvent + creates `risk.risks` entry when severity ≥ HIGH

### Backend — BCM Module
- [ ] Entities: `CriticalProcess`, `BiaRecord`, `BcmPlan`, `BcmExercise`, `RecoveryStrategy`
- [ ] `BiaService.java` — RTO/RPO gap calculation, maximum tolerable outage tracking
- [ ] `BcmPlanService.java` — plan versioning, review scheduling
- [ ] `BcmExerciseService.java` — exercise result capture, lessons learned
- [ ] Controllers for all entities
- [ ] Event: `BiaHighImpactEvent` → creates linked risk when process criticality = HIGH/CRITICAL

### Backend — Privacy Module
- [ ] Entities: `DataCategory`, `ProcessingActivity`, `PrivacyAssessment`, `DataBreach`, `DataSubjectRequest`, `ConsentRecord`
- [ ] `BreachNotificationService.java` — 72h regulatory clock, PDPC notification workflow
- [ ] `DsrFulfillmentService.java` — DSR lifecycle with SLA tracking (30 calendar days)
- [ ] `RopaController.java` — RoPA register with PDPL Article 12 fields
- [ ] Event: `DataBreachConfirmedEvent` → creates linked risk + triggers workflow + notifies DPO

### Backend — Compliance Module
- [ ] Entities: `ComplianceAssessment`, `ComplianceFinding`, `RemediationPlan`, `EvidenceItem`, `AuditEngagement`
- [ ] `ComplianceScoreService.java` — score = (compliant controls / total controls) × 100, cached
- [ ] `EvidenceService.java` — evidence upload, integrity hash (SHA-256), linkage to controls
- [ ] `GapAnalysisService.java` — identifies controls without evidence or with expired evidence
- [ ] Controllers: assessment CRUD, finding management, evidence upload/download
- [ ] Event: `FindingHighSeverityEvent` → creates linked risk entry in ERM

### Backend — Data Governance Module
- [ ] Entities: `DataAsset`, `DataClassification`, `DataStewardship`, `DataQualityRule`, `DataLineage`
- [ ] `DataClassificationService.java` — applies NCA ECC data classification scheme
- [ ] `DataStewardshipService.java` — steward assignment notifications
- [ ] Controllers for all entities
- [ ] Integration: data assets linked to privacy RoPA processing activities

### Backend — Reporting Module (Foundation)
- [ ] `ReportDefinition.java`, `ReportExecution.java`, `ReportSchedule.java` entities
- [ ] `ExcelExportService.java` — Apache POI 5.x; bilingual headers; RTL sheet layout for Arabic
- [ ] `PdfExportService.java` — OpenPDF; Amiri/Noto Naskh fonts for Arabic; Bidi text support
- [ ] `ReportExecutionService.java` — @Async execution, status tracking, file storage
- [ ] `DashboardAggregationService.java` — cross-module KPIs with Caffeine cache
- [ ] Enhanced `DashboardController.java` — all 5 module KPIs, cross-module charts

### Backend — Cross-Module Risk Linkage
- [ ] `RiskLinkageService.java` — creates `risk.risks` entry from source module event with `source_module` + `source_entity_id` populated
- [ ] Spring ApplicationEvent wiring for all 4 source modules (Cyber, BCM, Privacy, Compliance)
- [ ] `UnifiedRiskViewController.java` — serves the unified risk view dashboard widget

### Frontend — `tss-grc-ui/` Pages (new pages added this sprint)
- [ ] `cyber/index.html` — asset inventory list; calls `GET /api/v1/cyber/assets`
- [ ] `cyber/incidents.html` — incident list + severity badges
- [ ] `cyber/vulnerabilities.html` — vulnerability register with CVSS scores
- [ ] `bcm/index.html` — critical process list; calls `GET /api/v1/bcm/processes`
- [ ] `bcm/bia.html` — BIA records with RTO/RPO gaps
- [ ] `bcm/plans.html` — BCM plan list + version history
- [ ] `privacy/ropa.html` — Record of Processing Activities table
- [ ] `privacy/breaches.html` — breach list with 72h regulatory clock
- [ ] `privacy/assessments.html` — PIA/DPIA assessment list
- [ ] `compliance/assessments.html` — compliance assessment list + score bars
- [ ] `compliance/findings.html` — findings with severity and remediation status
- [ ] `compliance/evidence.html` — evidence upload and control linkage
- [ ] `compliance/gap-analysis.html` — control gap heatmap by framework (Chart.js matrix)
- [ ] `reports/index.html` — report catalogue; run/schedule/download buttons calling export API
- [ ] `dashboard.html` updated — unified risk widget, compliance by framework doughnut, incident count KPI

### Frontend — `tss-grc-ui/assets/js/` (new JS files)
- [ ] `risk-linkage.js` — renders source module badge (CYBER / BCM / PRIVACY / COMPLIANCE) on risk cards
- [ ] `export.js` — polls `GET /api/v1/reports/executions/{id}` every 2s until COMPLETED, then triggers download
- [ ] `compliance-chart.js` — gap heatmap and framework comparison Chart.js renderers

---

## Acceptance Criteria

1. All 5 new module menu items visible and accessible with correct permissions
2. Creating a cyber incident with severity=CRITICAL auto-creates a linked risk in the ERM register with `source_module='CYBER'`
3. BIA record with criticality=HIGH creates a linked BCM risk
4. Data breach confirmation triggers workflow task to DPO with 72h SLA clock visible
5. Compliance assessment score updates when a finding is resolved and evidence attached
6. Excel export of risk register includes Arabic column headers when locale=ar
7. PDF export of compliance assessment renders Arabic text right-to-left correctly
8. Dashboard KPI cards reflect live cross-module counts (no stale cache beyond 5 minutes)
9. Unified risk view shows risks from all 5 source modules with correct source badge

---

## Definition of Done

- Unit tests for all new Service classes (min 70% branch coverage)
- Integration tests for all cross-module event flows
- Export tests: Excel/PDF files open without corruption in LibreOffice and Acrobat
- Arabic strings complete for all Sprint 2 UI elements in `messages_ar.properties`
- Performance: dashboard page loads in < 2s with 10,000 risk records in the DB
