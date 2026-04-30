# 17 - Availability and Resilience

## Purpose

This document defines the availability targets, resilience patterns, error handling strategy, and data integrity mechanisms for the TSS GRC platform. A GRC platform manages business-critical regulatory and compliance data, making availability and data integrity essential for customer trust.

---

## 1. Availability Targets

### 1.1 Service Level Objectives

| Metric | Target | Measurement |
|---|---|---|
| Availability (business hours) | 99.5% | Sun-Thu 7:00-22:00 AST (Saudi business hours) |
| Availability (overall) | 99.0% | Including maintenance windows |
| Planned maintenance window | Fri 22:00 - Sat 06:00 AST | Weekly window for updates |
| Unplanned downtime budget | ~3.65 hours/month (99.5%) | Max allowable outage during business hours |
| Recovery Time Objective (RTO) | < 4 hours | Time to restore service after major failure |
| Recovery Point Objective (RPO) | < 1 hour | Maximum data loss after failure |

**Rationale for 99.5% (not 99.9%)**: GRC platforms are not transactional systems requiring five-nines. Users are knowledge workers performing assessments, reviews, and reporting during business hours. Brief outages during off-hours are acceptable. Targeting 99.9%+ would require active-active multi-region deployment, which is disproportionate to the business need.

### 1.2 Maintenance Windows

- **Planned**: Friday evening to Saturday morning (lowest usage period in Saudi Arabia)
- **Communication**: 48-hour advance notice for planned maintenance
- **Emergency**: Unplanned patches applied with immediate notification; post-incident report within 24 hours

---

## 2. Health Checks

### 2.1 Spring Actuator Health Endpoint

**Decision**: Spring Boot Actuator with custom health indicators for all critical dependencies.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
      base-path: /actuator
  endpoint:
    health:
      show-details: when_authorized  # Full details for authenticated admin users
      group:
        liveness:
          include: ping
        readiness:
          include: db,diskSpace,mail,custom
  health:
    mail:
      enabled: true
    diskspace:
      enabled: true
      threshold: 1073741824  # 1 GB minimum free space
```

### 2.2 Custom Health Indicators

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {
            long start = System.nanoTime();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            if (durationMs > 5000) {
                return Health.down()
                    .withDetail("database", "Response time exceeded 5s: " + durationMs + "ms")
                    .build();
            }
            return Health.up()
                .withDetail("responseTimeMs", durationMs)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

@Component
public class StorageHealthIndicator implements HealthIndicator {

    @Value("${tss.storage.upload-dir}")
    private String uploadDir;

    @Override
    public Health health() {
        Path path = Path.of(uploadDir);
        if (!Files.isWritable(path)) {
            return Health.down()
                .withDetail("storage", "Upload directory is not writable")
                .build();
        }
        long freeSpaceGB = path.toFile().getFreeSpace() / (1024 * 1024 * 1024);
        if (freeSpaceGB < 2) {
            return Health.down()
                .withDetail("freeSpaceGB", freeSpaceGB)
                .withDetail("storage", "Less than 2GB free space")
                .build();
        }
        return Health.up()
            .withDetail("freeSpaceGB", freeSpaceGB)
            .build();
    }
}
```

### 2.3 Load Balancer Integration

| Endpoint | Purpose | Expected Response |
|---|---|---|
| `/actuator/health/liveness` | Is the process running? | 200 if JVM is healthy |
| `/actuator/health/readiness` | Can the app serve requests? | 200 if DB, storage, mail are healthy |

**Load balancer configuration**: Health check every 10 seconds against `/actuator/health/readiness`. Mark instance unhealthy after 3 consecutive failures. Restore after 2 consecutive successes.

---

## 3. Graceful Degradation

### 3.1 Non-Critical Service Failures

**Decision**: If a non-critical service fails, the core application continues to function.

| Service | If Unavailable | User Impact |
|---|---|---|
| SMTP (email) | Notifications queued, retried | Users do not receive email notifications; all other functions work |
| LDAP/AD | LDAP auth fails, local auth still works | LDAP users cannot log in; local users unaffected |
| File storage | File upload/download fails | Users cannot attach files; all other functions work |
| Cache (Caffeine) | Queries hit database directly | Slightly slower performance; no functional impact |
| Audit logging (async) | Audit writes queued, retried | Temporary gap in audit trail; business operations continue |

### 3.2 Critical Service Failures

| Service | If Unavailable | Impact |
|---|---|---|
| SQL Server database | Application cannot function | Full outage; health check reports unhealthy |
| Application JVM | Instance down | Load balancer routes to other instances (if multi-instance) |

---

## 4. Application Resilience

### 4.1 Stateless Design

**Decision**: Application instances are stateless (with session affinity for Phase 1). No local file state, no in-memory business state.

**What this means**:
- Any instance can be restarted without data loss
- Scaling out adds capacity without data migration
- Instance failure loses only in-flight requests and active sessions (users re-login)
- All persistent state lives in SQL Server and file storage

### 4.2 Startup and Shutdown

**Graceful shutdown**:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # Wait up to 30s for in-flight requests to complete
```

**Startup checks**: Application refuses to start if critical dependencies are unavailable:

```java
@Component
public class StartupValidator implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        validateDatabaseConnection();
        validateStorageDirectory();
        validateEncryptionKeyAvailable();
        log.info("TSS GRC Platform startup validation passed");
    }

    private void validateDatabaseConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot connect to database", e);
        }
    }
}
```

---

## 5. Error Handling

### 5.1 Global Exception Handler (Web)

```java
@ControllerAdvice
public class GlobalWebExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(Model model, HttpServletRequest request) {
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorId", MDC.get("requestId"));
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleForbidden(Model model) {
        model.addAttribute("errorCode", "403");
        model.addAttribute("errorId", MDC.get("requestId"));
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception ex, Model model) {
        String errorId = MDC.get("requestId");
        log.error("Unexpected error [{}]: {}", errorId, ex.getMessage(), ex);
        model.addAttribute("errorCode", "500");
        model.addAttribute("errorId", errorId);
        return "error/500";
    }
}
```

### 5.2 User-Friendly Error Pages

Each error page displays:
- Error type in user's language (Arabic/English)
- Correlation ID for support reference
- Suggestion to retry or contact support
- No stack traces or technical details exposed to users

```html
<!-- error/500.html -->
<div class="error-page">
    <h1 th:text="#{error.unexpected.title}">Unexpected Error</h1>
    <p th:text="#{error.unexpected.message}">An unexpected error occurred. Please try again.</p>
    <p th:text="#{error.reference(${errorId})}">Reference: REQ-abc-123</p>
    <a th:href="@{/dashboard}" th:text="#{error.back.dashboard}">Back to Dashboard</a>
</div>
```

### 5.3 Error Correlation IDs

Every request gets a unique correlation ID, logged across all operations for that request:

```java
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
            .orElse("REQ-" + UUID.randomUUID().toString().substring(0, 8));
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}
```

---

## 6. Circuit Breaker Readiness

### 6.1 Resilience4j for External Calls

**Decision**: Resilience4j circuit breaker for external service calls (LDAP, SMTP, webhooks).

**Rationale**: External services can become slow or unavailable. Without circuit breakers, the application threads block waiting for timeouts, eventually exhausting the thread pool and causing cascading failures. Resilience4j is lightweight, does not require Spring Cloud, and provides circuit breaker, retry, and timeout patterns.

**Phase 1**: Timeout + retry for LDAP and SMTP calls.

**Phase 2**: Full circuit breaker pattern for all external integrations.

```java
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)          // Open circuit at 50% failure rate
            .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait before half-open
            .slidingWindowSize(10)             // Evaluate last 10 calls
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();
        return CircuitBreakerRegistry.of(config);
    }
}

// Usage
@Service
public class LdapAuthenticationService {

    private final CircuitBreaker ldapCircuitBreaker;

    public boolean authenticate(String username, String password) {
        return CircuitBreaker.decorateSupplier(ldapCircuitBreaker,
            () -> ldapTemplate.authenticate(username, password)
        ).get();
    }
}
```

### 6.2 Circuit Breaker States

```
CLOSED (normal operation)
  → Failures exceed threshold → OPEN (fast-fail, no external calls)
    → Wait duration elapsed → HALF_OPEN (probe with limited calls)
      → Probes succeed → CLOSED
      → Probes fail → OPEN
```

---

## 7. Monitoring

### 7.1 Spring Actuator Metrics

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true  # Expose metrics in Prometheus format
    distribution:
      percentiles-histogram:
        http.server.requests: true
      sla:
        http.server.requests: 100ms, 500ms, 1s, 5s
```

### 7.2 Key Metrics

| Metric | Type | Alert Threshold |
|---|---|---|
| `http.server.requests` | Timer | P95 > 3s |
| `hikaricp.connections.active` | Gauge | > 80% of max |
| `hikaricp.connections.pending` | Gauge | > 0 sustained for 30s |
| `jvm.memory.used` | Gauge | > 80% of max heap |
| `jvm.gc.pause` | Timer | > 500ms |
| `system.cpu.usage` | Gauge | > 80% sustained |
| `tss.login.failures` | Counter | > 20/min (potential brute force) |
| `tss.errors.500` | Counter | > 5/min |
| `disk.free` | Gauge | < 2 GB |

### 7.3 JMX Exposure

```yaml
spring:
  jmx:
    enabled: true  # Enable JMX for monitoring tools (VisualVM, JConsole)
```

---

## 8. Zero-Downtime Deployment

### 8.1 Rolling Update Strategy

**Decision**: Rolling updates behind a load balancer for zero-downtime deployments.

**Procedure**:

```
1. Deploy new version to Instance B (behind load balancer)
2. Wait for Instance B health check to pass
3. Load balancer routes traffic to Instance B
4. Remove Instance A from load balancer
5. Deploy new version to Instance A
6. Wait for Instance A health check to pass
7. Add Instance A back to load balancer
```

### 8.2 Database Migration Compatibility

**Decision**: All Flyway migrations must be backward-compatible with the previous application version.

**Rules**:
- Add columns as nullable or with defaults (never NOT NULL without default on existing table)
- Never rename columns in a single release; use add-new, migrate-data, remove-old across two releases
- Never drop columns that the previous version reads
- Index creation uses `CREATE INDEX CONCURRENTLY` equivalent (SQL Server online index rebuild)

**Rationale**: During rolling updates, the old and new application versions run simultaneously against the same database. Backward-compatible migrations ensure the old version does not break when the new schema is applied.

---

## 9. Data Integrity

### 9.1 Database Transactions

**Decision**: Spring `@Transactional` on all service methods that modify data.

```java
@Service
@Transactional(readOnly = true)  // Default read-only
public class RiskService {

    @Transactional  // Read-write for modifications
    public RiskDto create(CreateRiskCommand command) { ... }

    @Transactional
    public RiskDto update(Long id, UpdateRiskCommand command) { ... }

    // Read operations inherit class-level readOnly = true
    public Page<RiskDto> findAll(Pageable pageable) { ... }
}
```

### 9.2 Optimistic Locking

**Decision**: SQL Server `ROWVERSION` (timestamp) column for optimistic concurrency control.

**Rationale**: Multiple users may edit the same risk, control, or assessment. Optimistic locking detects conflicts at save time rather than locking rows for the duration of editing (which would block other users). The `ROWVERSION` column auto-increments on every row update, and JPA compares it at save time.

```java
@Entity
@Table(name = "risks", schema = "erm")
public class Risk {

    @Version
    @Column(name = "rowversion", columnDefinition = "ROWVERSION")
    private byte[] version;

    // ... other fields
}
```

**Conflict handling**:

```java
@ExceptionHandler(OptimisticLockingFailureException.class)
public String handleConflict(Model model) {
    model.addAttribute("error", "This record was modified by another user. " +
        "Please refresh the page and try again.");
    return "error/conflict";
}
```

### 9.3 Constraint Enforcement

| Constraint Type | Implementation | Example |
|---|---|---|
| NOT NULL | Database column constraint | Risk title cannot be null |
| UNIQUE | Database unique constraint | Org code must be unique |
| FOREIGN KEY | Database FK constraint | Risk must reference valid category |
| CHECK | Database check constraint | Risk score must be between 1 and 25 |
| Business rules | Application-level validation | Risk cannot be approved by its owner |

**Decision**: Database constraints as the ultimate safety net; application-level validation for user-friendly error messages.

**Rationale**: Application validation provides immediate, localized feedback. Database constraints prevent data corruption even if application validation is bypassed (direct DB access, concurrent requests, bugs).

---

## 10. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Availability target | 99.5% business hours | 99.9%, 99.99% | Appropriate for GRC workload; avoids multi-region complexity |
| Health checks | Spring Actuator + custom indicators | Custom health endpoints | Standard, widely integrated with load balancers and monitoring |
| Degradation strategy | Continue without non-critical services | Fail-fast on any dependency | GRC users prefer partial functionality over full outage |
| Circuit breaker | Resilience4j | Hystrix, Sentinel | Lightweight, no Spring Cloud dependency, actively maintained |
| Concurrency control | Optimistic locking (ROWVERSION) | Pessimistic locking | Better UX for concurrent editing; conflicts are rare |
| Deployment strategy | Rolling updates | Blue-green, canary | Simplest for 2-3 instance deployments |
| Migration compatibility | Backward-compatible only | Breaking migrations with downtime | Enables zero-downtime rolling updates |
| Error handling | Correlation IDs + user-friendly pages | Technical error details | Security (no stack trace exposure) + supportability (correlation ID) |
