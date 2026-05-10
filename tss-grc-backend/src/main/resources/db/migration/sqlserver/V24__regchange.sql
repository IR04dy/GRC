-- V24 — Regulatory Change tracker — Sprint 4 §3.2 #3.
--
-- Tracks Saudi-regulatory updates (NCA, SAMA, CMA, SDAIA, CITC, ZATCA, MoH,
-- HRSD, MoC, Nazaha) from initial publication through impact assessment to
-- implementation. State machine:
--   NEW → ASSESSED → IMPACT_ANALYSED → IN_IMPLEMENTATION → IMPLEMENTED → CLOSED
--   any non-terminal → DEPRIORITISED (with reason)

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'regchange')
    EXEC('CREATE SCHEMA regchange');
GO

CREATE TABLE regchange.changes (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                   -- RC-2026-001
    organisation_id   BIGINT        NOT NULL,
    -- Source regulator + reference:
    regulator         NVARCHAR(20)  NOT NULL,                   -- NCA/SAMA/CMA/SDAIA/CITC/ZATCA/MoH/HRSD/MoC/NAZAHA/OTHER
    citation          NVARCHAR(255),                              -- e.g. "ECC-2:2024 §3.4.1", "PDPL Art.29"
    title_en          NVARCHAR(500) NOT NULL,
    title_ar          NVARCHAR(500) NOT NULL,
    summary_en        NVARCHAR(MAX),
    summary_ar        NVARCHAR(MAX),
    source_url        NVARCHAR(500),
    published_at      DATE,
    effective_at      DATE,                                       -- when org must comply
    status            NVARCHAR(30)  NOT NULL DEFAULT 'NEW',
    -- Assessment
    assessed_at       DATETIME2,
    assessed_by       NVARCHAR(255),
    applicability     NVARCHAR(20),                               -- APPLICABLE / NOT_APPLICABLE / PARTIAL
    impact_score      INT,                                        -- 1..5
    impact_summary_en NVARCHAR(MAX),
    impact_summary_ar NVARCHAR(MAX),
    -- Implementation
    implementation_owner NVARCHAR(255),
    implementation_started_at DATETIME2,
    implementation_target_date DATE,
    implementation_completed_at DATETIME2,
    -- Linked artefacts (comma-sep)
    linked_controls   NVARCHAR(MAX),
    linked_capa_ids   NVARCHAR(MAX),
    linked_evidence   NVARCHAR(MAX),
    deprioritisation_reason_en NVARCHAR(MAX),
    closed_at         DATETIME2,
    created_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_rc_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_rc_org_status   ON regchange.changes (organisation_id, status, is_deleted);
CREATE INDEX ix_rc_regulator    ON regchange.changes (organisation_id, regulator, is_deleted);
CREATE INDEX ix_rc_effective    ON regchange.changes (organisation_id, effective_at, is_deleted);
GO
