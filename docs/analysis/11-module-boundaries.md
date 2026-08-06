# Module Boundaries
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the boundaries between TSS GRC Platform modules, specifying what is shared, what is owned exclusively by each module, how modules integrate, and the architectural patterns used to maintain module independence while enabling cross-module collaboration.

---

## Shared Kernel

The shared kernel contains entities, services, and infrastructure that are common across all modules. These are owned by the platform infrastructure (Administration & Configuration module) and consumed by all other modules.

### Shared Entities

| Entity | Owner | Description | Used By |
|--------|-------|-------------|---------|
| **Organization** | Administration | Tenant/customer organization record | All modules (data scoping) |
| **Department / Business Unit** | Administration | Organizational hierarchy node | All modules (data ownership, filtering) |
| **Location** | Administration | Physical location reference | BCM, Cybersecurity, ERM |
| **User** | Administration | User account and profile | All modules (ownership, assignment, audit) |
| **Role** | Administration | Security role with permissions | All modules (authorization) |
| **Permission** | Administration | Atomic permission definition | All modules (access control) |
| **Lookup Table** | Administration | Configurable reference data (statuses, severities, categories) | All modules (dropdowns, classifications) |
| **Audit Log** | Administration | Immutable activity record | All modules (write audit entries) |
| **File Attachment** | Administration | Uploaded file with metadata | All modules (evidence, documents) |
| **Notification** | Administration | In-app and email notification | All modules (alerts, reminders) |
| **Notification Template** | Administration | Configurable message template | All modules (customizable messaging) |
| **System Setting** | Administration | Configuration parameter | All modules (behavior configuration) |
| **Custom Field Definition** | Administration | Entity extension field definition | All modules (extensibility) |
| **Custom Field Value** | Administration | Entity extension field value | All modules (extensibility) |

### Shared Services

| Service | Owner | Description | Consumers |
|---------|-------|-------------|-----------|
| **Authentication & Session** | Administration | User authentication, session management, SSO | All modules |
| **Authorization (RBAC)** | Administration | Permission checking at API and UI levels | All modules |
| **Audit Trail** | Administration | Write and query audit log entries | All modules |
| **File Management** | Administration | Upload, download, version, and link files | All modules |
| **Notification Service** | Administration | Send email and in-app notifications | All modules |
| **Lookup Service** | Administration | Retrieve lookup data for dropdowns and classifications | All modules |
| **Organization Context** | Administration | Resolve current org, BU, and hierarchy | All modules |
| **Localization Service** | Administration | Resolve AR/EN labels, messages, and formats | All modules |
| **Workflow Engine** | Workflow | Create, route, and manage approval workflows | All GRC modules |
| **Task Management** | Workflow | Assign, track, and escalate tasks | All GRC modules |
| **License Enforcement** | Licensing | Check module/feature entitlements at runtime | All modules |
| **Search Service** | Administration | Full-text search across entities (module-scoped) | All modules |

### Shared Database Objects

| Object Type | Examples | Purpose |
|-------------|----------|---------|
| **Organization tables** | `organizations`, `departments`, `locations` | Shared organizational reference |
| **User/Role tables** | `users`, `roles`, `permissions`, `user_roles` | Shared identity and access |
| **Audit tables** | `audit_logs` | Shared audit trail |
| **File tables** | `file_attachments` | Shared file metadata |
| **Notification tables** | `notifications`, `notification_templates` | Shared messaging |
| **Lookup tables** | `lookup_categories`, `lookup_values` | Shared reference data |
| **Workflow tables** | `workflow_definitions`, `workflow_instances`, `workflow_tasks` | Shared workflow engine |
| **Custom field tables** | `custom_field_definitions`, `custom_field_values` | Shared extensibility |
| **System settings** | `system_settings` | Shared configuration |

---

## Module-Owned Entities

Each module exclusively owns specific entities. Other modules may reference these entities (via ID) but never directly modify them.

### ERM-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Risk | `erm_risks` | Enterprise risk register entries |
| Risk Assessment | `erm_risk_assessments` | Point-in-time risk evaluations |
| Risk Category | `erm_risk_categories` | Risk taxonomy |
| Risk Treatment | `erm_risk_treatments` | Treatment plans |
| Treatment Action | `erm_treatment_actions` | Individual treatment actions |
| Risk Appetite Statement | `erm_risk_appetite` | Board-approved appetite levels |
| KRI | `erm_kris` | Key Risk Indicator definitions |
| KRI Data | `erm_kri_data` | KRI measurement history |
| Impact Scale | `erm_impact_scales` | Configurable impact criteria |
| Likelihood Scale | `erm_likelihood_scales` | Configurable likelihood criteria |
| Risk Assessment Campaign | `erm_assessment_campaigns` | Coordinated assessment batches |

### Cybersecurity-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Cyber Asset | `cyb_assets` | IT/OT asset inventory |
| Vulnerability | `cyb_vulnerabilities` | Tracked vulnerabilities |
| Threat | `cyb_threats` | Threat register |
| Cyber Incident | `cyb_incidents` | Cybersecurity incidents |
| Cyber Incident Timeline | `cyb_incident_timeline` | Incident response timeline entries |
| Cyber Risk | `cyb_risks` | Cybersecurity-specific risks |
| NCA Control Assessment | `cyb_nca_assessments` | NCA framework assessments |
| Penetration Test | `cyb_pen_tests` | Security testing records |
| Asset Dependency | `cyb_asset_dependencies` | Asset relationship mapping |

### BCM-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Business Process | `bcm_business_processes` | Processes subject to BIA |
| BIA Record | `bcm_bia_records` | Business impact analysis data |
| Business Continuity Plan | `bcm_plans` | BCP documents and metadata |
| BCP Version | `bcm_plan_versions` | Versioned plan content |
| Recovery Strategy | `bcm_recovery_strategies` | Recovery approaches |
| Dependency | `bcm_dependencies` | Process dependency mapping |
| Exercise | `bcm_exercises` | BCM exercises and tests |
| Exercise Observation | `bcm_exercise_observations` | Exercise findings |
| BCM Incident | `bcm_incidents` | Business continuity events |
| Call Tree | `bcm_call_trees` | Emergency communication chains |

### Data Governance-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Data Asset | `dgv_data_assets` | Data inventory entries |
| Data Classification | `dgv_classifications` | Applied classifications |
| Classification Scheme | `dgv_classification_schemes` | Classification taxonomies |
| Data Steward Assignment | `dgv_steward_assignments` | Stewardship tracking |
| Data Quality Rule | `dgv_quality_rules` | Quality rule definitions |
| Data Quality Assessment | `dgv_quality_assessments` | Quality measurement results |
| Data Lineage | `dgv_lineage` | Source-to-target data flows |
| Data Dictionary Entry | `dgv_dictionary` | Data element definitions |
| Data Sharing Agreement | `dgv_sharing_agreements` | Data exchange agreements |
| Data Retention Policy | `dgv_retention_policies` | Retention rules |

### Privacy-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Processing Activity | `prv_processing_activities` | RoPA entries |
| PIA/DPIA | `prv_assessments` | Privacy impact assessments |
| PIA/DPIA Finding | `prv_assessment_findings` | Assessment findings |
| Consent Record | `prv_consent_records` | Individual consent tracking |
| Data Subject Request | `prv_dsar` | DSAR lifecycle tracking |
| Privacy Breach | `prv_breaches` | Privacy breach records |
| Privacy Breach Timeline | `prv_breach_timeline` | Breach response timeline |
| Cross-Border Transfer | `prv_cross_border_transfers` | Transfer register |
| Third-Party Processor | `prv_third_party_processors` | Processor management |
| Privacy Risk | `prv_risks` | Privacy-specific risks |

### Compliance-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Compliance Framework | `cmp_frameworks` | Framework definitions |
| Control | `cmp_controls` | Control library |
| Control Mapping | `cmp_control_mappings` | Cross-framework control mapping |
| Compliance Assessment | `cmp_assessments` | Framework assessments |
| Control Assessment | `cmp_control_assessments` | Individual control evaluations |
| Finding | `cmp_findings` | Compliance and audit findings |
| Corrective Action Plan | `cmp_corrective_actions` | Remediation plans |
| Evidence | `cmp_evidence` | Evidence records (metadata; files in shared storage) |
| Evidence-Control Link | `cmp_evidence_controls` | Many-to-many evidence-control mapping |
| Audit Engagement | `cmp_audit_engagements` | Internal audit engagements |
| Regulatory Obligation | `cmp_obligations` | Regulatory requirement tracking |

### Workflow-Owned (beyond shared tables)

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Case | `wfl_cases` | Case tracking records |
| Case Activity | `wfl_case_activities` | Case activity log |
| Escalation Rule | `wfl_escalation_rules` | Escalation configuration |

### Reporting-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| Dashboard | `rpt_dashboards` | Dashboard configurations |
| Dashboard Widget | `rpt_dashboard_widgets` | Widget definitions |
| Report Template | `rpt_report_templates` | Report template definitions |
| Scheduled Report | `rpt_scheduled_reports` | Report schedule configuration |
| Generated Report | `rpt_generated_reports` | Generated report records |
| Saved Filter | `rpt_saved_filters` | User-saved filters |

### Licensing-Owned

| Entity | Table(s) | Notes |
|--------|----------|-------|
| License | `lic_licenses` | Module license records |
| Subscription | `lic_subscriptions` | Subscription tracking |
| Feature Flag | `lic_feature_flags` | Feature toggle definitions |
| User Seat | `lic_user_seats` | Seat allocation tracking |
| License Event | `lic_license_events` | License change audit |
| Usage Metric | `lic_usage_metrics` | Usage metering data |

---

## Integration Points Between Modules

### Cross-Module Integration Matrix

| # | Source Module | Target Module | Integration Type | Data Exchanged | Direction | Trigger |
|---|-------------|---------------|-----------------|----------------|-----------|---------|
| 1 | Cybersecurity | ERM | Event + Reference | Cyber risk details, risk ID | Cyber -> ERM | Cyber risk exceeds threshold, manual escalation |
| 2 | BCM | ERM | Event + Reference | BCM risk details, risk ID | BCM -> ERM | BCM risk identified, post-exercise risk |
| 3 | Privacy | ERM | Event + Reference | Privacy risk details, risk ID | Privacy -> ERM | Privacy risk exceeds threshold, breach impact |
| 4 | Compliance | ERM | Reference | Control effectiveness scores | Compliance -> ERM | Assessment completed |
| 5 | Cybersecurity | Privacy | Event | Cyber incident details (breach potential) | Cyber -> Privacy | Incident classified as potential data breach |
| 6 | Cybersecurity | BCM | Event | Cyber incident details (service disruption) | Cyber -> BCM | Incident causes service disruption |
| 7 | Data Governance | Privacy | Reference | Data asset inventory, classification | DGov -> Privacy | On-demand query; data change notification |
| 8 | Data Governance | Cybersecurity | Reference | Data asset classification | DGov -> Cyber | Asset classification context |
| 9 | Compliance | Cybersecurity | Shared Reference | Shared control library (NCA controls) | Bidirectional | Control library updates |
| 10 | Compliance | Privacy | Shared Reference | Shared control library (PDPL controls) | Bidirectional | Control library updates |
| 11 | Compliance | BCM | Shared Reference | Shared control library (ISO 22301 controls) | Bidirectional | Control library updates |
| 12 | All GRC Modules | Workflow | Service Call | Workflow trigger, entity reference | GRC -> Workflow | Approval needed, task created |
| 13 | Workflow | All GRC Modules | Callback | Approval decision, task status | Workflow -> GRC | Decision made, task completed |
| 14 | All Modules | Reporting | Read-Only Query | Module-specific data | Modules -> Reporting | Dashboard refresh, report generation |
| 15 | Licensing | All Modules | Service Call | Entitlement check result | Licensing -> Modules | Every API request, feature access |
| 16 | Administration | All Modules | Service/Reference | Org hierarchy, users, roles, lookups, audit | Admin -> Modules | On-demand query, cached reference |
| 17 | ERM | Reporting | Data Feed | Risk register, heatmap data, KRIs | ERM -> Reporting | Data change, scheduled refresh |
| 18 | Cybersecurity | Reporting | Data Feed | NCA scorecards, incident data, asset risks | Cyber -> Reporting | Data change, scheduled refresh |
| 19 | BCM | Reporting | Data Feed | BIA data, BCP status, exercise results | BCM -> Reporting | Data change, scheduled refresh |
| 20 | Privacy | Reporting | Data Feed | PDPL compliance, DSAR metrics, breach data | Privacy -> Reporting | Data change, scheduled refresh |

---

## Anti-Corruption Layer Patterns

To maintain module independence while allowing cross-module references, the platform employs anti-corruption layer (ACL) patterns:

### Pattern 1: ID-Only References

When a module needs to reference an entity from another module, it stores only the foreign entity's ID and type. It never stores a copy of the foreign entity's data.

```
Example: ERM Risk references a Cyber Incident
- erm_risks table has: source_module VARCHAR, source_entity_id BIGINT
- Values: source_module='CYBERSECURITY', source_entity_id=12345
- ERM module resolves the reference via a service call to Cybersecurity module
```

### Pattern 2: Shared Interface DTOs

Cross-module data exchange uses shared Data Transfer Objects (DTOs) defined in a common interface package. Each module maps its internal entities to these shared DTOs when communicating.

```
Example: Risk Escalation DTO
- RiskEscalationDTO { sourceModule, sourceEntityId, riskTitle, riskDescription,
                      impactScore, likelihoodScore, category, suggestedTreatment }
- Cybersecurity creates this DTO from its CyberRisk entity
- ERM consumes this DTO and creates its own Risk entity
```

### Pattern 3: Module Service Interfaces

Each module exposes a well-defined service interface that other modules consume. Modules never access each other's database tables directly.

```
Example: CybersecurityModuleService interface
- getCyberAssetById(Long id): CyberAssetSummaryDTO
- getCyberIncidentById(Long id): CyberIncidentSummaryDTO
- getAssetsByCriticality(String criticality): List<CyberAssetSummaryDTO>
```

### Pattern 4: Ownership Boundary Enforcement

Each entity belongs to exactly one module. Even if multiple modules display the same data, only the owning module can modify it. Other modules get read-only views via the owning module's service interface.

```
Example: Compliance Control displayed in Cybersecurity
- Control entity owned by Compliance module (cmp_controls table)
- Cybersecurity module reads controls via ComplianceModuleService
- Cybersecurity cannot modify controls; it links its assessments to control IDs
```

---

## Event-Based vs Direct-Reference Integrations

### Event-Based Integrations

Used when one module's action triggers a process in another module. The source module publishes an event; the target module subscribes and reacts.

| Event | Publisher | Subscriber(s) | Payload | Implementation |
|-------|-----------|---------------|---------|----------------|
| `CyberIncidentCreated` | Cybersecurity | Privacy, BCM | Incident ID, severity, affected assets, classification | Application event (Spring ApplicationEvent) |
| `CyberRiskEscalated` | Cybersecurity | ERM | RiskEscalationDTO | Application event |
| `BCMRiskEscalated` | BCM | ERM | RiskEscalationDTO | Application event |
| `PrivacyRiskEscalated` | Privacy | ERM | RiskEscalationDTO | Application event |
| `PrivacyBreachCreated` | Privacy | ERM | Breach ID, severity, impact scope | Application event |
| `ComplianceAssessmentCompleted` | Compliance | ERM, Reporting | Assessment ID, framework, overall score | Application event |
| `ControlEffectivenessUpdated` | Compliance | ERM, Cybersecurity | Control ID, new effectiveness score | Application event |
| `WorkflowDecisionMade` | Workflow | Source module | Instance ID, decision, comments | Application event (callback) |
| `LicenseStatusChanged` | Licensing | All modules | Organization ID, module, new status | Application event |
| `UserRoleChanged` | Administration | All modules | User ID, old roles, new roles | Application event (cache invalidation) |
| `DataAssetClassificationChanged` | Data Governance | Privacy, Cybersecurity | Asset ID, new classification | Application event |

**Implementation approach:** Spring `ApplicationEventPublisher` for in-process events within a monolithic deployment. If the platform evolves to microservices, these events would be published to a message broker (e.g., Apache Kafka, RabbitMQ).

### Direct-Reference Integrations

Used when a module needs to read data from another module on-demand, without requiring a triggering event.

| Consumer Module | Provider Module | Data Accessed | Access Method |
|----------------|----------------|---------------|---------------|
| ERM | Compliance | Control effectiveness scores | `ComplianceModuleService.getControlEffectiveness(controlId)` |
| ERM | All GRC modules | Escalated risk source details | `ModuleService.getEntitySummary(entityType, entityId)` |
| Privacy | Data Governance | Data asset inventory and classification | `DataGovernanceModuleService.getDataAssets(filter)` |
| Cybersecurity | Data Governance | Data asset classification for security context | `DataGovernanceModuleService.getAssetClassification(assetId)` |
| Reporting | All modules | Module-specific data for dashboards | `ModuleReportingService.getReportData(query)` |
| All modules | Administration | Organization hierarchy, user details | `OrganizationService`, `UserService` |
| All modules | Licensing | Entitlement checks | `LicenseService.isFeatureEnabled(module, feature, orgId)` |
| All modules | Workflow | Workflow status for linked entities | `WorkflowService.getWorkflowStatus(entityType, entityId)` |
| Compliance | Cybersecurity | NCA assessment data | `CybersecurityModuleService.getNCAAssessmentSummary(assessmentId)` |
| Compliance | Privacy | PDPL compliance data | `PrivacyModuleService.getPDPLComplianceStatus(orgId)` |

---

## Module Dependency Graph

The following describes the dependency relationships between modules for diagramming (Mermaid-compatible structure):

```
graph TD
    ADM[Administration & Configuration]
    LIC[Licensing & Subscription]
    WFL[Workflow & Case Management]
    RPT[Reporting & Analytics]
    ERM[Enterprise Risk Management]
    CYB[Cybersecurity Risk & Compliance]
    BCM[Business Continuity Management]
    DGV[Data Governance / Data Office]
    PRV[Privacy & Personal Data Protection]
    CMP[Compliance & Audit Management]

    %% Infrastructure dependencies (all modules depend on these)
    ADM --> |org, users, roles, audit, files, notifications| ERM
    ADM --> |org, users, roles, audit, files, notifications| CYB
    ADM --> |org, users, roles, audit, files, notifications| BCM
    ADM --> |org, users, roles, audit, files, notifications| DGV
    ADM --> |org, users, roles, audit, files, notifications| PRV
    ADM --> |org, users, roles, audit, files, notifications| CMP
    ADM --> |org, users, roles, audit, files, notifications| WFL
    ADM --> |org, users, roles, audit, files, notifications| RPT

    LIC --> |entitlement enforcement| ERM
    LIC --> |entitlement enforcement| CYB
    LIC --> |entitlement enforcement| BCM
    LIC --> |entitlement enforcement| DGV
    LIC --> |entitlement enforcement| PRV
    LIC --> |entitlement enforcement| CMP

    WFL --> |workflow engine| ERM
    WFL --> |workflow engine| CYB
    WFL --> |workflow engine| BCM
    WFL --> |workflow engine| DGV
    WFL --> |workflow engine| PRV
    WFL --> |workflow engine| CMP

    %% Domain-specific dependencies
    CYB --> |risk escalation| ERM
    BCM --> |risk escalation| ERM
    PRV --> |risk escalation| ERM
    CMP --> |control effectiveness| ERM

    CYB --> |incident: potential breach| PRV
    CYB --> |incident: service disruption| BCM

    DGV --> |data inventory, classification| PRV
    DGV --> |asset classification| CYB

    CMP --> |shared control library| CYB
    CMP --> |shared control library| PRV
    CMP --> |shared control library| BCM

    %% Reporting aggregation
    ERM --> |risk data| RPT
    CYB --> |cyber data| RPT
    BCM --> |BCM data| RPT
    DGV --> |data gov data| RPT
    PRV --> |privacy data| RPT
    CMP --> |compliance data| RPT
    WFL --> |workflow metrics| RPT
```

### Dependency Layers

**Layer 0 -- Platform Infrastructure (always present):**
- Administration & Configuration
- Licensing & Subscription
- Workflow & Case Management

**Layer 1 -- Domain Modules (independently licensable):**
- Enterprise Risk Management
- Cybersecurity Risk & Compliance
- Business Continuity Management
- Data Governance / Data Office
- Privacy & Personal Data Protection
- Compliance & Audit Management

**Layer 2 -- Aggregation (activated by licensed modules):**
- Reporting & Analytics

### Dependency Rules

1. **Layer 0 modules have no dependencies on Layer 1 or Layer 2 modules.** They provide services consumed by upper layers.
2. **Layer 1 modules depend on Layer 0 modules** for infrastructure services (auth, workflow, licensing, audit).
3. **Layer 1 modules have optional dependencies on other Layer 1 modules.** These activate only when both modules are licensed. When a dependency target is not licensed, the feature gracefully degrades (e.g., no cross-module risk escalation, no data inventory lookup).
4. **Layer 2 (Reporting) depends on Layer 0 and reads from all licensed Layer 1 modules.** It adapts its available dashboards based on which modules are licensed.
5. **No circular dependencies exist.** All cross-module references are unidirectional or event-based.

---

## Data Isolation Principles

| Principle | Description |
|-----------|-------------|
| **Organization isolation** | All data is scoped to an organization. Cross-organization data access is prohibited except for Platform Administrators. Every query includes an organization filter. |
| **Module table isolation** | Each module owns its database tables with a module prefix (e.g., `erm_`, `cyb_`, `bcm_`). No module directly queries another module's tables. |
| **Service-mediated access** | Cross-module data access is always through service interfaces, never through direct database joins. |
| **Event-driven coupling** | Modules that trigger processes in other modules use events, not direct method calls on internal services. |
| **Graceful degradation** | When a dependency module is not licensed, the dependent feature is hidden or shows a "module not licensed" message. No errors or broken functionality. |
| **Audit independence** | Each module writes to the shared audit log but can only query its own audit entries (plus shared infrastructure entries). |
| **File isolation** | File attachments are stored with module and entity references. Access is controlled by the owning module's authorization rules. |

---

## Schema Naming Conventions

| Convention | Pattern | Example |
|------------|---------|---------|
| **Shared tables** | No prefix or `sys_` prefix | `organizations`, `users`, `sys_audit_logs` |
| **Module tables** | Three-letter module prefix + underscore | `erm_risks`, `cyb_assets`, `bcm_plans` |
| **Junction tables** | `{module}_{entity1}_{entity2}` | `cmp_evidence_controls`, `erm_risk_categories` |
| **History/audit tables** | `{module}_{entity}_history` | `erm_risk_assessment_history` |
| **Workflow tables** | `wfl_` prefix | `wfl_workflow_definitions`, `wfl_tasks` |
| **Reporting tables** | `rpt_` prefix | `rpt_dashboards`, `rpt_report_templates` |
| **Licensing tables** | `lic_` prefix | `lic_licenses`, `lic_feature_flags` |
| **Lookup tables** | `lkp_` prefix | `lkp_categories`, `lkp_values` |

All tables include: `id` (BIGINT, PK), `organization_id` (BIGINT, FK -- except system-level tables), `created_by`, `created_at`, `updated_by`, `updated_at`, `is_deleted` (soft delete flag).
