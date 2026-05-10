-- V23 — CAPA (Corrective and Preventive Actions) module — Sprint 4 §3.2 #2.
--
-- Universal CAPA tracker. Linked to the originating issue/finding/incident
-- via (origin_type, origin_id). State machine:
--   OPEN → IN_PROGRESS → AWAITING_VERIFICATION → CLOSED
--   any active state → CANCELLED (with reason)

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'capa')
    EXEC('CREATE SCHEMA capa');
GO

CREATE TABLE capa.tasks (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                   -- CAPA-2026-001
    organisation_id   BIGINT        NOT NULL,
    -- Where this CAPA originated:
    origin_type       NVARCHAR(60)  NOT NULL,                   -- 'audit.finding', 'incident', 'cyber.vuln', etc.
    origin_id         NVARCHAR(120) NOT NULL,
    title_en          NVARCHAR(500) NOT NULL,
    title_ar          NVARCHAR(500) NOT NULL,
    description_en    NVARCHAR(MAX),
    description_ar    NVARCHAR(MAX),
    capa_type         NVARCHAR(20)  NOT NULL DEFAULT 'CORRECTIVE',  -- CORRECTIVE | PREVENTIVE
    severity          NVARCHAR(10),                                  -- CRITICAL/HIGH/MEDIUM/LOW
    owner             NVARCHAR(255) NOT NULL,
    target_date       DATE          NOT NULL,
    status            NVARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    started_at        DATETIME2,
    submitted_for_verification_at DATETIME2,
    verified_at       DATETIME2,
    verified_by       NVARCHAR(255),
    closed_at         DATETIME2,
    cancelled_at      DATETIME2,
    cancellation_reason_en NVARCHAR(MAX),
    progress_notes_en NVARCHAR(MAX),
    progress_notes_ar NVARCHAR(MAX),
    evidence_refs     NVARCHAR(MAX),                                  -- comma-sep file ids
    created_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_capa_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_capa_org_status ON capa.tasks (organisation_id, status, is_deleted);
CREATE INDEX ix_capa_origin     ON capa.tasks (organisation_id, origin_type, origin_id, is_deleted);
CREATE INDEX ix_capa_owner      ON capa.tasks (organisation_id, owner, status, is_deleted);
CREATE INDEX ix_capa_target     ON capa.tasks (organisation_id, target_date, status, is_deleted);
GO
