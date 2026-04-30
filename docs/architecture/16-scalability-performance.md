# 16 - Scalability and Performance

## Purpose

This document defines the scalability and performance architecture for the TSS GRC platform. It covers application scaling, database optimization, caching, async processing, and growth planning. The platform must perform well for organizations with up to 500 concurrent users and data volumes growing over a 5-year horizon.

---

## 1. Performance Targets

| Metric | Target | Rationale |
|---|---|---|
| Page load time | < 2 seconds (P95) | Acceptable UX for enterprise applications |
| API response time | < 500ms (P95) for read, < 1s for write | Standard SLA for internal tools |
| Dashboard render | < 3 seconds (P95) | Dashboards involve aggregation queries |
| Report generation | < 30 seconds for standard reports | Async for large reports |
| Search response | < 1 second (P95) | Interactive search UX |
| Concurrent users | 500 per instance | Covers large Saudi enterprise/government orgs |
| Database size | Up to 500 GB over 5 years | Audit logs are the primary growth driver |

---

## 2. Application Scaling

### 2.1 Horizontal Scaling

**Decision**: Horizontal scaling via multiple application server instances behind a load balancer.

**Rationale**: The modular monolith is designed as a stateless application (session state in server memory for Phase 1, externalizable for Phase 2). Multiple instances behind a load balancer provide both throughput scaling and availability.

```
                    ┌─────────────────┐
                    │  Load Balancer   │
                    │  (Nginx/F5/ALB)  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼───────┐ ┌───▼──────────┐ ┌▼──────────────┐
     │  App Instance 1 │ │ App Instance 2│ │ App Instance 3│
     │  (Spring Boot)  │ │ (Spring Boot) │ │ (Spring Boot) │
     └────────┬───────┘ └───┬──────────┘ └┬──────────────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────▼────────┐
                    │   SQL Server     │
                    │   (Primary)      │
                    └─────────────────┘
```

### 2.2 Session Strategy

**Phase 1: Sticky Sessions**

**Decision**: Load balancer sticky sessions (cookie-based affinity) for Phase 1.

**Rationale**: Sticky sessions are the simplest approach when sessions are stored in server memory. They work with any load balancer and require zero additional infrastructure. The trade-off is that instance failure loses sessions for attached users (they must re-login), which is acceptable for Phase 1.

**Configuration**: Load balancer routes by session cookie (`TSSSESSIONID`). If an instance fails, the load balancer redirects to another instance and the user re-authenticates.

**Phase 2: Distributed Sessions**

```java
// Spring Session with Redis for distributed sessions
@Configuration
@EnableRedisHttpSession
public class SessionConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("${REDIS_HOST}");
        config.setPort(6379);
        return new LettuceConnectionFactory(config);
    }
}
```

When distributed sessions are enabled, sticky sessions are no longer required and any instance can serve any user.

---

## 3. Database Scaling

### 3.1 Connection Pooling

**Decision**: HikariCP with tuned pool size.

**Rationale**: HikariCP is the default and fastest connection pool in Spring Boot. Proper sizing prevents connection exhaustion under load while avoiding idle connection waste.

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 10            # Minimum idle connections
      maximum-pool-size: 30       # Max connections per instance
      idle-timeout: 300000        # 5 minutes idle before close
      max-lifetime: 1800000       # 30 minutes max connection lifetime
      connection-timeout: 20000   # 20 seconds to obtain connection
      leak-detection-threshold: 60000  # 1 minute leak detection
      pool-name: tss-hikari-pool
```

**Sizing rationale**: For a typical deployment with 2-3 app instances, 30 connections per instance = 60-90 total connections. SQL Server default max is 32,767. This leaves ample headroom for reporting tools, admin connections, and monitoring.

### 3.2 Read Replica Readiness

**Phase 1**: Single SQL Server instance.

**Phase 2**: SQL Server Always On Availability Groups with read-intent routing for reporting queries.

```java
// Future: Read-only datasource for reporting
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        // Read-write primary
    }

    @Bean
    @Qualifier("readOnly")
    public DataSource readOnlyDataSource() {
        // Read-only replica connection
        // applicationIntent=ReadOnly in connection string
    }
}
```

---

## 4. Caching

### 4.1 Cache Strategy

**Decision**: Caffeine for local in-memory caching.

**Rationale**: Caffeine is the highest-performance Java caching library, integrated with Spring Cache abstraction. Local caching is sufficient for Phase 1 (single instance or sticky sessions). For Phase 2 multi-instance with cache coherence needs, Redis or Hazelcast can be added behind the same Spring Cache abstraction.

### 4.2 Cached Data

| Cache Name | TTL | Max Size | Data | Rationale |
|---|---|---|---|---|
| `permissions` | 15 min | 1,000 | User permission sets | Checked on every request |
| `lookups` | 30 min | 5,000 | Dropdown values (categories, statuses) | Rarely change, frequently read |
| `configurations` | 5 min | 500 | System and org config values | Frequently read, occasional update |
| `orgDetails` | 15 min | 100 | Organization metadata, branding | Used on every page render |
| `frameworkControls` | 60 min | 10,000 | Compliance framework control definitions | Large, static reference data |
| `dashboardStats` | 5 min | 200 | Dashboard aggregate counts | Expensive queries, acceptable staleness |

### 4.3 Implementation

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheSpecification("maximumSize=1000,expireAfterWrite=15m");
        // Per-cache configuration
        manager.registerCustomCache("permissions",
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(15)).build());
        manager.registerCustomCache("lookups",
            Caffeine.newBuilder().maximumSize(5000).expireAfterWrite(Duration.ofMinutes(30)).build());
        manager.registerCustomCache("configurations",
            Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(5)).build());
        manager.registerCustomCache("dashboardStats",
            Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(5)).build());
        return manager;
    }
}

// Usage
@Service
public class LookupService {

    @Cacheable(value = "lookups", key = "#orgId + ':' + #lookupType")
    public List<LookupDto> getLookupValues(Long orgId, String lookupType) {
        return lookupRepository.findByOrgIdAndType(orgId, lookupType)
            .stream().map(this::toDto).toList();
    }

    @CacheEvict(value = "lookups", key = "#orgId + ':' + #lookupType")
    public void updateLookup(Long orgId, String lookupType, LookupDto dto) {
        // update logic
    }
}
```

### 4.4 Cache Invalidation Strategy

| Trigger | Action |
|---|---|
| Role/permission change | Evict `permissions` cache for affected users |
| Lookup data update | Evict specific lookup cache entry |
| Configuration change | Evict specific config cache entry |
| Branding change | Evict org details cache entry |
| Framework import | Evict entire `frameworkControls` cache |

---

## 5. Async Processing

### 5.1 Spring @Async

**Decision**: Spring `@Async` for fire-and-forget operations that do not need to block the user request.

**Rationale**: These operations are secondary to the user's primary action. Email notification after risk creation, for example, should not delay the HTTP response. Spring `@Async` is the simplest approach, runs on a configurable thread pool, and requires no external message broker.

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tss-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 5.2 Async Operations

| Operation | Why Async | Failure Handling |
|---|---|---|
| Email notifications | SMTP is slow (1-5s per email) | Retry 3x, log failure, do not block user |
| Audit log writing | Must not slow down business operations | Retry, eventually consistent |
| Report generation (large) | Can take 30+ seconds | User notified when complete |
| Data import processing | Bulk operations are slow | Progress tracking, result notification |
| LDAP attribute sync | External system call | Retry, log failure |

### 5.3 Tenant Context Propagation

**Important**: ThreadLocal tenant context must be propagated to async threads.

```java
@Component
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        TenantContext context = TenantContextHolder.get();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return () -> {
            try {
                TenantContextHolder.set(context);
                SecurityContextHolder.getContext().setAuthentication(auth);
                runnable.run();
            } finally {
                TenantContextHolder.clear();
                SecurityContextHolder.clearContext();
            }
        };
    }
}
```

---

## 6. Query Optimization

### 6.1 N+1 Prevention

**Decision**: Explicit fetch strategies to prevent N+1 queries.

```java
// BAD: N+1 - each risk triggers a separate query for owner
@Query("SELECT r FROM Risk r")
List<Risk> findAll();

// GOOD: JOIN FETCH - single query
@Query("SELECT r FROM Risk r JOIN FETCH r.owner JOIN FETCH r.category")
List<Risk> findAllWithDetails();

// GOOD: EntityGraph for flexible fetching
@EntityGraph(attributePaths = {"owner", "category", "businessUnit"})
@Query("SELECT r FROM Risk r WHERE r.orgId = :orgId")
Page<Risk> findByOrgId(@Param("orgId") Long orgId, Pageable pageable);
```

### 6.2 Pagination for All List Queries

**Rule**: Every list query must use `Pageable`. No unbounded `findAll()` calls in service or controller layers.

```java
// Repository
Page<Risk> findByOrgIdAndStatus(Long orgId, RiskStatus status, Pageable pageable);

// Service - never returns full list
public Page<RiskDto> findAll(Pageable pageable) {
    return riskRepository
        .findAll(withTenantFilter(), pageable)
        .map(this::toDto);
}
```

### 6.3 Database Indexing Strategy

| Table | Index | Columns | Purpose |
|---|---|---|---|
| All tenant tables | ix_{table}_org | (org_id) | Tenant filtering |
| erm.risks | ix_risks_org_status | (org_id, status) | Filtered risk lists |
| erm.risks | ix_risks_org_level | (org_id, risk_level) | Dashboard aggregation |
| erm.risks | ix_risks_org_bu | (org_id, business_unit_id) | BU-scoped queries |
| compliance.assessments | ix_assessments_org_framework | (org_id, framework_id) | Framework-filtered lists |
| audit.audit_logs | ix_audit_org_date | (org_id, created_at) | Audit log queries |
| audit.audit_logs | ix_audit_entity | (entity_type, entity_id) | Entity history lookup |

### 6.4 Lazy Loading Strategy

**Decision**: JPA default lazy loading for associations, with explicit eager fetching via `@EntityGraph` or `JOIN FETCH` where needed.

**Rationale**: Lazy loading prevents loading entire object graphs when only a subset is needed. Explicit fetch strategies (EntityGraph, JOIN FETCH) are applied per query to load exactly what each use case requires.

**Open Session in View**: Disabled.

```yaml
spring:
  jpa:
    open-in-view: false  # Prevent lazy loading in view layer; force explicit fetching
```

**Rationale for disabling OSIV**: Open Session in View masks N+1 problems by silently executing queries during template rendering. Disabling it forces developers to explicitly define fetch strategies, resulting in predictable, optimizable query patterns.

---

## 7. Large Table Management

### 7.1 Audit Log Partitioning

**Decision**: Partition `audit.audit_logs` by month using SQL Server table partitioning.

**Rationale**: Audit logs are append-only and grow continuously. Partitioning by month enables efficient querying of recent data, fast archival of old partitions, and manageable backup/restore of individual partitions.

```sql
-- Partition function by month
CREATE PARTITION FUNCTION pf_audit_monthly (DATETIME2)
AS RANGE RIGHT FOR VALUES (
    '2026-01-01', '2026-02-01', '2026-03-01', '2026-04-01',
    '2026-05-01', '2026-06-01', '2026-07-01', '2026-08-01',
    '2026-09-01', '2026-10-01', '2026-11-01', '2026-12-01',
    '2027-01-01'
    -- Extended annually by scheduled maintenance job
);

CREATE PARTITION SCHEME ps_audit_monthly
AS PARTITION pf_audit_monthly
ALL TO ([PRIMARY]);

CREATE TABLE audit.audit_logs (
    id              BIGINT IDENTITY(1,1),
    org_id          BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    -- ... other columns ...
    CONSTRAINT pk_audit_logs PRIMARY KEY (id, created_at)
) ON ps_audit_monthly(created_at);
```

### 7.2 Data Archival

| Data Type | Retention (Active) | Archive Strategy | Archive Storage |
|---|---|---|---|
| Audit logs | 2 years | Move to archive table after 2 years | Same DB, compressed |
| Notifications | 6 months | Delete after 6 months | Not archived |
| Session history | 90 days | Delete after 90 days | Not archived |
| Business data (risks, controls) | Indefinite | Soft delete, never physically removed | Same DB |
| Report outputs | 1 year | Move to cold storage after 1 year | File system archive |

### 7.3 Archival Process

```java
@Scheduled(cron = "0 0 2 1 * ?")  // 2 AM on 1st of each month
public void archiveOldAuditLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusYears(2);
    int archived = auditArchivalService.archiveBefore(cutoff);
    log.info("Archived {} audit log records older than {}", archived, cutoff);
}
```

---

## 8. Report Optimization

### 8.1 Pre-Computed Reporting Views

**Decision**: Database views with pre-aggregated data for dashboard and reporting queries.

**Rationale**: Dashboard queries that aggregate across thousands of risks, controls, and assessments are expensive if computed from normalized tables on every request. Database views pre-join and pre-aggregate common reporting dimensions, reducing query complexity and execution time.

```sql
-- Risk summary view for dashboards
CREATE VIEW reporting.vw_risk_summary AS
SELECT
    r.org_id,
    r.business_unit_id,
    r.risk_level,
    r.status,
    COUNT(*) AS risk_count,
    AVG(r.risk_score) AS avg_risk_score,
    MAX(r.updated_at) AS last_updated
FROM erm.risks r
WHERE r.is_deleted = 0
GROUP BY r.org_id, r.business_unit_id, r.risk_level, r.status;

-- Compliance posture view
CREATE VIEW reporting.vw_compliance_posture AS
SELECT
    a.org_id,
    a.framework_id,
    f.name_en AS framework_name,
    a.status,
    COUNT(*) AS control_count,
    SUM(CASE WHEN a.compliance_status = 'COMPLIANT' THEN 1 ELSE 0 END) AS compliant_count,
    CAST(SUM(CASE WHEN a.compliance_status = 'COMPLIANT' THEN 1.0 ELSE 0 END) /
         NULLIF(COUNT(*), 0) * 100 AS DECIMAL(5,2)) AS compliance_percentage
FROM compliance.control_assessments a
JOIN compliance.frameworks f ON f.id = a.framework_id
GROUP BY a.org_id, a.framework_id, f.name_en, a.status;
```

### 8.2 Dashboard Query Caching

Dashboard aggregate queries are cached for 5 minutes (see Section 4.2). The cache is invalidated when underlying data changes significantly (bulk import, status change).

---

## 9. Static Resource Optimization

### 9.1 Browser Caching

**Decision**: Aggressive browser caching with cache-busting versioning for static resources.

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**", "/js/**", "/images/**")
            .addResourceLocations("classpath:/static/css/",
                                   "classpath:/static/js/",
                                   "classpath:/static/images/")
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS))
            .resourceChain(true)
            .addResolver(new VersionResourceResolver()
                .addContentVersionStrategy("/**"));
    }
}
```

**Thymeleaf usage**:

```html
<!-- Generates versioned URL: /css/app-abc123def.css -->
<link rel="stylesheet" th:href="@{/css/app.css}"/>
```

### 9.2 Asset Optimization

| Technique | Implementation |
|---|---|
| CSS minification | Build-time minification (Maven resource plugin) |
| JS minification | Build-time minification |
| Image optimization | Pre-optimized assets, WebP format where supported |
| Gzip compression | Server/load balancer gzip for text resources |
| Bundle reduction | Minimal JS dependencies (HTMX + Alpine.js only) |

---

## 10. Database Growth Planning

### 10.1 5-Year Growth Projections

Assumptions: Mid-size organization, 200 users, moderate GRC activity.

| Table Category | Year 1 | Year 3 | Year 5 | Growth Driver |
|---|---|---|---|---|
| Audit logs | 5M rows / 2 GB | 15M rows / 6 GB | 30M rows / 12 GB | Every user action logged |
| Risks | 500 rows / 5 MB | 2,000 rows / 20 MB | 5,000 rows / 50 MB | Risk register growth |
| Controls | 1,000 rows / 10 MB | 3,000 rows / 30 MB | 5,000 rows / 50 MB | Control catalog expansion |
| Assessments | 2,000 rows / 50 MB | 10,000 rows / 250 MB | 25,000 rows / 600 MB | Annual assessment cycles |
| Documents/attachments | 1,000 files / 5 GB | 5,000 files / 25 GB | 10,000 files / 50 GB | Evidence and policy docs |
| **Total (estimated)** | **~10 GB** | **~35 GB** | **~75 GB** | |

**Multi-tenant SaaS** (10 organizations): Multiply by 10 for approximately 100-750 GB over 5 years.

### 10.2 Storage Monitoring

```sql
-- Table size monitoring query
SELECT
    s.name AS schema_name,
    t.name AS table_name,
    SUM(p.rows) AS row_count,
    SUM(a.total_pages) * 8 / 1024 AS total_size_mb,
    SUM(a.used_pages) * 8 / 1024 AS used_size_mb
FROM sys.tables t
JOIN sys.schemas s ON t.schema_id = s.schema_id
JOIN sys.partitions p ON t.object_id = p.object_id AND p.index_id IN (0, 1)
JOIN sys.allocation_units a ON p.partition_id = a.container_id
GROUP BY s.name, t.name
ORDER BY total_size_mb DESC;
```

**Alerting thresholds**:
- Database 70% capacity: warning alert
- Database 85% capacity: critical alert
- Audit log partition > 50 GB: archival recommended
- Connection pool utilization > 80%: scaling alert

---

## 11. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Caching library | Caffeine | EhCache, Guava | Highest performance, Spring Boot default L2 cache |
| Session scaling (Phase 1) | Sticky sessions | Redis from start | Simplest; avoids Redis infrastructure for Phase 1 |
| Session scaling (Phase 2) | Spring Session + Redis | Hazelcast, database sessions | Redis is fastest; Spring Session provides seamless swap |
| Connection pool | HikariCP (30 per instance) | C3P0, DBCP2 | Fastest, Spring Boot default, leak detection built-in |
| Async framework | Spring @Async | Kafka, RabbitMQ | No extra infrastructure; sufficient for Phase 1 volumes |
| Audit partitioning | Monthly | Yearly, daily | Monthly balances partition count with query performance |
| OSIV | Disabled | Enabled | Forces explicit fetch strategies, prevents hidden N+1 |
| Pagination | Mandatory on all lists | Optional | Prevents accidental full-table loads |
| Report optimization | Database views | Materialized views, OLAP cube | Views are simple, auto-refreshing, sufficient for volumes |
