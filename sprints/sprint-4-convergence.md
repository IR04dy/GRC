# Sprint 4 — Platform Convergence Plan

**Duration:** 12 weeks (4 parallel tracks)
**Theme:** Close the gap with market leaders (ServiceNow IRM, OneTrust, MetricStream, Archer, Diligent HighBond), eliminate production blockers, and ship a real V1.

---

## Executive Summary

The platform reassessment surfaced three classes of work:

1. **Production blockers** — security, deployment, documentation drift. Small surface, high severity. *Must finish before any pilot.*
2. **Frontend convergence** — the UI has the *shape* of ServiceNow IRM but several core primitives (list engine, charts, filter builder) are toy-level. *Most user-visible leverage.*
3. **Backend completion** — 39 of 54 frontend pages are demo-only. Decide per-page: wire to a real Spring service, or delete from the router. *Eliminates the "looks finished, isn't" risk.*

A fourth continuous track covers quality (tests, docs, deps, telemetry) that runs alongside.

After 12 weeks the platform credibly competes in a ServiceNow IRM bake-off **and** carries unique Saudi/bilingual differentiation no competitor can match.

---

## Tracks at a Glance

| Track | Window | Outcome |
|---|---|---|
| **1. Production Blockers** | Week 0–1 | App is deployable; no critical security findings |
| **2. Frontend Convergence** | Week 1–8 | Functional parity with ServiceNow IRM on lists, forms, charts, search, real-time |
| **3. Backend Completion** | Week 4–12 | Every demo-only page is either wired or removed |
| **4. Quality & Hardening** | Continuous | Test coverage, type safety, deps, telemetry, bilingual gaps |

---

# Track 1 — Production Blockers (Week 0–1)

**Goal:** No critical security finding, no deployment blocker, no documentation lie. Pilot-ready in 5 working days.

## 1.1 — JWT secret hardening *(CRITICAL)*

**Why:** `application.yml:126` defaults `JWT_SECRET` to a literal string. If `JWT_SECRET` env var is unset in prod, the app silently boots with a known secret. `application-prod.yml` does not override it.

**Files:**
- `tss-grc-backend/src/main/resources/application.yml` — remove the placeholder default
- `tss-grc-backend/src/main/java/com/tss/grc/security/JwtTokenProvider.java` — add `@PostConstruct` strength check

**Tasks:**
- [ ] Remove the `${JWT_SECRET:change-this-...}` default; let Spring fail on missing property
- [ ] In `JwtTokenProvider.@PostConstruct init()`, assert `secret.length() >= 32` AND `!secret.startsWith("CHANGE_ME")` AND `!secret.contains("change-this")`
- [ ] Throw `IllegalStateException` with a clear message ("JWT_SECRET environment variable is missing or uses the placeholder value")
- [ ] Add a smoke test in `JwtTokenProviderTest` that verifies the @PostConstruct rejects a 31-char secret + the placeholder string

**Effort:** 2 hours
**Acceptance:** Backend refuses to start with missing/short/placeholder JWT_SECRET; documented in `application-prod.yml` comment.

## 1.2 — Dockerfile + GitHub Actions CI

**Why:** No Dockerfile, no `.github/workflows/`, no `Jenkinsfile`. Cannot deploy this anywhere.

**Files (new):**
- `Dockerfile` (root)
- `.dockerignore`
- `.github/workflows/ci.yml`
- `.github/workflows/build-image.yml`

**Tasks:**
- [ ] Multi-stage Dockerfile: `eclipse-temurin:17-jdk` build stage → `eclipse-temurin:17-jre-alpine` runtime stage
- [ ] Non-root user (`adduser -D -u 10001 grc`), `WORKDIR /app`, `EXPOSE 8084`
- [ ] `ENTRYPOINT exec java -jar app.jar --spring.profiles.active=${PROFILE:-prod}`
- [ ] Healthcheck: `curl -f http://localhost:8084/actuator/health || exit 1`
- [ ] `.dockerignore` excludes `.git`, `target/`, `.tools/`, `node_modules/`, `*.log`
- [ ] CI workflow on `push` + `pull_request`:
  - Backend: `mvn -B verify`
  - Frontend: `cd tss-grc-ui-v2 && npm ci && npm run build`
  - Cache `~/.m2/` and `~/.npm/`
- [ ] Image build workflow on `push` to `main`: build + push to GHCR
- [ ] Frontend Vite Docker stage with nginx for static serving (separate image or part of app jar via WebJar)

**Effort:** 1 day
**Acceptance:** `docker run -e JWT_SECRET=... -e DB_PASSWORD=... ghcr.io/tss/grc-platform:latest` boots; CI runs green on PRs.

## 1.3 — Flyway baseline (V1 + V2)

**Why:** Migrations start at V3. Dev uses Python bootstrap; prod cannot Flyway-from-zero.

**Files (new):**
- `tss-grc-backend/src/main/resources/db/migration/sqlserver/V1__core_schemas.sql`
- `tss-grc-backend/src/main/resources/db/migration/sqlserver/V2__core_seed.sql`

**Tasks:**
- [ ] Reverse-engineer Sprint-1 schemas from existing Python scripts (`db/sqlserver/` reference DDL + `setup_*_db.py`):
  - `auth.users`, `iam.users`, `iam.roles`, `iam.permissions`, `iam.role_permissions`
  - `platform.organisations`, `platform.controls`, `platform.notifications`
  - `risk.risks`, `risk.kri_*`, `compliance.*`, `evidence.*`, etc.
- [ ] Consolidate into V1 (schemas + tables + indexes)
- [ ] V2 = seed data (3 default users + roles + 1 demo org)
- [ ] Add `flyway.baseline-on-migrate: true` and `flyway.baseline-version: 0` to `application-prod.yml`
- [ ] Verify `mvn flyway:migrate -Dspring.profiles.active=prod` against an empty SQL Server brings the DB to V19 schema
- [ ] Update `setup_*_db.py` scripts with a deprecation note pointing to Flyway for prod

**Effort:** 1.5 days (mostly schema reverse-engineering)
**Acceptance:** Empty DB → `mvn flyway:migrate` → backend boots → smoke tests pass.

## 1.4 — `EvidenceMatrixService.findAll()` tenant fix

**Why:** `tss-grc-backend/src/main/java/com/tss/grc/module/evidence/service/EvidenceMatrixService.java:59` calls `frameworkRepo.findAll()` — cross-tenant leak risk.

**Tasks:**
- [ ] Verify whether `EvidenceFramework` is platform-shared (one row per framework, used by all tenants) or tenant-owned
- [ ] If platform-shared: add `@SuppressWarnings("MultiTenantBypass")` + a code comment explaining intentional global query, plus an assertion that `framework.organisationId IS NULL` for all rows
- [ ] If tenant-owned: replace with `findByOrganisationId(TenantContext.requireOrganisationId())`
- [ ] Audit other 4 occurrences: `OrganisationController.accessible()` (already correct), and grep for any other `repo.findAll()` calls in services that touch tenant data

**Effort:** 4 hours including audit
**Acceptance:** Zero unscoped `findAll()` on tenant tables.

## 1.5 — README refresh

**Why:** README claims 10 modules (real: 25), points to legacy `tss-grc-ui/` on `:3000`/`:8080` (real: `tss-grc-ui-v2/` on `:5173`/`:8084`).

**Files:**
- `README.md` — full rewrite of intro + Quick Start
- `STATUS.md` — re-snapshot or delete the stale repo-path line

**Tasks:**
- [ ] Replace module count "10" with current 25, update the matrix
- [ ] Demote `tss-grc-ui/` to "Legacy / reference" section, make `tss-grc-ui-v2/` canonical
- [ ] Update Quick Start ports (8084 backend, 5173 Vite UI)
- [ ] Add a "Sprint 4" status block linking to this plan
- [ ] Document the 5 lifecycle modules + their REST endpoints
- [ ] Add Docker quickstart section
- [ ] Update STATUS.md "code path" line, re-snapshot completion percentages

**Effort:** 3 hours
**Acceptance:** A new dev can clone the repo and have backend + UI running in 15 minutes following only the README.

## 1.6 — Other authorization gaps *(HIGH)*

**Files:**
- `tss-grc-backend/src/main/java/com/tss/grc/module/dashboard/controller/DashboardController.java:29-68`
- `tss-grc-backend/src/main/java/com/tss/grc/module/platform/controller/OrganisationController.java:41,59`

**Tasks:**
- [ ] `DashboardController` — replace `isAuthenticated()` on 8 endpoints with `hasPermission('DASHBOARD','READ')`
- [ ] `OrganisationController.accessible()` and `switchTo()` — add explicit `@PreAuthorize("isAuthenticated()")` annotations (currently inline check); add `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` on `findAll()`
- [ ] Grep audit: any other endpoint missing `@PreAuthorize` should be flagged in a spreadsheet for product decision

**Effort:** 3 hours
**Acceptance:** ≥98% of endpoints have explicit `@PreAuthorize`.

---

# Track 2 — Frontend Convergence (Week 1–8)

**Goal:** Functional parity with ServiceNow IRM on the UI primitives that drive 80% of user time. Each item is independently shippable; later items depend on earlier ones.

## 2.1 — Replace `DataTable` with TanStack Table v8 *(SINGLE BIGGEST GAP)*

**Why:** Current `tss-grc-ui-v2/src/components/ui/DataTable.tsx` is **74 lines** with no sort, no pagination, no virtualization, no column chooser, no multi-select. Every list page suffers.

**Library choice:** `@tanstack/react-table` v8 (headless, ~14KB gz, framework-grade) + `@tanstack/react-virtual` for windowing.

**Files:**
- `tss-grc-ui-v2/src/components/ui/DataTable.tsx` — full rewrite
- `tss-grc-ui-v2/src/components/ui/data-table/` (new folder):
  - `useDataTable.ts` — hook wrapping useReactTable
  - `DataTableHeader.tsx` — sortable, resizable, sticky
  - `DataTableBody.tsx` — virtualized via react-virtual
  - `DataTablePagination.tsx`
  - `DataTableColumnChooser.tsx` — checkboxes in a popover
  - `DataTableToolbar.tsx` — slot for filters + bulk actions
  - `types.ts` — shared `ColumnDef`, `RowAction`, etc.

**Capabilities to implement:**
- [ ] Multi-column sort (Shift-click for secondary sort)
- [ ] Column resize via drag handle on header borders
- [ ] Column show/hide (chooser popover)
- [ ] Column reorder (drag-and-drop headers, optional Phase 2)
- [ ] Sticky header on scroll
- [ ] Sticky first column option (for wide tables)
- [ ] Pagination: page size 25/50/100/all, server-side or client-side
- [ ] Row virtualization for >500 rows (keep 60fps scroll)
- [ ] Multi-select via leading checkbox column + `Shift-click` range select
- [ ] Per-row action menu (Lucide `MoreVertical` → dropdown)
- [ ] Empty state slot
- [ ] Loading state (skeleton rows)
- [ ] Error state slot
- [ ] Density toggle (compact/standard/comfortable)

**API contract:**
```tsx
<DataTable
  data={vulnerabilities}
  columns={vulnColumns}
  rowKey={(v) => v.id}
  onRowClick={(v) => navigate(`/cyber/vulns/${v.id}`)}
  selection={{ enabled: true, onChange: setSelected }}
  pagination={{ pageSize: 50 }}
  virtualization={{ enabled: data.length > 500 }}
  toolbar={<><FilterBar /><BulkActions /></>}
  emptyState={<EmptyState title={t('vulns.empty')} />}
/>
```

**Migration plan:**
- [ ] Build new DataTable in `data-table/` folder, leave old in place
- [ ] Migrate 3 pilot pages: `risk/RiskRegisterPage`, `cyber/CyberRegisterPage`, `compliance/ControlsPage`
- [ ] Verify performance with 10K-row dataset (use `Array.from({length: 10_000}, ...)` synthetic)
- [ ] Migrate remaining 30+ list pages over 2 weeks; delete old DataTable
- [ ] Add Storybook stories (if Storybook gets adopted in 2.10)

**Effort:** 5 days build + 5 days migration
**Acceptance:** All list pages support multi-column sort, column chooser, pagination, multi-select; 10K rows scrolls smoothly.

## 2.2 — Chart library (Recharts)

**Why:** Hand-rolled SVG in `components/viz/` (BarChart3D, Donut, HeatMap, TimeSeries, CoverageMatrix, RiskTable) cannot do drill-down, time-series compare, or breakdown. Dashboards are the second-most-used surface in GRC.

**Library choice:** **Recharts 2.x** (React-native, declarative, ~85KB gz, well-maintained). ECharts is more powerful but 4× the bundle.

**Files:**
- `tss-grc-ui-v2/package.json` — add `"recharts": "^2.13.0"`
- `tss-grc-ui-v2/src/components/charts/` (new folder):
  - `LineChart.tsx`, `BarChart.tsx`, `PieChart.tsx`, `AreaChart.tsx`, `ScatterChart.tsx`, `ComposedChart.tsx`
  - `ChartTooltip.tsx`, `ChartLegend.tsx` — branded
  - `useChartTheme.ts` — light/dark/RTL-aware palette
- `tss-grc-ui-v2/src/lib/chartTheme.ts` — color tokens, font sizes, mirror our design system

**Tasks:**
- [ ] Adopt Recharts; build wrapper components that auto-apply theme
- [ ] RTL: charts must flip axis order on `dir="rtl"`
- [ ] Tooltip: bilingual labels via i18n
- [ ] **Drill-down hook**: `onPointClick` propagates to parent (e.g. clicking a "HIGH severity" bar navigates to filtered list)
- [ ] **Compare mode**: 2 series side-by-side (this period vs last period)
- [ ] Export: every chart has a `Download as PNG` action via `recharts-to-png` or `html-to-image`
- [ ] Migrate `viz/BarChart3D` → `charts/BarChart` on dashboards
- [ ] Migrate `viz/Donut` → `charts/PieChart`
- [ ] Keep `viz/CoverageMatrix` (custom, no Recharts equivalent)
- [ ] Performance: lazy-load Recharts via dynamic import to keep initial bundle slim

**Effort:** 4 days build + 3 days migration
**Acceptance:** 5 dashboards refactored to Recharts; click-to-drill works on at least 2 (Risk dashboard, Cyber vulnerabilities); bundle stays under 3 MB.

## 2.3 — Filter expression builder

**Why:** Current `FilterModal` is single-clause select/text/date. ServiceNow's encoded query (`status=OPEN^severity=CRITICAL^opened_at>javascript:gs.daysAgo(7)`) is iconic for a reason.

**Files:**
- `tss-grc-ui-v2/src/components/ui/filter/` (new folder):
  - `FilterBuilder.tsx` — main popover
  - `FilterRow.tsx` — single condition (field + operator + value)
  - `FilterGroup.tsx` — AND/OR group with nested FilterRow / FilterGroup
  - `useFilterState.ts` — state hook with serialization
  - `operators.ts` — operator registry per field type
  - `serialize.ts` — to/from URL query string + back-end-friendly JSON

**Operators per field type:**
| Field type | Operators |
|---|---|
| string | `contains`, `eq`, `ne`, `starts_with`, `ends_with`, `is_empty` |
| number | `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `between`, `in` |
| date | `before`, `after`, `between`, `today`, `last_n_days`, `is_overdue` |
| select | `eq`, `ne`, `in`, `not_in` |
| reference | `eq`, `ne`, `in`, `assigned_to_me`, `created_by_me` |

**Tasks:**
- [ ] Implement headless `useFilterState` hook (returns predicate function + serializable state)
- [ ] Build UI: `FilterBuilder` is a popover with "+ Add condition" / "+ Add group"
- [ ] AND/OR toggle per group
- [ ] Drag-to-reorder conditions (optional, Phase 2)
- [ ] Serialize to URL query so filters survive page refresh / sharing
- [ ] Backend integration: filter JSON sent to `?filter=base64(json)` query param; controllers translate via Spring Data Specifications
- [ ] Predefined presets per page ("My Open", "Critical & Overdue", "Pending My Approval")

**Acceptance:** A user can build "(severity = HIGH OR severity = CRITICAL) AND assigned_to = me AND sla_deadline < 7 days from now" and the URL captures the filter.

**Effort:** 4 days
**Depends on:** 2.1 (DataTable) for the filter to plug into

## 2.4 — Generic `RecordPicker`

**Why:** Every page that picks a Risk, Vendor, Control, or Asset reinvents the wheel. `UserPicker` (the only existing one) is solid — generalize it.

**Files:**
- `tss-grc-ui-v2/src/components/ui/RecordPicker.tsx` (new)
- `tss-grc-ui-v2/src/lib/recordRegistry.ts` (new) — registry mapping `entity` → `{searchFn, displayFn, iconFn}`

**Tasks:**
- [ ] Define `RecordRegistry`:
  ```ts
  registerRecord('risk',     { search: riskApi.search, display: r => r.title, icon: AlertTriangle });
  registerRecord('vendor',   { search: tprmApi.search, display: v => v.name, icon: Building });
  registerRecord('control',  { search: complianceApi.searchControls, display: c => `${c.code} — ${c.title}`, icon: Shield });
  registerRecord('asset',    { search: cyberApi.searchAssets,   display: a => a.name, icon: Server });
  registerRecord('user',     { search: iamApi.searchUsers,     display: u => u.fullName, icon: User });
  ```
- [ ] `<RecordPicker entity="risk" value={riskId} onChange={setRiskId} />` — type-ahead with debounced search, shows icon + display
- [ ] Multi-select variant: `<RecordPicker entity="control" multiple value={ids} />`
- [ ] Recent records (last 5 picked of this type, per user, in localStorage)
- [ ] Keyboard navigation (↑↓ + Enter)
- [ ] Migrate `UserPicker` to use the registry under the hood (backwards compat)

**Effort:** 3 days
**Acceptance:** Adding a new reference field on any form is a 1-line change once the entity is registered.

## 2.5 — Saved views + per-user preferences

**Why:** Without saved views, "show me all my CRITICAL open vulnerabilities" requires re-clicking every visit. The "my list" pattern is universal in GRC tools.

**Files:**
- `tss-grc-ui-v2/src/lib/savedViews.ts` (new) — CRUD against localStorage initially, `/api/platform/saved-views` later
- `tss-grc-ui-v2/src/components/ui/SavedViewSelector.tsx` (new) — dropdown in DataTable toolbar
- Backend: `tss-grc-backend/src/main/resources/db/migration/sqlserver/V20__saved_views.sql`
- Backend: `tss-grc-backend/src/main/java/com/tss/grc/module/platform/savedview/` (new package)

**Schema:**
```sql
CREATE TABLE platform.saved_views (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  organisation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  page_key NVARCHAR(100) NOT NULL,        -- 'cyber/vulns', 'risk/register'
  view_name NVARCHAR(200) NOT NULL,
  is_default BIT NOT NULL DEFAULT 0,
  is_shared BIT NOT NULL DEFAULT 0,        -- visible to team
  filter_json NVARCHAR(MAX),                -- serialized FilterBuilder state
  sort_json NVARCHAR(MAX),                  -- column sort
  visible_columns NVARCHAR(MAX),            -- comma-sep
  created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  updated_at DATETIME2,
  is_deleted SMALLINT NOT NULL DEFAULT 0
);
```

**Tasks:**
- [ ] Phase 1 (week 4): localStorage-only — same shape as the eventual API
- [ ] Phase 2 (week 6): Spring backend + adapter wiring
- [ ] UI: dropdown in DataTable toolbar with "My views", "Shared", "+ Save current as..."
- [ ] Per-page default view auto-applied on load
- [ ] Share view = visible to all users in same org
- [ ] Star/favorite a view → pinned to top of dropdown

**Effort:** 2 days frontend + 1 day backend
**Depends on:** 2.1 + 2.3
**Acceptance:** A user saves "My Critical Open" on Vulnerabilities page; closes browser; reopens; selecting it restores filter + sort + visible columns.

## 2.6 — Bulk actions framework

**Why:** Lifecycle kanban pipelines have multi-select; flat lists do not. ServiceNow's pattern (select rows → action menu floats up) is universally expected.

**Files:**
- `tss-grc-ui-v2/src/components/ui/BulkActionsBar.tsx` (new) — floating bar at bottom when >0 selected
- Integration with DataTable toolbar slot

**Tasks:**
- [ ] Floating bar slides up from bottom when selection > 0
- [ ] Shows: "N selected · {action 1} · {action 2} · ... · Clear"
- [ ] Standard actions: "Assign to...", "Set status...", "Add comment...", "Export selected", "Delete (typed confirm)"
- [ ] Per-page custom actions registered via prop:
  ```tsx
  <BulkActionsBar
    selectedIds={selected}
    actions={[
      { label: t('vulns.bulk.triage'), icon: Filter, fn: handleBulkTriage },
      { label: t('vulns.bulk.assign'), icon: User,   fn: handleBulkAssign },
    ]}
  />
  ```
- [ ] Confirmation modal for destructive bulk actions
- [ ] Progress toast for long-running batches ("Updating 47 of 200...")
- [ ] Backend: each module's controller exposes `POST /bulk/{action}` with `{ ids: [...], payload: {...} }`

**Effort:** 3 days
**Depends on:** 2.1 (selection mechanic)
**Acceptance:** On any list, select 5+ rows, floating bar appears, "Assign to..." opens RecordPicker for users, all 5 update with one network round-trip.

## 2.7 — WebSocket / SSE real-time layer

**Why:** SLA pulses, approvals, notifications, lifecycle transitions all currently `setInterval(5000)` poll. ServiceNow uses AMB; OneTrust/MetricStream use SSE. Stale data costs trust.

**Files:**
- Backend: `tss-grc-backend/src/main/java/com/tss/grc/platform/realtime/` (new package)
  - `WebSocketConfig.java` — STOMP over WebSocket, `/ws` endpoint
  - `RealtimeBroadcaster.java` — service that other services call to publish events
  - Subscribed topics: `/topic/org.{orgId}.notifications`, `/topic/org.{orgId}.entity.{type}.{id}`, `/topic/user.{userId}`
- Frontend: `tss-grc-ui-v2/src/lib/realtime.ts` (new)
  - SockJS + STOMP client wrapper
  - `subscribe(topic, handler)`, `unsubscribe(handlerId)`
  - Auto-reconnect on disconnect
  - Auth via JWT in CONNECT frame

**Backend integration points:**
- [ ] `NotificationOutbox.send()` → broadcast `/topic/user.{recipientId}` with notification payload
- [ ] Every lifecycle service `transition()` → broadcast `/topic/org.{orgId}.entity.{type}.{id}` with new state
- [ ] CCM jobs → broadcast `/topic/org.{orgId}.ccm.update`

**Frontend integration points:**
- [ ] `Topbar` notification bell auto-increments unread on new event (no poll)
- [ ] Every detail page subscribes to `entity.{type}.{id}` topic and refreshes when receiving a transition event
- [ ] `LiveSlaPulse` component drops `setInterval`, listens for SLA-tick broadcasts (server-side scheduler emits every 60s)

**Effort:** 5 days (backend STOMP + 5 frontend integrations)
**Acceptance:** Open the same record in two browser tabs; transition in tab A; tab B updates within 1s.

## 2.8 — Mobile responsiveness

**Why:** Audit pack: minimal `@media`, untested at phone breakpoints. Field auditors carry phones, not laptops, in MENA enterprise environments.

**Files:**
- `tss-grc-ui-v2/src/styles/breakpoints.css` (new) — design tokens for `sm/md/lg/xl`
- Per-page CSS adjustments

**Pages to make mobile-first (Phase 1):**
- [ ] `intake/IntakeFormPage` — public submitter often on phone
- [ ] `approvals/ApprovalInboxPage` — approval-on-the-go
- [ ] `notification-outbox/NotificationOutboxPage`
- [ ] `dashboard/DashboardPage` — KPI tiles stack vertically
- [ ] `portal/VendorPortalPage` — external vendors on mobile
- [ ] `comms/*Page` — sometimes used during incident response from a phone
- [ ] All 5 lifecycle pages — kanban becomes vertical stack on mobile

**Tasks per page:**
- [ ] Sidebar → drawer (hamburger) below 768px
- [ ] Tables → cards on mobile (each row collapses to a stacked card)
- [ ] Forms → single-column always on mobile
- [ ] Tap targets minimum 44×44px (Apple HIG)
- [ ] Toast/modal/drawer fill width on mobile

**Effort:** 5 days
**Acceptance:** Lighthouse mobile score ≥ 80 on the 7 priority pages; manual test on iPhone 13 + Galaxy S22 simulators in Chrome DevTools.

## 2.9 — Knowledge base + contextual help

**Why:** SN, MetricStream, OneTrust all ship with KB. Today our users have no in-app guidance.

**Files (frontend):**
- `tss-grc-ui-v2/src/pages/knowledge-base/` (new folder)
  - `KbHomePage.tsx` — search + category browse
  - `KbArticlePage.tsx` — markdown rendering
  - `KbCategoryPage.tsx`
- `tss-grc-ui-v2/src/components/help/HelpIcon.tsx` (new) — `?` icon next to field labels, opens popover with KB excerpt + "Read more" link
- `tss-grc-ui-v2/src/lib/kb.ts` — KB CRUD API

**Files (backend):**
- `tss-grc-backend/src/main/resources/db/migration/sqlserver/V21__knowledge_base.sql`
- `tss-grc-backend/src/main/java/com/tss/grc/module/knowledge/` (new package): entity, repo, service, controller
- Articles support: bilingual (titleEn/titleAr, contentEn/contentAr), categories, tags, search via SQL Server full-text

**Tasks:**
- [ ] Schema: `kb.articles`, `kb.categories`, `kb.article_categories`
- [ ] Backend CRUD with search endpoint
- [ ] Frontend: KB browse, search, article render
- [ ] HelpIcon component placed next to ~20 field labels initially (manually wired to article slugs)
- [ ] Seed 30 articles (10 EN-only baseline, then translated to AR)

**Effort:** 4 days
**Acceptance:** Users can search KB, read articles bilingually, and hover `?` icons on key form fields to get inline help.

---

# Track 3 — Backend Completion (Week 4–12)

**Goal:** Eliminate the "looks finished, isn't" risk. Every demo-only page either gets a real backend or gets removed from the router.

## 3.1 — Triage the 39 demo-only pages

The previous audit identified 39 page folders that are "demo-only" (zero backend wiring). Decide per-page:

| Verdict | Action | Pages |
|---|---|---|
| **Wire it** | Build a Spring module (V15-V19 pattern: ~1 day each) | See 3.2 below |
| **Cull it** | Remove from `App.tsx` router + delete folder | See 3.3 below |
| **Defer** | Keep as visible roadmap placeholder, mark "Coming in v2" | See 3.4 below |

**Tasks:**
- [ ] Product workshop: classify all 39 pages into the three buckets above
- [ ] Document the decisions in this plan (Appendix C)
- [ ] Begin implementation per the schedule below

## 3.2 — Wire candidates (12 pages, 1 day each = 12 days)

Pages that have real product value and should get a backend. Pattern follows V15-V19 (entity + repo + service + controller + Flyway migration + adapter wiring):

| Page | Suggested module name | State machine? |
|---|---|---|
| `approvals/` | `approvals_lc` (or shared `platform.approvals`) | DRAFT → PENDING → APPROVED/REJECTED/DELEGATED |
| `assets/` | `cmdb` | (CRUD, no workflow) |
| `audit/` | `audit_eng` (audit engagements as state machine) | PLANNING → FIELDWORK → REPORTING → FOLLOWUP → CLOSED |
| `bcm-dependency/` | extend `bcm_lc` | (CRUD) |
| `board-pack/` | `board_pack` | DRAFT → REVIEW → APPROVED → PUBLISHED |
| `capa/` | `capa` | OPEN → IN_PROGRESS → AWAITING_VERIFICATION → CLOSED |
| `crisis/` | extend `bcm_lc` (Invocation already covers this) | (link, no new module) |
| `nca-ecc/` | `nca_ecc` (annual filing) | DRAFT → PEER_REVIEW → CISO_APPROVAL → SUBMITTED |
| `notification-outbox/` | `notifications` (already V11) | (extend with full CRUD) |
| `regulatory-change/` | `regchange` | NEW → ASSESSED → IMPACT_ANALYSED → IMPLEMENTED → CLOSED |
| `ropa/` | extend `privacy_lc.ropa_activities` (already V17) | (link existing API) |
| `sso/` | extend `platform.sso_provider_config` (already V14) | (CRUD) |

**Per-page tasks (template):**
- [ ] Flyway migration `Vxx__{module}.sql` (schema + indexes)
- [ ] Java entity + repo + service (with state machine if applicable) + controller + DTO
- [ ] Mockito test for the service (state machine + happy path + 404)
- [ ] Frontend `lib/{module}Api.ts` adapter
- [ ] Frontend page refactored to call the adapter (replace mock data)
- [ ] Add to `LIFECYCLE_ADAPTERS` in `platformPersistence.ts` if applicable
- [ ] Smoke test: list/create/transition/get via REST

**Effort:** 12 days (≈1 day per page)

## 3.3 — Cull candidates (8 pages — DELETE)

Pages that don't justify backend investment yet. Remove from router, delete folder, update navigation:

| Page | Why cull |
|---|---|
| `production-readiness/` | Internal-only checklist; spreadsheet would do |
| `monitoring/` | Use Grafana/Datadog instead of building our own |
| `backup-dr/` | Operational concern, not GRC product feature |
| `ai-governance/` | Premature — wait for real EU AI Act demand |
| `process-mining/` | Out of scope; use Celonis/Disco |
| `mfa/` | MFA is an auth concern, owned by `iam` |
| `white-label/` | Defer until first paying customer asks |
| `csv-import/` | Replaced by per-module import in DataTable toolbar |

**Tasks:**
- [ ] Remove from `tss-grc-ui-v2/src/App.tsx` routes
- [ ] Remove from `Sidebar.tsx` navigation
- [ ] Delete folders + their lib files (after grep-checking nothing else imports)
- [ ] Update README module list

**Effort:** 1 day total

## 3.4 — Defer candidates (19 pages — keep as roadmap placeholders)

Pages that have product value but aren't in scope for V1. Keep visible with "Coming Soon" badge:

`audit-lifecycle` (already wired), `bcm-lifecycle` (wired), `cyber-lifecycle` (wired), `risk-lifecycle` (wired), `privacy-lifecycle` (wired), `authority`, `comms`, `control-crosswalk`, `esg`, `framework-packs`, `intake` (form runner exists; backend deferred), `lifecycle-overview`, `notifications`, `notifications-channels`, `onboarding`, `policy-templates`, `portal/*` (4 portal pages), `workflow-versions`.

**Tasks:**
- [ ] Add `<ComingSoonBadge />` component
- [ ] Show on cards/tiles in dashboard for unwired modules
- [ ] When clicked: navigate to a "Roadmap" page that lists v2/v3 features
- [ ] Strip from primary navigation; keep accessible via deep link

**Effort:** 1 day

## 3.5 — Stub modules (admin, datagov)

Currently `module/admin/` and `module/datagov/` have Javadoc claiming features that don't exist (CRUD, RBAC, branding, lookup tables, lineage).

**Tasks:**
- [ ] **Admin**: Either implement per the package-info claims (~1 week) OR demote Javadoc to "Reserved for future tenant-admin features"
- [ ] **Datagov**: Same call — implement (~2 weeks for data lineage + classification) or demote to "Reserved for v2"
- [ ] Recommended: demote both for now; add to roadmap

**Effort:** 1 hour (demote) or 1-3 weeks (implement)

---

# Track 4 — Quality & Hardening (Continuous)

**Goal:** Test coverage, type safety, dependency freshness, telemetry, bilingual gaps. Runs alongside Tracks 1-3.

## 4.1 — Test coverage on legacy modules

Currently only the 5 lifecycle services have tests (30 test methods, all green). Twenty other modules have zero coverage.

**Priority order (by blast radius if broken):**
1. `iam` — auth + RBAC; if broken, everyone is locked out or over-permissioned
2. `auth` — login flow; if broken, no one logs in
3. `compliance` — largest module (2,475 LoC, 66 endpoints)
4. `risk` — core domain (1,676 LoC)
5. `cyber` — 1,932 LoC, 53 endpoints
6. `evidence` — 1,998 LoC, 5 endpoints unprotected
7. `incident` — 9-step state machine, no tests
8. `change_mgmt` — 13-state machine, no tests
9. `workflow` — engine module
10. Others (ethics, scm, bcm, privacy, tprm, intake, reporting, dashboard, platform)

**Per-module tasks:**
- [ ] Service tests (Mockito): happy path + state-machine ALLOWED map + 404 + 409 (where applicable)
- [ ] Controller tests (`@WebMvcTest` + MockMvc): authorization, request validation, error handling
- [ ] Repository tests (`@DataJpaTest` against H2 — note SQL Server compat caveats)
- [ ] Integration test for one critical flow per module (e.g. login → create risk → transition → close)

**Coverage target:** 60% line coverage on services + controllers; 100% on state-machine logic.

**Effort:** ≈2 days per module × 20 = 40 days. Spread across the 12-week sprint at ~2 modules/week.

## 4.2 — TypeScript bug fixes

13 real type errors outside the lifecycle code (audit identified). Plus 28 unused-import noise.

**Real bugs to fix:**
- [ ] `tss-grc-ui-v2/src/pages/portal/VendorPortalPage.tsx:71,151` — missing required `title` prop on `<Card>`
- [ ] `tss-grc-ui-v2/src/pages/notification-outbox/NotificationOutboxPage.tsx:135` — `<Badge>` unsupported `title` prop
- [ ] `tss-grc-ui-v2/src/components/comms/CommsPanel.tsx:294` and `pages/comms/CommunicationsPage.tsx:39` — `CHANNEL_ICONS` map missing `IN_APP/SMS/WHATSAPP`
- [ ] `tss-grc-ui-v2/src/pages/intake/IntakeCatalogPage.tsx:27` — icon map missing 5 IntakeKind values
- [ ] `tss-grc-ui-v2/src/lib/productionMode.ts:14` — duplicate `ImportMeta.env` declaration
- [ ] `tss-grc-ui-v2/src/pages/csv-import/CsvImportPage.tsx:284` — `<Badge>` `title` prop
- [ ] `tss-grc-ui-v2/src/pages/lifecycle-overview/LifecycleOverviewPage.tsx` — 3 invalid `as const` assertions
- [ ] Other TS2322 type mismatches (8) — case-by-case

**Cleanup (cosmetic):**
- [ ] Remove 28 unused imports/locals (one PR)
- [ ] Configure ESLint `no-unused-vars` warning to fail CI to prevent regression

**Effort:** 1 day for real bugs + 0.5 day cleanup
**Acceptance:** `tsc -b && vite build` succeeds; CI runs `npm run build` on every PR

## 4.3 — Dependency updates

| Dep | Current | Action |
|---|---|---|
| Spring Boot | 3.5.0 | → 3.5.x latest patch (CVE patches) |
| Lombok | 1.18.30 | → 1.18.34+ |
| mssql-jdbc | 12.6.1.jre11 | → 12.8.x.jre11 |
| OpenPDF | 1.3.34 | Consider migration to `librepdf 2.x` (different group/artifact) |
| Caffeine | 3.1.8 | → 3.2.x |
| Vite | 5.4 | Optional: → 6 (breaking changes; defer) |
| React Router | 6.28 | Optional: → 7 (breaking changes; defer) |

**Tasks:**
- [ ] Run `mvn versions:display-dependency-updates` and review
- [ ] Bump patch versions (low risk)
- [ ] Bump Spring Boot 3.5.x patch — re-test
- [ ] Bump Lombok — re-test (Java 17 still pinned)
- [ ] Frontend: `npm outdated`, bump patches; defer majors

**Effort:** 1 day

## 4.4 — Operational telemetry

| Concern | Today | Target |
|---|---|---|
| Logging | SLF4J + Logback file appender | + JSON appender for prod (Logstash encoder) |
| Metrics | Actuator `/health`, `/info`, `/metrics` only | + Micrometer + Prometheus endpoint on `:8090` |
| Tracing | None | + OpenTelemetry agent, Jaeger/Tempo backend (optional but recommended) |
| Errors | None | + Sentry SDK (free tier covers small deployments) |

**Tasks:**
- [ ] Add `micrometer-registry-prometheus` dependency
- [ ] Configure Logback `prod` profile with JSON encoder
- [ ] Add `io.sentry:sentry-spring-boot-starter` + DSN env var
- [ ] Document the management port `:8090` separation in `application-prod.yml`
- [ ] Custom counters for: `lifecycle.transition.{module}.{from}.{to}`, `auth.login.{success|failure}`, `notification.dispatch.{channel}.{outcome}`

**Effort:** 2 days
**Acceptance:** Prometheus scrape returns counters; Sentry captures one test exception; logs in `docker logs` are JSON.

## 4.5 — Bilingual coverage gaps

Two outliers found in entity audit:

- [ ] `tss-grc-backend/src/main/java/com/tss/grc/module/reporting/entity/ReportSchedule.java` — add `nameAr`, `descriptionAr` columns + Flyway migration
- [ ] `tss-grc-backend/src/main/java/com/tss/grc/module/intake/entity/IntakeRecord.java` — confirm with product if `rejection_reason` etc. need bilingual; if yes, add `*Ar` columns

**Frontend hardcoded Arabic:**
- [ ] `tss-grc-ui-v2/src/pages/lifecycle-overview/LifecycleOverviewPage.tsx` — 27 hardcoded Arabic strings, route through i18n

**Effort:** 1 day
**Acceptance:** Zero `[؀-ۿ]+` matches in non-mock `.tsx`/`.ts` files outside `translations/`.

---

# Risk Register

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | TanStack Table migration breaks existing list pages mid-flight | Medium | High | Build alongside old DataTable; migrate page-by-page; keep old as fallback for 2 sprints |
| R2 | Recharts bundle size pushes us past 4MB | Low | Medium | Lazy-load via dynamic import; keep `viz/` for non-dashboard pages |
| R3 | WebSocket auth + multi-tenant isolation leaks data across tenants | Medium | Critical | STOMP CONNECT frame validates JWT; all topic names include `org.{orgId}.` prefix; add integration test for cross-tenant subscription rejection |
| R4 | Flyway baseline V1/V2 doesn't match actual dev DB state, causing prod schema drift | High | High | Verify via `mvn flyway:info` on a dev clone before merging V1/V2; checksum-validate against Python bootstrap output |
| R5 | Backend module wiring blocks Track 2 frontend work | Medium | Medium | Tracks are independent; frontend can mock until backend ships |
| R6 | Dependency bumps introduce breaking changes (esp. Spring Boot patches) | Low | Medium | Bump in feature branch; full test suite must pass before merge |
| R7 | Mobile responsiveness reveals deeper layout debt | Medium | Medium | Pilot 7 priority pages first; budget extra week if needed |
| R8 | Knowledge base seeding takes longer than 4 days (translation) | High | Low | Start with EN-only; AR translations can ship in v1.1 |

---

# Acceptance Criteria — Definition of Done for Sprint 4

A feature is "done" when ALL apply:

- [ ] Code merged to `main` with passing CI
- [ ] At least one test (unit or integration) covers the new behavior
- [ ] Bilingual (EN + AR) where user-visible
- [ ] `@PreAuthorize` set correctly on backend, route-level role check on frontend
- [ ] Multi-tenant scoped (no cross-tenant data leak)
- [ ] Soft-delete respected (no hard deletes)
- [ ] Activity logged via `lib/activityLog.ts` for state changes
- [ ] Documented in:
  - `README.md` if it changes the user story
  - `STATUS.md` checkbox flipped
  - JavaDoc / TSDoc on public APIs
- [ ] Manual smoke test on Chrome + Edge + Safari (latest)

The whole **sprint** is "done" when:

- [ ] All Track 1 items complete (production blockers cleared)
- [ ] DataTable + Recharts + FilterBuilder + RecordPicker + SavedViews + BulkActions live on ≥10 pages
- [ ] WebSocket layer broadcasting at least: notifications, lifecycle transitions
- [ ] Test coverage ≥ 50% on services, ≥ 80% on state machines
- [ ] CI runs on every PR (backend + frontend builds)
- [ ] Docker image published, `docker run` brings up a working stack
- [ ] Bake-off demo script runs end-to-end against ServiceNow IRM checklist

---

# Appendix A — File-Level Change Summary

## New files (backend)

```
tss-grc-backend/src/main/resources/db/migration/sqlserver/
  V1__core_schemas.sql
  V2__core_seed.sql
  V20__saved_views.sql
  V21__knowledge_base.sql
  V22__cmdb.sql
  V23__capa.sql
  V24__regchange.sql
  V25__board_pack.sql
  V26__nca_ecc.sql
  V27__audit_engagements.sql
  V28__approvals.sql
  V29__bilingual_report_schedule.sql

tss-grc-backend/src/main/java/com/tss/grc/
  platform/realtime/WebSocketConfig.java
  platform/realtime/RealtimeBroadcaster.java
  module/platform/savedview/{entity,repo,service,controller,dto}/*.java
  module/knowledge/{entity,repo,service,controller,dto}/*.java
  module/cmdb/{...}/*.java
  module/capa/{...}/*.java
  module/regchange/{...}/*.java
  module/board_pack/{...}/*.java
  module/nca_ecc/{...}/*.java
  module/audit_eng/{...}/*.java
  module/approvals/{...}/*.java

tss-grc-backend/src/test/java/com/tss/grc/
  (test class per module — 20 modules × ~5 test methods)
```

## New files (frontend)

```
tss-grc-ui-v2/src/components/ui/data-table/
  useDataTable.ts
  DataTableHeader.tsx
  DataTableBody.tsx
  DataTablePagination.tsx
  DataTableColumnChooser.tsx
  DataTableToolbar.tsx
  types.ts

tss-grc-ui-v2/src/components/ui/filter/
  FilterBuilder.tsx
  FilterRow.tsx
  FilterGroup.tsx
  useFilterState.ts
  operators.ts
  serialize.ts

tss-grc-ui-v2/src/components/ui/
  RecordPicker.tsx
  SavedViewSelector.tsx
  BulkActionsBar.tsx
  ComingSoonBadge.tsx

tss-grc-ui-v2/src/components/help/
  HelpIcon.tsx

tss-grc-ui-v2/src/components/charts/
  LineChart.tsx, BarChart.tsx, PieChart.tsx, AreaChart.tsx, ScatterChart.tsx, ComposedChart.tsx
  ChartTooltip.tsx, ChartLegend.tsx
  useChartTheme.ts

tss-grc-ui-v2/src/lib/
  realtime.ts
  recordRegistry.ts
  savedViews.ts
  chartTheme.ts
  kb.ts

tss-grc-ui-v2/src/pages/knowledge-base/
  KbHomePage.tsx, KbArticlePage.tsx, KbCategoryPage.tsx
```

## Modified files (high-impact)

```
tss-grc-backend/src/main/resources/application.yml         (JWT secret default removed)
tss-grc-backend/src/main/java/com/tss/grc/security/JwtTokenProvider.java   (@PostConstruct strength check)
tss-grc-backend/src/main/java/com/tss/grc/module/evidence/service/EvidenceMatrixService.java   (line 59 fix)
tss-grc-backend/src/main/java/com/tss/grc/module/dashboard/controller/DashboardController.java   (8 endpoints @PreAuthorize)
tss-grc-backend/src/main/java/com/tss/grc/module/platform/controller/OrganisationController.java
tss-grc-backend/pom.xml                                    (Spring Boot patch, Lombok, mssql-jdbc bumps)
tss-grc-backend/src/main/resources/application-prod.yml    (Sentry DSN, Prometheus port, structured logging)

tss-grc-ui-v2/package.json                                  (+ recharts, @tanstack/react-table, @tanstack/react-virtual, @sentry/react, @stomp/stompjs, sockjs-client)
tss-grc-ui-v2/src/components/ui/DataTable.tsx              (full rewrite)
tss-grc-ui-v2/src/components/layout/Topbar.tsx              (notifications via WebSocket)
tss-grc-ui-v2/src/App.tsx                                   (router cleanup for culled pages)
tss-grc-ui-v2/src/components/layout/Sidebar.tsx             (favorites, collapsible)

README.md, STATUS.md                                        (full refresh)

Dockerfile, .dockerignore, .github/workflows/ci.yml, .github/workflows/build-image.yml   (new)
```

---

# Appendix B — Out of Scope (Sprint 5 candidates)

The following surfaced during the reassessment but are explicitly deferred:

- Form Designer (low-code form builder like ServiceNow's)
- Workflow simulator (test runs of state machines before going live)
- Now Assist-style GenAI (summarize a record, suggest next action)
- Mobile native app (use PWA for now)
- Advanced reporting builder (pivot tables, calculated columns, multi-source)
- Service Catalog with form orchestration beyond intake
- Connect Chat-style messaging (use Teams integration instead)
- Marketplace / certified-partner integrations
- Multi-region replication / SaaS multi-tenant fabric
- Data lineage / classification (datagov module reserved)

---

# Appendix C — Demo-Page Triage Decisions

*To be completed in product workshop — placeholder for the per-page Wire/Cull/Defer decision matrix referenced in Section 3.1.*

| Page | Decision | Rationale | Owner | Target sprint |
|---|---|---|---|---|
| approvals | WIRE | Core to GRC workflow | TBD | Sprint 4 |
| assets | WIRE | CMDB foundation | TBD | Sprint 4 |
| audit | WIRE | Audit module already partial | TBD | Sprint 4 |
| audit-lifecycle | DONE | Wired in Sprint 3.5 | — | — |
| backup-dr | CULL | Operational, not GRC | — | — |
| bcm | DONE | Wired (existing) | — | — |
| bcm-dependency | WIRE | Extends bcm_lc | TBD | Sprint 4 |
| bcm-lifecycle | DONE | Wired in Sprint 3.5 | — | — |
| board-pack | WIRE | Board reporting essential | TBD | Sprint 4 |
| capa | WIRE | Universal across audit/cyber | TBD | Sprint 4 |
| comms | DEFER | Templates exist; integrations later | TBD | Sprint 5 |
| compliance | DONE | Wired (existing) | — | — |
| control-crosswalk | DEFER | Visualization-heavy; v2 | TBD | Sprint 5 |
| crisis | WIRE | Link to bcm_lc invocations | TBD | Sprint 4 |
| csv-import | CULL | Replaced by per-module import | — | — |
| cyber | DONE | Wired (existing) | — | — |
| cyber-lifecycle | DONE | Wired in Sprint 3.5 | — | — |
| esg | DEFER | Premature; wait for demand | TBD | Sprint 5 |
| ethics | DONE | Wired (existing) | — | — |
| evidence | DONE | Wired (existing) | — | — |
| framework-packs | DEFER | Catalog-style; v2 | TBD | Sprint 5 |
| ai-governance | CULL | Premature | — | — |
| backup-dr | CULL | Out of scope | — | — |
| mfa | CULL | Belongs in iam | — | — |
| monitoring | CULL | Use Grafana | — | — |
| nca-ecc | WIRE | Saudi-specific differentiator | TBD | Sprint 4 |
| notification-outbox | WIRE | Already V11; extend | TBD | Sprint 4 |
| notifications | DEFER | Channel matrix UI | TBD | Sprint 5 |
| notifications-channels | DEFER | Provider config | TBD | Sprint 5 |
| onboarding | DEFER | Checklist; v2 | TBD | Sprint 5 |
| policy-templates | DEFER | Catalog; v2 | TBD | Sprint 5 |
| portal/* | DEFER | Vendor portal — Q2 priority | TBD | Sprint 5 |
| privacy | DONE | Wired (existing) | — | — |
| privacy-lifecycle | DONE | Wired in Sprint 3.5 | — | — |
| process-mining | CULL | Out of scope | — | — |
| production-readiness | CULL | Internal checklist | — | — |
| regulatory-change | WIRE | Compliance-critical | TBD | Sprint 4 |
| reporting | DONE | Wired (existing) | — | — |
| risk | DONE | Wired (existing) | — | — |
| risk-lifecycle | DONE | Wired in Sprint 3.5 | — | — |
| ropa | WIRE | Extend privacy_lc | TBD | Sprint 4 |
| scm | DONE | Wired (existing) | — | — |
| sso | WIRE | Already V14; UI to manage | TBD | Sprint 4 |
| tprm | DONE | Wired (existing) | — | — |
| white-label | CULL | Defer to first paying customer | — | — |
| workflow | DONE | Wired (existing) | — | — |
| workflow-versions | DEFER | Workflow editor v2 | TBD | Sprint 5 |

**Counts:** WIRE = 12, CULL = 8, DEFER = 19, DONE = 15. Totals to 54 pages + 4 root.

---

# Appendix D — Estimated Effort Summary

| Track | Days | Comment |
|---|---:|---|
| Track 1 (Production Blockers) | 4 | Highest urgency |
| Track 2 (Frontend Convergence) | 36 | Single biggest investment |
| Track 3 (Backend Completion) | 14 | 12 wires + cull + defer setup |
| Track 4 (Quality & Hardening) | 44 | Continuous; spread across 12 weeks |
| **TOTAL** | **98 person-days** | ≈ 12 weeks for 1 dev, 6 weeks for 2 devs, 4 weeks for 3 devs |

Realistic team for this sprint: **3 developers** (1 backend, 1 frontend, 1 full-stack) + 0.5 FTE QA + 0.25 FTE PM = **4 people, 6 weeks calendar time**.

---

*End of Sprint 4 Plan.*
