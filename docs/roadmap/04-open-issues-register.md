# 04 - Open Issues Register

Issues and decisions that require resolution before or during Sprint 1. Updated as items are resolved.

---

| # | Issue | Priority | Owner | Status | Resolution |
|---|---|---|---|---|---|
| 001 | **JWT secret key management** — The `grc.jwt.secret` config value must be a cryptographically random 256-bit key in production. A key rotation strategy (rotate every 90 days, invalidate all active sessions) must be defined before go-live. | HIGH | Security Lead | Open | — |
| 002 | **Refresh token revocation** — Current design has no server-side refresh token store. If a refresh token is compromised, it remains valid until expiry (7 days). Options: (a) store issued refresh token IDs in DB and check on use, (b) shorten TTL to 24h, (c) accept risk for v1.0. | MEDIUM | Architect | Open | — |
| 003 | **Arabic PDF fonts** — OpenPDF requires Noto Kufi Arabic or Amiri font files bundled in `src/main/resources/fonts/`. Font licensing must be confirmed (SIL Open Font License — verified as permissive). Files not yet added to the repository. | HIGH | Frontend Lead | Open | — |
| 004 | **SQL Server Express 10 GB limit monitoring** — The `@Scheduled` DB size check job must be implemented in Sprint 3. Alert threshold and escalation path (email to DBA + in-app warning) need definition. | MEDIUM | DB Lead | Open | — |
| 005 | **SAMA CSF control seed data** — SAMA CSF 140 controls are not yet seeded in `R__seed_controls.sql`. Content must be sourced from the official SAMA publication and structured as SQL INSERT statements. | HIGH | Compliance SME | Open | — |
| 006 | **PDPC notification API** — Saudi PDPC currently has no published machine-readable API for breach notification. Manual process must be documented and a manual notification workflow added to `privacy.data_breaches`. | LOW | Privacy SME | Open | — |
| 007 | **Multi-language email templates** — Email notification templates (risk assigned, task due, breach alert) must be authored in both Arabic and English. Template content not yet written. | MEDIUM | Content Lead | Open | — |
| 008 | **MFA TOTP library selection** — Sprint 3 plans TOTP-based MFA. `dev.samstevens.totp` library selected tentatively. Dependency must be added to `pom.xml` and security review completed before implementation. | MEDIUM | Security Lead | Open | — |
| 009 | **LDAP/AD integration configuration** — `spring-boot-starter-data-ldap` is included as optional. Integration with customer AD requires per-customer LDAP attribute mapping (DN format, group-to-role mapping). A configuration guide must be written. | LOW | IAM Lead | Open | — |
| 010 | **Logo and branding assets** — `logo-white.svg` and `logo-dark.svg` placeholder paths referenced in `app.js` and `login.html`. Final TSS brand assets not yet provided. | LOW | Design | Open | — |
| 011 | **Nginx configuration for production** — Sample `nginx.conf` for serving `tss-grc-ui/` and proxying `/api/` is documented in `20-frontend-architecture.md` but not yet committed as a file. Add to `/docs/operations/` in Sprint 3. | MEDIUM | DevOps | Open | — |
| 012 | **Cross-border data transfer safeguards wording** — PDPL Article 29 requires documented safeguards for any transfer of personal data outside KSA. Template legal language for the `processing_activities.transfer_safeguards_en` field is needed. | MEDIUM | Legal / Privacy SME | Open | — |
