# 12 - API and Integration Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the API design standards, integration patterns, and external system connectivity for the TSS GRC platform. It covers internal API conventions for the Thymeleaf web layer, the external REST API surface for future consumers, and integration points with enterprise systems.

---

## 1. Internal APIs: Web Layer

### 1.1 Controller Architecture

**Decision**: Spring MVC controllers serve both Thymeleaf views and JSON responses from the same module, distinguished by URL pattern and content negotiation.

**Rationale**: The primary interface is server-rendered HTML (Thymeleaf). JSON endpoints support HTMX partial updates and AJAX interactions within the web UI. Separating these into distinct controllers keeps responsibilities clear.

```
/erm/risks                → Thymeleaf HTML (full page)
/erm/risks/fragments/list → Thymeleaf HTML fragment (HTMX partial)
/api/v1/erm/risks         → JSON response (external API)
```

### 1.2 Web Controller Conventions

```java
@Controller
@RequestMapping("/erm/risks")
@RequiredArgsConstructor
public class RiskWebController {

    private final RiskService riskService;

    @GetMapping
    public String list(Model model, Pageable pageable) {
        model.addAttribute("risks", riskService.findAll(pageable));
        return "erm/risks/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("risk", riskService.findById(id));
        return "erm/risks/detail";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute CreateRiskForm form,
                         BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) return "erm/risks/create";
        riskService.create(form.toCommand());
        redirect.addFlashAttribute("success", true);
        return "redirect:/erm/risks";
    }
}
```

### 1.3 HTMX Fragment Endpoints

For dynamic partial page updates without full-page reload:

```java
@Controller
@RequestMapping("/erm/risks/fragments")
public class RiskFragmentController {

    @GetMapping("/list")
    public String listFragment(Model model, Pageable pageable,
                                @RequestParam(required = false) String search) {
        model.addAttribute("risks", riskService.search(search, pageable));
        return "erm/risks/fragments/list :: riskTable";
    }

    @GetMapping("/detail/{id}")
    public String detailFragment(@PathVariable Long id, Model model) {
        model.addAttribute("risk", riskService.findById(id));
        return "erm/risks/fragments/detail :: riskDetail";
    }
}
```

---

## 2. External REST API

### 2.1 API Versioning Strategy

**Decision**: URL-based versioning with `/api/v1/` prefix.

**Rationale**: URL-based versioning is the most explicit and debuggable approach. It is visible in logs, easy to route at the load balancer level, and unambiguous. Header-based versioning (Accept header) adds complexity without meaningful benefit for the TSS use case.

```
/api/v1/erm/risks          → Version 1 of risk API
/api/v2/erm/risks          → Version 2 (future, breaking changes only)
```

**Version lifecycle**:
- New versions introduced only for breaking changes
- Previous version supported for minimum 12 months after deprecation
- Non-breaking additions (new fields, new endpoints) added to current version

### 2.2 API Controller Conventions

```java
@RestController
@RequestMapping("/api/v1/erm/risks")
@RequiredArgsConstructor
public class RiskApiController {

    private final RiskService riskService;

    @GetMapping
    public ResponseEntity<PageResponse<RiskDto>> list(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long businessUnitId) {
        Page<RiskDto> page = riskService.findAll(pageable, status, businessUnitId);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RiskDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(riskService.findById(id));
    }

    @PostMapping
    public ResponseEntity<RiskDto> create(@Valid @RequestBody CreateRiskRequest request) {
        RiskDto created = riskService.create(request.toCommand());
        URI location = URI.create("/api/v1/erm/risks/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RiskDto> update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRiskRequest request) {
        return ResponseEntity.ok(riskService.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        riskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 2.3 API Design Standards

**Resource naming**:
- Plural nouns for collections: `/api/v1/erm/risks`, `/api/v1/compliance/controls`
- Singular resource by ID: `/api/v1/erm/risks/{id}`
- Sub-resources for relationships: `/api/v1/erm/risks/{id}/treatments`
- Actions as sub-resources when needed: `/api/v1/erm/risks/{id}/approve`

**HTTP methods**:

| Method | Usage | Response Code |
|---|---|---|
| GET | Read resource(s) | 200 OK |
| POST | Create resource | 201 Created (with Location header) |
| PUT | Full update | 200 OK |
| PATCH | Partial update | 200 OK |
| DELETE | Remove resource | 204 No Content |

**Standard headers**:

| Header | Direction | Purpose |
|---|---|---|
| `Content-Type: application/json` | Request/Response | JSON payload |
| `Accept-Language: ar` / `en` | Request | Response language for messages and labels |
| `X-Request-Id` | Request/Response | Correlation ID for tracing |
| `X-Total-Count` | Response | Total record count for paginated responses |

---

## 3. Pagination

### 3.1 Request Format

**Decision**: Offset-based pagination with `page` and `size` parameters.

**Rationale**: Offset-based pagination is simple, well-supported by Spring Data, and sufficient for GRC data volumes. Cursor-based pagination adds complexity without benefit when datasets are typically under 100K records per module per tenant.

```
GET /api/v1/erm/risks?page=0&size=20&sort=createdAt,desc
```

| Parameter | Default | Max | Description |
|---|---|---|---|
| `page` | 0 | - | Zero-based page index |
| `size` | 20 | 100 | Items per page |
| `sort` | varies | - | Field and direction (e.g., `name,asc`) |

### 3.2 Response Format

```json
{
  "content": [
    { "id": 1, "title": "Cybersecurity Risk", "status": "OPEN" },
    { "id": 2, "title": "Data Breach Risk", "status": "MITIGATED" }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

**Implementation**:

```java
public record PageResponse<T>(
    List<T> content,
    PageMetadata page
) {
    public static <T> PageResponse<T> of(Page<T> springPage) {
        return new PageResponse<>(
            springPage.getContent(),
            new PageMetadata(
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
            )
        );
    }

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {}
}
```

---

## 4. Filtering and Search

### 4.1 Simple Filtering

Common filters exposed as query parameters:

```
GET /api/v1/erm/risks?status=OPEN&businessUnitId=5&riskLevel=HIGH
GET /api/v1/compliance/controls?frameworkId=3&status=IMPLEMENTED
```

### 4.2 Complex Filtering (Specification Pattern)

**Decision**: Spring Data JPA Specifications for complex, dynamic queries.

**Rationale**: The Specification pattern allows composable, type-safe query building without string concatenation or custom query languages. It maps directly to JPA Criteria API and integrates with Spring Data repositories.

```java
public class RiskSpecifications {

    public static Specification<Risk> hasStatus(String status) {
        return (root, query, cb) ->
            status == null ? null : cb.equal(root.get("status"), RiskStatus.valueOf(status));
    }

    public static Specification<Risk> inBusinessUnit(Long buId) {
        return (root, query, cb) ->
            buId == null ? null : cb.equal(root.get("businessUnit").get("id"), buId);
    }

    public static Specification<Risk> riskLevelIn(List<String> levels) {
        return (root, query, cb) ->
            levels == null ? null : root.get("riskLevel").in(levels);
    }

    public static Specification<Risk> titleContains(String search) {
        return (root, query, cb) ->
            search == null ? null : cb.or(
                cb.like(cb.lower(root.get("titleEn")), "%" + search.toLowerCase() + "%"),
                cb.like(root.get("titleAr"), "%" + search + "%")
            );
    }
}
```

### 4.3 Full-Text Search

**Phase 1**: LIKE-based search on key text fields (title, description, reference number).

**Future**: SQL Server Full-Text Search indexes for Arabic and English content, exposed via a dedicated search endpoint.

---

## 5. Error Handling

### 5.1 Standard Error Response

**Decision**: Consistent error response structure across all API endpoints.

```json
{
  "timestamp": "2026-03-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "requestId": "req-abc-123",
  "details": [
    {
      "field": "title",
      "message": "Title is required",
      "messageAr": "العنوان مطلوب",
      "rejectedValue": null
    },
    {
      "field": "riskLevel",
      "message": "Invalid risk level",
      "messageAr": "مستوى المخاطر غير صالح",
      "rejectedValue": "INVALID"
    }
  ]
}
```

### 5.2 HTTP Status Code Usage

| Status | Usage |
|---|---|
| 200 | Successful read or update |
| 201 | Successful creation |
| 204 | Successful deletion |
| 400 | Validation errors, malformed request |
| 401 | Not authenticated |
| 403 | Authenticated but not authorized |
| 404 | Resource not found |
| 409 | Conflict (optimistic locking violation, duplicate) |
| 422 | Business rule violation |
| 429 | Rate limit exceeded (future) |
| 500 | Unexpected server error |

### 5.3 Global Exception Handler

```java
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Map field errors to localized error details
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(ErrorResponse.notFound(ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(409).body(ErrorResponse.conflict(
            "Record was modified by another user. Please refresh and try again."));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(422).body(ErrorResponse.businessRule(ex.getMessageKey()));
    }
}
```

---

## 6. API Documentation

### 6.1 SpringDoc OpenAPI

**Decision**: SpringDoc OpenAPI 3.0 with Swagger UI for interactive API documentation.

**Rationale**: SpringDoc is the actively maintained successor to SpringFox, generates OpenAPI 3.0 specs from code annotations, and provides an interactive Swagger UI for testing APIs. It is zero-configuration for standard Spring MVC controllers and supports custom schema documentation.

**Configuration**:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tssOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("TSS GRC Platform API")
                .description("REST API for the TSS Governance, Risk, and Compliance platform")
                .version("1.0.0")
                .contact(new Contact().name("TSS").email("support@tss.com.sa")))
            .addSecurityItem(new SecurityRequirement().addList("session"))
            .components(new Components()
                .addSecuritySchemes("session",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("TSSSESSIONID")));
    }
}
```

**Access**: Swagger UI available at `/swagger-ui.html` in development and staging environments. Disabled in production by default via Spring profile.

---

## 7. Integration Patterns

### 7.1 Synchronous Integration: REST

Used for all external system calls where an immediate response is needed.

| Integration | Protocol | Direction | Purpose |
|---|---|---|---|
| LDAP/AD | LDAP | Outbound | User authentication, attribute sync |
| SMTP | SMTP | Outbound | Email notifications |
| File storage | Filesystem/S3 | Outbound | Document storage and retrieval |

**REST client**: Spring `RestClient` (Spring Boot 3.5) for outbound HTTP calls.

```java
@Component
public class ExternalNotificationClient {

    private final RestClient restClient;

    public ExternalNotificationClient(RestClient.Builder builder,
                                       @Value("${tss.integration.webhook-timeout:5s}") Duration timeout) {
        this.restClient = builder
            .baseUrl("configured-per-webhook")
            .requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()))
            .build();
    }
}
```

### 7.2 Asynchronous Integration: Spring Application Events

**Decision**: Spring `ApplicationEventPublisher` for internal async communication between modules.

**Rationale**: Spring Events provide loose coupling between modules without introducing message broker infrastructure. They run in-process, are simple to implement and test, and sufficient for Phase 1 volumes. If message broker support is needed later, event handlers can be adapted to consume from Kafka/RabbitMQ with minimal change.

```java
// Publishing module
@Service
public class RiskService {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public RiskDto create(CreateRiskCommand command) {
        Risk risk = riskRepository.save(mapToEntity(command));
        eventPublisher.publishEvent(new RiskCreatedEvent(risk.getId(), risk.getOrgId()));
        return mapToDto(risk);
    }
}

// Consuming module (e.g., notification)
@Component
public class RiskNotificationListener {

    @Async
    @EventListener
    public void onRiskCreated(RiskCreatedEvent event) {
        // Send notification to risk owner and relevant stakeholders
        notificationService.notifyRiskCreation(event.getRiskId());
    }
}
```

**Event naming convention**: `{Entity}{Action}Event` (e.g., `RiskCreatedEvent`, `AssessmentSubmittedEvent`, `ControlUpdatedEvent`).

---

## 8. Data Import APIs

### 8.1 Bulk Import

**Decision**: CSV and Excel (XLSX) import via file upload endpoints with validation and error reporting.

**Rationale**: GRC implementations require bulk data migration from existing tools (spreadsheets, legacy systems). CSV/Excel are universal formats that non-technical users can prepare. Import with validation and detailed error reporting reduces implementation effort.

**Import flow**:

```
1. User uploads CSV/Excel file
2. Server parses and validates all rows
3. Validation report returned (errors by row/column)
4. If no critical errors, user confirms import
5. Server processes rows in batches (100 per batch)
6. Import result summary returned (created/updated/failed counts)
```

**API endpoint**:

```java
@PostMapping("/api/v1/erm/risks/import")
@PreAuthorize("hasPermission('erm:risk:import')")
public ResponseEntity<ImportResult> importRisks(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean validateOnly) {
    ImportResult result = riskImportService.importFromFile(file, validateOnly);
    return ResponseEntity.ok(result);
}
```

**Import result format**:

```json
{
  "totalRows": 150,
  "created": 142,
  "updated": 3,
  "failed": 5,
  "errors": [
    { "row": 12, "column": "riskLevel", "message": "Invalid value: CRITICAL. Expected: LOW, MEDIUM, HIGH, VERY_HIGH" },
    { "row": 45, "column": "owner", "message": "User 'jsmith' not found" }
  ]
}
```

### 8.2 Supported Import Formats

| Module | Entity | Supported Formats | Key Fields |
|---|---|---|---|
| ERM | Risks | CSV, XLSX | Title, description, category, level, owner, BU |
| ERM | Controls | CSV, XLSX | Title, type, category, status, owner |
| Compliance | Assets | CSV, XLSX | Name, type, classification, owner, BU |
| Admin | Users | CSV, XLSX | Username, email, name, role, BU |

---

## 9. Data Export APIs

### 9.1 Export Endpoints

```java
@GetMapping("/api/v1/erm/risks/export")
@PreAuthorize("hasPermission('erm:risk:export')")
public void exportRisks(
        @RequestParam(defaultValue = "xlsx") String format,
        @RequestParam(required = false) String status,
        HttpServletResponse response) {
    List<RiskDto> risks = riskService.findAllForExport(status);
    exportService.export(risks, format, response);
}
```

### 9.2 Supported Export Formats

| Format | Library | Use Case |
|---|---|---|
| PDF | Apache PDFBox + OpenHTMLToPDF | Formal reports, board presentations |
| Excel (XLSX) | Apache POI | Data analysis, bulk editing |
| Word (DOCX) | Apache POI (XWPF) | Policy documents, formal submissions |
| CSV | OpenCSV | Data exchange, simple export |

**Decision**: OpenHTMLToPDF for PDF generation from Thymeleaf templates.

**Rationale**: Reports are already designed as Thymeleaf templates for web display. OpenHTMLToPDF renders the same HTML/CSS to PDF, ensuring visual consistency between web and PDF output. It supports Arabic text rendering with embedded fonts, which is critical for bilingual reports.

### 9.3 Report Generation Architecture

```
Report request
  → Thymeleaf template engine renders HTML
    → For web: return HTML response
    → For PDF: pipe HTML through OpenHTMLToPDF
    → For Excel: map data to Apache POI workbook
    → For Word: map data to Apache POI XWPF document
```

**Async generation for large reports**:

```java
@Async
public CompletableFuture<String> generateLargeReport(ReportRequest request) {
    // Generate report, store to file system
    String filePath = reportGenerator.generate(request);
    // Notify user via notification system
    notificationService.notifyReportReady(request.getUserId(), filePath);
    return CompletableFuture.completedFuture(filePath);
}
```

---

## 10. Webhook Readiness

### 10.1 Outbound Webhooks (Phase 2)

**Design**: Event-driven outbound HTTP calls to external systems when specific events occur in TSS.

**Configurable per organization**:
- Webhook URL
- Events to subscribe to (e.g., `risk.created`, `assessment.completed`)
- Authentication (API key header, Basic auth)
- Retry policy (3 retries with exponential backoff)

**Webhook payload**:

```json
{
  "event": "risk.created",
  "timestamp": "2026-03-15T10:30:00Z",
  "data": {
    "id": 42,
    "title": "New cybersecurity risk identified",
    "riskLevel": "HIGH",
    "module": "erm"
  },
  "webhookId": "wh-abc-123"
}
```

### 10.2 External System Integration Points

| System | Integration Type | Phase | Purpose |
|---|---|---|---|
| LDAP/AD | LDAP protocol | Phase 1 | Authentication, user sync |
| SMTP server | SMTP | Phase 1 | Email notifications |
| File system / S3 | Filesystem / AWS SDK | Phase 1 | Document and attachment storage |
| SIEM | Syslog / REST webhook | Phase 2 | Security event forwarding |
| ServiceNow | REST API | Phase 3 | Incident/change management integration |
| SAP GRC | REST API | Phase 3 | Risk and control synchronization |

---

## 11. Rate Limiting Readiness

### 11.1 Design

**Phase 1**: No rate limiting (internal users only, session-based authentication).

**Phase 2** (when external API consumers are onboarded):

```java
@Bean
public RateLimiterRegistry rateLimiterRegistry() {
    RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(100)           // 100 requests
        .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
        .timeoutDuration(Duration.ZERO)  // reject immediately when exceeded
        .build();
    return RateLimiterRegistry.of(config);
}
```

**Strategy**: Configurable per endpoint category:

| Endpoint Category | Rate Limit | Rationale |
|---|---|---|
| Read endpoints | 100/min | Standard read operations |
| Write endpoints | 30/min | Prevent bulk mutation abuse |
| Import endpoints | 5/min | Resource-intensive operations |
| Export endpoints | 10/min | Resource-intensive operations |
| Authentication | 10/min per IP | Brute-force protection |

---

## 12. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| API versioning | URL-based (/api/v1/) | Header-based, query param | Most explicit, debuggable, easy to route |
| Pagination | Offset-based | Cursor-based | Simpler, sufficient for GRC data volumes |
| Filtering | Specification pattern | RSQL, GraphQL | Type-safe, Spring Data native, no extra dependencies |
| Async integration | Spring Events | Kafka, RabbitMQ | No extra infrastructure; sufficient for Phase 1 |
| PDF generation | OpenHTMLToPDF | JasperReports, iText | Reuses Thymeleaf templates, supports Arabic, simpler |
| Excel generation | Apache POI | JExcel | Industry standard, actively maintained, full XLSX support |
| API documentation | SpringDoc OpenAPI 3.0 | SpringFox, manual docs | Actively maintained, OpenAPI 3.0, auto-generates from code |
| REST client | Spring RestClient | RestTemplate, WebClient | Modern, synchronous, Spring Boot 3.5 native |
| Error response format | Structured JSON with bilingual messages | RFC 7807 Problem Details | Bilingual message support needed; custom format clearer |
