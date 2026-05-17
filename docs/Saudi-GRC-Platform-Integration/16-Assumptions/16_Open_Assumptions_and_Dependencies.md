# 16 — Open Assumptions & Dependencies

The implementation prompt's final instruction (§32) says:

> "Do not claim regulatory completeness unless official control
> libraries are imported. Use placeholders for official control IDs
> where needed."

This document captures those caveats explicitly.

## Placeholder regulatory content

The platform seeds catalogs for NCA (ECC v2 / CSCC / CCC / OTCC /
DCC / TCC), PDPL 2023, SAMA frameworks (BCM / Cyber / Outsourcing /
Op-Risk), DGA (Maturity / Interoperability), NRC, and the ISO suite
(22301 / 27001 / 27701 / 31000) plus SOC 2 — all flagged
`is_placeholder = 1` and `translation_status = EN_ONLY` in
`platform.authority_documents`.

Until the official text is imported via the regulator's preferred
channel:

- UI banners on `/regulatory/library`, `/controls/library`,
  `/compliance/posture` warn the user.
- The `translation_status` flag tracks AR quality at the catalog
  + citation level (EN_ONLY → AR_AUTO → AR_HUMAN_REVIEWED →
  AR_OFFICIAL).
- Citation codes follow the regulator's own numbering (e.g.
  `2-1-3` for NCA ECC) and we do NOT invent IDs.

## Authentication assumptions

- **In scope today:** JWT (access 1h dev / 15min prod) + HttpOnly
  refresh cookie 7d. Username/password with BCrypt.
- **Out of scope today:** Saudi Nafath / Absher SSO. Skeleton
  exists at `module/auth/sso` for a future integration; the
  current contract assumes the customer has their own IdP for SSO
  via OAuth2 / OIDC.

## Data residency

- Demo + dev: SQL Server local.
- Prod assumption: SQL Server hosted in KSA (Saudi cloud regions
  on Microsoft Azure or local DC). Customer to provide cluster +
  backup + DR strategy.

## Continuous Controls Monitoring

The platform does NOT yet integrate with AWS / Azure / Microsoft
365 / Okta for live posture telemetry. This is the single biggest
gap vs. Leaders-quadrant IRM tools and is on the future track
(post-Sprint 8).

## AI features

Deferred to task #41. The platform doesn't ship LLM integration
today. When it does, the design will route every AI output through
the existing audit log + automation rule mechanisms so AI
suggestions are auditable + reversible.

## Integration connectors

| Connector | Status |
|---|---|
| SMTP (email) | ✅ built into `NotificationService` |
| Webhook (Slack/Teams) | ✅ built |
| SAML / OIDC SSO | 🟡 scaffolded under `module/auth/sso` |
| SIEM bridge | 🔜 deferred |
| ServiceNow connector | 🔜 deferred |
| Jira connector | 🔜 deferred |
| Azure Sentinel | 🔜 deferred |
| AWS Security Hub | 🔜 deferred |

## SLAs assumed in the platform

- **PDPL breach notification:** 72h (Article 27) — hard-coded.
- **DSR initial response:** 30 days, extendable to 90 (PDPL
  Article 18).
- **Vulnerability remediation:** Critical 15d / High 30d / Medium
  60d — configurable per tenant in `cyber_lc.vuln_sla_settings`.
- **NCA evidence expiry warning:** 60 days — configurable.

## Hardware / platform assumptions

- Browser: modern evergreen (Chrome / Edge / Safari / Firefox).
  No IE.
- Mobile: Sidebar collapses on < 980px. Touch-first is supported
  on tablets; mobile phone view is functional but not optimised
  beyond authentication + dashboard.
- Network: assumes corporate or trusted network. Public-internet-
  exposed instances should sit behind a WAF (AWS WAF / Azure App
  Gateway recommended).

## Future-track items (not in current package)

| Item | Reason for deferral |
|---|---|
| AI assistance (5 endpoints) | Vendor selection + guardrail design pending |
| CCM connectors | Per-vendor design effort |
| RTL hardening sweep | Visual regression suite needed first |
| Dedicated dashboards (BCM / SCM / ERM / NCA / PDPL) | task #36 — straightforward reuse of widgets |
| Missing entities (Department, Consent Record, Cloud Service, Issue) | task #39 — small data-model slice |
| F8 action handlers wired into each module | Per-module review |
| F9 LicenseGuard aspect + JWT claim injection | Backend follow-up |

## Implementation team assumptions

- 1 GRC product lead (Saudi regulator expertise required)
- 1 architect (this platform's spine)
- 2-3 full-stack engineers (Java/Spring + React/TS)
- 1 QA lead (running the QA checklists)
- 1 Arabic UX reviewer (Saudi-native phrasing per MEMORY.md)
- 1 DevOps / platform engineer (CI + deployment)

## Operating model assumptions

- The customer designates a "GRC Administrator" superuser who owns
  the tenant's role catalogue, RBAC matrix, and license
  configuration.
- The customer designates a "DPO" who owns the PDPL module + DSR
  responses + breach notifications.
- The customer designates a "Risk Manager" + "BCM Manager" +
  "Cybersecurity Manager" who own their domains.
- The customer designates a "Compliance Officer" who owns the
  regulator-facing submissions + posture hub.
- The customer designates a "Board Secretary" who runs the board
  pack workflow.

These roles map to the `iam.roles` seed; customers can rename
them but the underlying permission set is what determines access.

## Documentation discipline (going forward)

This Saudi-GRC-Platform-Integration package is the saleable
artefact. To keep it accurate:

1. Every new module → 02_Module_Architecture entry.
2. Every new authority document → 07_Regulatory_Mapping entry.
3. Every new automation rule → 09_Automation_Rules entry.
4. Every new UAT → 14_Test_Cases entry.
5. Every new page → 05_UI_Screen_Requirements entry.

The QA checklists in `docs/<module>/QA_CHECKLIST.md` remain the
**runtime gate** — this package is the **release artefact**.

---

**Closing.** This package, paired with the codebase, is sufficient
for a development team, a compliance consultancy, and an executive
sponsor to start building, auditing, and selling the platform
without re-deriving the architecture.
