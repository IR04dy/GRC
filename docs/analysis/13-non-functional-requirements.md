# 13. Non-Functional Requirements (NFRs)
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Document Information

| Field | Value |
|-------|-------|
| Document Title | TSS GRC Platform - Non-Functional Requirements |
| Version | 1.0 |
| Classification | Internal |
| Last Updated | 2026-04-05 |

## 1. Overview

This document defines the non-functional requirements (NFRs) for the TSS GRC Platform. Each requirement is assigned a unique identifier, categorized, and includes measurable acceptance criteria. These NFRs apply across all modules and govern the platform's quality attributes.

### Priority Definitions

| Priority | Definition |
|----------|------------|
| **P1 - Critical** | Must be met for production release. Failure blocks deployment. |
| **P2 - High** | Should be met for production release. Temporary workarounds acceptable. |
| **P3 - Medium** | Expected within first 3 releases. Enhances platform quality. |
| **P4 - Low** | Desirable. Planned for future iterations. |

---

## 2. Security Requirements (NFR-SEC)

### NFR-SEC-001: Authentication Mechanism

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-001 |
| **Category** | Security - Authentication |
| **Description** | The platform shall implement Spring Security-based authentication with support for username/password login, account lockout after failed attempts, and session-based authentication with CSRF protection. The system shall support future integration with LDAP/Active Directory and SAML 2.0/OAuth 2.0 SSO providers. |
| **Acceptance Criteria** | 1. Users authenticate via username and password stored with bcrypt hashing (strength 12+). 2. Account locks after 5 consecutive failed login attempts for a configurable duration (default: 30 minutes). 3. CSRF tokens are generated per session and validated on all state-changing requests. 4. Session timeout is configurable (default: 30 minutes of inactivity). 5. Login page is accessible without authentication; all other pages require valid session. 6. Authentication events (success, failure, lockout) are logged to the audit trail. |
| **Priority** | P1 - Critical |

### NFR-SEC-002: Role-Based Authorization

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-002 |
| **Category** | Security - Authorization |
| **Description** | The platform shall enforce role-based access control (RBAC) at both the UI layer (menu/page visibility) and the service layer (method-level security). Authorization rules shall be declarative using Spring Security annotations and configurable per organization. |
| **Acceptance Criteria** | 1. All controller endpoints are annotated with `@PreAuthorize` or equivalent security expressions. 2. Service methods performing sensitive operations have method-level security checks. 3. UI elements (menus, buttons, sections) are conditionally rendered based on user roles and permissions. 4. Unauthorized access attempts return HTTP 403 and are logged. 5. Role-permission mappings are stored in the database and modifiable by administrators. 6. A minimum of three default role templates exist: Administrator, Manager, User. |
| **Priority** | P1 - Critical |

### NFR-SEC-003: Data Encryption

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-003 |
| **Category** | Security - Encryption |
| **Description** | All data in transit shall be encrypted using TLS 1.2 or higher. Sensitive data at rest (passwords, API keys, encryption keys) shall be encrypted or hashed using industry-standard algorithms. Database connections shall use encrypted channels. |
| **Acceptance Criteria** | 1. Application enforces HTTPS for all endpoints (HTTP redirects to HTTPS). 2. TLS 1.2+ is required; TLS 1.0 and 1.1 are disabled. 3. Passwords are stored using bcrypt with a minimum cost factor of 12. 4. Sensitive configuration values (DB credentials, API keys) are encrypted in application properties or sourced from environment variables. 5. SQL Server connections use `encrypt=true` in the JDBC connection string. 6. No sensitive data appears in application logs. |
| **Priority** | P1 - Critical |

### NFR-SEC-004: Session Management

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-004 |
| **Category** | Security - Session Management |
| **Description** | The platform shall implement secure session management with configurable timeouts, concurrent session control, and session fixation protection. |
| **Acceptance Criteria** | 1. Session IDs are regenerated upon successful authentication (session fixation protection). 2. Sessions expire after configurable inactivity period (default: 30 minutes). 3. Maximum concurrent sessions per user is configurable (default: 1, with option for multiple). 4. Session cookies are set with `HttpOnly`, `Secure`, and `SameSite=Lax` attributes. 5. Users can explicitly log out, which invalidates the server-side session. 6. Expired session access redirects to the login page with an appropriate message. |
| **Priority** | P1 - Critical |

### NFR-SEC-005: OWASP Top 10 Protections

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-005 |
| **Category** | Security - OWASP |
| **Description** | The platform shall implement protections against the OWASP Top 10 vulnerabilities, including but not limited to: injection, broken authentication, sensitive data exposure, XML external entities, broken access control, security misconfiguration, XSS, insecure deserialization, using components with known vulnerabilities, and insufficient logging. |
| **Acceptance Criteria** | 1. All database queries use parameterized statements (JPA/Hibernate named parameters); no string concatenation in queries. 2. All user input rendered in HTML is escaped by default (Thymeleaf auto-escaping). 3. Content Security Policy (CSP) headers are configured to prevent inline script execution. 4. File upload functionality validates file type, size, and content; stores files outside the web root. 5. Error pages do not expose stack traces, framework versions, or internal paths. 6. Dependencies are scanned for known vulnerabilities using OWASP Dependency-Check or equivalent. 7. All security-relevant events are logged with sufficient detail for forensic analysis. |
| **Priority** | P1 - Critical |

### NFR-SEC-006: Secure HTTP Headers

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-006 |
| **Category** | Security - HTTP Headers |
| **Description** | The platform shall send security-related HTTP response headers to protect against common browser-based attacks. |
| **Acceptance Criteria** | 1. `X-Content-Type-Options: nosniff` is present on all responses. 2. `X-Frame-Options: DENY` (or `SAMEORIGIN` where iframes are required) is present. 3. `X-XSS-Protection: 0` is set (deferred to CSP). 4. `Strict-Transport-Security: max-age=31536000; includeSubDomains` is set when HTTPS is enabled. 5. `Content-Security-Policy` header is configured with restrictive defaults. 6. `Referrer-Policy: strict-origin-when-cross-origin` is set. 7. `Permissions-Policy` restricts access to browser features (camera, microphone, geolocation). |
| **Priority** | P2 - High |

### NFR-SEC-007: Input Validation

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-007 |
| **Category** | Security - Input Validation |
| **Description** | All user inputs shall be validated on both the client side (for usability) and the server side (for security). Validation rules shall be centrally defined and consistently applied. |
| **Acceptance Criteria** | 1. All form inputs have server-side validation using Bean Validation (JSR 380) annotations. 2. Client-side validation provides immediate feedback but is not relied upon for security. 3. String inputs have maximum length constraints matching database column sizes. 4. Email, URL, and date fields use format-specific validators. 5. Numeric fields validate range boundaries. 6. Rich text inputs (if any) are sanitized using a whitelist-based HTML sanitizer. 7. File upload names are sanitized to prevent path traversal. 8. Validation error messages are localized (Arabic/English). |
| **Priority** | P1 - Critical |

### NFR-SEC-008: Password Policy

| Field | Value |
|-------|-------|
| **ID** | NFR-SEC-008 |
| **Category** | Security - Password Policy |
| **Description** | The platform shall enforce configurable password policies that meet enterprise security standards. |
| **Acceptance Criteria** | 1. Minimum password length of 8 characters (configurable, up to 128). 2. Requires at least one uppercase letter, one lowercase letter, one digit, and one special character (configurable rules). 3. Password history prevents reuse of the last N passwords (default: 5). 4. Password expiry is configurable (default: 90 days) with advance warning notifications. 5. Password strength indicator is displayed during password creation. 6. First-login forced password change is supported. |
| **Priority** | P2 - High |

---

## 3. Performance Requirements (NFR-PER)

### NFR-PER-001: Page Load Time

| Field | Value |
|-------|-------|
| **ID** | NFR-PER-001 |
| **Category** | Performance - Page Load |
| **Description** | All server-rendered pages shall load within acceptable time thresholds under normal operating conditions. This includes Thymeleaf template rendering, data retrieval, and static asset delivery. |
| **Acceptance Criteria** | 1. Initial page load (HTML delivered to browser) completes within 3 seconds for 95% of requests under normal load (up to 100 concurrent users). 2. Subsequent page navigations (with cached static assets) complete within 2 seconds for 95% of requests. 3. Dashboard pages with charts and aggregated data load within 5 seconds for 95% of requests. 4. Static assets (CSS, JS, images) are served with cache headers (max-age >= 1 day for versioned assets). 5. Gzip/Brotli compression is enabled for text-based responses. |
| **Priority** | P1 - Critical |

### NFR-PER-002: API Response Time

| Field | Value |
|-------|-------|
| **ID** | NFR-PER-002 |
| **Category** | Performance - API |
| **Description** | All REST API endpoints (used for AJAX calls, data tables, form submissions) shall respond within defined time thresholds. |
| **Acceptance Criteria** | 1. Simple CRUD API responses (single entity read/write) complete within 500ms for 95% of requests. 2. List/search API responses (paginated, up to 50 records) complete within 1 second for 95% of requests. 3. Complex aggregation API responses complete within 2 seconds for 95% of requests. 4. Form submission responses complete within 1 second for 95% of requests. 5. No API endpoint exceeds 5 seconds under normal load without returning a timeout or async acknowledgment. |
| **Priority** | P1 - Critical |

### NFR-PER-003: Report Generation Time

| Field | Value |
|-------|-------|
| **ID** | NFR-PER-003 |
| **Category** | Performance - Reports |
| **Description** | Report generation (PDF, Excel, and on-screen reports) shall complete within acceptable thresholds based on complexity and data volume. |
| **Acceptance Criteria** | 1. Simple reports (single table, up to 1,000 rows) generate within 10 seconds. 2. Standard reports (multiple sections, up to 5,000 rows, charts) generate within 30 seconds. 3. Complex reports (cross-module, 10,000+ rows, multiple charts) generate within 60 seconds or are processed asynchronously with progress indication. 4. Reports exceeding 60 seconds are queued for background processing with notification upon completion. 5. Report generation does not block UI responsiveness for other users. |
| **Priority** | P2 - High |

### NFR-PER-004: Concurrent User Support

| Field | Value |
|-------|-------|
| **ID** | NFR-PER-004 |
| **Category** | Performance - Concurrency |
| **Description** | The platform shall support a minimum number of concurrent active users without degradation of response times beyond defined thresholds. |
| **Acceptance Criteria** | 1. The system supports 200 concurrent active users with response times within defined NFR-PER-001 and NFR-PER-002 thresholds. 2. The system supports 500 concurrent active users with no more than 20% degradation in response times. 3. The system supports 50 concurrent report generation requests without system instability. 4. Connection pool exhaustion does not occur under stated concurrency levels. 5. Load testing validates concurrency targets before production deployment. |
| **Priority** | P1 - Critical |

### NFR-PER-005: Database Query Performance

| Field | Value |
|-------|-------|
| **ID** | NFR-PER-005 |
| **Category** | Performance - Database |
| **Description** | Database queries generated by the application shall execute within defined thresholds. Query plans shall be optimized and monitored. |
| **Acceptance Criteria** | 1. Single-entity lookup queries (by primary key or indexed column) execute within 100ms. 2. Paginated list queries (with filtering and sorting) execute within 500ms. 3. Aggregation queries (COUNT, SUM, GROUP BY for dashboards) execute within 1 second. 4. No query performs a full table scan on tables with more than 10,000 rows (verified via execution plan analysis). 5. Slow query threshold is configured at 1 second; queries exceeding this are logged with execution plans. 6. Database connection acquisition from the pool completes within 100ms. |
| **Priority** | P1 - Critical |

### NFR-PER-006: Static Asset Optimization

| Field | Value |
|-------|-------|
| **ID** | NFR-PER-006 |
| **Category** | Performance - Assets |
| **Description** | Static assets (CSS, JavaScript, images) shall be optimized for delivery performance. |
| **Acceptance Criteria** | 1. CSS and JavaScript files are minified in production builds. 2. Static assets include content-based hash in filenames for cache busting. 3. Browser caching headers are set with appropriate max-age values. 4. Total page weight for a typical page does not exceed 2MB (excluding user-uploaded content). 5. Images used in the UI are optimized (compressed, appropriate format). |
| **Priority** | P3 - Medium |

---

## 4. Availability Requirements (NFR-AVL)

### NFR-AVL-001: Uptime Target

| Field | Value |
|-------|-------|
| **ID** | NFR-AVL-001 |
| **Category** | Availability - Uptime |
| **Description** | The platform shall maintain a minimum uptime percentage during defined operational hours. Planned maintenance windows are excluded from uptime calculations. |
| **Acceptance Criteria** | 1. The platform achieves 99.5% uptime during business hours (Sunday-Thursday, 07:00-22:00 AST) measured monthly. 2. Unplanned downtime does not exceed 3.6 hours per month. 3. Planned maintenance windows are scheduled outside business hours with 48-hour advance notice. 4. Uptime is measured from the application health check endpoint, not individual page responses. |
| **Priority** | P1 - Critical |

### NFR-AVL-002: Health Check Endpoints

| Field | Value |
|-------|-------|
| **ID** | NFR-AVL-002 |
| **Category** | Availability - Monitoring |
| **Description** | The platform shall expose health check endpoints that report the status of critical dependencies (database, file storage, external services). |
| **Acceptance Criteria** | 1. A `/actuator/health` endpoint returns HTTP 200 with status `UP` when all critical services are available. 2. Individual health indicators report status of: database connectivity, disk space, and application memory. 3. Health endpoint responds within 5 seconds. 4. Health endpoint is accessible without authentication (or with a dedicated monitoring credential). 5. Health status includes detailed component statuses when accessed with appropriate authorization. 6. Spring Boot Actuator is configured with only necessary endpoints exposed. |
| **Priority** | P2 - High |

### NFR-AVL-003: Graceful Degradation

| Field | Value |
|-------|-------|
| **ID** | NFR-AVL-003 |
| **Category** | Availability - Resilience |
| **Description** | The platform shall degrade gracefully when non-critical dependencies are unavailable, maintaining core functionality. |
| **Acceptance Criteria** | 1. If the email service is unavailable, the application continues to function; email notifications are queued for retry. 2. If report generation service is overloaded, users receive a queue position and estimated completion time rather than an error. 3. If file storage is temporarily unavailable, file upload/download shows an appropriate message; other features remain operational. 4. Database connection failures trigger automatic retry with exponential backoff before returning an error. 5. Circuit breaker patterns are applied to external service calls. |
| **Priority** | P2 - High |

### NFR-AVL-004: Application Restart Recovery

| Field | Value |
|-------|-------|
| **ID** | NFR-AVL-004 |
| **Category** | Availability - Recovery |
| **Description** | The application shall recover to a fully operational state after an unplanned restart within defined time limits. |
| **Acceptance Criteria** | 1. Application cold start (from process start to first request served) completes within 60 seconds. 2. In-progress user sessions are invalidated cleanly; users are redirected to login. 3. Background jobs that were interrupted resume or are re-queued on startup. 4. No data corruption occurs due to unclean shutdown (transactional integrity). 5. Application startup logs report readiness status and any initialization failures. |
| **Priority** | P2 - High |

---

## 5. Scalability Requirements (NFR-SCL)

### NFR-SCL-001: Horizontal Scaling Readiness

| Field | Value |
|-------|-------|
| **ID** | NFR-SCL-001 |
| **Category** | Scalability - Architecture |
| **Description** | The application architecture shall support horizontal scaling by running multiple instances behind a load balancer, without requiring code changes. |
| **Acceptance Criteria** | 1. Application does not store session state locally (sessions are stored in database or external store, or are sticky-session compatible). 2. No file system dependencies for shared state between instances (uploaded files stored in shared/external storage). 3. Scheduled tasks use distributed locking (e.g., ShedLock with SQL Server) to prevent duplicate execution across instances. 4. Application-level caches are either local with acceptable staleness or backed by a shared cache provider. 5. Configuration supports externalized session store (Spring Session with JDBC). |
| **Priority** | P3 - Medium |

### NFR-SCL-002: Connection Pooling

| Field | Value |
|-------|-------|
| **ID** | NFR-SCL-002 |
| **Category** | Scalability - Database |
| **Description** | Database connections shall be managed through a connection pool with configurable sizing to optimize resource usage and prevent connection exhaustion. |
| **Acceptance Criteria** | 1. HikariCP is used as the connection pool provider (Spring Boot default). 2. Minimum pool size is configurable (default: 10). 3. Maximum pool size is configurable (default: 50). 4. Connection timeout is configurable (default: 30 seconds). 5. Idle connection timeout is configured to release unused connections (default: 10 minutes). 6. Connection pool metrics are exposed via Spring Boot Actuator. 7. Connection leak detection is enabled with a configurable threshold (default: 60 seconds). |
| **Priority** | P1 - Critical |

### NFR-SCL-003: Application-Level Caching

| Field | Value |
|-------|-------|
| **ID** | NFR-SCL-003 |
| **Category** | Scalability - Caching |
| **Description** | The platform shall implement application-level caching for frequently accessed, infrequently changed data to reduce database load. |
| **Acceptance Criteria** | 1. Spring Cache abstraction is used with a configurable cache provider (default: Caffeine for single instance). 2. Reference data (lookup tables, configurations, role-permission mappings) is cached with configurable TTL (default: 15 minutes). 3. User session-related data (permissions, organization context) is cached per session. 4. Cache eviction occurs on data modification (write-through or explicit eviction). 5. Cache hit/miss ratios are logged and available via Actuator metrics. 6. Cache can be manually cleared via an admin endpoint without application restart. |
| **Priority** | P2 - High |

### NFR-SCL-004: Asynchronous Processing

| Field | Value |
|-------|-------|
| **ID** | NFR-SCL-004 |
| **Category** | Scalability - Async |
| **Description** | Long-running operations shall be processed asynchronously to prevent request thread blocking and maintain UI responsiveness. |
| **Acceptance Criteria** | 1. Report generation (PDF, Excel) is processed asynchronously with progress tracking. 2. Bulk import/export operations are processed asynchronously. 3. Email notification sending is asynchronous and non-blocking. 4. Async operations use Spring `@Async` with a configurable thread pool (default: core=5, max=20, queue=100). 5. Failed async operations are retried with configurable retry policy (default: 3 retries with exponential backoff). 6. Users can view the status of their pending async operations. |
| **Priority** | P2 - High |

### NFR-SCL-005: Data Volume Capacity

| Field | Value |
|-------|-------|
| **ID** | NFR-SCL-005 |
| **Category** | Scalability - Data |
| **Description** | The platform shall maintain performance characteristics at defined data volumes without requiring architectural changes. |
| **Acceptance Criteria** | 1. System performs within NFR-PER thresholds with up to 500 active users in the system. 2. System performs within NFR-PER thresholds with up to 100,000 risk records. 3. System performs within NFR-PER thresholds with up to 50,000 control records. 4. System performs within NFR-PER thresholds with up to 1,000,000 audit log entries. 5. Audit log table is partitioned or archived to maintain query performance. 6. Database indexing strategy is documented and reviewed quarterly. |
| **Priority** | P2 - High |

---

## 6. Maintainability Requirements (NFR-MNT)

### NFR-MNT-001: Code Quality Standards

| Field | Value |
|-------|-------|
| **ID** | NFR-MNT-001 |
| **Category** | Maintainability - Code Quality |
| **Description** | The codebase shall adhere to defined coding standards and quality metrics to ensure long-term maintainability. |
| **Acceptance Criteria** | 1. Java code follows Google Java Style Guide or a documented project-specific style guide. 2. All public methods and classes have Javadoc comments. 3. Cyclomatic complexity per method does not exceed 15 (measured via static analysis). 4. No class exceeds 500 lines of code (excluding generated code). 5. No method exceeds 50 lines of code. 6. Code duplication is below 5% (measured via static analysis). 7. SonarQube (or equivalent) quality gate is configured and passes on each build. |
| **Priority** | P2 - High |

### NFR-MNT-002: Documentation Requirements

| Field | Value |
|-------|-------|
| **ID** | NFR-MNT-002 |
| **Category** | Maintainability - Documentation |
| **Description** | The platform shall maintain comprehensive documentation for developers, administrators, and users. |
| **Acceptance Criteria** | 1. API endpoints are documented (manually or via Swagger/OpenAPI for REST endpoints). 2. Database schema is documented with entity-relationship diagrams. 3. Architecture decisions are recorded in Architecture Decision Records (ADRs). 4. Deployment procedures are documented in runbooks. 5. Configuration parameters are documented with descriptions, defaults, and valid ranges. 6. Module-specific developer guides exist for each GRC module. |
| **Priority** | P2 - High |

### NFR-MNT-003: Test Coverage

| Field | Value |
|-------|-------|
| **ID** | NFR-MNT-003 |
| **Category** | Maintainability - Testing |
| **Description** | The platform shall maintain minimum test coverage levels to ensure reliability and enable safe refactoring. |
| **Acceptance Criteria** | 1. Overall line coverage is at least 70% (measured by JaCoCo). 2. Service layer coverage is at least 80%. 3. Repository/DAO layer coverage is at least 60% (integration tests). 4. All critical business logic methods have unit tests. 5. Controller endpoints have integration tests verifying request/response behavior. 6. Security configuration has tests verifying access control rules. 7. Test reports are generated as part of the build pipeline. |
| **Priority** | P2 - High |

### NFR-MNT-004: Modular Architecture

| Field | Value |
|-------|-------|
| **ID** | NFR-MNT-004 |
| **Category** | Maintainability - Architecture |
| **Description** | The platform shall follow a modular architecture where each GRC domain is implemented as a distinct module with clear boundaries, enabling independent development and testing. |
| **Acceptance Criteria** | 1. Each GRC module (ERM, Compliance, Audit, etc.) has its own package structure under a common root. 2. Cross-module dependencies are through defined service interfaces, not direct repository access. 3. Shared functionality (workflow, notifications, reporting) is in a platform-core module. 4. Database tables are organized by schema per module (e.g., `risk.*`, `compliance.*`, `audit.*`). 5. A module can be disabled without causing application startup failures. 6. Module boundaries are enforced through ArchUnit or equivalent architecture tests. |
| **Priority** | P1 - Critical |

### NFR-MNT-005: Build and CI/CD

| Field | Value |
|-------|-------|
| **ID** | NFR-MNT-005 |
| **Category** | Maintainability - Build |
| **Description** | The platform shall have a reproducible build process and support continuous integration practices. |
| **Acceptance Criteria** | 1. Maven build completes successfully with a single command (`mvn clean package`). 2. All tests execute as part of the build. 3. Build produces a deployable artifact (JAR/WAR) with embedded configuration profiles. 4. Build version is embedded in the artifact and accessible at runtime (e.g., `/actuator/info`). 5. Database migration scripts (Flyway) are included in the build artifact. 6. Build is environment-independent (no hardcoded paths or environment-specific values). |
| **Priority** | P2 - High |

---

## 7. Configurability Requirements (NFR-CFG)

### NFR-CFG-001: Runtime Configuration

| Field | Value |
|-------|-------|
| **ID** | NFR-CFG-001 |
| **Category** | Configurability - Runtime |
| **Description** | Key application parameters shall be configurable at runtime without requiring application redeployment. |
| **Acceptance Criteria** | 1. System settings are stored in the `admin.system_settings` database table with key-value pairs. 2. Settings include: session timeout, password policy parameters, email server configuration, notification preferences, report defaults. 3. Changes to system settings take effect within 5 minutes (via cache refresh or event-driven update). 4. An admin UI allows viewing and modifying system settings. 5. Setting changes are audit-logged with before/after values. 6. Environment-specific settings (DB connection, server port) remain in application properties files. |
| **Priority** | P2 - High |

### NFR-CFG-002: Feature Toggles

| Field | Value |
|-------|-------|
| **ID** | NFR-CFG-002 |
| **Category** | Configurability - Feature Toggles |
| **Description** | The platform shall support feature toggles to enable or disable specific features without code deployment. Feature toggles serve both operational (kill switches) and licensing (module activation) purposes. |
| **Acceptance Criteria** | 1. Feature toggles are stored in the database (`admin.feature_toggles` table). 2. Toggles can be scoped: global, per-organization, or per-module. 3. Toggle checks are performed via a centralized `FeatureToggleService`. 4. UI elements controlled by toggles are hidden (not just disabled) when the feature is off. 5. API endpoints for toggled-off features return HTTP 403 with a descriptive message. 6. Toggle state changes are audit-logged. 7. Default toggle states are defined in seed data and overridable per environment. |
| **Priority** | P2 - High |

### NFR-CFG-003: Branding Configuration

| Field | Value |
|-------|-------|
| **ID** | NFR-CFG-003 |
| **Category** | Configurability - Branding |
| **Description** | The platform shall support customer-specific branding including logo, color scheme, and organization name displayed in the UI and generated reports. |
| **Acceptance Criteria** | 1. Organization logo can be uploaded and is displayed in the header and login page. 2. Primary and secondary brand colors are configurable and applied via CSS custom properties. 3. Organization name appears in the page title and report headers. 4. Login page can display a custom welcome message. 5. Generated PDF reports include the organization logo and name. 6. Branding changes take effect immediately without application restart. |
| **Priority** | P3 - Medium |

### NFR-CFG-004: Workflow Configuration

| Field | Value |
|-------|-------|
| **ID** | NFR-CFG-004 |
| **Category** | Configurability - Workflow |
| **Description** | Approval workflows and status transitions shall be configurable per module and per organization without code changes. |
| **Acceptance Criteria** | 1. Workflow states and transitions are defined in database configuration tables. 2. Each module can have its own set of workflow definitions. 3. Workflow steps can be configured with: required approvers (by role), escalation rules, SLA durations. 4. Parallel and sequential approval steps are supported. 5. Workflow configuration changes apply to new instances; in-progress instances follow their original workflow. 6. An admin UI allows viewing and modifying workflow configurations. |
| **Priority** | P3 - Medium |

---

## 8. Localization Requirements (NFR-LOC)

### NFR-LOC-001: Bilingual Interface

| Field | Value |
|-------|-------|
| **ID** | NFR-LOC-001 |
| **Category** | Localization - Language |
| **Description** | The platform shall fully support Arabic (ar-SA) and English (en-US) languages. Users shall be able to switch between languages without data loss or session interruption. |
| **Acceptance Criteria** | 1. All static UI text (labels, buttons, messages, tooltips) is available in both Arabic and English. 2. Language switching is available from any page via a persistent UI control. 3. Language preference is stored per user and persists across sessions. 4. Switching language does not cause page data loss (form state is preserved where technically feasible). 5. Language selection is available on the login page. 6. Default language is configurable per organization. |
| **Priority** | P1 - Critical |

### NFR-LOC-002: RTL/LTR Layout Support

| Field | Value |
|-------|-------|
| **ID** | NFR-LOC-002 |
| **Category** | Localization - Layout |
| **Description** | The platform shall correctly render right-to-left (RTL) layout for Arabic and left-to-right (LTR) layout for English, including mirrored navigation, form alignment, and table layouts. |
| **Acceptance Criteria** | 1. HTML `dir` attribute is set dynamically based on the active locale (`rtl` for Arabic, `ltr` for English). 2. CSS uses logical properties (`margin-inline-start`, `padding-inline-end`) instead of physical properties where applicable. 3. Navigation menus, sidebars, and breadcrumbs mirror position for RTL. 4. Form labels and input alignment are correct for both directions. 5. Data tables maintain correct column order and text alignment per direction. 6. Icons with directional meaning (arrows, chevrons) are mirrored for RTL. 7. Bootstrap RTL variant or equivalent framework RTL support is utilized. |
| **Priority** | P1 - Critical |

### NFR-LOC-003: Unicode Support

| Field | Value |
|-------|-------|
| **ID** | NFR-LOC-003 |
| **Category** | Localization - Unicode |
| **Description** | The platform shall support full Unicode character sets throughout the technology stack, ensuring Arabic and other scripts are stored, processed, and displayed correctly. |
| **Acceptance Criteria** | 1. All database text columns use `nvarchar` (or `ntext` for large text) data types. 2. Database collation supports Arabic sorting (e.g., `Arabic_CI_AS` or `Latin1_General_100_CI_AS_SC_UTF8`). 3. Application character encoding is UTF-8 throughout (HTTP responses, file I/O, logging). 4. Search and sorting operations handle Arabic characters correctly. 5. CSV/Excel exports preserve Arabic text without corruption. 6. PDF generation renders Arabic text correctly with appropriate fonts. |
| **Priority** | P1 - Critical |

### NFR-LOC-004: Date and Calendar Support

| Field | Value |
|-------|-------|
| **ID** | NFR-LOC-004 |
| **Category** | Localization - Date/Calendar |
| **Description** | The platform shall display dates in the appropriate format for the active locale and support readiness for Hijri calendar display alongside Gregorian dates. |
| **Acceptance Criteria** | 1. Dates are stored in the database as Gregorian (SQL Server `date`, `datetime2` types). 2. Date display format follows locale convention: `dd/MM/yyyy` for Arabic, `MM/dd/yyyy` or `yyyy-MM-dd` for English (configurable). 3. Date picker components support both locales. 4. The architecture supports future addition of Hijri date display (conversion utility placeholder). 5. All date/time values include timezone context (stored as UTC, displayed in local time). |
| **Priority** | P2 - High |

### NFR-LOC-005: Number and Currency Formatting

| Field | Value |
|-------|-------|
| **ID** | NFR-LOC-005 |
| **Category** | Localization - Numbers |
| **Description** | Numbers and currency values shall be formatted according to the active locale conventions. |
| **Acceptance Criteria** | 1. Decimal separator follows locale convention (`.` for English, configurable for Arabic). 2. Thousands separator follows locale convention (`,` for English, configurable for Arabic). 3. Currency values display with SAR symbol or code. 4. The system supports future option for Arabic-Indic numerals (Eastern Arabic digits). 5. Percentage values are formatted with locale-appropriate decimal handling. 6. Number formatting is consistent across UI display and exported documents. |
| **Priority** | P2 - High |

---

## 9. Accessibility Requirements (NFR-ACC)

### NFR-ACC-001: WCAG 2.1 AA Compliance Target

| Field | Value |
|-------|-------|
| **ID** | NFR-ACC-001 |
| **Category** | Accessibility - Standards |
| **Description** | The platform shall target WCAG 2.1 Level AA compliance for all user-facing pages, ensuring usability for people with disabilities. |
| **Acceptance Criteria** | 1. All images have meaningful `alt` attributes. 2. Color contrast ratios meet WCAG AA minimums (4.5:1 for normal text, 3:1 for large text). 3. Form inputs have associated `<label>` elements. 4. Error messages are programmatically associated with their form fields. 5. Page structure uses semantic HTML (`<header>`, `<nav>`, `<main>`, `<footer>`, headings hierarchy). 6. Focus indicators are visible on all interactive elements. 7. Accessibility is tested using automated tools (axe, Lighthouse) and manual keyboard testing. |
| **Priority** | P3 - Medium |

### NFR-ACC-002: Keyboard Navigation

| Field | Value |
|-------|-------|
| **ID** | NFR-ACC-002 |
| **Category** | Accessibility - Keyboard |
| **Description** | All platform functionality shall be operable through keyboard-only interaction. |
| **Acceptance Criteria** | 1. All interactive elements are reachable via Tab key navigation in a logical order. 2. Modal dialogs trap focus within the dialog until dismissed. 3. Dropdown menus and custom components are operable with arrow keys, Enter, and Escape. 4. Skip-to-content links are present on all pages. 5. Keyboard shortcuts do not conflict with screen reader shortcuts. 6. Data table rows are navigable and selectable via keyboard. |
| **Priority** | P3 - Medium |

### NFR-ACC-003: Screen Reader Support

| Field | Value |
|-------|-------|
| **ID** | NFR-ACC-003 |
| **Category** | Accessibility - Screen Reader |
| **Description** | The platform shall be compatible with common screen readers, providing appropriate ARIA attributes and semantic markup. |
| **Acceptance Criteria** | 1. ARIA landmarks are used to identify page regions. 2. Dynamic content updates use `aria-live` regions for screen reader announcements. 3. Custom interactive components (dropdowns, modals, tabs) have appropriate ARIA roles, states, and properties. 4. Loading states are communicated via `aria-busy` and status messages. 5. Data tables use proper `<th>` headers with scope attributes. 6. Form validation errors are announced to screen readers when they occur. |
| **Priority** | P4 - Low |

---

## 10. Traceability Requirements (NFR-TRC)

### NFR-TRC-001: Correlation IDs

| Field | Value |
|-------|-------|
| **ID** | NFR-TRC-001 |
| **Category** | Traceability - Correlation |
| **Description** | Every HTTP request shall be assigned a unique correlation ID that flows through all processing layers (controller, service, repository) and appears in all log entries related to that request. |
| **Acceptance Criteria** | 1. A UUID-based correlation ID is generated for each incoming HTTP request via a servlet filter. 2. The correlation ID is placed in the MDC (Mapped Diagnostic Context) and appears in all log statements. 3. The correlation ID is included in the HTTP response header (`X-Correlation-ID`). 4. The correlation ID is passed to async operations initiated by the request. 5. Error responses include the correlation ID for support reference. 6. Log format includes the correlation ID as a structured field. |
| **Priority** | P2 - High |

### NFR-TRC-002: Request Tracking and Logging

| Field | Value |
|-------|-------|
| **ID** | NFR-TRC-002 |
| **Category** | Traceability - Request Logging |
| **Description** | All HTTP requests shall be logged with sufficient detail for debugging and performance monitoring. |
| **Acceptance Criteria** | 1. Each request logs: timestamp, correlation ID, HTTP method, URI, user principal, response status, and duration. 2. Request logging does not include sensitive data (passwords, tokens, PII in request bodies). 3. Log levels are configurable per package without restart (via Actuator loggers endpoint). 4. Separate log files or appenders are used for: application logs, audit logs, and access logs. 5. Log rotation is configured (max file size: 100MB, retention: 30 days). 6. Structured logging format (JSON) is available for log aggregation tools. |
| **Priority** | P2 - High |

### NFR-TRC-003: Error Tracking

| Field | Value |
|-------|-------|
| **ID** | NFR-TRC-003 |
| **Category** | Traceability - Errors |
| **Description** | Application errors shall be captured, logged, and presented to users in a controlled manner. |
| **Acceptance Criteria** | 1. Unhandled exceptions are caught by a global exception handler (`@ControllerAdvice`). 2. Error responses include the correlation ID but not stack traces or internal details. 3. All exceptions are logged at ERROR level with full stack traces in server logs. 4. Custom error pages are displayed for HTTP 400, 403, 404, and 500 errors. 5. Error pages are localized (Arabic/English). 6. Repeated errors of the same type within a time window are aggregated in logs to prevent log flooding. |
| **Priority** | P2 - High |

---

## 11. Audit Logging Requirements (NFR-AUD)

### NFR-AUD-001: Comprehensive Audit Trail

| Field | Value |
|-------|-------|
| **ID** | NFR-AUD-001 |
| **Category** | Audit - Coverage |
| **Description** | All write operations (create, update, delete) across all modules shall be recorded in an immutable audit trail. The audit trail shall capture who did what, when, and what changed. |
| **Acceptance Criteria** | 1. All entity create operations are logged with the full entity state. 2. All entity update operations are logged with before and after values for changed fields. 3. All entity delete (soft delete) operations are logged. 4. Audit entries include: timestamp, user ID, user display name, action type, entity type, entity ID, module, organization ID, IP address, and correlation ID. 5. Audit logging is implemented via JPA entity listeners or Spring AOP, not manual coding in each service method. 6. Audit log entries are written to the `audit.audit_logs` table. |
| **Priority** | P1 - Critical |

### NFR-AUD-002: Audit Log Immutability

| Field | Value |
|-------|-------|
| **ID** | NFR-AUD-002 |
| **Category** | Audit - Integrity |
| **Description** | Audit log entries shall be immutable. No application-level operation shall modify or delete audit log records. |
| **Acceptance Criteria** | 1. The audit log table has no UPDATE or DELETE operations in application code. 2. The application database user has only INSERT and SELECT permissions on the audit log table. 3. Audit log entity is mapped as read-only in JPA (no `@Setter` on audit fields). 4. API endpoints for audit logs are read-only (GET only). 5. Database triggers or constraints prevent UPDATE/DELETE on audit tables (defense in depth). |
| **Priority** | P1 - Critical |

### NFR-AUD-003: Audit Log Retention

| Field | Value |
|-------|-------|
| **ID** | NFR-AUD-003 |
| **Category** | Audit - Retention |
| **Description** | Audit logs shall be retained for a configurable period, with archival support for compliance requirements. |
| **Acceptance Criteria** | 1. Default retention period is 7 years (configurable per organization). 2. Audit logs older than the active retention period are archived to a separate table or exported. 3. Archived audit logs remain queryable but may have slower access times. 4. Audit log table is partitioned by month to support efficient archival. 5. Retention policy compliance is verified by a scheduled job. 6. Audit log export supports CSV and PDF formats for compliance reporting. |
| **Priority** | P2 - High |

### NFR-AUD-004: Authentication Event Logging

| Field | Value |
|-------|-------|
| **ID** | NFR-AUD-004 |
| **Category** | Audit - Authentication |
| **Description** | All authentication-related events shall be logged separately for security monitoring. |
| **Acceptance Criteria** | 1. Successful login events are logged with: timestamp, user ID, IP address, user agent. 2. Failed login attempts are logged with: timestamp, attempted username, IP address, failure reason. 3. Account lockout events are logged. 4. Password change events are logged (without the actual passwords). 5. Session timeout and explicit logout events are logged. 6. Authentication logs are queryable by administrators through the UI. |
| **Priority** | P1 - Critical |

---

## 12. Extensibility Requirements (NFR-EXT)

### NFR-EXT-001: Plugin Architecture Readiness

| Field | Value |
|-------|-------|
| **ID** | NFR-EXT-001 |
| **Category** | Extensibility - Plugins |
| **Description** | The platform architecture shall support future extension through plugin-like mechanisms, allowing new modules or features to be added without modifying core platform code. |
| **Acceptance Criteria** | 1. Module registration is discovery-based (Spring component scanning per module package). 2. Menu items and navigation entries are contributed by modules via a registration API. 3. Dashboard widgets can be registered by modules into the dashboard framework. 4. Event-driven communication between modules uses Spring Application Events. 5. New entity types can be added by new modules without schema changes to core tables. 6. Report templates are module-specific and discoverable by the reporting engine. |
| **Priority** | P3 - Medium |

### NFR-EXT-002: API-First Design

| Field | Value |
|-------|-------|
| **ID** | NFR-EXT-002 |
| **Category** | Extensibility - API |
| **Description** | All platform data and operations shall be accessible through well-defined REST APIs, enabling integration with external systems and future front-end alternatives. |
| **Acceptance Criteria** | 1. All data operations available in the UI are also available via REST API endpoints. 2. APIs follow RESTful conventions (proper HTTP methods, status codes, resource naming). 3. API responses use consistent JSON structure with standardized error format. 4. APIs support pagination, filtering, and sorting for list endpoints. 5. API versioning strategy is defined (URL path versioning: `/api/v1/`). 6. API authentication uses the same Spring Security context as the web UI. |
| **Priority** | P2 - High |

### NFR-EXT-003: Custom Fields Readiness

| Field | Value |
|-------|-------|
| **ID** | NFR-EXT-003 |
| **Category** | Extensibility - Custom Fields |
| **Description** | The platform architecture shall support the future addition of user-defined custom fields on core entities without schema changes. |
| **Acceptance Criteria** | 1. Database design includes a `custom_fields` JSON column (or EAV pattern) on major entities. 2. Custom field definitions are stored in a metadata table (`admin.custom_field_definitions`). 3. Custom fields support types: text, number, date, dropdown (single/multi), boolean. 4. The UI rendering framework can dynamically render custom fields based on metadata. 5. Custom field values are included in search, export, and reporting. 6. Custom field definitions are scoped per organization and per entity type. |
| **Priority** | P4 - Low |

---

## 13. Data Segregation Requirements (NFR-DSG)

### NFR-DSG-001: Tenant-Level Data Isolation

| Field | Value |
|-------|-------|
| **ID** | NFR-DSG-001 |
| **Category** | Data Segregation - Tenant |
| **Description** | In multi-tenant deployments, each tenant's data shall be completely isolated from other tenants. No data leakage shall occur between tenants under any circumstance. |
| **Acceptance Criteria** | 1. All tenant-specific tables include a `tenant_id` column. 2. All database queries for tenant-specific data include a tenant filter (enforced via Hibernate filters or Spring Data specifications). 3. Tenant context is established at the beginning of each request from the authenticated user's tenant association. 4. Cross-tenant data access is impossible through the application layer. 5. Database-level row-level security (RLS) is implemented as defense in depth. 6. Tenant isolation is verified through integration tests that attempt cross-tenant access. |
| **Priority** | P1 - Critical |

### NFR-DSG-002: Organization-Level Data Filtering

| Field | Value |
|-------|-------|
| **ID** | NFR-DSG-002 |
| **Category** | Data Segregation - Organization |
| **Description** | Within a tenant, data shall be filtered by the user's organization membership. Users shall only see data belonging to their assigned organization(s) and any data explicitly shared with them. |
| **Acceptance Criteria** | 1. All module-specific entities reference an `organization_id`. 2. Data queries automatically filter by the user's organization context. 3. Users with multi-organization access can switch organization context. 4. Organization filtering applies to all list views, reports, dashboards, and exports. 5. Platform administrators can view cross-organization data when authorized. 6. Organization context is included in the audit trail. |
| **Priority** | P1 - Critical |

### NFR-DSG-003: Business Unit Visibility

| Field | Value |
|-------|-------|
| **ID** | NFR-DSG-003 |
| **Category** | Data Segregation - Business Unit |
| **Description** | Within an organization, data visibility can be further restricted by business unit hierarchy. Users shall see data for their business unit and optionally for child business units. |
| **Acceptance Criteria** | 1. Business unit hierarchy is represented as a tree structure in the database. 2. Data queries can be scoped to a specific business unit and its descendants. 3. Business unit filtering is optional and configurable per module. 4. Users can be assigned to one or more business units. 5. Dashboard and report data respect business unit visibility rules. 6. Business unit hierarchy supports at least 5 levels of depth. |
| **Priority** | P2 - High |

---

## 14. Backup and Recovery Requirements (NFR-BKP)

### NFR-BKP-001: Recovery Point Objective (RPO)

| Field | Value |
|-------|-------|
| **ID** | NFR-BKP-001 |
| **Category** | Backup - RPO |
| **Description** | The maximum acceptable data loss in the event of a disaster shall not exceed the defined Recovery Point Objective. |
| **Acceptance Criteria** | 1. RPO is 1 hour or less for all production data. 2. SQL Server transaction log backups run every 30 minutes (or more frequently). 3. Full database backups run daily. 4. Differential backups run every 6 hours. 5. Backup completeness and integrity are verified automatically. 6. Backup schedule documentation is maintained and reviewed quarterly. |
| **Priority** | P1 - Critical |

### NFR-BKP-002: Recovery Time Objective (RTO)

| Field | Value |
|-------|-------|
| **ID** | NFR-BKP-002 |
| **Category** | Backup - RTO |
| **Description** | The maximum acceptable time to restore the platform to operational state after a disaster shall not exceed the defined Recovery Time Objective. |
| **Acceptance Criteria** | 1. RTO is 4 hours or less for full system restoration. 2. Database restoration from backups can be completed within 2 hours. 3. Application redeployment and configuration restoration can be completed within 1 hour. 4. Recovery procedures are documented in a disaster recovery runbook. 5. Recovery procedures are tested at least annually. 6. Recovery testing results are documented with actual measured times. |
| **Priority** | P1 - Critical |

### NFR-BKP-003: Automated Backup Management

| Field | Value |
|-------|-------|
| **ID** | NFR-BKP-003 |
| **Category** | Backup - Automation |
| **Description** | Backup operations shall be automated and monitored, with alerts for backup failures. |
| **Acceptance Criteria** | 1. All backup jobs are scheduled and automated (no manual intervention required). 2. Backup job success/failure is monitored with automated alerts on failure. 3. Backup files are stored in a location separate from the primary database server. 4. Backup retention follows a defined policy: daily backups retained for 30 days, weekly for 90 days, monthly for 1 year. 5. Backup storage space is monitored with alerts for capacity thresholds. 6. Backup encryption is enabled for all backup files. |
| **Priority** | P2 - High |

---

## 15. Export Requirements (NFR-EXP)

### NFR-EXP-001: PDF Export

| Field | Value |
|-------|-------|
| **ID** | NFR-EXP-001 |
| **Category** | Export - PDF |
| **Description** | The platform shall support exporting reports, assessments, and selected data views to PDF format with proper formatting and branding. |
| **Acceptance Criteria** | 1. PDF exports include organization branding (logo, name, colors). 2. PDF exports support RTL layout for Arabic content. 3. PDF exports include page numbers, headers, and footers. 4. PDF exports support embedded charts and tables. 5. Large PDF exports (100+ pages) are generated asynchronously. 6. PDF generation uses a templating approach (e.g., Thymeleaf + Flying Saucer, or Apache PDFBox). 7. Generated PDFs are searchable (text-based, not image-based). |
| **Priority** | P2 - High |

### NFR-EXP-002: Excel Export

| Field | Value |
|-------|-------|
| **ID** | NFR-EXP-002 |
| **Category** | Export - Excel |
| **Description** | The platform shall support exporting data tables, reports, and assessment results to Excel format (.xlsx). |
| **Acceptance Criteria** | 1. Excel exports use Apache POI or equivalent library for .xlsx generation. 2. Exports include column headers with appropriate formatting. 3. Numeric fields are exported as numbers (not text) for Excel calculations. 4. Date fields are exported with proper Excel date formatting. 5. Arabic text is preserved correctly in exports. 6. Large exports (10,000+ rows) use streaming API to prevent memory issues. 7. Exports include a metadata sheet with report parameters and generation timestamp. |
| **Priority** | P2 - High |

### NFR-EXP-003: Word Export

| Field | Value |
|-------|-------|
| **ID** | NFR-EXP-003 |
| **Category** | Export - Word |
| **Description** | The platform shall support exporting assessment reports and structured documents to Word format (.docx) for further editing by users. |
| **Acceptance Criteria** | 1. Word exports use Apache POI or docx4j for .docx generation. 2. Exports include organization branding in headers. 3. Document structure uses proper Word styles (Heading 1, Heading 2, Body Text). 4. Tables in exports are formatted as Word tables. 5. RTL layout is supported for Arabic content. 6. Generated documents are editable (not locked or protected). |
| **Priority** | P3 - Medium |

---

## 16. Dashboard Readiness Requirements (NFR-DSH)

### NFR-DSH-001: Real-Time Data Display

| Field | Value |
|-------|-------|
| **ID** | NFR-DSH-001 |
| **Category** | Dashboard - Data |
| **Description** | Dashboard components shall display data that is current within an acceptable staleness window. |
| **Acceptance Criteria** | 1. Dashboard data is refreshed at minimum every 5 minutes (configurable). 2. Manual refresh option is available on all dashboard pages. 3. Dashboard queries use optimized aggregation queries or pre-computed summary tables. 4. Dashboard loading does not block other application functionality. 5. Dashboard data respects the user's organization and business unit context. 6. Empty states are handled gracefully with informative messages. |
| **Priority** | P2 - High |

### NFR-DSH-002: Chart and Visualization Support

| Field | Value |
|-------|-------|
| **ID** | NFR-DSH-002 |
| **Category** | Dashboard - Visualization |
| **Description** | The platform shall support various chart types and visualization components for dashboard displays. |
| **Acceptance Criteria** | 1. Supported chart types include: bar, pie/donut, line, gauge, and heatmap. 2. Charts use a consistent JavaScript charting library (e.g., Chart.js, ApexCharts). 3. Charts are responsive and render correctly on different screen sizes. 4. Charts support RTL layout (axis labels, legends). 5. Charts include tooltips with detailed data on hover. 6. Color schemes are consistent and accessible (sufficient contrast, colorblind-friendly palettes). |
| **Priority** | P2 - High |

### NFR-DSH-003: Drill-Down Navigation

| Field | Value |
|-------|-------|
| **ID** | NFR-DSH-003 |
| **Category** | Dashboard - Navigation |
| **Description** | Dashboard visualizations shall support drill-down navigation from summary views to detailed records. |
| **Acceptance Criteria** | 1. Clicking a chart segment or summary number navigates to the filtered list of underlying records. 2. Drill-down preserves the filter context (date range, organization, status). 3. Back navigation returns to the dashboard with the previous state preserved. 4. Drill-down links are accessible via keyboard. 5. Drill-down behavior is consistent across all dashboard components. |
| **Priority** | P3 - Medium |

---

## 17. SQL Server Performance Requirements (NFR-SQL)

### NFR-SQL-001: Indexing Strategy

| Field | Value |
|-------|-------|
| **ID** | NFR-SQL-001 |
| **Category** | SQL Server - Indexing |
| **Description** | All database tables shall have appropriate indexes to support query patterns used by the application. |
| **Acceptance Criteria** | 1. All primary keys have clustered indexes. 2. All foreign key columns have non-clustered indexes. 3. Columns used in WHERE clauses, JOIN conditions, and ORDER BY have appropriate indexes. 4. Composite indexes are created for frequently combined filter/sort patterns. 5. Index usage is reviewed using SQL Server DMVs (missing indexes, unused indexes) monthly. 6. Index fragmentation is monitored and maintenance (rebuild/reorganize) is scheduled. 7. Filtered indexes are used for soft-delete patterns (`WHERE is_deleted = 0`). |
| **Priority** | P1 - Critical |

### NFR-SQL-002: Query Optimization

| Field | Value |
|-------|-------|
| **ID** | NFR-SQL-002 |
| **Category** | SQL Server - Query Optimization |
| **Description** | Database queries shall be optimized to minimize resource consumption and execution time. |
| **Acceptance Criteria** | 1. JPA/Hibernate queries avoid the N+1 problem (verified through query logging in development). 2. Fetch strategies (eager/lazy) are explicitly configured per relationship. 3. List/search queries use database-level pagination (`OFFSET`/`FETCH` or equivalent). 4. Aggregation queries for dashboards use database-level aggregation, not application-level. 5. Read-only operations use `@Transactional(readOnly = true)` for optimization hints. 6. Query execution plans are reviewed for queries exceeding the slow query threshold. 7. No queries use `SELECT *`; only required columns are fetched for list/search operations (using projections or DTOs). |
| **Priority** | P1 - Critical |

### NFR-SQL-003: Table Partitioning

| Field | Value |
|-------|-------|
| **ID** | NFR-SQL-003 |
| **Category** | SQL Server - Partitioning |
| **Description** | Large and continuously growing tables shall be partitioned to maintain query performance and simplify data management. |
| **Acceptance Criteria** | 1. Audit log table (`audit.audit_logs`) is partitioned by month on the `created_at` column. 2. Notification history table is partitioned by month. 3. Partition maintenance (creation of future partitions, merging of old partitions) is automated. 4. Partition-aligned indexes are used on partitioned tables. 5. Queries on partitioned tables include the partition key in WHERE clauses for partition elimination. 6. Partition strategy is documented and reviewed annually based on data growth. |
| **Priority** | P2 - High |

### NFR-SQL-004: Database Maintenance

| Field | Value |
|-------|-------|
| **ID** | NFR-SQL-004 |
| **Category** | SQL Server - Maintenance |
| **Description** | Routine database maintenance operations shall be scheduled and automated to maintain optimal performance. |
| **Acceptance Criteria** | 1. Index rebuild/reorganize is scheduled weekly (rebuild for fragmentation > 30%, reorganize for 10-30%). 2. Statistics are updated weekly or after significant data changes. 3. Database integrity checks (DBCC CHECKDB) run weekly. 4. Transaction log space is monitored with alerts at 80% capacity. 5. TempDB usage is monitored during peak hours. 6. Maintenance windows are defined and do not conflict with backup schedules. |
| **Priority** | P2 - High |

---

## 18. NFR Traceability Matrix

| NFR ID | Category | Priority | Verification Method |
|--------|----------|----------|-------------------|
| NFR-SEC-001 | Security - Authentication | P1 | Integration test, security review |
| NFR-SEC-002 | Security - Authorization | P1 | Integration test, security review |
| NFR-SEC-003 | Security - Encryption | P1 | Configuration review, penetration test |
| NFR-SEC-004 | Security - Session | P1 | Integration test, security review |
| NFR-SEC-005 | Security - OWASP | P1 | Penetration test, SAST scan |
| NFR-SEC-006 | Security - Headers | P2 | Automated header scan |
| NFR-SEC-007 | Security - Validation | P1 | Unit test, integration test |
| NFR-SEC-008 | Security - Password | P2 | Integration test |
| NFR-PER-001 | Performance - Page Load | P1 | Load test |
| NFR-PER-002 | Performance - API | P1 | Load test |
| NFR-PER-003 | Performance - Reports | P2 | Performance test |
| NFR-PER-004 | Performance - Concurrency | P1 | Load test |
| NFR-PER-005 | Performance - Database | P1 | Query analysis, load test |
| NFR-PER-006 | Performance - Assets | P3 | Lighthouse audit |
| NFR-AVL-001 | Availability - Uptime | P1 | Monitoring dashboard |
| NFR-AVL-002 | Availability - Health | P2 | Integration test |
| NFR-AVL-003 | Availability - Resilience | P2 | Chaos test |
| NFR-AVL-004 | Availability - Recovery | P2 | Restart test |
| NFR-SCL-001 | Scalability - Architecture | P3 | Architecture review |
| NFR-SCL-002 | Scalability - Connection Pool | P1 | Configuration review, load test |
| NFR-SCL-003 | Scalability - Caching | P2 | Performance test |
| NFR-SCL-004 | Scalability - Async | P2 | Integration test |
| NFR-SCL-005 | Scalability - Data Volume | P2 | Volume test |
| NFR-MNT-001 | Maintainability - Code | P2 | SonarQube quality gate |
| NFR-MNT-002 | Maintainability - Docs | P2 | Documentation review |
| NFR-MNT-003 | Maintainability - Testing | P2 | JaCoCo coverage report |
| NFR-MNT-004 | Maintainability - Architecture | P1 | ArchUnit tests |
| NFR-MNT-005 | Maintainability - Build | P2 | CI/CD pipeline |
| NFR-CFG-001 | Configurability - Runtime | P2 | Integration test |
| NFR-CFG-002 | Configurability - Toggles | P2 | Integration test |
| NFR-CFG-003 | Configurability - Branding | P3 | Manual test |
| NFR-CFG-004 | Configurability - Workflow | P3 | Integration test |
| NFR-LOC-001 | Localization - Language | P1 | Manual test, UI review |
| NFR-LOC-002 | Localization - RTL/LTR | P1 | Visual regression test |
| NFR-LOC-003 | Localization - Unicode | P1 | Integration test |
| NFR-LOC-004 | Localization - Date | P2 | Unit test |
| NFR-LOC-005 | Localization - Numbers | P2 | Unit test |
| NFR-ACC-001 | Accessibility - WCAG | P3 | axe/Lighthouse audit |
| NFR-ACC-002 | Accessibility - Keyboard | P3 | Manual test |
| NFR-ACC-003 | Accessibility - Screen Reader | P4 | Manual test |
| NFR-TRC-001 | Traceability - Correlation | P2 | Integration test |
| NFR-TRC-002 | Traceability - Logging | P2 | Log review |
| NFR-TRC-003 | Traceability - Errors | P2 | Integration test |
| NFR-AUD-001 | Audit - Coverage | P1 | Integration test |
| NFR-AUD-002 | Audit - Immutability | P1 | Security review, DB permission test |
| NFR-AUD-003 | Audit - Retention | P2 | Configuration review |
| NFR-AUD-004 | Audit - Authentication | P1 | Integration test |
| NFR-EXT-001 | Extensibility - Plugins | P3 | Architecture review |
| NFR-EXT-002 | Extensibility - API | P2 | API review |
| NFR-EXT-003 | Extensibility - Custom Fields | P4 | Architecture review |
| NFR-DSG-001 | Data Segregation - Tenant | P1 | Integration test, security test |
| NFR-DSG-002 | Data Segregation - Organization | P1 | Integration test |
| NFR-DSG-003 | Data Segregation - BU | P2 | Integration test |
| NFR-BKP-001 | Backup - RPO | P1 | Backup verification |
| NFR-BKP-002 | Backup - RTO | P1 | DR drill |
| NFR-BKP-003 | Backup - Automation | P2 | Monitoring review |
| NFR-EXP-001 | Export - PDF | P2 | Integration test |
| NFR-EXP-002 | Export - Excel | P2 | Integration test |
| NFR-EXP-003 | Export - Word | P3 | Integration test |
| NFR-DSH-001 | Dashboard - Data | P2 | Performance test |
| NFR-DSH-002 | Dashboard - Visualization | P2 | Visual review |
| NFR-DSH-003 | Dashboard - Navigation | P3 | Integration test |
| NFR-SQL-001 | SQL Server - Indexing | P1 | Index analysis |
| NFR-SQL-002 | SQL Server - Query Opt | P1 | Query plan review |
| NFR-SQL-003 | SQL Server - Partitioning | P2 | DBA review |
| NFR-SQL-004 | SQL Server - Maintenance | P2 | Job scheduling review |

---

## 19. Summary Statistics

| Priority | Count | Percentage |
|----------|-------|------------|
| P1 - Critical | 24 | 39% |
| P2 - High | 27 | 44% |
| P3 - Medium | 8 | 13% |
| P4 - Low | 2 | 3% |
| **Total** | **61** | **100%** |

| Category | Count |
|----------|-------|
| Security (SEC) | 8 |
| Performance (PER) | 6 |
| Availability (AVL) | 4 |
| Scalability (SCL) | 5 |
| Maintainability (MNT) | 5 |
| Configurability (CFG) | 4 |
| Localization (LOC) | 5 |
| Accessibility (ACC) | 3 |
| Traceability (TRC) | 3 |
| Audit (AUD) | 4 |
| Extensibility (EXT) | 3 |
| Data Segregation (DSG) | 3 |
| Backup (BKP) | 3 |
| Export (EXP) | 3 |
| Dashboard (DSH) | 3 |
| SQL Server (SQL) | 4 |
| **Total** | **61** |
