# 02 - Logical Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the logical layering of the TSS GRC platform, establishing the four-layer architecture, dependency rules, and package mapping that govern how code is organized within each module and across the platform.

---

## 1. Architectural Layers

TSS adopts a strict four-layer architecture. Each layer has a clearly defined responsibility and a unidirectional dependency rule: layers depend only on the layer directly below them. The domain layer sits at the center and has zero infrastructure dependencies.

```
+---------------------------------------------------------------+
|                    PRESENTATION LAYER                          |
|  Thymeleaf templates, REST controllers, static assets, i18n   |
+---------------------------------------------------------------+
         |  depends on  |
         v              v
+---------------------------------------------------------------+
|                    APPLICATION LAYER                           |
|  Service classes, DTOs, validation, transaction management     |
+---------------------------------------------------------------+
         |  depends on  |
         v              v
+---------------------------------------------------------------+
|                      DOMAIN LAYER                              |
|  Entities, value objects, domain events, business rules        |
+---------------------------------------------------------------+
         ^              ^
         |  implemented by  |
+---------------------------------------------------------------+
|                   INFRASTRUCTURE LAYER                         |
|  Repositories, external clients, file storage, email           |
+---------------------------------------------------------------+

+---------------------------------------------------------------+
|                    CROSS-CUTTING CONCERNS                      |
|  Security, logging, audit, caching, exception handling         |
+---------------------------------------------------------------+
```

---

## 2. Presentation Layer

### Responsibility

Handles all user-facing concerns: HTTP request routing, view rendering, content negotiation, and locale management. This layer translates user intent into application service calls and formats responses for the client.

### Components

| Component | Implementation | Purpose |
|---|---|---|
| **Page Controllers** | `@Controller` classes | Serve Thymeleaf HTML pages, handle form submissions |
| **REST Controllers** | `@RestController` classes | Serve JSON/XML API responses for programmatic access and htmx |
| **Thymeleaf Templates** | `.html` files in `src/main/resources/templates` | Server-rendered HTML with layout dialect |
| **Static Assets** | CSS, JS, images in `src/main/resources/static` | Bootstrap 5 RTL, htmx, Chart.js, custom styles |
| **Message Bundles** | `messages_en.properties`, `messages_ar.properties` | Externalized UI text for bilingual support |
| **Locale Resolution** | `LocaleChangeInterceptor` + `CookieLocaleResolver` | Detects and switches user locale (AR/EN) |
| **View Models** | Lightweight POJOs | Shape data specifically for view rendering |

### Design Decisions

**Decision: Dual controller pattern (Page + REST)**
- `@Controller` classes return Thymeleaf view names for full-page navigation
- `@RestController` classes return JSON for htmx partial updates and external API consumers
- Both delegate to the same application services, ensuring a single source of truth for business logic
- Rationale: separating concerns allows the HTML UI and API to evolve independently without duplicating service-layer code

**Decision: Cookie-based locale resolution**
- User's locale preference stored in a cookie (`TSS_LOCALE`)
- Authenticated users also have a locale preference persisted in the database
- Locale cookie is set on login from user profile and can be switched at any time via the UI
- Rationale: cookies survive session expiry and work for unauthenticated pages (login, public reports)

**Decision: Layout dialect for template inheritance**
- A base layout template (`layout/default.html`) defines the page skeleton (header, sidebar, footer)
- Individual pages define only their content fragment
- Rationale: eliminates duplication across hundreds of pages, ensures consistent chrome

### Package Mapping

```
com.tss.module.{module}.web
    ├── controller/          # @Controller classes (HTML pages)
    ├── api/                 # @RestController classes (JSON API)
    ├── dto/                 # Request/Response DTOs (may also live in application layer)
    └── viewmodel/           # View-specific model classes
```

### Rules

1. Controllers must never contain business logic. They delegate to application services.
2. Controllers must never access repositories directly.
3. DTOs or view models are the only objects passed to templates -- never domain entities.
4. All user-facing strings must come from message bundles, never hardcoded in templates or controllers.

---

## 3. Application Layer

### Responsibility

Orchestrates use cases. Each application service method represents a single use case (e.g., "create a risk", "approve a workflow step", "generate a compliance report"). This layer manages transactions, validates input, coordinates domain operations, and publishes events.

### Components

| Component | Implementation | Purpose |
|---|---|---|
| **Application Services** | `@Service` + `@Transactional` classes | Use case orchestration, transaction boundaries |
| **DTOs** | Plain Java records or classes | Data transfer between layers, API contracts |
| **Validators** | Jakarta Bean Validation + custom `@Constraint` | Input validation before domain operations |
| **Mappers** | MapStruct interfaces | Entity-to-DTO and DTO-to-entity conversion |
| **Event Publishers** | `ApplicationEventPublisher` | Publish domain events after successful operations |
| **Specifications** | JPA `Specification<T>` classes | Dynamic query building for search/filter |

### Design Decisions

**Decision: Service-per-aggregate transaction boundary**
- Each aggregate root (e.g., `Risk`, `CyberIncident`, `BCMPlan`) has a dedicated application service
- `@Transactional` is applied at the service method level, not at the repository level
- Rationale: ensures each use case runs in a single transaction; the service controls commit/rollback semantics

**Decision: DTOs as records where possible**
- Java records provide immutable, concise data carriers with built-in `equals`/`hashCode`
- Request DTOs use records with Jakarta Validation annotations
- Response DTOs use records for serialization
- Rationale: reduces boilerplate, enforces immutability, and makes API contracts explicit

**Decision: MapStruct over manual mapping**
- MapStruct generates type-safe mapping code at compile time
- Custom mapping methods handle bilingual field mapping (e.g., `nameEn`/`nameAr` to localized `name`)
- Rationale: eliminates runtime reflection overhead (unlike ModelMapper), catches mapping errors at compile time, and handles the bilingual field pattern consistently

### Package Mapping

```
com.tss.module.{module}.application
    ├── service/             # Application service classes
    ├── dto/                 # Request and response DTOs
    │   ├── request/         # Inbound DTOs (create, update commands)
    │   └── response/        # Outbound DTOs (query results)
    ├── mapper/              # MapStruct mapper interfaces
    ├── validator/           # Custom validation constraints
    └── specification/       # JPA Specification builders for dynamic queries
```

### Rules

1. Application services must not hold domain state between calls. They are stateless.
2. Application services must never expose domain entities to the presentation layer. Always map to DTOs.
3. Transactions start and end at the application service boundary.
4. Cross-module calls go through the other module's application service interface, never its repositories.
5. Event publication happens within the transaction boundary to ensure events are published only on successful commit.

---

## 4. Domain Layer

### Responsibility

Contains the core business rules, domain logic, and domain model. This layer is technology-agnostic: it has no dependencies on Spring, JPA annotations (beyond minimal `@Entity`/`@Id`), databases, or external frameworks. It represents the business reality of GRC operations.

### Components

| Component | Implementation | Purpose |
|---|---|---|
| **Entities** | JPA `@Entity` classes | Persistent domain objects with identity |
| **Aggregate Roots** | Entity classes marked as aggregate boundaries | Consistency boundaries for transactions |
| **Value Objects** | Immutable classes or records | Concepts defined by attributes, not identity |
| **Domain Events** | POJO classes extending `ApplicationEvent` or custom marker | Notifications of significant domain state changes |
| **Domain Services** | Plain Java classes | Business logic that does not belong to a single entity |
| **Enumerations** | Java `enum` types | Fixed sets of domain values (status, severity, etc.) |
| **Repository Interfaces** | Java interfaces (domain-side contract) | Abstraction for data access, defined in domain, implemented in infrastructure |

### Design Decisions

**Decision: JPA annotations on domain entities (pragmatic DDD)**
- Pure DDD advocates separating the domain model from persistence annotations entirely
- TSS adopts a pragmatic approach: `@Entity`, `@Id`, `@Column`, and relationship annotations live on domain entities
- Rationale: maintaining a separate persistence model doubles the entity count and mapping code with minimal benefit for a monolith where Hibernate is the sole persistence provider. The trade-off is acceptable because the domain layer still has no dependency on Spring services or infrastructure classes.

**Decision: Domain events as Spring ApplicationEvents**
- Domain events implement a `DomainEvent` marker interface and extend Spring's `ApplicationEvent`
- Events are published via `ApplicationEventPublisher` after the aggregate mutation
- Rationale: Spring's event system provides synchronous in-process delivery with `@TransactionalEventListener` support, ensuring events fire only after commit. This can be replaced with a message broker for async delivery in Phase 2.

**Decision: Repository interfaces defined in domain, implemented in infrastructure**
- The domain layer defines repository interfaces (e.g., `RiskRepository`)
- The infrastructure layer provides Spring Data JPA implementations
- Rationale: the domain layer depends on abstractions, not concrete data access. This preserves the dependency inversion principle and makes the domain testable without a database.

### Package Mapping

```
com.tss.module.{module}.domain
    ├── model/               # Entity classes, aggregate roots
    ├── valueobject/         # Value objects (RiskScore, SeverityLevel, etc.)
    ├── event/               # Domain event classes
    ├── service/             # Domain services (pure business logic)
    ├── repository/          # Repository interfaces (contracts)
    └── enums/               # Domain enumerations
```

### Rules

1. The domain layer must not import any Spring service or infrastructure class.
2. The domain layer must not depend on the application, presentation, or infrastructure layers.
3. Domain entities must enforce their own invariants. An entity must never be in an invalid state.
4. Aggregate roots are the only entry point for modifying enclosed entities.
5. Value objects are immutable. Equality is based on attributes, not identity.
6. Domain services contain logic that spans multiple entities but does not fit in a single entity.

---

## 5. Infrastructure Layer

### Responsibility

Provides concrete implementations of technical concerns: database access, external service integration, file system operations, email delivery, and any other I/O operation. This layer implements the interfaces defined in the domain and application layers.

### Components

| Component | Implementation | Purpose |
|---|---|---|
| **JPA Repositories** | Spring Data JPA `@Repository` interfaces | Concrete data access implementations |
| **Custom Repository Impls** | Classes implementing custom query methods | Complex queries beyond Spring Data's method naming |
| **External Service Clients** | `RestClient` / `WebClient` wrappers | Integration with LDAP, SMS gateways, external APIs |
| **File Storage** | `FileStorageService` implementation | Local filesystem or S3-compatible object storage |
| **Email Service** | `JavaMailSender` wrapper | SMTP email delivery |
| **Cache Configuration** | Spring `@Cacheable`, Caffeine config | Cache definitions and eviction policies |
| **Persistence Configuration** | `DataSource`, `EntityManagerFactory`, schema config | Database connection, schema routing, dialect settings |
| **Flyway Migrations** | SQL migration scripts per module | Schema versioning and evolution |

### Design Decisions

**Decision: Spring Data JPA for standard CRUD, custom implementations for complex queries**
- Standard CRUD and simple queries use Spring Data's derived query methods and `@Query` annotations
- Complex dynamic queries (multi-criteria search, reporting aggregations) use custom repository implementations with `Criteria API` or native SQL
- Rationale: Spring Data eliminates boilerplate for 80% of queries; custom implementations handle the remaining 20% without forcing everything through method naming conventions

**Decision: Local filesystem as default file storage with abstract interface**
- `FileStorageService` interface abstracts storage operations (store, retrieve, delete)
- Default implementation writes to a configurable local directory with structured paths (`/{tenant}/{module}/{entity_type}/{entity_id}/{filename}`)
- An S3-compatible implementation can be swapped in for SaaS deployments
- Rationale: on-premises customers may not have object storage; the abstraction allows deployment-specific storage without code changes

**Decision: Flyway migrations organized per module**
- Each module owns its migration scripts: `db/migration/{module}/V{version}__{description}.sql`
- Migrations create and modify tables within the module's schema only
- Rationale: aligns with schema-per-module strategy; each module's schema evolves independently

### Package Mapping

```
com.tss.module.{module}.infrastructure
    ├── persistence/         # Spring Data JPA repository implementations
    │   ├── repository/      # JPA repository interfaces extending Spring Data
    │   └── entity/          # JPA-specific entity mappings (if separated from domain)
    ├── client/              # External service clients (REST, LDAP, SMS)
    ├── storage/             # File storage implementations
    ├── config/              # Module-specific infrastructure configuration
    └── adapter/             # Adapters for external systems
```

### Rules

1. Infrastructure classes implement interfaces defined in the domain or application layer.
2. Infrastructure code is never called directly by the presentation layer.
3. External system failures must be handled gracefully with retries, circuit breakers, or fallback behavior.
4. All database migrations must be forward-only. Rollback is handled by compensating migrations, not by deleting migration files.

---

## 6. Cross-Cutting Concerns

Cross-cutting concerns are implemented as aspects, filters, interceptors, or shared library classes that apply uniformly across all modules and layers.

### 6.1 Security

| Mechanism | Implementation | Scope |
|---|---|---|
| Authentication | Spring Security filter chain | All HTTP requests |
| Authorization | `@PreAuthorize`, custom `PermissionEvaluator` | Service methods, URL patterns |
| CSRF Protection | Spring Security CSRF token | All state-changing requests |
| Session Management | Spring Session (local or Redis-backed) | Session fixation protection, concurrent session control |
| Input Sanitization | OWASP HTML Sanitizer | All user-submitted text fields |

### 6.2 Logging

| Mechanism | Implementation | Scope |
|---|---|---|
| Structured Logging | SLF4J + Logback with JSON encoder | All application logs |
| MDC Context | `tenant_id`, `user_id`, `session_id`, `request_id` in MDC | Every log statement |
| Request Logging | `CommonsRequestLoggingFilter` or custom filter | All HTTP requests (method, URI, status, duration) |
| Sensitive Data Masking | Custom Logback converter | Passwords, tokens, PII in log output |

### 6.3 Audit

| Mechanism | Implementation | Scope |
|---|---|---|
| Entity Auditing | JPA `@EntityListeners(AuditingEntityListener.class)` | Created/modified timestamps and users on all entities |
| Change Logging | Custom JPA entity listener + AOP | Full before/after snapshots to `audit.audit_logs` |
| Tamper Resistance | Insert-only audit table, no UPDATE/DELETE grants | Audit log integrity |

### 6.4 Caching

| Mechanism | Implementation | Scope |
|---|---|---|
| L1 Cache | Caffeine (in-process) | Lookup tables, permission checks, localization strings |
| Cache Annotations | `@Cacheable`, `@CacheEvict`, `@CachePut` | Service methods returning frequently-accessed data |
| Cache Configuration | Per-cache TTL and size limits | Configured in `application.yml` |
| Future L2 Cache | Redis (when deployed in SaaS cluster) | Shared cache for multi-instance deployments |

### 6.5 Exception Handling

| Mechanism | Implementation | Scope |
|---|---|---|
| Global Handler | `@ControllerAdvice` class | Catches all unhandled exceptions |
| Business Exceptions | Custom exception hierarchy (`BusinessException`, `NotFoundException`, etc.) | Typed exceptions with error codes |
| Error Responses | Standardized `ErrorResponse` DTO | Consistent JSON structure: `{code, message, details, timestamp}` |
| Validation Errors | `MethodArgumentNotValidException` handler | Field-level error details in response |
| Localized Messages | Error messages resolved from message bundles | Errors returned in user's locale |

### Cross-Cutting Package Mapping

```
com.tss.platform
    ├── security/            # Spring Security configuration, filters, evaluators
    ├── logging/             # Logging configuration, MDC filters, masking
    ├── audit/               # Audit entity listeners, AOP aspects
    ├── cache/               # Cache configuration, key generators
    ├── exception/           # Global exception handlers, exception hierarchy
    ├── i18n/                # Locale configuration, message source setup
    ├── config/              # Application-wide Spring configuration
    └── web/                 # Web filters, interceptors, CORS configuration
```

---

## 7. Dependency Rules

### Layer Dependency Matrix

| Layer | Can Depend On | Must Not Depend On |
|---|---|---|
| Presentation | Application | Domain (directly), Infrastructure |
| Application | Domain | Presentation, Infrastructure (directly) |
| Domain | Nothing | All other layers |
| Infrastructure | Domain, Application (interfaces) | Presentation |
| Cross-Cutting | All layers (via aspects/interceptors) | N/A (applied orthogonally) |

### Module Dependency Rules

1. **No circular dependencies**: Module A cannot depend on Module B if Module B already depends on Module A.
2. **Cross-module access via application services only**: Module A calls Module B's application service, never its repositories or domain entities directly.
3. **Shared kernel is minimal**: Only truly shared concepts (Organization, User, Control) live in `com.tss.platform.shared`.
4. **Event-based decoupling preferred**: When module A needs to react to module B's state changes, module B publishes an event and module A subscribes. No direct service call needed.

### Enforcement Strategy

| Mechanism | Tool | What It Enforces |
|---|---|---|
| Package access rules | ArchUnit tests | Layers do not violate dependency direction |
| Module boundary tests | ArchUnit tests | Modules do not access each other's internals |
| Compile-time checks | Maven module dependencies | Module JARs only depend on declared dependencies |
| Code review gates | Pull request reviews | Architectural rules verified in review |

### ArchUnit Rule Examples

```java
// Domain layer must not depend on infrastructure
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")

// Controllers must not access repositories directly
noClasses().that().resideInAPackage("..web..")
    .should().dependOnClassesThat().resideInAPackage("..persistence..")

// Modules must not access each other's domain or infrastructure
noClasses().that().resideInAPackage("..module.erm..")
    .should().dependOnClassesThat().resideInAPackage("..module.cyber..domain..")
```

---

## 8. Full Package Structure

```
com.tss
├── platform/                          # Cross-cutting platform concerns
│   ├── security/                      # Authentication, authorization
│   ├── audit/                         # Audit trail infrastructure
│   ├── cache/                         # Cache configuration
│   ├── config/                        # Application-wide config
│   ├── exception/                     # Global exception handling
│   ├── i18n/                          # Localization support
│   ├── logging/                       # Structured logging, MDC
│   ├── web/                           # Web filters, interceptors
│   └── shared/                        # Shared kernel (Organization, User, etc.)
│       ├── model/
│       ├── dto/
│       ├── service/
│       └── repository/
│
├── module/
│   ├── erm/                           # Enterprise Risk Management
│   │   ├── web/
│   │   │   ├── controller/            # @Controller (HTML)
│   │   │   ├── api/                   # @RestController (JSON)
│   │   │   └── viewmodel/
│   │   ├── application/
│   │   │   ├── service/
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── mapper/
│   │   │   ├── validator/
│   │   │   └── specification/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   ├── valueobject/
│   │   │   ├── event/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   └── enums/
│   │   └── infrastructure/
│   │       ├── persistence/
│   │       │   └── repository/
│   │       ├── config/
│   │       └── adapter/
│   │
│   ├── cyber/                         # Cybersecurity Risk & Compliance
│   │   └── (same structure as erm)
│   │
│   ├── bcm/                           # Business Continuity Management
│   │   └── (same structure as erm)
│   │
│   ├── datagov/                       # Data Governance
│   │   └── (same structure as erm)
│   │
│   ├── privacy/                       # Privacy & Personal Data Protection
│   │   └── (same structure as erm)
│   │
│   ├── audit/                         # Compliance & Audit
│   │   └── (same structure as erm)
│   │
│   ├── workflow/                       # Workflow & Case Management
│   │   └── (same structure as erm)
│   │
│   ├── reporting/                     # Reporting & Analytics
│   │   └── (same structure as erm)
│   │
│   ├── licensing/                     # Licensing & Subscription
│   │   └── (same structure as erm)
│   │
│   └── admin/                         # Administration & Configuration
│       └── (same structure as erm)
```

---

## 9. Architectural Rationale Summary

| Decision | Alternatives Considered | Why This Choice |
|---|---|---|
| Four-layer architecture | Hexagonal, Onion, Clean Architecture | Four-layer is the most widely understood by Java/Spring teams; achieves the same dependency inversion goals with less ceremony |
| Domain entities carry JPA annotations | Separate persistence model | Eliminates entity duplication; acceptable trade-off in a monolith where Hibernate is the only persistence provider |
| MapStruct for mapping | ModelMapper, manual mapping | Compile-time safety, zero runtime reflection, handles bilingual field mapping via custom methods |
| ArchUnit for enforcement | Manual code review only | Automated, runs in CI, catches violations before merge |
| Cross-cutting via aspects | Explicit calls in every service | Reduces boilerplate, ensures consistency, decouples concerns from business logic |
