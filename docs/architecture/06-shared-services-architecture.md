# 06 - Shared Services Architecture

## Purpose

This document defines the design, API, and implementation details of the platform-level shared services consumed by all TSS GRC modules. These services provide cross-cutting capabilities that are not owned by any single business module.

---

## 1. Service Inventory

| Service | Package | Schema | Purpose |
|---|---|---|---|
| AuditService | `com.tss.platform.audit` | `audit` | Entity change tracking and action logging |
| WorkflowService | `com.tss.platform.workflow` | `workflow` | Configurable approval workflows and SLA tracking |
| NotificationService | `com.tss.platform.notification` | `platform` | Email and in-app notification delivery |
| FileStorageService | `com.tss.platform.storage` | `platform` | File upload, storage, and retrieval |
| LocalizationService | `com.tss.platform.i18n` | `platform` | Message resolution, translation table lookup |
| CacheService | `com.tss.platform.cache` | N/A | Cache abstraction with Caffeine backing |
| SearchService | `com.tss.platform.search` | N/A | Full-text search abstraction |
| ExportService | `com.tss.platform.export` | N/A | PDF, Excel, Word, CSV generation |

---

## 2. AuditService

### Purpose

Captures every significant change to every entity in the platform. The audit trail is the foundation for regulatory compliance evidence, providing a tamper-resistant record of who changed what, when, and why.

### Design Decisions

**Decision: JPA Entity Listeners for automatic capture**
- All auditable entities are annotated with `@EntityListeners(AuditEntityListener.class)`
- The listener intercepts `@PrePersist`, `@PreUpdate`, and `@PreRemove` lifecycle events
- Rationale: automatic capture ensures no developer can accidentally bypass audit logging

**Decision: AOP for service-level action auditing**
- Non-entity actions (login, export, bulk operations) are captured via `@Audited` custom annotation processed by an AOP aspect
- Rationale: entity listeners only capture data changes; service-level AOP captures higher-level business actions

**Decision: JSON diff storage for old/new values**
- Old and new entity states are serialized to JSON and stored in `audit.audit_logs`
- Rationale: JSON provides a flexible, queryable format that works with SQL Server's `JSON_VALUE` function

### Storage Schema

```sql
CREATE TABLE audit.audit_logs (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    module              VARCHAR(50) NOT NULL,       -- 'erm', 'cyber', 'bcm', etc.
    entity_type         VARCHAR(100) NOT NULL,      -- 'Risk', 'CyberIncident', etc.
    entity_id           BIGINT NOT NULL,
    action              VARCHAR(20) NOT NULL,       -- 'CREATE', 'UPDATE', 'DELETE'
    old_values          NVARCHAR(MAX),              -- JSON of previous state
    new_values          NVARCHAR(MAX),              -- JSON of new state
    changed_fields      NVARCHAR(MAX),              -- JSON array of changed field names
    user_id             BIGINT NOT NULL,
    user_name           NVARCHAR(200),
    session_id          VARCHAR(100),
    ip_address          VARCHAR(45),
    user_agent          NVARCHAR(500),
    timestamp           DATETIME2 NOT NULL DEFAULT GETUTCDATE(),

    INDEX IX_audit_entity (entity_type, entity_id),
    INDEX IX_audit_user (user_id, timestamp),
    INDEX IX_audit_module (module, timestamp),
    INDEX IX_audit_tenant (tenant_id, timestamp)
);
```

### Configuration

Audit behavior is configurable per entity type:

```yaml
tss:
  audit:
    enabled: true
    default-capture-mode: FULL      # FULL, CHANGES_ONLY, NONE
    entity-overrides:
      Risk: FULL                     # Capture all fields on every change
      LookupValue: CHANGES_ONLY     # Capture only changed fields
      UserSession: NONE             # Skip audit for session tracking
    excluded-fields:
      - password
      - apiKey
      - accessToken
    retention-days: 2555             # ~7 years for regulatory compliance
```

### Service Interface

```java
public interface AuditService {

    void logEntityChange(AuditEntry entry);

    void logAction(ActionAuditEntry entry);

    Page<AuditLogResponse> getAuditTrail(String entityType, Long entityId, Pageable pageable);

    Page<AuditLogResponse> search(AuditSearchCriteria criteria, Pageable pageable);

    byte[] exportAuditTrail(AuditExportRequest request);
}
```

---

## 3. WorkflowService

Documented in detail in [07-workflow-engine-architecture.md](07-workflow-engine-architecture.md). Summary of the shared service interface:

### Service Interface

```java
public interface WorkflowService {

    WorkflowInstanceResponse startWorkflow(String definitionKey, String entityType, Long entityId);

    WorkflowStepResponse submitStep(Long instanceId, Long stepId,
                                     WorkflowAction action, String comments);

    void delegateStep(Long stepId, Long toUserId, String reason);

    Page<WorkflowTaskResponse> getMyTasks(Long userId, Pageable pageable);

    WorkflowInstanceResponse getWorkflowStatus(String entityType, Long entityId);

    List<WorkflowHistoryResponse> getWorkflowHistory(String entityType, Long entityId);

    boolean isWorkflowComplete(String entityType, Long entityId);

    WorkflowAction getRequiredAction(String entityType, Long entityId, Long userId);
}
```

### Integration Pattern

Any entity can be workflow-enabled by implementing the `WorkflowEnabled` marker interface:

```java
public interface WorkflowEnabled {
    String getEntityType();   // e.g., "Risk"
    Long getEntityId();       // e.g., risk.getId()
    String getModuleName();   // e.g., "erm"
}
```

---

## 4. NotificationService

Documented in detail in [09-notification-architecture.md](09-notification-architecture.md). Summary of the shared service interface:

### Service Interface

```java
public interface NotificationService {

    void send(NotificationRequest request);

    void sendTemplated(String templateKey, Map<String, Object> variables, Long recipientUserId);

    void sendBulk(String templateKey, Map<String, Object> variables, List<Long> recipientUserIds);

    Page<NotificationResponse> getNotifications(Long userId, Boolean readOnly, Pageable pageable);

    long getUnreadCount(Long userId);

    void markAsRead(Long notificationId);

    void markAllAsRead(Long userId);
}
```

### Notification Request

```java
public record NotificationRequest(
    NotificationChannel channel,      // EMAIL, IN_APP, BOTH
    Long recipientUserId,
    String subjectKey,                 // Message bundle key for subject
    String bodyKey,                    // Message bundle key or template key for body
    Map<String, Object> variables,     // Template variables
    String module,                     // Source module
    String entityType,                 // Related entity type
    Long entityId,                     // Related entity ID
    NotificationPriority priority      // HIGH, NORMAL, LOW
) {}
```

---

## 5. FileStorageService

### Purpose

Provides a unified interface for storing, retrieving, and managing file attachments across all modules. Supports evidence documents, report exports, uploaded forms, and any binary content.

### Design Decisions

**Decision: Abstract interface with pluggable implementations**
- `FileStorageService` is an interface; the active implementation is selected by profile
- `LocalFileStorageService` for on-premises deployments (writes to local/NAS filesystem)
- `S3FileStorageService` for cloud deployments (writes to S3-compatible object storage)
- Rationale: on-premises government clients may not have object storage; SaaS deployments benefit from managed storage

**Decision: Metadata stored in database, content stored externally**
- File metadata (name, type, size, uploader, entity link) stored in `platform.attachments`
- File content stored on filesystem/S3 with a generated storage key
- Rationale: separates searchable metadata from binary content; enables metadata queries without scanning storage

**Decision: Structured storage paths**
- Files stored at: `{basePath}/{tenantId}/{module}/{entityType}/{entityId}/{uuid}_{originalFilename}`
- Rationale: organized by tenant and entity for easy browsing, backup, and cleanup

### Metadata Schema

```sql
CREATE TABLE platform.attachments (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    storage_key         VARCHAR(500) NOT NULL UNIQUE,
    original_filename   NVARCHAR(500) NOT NULL,
    content_type        VARCHAR(200) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    module              VARCHAR(50) NOT NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           BIGINT NOT NULL,
    description         NVARCHAR(1000),
    uploaded_by         BIGINT NOT NULL,
    uploaded_at         DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    checksum_sha256     VARCHAR(64),
    is_deleted          BIT NOT NULL DEFAULT 0,

    INDEX IX_attach_entity (entity_type, entity_id),
    INDEX IX_attach_tenant (tenant_id),
    INDEX IX_attach_module (module, entity_type)
);
```

### Service Interface

```java
public interface FileStorageService {

    AttachmentResponse store(FileUploadRequest request);

    Resource retrieve(String storageKey);

    AttachmentMetadataResponse getMetadata(String storageKey);

    List<AttachmentMetadataResponse> listByEntity(String entityType, Long entityId);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
```

### File Upload Request

```java
public record FileUploadRequest(
    MultipartFile file,
    String module,
    String entityType,
    Long entityId,
    String description
) {}
```

### Security Controls

| Control | Implementation |
|---|---|
| File type validation | Whitelist of allowed MIME types (PDF, DOCX, XLSX, PNG, JPG, CSV) |
| File size limit | Configurable maximum (default 50 MB) |
| Virus scanning | Pluggable `VirusScanService` interface (ClamAV integration for on-premises) |
| Access control | File access requires `VIEW` permission on the linked entity |
| Storage encryption | Filesystem-level encryption (BitLocker/EFS) or S3 server-side encryption |

---

## 6. LocalizationService

### Purpose

Provides bilingual (Arabic/English) text resolution for the entire platform. Handles both static UI text (message bundles) and dynamic user-entered content (translation tables).

### Design Decisions

**Decision: Dual-source localization**
- Static UI text: Spring `MessageSource` backed by properties files (`messages_en.properties`, `messages_ar.properties`)
- Dynamic content: Database translation table (`platform.translations`) for user-entered bilingual data
- Rationale: static text is developer-managed and deployed with the application; dynamic content is admin-managed at runtime

**Decision: Cookie + user preference locale resolution**
- Locale resolved in this order: (1) URL parameter `lang`, (2) cookie `TSS_LOCALE`, (3) user profile preference, (4) browser `Accept-Language`, (5) default `en`
- Rationale: allows unauthenticated pages to respect locale preference via cookie while authenticated users have a persistent preference

**Decision: Bilingual columns on entities (not EAV)**
- User-facing text fields stored as paired columns: `title_en` / `title_ar`, `description_en` / `description_ar`
- Rationale: paired columns are simpler to query, index, and join than an EAV translation table. The platform supports exactly two languages, so the column count is bounded.

### Translation Table (for admin-managed translations)

```sql
CREATE TABLE platform.translations (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    table_name          VARCHAR(100) NOT NULL,
    record_id           BIGINT NOT NULL,
    field_name          VARCHAR(100) NOT NULL,
    locale              VARCHAR(10) NOT NULL,       -- 'en', 'ar'
    translated_value    NVARCHAR(MAX) NOT NULL,
    updated_by          BIGINT,
    updated_at          DATETIME2 NOT NULL DEFAULT GETUTCDATE(),

    CONSTRAINT UQ_translation UNIQUE (tenant_id, table_name, record_id, field_name, locale),
    INDEX IX_trans_lookup (tenant_id, table_name, record_id, locale)
);
```

### Service Interface

```java
public interface LocalizationService {

    String getMessage(String key, Locale locale, Object... args);

    String getMessage(String key, Object... args);   // Uses current locale

    String getTranslation(String tableName, Long recordId, String fieldName, Locale locale);

    void saveTranslation(String tableName, Long recordId, String fieldName,
                         String locale, String value);

    Locale resolveUserLocale(Long userId);

    Locale resolveCurrentLocale();

    List<Locale> getSupportedLocales();
}
```

### Locale Configuration

```java
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("TSS_LOCALE");
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        source.setCacheSeconds(3600);
        return source;
    }
}
```

---

## 7. CacheService

### Purpose

Provides centralized cache management using Spring Cache abstraction backed by Caffeine for in-process caching. Designed for frequently-accessed, slowly-changing data such as lookups, permissions, and organization hierarchies.

### Design Decision: Caffeine with Spring Cache Abstraction

- **Why Caffeine**: High-performance, in-process Java cache with configurable size-based and time-based eviction. No external infrastructure required.
- **Why Spring Cache abstraction**: Allows transparent caching via `@Cacheable` annotations. Backing store can be swapped to Redis for multi-instance SaaS deployments without code changes.
- **Why not Redis initially**: On-premises deployments should not require additional infrastructure. Caffeine is sufficient for single-instance and low-traffic deployments.

### Cache Definitions

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheSpecification("maximumSize=10000,expireAfterWrite=30m");

        // Override defaults for specific caches
        Map<String, CaffeineSpec> cacheSpecs = Map.of(
            "lookups",              CaffeineSpec.parse("maximumSize=10000,expireAfterWrite=30m"),
            "permissions",          CaffeineSpec.parse("maximumSize=5000,expireAfterWrite=5m"),
            "organizationTree",     CaffeineSpec.parse("maximumSize=1000,expireAfterWrite=15m"),
            "translations",         CaffeineSpec.parse("maximumSize=50000,expireAfterWrite=30m"),
            "workflowDefinitions",  CaffeineSpec.parse("maximumSize=500,expireAfterWrite=60m"),
            "systemConfig",         CaffeineSpec.parse("maximumSize=1000,expireAfterWrite=60m"),
            "frameworkControls",    CaffeineSpec.parse("maximumSize=20000,expireAfterWrite=60m"),
            "userProfiles",         CaffeineSpec.parse("maximumSize=2000,expireAfterWrite=10m")
        );

        manager.setCacheNames(cacheSpecs.keySet());
        return manager;
    }
}
```

### Cache Usage Pattern

```java
@Service
public class LookupService {

    @Cacheable(value = "lookups", key = "#type + '_' + #tenantId")
    public List<LookupValueResponse> getByType(String type, Long tenantId) {
        return lookupRepository.findByTypeAndTenantId(type, tenantId)
                .stream()
                .map(lookupMapper::toResponse)
                .toList();
    }

    @CacheEvict(value = "lookups", key = "#type + '_' + #tenantId")
    public void evictLookupCache(String type, Long tenantId) {
        // Explicitly triggered when admin updates lookup values
    }
}
```

### Cache Eviction Strategy

| Trigger | Action |
|---|---|
| Admin updates lookup values | Evict `lookups` cache for the affected type |
| Role/permission changed | Evict `permissions` cache for affected users |
| Organization structure changed | Evict entire `organizationTree` cache |
| Translation updated | Evict specific `translations` entry |
| Workflow definition modified | Evict `workflowDefinitions` cache |
| System configuration changed | Evict `systemConfig` cache |

---

## 8. SearchService

### Purpose

Provides a full-text search abstraction for future implementation. Initially backed by SQL Server Full-Text Search; designed to be replaced with Elasticsearch if needed.

### Design Decision: SQL Server Full-Text Search Initially

- SQL Server Full-Text Search is built-in, requires no additional infrastructure
- Supports Arabic language word breaking and stemming
- Adequate for initial deployment scale (fewer than 50 concurrent users)
- The abstraction layer allows swapping to Elasticsearch when scale demands it

### Service Interface

```java
public interface SearchService {

    SearchResults search(SearchRequest request);

    void indexEntity(String module, String entityType, Long entityId,
                     Map<String, String> searchableFields);

    void removeFromIndex(String module, String entityType, Long entityId);

    void reindexModule(String module);
}

public record SearchRequest(
    String query,
    String module,           // Optional: limit to specific module
    String entityType,       // Optional: limit to specific entity type
    Locale locale,           // Search in specific language
    Pageable pageable
) {}

public record SearchResults(
    List<SearchHit> hits,
    long totalHits,
    long took                // Search duration in ms
) {}

public record SearchHit(
    String module,
    String entityType,
    Long entityId,
    String title,
    String snippet,          // Highlighted matching text
    float score
) {}
```

---

## 9. ExportService

### Purpose

Generates exportable documents (PDF, Excel, Word, CSV) from data provided by any module. Handles bilingual content, RTL layout for Arabic PDFs, and enterprise-grade formatting.

### Design Decisions

**Decision: Apache POI for Excel and Word**
- Apache POI provides full control over Excel (.xlsx) and Word (.docx) formatting
- Supports complex layouts: merged cells, conditional formatting, charts, headers/footers
- Rationale: the most mature Java library for Office document generation; used widely in enterprise

**Decision: OpenPDF for PDF generation**
- OpenPDF (fork of iText 4, LGPL licensed) for PDF generation
- Supports Arabic text rendering with proper RTL and font embedding
- Rationale: avoids iText 5+ AGPL licensing; OpenPDF is sufficient for report-quality PDFs

**Decision: Template-based generation**
- Excel exports use programmatic POI APIs (data-driven tabular output)
- PDF exports use Thymeleaf-to-HTML-to-PDF pipeline for complex layouts (alternative: direct OpenPDF for simple reports)
- Word exports use Apache POI XWPF for programmatic document construction
- Rationale: different formats benefit from different approaches; tabular data is best built programmatically, while complex layouts benefit from HTML templating

### Service Interface

```java
public interface ExportService {

    byte[] exportToExcel(ExcelExportRequest request);

    byte[] exportToPdf(PdfExportRequest request);

    byte[] exportToWord(WordExportRequest request);

    byte[] exportToCsv(CsvExportRequest request);
}
```

### Excel Export Request

```java
public record ExcelExportRequest(
    String sheetName,
    List<ColumnDefinition> columns,
    List<Map<String, Object>> rows,
    String title,
    String subtitle,
    Locale locale,                    // Determines text direction
    boolean includeHeaderRow,
    boolean autoSizeColumns
) {}

public record ColumnDefinition(
    String key,                       // Field key in row data
    String headerEn,
    String headerAr,
    ColumnType type,                  // TEXT, NUMBER, DATE, PERCENTAGE
    int widthChars                    // Column width in characters
) {}
```

### PDF Export with RTL Support

```java
@Service
public class PdfExportServiceImpl implements PdfExportService {

    private static final String ARABIC_FONT = "fonts/NotoSansArabic-Regular.ttf";

    public byte[] generatePdf(PdfExportRequest request) {
        Document document = new Document(PageSize.A4);
        if ("ar".equals(request.locale().getLanguage())) {
            document.setPageSize(PageSize.A4);
            // RTL: text runs right-to-left, tables mirror columns
        }

        BaseFont arabicFont = BaseFont.createFont(ARABIC_FONT,
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font bodyFont = new Font(arabicFont, 11);

        // Generate document content with proper font and direction
        // ...
    }
}
```

### Export Capabilities by Format

| Capability | Excel (.xlsx) | PDF | Word (.docx) | CSV |
|---|---|---|---|---|
| Tabular data | Yes | Yes | Yes | Yes |
| Charts | Yes (via POI) | No | No | No |
| RTL/Arabic | Yes (cell direction) | Yes (page direction) | Yes (paragraph direction) | N/A |
| Images/logos | Yes | Yes | Yes | No |
| Headers/footers | Yes | Yes | Yes | No |
| Conditional formatting | Yes | Via CSS | Limited | No |
| Multiple sheets | Yes | N/A | N/A | N/A |
| Password protection | Yes | Yes | Yes | No |

---

## 10. Service Dependency Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BUSINESS MODULES                             │
│  erm, cyber, bcm, privacy, audit, datagov, reporting, admin        │
└──────────┬──────────────────────────────────────────────────────────┘
           │ consume
           v
┌─────────────────────────────────────────────────────────────────────┐
│                        SHARED SERVICES                              │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ AuditService │  │WorkflowSvc   │  │ NotificationService      │  │
│  │ (auto via    │  │ (explicit    │  │ (event-driven +          │  │
│  │  listeners)  │  │  service     │  │  explicit calls)         │  │
│  └──────────────┘  │  calls)      │  └──────────────────────────┘  │
│                    └──────────────┘                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │FileStorageSvc│  │Localization  │  │ ExportService            │  │
│  │ (explicit    │  │Service       │  │ (PDF, Excel, Word, CSV)  │  │
│  │  calls)      │  │ (auto via    │  └──────────────────────────┘  │
│  └──────────────┘  │  Spring i18n)│                                 │
│                    └──────────────┘  ┌──────────────────────────┐  │
│  ┌──────────────┐                    │ SearchService            │  │
│  │ CacheService │                    │ (full-text search)       │  │
│  │ (annotations)│                    └──────────────────────────┘  │
│  └──────────────┘                                                   │
└─────────────────────────────────────────────────────────────────────┘
           │ depend on
           v
┌─────────────────────────────────────────────────────────────────────┐
│                     INFRASTRUCTURE                                   │
│  SQL Server, Filesystem/S3, SMTP, Caffeine Cache                    │
└─────────────────────────────────────────────────────────────────────┘
```
