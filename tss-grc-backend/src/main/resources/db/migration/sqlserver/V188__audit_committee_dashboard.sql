-- V188 — Audit Committee Dashboard (Sprint 11).
--
-- Regulatory driver:
--   • SAMA Corporate Governance Regulations §6 — Audit Committee
--     responsibilities require regular reporting on risk, internal
--     control, compliance, and findings. The Committee must include
--     independent non-executive directors and meet at least quarterly.
--   • CMA Listed-Companies Corporate Governance Code Article 54 —
--     Audit Committee must "review reports and disclosures relating
--     to internal control, risk management, compliance" and produce
--     a formal record of each meeting with attestations.
--   • Saudi Companies Law Art.103 — Audit Committee mandate.
--
-- This migration is a CURATION SLICE, not a new register. Live
-- dashboard data is computed on demand from existing module services
-- (risk, compliance, capa, incident, AI governance, climate,
-- obligations, CCM, vendor-BCP, 3LoD). A snapshot freezes that
-- computed view at a meeting moment so the Committee minutes have a
-- stable, signed record.
--
-- Two tables under a new `audit_committee` schema:
--
--   audit_committee.dashboard_snapshot      ← frozen meeting view
--   audit_committee.dashboard_attestation   ← signed attestations
--
-- Tamper-evidence: every attestation row stores a SHA-256 of
-- `attestation_text || user_id || ISO-8601(timestamp)` in
-- `signature_hash`. The auditor reproducing the hash from the
-- attestation text + user + timestamp confirms nobody rewrote
-- history post-meeting.
--
-- All tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- QUOTED_IDENTIFIER ON for filtered indexes.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'audit_committee') IS NULL EXEC(N'CREATE SCHEMA audit_committee');
GO

-- ─── 1. Dashboard snapshot ─────────────────────────────────────────
IF OBJECT_ID(N'audit_committee.dashboard_snapshot', N'U') IS NULL
BEGIN
    CREATE TABLE audit_committee.dashboard_snapshot (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- Human-readable code, e.g. AC-DASH-2026-Q2-MEETING. Unique
        -- per tenant within live rows.
        snapshot_code                 NVARCHAR(64)  NOT NULL,

        -- When the Committee will / did meet, distinct from
        -- generated_at (snapshot creation time — usually the morning
        -- of the meeting).
        meeting_date                  DATE          NOT NULL,
        -- QUARTERLY | ANNUAL | AD_HOC | OFFCYCLE
        meeting_type                  VARCHAR(20)   NOT NULL,

        generated_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        generated_by_user_id          BIGINT        NOT NULL,

        -- Frozen JSON document — entire dashboard payload at the
        -- moment of generation. Sections: risk, controls, findings,
        -- capa, ai, climate, obligations, incidents, vendorBcp,
        -- threeLines + a 'computedAt' ISO timestamp.
        kpis_json                     NVARCHAR(MAX) NOT NULL,

        -- Committee Chair's executive summary, added at finalize time.
        executive_summary_en          NVARCHAR(MAX) NULL,
        executive_summary_ar          NVARCHAR(MAX) NULL,

        -- JSON array of resolutions captured during the meeting:
        --   [{ id, recordedAt, recordedByUserId, textEn, textAr }, ...]
        committee_resolutions         NVARCHAR(MAX) NULL,

        is_finalized                  BIT           NOT NULL DEFAULT 0,
        finalized_at                  DATETIME2(3)  NULL,
        finalized_by_user_id          BIGINT        NULL,

        -- Audit + soft-delete.
        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT CK_ac_snap_meeting_type CHECK (meeting_type IN
            (N'QUARTERLY', N'ANNUAL', N'AD_HOC', N'OFFCYCLE'))
    );
END
GO

-- Filtered indexes — unique snapshot_code per tenant on live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_ac_snap_code' AND object_id = OBJECT_ID(N'audit_committee.dashboard_snapshot'))
    CREATE UNIQUE INDEX UQ_ac_snap_code ON audit_committee.dashboard_snapshot (organisation_id, snapshot_code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ac_snap_org_meeting' AND object_id = OBJECT_ID(N'audit_committee.dashboard_snapshot'))
    CREATE INDEX IX_ac_snap_org_meeting ON audit_committee.dashboard_snapshot (organisation_id, meeting_date DESC) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ac_snap_org_external' AND object_id = OBJECT_ID(N'audit_committee.dashboard_snapshot'))
    CREATE UNIQUE INDEX IX_ac_snap_org_external ON audit_committee.dashboard_snapshot (organisation_id, external_id) WHERE is_deleted = 0;
GO

-- ─── 2. Attestation ────────────────────────────────────────────────
IF OBJECT_ID(N'audit_committee.dashboard_attestation', N'U') IS NULL
BEGIN
    CREATE TABLE audit_committee.dashboard_attestation (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        snapshot_id                   BIGINT        NOT NULL,
        attested_by_user_id           BIGINT        NOT NULL,

        -- CHAIR | MEMBER | AUDIT_DIRECTOR | EXTERNAL_AUDITOR |
        -- COMPANY_SECRETARY
        attested_role                 VARCHAR(40)   NOT NULL,

        attestation_text_en           NVARCHAR(2000) NULL,
        attestation_text_ar           NVARCHAR(2000) NULL,

        attested_at                   DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),

        -- SHA-256 hex of attestation_text + user_id + ISO-8601(timestamp).
        -- Auditor reproducing the hash from the row's plaintext fields
        -- confirms the row was not rewritten post hoc.
        signature_hash                NVARCHAR(255) NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_ac_att_snapshot FOREIGN KEY (snapshot_id)
            REFERENCES audit_committee.dashboard_snapshot(id),
        CONSTRAINT CK_ac_att_role CHECK (attested_role IN
            (N'CHAIR', N'MEMBER', N'AUDIT_DIRECTOR',
             N'EXTERNAL_AUDITOR', N'COMPANY_SECRETARY'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ac_att_org_snap' AND object_id = OBJECT_ID(N'audit_committee.dashboard_attestation'))
    CREATE INDEX IX_ac_att_org_snap ON audit_committee.dashboard_attestation (organisation_id, snapshot_id, attested_at DESC) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ac_att_org_external' AND object_id = OBJECT_ID(N'audit_committee.dashboard_attestation'))
    CREATE UNIQUE INDEX IX_ac_att_org_external ON audit_committee.dashboard_attestation (organisation_id, external_id) WHERE is_deleted = 0;
GO
