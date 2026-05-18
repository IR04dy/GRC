-- V107: BCP/DRP plan builder — multi-section entities + version lock (audit T1.4b).
--
-- Replaces the 6-field BcmPlan placeholder with a structured plan composed
-- of named sections (ISO 22301 §8.4.2). One plan = one row in bcm.bcp_plans
-- (parent) + N rows in bcm.bcp_sections (one per ISO section) + N rows in
-- bcm.bcp_versions (snapshots taken at every approve / publish so an
-- auditor can diff v3 vs v1).
--
-- Same shape serves BCP (planType=BCP) and DRP (planType=DRP) — the only
-- difference is the section catalog the UI presents (handled in the wizard,
-- not the database). Tables are sector-neutral.

-- ─── Plans (parent) ──────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bcp_plans' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bcp_plans (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        plan_code           NVARCHAR(50)    NOT NULL,   -- e.g. BCP-2026-000123

        title_en            NVARCHAR(500)   NOT NULL,
        title_ar            NVARCHAR(500)   NULL,

        -- BCP | DRP — distinct flavours, identical shape
        plan_type           NVARCHAR(20)    NOT NULL DEFAULT 'BCP',

        -- Provenance — where this plan was generated from
        source_bia_id       BIGINT          NULL,   -- soft FK to bcm.bia_assessments
        service_id          BIGINT          NULL,   -- scope: critical service this plan covers
        process_id          BIGINT          NULL,

        -- Recovery objectives — typically copied from the source BIA at
        -- generation time, then frozen with the plan version.
        rto_hours           INT             NULL,
        rpo_hours           INT             NULL,
        mtpd_hours          INT             NULL,

        -- Ownership
        plan_owner_id       BIGINT          NULL,
        response_team_role  NVARCHAR(100)   NULL,

        -- Status lifecycle
        --   DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED → ARCHIVED
        --   DRAFT → CANCELLED (short-circuit for plans that won't ship)
        --   PUBLISHED → SUPERSEDED (when a newer version takes over)
        status              NVARCHAR(20)    NOT NULL DEFAULT 'DRAFT',

        current_version     INT             NOT NULL DEFAULT 1,

        approved_by         NVARCHAR(255)   NULL,
        approved_at         DATETIME2       NULL,
        published_at        DATETIME2       NULL,

        last_exercised_at   DATETIME2       NULL,
        next_review_date    DATE            NULL,

        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NULL,
        created_by          BIGINT          NULL,
        updated_by          BIGINT          NULL,
        is_deleted          BIT             NOT NULL DEFAULT 0,
        deleted_at          DATETIME2       NULL,
        deleted_by          BIGINT          NULL,
        row_version         ROWVERSION,

        CONSTRAINT ck_bcp_plan_type CHECK (plan_type IN ('BCP', 'DRP')),
        CONSTRAINT ck_bcp_status    CHECK (status IN
            ('DRAFT','UNDER_REVIEW','APPROVED','PUBLISHED','ARCHIVED','SUPERSEDED','CANCELLED'))
    );
    CREATE UNIQUE INDEX UX_bcp_plans_org_code   ON bcm.bcp_plans(organisation_id, plan_code) WHERE is_deleted = 0;
    CREATE INDEX        IX_bcp_plans_org_status ON bcm.bcp_plans(organisation_id, status)    WHERE is_deleted = 0;
    CREATE INDEX        IX_bcp_plans_org_bia    ON bcm.bcp_plans(organisation_id, source_bia_id) WHERE is_deleted = 0;
    CREATE INDEX        IX_bcp_plans_org_type   ON bcm.bcp_plans(organisation_id, plan_type)  WHERE is_deleted = 0;
END
GO

-- ─── Sections ────────────────────────────────────────────────────
-- One row per ISO 22301 §8.4.2 named section. Order is fixed at creation
-- time so the section navigator UI is stable. Body is NVARCHAR(MAX) so
-- richer content (HTML / Markdown) can be stored; the dev UI uses
-- plain-text plus simple markdown for now.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bcp_sections' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bcp_sections (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        plan_id             BIGINT          NOT NULL,

        section_key         NVARCHAR(50)    NOT NULL,
        -- overview | scope | roles | activation_criteria | recovery_priorities
        -- | recovery_steps | comms_plan | manual_workaround | resources
        -- | supplier_escalation | return_to_normal | appendices
        section_title_en    NVARCHAR(255)   NOT NULL,
        section_title_ar    NVARCHAR(255)   NULL,
        section_order       INT             NOT NULL,

        body_en             NVARCHAR(MAX)   NULL,
        body_ar             NVARCHAR(MAX)   NULL,

        last_edited_by      NVARCHAR(255)   NULL,
        last_edited_at      DATETIME2       NULL,

        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NULL,
        is_deleted          BIT             NOT NULL DEFAULT 0,

        CONSTRAINT FK_bcp_sections_plan FOREIGN KEY (plan_id) REFERENCES bcm.bcp_plans(id)
    );
    CREATE UNIQUE INDEX UX_bcp_sections_plan_key
        ON bcm.bcp_sections(plan_id, section_key) WHERE is_deleted = 0;
    CREATE INDEX        IX_bcp_sections_plan_order
        ON bcm.bcp_sections(plan_id, section_order);
END
GO

-- ─── Versions ───────────────────────────────────────────────────
-- Append-only snapshot of every approved version. body_json holds the
-- full section payload at snapshot time so an auditor can diff v3 vs v1
-- without reconstructing history from edits.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bcp_versions' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bcp_versions (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        plan_id             BIGINT          NOT NULL,

        version_number      INT             NOT NULL,
        body_json           NVARCHAR(MAX)   NOT NULL,
        --   Shape: {"plan": {...header fields...},
        --           "sections": [{section_key, title_en, title_ar, body_en, body_ar}, ...]}

        approved_by         NVARCHAR(255)   NULL,
        approved_at         DATETIME2       NULL,
        change_summary      NVARCHAR(1000)  NULL,

        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted          BIT             NOT NULL DEFAULT 0,

        CONSTRAINT FK_bcp_versions_plan FOREIGN KEY (plan_id) REFERENCES bcm.bcp_plans(id)
    );
    CREATE UNIQUE INDEX UX_bcp_versions_plan_number
        ON bcm.bcp_versions(plan_id, version_number) WHERE is_deleted = 0;
END
GO
