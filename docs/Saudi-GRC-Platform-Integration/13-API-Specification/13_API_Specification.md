# 13 — API Specification

The platform exposes a REST API documented live via SpringDoc:

- **OpenAPI JSON:** `http://<host>:8084/v3/api-docs`
- **Swagger UI:** `http://<host>:8084/swagger-ui.html` (dev profile only)
- **Postman collection:** auto-generated from the OpenAPI spec

This document is the **map** to that live spec — it groups
endpoints by module and notes the framework-intersection cross-cuts.

## Conventions

- Base path: `/api/v1/<resource>` for everything new.
- Auth: `Authorization: Bearer <jwt>` on every endpoint except
  `/api/v1/auth/login` and `/api/v1/auth/refresh`.
- Pagination: Spring `Pageable` style (`page`, `size`, optional
  `sort`).
- Errors: `application/problem+json` per RFC 7807.

## Endpoints by module

### Auth (`module/auth`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/login` | username/password → access JWT + refresh cookie |
| POST | `/api/v1/auth/refresh` | rotate access JWT |
| POST | `/api/v1/auth/logout` | clear refresh cookie |
| POST | `/api/v1/auth/reset/request` | send reset email |
| POST | `/api/v1/auth/reset/confirm` | reset password with token |

### Risk (`module/risk`)

| Method | Path | Purpose |
|---|---|---|
| GET / POST / PUT / DELETE | `/api/v1/risk/risks[/:id]` | register CRUD |
| POST | `/api/v1/risk/risks/:id/transition` | lifecycle move |
| GET / POST | `/api/v1/risk/risks/:id/assessments` | risk assessments |
| GET / POST / PUT / DELETE | `/api/v1/risk/risks/:id/treatments[/:tid]` | treatments |
| POST | `/api/v1/risk/risks/:id/treatments/:tid/transition` | treatment lifecycle |
| GET / POST | `/api/v1/risk/kri[/:id]` | KRI definitions + readings |
| GET / POST | `/api/v1/risk/appetite` | risk appetite |
| GET | `/api/v1/risk/heatmap` | 5×5 heatmap data |

### BCM (`module/bcm`)

`/api/v1/bcm/{services, bia, plans, drp, exercises, crisis, comms}` —
same RESTful pattern. See Swagger.

### SCM (`module/scm`) + TPRM (`module/tprm`)

`/api/v1/scm/{suppliers, contracts, dependencies, resilience-tests, sla, alternates, exit-plans}`
`/api/v1/tprm/{vendors, vendor-assessments, questionnaire-templates, questionnaires, processors}`

### Cyber (`module/cyber`)

`/api/v1/cyber/{frameworks, controls, sub-controls, assessments, assets, vulns, exceptions, incidents, evidence, nca/score, ccc, otcc}`

### Privacy (`module/privacy`) + Privacy LC (`module/privacy_lc`)

`/api/v1/privacy/{ropa, dsr, dpia, notices, consent, cross-border, breaches, processors, disposal}`

### Policy (`module/policy`)

`/api/v1/policy/{documents, exceptions, attestations}` + transition
endpoints.

### NDI (`module/ndi`)

`/api/v1/ndi/{cycles, framework, maturity, compliance, opex, governance, evidence, gaps, capa, plans, submission, reports, settings, audit}`.

### Compliance + Audit (`module/compliance`, `module/audit_lc`)

`/api/v1/compliance/{self-assessments, findings}` and
`/api/v1/audit-lc/{engagements, findings, capas}`.

### Evidence (`module/evidence`)

`/api/v1/evidence/{items, links, requests, chain}`.

### Incident + Crisis

`/api/v1/incident/{incidents, rca, capa}` + `/api/v1/crisis/{events, comms-logs}`.

### Reporting + Dashboard

`/api/v1/reporting/{definitions, runs, schedules}` +
`/api/v1/dashboard/{health-score, top-risks, …}`.

### Workflow + Approvals + Signature

`/api/v1/workflow/{definitions, instances, transitions}` +
`/api/v1/approvals/{requests, steps}` +
`/api/v1/signature/transactions`.

### Platform spine (Framework Intersection)

| F# | Path prefix | Purpose |
|---|---|---|
| F1 | `/api/v1/regulatory/authority-documents` | catalog |
| F1 | `/api/v1/regulatory/authority-documents/:id/citations` | per-doc citations |
| F1 | `/api/v1/regulatory/citations/:id` | one citation resolved with parent |
| F2 | `/api/v1/controls` | unified control library |
| F2 | `/api/v1/control-objectives` | groupings |
| F2 | `/api/v1/control-mappings` | control ↔ citation |
| F2 | `/api/v1/risk-control-links` | risk ↔ control matrix |
| F2 | `/api/v1/control-assessments` | unified assessments |
| F5/F6 | `/api/v1/regulatory/authority-documents/:id/coverage` | per-doc breakdown |
| F5/F6 | `/api/v1/regulatory/posture` | heatmap |
| F5 | `/api/v1/regulatory/domain-alignment` | per-domain rollup |
| F7 | `/api/v1/audit-log` | unified audit ledger |
| F8 | `/api/v1/automation/rules`, `/runs`, `/stats` | rules engine admin |
| F9 | `/api/v1/licensing/{modules, me/enabled, catalog}` | licensing |

### Admin + IAM

`/api/v1/admin/{tenants, settings}` +
`/api/v1/iam/{users, roles, permissions, committees}`.

## Common query parameters

| Name | Values | Default |
|---|---|---|
| `page` | 0..N | 0 |
| `size` | 1..200 | 20 (resource-specific) |
| `sort` | `field,asc/desc` | per-resource default |
| `q` | free-text search | none |

## Rate limiting

Per-tenant + per-endpoint via Bucket4j (configured in
`platform.rate_limits` table). Default: 600 req/min for read,
60 req/min for write, 10 req/min for export.

## Versioning

Path-versioned (`/api/v1`). When a breaking change is needed, a
new path (`/api/v2/...`) is introduced and v1 stays alive for at
least one full release cycle.

---

**See also:** `13_Sample_JSON_Payloads.md`, Swagger UI at
`/swagger-ui.html`.
