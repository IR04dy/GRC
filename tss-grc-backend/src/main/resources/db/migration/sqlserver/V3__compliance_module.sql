-- ============================================================================
-- TSS GRC Platform — Sprint 2: Compliance Module Tables
-- Schema: compliance
-- Run via Flyway (disabled in dev — run manually with sqlcmd)
-- ============================================================================

-- ⚠ `USE TSS_GRC;` removed 2026-08-01 (F2-76). Flyway is already connected to the target
-- database, so the statement was redundant here and actively wrong anywhere else: on a customer
-- database with any other name it either fails outright, or — worse — SUCCEEDS by switching to some
-- unrelated database that happens to be called TSS_GRC on the same instance, writing this file's
-- DDL there while Flyway records the migration as applied. Five files carried it (V3, V4, V5, V7,
-- V10), all below the V292 baseline, which is why neither environment ever executed them.

-- ── The schema this file's tables live in (F2-75, added 2026-08-01) ───────────
--
-- ⚠ This was missing for the entire life of the file, and it is the earliest reason
-- `flyway:migrate` cannot build this platform from empty. V1 declares thirteen schemas and
-- `compliance` is NOT one of them; the first CREATE SCHEMA compliance in the whole migration set
-- sits at V184 — 181 files AFTER the three tables below. So a genuinely new customer database dies
-- on the THIRD migration, with "The specified schema name 'compliance' either does not exist".
-- The plan's estimate was that an empty-database run "aborts no later than V47". It aborts at V3.
--
-- Why nobody hit it: dev runs with Flyway OFF and the Python bootstrap made the schema, while
-- production is baselined at V292 so V1-V291 are recorded as applied and never execute. Two schema
-- authorities, and neither of them runs this file. RC-A exactly.
--
-- Editing a shipped migration is normally forbidden — it breaks flyway:validate wherever the file
-- already ran. Verified safe here rather than assumed: prod's platform.flyway_schema_history holds
-- 45 rows of which exactly ONE is at or below the baseline (the '<< Flyway Baseline >>' marker at
-- V292), so no checksum for V1-V291 exists anywhere, and dev never ran Flyway at all.
--
-- Guarded and idempotent, in the same form V184 uses.
IF SCHEMA_ID('compliance') IS NULL EXEC('CREATE SCHEMA compliance');
GO

-- ── Controls ─────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'compliance' AND TABLE_NAME = 'controls')
BEGIN
    CREATE TABLE compliance.controls (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        organisation_id BIGINT                NOT NULL,
        code            NVARCHAR(50)          NOT NULL,
        name_en         NVARCHAR(255)         NOT NULL,
        name_ar         NVARCHAR(255)         NULL,
        framework       NVARCHAR(100)         NOT NULL,
        domain          NVARCHAR(100)         NULL,
        maturity_level  TINYINT               NOT NULL DEFAULT 1,
        status          NVARCHAR(50)          NOT NULL DEFAULT 'PLANNED',
        owner           NVARCHAR(255)         NULL,
        description     NVARCHAR(MAX)         NULL,
        -- Audit columns
        created_by      BIGINT                NULL,
        created_at      DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by      BIGINT                NULL,
        updated_at      DATETIME2(7)          NULL,
        is_deleted      BIT                   NOT NULL DEFAULT 0,
        deleted_at      DATETIME2(7)          NULL,
        deleted_by      BIGINT                NULL,
        row_version     ROWVERSION            NOT NULL,
        CONSTRAINT PK_controls PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_controls_maturity CHECK (maturity_level BETWEEN 1 AND 5),
        CONSTRAINT CK_controls_status CHECK (status IN ('IMPLEMENTED','PARTIAL','PLANNED','NOT_APPLICABLE'))
    );
    PRINT 'Created compliance.controls';
END
GO

-- ── Compliance Assessments ────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'compliance' AND TABLE_NAME = 'assessments')
BEGIN
    CREATE TABLE compliance.assessments (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        organisation_id BIGINT                NOT NULL,
        framework       NVARCHAR(100)         NOT NULL,
        period          NVARCHAR(100)         NULL,
        auditor         NVARCHAR(255)         NULL,
        score           DECIMAL(5,2)          NULL,
        status          NVARCHAR(50)          NOT NULL DEFAULT 'PLANNED',
        assessment_date DATE                  NULL,
        notes           NVARCHAR(MAX)         NULL,
        -- Audit columns
        created_by      BIGINT                NULL,
        created_at      DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by      BIGINT                NULL,
        updated_at      DATETIME2(7)          NULL,
        is_deleted      BIT                   NOT NULL DEFAULT 0,
        deleted_at      DATETIME2(7)          NULL,
        deleted_by      BIGINT                NULL,
        row_version     ROWVERSION            NOT NULL,
        CONSTRAINT PK_assessments PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_assessments_status CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED')),
        CONSTRAINT CK_assessments_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
    );
    PRINT 'Created compliance.assessments';
END
GO

-- ── Audit Findings ────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'compliance' AND TABLE_NAME = 'findings')
BEGIN
    CREATE TABLE compliance.findings (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        organisation_id BIGINT                NOT NULL,
        title           NVARCHAR(255)         NOT NULL,
        assessment_id   BIGINT                NULL,
        severity        NVARCHAR(20)          NOT NULL,
        owner           NVARCHAR(255)         NULL,
        capa            NVARCHAR(MAX)         NULL,
        due_date        DATE                  NULL,
        status          NVARCHAR(50)          NOT NULL DEFAULT 'OPEN',
        -- Audit columns
        created_by      BIGINT                NULL,
        created_at      DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by      BIGINT                NULL,
        updated_at      DATETIME2(7)          NULL,
        is_deleted      BIT                   NOT NULL DEFAULT 0,
        deleted_at      DATETIME2(7)          NULL,
        deleted_by      BIGINT                NULL,
        row_version     ROWVERSION            NOT NULL,
        CONSTRAINT PK_findings PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_findings_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
        CONSTRAINT CK_findings_status CHECK (status IN ('OPEN','IN_PROGRESS','REMEDIATED','VERIFIED','CLOSED')),
        CONSTRAINT FK_findings_assessment FOREIGN KEY (assessment_id)
            REFERENCES compliance.assessments(id)
    );
    PRINT 'Created compliance.findings';
END
GO

PRINT 'Sprint 2: Compliance module tables ready.';
GO
