-- V25 — Audit Engagements module — Sprint 4 §3.2 #4.
--
-- Internal audit engagement lifecycle. State machine:
--   PLANNING → FIELDWORK → REPORTING → FOLLOW_UP → CLOSED
--   any active state → CANCELLED
-- Aligned with IIA Standards 2200 (Engagement Planning), 2300 (Performing
-- the Engagement), 2400 (Communicating Results), 2500 (Monitoring Progress).

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'audit_eng')
    EXEC('CREATE SCHEMA audit_eng');
GO

CREATE TABLE audit_eng.engagements (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(64)  NOT NULL,                -- ENG-2026-001
    organisation_id       BIGINT        NOT NULL,
    engagement_code       NVARCHAR(32),                            -- IA-Q1-2026-01
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500) NOT NULL,
    objective_en          NVARCHAR(MAX),
    objective_ar          NVARCHAR(MAX),
    scope_en              NVARCHAR(MAX),
    scope_ar              NVARCHAR(MAX),
    audit_type            NVARCHAR(30)  NOT NULL DEFAULT 'INTERNAL',  -- INTERNAL/EXTERNAL/REGULATORY/CO_SOURCED
    framework             NVARCHAR(50),                            -- ISO27001/NCA-ECC/SAMA-CSF/PDPL/...
    auditor_firm          NVARCHAR(255),
    lead_auditor          NVARCHAR(255) NOT NULL,
    audit_team            NVARCHAR(MAX),                            -- comma-sep names
    audit_committee_chair NVARCHAR(255),
    status                NVARCHAR(30)  NOT NULL DEFAULT 'PLANNING',
    -- Timeline
    planning_started_at   DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    fieldwork_started_at  DATETIME2,
    fieldwork_ended_at    DATETIME2,
    reporting_started_at  DATETIME2,
    report_issued_at      DATETIME2,
    follow_up_started_at  DATETIME2,
    closed_at             DATETIME2,
    cancelled_at          DATETIME2,
    cancellation_reason_en NVARCHAR(MAX),
    -- Budget & metrics
    budget_hours          INT,
    actual_hours          INT,
    findings_count        INT           NOT NULL DEFAULT 0,
    -- Linked artefacts
    linked_finding_ids    NVARCHAR(MAX),                            -- comma-sep audit_lc.findings.external_id
    annual_plan_id        NVARCHAR(64),                              -- audit_lc.annual_plans.external_id
    is_deleted            SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_eng_external UNIQUE (organisation_id, external_id),
    CONSTRAINT uk_eng_code     UNIQUE (organisation_id, engagement_code)
);
CREATE INDEX ix_eng_org_status ON audit_eng.engagements (organisation_id, status, is_deleted);
CREATE INDEX ix_eng_lead       ON audit_eng.engagements (organisation_id, lead_auditor, is_deleted);
CREATE INDEX ix_eng_framework  ON audit_eng.engagements (organisation_id, framework, is_deleted);
GO
