# TSS GRC Platform — Remediation Plan

**Source:** [`reports/audit-report.html`](audit-report.html) — 7-phase audit at commit `3ca91bf` (2026-06-18).
**Scope:** all **287 findings** — 4 Critical · 52 High · 114 Medium · 93 Low · 24 Info (every Critical/High adversarially verified).
**Goal of this plan:** fix **root causes, not symptoms** — most findings collapse into a small number of systemic patterns, so a handful of cross-cutting changes clears dozens of findings at once.

> This plan groups the 287 findings into **16 workstreams** and sequences them across **4 priority waves (P0–P3)**. Per-finding detail (evidence, line numbers, before/after patches) lives in the HTML report; this document is the execution roadmap.

---

## 1. Guiding principles

1. **Root-cause first.** ~67 findings (WS1+WS2) are the *same* authorization defect repeated across modules. Build the fix once (a reusable pattern + a guard test) and apply it everywhere.
2. **No regressions.** The backend already has 1,858 passing tests. Every change must keep them green and add a test that would have caught the finding.
3. **Verify against the running stack.** Re-run the exact audit harnesses in `audit/` (Trivy, npm audit, k6, Playwright+axe, JaCoCo) after each wave — fixes are "done" only when the tool that found the issue stops reporting it.
4. **Branch + PR per workstream.** Small, reviewable PRs scoped to one workstream; never mix security and cosmetic changes.
5. **Production-config parity.** Several findings are dev-only (dev CORS, dev JWT secret, pre-filled creds). Fixes must not weaken dev ergonomics while ensuring prod profiles are hardened — gate by Spring profile.

---

## 2. Priority waves & timeline

| Wave | Theme | Workstreams | Findings | Target |
|------|-------|-------------|---------:|--------|
| **P0 — Stop the bleeding** | Cross-tenant data exposure + systemic authorization + the unsafe SQL runner | WS1, WS2, WS4 (critical subset) | ~62 | **Sprint 1 (1–2 wks)** |
| **P1 — Security & reliability hardening** | Auth/session, dependency CVEs, backend perf High, test foundation, accessibility | WS3, WS5, WS12, WS14, WS15 | ~74 | **Sprint 2–3** |
| **P2 — Quality, validation, UX** | Input validation, God-classes, duplication, error handling, frontend perf, UX/copy | WS4 (rest), WS6, WS7, WS8, WS9, WS10, WS13, WS16 | ~128 | **Sprint 4–6** |
| **P3 — Cleanup** | Dead code + residual Low/Info | WS11 + Low/Info tail | ~23 | **Ongoing / backlog** |

**Exit criteria per wave:** P0 → 0 Critical, 0 cross-tenant/IDOR High; P1 → 0 High security, 0 High/Critical CVE, contrast passes axe, coverage gate established; P2 → Medium count down ≥70%, frontend test suite live; P3 → Low/Info triaged or accepted with rationale.

---

## 3. Workstreams

Each workstream lists: **objective · representative finding IDs · root cause · the fix · acceptance criteria · verification · effort.**

### WS1 — Cross-tenant data isolation  🔴 P0
*20 findings — 2 Critical, 8 High, 6 Med, 3 Low, 1 Info*

- **Representative IDs:** `SEC-CYB-001`, `SEC-CYB-002` (Critical — IDOR read/overwrite across `CyberService`), `SEC-BCM-001` (legacy BIA path, no org column), `SEC-INT-002` (ServiceNow webhook resolves rows un-scoped), `SEC-NDI-002` (audit ledger).
- **Root cause:** repositories expose `findById(...)` / `findByProcessId(...)` and services act on the result **without checking `organisation_id`**. There is no global Hibernate tenant filter (documented in `CLAUDE.md`) — scoping is per-query and was missed in several places.
- **Fix:**
  1. Introduce a `TenantScopedRepository` base (or a shared `mustGetForTenant(id)` service helper) that always filters by `TenantContext.getOrganisationId()`; replace every bare `findById`/`save(clientId)` on tenant-owned entities.
  2. Add the missing `organisation_id` column + backfill to `BiaRecord` (and any entity lacking it) and scope `BiaRecordRepository`.
  3. For webhooks/integrations that match by external id, scope the lookup to the integration's owning org (`SEC-INT-002`).
  4. Add a `@PrePersist`/interceptor backstop that stamps `organisation_id` server-side and rejects client-supplied org ids (wire up the stubbed `TenantWriteGuardInterceptor`, `SEC-BCM-006`).
- **Before/After (canonical):**
  ```java
  // BEFORE — cross-tenant IDOR
  var row = repo.findById(id).orElseThrow();
  // AFTER
  var row = repo.findByIdAndOrganisationId(id, TenantContext.getOrganisationId())
                .orElseThrow(() -> new NotFoundException(id));
  ```
- **Acceptance:** no service reads/writes a tenant entity without an org predicate; a parameterised cross-tenant test (org A token → org B id → 404, not 200) for each fixed endpoint.
- **Verify:** new integration tests + a Playwright cross-tenant journey (WS14) using a second seeded tenant.
- **Effort:** 3×L, 9×M, 8×S.

### WS2 — Function-level authorization (BFLA)  🔴 P0
*39 findings — 2 Critical, 11 High, 20 Med, 4 Low, 2 Info*

- **Representative IDs:** `SEC-FIN-001` (Critical — unauthenticated public intake tracker), `SEC-IAM-001` (Critical — approval actor from request body), `SEC-PLAT-001/002/005/006`, `SEC-ESG-001`, `SEC-NDI-001`, `SEC-BCM-002`, `SEC-BCM-003` (SoD).
- **Root cause:** dozens of write/admin endpoints are annotated `@PreAuthorize("isAuthenticated()")` (or have **no** annotation, falling back to the chain's `anyRequest().authenticated()`) instead of `@PreAuthorize("hasPermission('MODULE','ACTION')")`. Any authenticated user (incl. AUDITOR) can mutate state — intra-tenant privilege escalation. A few endpoints take the actor identity from the request body instead of the principal.
- **Fix:**
  1. **Inventory:** script a sweep listing every `@*Mapping` and its effective authorization (the audit already counts 1,924 `@PreAuthorize`); flag every `isAuthenticated()` and every unannotated write endpoint.
  2. Replace with the correct `hasPermission('MODULE','ACTION')` (READ/WRITE/APPROVE/DELETE), using the well-built modules (Sprint-11 `climate`, `ai/models`) as the reference pattern.
  3. Enforce **segregation of duties** in the service layer using the authenticated principal (`SecurityContext`), never a body field (`SEC-IAM-001`, `SEC-ESG-002`, `SEC-BCM-003`).
  4. Lock down or authenticate the public intake tracker (`SEC-FIN-001`) — require a per-case token, not an enumerable id.
- **Acceptance:** zero `isAuthenticated()`-only write endpoints; a **meta-test** that reflects over all controllers and fails if a write mapping lacks a `hasPermission` guard.
- **Verify:** MockMvc tests *with Spring Security enabled* (current controller tests bypass it — see `FUNC-004`); negative E2E as AUDITOR (WS14).
- **Effort:** 22×S, 17×M — mostly mechanical once the pattern + meta-test exist.

### WS3 — Authentication & session hardening  🟠 P1
*20 findings — 1 High, 7 Med, 7 Low, 5 Info*

- **Representative IDs:** `SEC-AUTH-001` (no brute-force lockout/throttle), `SEC-AUTH-002` (weak password policy), `E2E-003` (access JWT in `sessionStorage` → XSS-exfiltratable), refresh-cookie flags, HMAC charset.
- **Root cause:** dev-tuned security settings and client-side token storage chosen for SPA convenience.
- **Fix:** enable account lockout/throttling in non-dev profiles; strengthen password policy (length ≥12 + complexity + breach check); move the access token to in-memory (refresh via the existing HttpOnly cookie) or accept the residual XSS risk with a documented CSP; set `secret.getBytes(UTF_8)`; verify refresh-cookie `Secure/HttpOnly/SameSite` in prod.
- **Acceptance:** lockout after N attempts (prod), policy enforced on create/change, token no longer readable from `sessionStorage` (or CSP + justification documented).
- **Verify:** auth unit tests; re-run the Playwright `session-keys` probe.
- **Effort:** 14×M, 6×S.

### WS4 — Injection & unsafe input handling  🟠 P0 (critical subset) / P2 (rest)
*23 findings — 2 High, 9 Med, 10 Low, 2 Info*

- **Representative IDs:** `SEC-INT-001` (**P0** — `CustomSqlRunner` executes tenant-supplied SQL with only a blacklist regex and no org scoping → cross-tenant exfiltration), `SEC-INT-004` (Tenable path injection), XSS (2), open-redirect (2), mass-assignment (2), CSV/XLSX formula injection (`SEC-BCM-005`).
- **Root cause:** raw SQL execution surface; raw JPA entities bound as `@RequestBody`; string-built external URLs/exports.
- **Fix:** **P0** — restrict `CustomSqlRunner` to a vetted allowlist of named, parameterised queries (or remove it); enforce per-tenant schema scoping; never accept free-form SQL. Rest — replace entity request bodies with validated DTOs (overlaps WS7); `@Pattern`-validate path vars used in outbound URLs; prefix-guard redirects (the login `next=` guard at `LoginPage.tsx:22` is the reference); neutralise CSV formula injection (`'` prefix on `=,+,-,@`).
- **Acceptance:** no `createNativeQuery` on user input; DTO + `@Valid` on all writes; redirect allowlist.
- **Verify:** targeted injection tests; re-scan.
- **Effort:** 3×L, 10×M, 10×S.

### WS5 — Dependency & supply-chain CVEs  🟠 P1
*10 findings clustered here — 1 High, 7 Med, 1 Low, 1 Info (the report has 14 dependency-CVE entries; the remainder fold into WS3/WS4)*

- **Representative IDs:** `SEC-CVE-001` (Tomcat 10.1.46 → adjusted High), `SEC-CVE-002` (Spring Security Web 6.5.5 → Med), `SEC-NPM-001` (vite 5.4.21 dev-server-only → Med), plus commons-compress/lang3, logback, jackson, mssql-jdbc, poi.
- **Root cause:** `spring-boot-starter-parent` pinned to 3.5.6; transitive deps lag.
- **Fix:** bump parent to **3.5.14** (clears most), add explicit `<dependencyManagement>` overrides for commons-compress, commons-lang3, logback, jackson where the BOM doesn't reach the fixed version; bump `mssql-jdbc`/`poi` properties; `npm update` / override `react-router`/`vite`.
- **Acceptance:** `trivy fs` reports 0 High/Critical; `npm audit` 0 High.
- **Verify:** re-run `audit/tools/trivy.exe fs` + `npm audit`; **`mvn test` must stay green** after the bump.
- **Effort:** 5×M, 5×S — low code risk, needs a regression run.

### WS6 — Secrets & configuration hardening  🟡 P2
*13 findings — 1 Med, 7 Low, 5 Info (several are intentional dev defaults — false positives)*

- **Representative IDs:** gitleaks dev-creds (`sa/tss.123`, dev JWT — **Info/Low**, already externalised in prod), `X-Forwarded-For` trust (`SEC-PLAT-009`), Python seed-script SQL build (bandit B608, dev-only).
- **Fix:** confirm prod externalises every secret (it does — verify the `application-prod.yml` contract); stop trusting client `X-Forwarded-X` for audit IP unless behind a known proxy; add a `.gitleaks.toml` baseline so intentional dev defaults don't re-alarm.
- **Acceptance:** documented prod secrets contract; gitleaks baseline committed.
- **Effort:** 12×S, 1×M.

### WS7 — Input validation & pagination  🟡 P2
*13 findings — 1 Med, 12 Low*

- **Root cause:** missing `@Valid`/Jakarta constraints on DTOs; list endpoints with no `Pageable` (unbounded result sets, e.g. `SEC-PLAT-008`, the in-memory `PageRequest.of(0,1000)` truncation in `QUAL-RC-006`).
- **Fix:** add `@Valid` + constraints; introduce a max page size; replace `findAll()`/`of(0,1000)` with proper `Pageable`.
- **Acceptance:** every write DTO validated; no unbounded list endpoint.
- **Effort:** 10×S, 3×M.

### WS8 — God-classes / SOLID / layering  🟡 P2
*27 findings — 4 High, 9 Med, 11 Low, 3 Info*

- **Representative IDs:** `QUAL-ESG-001` (`DataOfficerService` 1,054 LOC/14 repos), `QUAL-CP-001` (`CyberService` 613 LOC), `QUAL-RC-001` (two `PolicyService` classes — bean-name hack), `QUAL-RC-003` (`FrameworkAnchorController` does aggregation across 8 repos), `QUAL-BCMP` (`BuyerGapService` spans 6 modules; `BcmController` binds entities).
- **Fix:** split god services per aggregate (the module already has the better-split pattern, e.g. cyber `asm/`,`vuln_sla/`); move aggregation out of controllers into services; resolve the duplicate `PolicyService` naming; introduce DTOs at controller boundaries.
- **Acceptance:** no service > ~400 LOC or > ~8 collaborators without justification; controllers contain no repository aggregation.
- **Effort:** 7×L, 13×M, 7×S — refactor-heavy, do incrementally.

### WS9 — Code duplication  🟡 P2
*33 findings — 5 High, 13 Med, 15 Low (overall dup 5.41%, 1,666 clones)*

- **Representative IDs:** `org()/currentUserId()/emit()/validate()` helper block copy-pasted across many services (18 copies of `currentUserId`), `nullSafe()/safe()` (~38 copies, divergent semantics), campaign-engine clone (`ObligationCampaignService`↔`VendorBcpCampaignService`), projection helpers in exercise services.
- **Fix:** extract a shared `TenantAuditSupport` base / `GrcStrings` util / `safe()` single source of truth; collapse the campaign engine into one generic service.
- **Acceptance:** jscpd duplication < 3%; the named helper clones removed.
- **Verify:** re-run `jscpd`.
- **Effort:** 6×L, 19×M, 8×S.

### WS10 — Error handling  🟡 P2
*12 findings — 7 Med, 5 Low*

- **Representative IDs:** `QUAL-CP-003` (empty `catch(Exception ignored)` on the SDAIA breach path, no logger), `QUAL-ESG-003` (`NdiAuditPublisher` swallows all, Javadoc lies about logging), over-broad `catch(Exception)` in export builders dropping the cause.
- **Fix:** never swallow — log with the cause at the right level and rethrow or handle deliberately; add loggers to the offending classes; narrow broad catches.
- **Acceptance:** no empty catch blocks; audit/breach paths log failures.
- **Effort:** 11×S, 1×M.

### WS11 — Dead code  ⚪ P3
*9 findings — 4 Med, 5 Low*

- **Representative IDs:** `QUAL-BCMP-001` (no-op `_touch()` / `KEEP_*` / `OBJECTS_KEEP_IMPORT` fields used only to suppress unused-import warnings across 10 files), injected-but-unused `ObjectMapper` masked with `@SuppressWarnings`.
- **Fix:** delete the dead imports/fields/loggers and the suppression scaffolding.
- **Effort:** 7×S, 2×M.

### WS12 — Backend performance (N+1 / indexes / pool)  🟠 P1
*24 findings — 9 High, 12 Med, 3 Low*

- **Representative IDs:** `PERF-NQ-*` (N+1 latest-KRI-reading loops, board-pack/export 2N queries), `QUAL-IE-001`/`PERF-NQ-003` (`EvidenceMatrixService` 4-level nested N+1), `PERF-IX-002` (missing indexes on `organisation_id`/FK in `V3/V4/V7` migrations), `PERF-IO-*` (Hikari pool sizing, synchronous outbound HTTP in integrations holding connections).
- **Fix:** add `JOIN FETCH`/batch fetch or projection queries for list views; add the missing covering indexes (org_id, FK, `is_deleted`, `workflow_status`) via a new migration; move blocking outbound integration calls off the request thread / out of `@Transactional`; tune `maximum-pool-size` for prod.
- **Acceptance:** no N+1 on register/dashboard endpoints (verify query counts); index migration applied; k6 re-run shows stable p95 under a heavier/write profile.
- **Verify:** `audit/tools/k6.exe run` (extend to a write endpoint to actually find the ceiling — current run hit 0 errors to ~350 rps).
- **Effort:** 4×L, 13×M, 7×S.

### WS13 — Frontend performance  🟠 P1
*4 findings — 2 High, 1 Med, 1 Info (Lighthouse /login Perf = 38)*

- **Representative IDs:** `LH-01` (no route-level code-splitting — `App.tsx` statically imports 300+ pages into one bundle), `LH-02` (i18n bundle), `LH-03` (CWV measured on dev server → Med).
- **Fix:** convert routes to `React.lazy` + `Suspense`; verify Vite manual chunking; lazy-load `recharts`; confirm `@tanstack/react-virtual` is used on large tables.
- **Acceptance:** Lighthouse Performance ≥ 70 on a **production build** (`npm run build` + `vite preview`), not the dev server.
- **Verify:** re-run Lighthouse against the preview build.
- **Effort:** 3×S, 1×M.

### WS14 — Test coverage  🟠 P1
*13 findings — 3 High, 5 Med, 2 Low, 3 Info*

- **Representative IDs:** `FUNC-001` (frontend has **0 tests**, no runner), `FUNC-002` (backend line 38.5% / branch 26%; 57.6% of classes at 0%), `FUNC-003` (3 of the Critical findings' classes have no tests; `CustomSqlRunner` test only checks DML rejection, not cross-tenant SELECT), `FUNC-004` (87% of controllers untested; controller tests bypass Spring Security), `E2E-005/006/007` (no CRUD/permission-denied/cross-tenant journeys).
- **Fix:** add **Vitest + React Testing Library** to the frontend (start with auth, api client, key pages); raise backend coverage on the high-risk classes fixed in WS1/WS2/WS4 (each fix ships with a test); add MockMvc-with-security tests; build a fixtured **Playwright** suite (per-role/tenant `storageState`, CRUD + negative + cross-tenant journeys — the audit harness in `audit/artifacts/e2e` is the starting point).
- **Acceptance:** frontend test runner live with smoke coverage; backend branch coverage ≥ 45% and **100% of WS1/WS2/WS4-fixed classes covered**; Playwright suite in CI.
- **Verify:** JaCoCo + `vitest run` + `playwright test` in CI.
- **Effort:** 3×L, 7×M, 3×S.

### WS15 — Accessibility (WCAG 2.1 AA)  🟠 P1
*7 findings — 3 High, 3 Med, 1 Info*

- **Representative IDs:** `A11Y-001/002/003` (color-contrast SC 1.4.3 — **68 instances** across 9 pages, root cause = 5 muted-text tokens in `tss-grc-ui-v2/src/styles/tokens.css`), plus manual-check recommendations (keyboard nav, visible focus, focus order, SR landmarks, motion).
- **Fix:** update the 5 tokens to values that reach **4.5:1** (the a11y agent computed exact before/after hex in the finding); then manually verify keyboard/focus/SR (axe can't automate these).
- **Acceptance:** axe reports **0 color-contrast violations** on all pages; documented keyboard/focus pass.
- **Verify:** re-run `audit/artifacts/e2e` axe pass (expect 68 → 0).
- **Effort:** 1×L, 3×M, 3×S — the token fix is a quick high-impact win.

### WS16 — UX & microcopy  🟡 P2
*20 findings — 3 High, 9 Med, 8 Low*

- **Representative IDs:** `COPY-01` (login pre-fills `admin/Admin@12345` + prints real creds — remove for non-dev), `UX-001` (topbar title crushed by fixed-width search), `UX-004/005` (mobile header/hero overlap), `COPY-02` (dead "continue as demo" CTA), `COPY-03` (dev jargon + hard-coded `localhost:8084` in user error), `COPY-04/05` (terse, inconsistent error voice).
- **Fix:** gate demo creds/hint behind a dev flag; make the topbar title flexible / search collapsible; add mobile `.page-header` rules; rewrite error copy to be specific + actionable + bilingual-parity (EN/AR mirror each change).
- **Acceptance:** no credentials shown in non-dev builds; mobile screenshots clean; error copy reviewed.
- **Effort:** 9×S, 11×M.

---

## 4. Master traceability

| WS | Title | Findings | C/H/M/L/I | Priority | Effort (S/M/L) |
|----|-------|---------:|-----------|----------|----------------|
| WS1 | Cross-tenant isolation | 20 | 2/8/6/3/1 | **P0** | 8/9/3 |
| WS2 | Function-level authz | 39 | 2/11/20/4/2 | **P0** | 22/17/0 |
| WS4 | Injection/unsafe input | 23 | 0/2/9/10/2 | P0*/P2 | 10/10/3 |
| WS3 | Auth/session hardening | 20 | 0/1/7/7/5 | P1 | 6/14/0 |
| WS5 | Dependency CVEs | 10 | 0/1/7/1/1 | P1 | 5/5/0 |
| WS12 | Backend perf | 24 | 0/9/12/3/0 | P1 | 7/13/4 |
| WS13 | Frontend perf | 4 | 0/2/1/0/1 | P1 | 3/1/0 |
| WS14 | Test coverage | 13 | 0/3/5/2/3 | P1 | 3/7/3 |
| WS15 | Accessibility | 7 | 0/3/3/0/1 | P1 | 3/3/1 |
| WS6 | Secrets/config | 13 | 0/0/1/7/5 | P2 | 12/1/0 |
| WS7 | Input validation | 13 | 0/0/1/12/0 | P2 | 10/3/0 |
| WS8 | God-class/SOLID | 27 | 0/4/9/11/3 | P2 | 7/13/7 |
| WS9 | Duplication | 33 | 0/5/13/15/0 | P2 | 8/19/6 |
| WS10 | Error handling | 12 | 0/0/7/5/0 | P2 | 11/1/0 |
| WS16 | UX & microcopy | 20 | 0/3/9/8/0 | P2 | 9/11/0 |
| WS11 | Dead code | 9 | 0/0/4/5/0 | P3 | 7/2/0 |

\* WS4's `CustomSqlRunner` (`SEC-INT-001`) is P0; the rest of WS4 is P2.

---

## 5. Quick wins (high value, S effort — do first within each wave)

- **WS15:** fix 5 color tokens in `tokens.css` → clears all 68 contrast violations (one file).
- **WS5:** bump `spring-boot-starter-parent` 3.5.6 → 3.5.14 → clears most of the 14 CVEs.
- **WS2:** ship the controller **meta-test** that fails CI on any unguarded write endpoint → prevents regression while the 39 fixes land.
- **WS16/COPY-01:** gate demo credentials behind a dev flag → removes the one-click platform-admin login from any non-dev build.
- **WS10:** add loggers + rethrow to the ~12 swallowed-exception sites (mechanical).

---

## 6. Verification & regression strategy

Re-run the audit's own harnesses (all in `audit/`) after each wave; a finding is closed only when its tool stops reporting it:

| Workstream(s) | Re-run command | Pass gate |
|---|---|---|
| WS1, WS2, WS4 | new MockMvc-with-security + cross-tenant integration tests; `mvn test` | 0 cross-tenant 200s; meta-test green |
| WS5 | `audit/tools/trivy.exe fs ...` + `npm audit` | 0 High/Critical |
| WS12 | `audit/tools/k6.exe run audit/artifacts/k6/load.js` (extend to a write endpoint) | stable p95, finds real ceiling |
| WS13 | `npx lighthouse` against `vite preview` (prod build) | Performance ≥ 70 |
| WS14 | `mvn ...jacoco:report`, `vitest run`, `playwright test` | branch ≥ 45%; FE suite live |
| WS15 | `audit/artifacts/e2e` axe pass | 0 contrast violations |
| all | re-generate the report: `python audit/tools/aggregate.py && python audit/tools/gen_report.py` | Critical = 0 after P0 |

**Definition of done for the whole program:** 0 Critical / 0 High security findings, 0 High/Critical CVEs, axe contrast clean, frontend test suite in CI, and the regenerated `audit-report.html` reflects it.

---

## 7. Suggested sprint breakdown

- **Sprint 1 (P0):** WS2 meta-test + the 4 Criticals (WS1 `SEC-CYB-001/002`, WS2 `SEC-FIN-001`/`SEC-IAM-001`) + `CustomSqlRunner` (`SEC-INT-001`) → **target: 0 Critical.**
- **Sprint 2 (P0→P1):** finish WS1 + WS2 High; start WS5 (dep bump) + WS15 (tokens).
- **Sprint 3 (P1):** WS3 auth hardening, WS12 backend perf High, WS14 test foundation (FE runner + security MockMvc).
- **Sprint 4–5 (P2):** WS7, WS8, WS9, WS10, WS13, WS16.
- **Sprint 6 (P2→P3):** WS6, WS11, residual Low/Info; full audit re-run + sign-off.

---

*Generated from `audit/all_findings.json` (287 findings) and `audit/workstreams.json`. Per-finding patches are embedded in [`audit-report.html`](audit-report.html). No source files were modified by the audit or this plan.*
