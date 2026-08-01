-- ============================================================================
-- TSS GRC Platform — Sprint 3: Third Party Risk Management (TPRM) Tables
-- Schema: tprm
-- ============================================================================

-- ⚠ `USE TSS_GRC;` removed 2026-08-01 (F2-76). Flyway is already connected to the target
-- database, so the statement was redundant here and actively wrong anywhere else: on a customer
-- database with any other name it either fails outright, or — worse — SUCCEEDS by switching to some
-- unrelated database that happens to be called TSS_GRC on the same instance, writing this file's
-- DDL there while Flyway records the migration as applied. Five files carried it (V3, V4, V5, V7,
-- V10), all below the V292 baseline, which is why neither environment ever executed them.

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'tprm')
    EXEC('CREATE SCHEMA tprm');
GO

-- ── Vendors ───────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'tprm' AND TABLE_NAME = 'vendors')
BEGIN
    CREATE TABLE tprm.vendors (
        id                  BIGINT IDENTITY(1,1)  NOT NULL,
        external_id         UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        organisation_id     BIGINT                NOT NULL,
        vendor_name         NVARCHAR(255)         NOT NULL,
        vendor_name_ar      NVARCHAR(255)         NULL,
        vendor_type         NVARCHAR(100)         NULL,
        country             NVARCHAR(100)         NULL,
        contact_name        NVARCHAR(255)         NULL,
        contact_email       NVARCHAR(255)         NULL,
        risk_rating         NVARCHAR(20)          NOT NULL DEFAULT 'MEDIUM',
        status              NVARCHAR(50)          NOT NULL DEFAULT 'ACTIVE',
        contract_start_date DATE                  NULL,
        contract_end_date   DATE                  NULL,
        description         NVARCHAR(MAX)         NULL,
        -- Audit columns
        created_by          BIGINT                NULL,
        created_at          DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2(7)          NULL,
        is_deleted          BIT                   NOT NULL DEFAULT 0,
        deleted_by          BIGINT                NULL,
        row_version         ROWVERSION            NOT NULL,
        CONSTRAINT PK_vendors PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_vendors_risk_rating CHECK (risk_rating IN ('CRITICAL','HIGH','MEDIUM','LOW')),
        CONSTRAINT CK_vendors_status CHECK (status IN ('ACTIVE','INACTIVE','PENDING_REVIEW','TERMINATED'))
    );
    PRINT 'Created tprm.vendors';
END
GO

-- ── Vendor Assessments ────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'tprm' AND TABLE_NAME = 'vendor_assessments')
BEGIN
    CREATE TABLE tprm.vendor_assessments (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        external_id     UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        vendor_id       BIGINT                NOT NULL,
        assessment_date DATE                  NOT NULL,
        assessor        NVARCHAR(255)         NULL,
        risk_score      DECIMAL(5,2)          NULL,
        risk_rating     NVARCHAR(20)          NOT NULL DEFAULT 'MEDIUM',
        status          NVARCHAR(50)          NOT NULL DEFAULT 'PLANNED',
        findings        NVARCHAR(MAX)         NULL,
        next_review_date DATE                 NULL,
        -- Audit columns
        created_at      DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted      BIT                   NOT NULL DEFAULT 0,
        deleted_by      BIGINT                NULL,
        row_version     ROWVERSION            NOT NULL,
        CONSTRAINT PK_vendor_assessments PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_va_risk_rating CHECK (risk_rating IN ('CRITICAL','HIGH','MEDIUM','LOW')),
        CONSTRAINT CK_va_status CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED')),
        CONSTRAINT FK_va_vendor FOREIGN KEY (vendor_id) REFERENCES tprm.vendors(id)
    );
    PRINT 'Created tprm.vendor_assessments';
END
GO

-- ── Seed Data ─────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM tprm.vendors WHERE organisation_id = 1)
BEGIN
    INSERT INTO tprm.vendors (organisation_id, vendor_name, vendor_name_ar, vendor_type, country, risk_rating, status, contract_end_date, description, created_by, created_at)
    VALUES
    (1, N'Cisco Systems',         N'سيسكو سيستمز',         N'IT Infrastructure', N'US',  'HIGH',   'ACTIVE', '2026-12-31', N'Network security hardware and software', 1, SYSUTCDATETIME()),
    (1, N'Microsoft Azure',       N'مايكروسوفت أزور',       N'Cloud Services',   N'US',  'HIGH',   'ACTIVE', '2027-03-31', N'Cloud infrastructure and Microsoft 365', 1, SYSUTCDATETIME()),
    (1, N'SAP SE',                N'إس إيه بي',             N'ERP',              N'DE',  'MEDIUM', 'ACTIVE', '2026-09-30', N'Enterprise resource planning system',    1, SYSUTCDATETIME()),
    (1, N'Deloitte & Touche',     N'ديلويت وتوش',           N'Consulting',       N'SA',  'LOW',    'ACTIVE', '2026-06-30', N'External auditing and advisory services', 1, SYSUTCDATETIME()),
    (1, N'Aramex',                N'أرامكس',                N'Logistics',        N'SA',  'LOW',    'ACTIVE', '2026-12-31', N'Courier and logistics services',         1, SYSUTCDATETIME());
END
GO

PRINT 'Sprint 3: TPRM module tables and seed data ready.';
GO
