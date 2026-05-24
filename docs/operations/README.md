# Operations guides

Files in this folder are referenced by the **Operations Lead** during install + go-live.

| File | Purpose | Closes |
|---|---|---|
| `nginx-production.conf` | Sample nginx config — TLS termination, SPA serving, /api proxy to Spring Boot, security headers per NCA ECC-2 control 2-9 | Issue #011 |
| `cross-border-transfer-safeguards.md` | Bilingual template legal wording for PDPL Art. 29 transfer safeguards (SCC+TIA, adequacy, consent) | Issue #012 |
| `jwt-secret-rotation.md` | JWT signing key generation, 90-day rotation cadence, 24h overlap window via `JWT_SECRET_PREVIOUS`, emergency revocation procedure | Issue #001 |
| `sqlexpress-size-monitor.md` | 10 GB Express cap monitor — WARN (70%) / ALERT (90%) / HALT (95%) thresholds, F8 event payload, operator playbook, emergency drop list | Issue #004 |
| `mfa-totp-selection.md` | TOTP library decision record (`dev.samstevens.totp:totp:1.7.1`), enrollment & verification flow, backup-code strategy, MFA enforcement policy | Issue #008 |
| `sdaia-breach-notification-workflow.md` | PDPL Article 31 72-hour SDAIA submission runbook — state machine, channels, required content, F8 hooks, Saudi cross-notification rules (SAMA / NCA) | Issue #006 |
| `ldap-ad-integration-guide.md` | Per-customer LDAP/AD integration recipe — `application-prod.yml` shape under `spring.ldap` + `grc.iam.ldap`, attribute mapping table, AD nested-group filters, transport security, troubleshooting matrix | Issue #009 |

## Related docs elsewhere

- `tss-grc-backend/docs/integrations/` — per-integration setup notes
- `README.md` (repo root) — installation overview
- `STATUS.md` — module-by-module rollout state
- `docs/roadmap/04-open-issues-register.md` — full open-issues register
