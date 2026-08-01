-- ============================================================================
-- TSS GRC Platform — Sprint 2: Incident Management Table
-- Schema: cyber
-- ============================================================================

-- ⚠ `USE TSS_GRC;` removed 2026-08-01 (F2-76). Flyway is already connected to the target
-- database, so the statement was redundant here and actively wrong anywhere else: on a customer
-- database with any other name it either fails outright, or — worse — SUCCEEDS by switching to some
-- unrelated database that happens to be called TSS_GRC on the same instance, writing this file's
-- DDL there while Flyway records the migration as applied. Five files carried it (V3, V4, V5, V7,
-- V10), all below the V292 baseline, which is why neither environment ever executed them.

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'cyber' AND TABLE_NAME = 'incidents')
BEGIN
    CREATE TABLE cyber.incidents (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        organisation_id BIGINT                NOT NULL,
        title           NVARCHAR(255)         NOT NULL,
        severity        NVARCHAR(20)          NOT NULL,
        category        NVARCHAR(50)          NULL,
        status          NVARCHAR(50)          NOT NULL DEFAULT 'OPEN',
        assignee        NVARCHAR(255)         NULL,
        discovery_date  DATE                  NULL,
        resolution_date DATE                  NULL,
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
        CONSTRAINT PK_incidents PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_incidents_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
        CONSTRAINT CK_incidents_status   CHECK (status IN ('OPEN','INVESTIGATING','RESOLVED','CLOSED')),
        CONSTRAINT CK_incidents_category CHECK (category IS NULL OR category IN (
            'CYBER','DATA_BREACH','INFRASTRUCTURE','ACCESS_CONTROL','OTHER'))
    );
    PRINT 'Created cyber.incidents';
END
GO

PRINT 'Sprint 2: Incident module table ready.';
GO
