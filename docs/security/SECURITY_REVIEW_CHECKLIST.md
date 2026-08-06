# Security Review Checklist
> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


A condensed checklist code reviewers run against any PR that adds or modifies a write endpoint. Drawn from the patterns found during the May 2026 bug-hunt arc (PRs #117–#128). Most items are 30-second checks.

## When to use this

Run through it whenever a PR touches:

- A new `@RestController` with `@PostMapping`, `@PutMapping`, `@PatchMapping`, or `@DeleteMapping`
- A new `@Service` method that calls `repo.save(...)` / `saveAll(...)`
- A new JPA entity or a change to an existing one's column set
- An authentication / authorization config (`SecurityConfig`, `JwtTokenProvider`, `application*.yml` JWT/CORS/cookies)

## Backend

### Mass-assignment defense

- [ ] **Does the controller accept a JPA entity directly via `@RequestBody`?** (e.g. `public Foo create(@RequestBody Foo req)`)
  - If yes → the service's create-style method MUST call `req.setId(null)` before `repo.save(req)`. See `docs/security/MASS_ASSIGNMENT_DEFENSE.md`.
  - If no (accepts a DTO/Request type) → verify the entity is constructed fresh via `new EntityName()` inside the service, with fields copied from the DTO. This pattern is structurally immune.
- [ ] **Does any update-style method copy `req.organisationId` onto an existing managed entity?**
  - If yes → fix to copy from `existing.organisationId` instead. The request body's `organisationId` is attacker-controlled.

### Tenant scoping

- [ ] **Does every repository query include `organisationId` in its predicate?** Either via:
  - A named finder: `findByOrganisationId(...)` / `findByOrganisationIdAnd<X>(...)`
  - A custom `@Query` with `WHERE organisation_id = :orgId`
  - `findByIdAndOrganisationId(...)` for single-row lookups
- [ ] **Avoid `repo.findAll()` in tenant-scoped services.** Use `findByOrganisationId(TenantContext.getCurrentTenant())` instead. The only legitimate `findAll()` callsites are platform-shared catalogs (Sprint-4 T1.4 audit) and cross-tenant scheduled sweeps.
- [ ] **Service writes set `entity.organisationId = TenantContext.getCurrentTenant()` BEFORE save.**

### Transaction + error handling

- [ ] **Write methods are `@Transactional`** (or the class has the annotation at type level).
- [ ] **Read methods that catch + swallow exceptions** (returning `Collections.emptyList()` for missing-table cases) carry `noRollbackFor = RuntimeException.class` on their `@Transactional(readOnly = true)`. Without this, the swallowed exception still triggers `UnexpectedRollbackException` at commit time (PR #117 root cause).
- [ ] **Controller methods don't leak stack traces.** Errors map through `GlobalExceptionHandler` to RFC-7807 ProblemDetail responses.

### Authorization

- [ ] **Every controller method has `@PreAuthorize(...)`** or the class has one at type level.
  - Read endpoints: `hasPermission('MODULE', 'READ')` or `hasAuthority('ROLE_X')`
  - Write endpoints: `hasPermission('MODULE', 'WRITE')` or stricter
  - `isAuthenticated()` is acceptable only for personal-scope endpoints (`/my-tasks`, etc) and login endpoints
- [ ] **The `@PreAuthorize` SpEL expression doesn't reference attacker-controlled input.** E.g. `hasPermission(#req.foo, 'WRITE')` — if `req.foo` is in the request body, the attacker chooses what they're checking against.

### Date/time

- [ ] **`LocalDate.now()` / `LocalDateTime.now()`** depends on the JVM default timezone, which is pinned to `Asia/Riyadh` in `Dockerfile` + `GrcApplication.main()` (PR #121).
- [ ] **`Instant.now()` is always UTC** — safe to use directly; Jackson serializes per `spring.jackson.time-zone: UTC`.

### Secrets

- [ ] **No literals in `application*.yml`** for secrets (JWT_SECRET, DB_PASSWORD, SMTP_PASSWORD). Must be `${ENV_VAR}`. Dev placeholder strings must be obviously placeholder (e.g. `CHANGE_ME_…`) and rejected by `JwtTokenProvider.validateSecret()`.

## Frontend

### Authentication

- [ ] **Authenticated `fetch` reads the token from `sessionStorage.getItem('grc.accessToken')`** — never just `'accessToken'` (PR #120 bug).
- [ ] **API base URL** uses the resolution chain in `lib/api.ts`: `VITE_API_BASE` env → `window.__GRC_API__` runtime override → empty string (same-origin). No hardcoded `http://localhost:8084` fallbacks (PR #120).

### Cross-site script / open redirects

- [ ] **`dangerouslySetInnerHTML`** is only used after the source is either (a) HTML-escaped via the same pass used in `KbArticlePage.tsx`, AND (b) URL-scheme-validated via `sanitiseUrl()` for any embedded links (PR #120).
- [ ] **`window.location.href` / `window.open`** is only assigned a relative URL or a value from a trusted source (server-generated reference codes are fine; attacker-controlled URL params are not).

### JSON.parse safety

- [ ] **`JSON.parse(localStorage.getItem('...'))`** is wrapped in `try/catch`. Corrupt localStorage shouldn't crash the page.

## CSS / accessibility

- [ ] **New interactive surfaces have `:focus-visible`** ring matching the platform pattern (`outline: 2px solid var(--primary); outline-offset: 2px`).
- [ ] **Bilingual UI** — every user-visible string uses `t('key')` or a `lang === 'ar' ? '…' : '…'` ternary. No English-only labels on workflows users will run in Arabic.

## Deployment

- [ ] **New backend services that walk all tenants** (scheduled sweeps) wrap their per-tenant loop body in `try (var scope = TenantContext.allowCrossTenantWrite()) { … }` if they update any entity's `organisationId`. (See `docs/security/MASS_ASSIGNMENT_DEFENSE.md`.)
- [ ] **New Spring beans that depend on the Hibernate session** are registered before `EntityManagerFactoryBean` initialises — typically via `HibernatePropertiesCustomizer`, not raw `application.yml`.

## Reference

- Mass-assignment defense pattern: `docs/security/MASS_ASSIGNMENT_DEFENSE.md`
- JWT secret hardening: `JwtTokenProvider.validateSecret()` (Sprint-4 T1.1)
- Tenant scoping: `TenantContext` (with the Sprint-6 cross-tenant write guard scaffolding)
- Audit log + tenant write guard: `TenantWriteGuardInterceptor` (PR #125), `TenantWriteGuardConfig` (PR #126)
