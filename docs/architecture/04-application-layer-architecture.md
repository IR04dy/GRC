# 04 - Application Layer Architecture

## Purpose

This document defines the application layer patterns, conventions, and implementation standards for the TSS GRC platform. It covers the controller, service, repository, DTO, validation, error handling, pagination, filtering, and mapping patterns that every module must follow.

---

## 1. Controller Pattern

### REST-Only Architecture

The TSS backend exposes **only REST controllers** (`@RestController`). There are no Thymeleaf page controllers. All HTML rendering is handled by the standalone `tss-grc-ui/` frontend project (see [20-frontend-architecture.md](20-frontend-architecture.md)).

Every module feature exposes a single REST controller under `/api/v1/{module}/`:

```java
@RestController
@RequestMapping("/api/v1/risk/risks")
@RequiredArgsConstructor
@Tag(name = "Risk Management", description = "Enterprise Risk Management API")
public class RiskController {

    private final RiskService riskService;

    @GetMapping
    @PreAuthorize("hasPermission('RISK', 'VIEW')")
    @Operation(summary = "List risks with filters and pagination")
    public Page<RiskSummaryResponse> listRisks(
            @Valid RiskFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return riskService.findAll(filter, pageable);
    }

    @PostMapping
    @PreAuthorize("hasPermission('RISK', 'CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new risk")
    public RiskDetailResponse createRisk(@Valid @RequestBody RiskCreateRequest request) {
        return riskService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'VIEW')")
    public RiskDetailResponse getRisk(@PathVariable Long id) {
        return riskService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'UPDATE')")
    public RiskDetailResponse updateRisk(@PathVariable Long id,
                                          @Valid @RequestBody RiskUpdateRequest request) {
        return riskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRisk(@PathVariable Long id) {
        riskService.delete(id);
    }
}
```

### Controller Naming Conventions

| Type | Pattern | Example |
|---|---|---|
| Page controller | `{Entity}PageController` | `RiskPageController` |
| REST controller | `{Entity}ApiController` | `RiskApiController` |
| Page URL path | `/{module}/{entity-plural}` | `/erm/risks` |
| API URL path | `/api/v1/{module}/{entity-plural}` | `/api/v1/erm/risks` |

### Controller Rules

1. Controllers must not contain business logic. All logic resides in the service layer.
2. Controllers receive validated DTOs and pass them to services.
3. Page controllers return view names (strings). REST controllers return DTOs.
4. Page controllers use `@ModelAttribute` for form binding. REST controllers use `@RequestBody`.
5. Both controller types apply the same `@PreAuthorize` security checks.
6. Page controllers use redirect-after-POST to prevent double submission.

---

## 2. Service Pattern

### Application Services

Application services are `@Service`-annotated classes that orchestrate use cases. Each service method represents a single use case with a clear transaction boundary.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskMapper riskMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final OrganizationQueryService organizationQueryService;
    private final WorkflowService workflowService;

    public Page<RiskSummaryResponse> findAll(RiskFilterRequest filter, Pageable pageable) {
        Specification<Risk> spec = RiskSpecifications.fromFilter(filter);
        return riskRepository.findAll(spec, pageable)
                .map(riskMapper::toSummaryResponse);
    }

    public RiskDetailResponse findById(Long id) {
        Risk risk = riskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Risk", id));
        BusinessUnitResponse bu = organizationQueryService
                .getBusinessUnit(risk.getBusinessUnitId());
        return riskMapper.toDetailResponse(risk, bu);
    }

    @Transactional
    public RiskDetailResponse create(RiskCreateRequest request) {
        Risk risk = riskMapper.toEntity(request);
        risk = riskRepository.save(risk);

        workflowService.startWorkflow("risk-review", "Risk", risk.getId());

        eventPublisher.publishEvent(new RiskCreatedEvent(this,
                risk.getId(),
                risk.getRiskType(),
                risk.getBusinessUnitId(),
                risk.getSeverityLevel()));

        return riskMapper.toDetailResponse(risk);
    }

    @Transactional
    public RiskDetailResponse update(Long id, RiskUpdateRequest request) {
        Risk risk = riskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Risk", id));
        riskMapper.updateEntity(request, risk);
        risk = riskRepository.save(risk);
        return riskMapper.toDetailResponse(risk);
    }

    @Transactional
    public void delete(Long id) {
        Risk risk = riskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Risk", id));
        risk.markAsDeleted();  // Soft delete
        riskRepository.save(risk);
    }
}
```

### Service Conventions

| Convention | Rule |
|---|---|
| Class annotation | `@Service` + `@RequiredArgsConstructor` |
| Default transaction | `@Transactional(readOnly = true)` at class level |
| Write methods | `@Transactional` (read-write) at method level overrides class default |
| Naming | `{Entity}Service` (e.g., `RiskService`, `IncidentService`) |
| Return types | DTOs for queries, DTOs for commands (return the created/updated state) |
| Exceptions | Throw typed business exceptions, never generic `RuntimeException` |
| Events | Publish after the mutation, within the transaction boundary |
| Soft delete | Use `markAsDeleted()` pattern, never hard delete business entities |

### Service Rules

1. Services are stateless. No instance variables that hold request-scoped state.
2. Services never expose domain entities. Always map to DTOs.
3. Services own the transaction boundary. Repositories do not declare their own transactions.
4. Cross-module calls go through the other module's service, never its repository.
5. A single service method should handle a single use case. Avoid "god methods" that do multiple unrelated things.

---

## 3. Repository Pattern

### Spring Data JPA Repositories

TSS uses Spring Data JPA for data access. Each aggregate root has a repository interface extending `JpaRepository` and `JpaSpecificationExecutor`.

```java
@Repository
public interface RiskRepository extends JpaRepository<Risk, Long>,
                                         JpaSpecificationExecutor<Risk> {

    Optional<Risk> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT r FROM Risk r WHERE r.businessUnitId = :buId AND r.status = :status")
    List<Risk> findByBusinessUnitAndStatus(@Param("buId") Long businessUnitId,
                                           @Param("status") RiskStatus status);

    @Query("SELECT r.status, COUNT(r) FROM Risk r WHERE r.tenantId = :tenantId GROUP BY r.status")
    List<Object[]> countByStatus(@Param("tenantId") Long tenantId);

    boolean existsByRiskCodeAndTenantId(String riskCode, Long tenantId);
}
```

### Custom Repository Implementations

For complex queries that exceed Spring Data's method naming, use custom repository implementations.

```java
// Custom interface
public interface RiskRepositoryCustom {
    List<RiskHeatmapData> getHeatmapData(Long tenantId, Long businessUnitId);
}

// Implementation
@RequiredArgsConstructor
public class RiskRepositoryCustomImpl implements RiskRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<RiskHeatmapData> getHeatmapData(Long tenantId, Long businessUnitId) {
        // Native SQL or Criteria API for complex aggregation
    }
}

// Repository extends both
@Repository
public interface RiskRepository extends JpaRepository<Risk, Long>,
                                         JpaSpecificationExecutor<Risk>,
                                         RiskRepositoryCustom {
}
```

### Repository Naming Conventions

| Type | Pattern | Example |
|---|---|---|
| Standard repository | `{Entity}Repository` | `RiskRepository` |
| Custom interface | `{Entity}RepositoryCustom` | `RiskRepositoryCustom` |
| Custom implementation | `{Entity}RepositoryCustomImpl` | `RiskRepositoryCustomImpl` |

### Repository Rules

1. Repositories exist only for aggregate roots, not for every entity.
2. Repositories must not contain business logic. They execute queries only.
3. Repositories must not be injected into controllers. Only services use repositories.
4. Tenant filtering is applied automatically via Hibernate filters, but explicit tenant-scoped queries exist as a safety net.
5. Soft-deleted entities are excluded by default via a `@Where` clause on the entity.

---

## 4. DTO Pattern

### Request/Response Separation

TSS uses separate DTOs for inbound requests and outbound responses. Entities are never exposed directly through controllers or APIs.

### Request DTOs

```java
public record RiskCreateRequest(
    @NotBlank(message = "{risk.title.required}")
    @Size(max = 500, message = "{risk.title.maxLength}")
    String titleEn,

    @Size(max = 500)
    String titleAr,

    @NotBlank(message = "{risk.description.required}")
    @Size(max = 4000)
    String descriptionEn,

    @Size(max = 4000)
    String descriptionAr,

    @NotNull(message = "{risk.businessUnit.required}")
    Long businessUnitId,

    @NotNull(message = "{risk.category.required}")
    Long riskCategoryId,

    @NotNull(message = "{risk.likelihood.required}")
    @Min(1) @Max(5)
    Integer likelihoodRating,

    @NotNull(message = "{risk.impact.required}")
    @Min(1) @Max(5)
    Integer impactRating,

    Long riskOwnerId
) {}
```

### Response DTOs

```java
public record RiskSummaryResponse(
    Long id,
    String title,        // Localized: titleEn or titleAr based on context
    String riskCode,
    String status,
    String businessUnitName,
    String categoryName,
    Integer likelihoodRating,
    Integer impactRating,
    Integer riskScore,
    String severityLevel,
    String ownerName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

public record RiskDetailResponse(
    Long id,
    String titleEn,
    String titleAr,
    String descriptionEn,
    String descriptionAr,
    String riskCode,
    String status,
    Long businessUnitId,
    String businessUnitNameEn,
    String businessUnitNameAr,
    Long riskCategoryId,
    String categoryNameEn,
    String categoryNameAr,
    Integer likelihoodRating,
    Integer impactRating,
    Integer riskScore,
    String severityLevel,
    Long riskOwnerId,
    String ownerName,
    List<AttachmentResponse> attachments,
    List<AuditLogResponse> recentChanges,
    WorkflowStatusResponse workflowStatus,
    LocalDateTime createdAt,
    String createdByName,
    LocalDateTime updatedAt,
    String updatedByName
) {}
```

### DTO Conventions

| Convention | Rule |
|---|---|
| Implementation | Java `record` for immutable DTOs |
| Request naming | `{Entity}{Action}Request` (e.g., `RiskCreateRequest`, `RiskUpdateRequest`) |
| Response naming | `{Entity}{View}Response` (e.g., `RiskSummaryResponse`, `RiskDetailResponse`) |
| Bilingual fields (request) | Separate `fieldEn` and `fieldAr` properties |
| Bilingual fields (response) | Summary: single localized `field`. Detail: both `fieldEn` and `fieldAr` |
| Validation | Jakarta Bean Validation annotations on request DTOs |
| Nullability | Use `@NotNull` for required fields; optional fields are nullable |
| Collections | Never return `null` for collections; use empty list |

### DTO Rules

1. Request DTOs carry validation annotations. Response DTOs do not.
2. DTOs must not contain business logic or domain behavior.
3. Entities must never appear in controller method signatures (parameters or return types).
4. Nested DTOs are acceptable (e.g., `RiskDetailResponse` containing `List<AttachmentResponse>`).
5. Filter/search DTOs are separate from create/update DTOs.

---

## 5. Validation

### Jakarta Bean Validation

Primary validation uses Jakarta Bean Validation annotations on request DTOs. The framework validates automatically via `@Valid` on controller method parameters.

### Standard Validators

| Annotation | Usage |
|---|---|
| `@NotBlank` | Required string fields (not null, not empty, not whitespace) |
| `@NotNull` | Required non-string fields |
| `@Size(min, max)` | String length and collection size constraints |
| `@Min`, `@Max` | Numeric range constraints |
| `@Email` | Email format validation |
| `@Pattern` | Regex-based format validation |
| `@Past`, `@Future`, `@PastOrPresent` | Date/time constraints |

### Custom Validators

For business rules that span multiple fields or require database lookups:

```java
@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = UniqueRiskCodeValidator.class)
public @interface UniqueRiskCode {
    String message() default "{risk.code.unique}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class UniqueRiskCodeValidator implements ConstraintValidator<UniqueRiskCode, RiskCreateRequest> {

    private final RiskRepository riskRepository;
    private final TenantContext tenantContext;

    @Override
    public boolean isValid(RiskCreateRequest request, ConstraintValidatorContext context) {
        if (request.riskCode() == null) return true;
        return !riskRepository.existsByRiskCodeAndTenantId(
                request.riskCode(), tenantContext.getCurrentTenantId());
    }
}
```

### Validation Groups

For scenarios where different validation rules apply based on the lifecycle stage:

```java
public interface OnCreate {}
public interface OnUpdate {}
public interface OnSubmit {}

public record RiskCreateRequest(
    @NotBlank(groups = {OnCreate.class, OnSubmit.class})
    String titleEn,

    @NotNull(groups = OnSubmit.class)  // Required only on workflow submission
    Long riskOwnerId
) {}
```

### Validation Error Messages

All validation messages are externalized to message bundles for bilingual support:

```properties
# messages_en.properties
risk.title.required=Risk title is required
risk.title.maxLength=Risk title must not exceed {max} characters
risk.businessUnit.required=Business unit must be selected
risk.code.unique=A risk with this code already exists

# messages_ar.properties
risk.title.required=\u0639\u0646\u0648\u0627\u0646 \u0627\u0644\u0645\u062E\u0627\u0637\u0631 \u0645\u0637\u0644\u0648\u0628
risk.title.maxLength=\u064A\u062C\u0628 \u0623\u0644\u0627 \u064A\u062A\u062C\u0627\u0648\u0632 \u0639\u0646\u0648\u0627\u0646 \u0627\u0644\u0645\u062E\u0627\u0637\u0631 {max} \u062D\u0631\u0641\u064B\u0627
risk.businessUnit.required=\u064A\u062C\u0628 \u0627\u062E\u062A\u064A\u0627\u0631 \u0648\u062D\u062F\u0629 \u0627\u0644\u0623\u0639\u0645\u0627\u0644
risk.code.unique=\u064A\u0648\u062C\u062F \u062E\u0637\u0631 \u0628\u0647\u0630\u0627 \u0627\u0644\u0631\u0645\u0632 \u0628\u0627\u0644\u0641\u0639\u0644
```

---

## 6. Error Handling

### Exception Hierarchy

```
BusinessException (abstract)
├── EntityNotFoundException           # 404 - Entity not found by ID
├── DuplicateEntityException          # 409 - Unique constraint violation
├── InvalidStateTransitionException   # 422 - Invalid workflow/status transition
├── AccessDeniedException             # 403 - Insufficient permissions
├── ValidationException               # 422 - Business rule validation failure
├── WorkflowException                 # 422 - Workflow operation failure
├── ConcurrencyException              # 409 - Optimistic lock conflict
└── ExternalServiceException          # 502 - External service failure
```

### Global Exception Handler

```java
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex, Locale locale) {
        return ResponseEntity.status(404).body(ErrorResponse.of(
                "ENTITY_NOT_FOUND",
                messageSource.getMessage("error.entity.notFound",
                        new Object[]{ex.getEntityType(), ex.getEntityId()}, locale),
                ex.getEntityType()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, Locale locale) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(422).body(ErrorResponse.of(
                "VALIDATION_FAILED",
                messageSource.getMessage("error.validation.failed", null, locale),
                fieldErrors));
    }

    @ExceptionHandler(ConcurrencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyException ex, Locale locale) {
        return ResponseEntity.status(409).body(ErrorResponse.of(
                "CONCURRENT_MODIFICATION",
                messageSource.getMessage("error.concurrent.modification", null, locale)));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, Locale locale) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(ErrorResponse.of(
                "INTERNAL_ERROR",
                messageSource.getMessage("error.internal", null, locale)));
    }
}
```

### Standardized Error Response

```java
public record ErrorResponse(
    String code,
    String message,
    List<FieldError> fieldErrors,
    String entityType,
    Instant timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of(), null, Instant.now());
    }

    public static ErrorResponse of(String code, String message, String entityType) {
        return new ErrorResponse(code, message, List.of(), entityType, Instant.now());
    }

    public static ErrorResponse of(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, fieldErrors, null, Instant.now());
    }
}

public record FieldError(String field, String message) {}
```

### Error Handling for Page Controllers

Page controllers handle errors differently -- they re-render the form with error messages instead of returning JSON:

```java
@PostMapping
public String createRisk(@Valid @ModelAttribute RiskCreateRequest request,
                         BindingResult result,
                         Model model) {
    if (result.hasErrors()) {
        model.addAttribute("severityLevels", lookupService.getSeverityLevels());
        return "erm/risk/create";  // Re-render form with errors
    }
    try {
        RiskDetailResponse created = riskService.create(request);
        return "redirect:/erm/risks/" + created.id();
    } catch (DuplicateEntityException ex) {
        result.rejectValue("riskCode", "risk.code.unique");
        return "erm/risk/create";
    }
}
```

---

## 7. Pagination

### Standard Pagination with Spring Data Pageable

All list endpoints support pagination via Spring Data's `Pageable` parameter.

### REST API Pagination

Request:
```
GET /api/v1/erm/risks?page=0&size=20&sort=createdAt,desc
```

Response:
```json
{
    "content": [ ... ],
    "totalElements": 156,
    "totalPages": 8,
    "number": 0,
    "size": 20,
    "first": true,
    "last": false,
    "sort": {
        "sorted": true,
        "orders": [{"property": "createdAt", "direction": "DESC"}]
    }
}
```

### Pagination Defaults

| Parameter | Default | Maximum |
|---|---|---|
| `page` | 0 | No limit |
| `size` | 20 | 100 |
| `sort` | `createdAt,desc` | Varies per entity |

### Pagination Configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver =
            new PageableHandlerMethodArgumentResolver();
        resolver.setMaxPageSize(100);
        resolver.setFallbackPageable(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        resolvers.add(resolver);
    }
}
```

---

## 8. Dynamic Filtering with Specification Pattern

### Specification Builder

TSS uses the JPA Specification pattern for dynamic query building. Filter criteria are encapsulated in a filter DTO and translated to specifications.

```java
public record RiskFilterRequest(
    String search,              // Free-text search on title/description
    Long businessUnitId,
    Long riskCategoryId,
    RiskStatus status,
    SeverityLevel severityLevel,
    LocalDate createdFrom,
    LocalDate createdTo,
    Long riskOwnerId
) {}

public class RiskSpecifications {

    public static Specification<Risk> fromFilter(RiskFilterRequest filter) {
        return Specification.where(hasSearch(filter.search()))
                .and(hasBusinessUnit(filter.businessUnitId()))
                .and(hasCategory(filter.riskCategoryId()))
                .and(hasStatus(filter.status()))
                .and(hasSeverity(filter.severityLevel()))
                .and(createdBetween(filter.createdFrom(), filter.createdTo()))
                .and(hasOwner(filter.riskOwnerId()));
    }

    private static Specification<Risk> hasSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("titleEn")), pattern),
                cb.like(cb.lower(root.get("titleAr")), pattern),
                cb.like(cb.lower(root.get("riskCode")), pattern));
    }

    private static Specification<Risk> hasBusinessUnit(Long businessUnitId) {
        if (businessUnitId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("businessUnitId"), businessUnitId);
    }

    private static Specification<Risk> hasStatus(RiskStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Risk> hasSeverity(SeverityLevel severityLevel) {
        if (severityLevel == null) return null;
        return (root, query, cb) -> cb.equal(root.get("severityLevel"), severityLevel);
    }

    private static Specification<Risk> hasCategory(Long categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("riskCategoryId"), categoryId);
    }

    private static Specification<Risk> createdBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"),
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay());
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
            } else {
                return cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay());
            }
        };
    }

    private static Specification<Risk> hasOwner(Long ownerId) {
        if (ownerId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("riskOwnerId"), ownerId);
    }
}
```

### Specification Conventions

| Convention | Rule |
|---|---|
| Class naming | `{Entity}Specifications` |
| Entry point | Static `fromFilter(FilterRequest)` method |
| Null handling | Each predicate method returns `null` if the filter value is absent (Spring Data ignores null specifications) |
| Text search | Case-insensitive `LIKE` on both English and Arabic text fields |
| Date range | Inclusive start, exclusive end (start-of-day semantics) |

---

## 9. Entity-DTO Mapping with MapStruct

### Mapper Interface

```java
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {LocalizationMapper.class})
public interface RiskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "riskCode", ignore = true)  // Generated by service
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "riskScore", ignore = true)  // Calculated
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Risk toEntity(RiskCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "riskCode", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(RiskUpdateRequest request, @MappingTarget Risk entity);

    @Mapping(target = "title", source = "risk", qualifiedByName = "localizedTitle")
    @Mapping(target = "businessUnitName", source = "risk", qualifiedByName = "localizedBUName")
    @Mapping(target = "categoryName", source = "risk", qualifiedByName = "localizedCategoryName")
    RiskSummaryResponse toSummaryResponse(Risk risk);

    RiskDetailResponse toDetailResponse(Risk risk);

    @Mapping(target = "businessUnitNameEn", source = "bu.nameEn")
    @Mapping(target = "businessUnitNameAr", source = "bu.nameAr")
    RiskDetailResponse toDetailResponse(Risk risk, BusinessUnitResponse bu);
}
```

### Bilingual Field Mapping

A shared `LocalizationMapper` handles locale-aware field resolution:

```java
@Mapper(componentModel = "spring")
public abstract class LocalizationMapper {

    @Autowired
    private LocaleContextHolder localeContextHolder;

    @Named("localizedTitle")
    public String localizedTitle(Risk risk) {
        return resolveLocalized(risk.getTitleEn(), risk.getTitleAr());
    }

    protected String resolveLocalized(String en, String ar) {
        Locale locale = LocaleContextHolder.getLocale();
        if ("ar".equals(locale.getLanguage()) && ar != null && !ar.isBlank()) {
            return ar;
        }
        return en;
    }
}
```

### Mapper Conventions

| Convention | Rule |
|---|---|
| Class naming | `{Entity}Mapper` |
| Component model | `spring` (injected as Spring beans) |
| Null handling | `NullValuePropertyMappingStrategy.IGNORE` for updates |
| Bilingual fields | Use `@Named` qualified methods in `LocalizationMapper` |
| Audit fields | Always `ignore = true` in `toEntity` mappings |
| ID fields | `ignore = true` in `toEntity`, preserved in `updateEntity` via `@MappingTarget` |

---

## 10. Naming Conventions Summary

### Class Naming

| Component | Pattern | Example |
|---|---|---|
| Page Controller | `{Entity}PageController` | `RiskPageController` |
| REST Controller | `{Entity}ApiController` | `RiskApiController` |
| Service | `{Entity}Service` | `RiskService` |
| Repository | `{Entity}Repository` | `RiskRepository` |
| Create Request DTO | `{Entity}CreateRequest` | `RiskCreateRequest` |
| Update Request DTO | `{Entity}UpdateRequest` | `RiskUpdateRequest` |
| Filter Request DTO | `{Entity}FilterRequest` | `RiskFilterRequest` |
| Summary Response DTO | `{Entity}SummaryResponse` | `RiskSummaryResponse` |
| Detail Response DTO | `{Entity}DetailResponse` | `RiskDetailResponse` |
| Mapper | `{Entity}Mapper` | `RiskMapper` |
| Specification | `{Entity}Specifications` | `RiskSpecifications` |
| Domain Event | `{Entity}{Action}Event` | `RiskCreatedEvent` |
| Custom Validator | `{Constraint}Validator` | `UniqueRiskCodeValidator` |
| Exception | `{Condition}Exception` | `EntityNotFoundException` |

### Method Naming

| Operation | Service Method | Controller Method | HTTP |
|---|---|---|---|
| List/search | `findAll(filter, pageable)` | `listRisks()` | `GET /risks` |
| Get by ID | `findById(id)` | `getRisk(id)` | `GET /risks/{id}` |
| Create | `create(request)` | `createRisk(request)` | `POST /risks` |
| Update | `update(id, request)` | `updateRisk(id, request)` | `PUT /risks/{id}` |
| Delete | `delete(id)` | `deleteRisk(id)` | `DELETE /risks/{id}` |
| Status change | `submit(id)`, `approve(id)` | `submitRisk(id)` | `POST /risks/{id}/submit` |
| Bulk operation | `bulkUpdate(ids, request)` | `bulkUpdateRisks(request)` | `PATCH /risks/bulk` |
