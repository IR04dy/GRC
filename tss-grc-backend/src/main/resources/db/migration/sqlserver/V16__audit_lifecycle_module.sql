-- V16 — Audit Lifecycle module backend tables.
-- Mirrors lib/auditLifecycle.ts. Same shape as V15 risk lifecycle.

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'audit_lc')
    EXEC('CREATE SCHEMA audit_lc');
GO

-- ─── Annual audit plan ──────────────────────────────────────────────────────
CREATE TABLE audit_lc.annual_plans (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id     NVARCHAR(64)  NOT NULL,                    -- AP-2026
    organisation_id BIGINT        NOT NULL,
    [year]          INT           NOT NULL,
    status          NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',    -- DRAFT/AC_REVIEW/AC_APPROVED/EXECUTING/CLOSED
    last_updated    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    ac_approved_by  NVARCHAR(255),
    ac_approved_at  DATETIME2,
    q1_codes        NVARCHAR(MAX),                              -- comma-separated engagement codes
    q2_codes        NVARCHAR(MAX),
    q3_codes        NVARCHAR(MAX),
    q4_codes        NVARCHAR(MAX),
    budget_hours    INT           NOT NULL DEFAULT 0,
    ac_notes_en     NVARCHAR(MAX),
    ac_notes_ar     NVARCHAR(MAX),
    is_deleted      SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ap_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_ap_org_year ON audit_lc.annual_plans (organisation_id, [year], is_deleted);
GO

-- ─── Working papers ─────────────────────────────────────────────────────────
CREATE TABLE audit_lc.working_papers (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                  -- WP-2026-001
    organisation_id   BIGINT        NOT NULL,
    engagement_code   NVARCHAR(64)  NOT NULL,
    type              NVARCHAR(20)  NOT NULL,                  -- TEST/INTERVIEW/OBSERVATION/DOCUMENT_REVIEW/WALKTHROUGH
    ref_number        NVARCHAR(20)  NOT NULL,                  -- WP-001
    title_en          NVARCHAR(500) NOT NULL,
    title_ar          NVARCHAR(500) NOT NULL,
    author            NVARCHAR(255) NOT NULL,
    created_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    status            NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PEER_REVIEW/APPROVED/REJECTED
    reviewer          NVARCHAR(255),
    reviewed_at       DATETIME2,
    procedure_en      NVARCHAR(MAX),
    procedure_ar      NVARCHAR(MAX),
    conclusion_en     NVARCHAR(MAX),
    conclusion_ar     NVARCHAR(MAX),
    controls_covered  NVARCHAR(MAX),                            -- comma-sep control codes
    hours             DECIMAL(6,2)  NOT NULL DEFAULT 0,
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_wp_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_wp_engagement ON audit_lc.working_papers (organisation_id, engagement_code, is_deleted);
CREATE INDEX ix_wp_status     ON audit_lc.working_papers (organisation_id, status, is_deleted);
GO

-- ─── Findings — primary state-machine entity for the audit lifecycle ───────
CREATE TABLE audit_lc.findings (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(64)  NOT NULL,                 -- FIN-2026-001
    organisation_id     BIGINT        NOT NULL,
    engagement_code     NVARCHAR(64)  NOT NULL,
    ref_number          NVARCHAR(20)  NOT NULL,                 -- FIN-001
    title_en            NVARCHAR(500) NOT NULL,
    title_ar            NVARCHAR(500) NOT NULL,
    severity            NVARCHAR(20)  NOT NULL,                 -- CRITICAL/HIGH/MEDIUM/LOW/OBSERVATION
    status              NVARCHAR(30)  NOT NULL DEFAULT 'OPEN',  -- OPEN/MGMT_RESPONSE/IN_REMEDIATION/AWAITING_VERIFICATION/CLOSED
    control_crosswalk   NVARCHAR(MAX),                           -- JSON [{framework, controlCode}]
    condition_en        NVARCHAR(MAX) NOT NULL,
    condition_ar        NVARCHAR(MAX) NOT NULL,
    criteria_en         NVARCHAR(MAX),
    criteria_ar         NVARCHAR(MAX),
    cause_en            NVARCHAR(MAX),
    cause_ar            NVARCHAR(MAX),
    recommendation_en   NVARCHAR(MAX),
    recommendation_ar   NVARCHAR(MAX),
    mgmt_response_en    NVARCHAR(MAX),
    mgmt_response_ar    NVARCHAR(MAX),
    mgmt_response_author NVARCHAR(255),
    mgmt_accepts        BIT,
    target_date         DATE,
    owner_role          NVARCHAR(255),
    raised_at           DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    capa_ids            NVARCHAR(MAX),                           -- comma-sep CAPA external ids
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_fin_engagement ON audit_lc.findings (organisation_id, engagement_code, is_deleted);
CREATE INDEX ix_fin_status     ON audit_lc.findings (organisation_id, status, is_deleted);
CREATE INDEX ix_fin_severity   ON audit_lc.findings (organisation_id, severity, is_deleted);
GO

-- ─── CAPA tasks ─────────────────────────────────────────────────────────────
CREATE TABLE audit_lc.capa_tasks (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(64)  NOT NULL,               -- CAPA-2026-001
    organisation_id       BIGINT        NOT NULL,
    finding_id            NVARCHAR(64)  NOT NULL,               -- finding external_id
    kind                  NVARCHAR(15)  NOT NULL,               -- CORRECTIVE/PREVENTIVE
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500) NOT NULL,
    owner_role            NVARCHAR(255) NOT NULL,
    target_date           DATE          NOT NULL,
    status                NVARCHAR(30)  NOT NULL DEFAULT 'OPEN',-- OPEN/IN_PROGRESS/AWAITING_VERIFICATION/CLOSED/OVERDUE
    closed_at             DATETIME2,
    verification_notes_en NVARCHAR(MAX),
    verification_notes_ar NVARCHAR(MAX),
    evidence_ref          NVARCHAR(64),
    is_deleted            SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_capa_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_capa_finding ON audit_lc.capa_tasks (organisation_id, finding_id, is_deleted);
CREATE INDEX ix_capa_status  ON audit_lc.capa_tasks (organisation_id, status, is_deleted);
GO
