# Sprint 3 — Hardening, Advanced Features & Production Readiness

> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Duration:** 6 weeks
**Theme:** Advanced reporting & analytics, licensing/subscription engine, full audit trail, advanced UI/UX polish, performance hardening, security penetration testing, and production deployment.

---

## Goals

By the end of Sprint 3, the platform is production-ready:
- Advanced analytics dashboards with drill-down and trend analysis
- Licensing and subscription management fully operational
- Complete audit trail with tamper-evident logs and export
- Full-featured admin portal (branding, system configuration, backup management)
- OWASP Top 10 penetration test passed
- Production deployment on Windows Server + SQL Server Express 2019
- Operator runbook and end-user documentation complete

---

## Deliverables by Layer

### Database (Flyway Migrations)
| Migration File | Description |
|---|---|
| `V3.0.0__licensing_tables.sql` | subscription_plans, organisation_subscriptions, usage_metrics, feature_flags |
| `V3.1.0__advanced_audit.sql` | Temporal tables enabled on: risks, compliance_assessments, platform.controls, iam.users |
| `V3.2.0__archival_tables.sql` | archived_audit_logs, archived_notifications (application-level archival replacing SQL partitions) |
| `V3.3.0__sprint3_indexes.sql` | Analytics query covering indexes |
| `V3.4.0__system_config_tables.sql` | system_settings, email_templates, branding_config |
| `R__seed_subscription_plans.sql` | Starter / Professional / Enterprise plan seed data |

### Backend — Licensing & Subscription Module
- [ ] Entities: `SubscriptionPlan`, `OrganisationSubscription`, `UsageMetric`, `FeatureFlag`
- [ ] `LicenseValidationService.java` — validates module access against subscription tier on every request via `@PreAuthorize`
- [ ] `UsageTrackingService.java` — records monthly usage metrics (users, records, storage)
- [ ] `SubscriptionExpiryJob.java` — @Scheduled daily check, email warning 30/7/1 day before expiry
- [ ] `LicensingController.java` — admin UI for subscription management
- [ ] Feature flag enforcement wired into all module `@PreAuthorize` expressions

### Backend — Advanced Audit Trail
- [ ] `AuditLogService.java` — enhanced with before/after value capture (JSON diff)
- [ ] `AuditQueryService.java` — filtered query by entity type, user, date range, module
- [ ] Temporal table query helper: `TemporalTableService.java` — point-in-time risk/assessment retrieval
- [ ] `AuditExportService.java` — tamper-evident export (SHA-256 chain hash appended per row)
- [ ] `AuditController.java` — admin audit log viewer with timeline UI

### Backend — Advanced Reporting & Analytics
- [ ] `TrendAnalysisService.java` — risk score trend over time (weekly snapshots)
- [ ] `FrameworkComparisonService.java` — side-by-side compliance % across frameworks
- [ ] `ExecutiveReportService.java` — auto-generated monthly executive summary PDF
- [ ] `ScheduledReportJob.java` — @Scheduled delivery of subscribed reports via SMTP
- [ ] `KpiAlertService.java` — threshold-based KRI alerts; sends notification when breached
- [ ] Report templates: Risk Summary, Compliance Dashboard, Incident Trend, BCM Readiness, Privacy Register (both EN + AR)

### Backend — Administration Enhancements
- [ ] `BrandingService.java` — per-org logo upload, primary colour, platform name override
- [ ] `SystemSettingsService.java` — configurable: password policy, session timeout, email sender, MFA toggle
- [ ] `EmailTemplateService.java` — admin-editable Thymeleaf email templates with bilingual preview
- [ ] `ArchivalJob.java` — @Scheduled monthly: move audit_logs > 12 months to archived_audit_logs table
- [ ] `BackupStatusController.java` — displays last DB backup time read from SQL Server `msdb..backupset` (Express-compatible)
- [ ] `SystemHealthController.java` — DB connectivity, storage space, DB size vs 10 GB limit warning, active sessions

### Backend — Security Hardening
- [ ] MFA support — TOTP (Google Authenticator) via `dev.samstevens.totp` library
- [ ] `BruteForceProtectionService.java` — account lockout after N failed attempts (configurable)
- [ ] `PasswordPolicyService.java` — enforce complexity, history (last 5), expiry
- [ ] Rate limiting on login endpoint — Bucket4j (in-memory token bucket)
- [ ] `ContentSecurityPolicyConfig.java` — strict CSP with nonce for inline scripts
- [ ] Security headers audit: HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy
- [ ] OWASP ZAP automated scan integrated into CI (fail on MEDIUM+)

### Backend — Performance
- [ ] Query optimisation: review EXPLAIN plans for all dashboard queries; add hints where needed
- [ ] `@QueryHints` on heavy JPA queries (fetchSize, readOnly)
- [ ] Response compression enabled (`server.compression`)
- [ ] HTTP/2 enabled in production profile
- [ ] JVM tuning recommendations documented (`-Xms512m -Xmx1024m -XX:+UseG1GC`)
- [ ] DB size monitoring: `@Scheduled` daily check, warn when > 8 GB (80% of Express 10 GB limit)

### Frontend — Advanced UI/UX (`tss-grc-ui/`)
- [ ] `dashboard.html` — drill-down chart clicks navigate to filtered module list; risk trend sparklines (Chart.js)
- [ ] All list pages — multi-select filter dropdowns replacing basic text search; loading skeleton screens
- [ ] All detail pages — breadcrumb navigation; print-optimised `@media print` CSS
- [ ] Notification centre — slide-out `<aside>` panel with paginated notification history (calls `GET /api/v1/notifications`)
- [ ] Keyboard navigation — Tab order audit; ARIA labels on all interactive elements
- [ ] RTL QA pass — manual review of all Arabic pages for layout, truncation, number formatting

### Frontend — New Pages (`tss-grc-ui/`)
- [ ] `admin/audit-log.html` — vertical timeline UI; before/after JSON diff viewer; calls `GET /api/v1/audit/logs`
- [ ] `admin/branding.html` — logo upload (multipart to `POST /api/v1/admin/branding`), colour picker, live preview
- [ ] `admin/system-settings.html` — password policy, session timeout, email config forms
- [ ] `admin/system-health.html` — DB size gauge (`GET /api/v1/admin/health/db`), backup status, active sessions
- [ ] `admin/licensing.html` — subscription plan display + feature flag toggles
- [ ] `reports/executive.html` — full-screen executive view; `window.print()` for PDF output
- [ ] `reports/trends.html` — risk and compliance trend line charts (Chart.js, 12-week rolling)
- [ ] `profile/index.html` — password change, MFA setup QR code display, notification preferences

### Operations & Documentation
- [ ] `docs/operations/runbook.md` — startup, shutdown, DB backup (SSMS/sqlcmd), log rotation, DB size monitoring
- [ ] `docs/operations/deployment-guide.md` — Windows Server 2019 + SQL Server Express 2019 + Java 17 setup
- [ ] `docs/operations/security-checklist.md` — pre-go-live security hardening checklist
- [ ] `docs/user-guide/` — 5 user guide documents (Dashboard, Risk, Compliance, Admin, Reports) in EN; AR translation pending post-launch
- [ ] API documentation — SpringDoc/Swagger exported as `docs/api/openapi.json`
- [ ] `CHANGELOG.md` — Sprint 1/2/3 changes documented

---

## Acceptance Criteria

1. MFA enrollment flow works: user scans QR, enters TOTP code, subsequent logins require TOTP
2. Account locks after 5 failed login attempts; admin can unlock from user management
3. Executive PDF report generates correctly in both English and Arabic with organisational branding
4. Audit log shows before/after diff for risk score changes and compliance finding resolutions
5. Temporal table query returns correct historical risk state for any past date
6. System health page shows accurate DB size with warning when > 8 GB
7. Archival job moves 12-month-old audit logs without data loss (verified by count)
8. OWASP ZAP baseline scan passes with zero HIGH findings
9. Load test: 50 concurrent users, dashboard page < 3s P95 response time
10. All 10 modules accessible in Arabic with correct RTL layout, no truncated text

---

## Definition of Done

- All Sprint 3 unit + integration tests passing (Jacoco min 75% overall)
- Security scan clean (OWASP ZAP + manual review)
- Load test results documented and within SLA
- Deployment guide validated on a clean Windows Server 2019 + SQL Server Express 2019 environment
- Arabic translation 100% complete for all modules
- No open CRITICAL or HIGH bugs in the bug tracker

---

## Sprint Completion = Production Launch Readiness ✓

At the end of Sprint 3, the TSS GRC Platform v1.0 is:
- Feature-complete across all 10 modules
- Security-hardened and penetration-tested
- Bilingual (Arabic / English) with full RTL support
- Deployable on-premise on SQL Server 2019 Express Edition
- Documented for operators and end users
- Ready for Saudi market launch with NCA ECC, ISO 27001, PDPL, ISO 22301 compliance awareness
