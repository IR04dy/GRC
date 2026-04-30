# Core Entities List

## 1. Purpose

This document provides a complete inventory of all entities in the TSS GRC platform, organized by module and schema. Each entity includes its description, aggregate root status, and estimated row volume classification.

## 2. Volume Classifications

| Classification | Expected Rows per Tenant |
|---|---|
| Low | < 1,000 |
| Medium | 1,000 -- 100,000 |
| High | 100,000 -- 10,000,000 |
| Very High | > 10,000,000 |

---

## 3. Platform Core (`platform` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | Lookup | Master lookup/reference data entry | Yes | Low |
| 2 | LookupValue | Individual value within a lookup with translations | No | Medium |
| 3 | LookupCategory | Grouping category for lookups | No | Low |
| 4 | Translation | Localized string for any translatable field | No | High |
| 5 | Attachment | File metadata with storage reference | Yes | High |
| 6 | AttachmentLink | Polymorphic link between attachment and owning entity | No | High |
| 7 | Notification | Platform notification record | Yes | Very High |
| 8 | NotificationTemplate | Template for generating notifications | No | Low |
| 9 | NotificationRecipient | Delivery record per recipient | No | Very High |
| 10 | Tag | User-defined tag/label | Yes | Low |
| 11 | TagAssignment | Association of tag to any entity | No | Medium |
| 12 | Comment | User comment on any entity | Yes | High |
| 13 | SystemConfiguration | Global platform configuration key-value pairs | No | Low |
| 14 | EmailTemplate | Email template with localized body | No | Low |
| 15 | Country | Country reference data | No | Low |
| 16 | City | City reference data | No | Low |
| 17 | Currency | Currency reference data | No | Low |

---

## 4. Identity & Access Management (`iam` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | User | Platform user identity and profile | Yes | Medium |
| 2 | UserCredential | Password hash, MFA secrets | No | Medium |
| 3 | UserSession | Active login session | No | High |
| 4 | UserPreference | User-specific settings (language, timezone, theme) | No | Medium |
| 5 | Role | Named role with description | Yes | Low |
| 6 | RolePermission | Permission assigned to a role | No | Medium |
| 7 | UserRole | Role assigned to a user (with optional scope) | No | Medium |
| 8 | Permission | Granular permission definition (module + action + resource) | Yes | Low |
| 9 | PermissionGroup | Logical grouping of related permissions | No | Low |
| 10 | ApiKey | API key for service-to-service authentication | No | Low |
| 11 | ExternalIdentityProvider | SSO/SAML/OIDC provider configuration per tenant | No | Low |
| 12 | UserExternalIdentity | Link between user and external identity | No | Medium |
| 13 | PasswordPolicy | Tenant-level password complexity and expiry rules | No | Low |
| 14 | LoginAttempt | Record of login attempts for security monitoring | No | Very High |
| 15 | MfaDevice | Registered MFA device for a user | No | Low |

---

## 5. Administration (`admin` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | Tenant | Top-level tenant/customer account | Yes | Low |
| 2 | TenantSetting | Tenant-specific configuration overrides | No | Low |
| 3 | TenantLicense | License entitlement and module activation | No | Low |
| 4 | Organization | Legal entity within a tenant | Yes | Low |
| 5 | BusinessUnit | Organizational subdivision | Yes | Low |
| 6 | Department | Department within a business unit | No | Low |
| 7 | Location | Physical or logical site/location | No | Low |
| 8 | OrganizationHierarchy | Closure table for org tree traversal | No | Low |
| 9 | ModuleActivation | Which modules are enabled per tenant | No | Low |
| 10 | DelegationRule | Authority delegation rules for absence/escalation | No | Low |
| 11 | FiscalPeriod | Fiscal year and period definitions | No | Low |

---

## 6. Workflow Engine (`workflow` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | WorkflowDefinition | Template defining process steps and transitions | Yes | Low |
| 2 | WorkflowStep | Individual step/state in a workflow definition | No | Low |
| 3 | WorkflowTransition | Allowed transition between steps with conditions | No | Low |
| 4 | WorkflowInstance | Running instance of a workflow | Yes | High |
| 5 | WorkflowInstanceStep | Current/completed step in an instance | No | High |
| 6 | Task | Assignable work item within a workflow | Yes | High |
| 7 | TaskComment | Comment on a task | No | High |
| 8 | TaskAttachment | Attachment linked to a task | No | Medium |
| 9 | EscalationRule | Time-based escalation configuration | No | Low |
| 10 | SlaDefinition | SLA thresholds for task/workflow completion | No | Low |
| 11 | ApprovalDecision | Record of an approval/rejection action | No | High |

---

## 7. Risk Management (`risk` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | Risk | Individual risk in the register | Yes | Medium |
| 2 | RiskCategory | Hierarchical risk taxonomy | No | Low |
| 3 | RiskAssessment | Point-in-time risk evaluation (inherent + residual) | Yes | Medium |
| 4 | RiskTreatment | Planned treatment/mitigation action | Yes | Medium |
| 5 | RiskTreatmentAction | Specific action step within a treatment | No | Medium |
| 6 | RiskAppetite | Organizational risk appetite threshold | Yes | Low |
| 7 | RiskAppetiteStatement | Detailed appetite per risk category | No | Low |
| 8 | RiskCriteria | Likelihood and impact scale definitions | No | Low |
| 9 | RiskHeatMapConfig | Heat map dimension and color configuration | No | Low |
| 10 | RiskIndicator | Key Risk Indicator (KRI) definition | No | Low |
| 11 | RiskIndicatorValue | KRI measurement value over time | No | High |
| 12 | RiskControlMapping | Link between risk and mitigating control | No | Medium |
| 13 | RiskAssetMapping | Link between risk and affected asset | No | Medium |
| 14 | RiskOwnerHistory | Historical record of risk ownership changes | No | Medium |
| 15 | RiskScenario | Detailed risk scenario narrative | No | Medium |

---

## 8. Cyber Security (`cyber` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | Asset | IT asset (hardware, software, service, data) | Yes | Medium |
| 2 | AssetCategory | Asset classification taxonomy | No | Low |
| 3 | AssetOwnership | Ownership and custodianship of an asset | No | Medium |
| 4 | Vulnerability | Known vulnerability record | Yes | High |
| 5 | VulnerabilityScan | Scan execution record | No | Medium |
| 6 | VulnerabilityFinding | Individual finding from a scan | No | High |
| 7 | ThreatScenario | Modeled threat with TTPs | Yes | Low |
| 8 | ThreatActor | Threat actor profile | No | Low |
| 9 | SecurityIncident | Security incident record | Yes | Medium |
| 10 | SecurityIncidentTimeline | Timeline entry for incident progression | No | Medium |
| 11 | SecurityControl | Technical/operational security control | Yes | Medium |
| 12 | SecurityControlAssessment | Periodic assessment of control effectiveness | No | Medium |
| 13 | AssetDependency | Dependency relationship between assets | No | Medium |
| 14 | PatchRecord | Patch application record for an asset | No | High |

---

## 9. Business Continuity Management (`bcm` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | BusinessImpactAnalysis | BIA for a business process/function | Yes | Low |
| 2 | BiaProcess | Business process evaluated in a BIA | No | Medium |
| 3 | BiaImpactRating | Impact rating per time interval for a process | No | Medium |
| 4 | BusinessContinuityPlan | BCP document with scope and procedures | Yes | Low |
| 5 | BcpProcedure | Recovery procedure step within a BCP | No | Medium |
| 6 | RecoveryPlan | Specific recovery plan for a resource | Yes | Low |
| 7 | RecoveryObjective | RTO/RPO targets per resource | No | Low |
| 8 | BCMExercise | Continuity exercise/drill record | Yes | Low |
| 9 | ExerciseResult | Outcome and lessons from an exercise | No | Low |
| 10 | CrisisManagementTeam | Crisis team composition | No | Low |
| 11 | CrisisTeamMember | Individual member of a crisis team | No | Low |
| 12 | ResourceDependency | Dependency between processes and resources | No | Medium |
| 13 | CallTree | Communication/call tree for crisis activation | No | Low |
| 14 | CallTreeNode | Individual node in a call tree | No | Low |

---

## 10. Privacy Management (`privacy` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | DataInventoryItem | Record of personal data processing (RoPA entry) | Yes | Medium |
| 2 | DataCategory | Category of personal data processed | No | Low |
| 3 | ProcessingActivity | Description of a processing activity | No | Medium |
| 4 | PrivacyImpactAssessment | PIA/DPIA record | Yes | Low |
| 5 | PiaQuestion | Question within a PIA template | No | Low |
| 6 | PiaResponse | Response to a PIA question | No | Medium |
| 7 | ConsentRecord | Data subject consent record | Yes | High |
| 8 | ConsentPurpose | Purpose for which consent was obtained | No | Low |
| 9 | DataSubjectRequest | DSR (access, erasure, portability) | Yes | Medium |
| 10 | PrivacyBreach | Personal data breach record | Yes | Low |
| 11 | BreachNotification | Notification sent to authority/data subject | No | Low |
| 12 | DataTransfer | Cross-border or third-party data transfer record | No | Medium |
| 13 | PrivacyPolicy | Published privacy policy version | No | Low |
| 14 | ThirdPartyProcessor | External processor/sub-processor record | No | Low |

---

## 11. Compliance Management (`compliance` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | ComplianceFramework | Regulatory/standards framework definition | Yes | Low |
| 2 | FrameworkVersion | Specific version of a framework | No | Low |
| 3 | FrameworkDomain | Top-level domain/chapter within a framework | No | Low |
| 4 | FrameworkRequirement | Individual requirement/control within a framework | No | Medium |
| 5 | ControlLibrary | Master control library | Yes | Low |
| 6 | Control | Individual control definition | Yes | Medium |
| 7 | ControlFamily | Grouping of related controls | No | Low |
| 8 | ControlObjective | Objective a control aims to achieve | No | Low |
| 9 | ControlFrameworkMapping | Mapping of control to framework requirement | No | Medium |
| 10 | ComplianceAssessment | Assessment cycle against a framework | Yes | Low |
| 11 | AssessmentScope | Scope definition for an assessment | No | Low |
| 12 | AssessmentResponse | Response to a specific requirement in an assessment | No | High |
| 13 | Finding | Identified gap or non-conformity | Yes | Medium |
| 14 | FindingSeverity | Severity classification for findings | No | Low |
| 15 | RemediationAction | Corrective action to address a finding | Yes | Medium |
| 16 | Evidence | Evidence artifact linked to an assessment response | No | High |
| 17 | EvidenceRequest | Request for evidence from a stakeholder | No | Medium |
| 18 | ComplianceObligation | Regulatory obligation tracked for the organization | No | Low |

---

## 12. Reporting & Analytics (`reporting` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | Dashboard | Named dashboard with layout | Yes | Low |
| 2 | DashboardWidget | Individual widget on a dashboard | No | Low |
| 3 | ReportDefinition | Saved report template | Yes | Low |
| 4 | ReportParameter | Parameter definition for a report | No | Low |
| 5 | ReportSchedule | Scheduled execution of a report | No | Low |
| 6 | ReportExecution | Record of a report generation run | No | High |
| 7 | Metric | KPI/metric definition | Yes | Low |
| 8 | MetricValue | Time-series value for a metric | No | Very High |
| 9 | MetricThreshold | Threshold/target for a metric | No | Low |
| 10 | DataSnapshot | Periodic snapshot of aggregate data | No | High |

---

## 13. Audit Trail (`audit` schema)

| # | Entity | Description | Aggregate Root | Volume |
|---|---|---|---|---|
| 1 | AuditEntry | Immutable record of a user/system action | Yes | Very High |
| 2 | AuditEntryDetail | Field-level change detail within an entry | No | Very High |
| 3 | AuditCategory | Classification of audit events | No | Low |
| 4 | DataAccessLog | Record of sensitive data access events | No | Very High |
| 5 | ExportLog | Record of data export operations | No | Medium |
| 6 | LoginAudit | Authentication event log (mirrors iam.LoginAttempt for immutability) | No | Very High |

---

## 14. Entity Count Summary

| Schema | Entity Count |
|---|---|
| platform | 17 |
| iam | 15 |
| admin | 11 |
| workflow | 11 |
| risk | 15 |
| cyber | 14 |
| bcm | 14 |
| privacy | 14 |
| compliance | 18 |
| reporting | 10 |
| audit | 6 |
| **Total** | **145** |
