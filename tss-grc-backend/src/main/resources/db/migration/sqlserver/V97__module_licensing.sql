-- V97: Module licensing — Framework Intersection plan F9.
--
-- Each tenant can be entitled to a different subset of modules
-- (RISK / BCM / SCM / CYBER / PDPL / TPRM / POLICY / NDI / …).
-- The Saudi GRC prompt §18 explicitly calls this out: "Include
-- enabled_modules or module_license_key to support separate module
-- licensing."
--
-- One row per (organisation, module). The LicenseGuard service uses
-- this to decide who can call which controller; the JWT factory
-- bakes the enabled modules into the claim set so the frontend can
-- hide unlicensed sidebar entries without a round trip.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'organisation_licenses' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.organisation_licenses (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        module_code         NVARCHAR(40)    NOT NULL,
        -- Canonical module codes (kept in sync with the sidebar):
        --   RISK, BCM, SCM, CYBER, PDPL, TPRM, POLICY, NDI,
        --   COMPLIANCE, AUDIT_LC, INCIDENT, EVIDENCE, REPORTING,
        --   REGULATORY (F1 catalog), CONTROL_LIB (F2 library),
        --   POSTURE (F6 hub), AUTOMATION (F8), AUDIT_LOG (F7),
        --   PLATFORM_ADMIN (always-on for super-admins)

        tier                NVARCHAR(20)    NOT NULL DEFAULT 'PRO',
        -- FREE | PRO | ENTERPRISE
        seat_count          INT             NULL,
        valid_from          DATE            NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATE),
        valid_to            DATE            NULL,
        enabled             BIT             NOT NULL DEFAULT 1,

        notes               NVARCHAR(MAX)   NULL,
        created_at          DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2(0)    NULL,

        CONSTRAINT uq_license_org_module UNIQUE (organisation_id, module_code),
        CONSTRAINT ck_license_tier CHECK (tier IN ('FREE','PRO','ENTERPRISE'))
    );
    CREATE INDEX ix_license_org ON platform.organisation_licenses(organisation_id, enabled);
END
GO

-- Seed: the demo tenant (org=1) gets ENTERPRISE access to every
-- module the platform currently exposes. Customers will get a
-- narrower subset.
IF NOT EXISTS (SELECT 1 FROM platform.organisation_licenses WHERE organisation_id = 1 AND module_code = 'RISK')
BEGIN
    INSERT INTO platform.organisation_licenses
      (organisation_id, module_code, tier, enabled, notes)
    SELECT 1, m, 'ENTERPRISE', 1, 'Demo tenant — full access seeded by V97'
    FROM (VALUES
        ('RISK'), ('BCM'), ('SCM'), ('CYBER'), ('PDPL'), ('TPRM'),
        ('POLICY'), ('NDI'), ('COMPLIANCE'), ('AUDIT_LC'),
        ('INCIDENT'), ('EVIDENCE'), ('REPORTING'),
        ('REGULATORY'), ('CONTROL_LIB'), ('POSTURE'),
        ('AUTOMATION'), ('AUDIT_LOG'), ('PLATFORM_ADMIN')
    ) AS v(m);
END
GO
