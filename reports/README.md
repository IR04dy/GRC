# TSS GRC Platform — Comprehensive Audit Report

**One-line result:** A 7-phase deep audit (security, quality, performance, functional, E2E, UX, accessibility) of the TSS GRC platform at commit `3ca91bf` produced **287 findings** — **4 Critical, 52 High, 114 Medium, 93 Low, 24 Info** (every Critical/High independently adversarially verified: 53 confirmed, 10 severity-adjusted, 2 refuted). The four Critical issues are all cross-tenant / authorization breaks: IDOR across `CyberService` (read + overwrite of any tenant's rows — `SEC-CYB-001/002`), an unauthenticated public intake tracker leaking full submissions (`SEC-FIN-001`), and approval-chain actor identity taken from the request body (`SEC-IAM-001`). The dominant systemic theme is app-wide broken function-level authorization (`@PreAuthorize("isAuthenticated()")` instead of `hasPermission('MODULE','ACTION')`).

## How to open

Open the self-contained report in any browser — no server, no internet needed:

```
reports/audit-report.html
```

On Windows: `start reports\audit-report.html` (or double-click it).

The file is fully standalone (inline CSS + vanilla JS, no external/CDN calls).

## Remediation plan

The prioritized fix roadmap is in **[`reports/REMEDIATION_PLAN.md`](REMEDIATION_PLAN.md)** — it clusters all 287 findings into 16 root-cause workstreams across 4 priority waves (P0–P3), with fix patterns, acceptance criteria, verification commands, and a suggested 6-sprint breakdown.

## Using the report

- **Filter** by severity with the top buttons, or **search** by keyword / file / finding-ID.
- **Expand/Collapse all** finding cards, or click any card header to toggle it.
- **Sidebar** links jump to the Executive Summary, Metrics, each of the 7 phase sections, and the consolidated Remediation Plan.
- **Print to PDF**: the report has a print stylesheet (cards auto-expand, sidebar/controls hidden) so `Ctrl+P → Save as PDF` exports cleanly.

## What was run

Static + dynamic, against the live stack (backend `:8084`, frontend `:5173`, SQL Server `:1433`):

- **Security:** Trivy (36 dependency CVEs), gitleaks (secrets), npm audit, bandit (Python), plus deep per-module manual review across all 62 backend packages and the frontend.
- **Quality:** jscpd (duplication 5.41%), tsc (0 type errors), per-module code-review.
- **Performance:** k6 load test (baseline/spike/soak/breakpoint) + static N+1 / index / pool analysis.
- **Functional:** Maven Surefire (1,858 backend tests, 100% pass) + JaCoCo coverage (38.5% line).
- **E2E / UX / Accessibility:** Playwright + axe-core (10 pages) + Lighthouse, driving a real admin session.

Every Critical and High finding was put through an independent adversarial verification pass.

## Caveats (full honesty)

- semgrep has no native Windows support and the Docker daemon was offline, so Java SAST was done by manual deep review + Trivy + pattern analysis rather than semgrep.
- The k6 breakpoint test reached ~350 req/s on a fast read path with **0 errors** — no failure threshold was hit under that profile; the true ceiling is higher and would need a heavier/write endpoint to find.
- Lighthouse ran on the public `/login` page; authenticated-page accessibility was measured via axe-core through a real Playwright session.

Source data: `audit/all_findings.json` (all findings), `audit/raw/` (raw tool output), `audit/artifacts/` (Playwright + k6 harnesses).
