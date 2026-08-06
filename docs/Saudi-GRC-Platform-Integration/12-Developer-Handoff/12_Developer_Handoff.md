# 12 — Developer Handoff
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


This is the single read-before-you-start document for engineers
joining the platform. The cousins (`12_Backend_Specification.md`,
`12_Frontend_Specification.md`, `12_Workflow_Engine_Specification.md`)
deep-dive each layer.

## Architecture summary

- **Style:** modular monolith. Spring Boot 3.5 / Java 17 backend,
  React 18 + Vite + TypeScript frontend, SQL Server 2019 with UTF-8
  collation.
- **Modules:** 35 under `tss-grc-backend/src/main/java/com/tss/grc/module/`,
  each with `controller/`, `dto/`, `entity/`, `repository/`,
  `service/`. See `02_Module_Architecture.md`.
- **Spine:** Framework Intersection F1-F9 cross-cuts (Authority
  Documents, Control Library, Compliance Posture, Audit Log,
  Automation Rules, Licensing). See
  `docs/grc/framework-intersection-plan.md`.

## Conventions

| Topic | Rule |
|---|---|
| Java version | 17 (pinned — Lombok incompatible with Java 25 in this repo) |
| Spring Boot | 3.5.x; use `@RestController`, `@Service`, `@Transactional` |
| JPA | Hibernate 6; entities extend `BaseEntity`; `@Where(clause = "is_deleted = 0")` |
| Multi-tenancy | every read scoped by `TenantContext.getCurrentTenant()` |
| Bilingual | every user-visible string has `*_en` + `*_ar` cols |
| Workflow | state machine in service `transition()` method; audit every move |
| Tests | Mockito (`MockitoExtension` strict mode); test class per service |
| Frontend | Vite + TypeScript + Tailwind utilities; `tsc --noEmit` must be clean |
| API base | `/api/v1/...` everywhere new; legacy `/api/...` only for older endpoints |
| Auth | JWT bearer; refresh via HttpOnly cookie; CORS strict in prod |
| Migrations | Flyway, prefix `V<n>__<snake>.sql`; never edit a shipped migration |

## Build + run

```bash
# Backend
cd tss-grc-backend
mvn spring-boot:run -Dspring.profiles.active=dev   # port 8084, Swagger at /swagger-ui.html

# Frontend
cd tss-grc-ui-v2
npm install
npm run dev                                        # Vite dev server
npx tsc --noEmit                                   # type check, must be clean

# Tests
mvn test                                           # all
mvn test -Dtest='ControlLibraryServiceTest'        # single class
```

## CI

`.github/workflows/ci.yml` — JDK 17 backend + Node 20 frontend +
Docker smoke. Triggered on push to master and PR to master.

## Branching

- `master` — production-ready.
- `feat/<feature-name>` — feature branches.
- PR squash-merges to master; commit message follows
  `<scope>: <subject>` style.

## Deployment

- **Dev:** Docker Compose with SQL Server LocalDB.
- **Prod:** containerised; SSL/TLS via PKCS12; Flyway runs migrations
  on startup with `flyway.clean-disabled=true`.

## Documentation discipline

- New module → add a section to `02_Module_Architecture.md`.
- New regulator catalog entry → seed in V9X migration + update
  `07_Regulatory_Mapping_Matrix.md`.
- New automation rule → seed row + entry in `09_Automation_Rules.md`.
- New page → entry in `05_UI_Screen_Requirements.md` +
  `05_Navigation_Menu.md`.
- New entity → entry in `04_Data_Model.md` ER diagram update +
  `04_API_Object_Model.md`.

## Common pitfalls (already-fixed, don't regress)

See `~/.claude/projects/.../memory/project_v2_lessons.md`. Highlights:

1. `JwtAuthenticationFilter` ordering + `TenantContext` setup.
2. Swagger CSP headers in `SecurityConfig`.
3. BCrypt password literal escaping in shell/Python seed scripts.
4. Java 17 pin.
5. `api.js` (frontend) must use absolute URL to backend in dev.
6. `IF NOT EXISTS` in Flyway migrations can silently skip table
   creation if a prior version had the same name with a different
   schema — caught V92↔V1 collision in F3 prep; always check column
   set when re-creating tables.

## Where to look first

- **What's done?** → `STATUS.md`.
- **Why this architecture?** → `docs/grc/framework-intersection-plan.md`.
- **What's a module's contract?** → `02_Module_Architecture.md`.
- **What's the schema?** → migrations under
  `src/main/resources/db/migration/sqlserver/`.
- **What's the API?** → Swagger UI at `/swagger-ui.html` (dev).

---

**Cousins:** `12_Backend_Specification.md`,
`12_Frontend_Specification.md`,
`12_Workflow_Engine_Specification.md`.
