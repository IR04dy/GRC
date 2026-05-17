# 12 — Backend Specification

## Stack

| Layer | Choice |
|---|---|
| Language | Java 17 (pinned) |
| Framework | Spring Boot 3.5 |
| Security | Spring Security 6 + JJWT 0.12 |
| ORM | Hibernate 6 / Spring Data JPA |
| DB | SQL Server 2019, `Latin1_General_100_CI_AS_SC_UTF8` |
| Migrations | Flyway 10.15 (non-dev profiles) |
| Caching | Caffeine L1 |
| API docs | SpringDoc OpenAPI 2.8 |
| Export | Apache POI 5.2 (Excel), OpenPDF 1.3 (RTL-aware PDF) |
| Build | Maven 3.9+ |

## Package layout

```
com.tss.grc/
├── GrcApplication.java                  @SpringBootApplication + @EnableAsync + @EnableCaching
├── config/                              SecurityConfig, CorsConfig, DataSourceConfig, CacheConfig, OpenApiConfig, LocaleConfig, WebMvcConfig
├── security/                            JwtTokenProvider, JwtAuthenticationFilter, GrcUserDetails, ModulePermissionEvaluator
├── common/
│   ├── exception/                       IllegalTransitionException, …
│   ├── model/                           BaseEntity
│   ├── tenant/                          TenantContext (thread-local)
│   └── util/
└── module/                              35 functional modules (see 02_Module_Architecture.md)
```

## Per-module layered layout

Every module follows:

```
module/<name>/
├── controller/      thin; @RestController; @PreAuthorize; map DTO ↔ entity
├── dto/             request + response shapes with Jakarta validation
├── entity/          JPA @Entity (extends BaseEntity)
├── repository/      Spring Data JPA; org-scoped finder methods
└── service/         @Service @Transactional; business logic + workflow
```

## REST conventions

- Base path `/api/v1/<resource>`.
- HTTP verbs:
  - `GET /api/v1/foo` → page
  - `GET /api/v1/foo/:id` → one
  - `POST /api/v1/foo` → create
  - `PUT /api/v1/foo/:id` → full update
  - `PATCH /api/v1/foo/:id` → partial
  - `POST /api/v1/foo/:id/transition` → workflow move
  - `DELETE /api/v1/foo/:id` → soft delete
- Errors via `ProblemDetail` (RFC 7807).
- Tenant scoping always implicit from JWT.

## Workflow state machines

Each service that runs a workflow exposes a `transition()` method
with an explicit `switch` over the current state:

```java
public Foo transition(Long id, String to) {
  Foo f = get(id);
  String from = f.getStatus();
  boolean ok = switch (from) {
    case "DRAFT"    -> "REVIEW".equals(to);
    case "REVIEW"   -> "APPROVED".equals(to) || "DRAFT".equals(to);
    case "APPROVED" -> "PUBLISHED".equals(to);
    default         -> false;
  };
  if (!ok) throw new IllegalTransitionException(from, to, Set.of());
  f.setStatus(to);
  // … domain side-effects …
  return repo.save(f);
}
```

After saving, the service publishes a `DomainEvent` for the F8
engine to consume and writes a row to `platform.audit_log` via F7.

## Permission model

`@PreAuthorize("hasPermission('MODULE','ACTION')")` resolves through
`ModulePermissionEvaluator`, which reads role permissions from
`iam.role_permissions`. Module licensing (F9) overlays a second
check via `LicenseGuard` (aspect, follow-up slice).

## Notification + audit events

- **Audit:** `AuditLogService.recordEvent(...)` writes to
  `platform.audit_log`.
- **Domain events:** `ApplicationEventPublisher.publishEvent(
  DomainEvent.of(org, "INCIDENT_CREATED", entityType, id, payload))`.
- **Notifications:** `NotificationService.send(template, recipient,
  payload)` writes to `platform.notifications`.

## Caching

- Caffeine L1, `maximumSize=1000`, `expireAfterWrite=300s`.
- Cached: license entitlements (F9), Authority Documents catalog
  reads, Compliance Posture heatmap (5-minute TTL).
- Evict on write: license patches, doc edits.

## Async

`@EnableAsync` in `GrcApplication`. Used by:

- `AutomationRuleEngine.onDomainEvent(...)` — rules fire async.
- `NotificationService.send(...)` — email/SMS async.
- `reporting.ReportRunService.run(...)` — long reports run async.

## Background jobs

- `bcm.EvidenceExpirySweep` — daily at 02:00 UTC.
- `policy.PolicyExpirySweep` — daily at 03:00 UTC.
- `approvals.EscalationScheduler` — every 60s.
- `reporting.ScheduleExecutor` — every 5 minutes.
- `risk.AcceptanceExpirySweep` — daily.

## Integration points

- **SSO/SAML/OAuth** — `module/auth/sso` (skeleton). Saudi
  Nafath/Absher are out of scope today.
- **SMTP** — JavaMailSender per-tenant config.
- **Webhook out** — `module/platform/webhooks` for Slack / Teams.

## Profiles

| Profile | Use | Highlights |
|---|---|---|
| `dev` | local | Flyway OFF, JPA `ddl-auto=none`, port 8084, hard-coded `sa/tss.123`, Swagger ON |
| `prod` | production | SSL/TLS PKCS12, Flyway ON, externalised secrets, no error details, Swagger OFF |
| `ci` | CI | reuses `dev` minus the real DB; Mockito tests only |

## Testing

- Unit tests: Mockito (`MockitoExtension`).
- Service-layer state-machine tests are mandatory for every
  workflow-bearing service (see `*ServiceTest`).
- Integration tests (Testcontainers + real SQL Server) live under
  `src/test/java/.../integration/` for the heaviest paths.

---

**See also:** `12_Developer_Handoff.md`,
`12_Workflow_Engine_Specification.md`.
