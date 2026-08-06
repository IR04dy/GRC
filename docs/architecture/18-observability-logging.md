# 18 - Observability and Logging
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the observability strategy for the TSS GRC platform, covering application logging, audit logging, metrics collection, health monitoring, and alerting. Observability is essential for troubleshooting production issues, meeting audit requirements, and ensuring platform reliability.

---

## 1. Logging Architecture

### 1.1 Framework

**Decision**: SLF4J facade with Logback implementation.

**Rationale**: SLF4J + Logback is the default logging stack in Spring Boot, the most widely used combination in the Java ecosystem, and provides all required features: structured logging, MDC support, async appenders, and configurable output formats. No reason to deviate from the default.

### 1.2 Log Categories

The platform produces two distinct log streams:

| Stream | Purpose | Destination | Retention |
|---|---|---|---|
| Application logs | Technical operations, errors, debug | File system + log aggregator | 30 days on disk, 90 days in aggregator |
| Audit logs | Business and security events | Database (`audit.audit_logs`) | 7 years (regulatory requirement) |

**Decision**: Audit logs are stored in the database, not in log files.

**Rationale**: Audit logs have different requirements from application logs: they must be queryable by entity, user, and date range from the application UI; they must be tamper-evident; and they must survive log rotation. Database storage meets all three requirements. Application logs, by contrast, are high-volume technical output best handled by file-based logging and log aggregation tools.

---

## 2. Application Log Configuration

### 2.1 Log Format

**Production**: Structured JSON for machine parsing.

```xml
<!-- logback-spring.xml -->
<configuration>
    <!-- Production: JSON format -->
    <springProfile name="prod,staging">
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>${LOG_DIR}/tss-grc.log</file>
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"app":"tss-grc","env":"${SPRING_PROFILES_ACTIVE}"}</customFields>
                <includeMdcKeyName>requestId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>orgId</includeMdcKeyName>
            </encoder>
            <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                <fileNamePattern>${LOG_DIR}/tss-grc.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
                <totalSizeCap>5GB</totalSizeCap>
            </rollingPolicy>
        </appender>
    </springProfile>

    <!-- Development: human-readable format -->
    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>
</configuration>
```

**JSON log entry example**:

```json
{
  "@timestamp": "2026-03-15T10:30:15.123Z",
  "level": "INFO",
  "logger": "com.tss.module.erm.service.RiskService",
  "message": "Risk created successfully",
  "requestId": "REQ-abc12345",
  "userId": "42",
  "orgId": "1",
  "thread": "http-nio-8080-exec-5",
  "app": "tss-grc",
  "env": "prod"
}
```

**Development log entry example**:

```
10:30:15.123 [http-nio-8080-exec-5] INFO  c.t.m.erm.service.RiskService [REQ-abc12345] - Risk created successfully
```

### 2.2 Log Levels

| Level | Usage | Examples |
|---|---|---|
| ERROR | Application failures requiring attention | Database connection failure, unhandled exception, data integrity violation |
| WARN | Potential issues that do not block operation | Slow query detected, retry attempt, deprecated API usage |
| INFO | Significant business operations | User login, risk created, report generated, configuration changed |
| DEBUG | Detailed technical information for development | SQL queries, method entry/exit, request/response payloads |
| TRACE | Very detailed tracing (rarely used in production) | ORM operations, framework internals |

**Production log level configuration**:

```yaml
logging:
  level:
    root: WARN
    com.tss: INFO
    com.tss.module: INFO
    com.tss.shared.security: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: WARN              # Enable DEBUG temporarily for SQL debugging
    org.hibernate.type.descriptor.sql: WARN
    com.zaxxer.hikari: WARN
```

---

## 3. Correlation IDs

### 3.1 MDC-Based Request Tracking

**Decision**: Every HTTP request gets a unique correlation ID propagated through MDC (Mapped Diagnostic Context) to all log entries.

**Rationale**: When investigating a production issue, the correlation ID allows filtering all log entries related to a single user action across all log sources. This is essential for debugging in multi-threaded, multi-instance environments.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
            .orElse("REQ-" + UUID.randomUUID().toString().substring(0, 8));

        MDC.put("requestId", requestId);

        // Add user and org context after authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TssUserDetails user) {
            MDC.put("userId", String.valueOf(user.getId()));
            MDC.put("orgId", String.valueOf(user.getOrgId()));
        }

        response.setHeader("X-Request-Id", requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 3.2 Async Context Propagation

MDC values are propagated to async threads via the `TenantAwareTaskDecorator` (see [16-scalability-performance.md](16-scalability-performance.md)):

```java
// Propagate MDC to async threads
@Override
public Runnable decorate(Runnable runnable) {
    Map<String, String> mdcContext = MDC.getCopyOfContextMap();
    return () -> {
        try {
            if (mdcContext != null) MDC.setContextMap(mdcContext);
            runnable.run();
        } finally {
            MDC.clear();
        }
    };
}
```

---

## 4. Request Logging

### 4.1 HTTP Request/Response Logging

**Decision**: Custom filter for request logging with duration tracking.

```java
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        long startTime = System.nanoTime();

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            int status = response.getStatus();

            if (shouldLog(request)) {
                log.info("HTTP {} {} {} {}ms user={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs,
                    getUsername());

                // Log slow requests as warnings
                if (durationMs > 3000) {
                    log.warn("Slow request: {} {} took {}ms",
                        request.getMethod(), request.getRequestURI(), durationMs);
                }
            }
        }
    }

    private boolean shouldLog(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Skip static resources and health checks
        return !uri.startsWith("/css") &&
               !uri.startsWith("/js") &&
               !uri.startsWith("/images") &&
               !uri.startsWith("/actuator/health");
    }
}
```

### 4.2 Logged Request Data

| Field | Logged | Not Logged (Security) |
|---|---|---|
| HTTP method | Yes | - |
| Request URI | Yes | - |
| Response status | Yes | - |
| Duration (ms) | Yes | - |
| Username | Yes | - |
| Client IP | Yes | - |
| Request body | No (only in DEBUG) | Passwords, tokens |
| Response body | No | Sensitive data |
| Request headers | No (only in DEBUG) | Authorization header values |
| Query parameters | Yes | - |

---

## 5. Audit Logging

### 5.1 Audit Log Architecture

Audit logs are a separate concern from application logs. They record business and security events for regulatory compliance and forensic investigation.

```java
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async  // Non-blocking; does not slow down business operations
    public void log(AuditEntry entry) {
        AuditLog auditLog = AuditLog.builder()
            .orgId(TenantContextHolder.get().orgId())
            .userId(SecurityContextHelper.getCurrentUserId())
            .username(SecurityContextHelper.getCurrentUsername())
            .action(entry.action())
            .entityType(entry.entityType())
            .entityId(entry.entityId())
            .description(entry.description())
            .previousValue(entry.previousValue())   // JSON of changed fields
            .newValue(entry.newValue())             // JSON of changed fields
            .ipAddress(entry.ipAddress())
            .userAgent(entry.userAgent())
            .requestId(MDC.get("requestId"))
            .createdAt(Instant.now())
            .build();

        auditLogRepository.save(auditLog);
    }
}
```

### 5.2 Audit Log Table

```sql
CREATE TABLE audit.audit_logs (
    id              BIGINT IDENTITY(1,1),
    org_id          BIGINT NOT NULL,
    user_id         BIGINT,
    username        NVARCHAR(100),
    action          NVARCHAR(50) NOT NULL,     -- CREATE, UPDATE, DELETE, VIEW, LOGIN, etc.
    entity_type     NVARCHAR(100),             -- Risk, Control, User, Role, etc.
    entity_id       BIGINT,
    description     NVARCHAR(1000),
    previous_value  NVARCHAR(MAX),             -- JSON snapshot of previous state
    new_value       NVARCHAR(MAX),             -- JSON snapshot of new state
    ip_address      NVARCHAR(45),
    user_agent      NVARCHAR(500),
    request_id      NVARCHAR(50),
    module          NVARCHAR(50),              -- erm, compliance, bcm, admin, etc.
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id, created_at)  -- Partitioned by created_at
) ON ps_audit_monthly(created_at);

CREATE INDEX ix_audit_org_date ON audit.audit_logs(org_id, created_at);
CREATE INDEX ix_audit_entity ON audit.audit_logs(entity_type, entity_id);
CREATE INDEX ix_audit_user ON audit.audit_logs(user_id, created_at);
CREATE INDEX ix_audit_action ON audit.audit_logs(org_id, action, created_at);
```

### 5.3 Audited Events

| Category | Events |
|---|---|
| Authentication | LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, ACCOUNT_LOCKED, PASSWORD_CHANGED |
| User management | USER_CREATED, USER_UPDATED, USER_DEACTIVATED, ROLE_ASSIGNED, ROLE_REMOVED |
| Risk management | RISK_CREATED, RISK_UPDATED, RISK_DELETED, RISK_APPROVED, RISK_ACCEPTED |
| Compliance | ASSESSMENT_CREATED, ASSESSMENT_SUBMITTED, ASSESSMENT_APPROVED, CONTROL_UPDATED |
| BCM | BIA_CREATED, BCP_UPDATED, EXERCISE_COMPLETED |
| Audit module | AUDIT_CREATED, FINDING_ADDED, AUDIT_REPORT_APPROVED |
| Administration | CONFIG_UPDATED, MODULE_TOGGLED, ORG_SETTINGS_CHANGED |
| Data operations | DATA_EXPORTED, DATA_IMPORTED, REPORT_GENERATED |

### 5.4 Audit Log Viewer

The admin UI provides an audit log viewer with:
- Filterable by date range, user, action type, entity type, module
- Searchable by description text
- Exportable to CSV/Excel
- Read-only (no modification or deletion through the application)

---

## 6. Metrics

### 6.1 Spring Actuator Metrics

**Decision**: Micrometer (via Spring Actuator) for metrics collection, Prometheus format for export.

**Rationale**: Micrometer is the metrics facade in Spring Boot (analogous to SLF4J for logging). It supports multiple monitoring backends. Prometheus is the most widely used time-series database for application metrics and integrates with Grafana for dashboarding.

### 6.2 Built-in Metrics

| Metric | Source | Use |
|---|---|---|
| `http.server.requests` | Spring MVC | Request rate, latency, error rate |
| `jvm.memory.*` | JVM | Heap usage, GC activity |
| `jvm.threads.*` | JVM | Thread pool utilization |
| `hikaricp.connections.*` | HikariCP | Connection pool health |
| `system.cpu.usage` | OS | CPU utilization |
| `disk.free` | OS | Disk space monitoring |
| `logback.events` | Logback | Error/warn log rate |

### 6.3 Custom Business Metrics

```java
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    // Track risk creation rate
    public void recordRiskCreated(String riskLevel) {
        registry.counter("tss.risks.created", "level", riskLevel).increment();
    }

    // Track login attempts
    public void recordLoginAttempt(boolean success) {
        registry.counter("tss.login.attempts",
            "result", success ? "success" : "failure").increment();
    }

    // Track report generation time
    public void recordReportGeneration(String reportType, long durationMs) {
        registry.timer("tss.reports.generation", "type", reportType)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Track active user sessions
    public void recordActiveSessions(int count) {
        registry.gauge("tss.sessions.active", count);
    }

    // Track import/export operations
    public void recordDataExport(String module, String format, int recordCount) {
        registry.counter("tss.data.exports",
            "module", module, "format", format).increment();
        registry.summary("tss.data.export.records", "module", module)
            .record(recordCount);
    }
}
```

---

## 7. Health Endpoints

### 7.1 Endpoint Configuration

| Endpoint | Purpose | Access |
|---|---|---|
| `/actuator/health` | Overall health status | Public (status only) |
| `/actuator/health/liveness` | JVM liveness probe | Public |
| `/actuator/health/readiness` | Full readiness check | Public |
| `/actuator/health` (detailed) | Component health details | Authenticated admin |
| `/actuator/metrics` | Metrics endpoint | Authenticated admin |
| `/actuator/info` | Application info (version, build) | Authenticated admin |
| `/actuator/prometheus` | Prometheus scrape endpoint | Internal network only |

### 7.2 Health Check Components

```java
// Readiness group includes all critical dependencies
management.endpoint.health.group.readiness.include=db,diskSpace,mail,storage

// Liveness group is minimal (is the JVM responsive?)
management.endpoint.health.group.liveness.include=ping
```

**Health response example**:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "Microsoft SQL Server",
        "validationQuery": "SELECT 1",
        "responseTimeMs": 12
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 107374182400,
        "free": 53687091200,
        "threshold": 1073741824
      }
    },
    "mail": {
      "status": "UP"
    },
    "storage": {
      "status": "UP",
      "details": {
        "freeSpaceGB": 50
      }
    }
  }
}
```

---

## 8. Log Rotation and Retention

### 8.1 Application Logs

| Setting | Value | Rationale |
|---|---|---|
| Max file size | 100 MB | Manageable file size for viewing/transfer |
| Rotation | Size-based + daily | New file each day or when size exceeded |
| Retention | 30 days on disk | Sufficient for immediate troubleshooting |
| Compression | Gzip for rotated files | Reduce disk usage by ~90% |
| Total size cap | 5 GB | Prevent disk exhaustion |

### 8.2 Audit Logs (Database)

| Setting | Value | Rationale |
|---|---|---|
| Active retention | 2 years in main table | Covers most audit inquiry periods |
| Archive | After 2 years, move to archive table | Reduces active table size |
| Total retention | 7 years | Saudi regulatory requirement (PDPL, NCA) |
| Purge | After 7 years, eligible for deletion | Requires explicit admin action |

---

## 9. Log Aggregation Readiness

### 9.1 ELK Stack Integration

**Decision**: Structured JSON logs are compatible with Elasticsearch/Logstash/Kibana (ELK) and Splunk without additional transformation.

**Logstash configuration** (example):

```
input {
  file {
    path => "/var/log/tss-grc/*.log"
    codec => "json"
    type => "tss-grc"
  }
}

filter {
  date {
    match => ["@timestamp", "ISO8601"]
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "tss-grc-%{+YYYY.MM.dd}"
  }
}
```

### 9.2 Syslog Readiness

For customers using syslog-based SIEM integration:

```xml
<!-- Logback syslog appender (optional) -->
<appender name="SYSLOG" class="ch.qos.logback.classic.net.SyslogAppender">
    <syslogHost>${SYSLOG_HOST}</syslogHost>
    <port>${SYSLOG_PORT}</port>
    <facility>LOCAL0</facility>
    <suffixPattern>tss-grc [%X{requestId}] %logger - %msg</suffixPattern>
</appender>
```

---

## 10. Performance Monitoring

### 10.1 Slow Query Logging

```yaml
# Hibernate slow query log
spring:
  jpa:
    properties:
      hibernate:
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: 1000  # Log queries taking > 1 second
```

**Application-level slow request logging** (see Section 4.1): Requests exceeding 3 seconds are logged at WARN level.

### 10.2 Request Duration Tracking

```java
// Tracked via Micrometer Timer automatically for all Spring MVC endpoints
// Additional custom timing for specific operations:

@Service
public class RiskService {

    private final MeterRegistry registry;

    public Page<RiskDto> search(String query, Pageable pageable) {
        Timer.Sample sample = Timer.start(registry);
        try {
            Page<RiskDto> results = // ... execute search
            return results;
        } finally {
            sample.stop(registry.timer("tss.search.duration", "module", "erm"));
        }
    }
}
```

---

## 11. Alerting Readiness

### 11.1 Prometheus + Grafana

**Decision**: Metrics exported in Prometheus format, dashboarded and alerted via Grafana.

**Alert rules** (Prometheus AlertManager format):

```yaml
groups:
  - name: tss-grc-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High 5xx error rate detected"

      - alert: HighResponseLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P95 response time exceeds 3 seconds"

      - alert: DatabaseConnectionExhaustion
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Database connection pool utilization above 80%"

      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "JVM heap usage above 85%"

      - alert: LoginBruteForce
        expr: rate(tss_login_attempts_total{result="failure"}[5m]) > 20
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Possible brute force attack: high login failure rate"
```

---

## 12. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Logging framework | SLF4J + Logback | Log4j2, JUL | Spring Boot default, MDC support, async appenders |
| Log format (prod) | Structured JSON | Plain text, CEF | Machine-parseable, ELK/Splunk compatible |
| Audit log storage | Database table | Log file, Elasticsearch | Queryable from UI, regulatory retention, tamper-evident |
| Metrics library | Micrometer + Prometheus | StatsD, Datadog | Spring Boot native, open-source, Grafana integration |
| Correlation | MDC-based request ID | W3C Trace Context | Simple, sufficient for monolith; W3C for future microservices |
| Log retention | 30 days disk, 90 days aggregator | 7 days, 365 days | Balances storage with troubleshooting needs |
| Audit retention | 7 years | 5 years, indefinite | Saudi regulatory requirement (NCA, PDPL) |
| Health checks | Spring Actuator + custom | Custom REST endpoints | Standard, widely supported, extensible |
