# 05 - Domain-Driven Decomposition

## Purpose

This document defines the Domain-Driven Design (DDD) approach for the TSS GRC platform. It maps bounded contexts to modules, identifies aggregate roots, defines domain events and value objects, and establishes the shared kernel. This decomposition drives the package structure, database schema boundaries, and inter-module communication patterns.

---

## 1. Strategic Design: Bounded Contexts

Each TSS module corresponds to a bounded context with its own ubiquitous language, domain model, and data ownership. The bounded context map below shows the relationships between contexts.

### Bounded Context Map

```
                          ┌─────────────────────────────────┐
                          │         SHARED KERNEL            │
                          │  Organization, BusinessUnit,     │
                          │  User, Control, LookupValue      │
                          └──────────────┬──────────────────┘
                                         │
         ┌───────────┬──────────┬────────┼────────┬──────────┬───────────┐
         │           │          │        │        │          │           │
    ┌────▼────┐ ┌────▼────┐ ┌──▼───┐ ┌──▼───┐ ┌──▼────┐ ┌──▼────┐ ┌───▼────┐
    │   ERM   │ │  CYBER  │ │ BCM  │ │PRIV- │ │COMP-  │ │DATA-  │ │WORKFLOW│
    │         │ │         │ │      │ │ ACY  │ │LIANCE │ │ GOV   │ │        │
    │Risk Mgmt│ │Cyber Sec│ │Bus.  │ │Data  │ │Audit &│ │Data   │ │Approval│
    │Assess.  │ │Incident │ │Cont. │ │Prot. │ │Compli.│ │Govern.│ │Process │
    │Treatment│ │Compliance│ │Plan  │ │DPIA  │ │Assess.│ │Catalog│ │SLA     │
    └─────────┘ └─────────┘ └──────┘ └──────┘ └───────┘ └───────┘ └────────┘
         │           │          │        │        │          │
         │     Conformist       │   Customer/    │    Published    │
         │◄─────────►│          │   Supplier     │    Language     │
         │  (shared  │          │◄──────►│       │◄──────────►│
         │  controls)│          │        │        │           │
```

### Context Relationship Types

| Relationship | Between | Pattern | Description |
|---|---|---|---|
| **Shared Kernel** | All modules and Platform | Shared ownership | Organization, User, Control entities are co-owned |
| **Conformist** | ERM and Cyber | ERM conforms to Cyber's control model | ERM accepts Cyber's control definitions for risk treatment |
| **Customer/Supplier** | Privacy and BCM | Privacy is customer, BCM is supplier | Privacy consumes BCM's continuity impact data |
| **Published Language** | All modules and Reporting | Reporting reads all, writes none | Reporting consumes standardized DTOs from all modules |
| **Anti-Corruption Layer** | All modules and Admin | ID-based references | Modules reference Admin entities by ID, not by direct association |
| **Partnership** | Cyber and Compliance/Audit | Bidirectional event exchange | Cyber findings feed audit; audit controls feed cyber |

---

## 2. Bounded Context Definitions

### 2.1 Enterprise Risk Management (ERM)

**Package**: `com.tss.module.erm`
**Schema**: `erm`
**Ubiquitous Language**: Risk, Risk Register, Risk Assessment, Risk Treatment, Risk Appetite, Inherent Risk, Residual Risk, Risk Owner, Risk Category, Likelihood, Impact, Risk Score, Heat Map

| Aggregate Root | Description | Key Entities/VOs |
|---|---|---|
| `Risk` | A potential event that could impact the organization | `RiskAssessment`, `RiskTreatment`, `RiskScore` (VO), `RiskCategory` |
| `RiskRegister` | A collection of risks organized by scope | `RiskRegisterEntry` |
| `RiskAppetite` | The organization's tolerance for risk | `AppetiteThreshold` (VO), `RiskAppetiteStatement` |

### 2.2 Cybersecurity Risk & Compliance (Cyber)

**Package**: `com.tss.module.cyber`
**Schema**: `cyber`
**Ubiquitous Language**: Cyber Incident, Security Control, Compliance Framework, Control Implementation, Vulnerability, Threat, Asset, Security Assessment, ECC Domain, SAMA Control

| Aggregate Root | Description | Key Entities/VOs |
|---|---|---|
| `CyberIncident` | A security event requiring investigation and response | `IncidentTimeline`, `IncidentResponse`, `SeverityLevel` (VO) |
| `SecurityControl` | A safeguard or countermeasure | `ControlImplementation`, `ControlEvidence`, `ComplianceStatus` (VO) |
| `ComplianceFramework` | A regulatory or standards framework (ECC, SAMA, ISO 27001) | `FrameworkDomain`, `FrameworkControl`, `FrameworkRequirement` |
| `SecurityAssessment` | An evaluation of control effectiveness | `AssessmentFinding`, `AssessmentScore` |

### 2.3 Business Continuity Management (BCM)

**Package**: `com.tss.module.bcm`
**Schema**: `bcm`
**Ubiquitous Language**: BIA, BCP, DR Plan, RTO, RPO, MTPD, Crisis, Exercise, Recovery Strategy, Critical Process, Continuity Plan

| Aggregate Root | Description | Key Entities/VOs |
|---|---|---|
| `BCMPlan` | A business continuity plan for a business unit or process | `PlanActivity`, `RecoveryProcedure`, `PlanStatus` |
| `BusinessImpactAnalysis` | Assessment of impact from process disruption | `BIAProcess`, `RecoveryObjective` (VO: RTO, RPO, MTPD) |
| `BCMExercise` | A test or exercise of a continuity plan | `ExerciseScenario`, `ExerciseResult`, `ExerciseFinding` |
| `DRPlan` | IT disaster recovery plan | `DRProcedure`, `DRTest` |

### 2.4 Privacy & Personal Data Protection (Privacy)

**Package**: `com.tss.module.privacy`
**Schema**: `privacy`
**Ubiquitous Language**: Personal Data, Data Subject, DPIA, Consent, Data Breach, Processing Activity, Legal Basis, Data Transfer, Retention Period, PDPL Requirement

| Aggregate Root | Description | Key Entities/VOs |
|---|---|---|
| `PrivacyAssessment` | Data Protection Impact Assessment (DPIA) | `AssessmentQuestion`, `AssessmentRisk`, `AssessmentOutcome` |
| `ProcessingActivity` | A record of personal data processing | `DataCategory`, `LegalBasis` (VO), `RetentionPeriod` (VO) |
| `DataSubjectRequest` | A request from a data subject (access, deletion, etc.) | `RequestType`, `RequestStatus`, `RequestResponse` |
| `PrivacyBreach` | A personal data breach incident | `BreachNotification`, `AffectedDataSubjects`, `BreachSeverity` (VO) |
| `ConsentRecord` | A record of data subject consent | `ConsentPurpose`, `ConsentStatus` |

### 2.5 Compliance & Audit (Audit)

**Package**: `com.tss.module.audit`
**Schema**: `audit`
**Ubiquitous Language**: Audit Plan, Audit Finding, Compliance Obligation, Remediation, Audit Universe, Control Testing, Compliance Assessment, Audit Observation

| Aggregate Root | Description | Key Entities/VOs |
|---|---|---|
| `ComplianceAssessment` | An evaluation against a compliance framework | `AssessmentControl`, `ComplianceScore`, `ComplianceStatus` (VO) |
| `AuditPlan` | A planned audit engagement | `AuditScope`, `AuditSchedule`, `AuditTeam` |
| `AuditFinding` | An observation or non-conformity from an audit | `FindingSeverity` (VO), `RemediationAction`, `RemediationStatus` |
| `ComplianceObligation` | A regulatory or contractual compliance requirement | `ObligationSchedule`, `ObligationStatus` |

### 2.6 Data Governance (DataGov)

**Package**: `com.tss.module.datagov`
**Schema**: `datagov`
**Ubiquitous Language**: Data Asset, Data Classification, Data Catalog, Data Quality, Data Steward, Data Lineage, Data Dictionary, NDMO Requirement

| Aggregate Root | Description | Key Entities/VOs |
|---|---|---|
| `DataAsset` | A registered data asset in the catalog | `DataClassification` (VO), `DataSteward`, `DataQualityMetric` |
| `DataPolicy` | A data governance policy or standard | `PolicyRule`, `PolicyScope`, `PolicyCompliance` |
| `DataQualityRule` | A rule for measuring data quality | `QualityDimension`, `QualityThreshold`, `QualityMeasurement` |

---

## 3. Shared Kernel

The shared kernel contains entities and value objects that are genuinely cross-cutting and co-owned by multiple bounded contexts. The shared kernel is kept minimal to reduce coupling.

### Package: `com.tss.platform.shared`

### Shared Entities

| Entity | Schema | Purpose | Referenced By |
|---|---|---|---|
| `Organization` | `platform` | Top-level tenant organization | All modules |
| `BusinessUnit` | `platform` | Organizational unit hierarchy | All modules (risk ownership, scope) |
| `Department` | `platform` | Departmental grouping within a business unit | All modules |
| `User` | `platform` | System user with profile and preferences | All modules (owners, assignees, reviewers) |
| `Role` | `platform` | Permission role definition | All modules (authorization) |
| `Control` | `platform` | Shared control definition (cross-framework) | `cyber`, `erm`, `audit`, `privacy` |
| `LookupType` | `platform` | Configurable lookup categories | All modules |
| `LookupValue` | `platform` | Lookup values within a type | All modules |

### Why These Entities Are Shared

- **Organization/BusinessUnit/Department**: Define the organizational scope for every business entity. Every risk, incident, plan, and assessment belongs to an organization and business unit.
- **User**: Every action in the system is performed by a user. Ownership, assignment, and audit attribution require a shared user identity.
- **Control**: Controls are the central linking concept in GRC. A single control may be relevant to cybersecurity compliance (ECC), risk treatment (ERM), audit testing, and privacy safeguards.
- **LookupType/LookupValue**: Centralized configurable enumerations (severity levels, status values, categories) used consistently across modules.

### Shared Kernel Rules

1. Changes to shared kernel entities require agreement from all consuming modules (coordinated change).
2. Shared entities are read by all modules but written only through the `admin` module's services.
3. Modules reference shared entities by ID, using cached lookups for display names.
4. The shared kernel must not grow beyond genuinely cross-cutting concepts. If only two modules share a concept, it belongs in one module with an event-based sync to the other.

---

## 4. Domain Events

### Event Naming Convention

All domain events follow the pattern `{Entity}{PastTenseAction}Event`. Events describe something that has already happened.

### Complete Event Catalog

#### ERM Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `RiskCreatedEvent` | riskId, riskType, businessUnitId, severityLevel, tenantId | New risk registered |
| `RiskAssessedEvent` | riskId, previousScore, newScore, assessorId, assessmentDate | Risk assessment completed |
| `RiskTreatmentUpdatedEvent` | riskId, treatmentPlanId, previousStatus, newStatus | Treatment plan status changed |
| `RiskEscalatedEvent` | riskId, previousSeverity, newSeverity, escalatedBy | Risk severity increased beyond threshold |
| `RiskClosedEvent` | riskId, closureReason, closedBy | Risk accepted or mitigated and closed |

#### Cyber Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `IncidentReportedEvent` | incidentId, severity, type, affectedAssets, reportedBy | New incident reported |
| `IncidentEscalatedEvent` | incidentId, previousLevel, newLevel, escalatedBy | Incident escalated to higher severity |
| `IncidentResolvedEvent` | incidentId, resolutionType, resolutionDate, resolvedBy | Incident resolved |
| `ControlStatusChangedEvent` | controlId, frameworkId, previousStatus, newStatus | Control implementation status updated |
| `AssessmentCompletedEvent` | assessmentId, frameworkId, score, assessorId | Framework assessment completed |

#### BCM Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `BCMPlanActivatedEvent` | planId, activationReason, activatedBy, businessUnitId | Continuity plan activated (real incident) |
| `BCMPlanDeactivatedEvent` | planId, deactivationReason, deactivatedBy | Plan deactivated (incident resolved) |
| `BCMTestCompletedEvent` | planId, testId, testResult, conductedBy | Plan exercise/test completed |
| `BIACompletedEvent` | biaId, businessUnitId, completedBy | Business impact analysis completed |

#### Privacy Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `PrivacyBreachDetectedEvent` | breachId, severity, affectedCount, detectedBy | Data breach detected |
| `PrivacyBreachNotifiedEvent` | breachId, notifiedAuthority, notificationDate | Authority notified of breach |
| `DPIACompletedEvent` | assessmentId, riskLevel, outcome, completedBy | DPIA completed |
| `DataSubjectRequestReceivedEvent` | requestId, requestType, dataSubjectId | DSR received |
| `DataSubjectRequestCompletedEvent` | requestId, requestType, completedBy, responseDate | DSR fulfilled |

#### Compliance & Audit Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `AuditFindingCreatedEvent` | findingId, auditPlanId, severity, assignedTo | Audit finding recorded |
| `AuditFindingRemediatedEvent` | findingId, remediationId, status, remediatedBy | Finding remediation completed |
| `ComplianceAssessmentCompletedEvent` | assessmentId, frameworkId, score, status | Compliance assessment completed |
| `ComplianceObligationDueEvent` | obligationId, dueDate, status | Obligation approaching due date |
| `ControlUpdatedEvent` | controlId, previousStatus, newStatus, updatedBy | Shared control status updated |

#### Data Governance Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `DataClassificationChangedEvent` | assetId, previousClassification, newClassification, changedBy | Data asset reclassified |
| `DataQualityIssueDetectedEvent` | assetId, ruleId, dimension, score | Quality threshold breached |
| `DataPolicyViolationEvent` | policyId, assetId, violationType, detectedBy | Policy rule violation detected |

#### Workflow Events

| Event | Payload Fields | Trigger |
|---|---|---|
| `WorkflowStartedEvent` | instanceId, definitionKey, entityType, entityId, initiatedBy | Workflow instance created |
| `WorkflowStepCompletedEvent` | instanceId, stepId, action, completedBy, comments | Approval/rejection step completed |
| `WorkflowCompletedEvent` | instanceId, entityType, entityId, finalStatus | All workflow steps completed |
| `SLABreachEvent` | instanceId, stepId, dueDate, breachDuration | Workflow step SLA exceeded |
| `WorkflowDelegatedEvent` | instanceId, stepId, fromUserId, toUserId | Step delegated to another user |

### Event Base Class

```java
public abstract class DomainEvent extends ApplicationEvent {

    private final String eventId;            // UUID, generated on creation
    private final Instant occurredAt;        // Timestamp of occurrence
    private final String module;             // e.g., "erm", "cyber", "bcm"
    private final String entityType;         // e.g., "Risk", "CyberIncident"
    private final Long entityId;             // ID of the affected entity
    private final Long tenantId;             // Tenant scope
    private final Long triggeredByUserId;    // User who triggered the action

    protected DomainEvent(Object source, String module, String entityType,
                          Long entityId, Long tenantId, Long triggeredByUserId) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.module = module;
        this.entityType = entityType;
        this.entityId = entityId;
        this.tenantId = tenantId;
        this.triggeredByUserId = triggeredByUserId;
    }
}
```

---

## 5. Value Objects

Value objects are immutable types defined by their attributes rather than identity. They encapsulate domain concepts that appear across multiple entities.

### Cross-Module Value Objects (Shared Kernel)

| Value Object | Fields | Used By | Description |
|---|---|---|---|
| `DateRange` | `startDate`, `endDate` | All modules | A temporal range with validation (start <= end) |
| `BilingualText` | `en`, `ar` | All modules | A text value with English and Arabic representations |
| `AuditMetadata` | `createdAt`, `createdBy`, `updatedAt`, `updatedBy` | All entities | Standard audit timestamps embedded in every entity |

### ERM Value Objects

| Value Object | Fields | Description |
|---|---|---|
| `RiskScore` | `likelihood` (1-5), `impact` (1-5), `score` (calculated) | Quantified risk level; score = likelihood x impact |
| `RiskAppetiteThreshold` | `riskCategory`, `acceptableScore`, `tolerableScore`, `unacceptableScore` | Thresholds that define risk tolerance per category |

### Cyber Value Objects

| Value Object | Fields | Description |
|---|---|---|
| `SeverityLevel` | `level` (CRITICAL, HIGH, MEDIUM, LOW, INFO), `numericValue` | Incident or vulnerability severity classification |
| `ComplianceStatus` | `status` (COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT, NOT_ASSESSED), `score` | Control or framework compliance state |
| `ControlMaturity` | `level` (INITIAL, REPEATABLE, DEFINED, MANAGED, OPTIMIZED) | CMM-based control maturity level |

### BCM Value Objects

| Value Object | Fields | Description |
|---|---|---|
| `RecoveryObjective` | `rto` (Duration), `rpo` (Duration), `mtpd` (Duration) | Recovery time/point objectives for a process |
| `ExerciseResult` | `outcome` (PASS, PARTIAL_PASS, FAIL), `score`, `findings` | Result of a BCM exercise |

### Privacy Value Objects

| Value Object | Fields | Description |
|---|---|---|
| `LegalBasis` | `basis` (CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTEREST, PUBLIC_INTEREST, LEGITIMATE_INTEREST) | PDPL legal basis for processing |
| `RetentionPeriod` | `duration`, `unit` (DAYS, MONTHS, YEARS), `justification` | Data retention requirement |
| `BreachSeverity` | `level`, `affectedCount`, `dataCategories`, `crossBorder` | Severity classification of a data breach |

### Compliance & Audit Value Objects

| Value Object | Fields | Description |
|---|---|---|
| `ComplianceScore` | `score` (0-100), `level` (COMPLIANT, PARTIALLY, NON_COMPLIANT) | Quantified compliance level |
| `FindingSeverity` | `level` (CRITICAL, MAJOR, MINOR, OBSERVATION), `priority` | Audit finding severity classification |
| `RemediationStatus` | `status` (OPEN, IN_PROGRESS, COMPLETED, OVERDUE, ACCEPTED), `dueDate` | Remediation tracking state |

### Value Object Implementation Pattern

```java
@Embeddable
public record RiskScore(
    @Column(name = "likelihood_rating") int likelihood,
    @Column(name = "impact_rating") int impact,
    @Column(name = "risk_score") int score
) {
    public RiskScore {
        if (likelihood < 1 || likelihood > 5) {
            throw new IllegalArgumentException("Likelihood must be between 1 and 5");
        }
        if (impact < 1 || impact > 5) {
            throw new IllegalArgumentException("Impact must be between 1 and 5");
        }
        score = likelihood * impact;
    }

    public static RiskScore of(int likelihood, int impact) {
        return new RiskScore(likelihood, impact, likelihood * impact);
    }

    public SeverityLevel toSeverityLevel() {
        if (score >= 20) return SeverityLevel.CRITICAL;
        if (score >= 15) return SeverityLevel.HIGH;
        if (score >= 8) return SeverityLevel.MEDIUM;
        if (score >= 4) return SeverityLevel.LOW;
        return SeverityLevel.VERY_LOW;
    }
}
```

---

## 6. Module Package Structure

### Standard Module Layout

Every module follows this identical package structure, ensuring consistency across the platform:

```
com.tss.module.{module}
│
├── web/                                    # Presentation Layer
│   ├── controller/                         # @Controller (Thymeleaf pages)
│   │   ├── {Entity}PageController.java
│   │   └── ...
│   ├── api/                                # @RestController (JSON APIs)
│   │   ├── {Entity}ApiController.java
│   │   └── ...
│   └── viewmodel/                          # View-specific models
│
├── application/                            # Application Layer
│   ├── service/                            # Application services
│   │   ├── {Entity}Service.java
│   │   └── ...
│   ├── dto/
│   │   ├── request/                        # Inbound DTOs
│   │   │   ├── {Entity}CreateRequest.java
│   │   │   ├── {Entity}UpdateRequest.java
│   │   │   └── {Entity}FilterRequest.java
│   │   └── response/                       # Outbound DTOs
│   │       ├── {Entity}SummaryResponse.java
│   │       └── {Entity}DetailResponse.java
│   ├── mapper/                             # MapStruct mappers
│   │   └── {Entity}Mapper.java
│   ├── validator/                          # Custom validators
│   └── specification/                      # JPA Specification builders
│       └── {Entity}Specifications.java
│
├── domain/                                 # Domain Layer
│   ├── model/                              # Entities and aggregate roots
│   │   ├── {AggregateRoot}.java
│   │   └── {ChildEntity}.java
│   ├── valueobject/                        # Value objects
│   │   └── {ValueObject}.java
│   ├── event/                              # Domain events
│   │   └── {Entity}{Action}Event.java
│   ├── service/                            # Domain services
│   ├── repository/                         # Repository interfaces (contracts)
│   │   └── {Entity}Repository.java
│   └── enums/                              # Domain enumerations
│       └── {Entity}Status.java
│
└── infrastructure/                         # Infrastructure Layer
    ├── persistence/
    │   └── repository/                     # Spring Data JPA implementations
    │       └── {Entity}JpaRepository.java
    ├── config/                             # Module-specific configuration
    └── adapter/                            # External system adapters
```

### Platform Package Structure

```
com.tss.platform
│
├── shared/                                 # Shared Kernel
│   ├── model/                              # Organization, User, Control, Lookup
│   ├── dto/                                # Shared DTOs
│   ├── service/                            # Shared services (OrganizationService, UserService)
│   └── repository/                         # Shared repositories
│
├── audit/                                  # AuditService, entity listeners
├── workflow/                               # WorkflowService, state machine
├── notification/                           # NotificationService, templates
├── storage/                                # FileStorageService, implementations
├── export/                                 # ExportService (PDF, Excel, Word)
├── i18n/                                   # LocalizationService, message sources
├── security/                               # Spring Security, permission evaluator
├── cache/                                  # Cache configuration
├── exception/                              # Exception hierarchy, global handler
├── logging/                                # MDC, structured logging
├── config/                                 # Application-wide configuration
└── web/                                    # Filters, interceptors, CORS
```

---

## 7. Aggregate Design Rules

### Aggregate Boundaries

1. **Consistency boundary**: An aggregate defines a transaction boundary. Changes within an aggregate are atomic.
2. **Small aggregates**: Prefer small aggregates. An aggregate should include only entities that must be consistent within a single transaction.
3. **Reference by ID**: Aggregates in different bounded contexts reference each other by ID, never by direct object association.
4. **Aggregate root as gateway**: External code accesses the aggregate only through its root entity. Child entities are not directly accessible from outside the aggregate.

### Example: Risk Aggregate

```
Risk (Aggregate Root)
├── RiskAssessment (entity within aggregate, owned by Risk)
├── RiskTreatment (entity within aggregate, owned by Risk)
├── RiskScore (value object, embedded)
├── businessUnitId (ID reference to Admin bounded context)
├── riskOwnerId (ID reference to Admin bounded context)
└── riskCategoryId (ID reference to shared Lookup)
```

### Cross-Aggregate References

```java
// Risk aggregate references BusinessUnit by ID only
@Entity
@Table(schema = "erm")
public class Risk {
    @Id @GeneratedValue
    private Long id;

    @Column(name = "business_unit_id", nullable = false)
    private Long businessUnitId;        // ID reference, NOT @ManyToOne

    @Column(name = "risk_owner_id")
    private Long riskOwnerId;           // ID reference, NOT @ManyToOne

    @Embedded
    private RiskScore riskScore;        // Value object, embedded

    @OneToMany(mappedBy = "risk", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskAssessment> assessments;    // Within aggregate boundary

    @OneToMany(mappedBy = "risk", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskTreatment> treatments;      // Within aggregate boundary
}
```

---

## 8. Context Mapping Patterns

### Pattern Usage

| Pattern | Implementation in TSS | Example |
|---|---|---|
| **Shared Kernel** | `com.tss.platform.shared` entities | Organization, User, Control entities shared by all modules |
| **Anti-Corruption Layer** | DTO translation at service boundaries | ERM does not use Cyber's entity classes; it receives DTOs |
| **Published Language** | Standardized event payloads and API DTOs | All events carry a common `DomainEvent` base with module/entity/ID |
| **Conformist** | ERM accepts Cyber's control model as-is | ERM links risk treatments to controls defined by Cyber |
| **Customer/Supplier** | Privacy requests BIA data from BCM | BCM exposes a read-only query service for BIA results |
| **Open Host Service** | REST APIs with versioned contracts | Each module exposes `/api/v1/{module}/...` for external and cross-module access |

### Dependency Direction

Dependencies flow from specialized modules toward shared/foundational modules:

```
Specialized:    privacy, audit, reporting
       |
       v
Core Domain:    erm, cyber, bcm, datagov
       |
       v
Foundation:     admin, workflow, platform (shared kernel)
```

No upward dependencies are permitted. If a foundation module needs to react to a specialized module's events, it subscribes to events rather than calling the specialized module directly.
