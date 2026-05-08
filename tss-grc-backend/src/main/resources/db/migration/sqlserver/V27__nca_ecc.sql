-- V27 — NCA ECC Annual Filing module — Sprint 4 §3.2 #6.
--
-- Saudi-specific differentiator: NCA Essential Cybersecurity Controls (ECC)
-- annual filing workflow. Organisations subject to NCA supervision must file
-- a self-assessment annually via the NCA "Haseen" portal.
--
-- State machine:
--   DRAFT → PEER_REVIEW → CISO_APPROVAL → SUBMITTED → ACKNOWLEDGED
--   any non-terminal → CANCELLED

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'nca_ecc')
    EXEC('CREATE SCHEMA nca_ecc');
GO

CREATE TABLE nca_ecc.filings (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                    -- NCA-2026-001
    organisation_id   BIGINT        NOT NULL,
    fiscal_year       INT           NOT NULL,                    -- 2026
    framework_version NVARCHAR(20)  NOT NULL DEFAULT 'ECC-2:2024',
    title_en          NVARCHAR(500),
    title_ar          NVARCHAR(500),
    -- Per-domain maturity scores (1-5). 5 NCA ECC domains:
    -- 1. Cybersecurity Governance
    -- 2. Cybersecurity Defense
    -- 3. Cybersecurity Resilience
    -- 4. Third-Party Cybersecurity
    -- 5. Industrial Control Systems Cybersecurity (when applicable)
    domain1_score     INT,
    domain2_score     INT,
    domain3_score     INT,
    domain4_score     INT,
    domain5_score     INT,
    overall_score     DECIMAL(4,2),                                -- computed average
    -- Detailed responses (JSON: domain → subdomain → control → maturity + evidence)
    responses_json    NVARCHAR(MAX),
    gap_summary_en    NVARCHAR(MAX),
    gap_summary_ar    NVARCHAR(MAX),
    capa_count        INT           NOT NULL DEFAULT 0,            -- linked CAPA tasks
    -- Lifecycle
    status            NVARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    drafted_by        NVARCHAR(255) NOT NULL,
    drafted_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    peer_review_started_at DATETIME2,
    peer_reviewer     NVARCHAR(255),
    peer_review_completed_at DATETIME2,
    peer_review_comments_en NVARCHAR(MAX),
    ciso_approval_at  DATETIME2,
    ciso_approver     NVARCHAR(255),
    submitted_at      DATETIME2,
    submission_reference NVARCHAR(100),                            -- Haseen portal reference
    acknowledged_at   DATETIME2,
    cancelled_at      DATETIME2,
    cancellation_reason_en NVARCHAR(MAX),
    -- Linked artefacts
    linked_capa_ids   NVARCHAR(MAX),
    linked_evidence   NVARCHAR(MAX),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ncaecc_external UNIQUE (organisation_id, external_id),
    CONSTRAINT uk_ncaecc_year     UNIQUE (organisation_id, fiscal_year)
);
CREATE INDEX ix_ncaecc_org_status ON nca_ecc.filings (organisation_id, status, is_deleted);
GO
