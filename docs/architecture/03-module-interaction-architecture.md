# 03 - Module Interaction Architecture

## Purpose

This document defines how the ten modules within the TSS GRC platform communicate with each other, share services, and maintain bounded-context integrity. It establishes the interaction patterns, dependency graph, anti-corruption mechanisms, and the evolution path toward asynchronous messaging.

---

## 1. Interaction Model Overview

All module interactions occur within a single JVM. TSS uses three interaction patterns, ordered by preference:

| Pattern | When to Use | Coupling Level |
|---|---|---|
| **Domain Events** | Module A notifies others of a state change; no response needed | Lowest |
| **Shared Service Calls** | Module accesses a platform capability (audit, workflow, notification) | Low |
| **Direct Service Calls** | Module A needs data or behavior from Module B synchronously | Medium |

### Guiding Principles

1. **Prefer events over direct calls.** If module A does not need a synchronous response from module B, publish an event.
2. **Reference by ID, not by object graph.** Modules never hold direct references to another module's domain entities.
3. **Shared services are infrastructure, not domain.** Workflow, audit, and notification are platform capabilities, not owned by any single business module.
4. **No circular dependencies.** If module A calls module B, module B must not call module A directly. Use events to break cycles.

---

## 2. Direct Service Calls

### Pattern

Module A injects module B's application service interface and calls it synchronously. The call executes within the same JVM process and can participate in the same transaction if needed.

```
[ERM Module]                      [Admin Module]
     |                                  |
     |  ermService.createRisk(dto)      |
     |  --> needs org structure         |
     |  ----> organizationService       |
     |        .getBusinessUnit(id)  --->|
     |  <---- BusinessUnitResponse <----|
     |                                  |
```

### Rules for Direct Calls

1. **Call the application service, never the repository.** Module A calls `organizationService.getBusinessUnit(id)`, not `businessUnitRepository.findById(id)`.
2. **Receive DTOs, not entities.** The called service returns a DTO (e.g., `BusinessUnitResponse`), never a JPA entity.
3. **Read-heavy, write-light.** Direct calls are primarily for reading reference data. Writing to another module's data should go through that module's service.
4. **Transaction boundaries.** The calling service owns its transaction. If the called service needs its own transaction (e.g., audit logging), it uses `REQUIRES_NEW` propagation.

### Permitted Direct Call Scenarios

| Caller Module | Called Module/Service | Purpose |
|---|---|---|
| Any business module | `admin` - `OrganizationService` | Resolve organization, business unit, department names |
| Any business module | `admin` - `UserService` | Resolve user names, roles, assignments |
| Any business module | `admin` - `LookupService` | Fetch lookup values (risk categories, severity levels) |
| `erm` | `cyber` - `CyberControlService` | Fetch control implementation status for risk treatment |
| `reporting` | Any module's query service | Fetch data for dashboards and reports |
| `workflow` | Any module's entity service | Fetch entity details for workflow display |
| `audit` (compliance) | Any module's assessment service | Fetch assessment data for audit findings |

---

## 3. Shared Services

Shared services are platform-level capabilities consumed by all business modules. They live in the `com.tss.platform` package namespace and are not owned by any single business module.

### 3.1 AuditService

**Package**: `com.tss.platform.audit`

| Method | Purpose |
|---|---|
| `logChange(AuditEntry entry)` | Record entity create/update/delete with before/after snapshots |
| `logAction(String module, String action, String details)` | Record non-entity actions (login, export, bulk operations) |
| `getAuditTrail(String entityType, Long entityId)` | Retrieve audit history for a specific entity |
| `searchAuditLogs(AuditSearchCriteria criteria)` | Search audit logs by date, user, module, action |

**Consumed by**: All modules (automatically via JPA entity listeners and AOP)

### 3.2 WorkflowService

**Package**: `com.tss.platform.workflow`

| Method | Purpose |
|---|---|
| `startWorkflow(String definitionKey, String entityType, Long entityId)` | Initiate a workflow instance for an entity |
| `submitStep(Long instanceId, Long stepId, WorkflowAction action, String comments)` | Submit approval/rejection for a workflow step |
| `delegate(Long stepId, Long toUserId)` | Delegate a step to another user |
| `getActiveSteps(Long userId)` | Get all pending workflow steps assigned to a user |
| `getWorkflowHistory(String entityType, Long entityId)` | Get complete workflow history for an entity |

**Consumed by**: `erm`, `cyber`, `bcm`, `privacy`, `audit`, `datagov`

### 3.3 NotificationService

**Package**: `com.tss.platform.notification`

| Method | Purpose |
|---|---|
| `send(NotificationRequest request)` | Send a notification (email, in-app, or both) |
| `sendTemplated(String templateKey, Map<String, Object> variables, Long recipientId)` | Send a notification using a named template |
| `sendBulk(String templateKey, Map<String, Object> variables, List<Long> recipientIds)` | Bulk notification to multiple users |
| `getUnread(Long userId)` | Get unread in-app notifications for a user |
| `markRead(Long notificationId)` | Mark a notification as read |

**Consumed by**: All modules (directly and via event listeners)

### 3.4 FileStorageService

**Package**: `com.tss.platform.storage`

| Method | Purpose |
|---|---|
| `store(FileUploadRequest request)` | Store a file, return metadata with storage key |
| `retrieve(String storageKey)` | Retrieve file content by storage key |
| `delete(String storageKey)` | Delete a stored file |
| `getMetadata(String storageKey)` | Get file metadata without content |
| `listByEntity(String entityType, Long entityId)` | List all attachments for an entity |

**Consumed by**: All modules (evidence attachments, report exports, document uploads)

### 3.5 LocalizationService

**Package**: `com.tss.platform.i18n`

| Method | Purpose |
|---|---|
| `getMessage(String key, Locale locale, Object... args)` | Resolve a message key to localized text |
| `getTranslation(String tableName, Long entityId, String fieldName, Locale locale)` | Fetch a translated field value from the translation table |
| `saveTranslation(TranslationRequest request)` | Save a translation for an entity field |
| `getSupportedLocales()` | Return the list of supported locales |
| `resolveUserLocale(Long userId)` | Resolve the preferred locale for a user |

**Consumed by**: All modules (UI rendering, notification templates, report generation)

### 3.6 CacheService

**Package**: `com.tss.platform.cache`

Implemented via Spring Cache abstraction with Caffeine as the backing provider. Not a service class per se, but a configuration and set of cache definitions used by `@Cacheable` annotations.

| Cache Name | TTL | Max Size | Content |
|---|---|---|---|
| `lookups` | 30 minutes | 10,000 | Lookup table values |
| `permissions` | 5 minutes | 5,000 | User permission sets |
| `organizationTree` | 15 minutes | 1,000 | Organization/BU hierarchy |
| `translations` | 30 minutes | 50,000 | Translation table entries |
| `workflowDefinitions` | 60 minutes | 500 | Workflow definition metadata |
| `systemConfig` | 60 minutes | 1,000 | System configuration values |

### 3.7 ExportService

**Package**: `com.tss.platform.export`

| Method | Purpose |
|---|---|
| `exportToExcel(ExportRequest request)` | Generate an Excel (.xlsx) file from tabular data |
| `exportToPdf(PdfExportRequest request)` | Generate a PDF with RTL support |
| `exportToWord(WordExportRequest request)` | Generate a Word (.docx) document |
| `exportToCsv(ExportRequest request)` | Generate a CSV file |

**Consumed by**: `reporting`, `erm`, `cyber`, `bcm`, `privacy`, `audit`, `datagov`

---

## 4. Domain Events

### Pattern

When a module completes a significant state change, it publishes a domain event via Spring's `ApplicationEventPublisher`. Other modules that need to react subscribe with `@TransactionalEventListener` or `@EventListener`.

```
[ERM Module]                           [Event Bus]                        [Cyber Module]
     |                                      |                                   |
     | riskService.createRisk(dto)          |                                   |
     |   --> save Risk entity               |                                   |
     |   --> publish RiskCreatedEvent  ---->|                                   |
     |                                      |---> @EventListener           ---->|
     |                                      |     cyberRiskCorrelationListener   |
     |                                      |     .onRiskCreated(event)          |
```

### Event Catalog

| Event | Published By | Payload | Subscribers |
|---|---|---|---|
| `RiskCreatedEvent` | `erm` | riskId, riskType, businessUnitId, severityLevel | `cyber` (control mapping), `reporting` (dashboard refresh) |
| `RiskAssessedEvent` | `erm` | riskId, previousScore, newScore, assessorId | `reporting` (trend update), `notification` (if threshold crossed) |
| `RiskTreatmentUpdatedEvent` | `erm` | riskId, treatmentPlanId, status | `audit` (compliance tracking) |
| `IncidentReportedEvent` | `cyber` | incidentId, severity, affectedAssets | `erm` (risk re-evaluation), `bcm` (continuity impact), `notification` (alerts) |
| `IncidentEscalatedEvent` | `cyber` | incidentId, escalationLevel, previousLevel | `notification` (escalation alerts), `workflow` (new approval step) |
| `IncidentResolvedEvent` | `cyber` | incidentId, resolutionSummary, resolutionDate | `erm` (risk update), `reporting` (incident metrics) |
| `AssessmentCompletedEvent` | `cyber`, `privacy`, `audit` | assessmentId, entityType, score, status | `reporting` (compliance dashboard), `erm` (risk input) |
| `ControlUpdatedEvent` | `cyber`, `audit` | controlId, previousStatus, newStatus | `erm` (residual risk recalc), `reporting` (control dashboard) |
| `BCMPlanActivatedEvent` | `bcm` | planId, activationReason, activatedBy | `notification` (crisis alerts), `erm` (risk impact) |
| `BCMTestCompletedEvent` | `bcm` | planId, testId, testResult | `audit` (evidence), `reporting` (BCM metrics) |
| `PrivacyBreachDetectedEvent` | `privacy` | breachId, severity, affectedDataSubjects | `notification` (breach alerts), `cyber` (incident correlation), `audit` (compliance) |
| `DPIACompletedEvent` | `privacy` | assessmentId, riskLevel, outcome | `erm` (privacy risk input), `audit` (evidence) |
| `WorkflowStepCompletedEvent` | `workflow` | instanceId, stepId, action, completedBy | `notification` (next assignee), originating module (status update) |
| `WorkflowCompletedEvent` | `workflow` | instanceId, entityType, entityId, finalStatus | Originating module (entity status update), `audit` (approval evidence) |
| `SLABreachEvent` | `workflow` | instanceId, stepId, dueDate, breachDuration | `notification` (escalation), `reporting` (SLA metrics) |
| `DataClassificationChangedEvent` | `datagov` | assetId, previousClassification, newClassification | `privacy` (impact assessment), `cyber` (control review) |
| `AuditFindingCreatedEvent` | `audit` | findingId, severity, assignedTo | `notification` (finding alerts), `erm` (risk input) |
| `ComplianceObligationDueEvent` | `audit` | obligationId, dueDate, status | `notification` (reminders), `reporting` (compliance status) |

### Event Design Rules

1. **Events are immutable.** Once published, an event's payload cannot be modified.
2. **Events carry IDs, not objects.** An event includes `riskId`, not the full `Risk` entity. Subscribers query their own module for additional data if needed.
3. **Events are past-tense named.** `RiskCreatedEvent`, not `CreateRiskEvent`. They describe something that already happened.
4. **Listeners must be idempotent.** A listener may be invoked more than once (retry scenarios). Side effects must be safe to repeat.
5. **Listeners must not throw exceptions that roll back the publisher's transaction.** Use `@TransactionalEventListener(phase = AFTER_COMMIT)` for decoupled processing.

### Event Base Class

```java
public abstract class DomainEvent extends ApplicationEvent {
    private final String eventId;          // UUID for deduplication
    private final Instant occurredAt;
    private final String module;           // Publishing module name
    private final String entityType;       // e.g., "Risk", "CyberIncident"
    private final Long entityId;
    private final Long tenantId;
    private final Long triggeredByUserId;
}
```

---

## 5. Module Dependency Graph

### Direct Service Dependencies

```
                    ┌──────────────┐
                    │    admin     │ (Organization, User, Lookup, Config)
                    └──────┬───────┘
                           │
          ┌────────────────┼────────────────────────┐
          │                │                         │
          v                v                         v
    ┌──────────┐    ┌──────────┐    ┌──────────┐  ┌──────────┐
    │   erm    │    │  cyber   │    │   bcm    │  │ datagov  │
    └────┬─────┘    └────┬─────┘    └──────────┘  └──────────┘
         │               │
         v               v
    ┌──────────┐    ┌──────────┐
    │ privacy  │    │  audit   │
    └──────────┘    └──────────┘

    ┌──────────────┐
    │  reporting   │ -----reads-from-----> ALL modules (read-only queries)
    └──────────────┘

    ┌──────────────┐
    │  licensing   │ -----checks-----> ALL modules (feature entitlement)
    └──────────────┘
```

### Shared Service Dependency Matrix

| Module | AuditService | WorkflowService | NotificationService | FileStorageService | LocalizationService | ExportService |
|---|---|---|---|---|---|---|
| `erm` | Auto | Yes | Yes | Yes | Yes | Yes |
| `cyber` | Auto | Yes | Yes | Yes | Yes | Yes |
| `bcm` | Auto | Yes | Yes | Yes | Yes | Yes |
| `datagov` | Auto | Yes | Yes | Yes | Yes | Yes |
| `privacy` | Auto | Yes | Yes | Yes | Yes | Yes |
| `audit` | Auto | Yes | Yes | Yes | Yes | Yes |
| `workflow` | Auto | -- | Yes | No | Yes | No |
| `reporting` | Auto | No | Yes | Yes | Yes | Yes |
| `licensing` | Auto | No | No | No | Yes | No |
| `admin` | Auto | No | Yes | Yes | Yes | No |

_Auto = AuditService is consumed automatically via JPA entity listeners; no explicit service call needed._

---

## 6. Anti-Corruption Patterns

### 6.1 ID-Based References

Modules reference entities from other modules by ID only. They never hold a direct JPA association to a foreign module's entity.

```java
// CORRECT: ERM module references organization by ID
@Entity
@Table(schema = "erm")
public class Risk {
    @Column(name = "business_unit_id", nullable = false)
    private Long businessUnitId;  // References admin.business_units.id

    // No @ManyToOne to BusinessUnit entity from admin module
}

// INCORRECT: Direct JPA association across module boundaries
@Entity
@Table(schema = "erm")
public class Risk {
    @ManyToOne
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;  // VIOLATION: crosses module boundary
}
```

### 6.2 DTO Boundaries at Module Interfaces

When module A calls module B's service, the response is always a DTO, never a JPA entity.

```java
// Module B (admin) exposes a service interface
public interface OrganizationQueryService {
    BusinessUnitResponse getBusinessUnit(Long id);
    List<BusinessUnitResponse> getBusinessUnitsByOrganization(Long orgId);
}

// Module A (erm) consumes the DTO
public class RiskService {
    private final OrganizationQueryService organizationQueryService;

    public RiskDetailResponse getRiskDetail(Long riskId) {
        Risk risk = riskRepository.findById(riskId).orElseThrow();
        BusinessUnitResponse bu = organizationQueryService
            .getBusinessUnit(risk.getBusinessUnitId());
        return riskMapper.toDetailResponse(risk, bu);
    }
}
```

### 6.3 Lookup Caching at Module Boundary

Modules that frequently resolve foreign IDs (e.g., organization names for display) use cached lookups to avoid repeated cross-module calls.

```java
@Service
public class OrganizationLookupCache {
    @Cacheable(value = "organizationTree", key = "#businessUnitId")
    public String getBusinessUnitName(Long businessUnitId, Locale locale) {
        BusinessUnitResponse bu = organizationQueryService.getBusinessUnit(businessUnitId);
        return locale.getLanguage().equals("ar") ? bu.nameAr() : bu.nameEn();
    }
}
```

### 6.4 Event-Based State Synchronization

When a module needs a denormalized copy of another module's data for query performance (e.g., reporting needs risk counts per business unit), it listens to events and maintains its own read model.

```java
@Component
public class ReportingRiskSyncListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onRiskCreated(RiskCreatedEvent event) {
        reportingDataService.incrementRiskCount(
            event.getBusinessUnitId(), event.getSeverityLevel());
    }
}
```

---

## 7. Future Microservice Extraction

### Phase 1: Current State (Modular Monolith)

- All events are Spring `ApplicationEvent` instances, delivered synchronously within the JVM
- All service calls are direct method invocations
- All modules share a single `ApplicationContext`

### Phase 2: Event Bus Extraction

Replace Spring's in-process event bus with a message broker for selected high-traffic event flows:

| Change | Implementation |
|---|---|
| Event publication | Swap `ApplicationEventPublisher` for a `DomainEventPublisher` abstraction that writes to RabbitMQ/Kafka |
| Event consumption | Swap `@EventListener` for message listener containers |
| Event serialization | Events are already POJOs with ID-based payloads; serialize to JSON |
| Ordering | Partition by `entityId` to maintain per-entity ordering |
| Idempotency | Already required by current design; no change needed |

### Phase 3: Service Extraction

Extract individual modules into independent deployable services:

| Step | Action |
|---|---|
| 1 | Identify extraction candidate (e.g., `reporting`, `workflow`) |
| 2 | Extract module's database schema to a dedicated database |
| 3 | Replace direct service calls with REST/gRPC client calls behind the existing service interface |
| 4 | Replace in-process events with message broker events (if not done in Phase 2) |
| 5 | Deploy the extracted module as an independent Spring Boot application |
| 6 | Route traffic through API gateway |

### Extraction Readiness Checklist

- [ ] All inter-module communication goes through service interfaces (not internal classes)
- [ ] All cross-module data references use IDs, not JPA associations
- [ ] All events carry serializable payloads (IDs, primitives, simple types)
- [ ] Each module's database schema is self-contained (no cross-schema foreign keys)
- [ ] Each module's Flyway migrations are independent
- [ ] ArchUnit tests verify module boundaries are not violated

---

## 8. Interaction Anti-Patterns

The following patterns are explicitly prohibited:

| Anti-Pattern | Why It Is Prohibited | Correct Approach |
|---|---|---|
| Cross-module repository access | Bypasses business rules, creates hidden coupling | Call the other module's application service |
| Cross-module JPA associations | Creates schema coupling, prevents independent deployment | Use ID references and resolve via service calls |
| Shared mutable state | Race conditions, unclear ownership | Each module owns its data; sync via events |
| Circular service dependencies | Deadlocks, unclear call hierarchy | Break cycle with events; module A publishes, module B listens |
| Passing JPA entities across module boundaries | Lazy loading failures, transaction scope issues | Map to DTOs at module boundary |
| Cross-schema SQL joins | Tight coupling at database level | Application-level joins via service calls or reporting views (read-only) |
| Direct event listener modifications of the publisher's data | Transaction contamination | Listeners should modify only their own module's data |

---

## 9. Exception: Reporting Views

The reporting module has a controlled exception to the "no cross-schema joins" rule. SQL Server views in the `reporting` schema may join across module schemas for read-only dashboards and reports.

### Rules for Reporting Views

1. Views are strictly read-only. No INSERT, UPDATE, or DELETE through views.
2. Views are defined in the `reporting` schema and owned by the reporting module's Flyway migrations.
3. Views use explicit column lists (no `SELECT *`) to decouple from schema changes.
4. View definitions are versioned and tested against sample data in CI.
5. Business modules must not depend on reporting views for their own operations.

```sql
-- Example: Unified risk view across modules
CREATE VIEW reporting.v_unified_risk_dashboard AS
SELECT
    r.id AS risk_id,
    r.title_en,
    r.title_ar,
    r.risk_score,
    r.status,
    bu.name_en AS business_unit_en,
    bu.name_ar AS business_unit_ar,
    'ERM' AS source_module
FROM erm.risks r
JOIN admin.business_units bu ON bu.id = r.business_unit_id
UNION ALL
SELECT
    ci.id AS risk_id,
    ci.title_en,
    ci.title_ar,
    ci.severity_score AS risk_score,
    ci.status,
    bu.name_en AS business_unit_en,
    bu.name_ar AS business_unit_ar,
    'CYBER' AS source_module
FROM cyber.incidents ci
JOIN admin.business_units bu ON bu.id = ci.business_unit_id;
```

This controlled exception exists because application-level joins for complex analytical queries would be prohibitively slow and complex. The views provide a read-only, denormalized query surface without compromising module write-path independence.
