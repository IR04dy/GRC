-- ============================================================================
-- V256 — S-O: Vendor ESG surveillance (real backend + TPRM link)
--
-- Promotes the frontend-only "Vendor ESG survey" mock (tss-grc-ui-v2
-- lib/esg.ts VENDOR_SURVEYS) to a real, tenant-scoped, auditable backend
-- surveillance workflow bound to the REAL vendor register (tprm.vendors):
--
--   DRAFT --send--> SENT --record-response--> RESPONDED --accept--> ACCEPTED
--                    |                           |
--                    | (past due, no response)   +--reject--> REJECTED
--                    +--sweep-overdue--> ESCALATED
--
--   * The survey links to a tprm.vendors row (FK, validated same-tenant in the
--     service) — no more free-typed vendor names divorced from the register.
--   * ESG-data requests reuse the generic evidence engine: evidence_item_id is
--     a real FK into evidence.evidence_items (S-C pattern), so a vendor's
--     uploaded ESG data carries a SHA-256 + review/approve lifecycle.
--   * Non-response escalation (ESCALATED) and a REJECTED response each open a
--     sust.corrective_action via CorrectiveActionService.autoOpen with the new
--     VENDOR_ESG source_type (widened below), deduped by (source_type,
--     source_ref) exactly like the other S-D auto-CAPA triggers.
--
-- Idempotent throughout (guards on sys.tables / sys.foreign_keys /
-- sys.check_constraints) — safe to re-run on the Python-applied dev DB
-- (Flyway is OFF in dev). Applies on top of an already-bootstrapped sust +
-- tprm + evidence schema.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sust' AND t.name = 'vendor_esg_survey')
BEGIN
    CREATE TABLE sust.vendor_esg_survey (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id       BIGINT        NOT NULL,
        external_id           NVARCHAR(64)  NULL,

        -- Link to the REAL vendor register (tprm.vendors). Validated
        -- same-tenant in the service; the SCM supplier id is an optional
        -- secondary link for tenants that run procurement in module/scm.
        tprm_vendor_id        BIGINT        NOT NULL,
        scm_supplier_id       BIGINT        NULL,
        vendor_name_en        NVARCHAR(255) NOT NULL,   -- snapshot at send time
        vendor_name_ar        NVARCHAR(255) NULL,

        reporting_year        SMALLINT      NOT NULL,

        status                NVARCHAR(30)  NOT NULL CONSTRAINT df_ves_status DEFAULT 'DRAFT',

        spend_sar_k           DECIMAL(18,2) NULL,       -- annual spend, SAR thousands (Scope-3 weighting)

        due_date              DATE          NULL,       -- response deadline (drives escalation)
        sent_at               DATETIME2     NULL,
        responded_at          DATETIME2     NULL,
        reviewed_at           DATETIME2     NULL,

        -- Vendor-reported ESG data (populated on record-response).
        reported_scope12      DECIMAL(18,2) NULL,       -- tCO2e (Scope 1+2)
        renewable_pct         INT           NULL,
        saudization_pct       INT           NULL,
        has_net_zero_target   BIT           NULL,
        iso14001              BIT           NULL,

        esg_score             DECIMAL(5,2)  NULL,       -- COMPUTED 0..100 (never free-typed)

        -- Real evidence FK (S-C) + auto-CAPA back-link (S-D).
        evidence_item_id      BIGINT        NULL,
        linked_capa_id        BIGINT        NULL,

        reviewer_name         NVARCHAR(255) NULL,
        reviewer_email        NVARCHAR(255) NULL,
        reject_reason         NVARCHAR(MAX) NULL,
        notes                 NVARCHAR(MAX) NULL,

        is_deleted            BIT           NOT NULL CONSTRAINT df_ves_is_deleted DEFAULT 0,
        created_at            DATETIME2     NOT NULL CONSTRAINT df_ves_created_at DEFAULT SYSUTCDATETIME(),
        created_by            NVARCHAR(100) NULL,
        updated_at            DATETIME2     NULL,
        updated_by            NVARCHAR(100) NULL,

        CONSTRAINT ck_ves_status CHECK (status IN (
            N'DRAFT', N'SENT', N'RESPONDED', N'ACCEPTED', N'REJECTED', N'ESCALATED')),
        CONSTRAINT FK_ves_tprm_vendor
            FOREIGN KEY (tprm_vendor_id)   REFERENCES tprm.vendors(id),
        CONSTRAINT FK_ves_evidence_item
            FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL
    );
END
GO

-- One live survey per (tenant, vendor, reporting year). FILTERED on is_deleted=0
-- so a soft-deleted survey does not block re-surveying the same vendor next cycle.
IF OBJECT_ID('sust.vendor_esg_survey') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_ves_org_vendor_year' AND object_id = OBJECT_ID('sust.vendor_esg_survey'))
    CREATE UNIQUE INDEX uq_ves_org_vendor_year
        ON sust.vendor_esg_survey (organisation_id, tprm_vendor_id, reporting_year)
        WHERE is_deleted = 0;
GO

IF OBJECT_ID('sust.vendor_esg_survey') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ves_org_status' AND object_id = OBJECT_ID('sust.vendor_esg_survey'))
    CREATE INDEX ix_ves_org_status ON sust.vendor_esg_survey (organisation_id, status);
GO

IF OBJECT_ID('sust.vendor_esg_survey') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_ves_org_year' AND object_id = OBJECT_ID('sust.vendor_esg_survey'))
    CREATE INDEX ix_ves_org_year ON sust.vendor_esg_survey (organisation_id, reporting_year);
GO

-- Widen the auto-CAPA source_type CHECK to admit VENDOR_ESG (non-response
-- escalation + rejected response). Additive — every previously-allowed value
-- stays valid. Drop-and-recreate so it is idempotent (mirrors V251).
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_ca_source'
             AND parent_object_id = OBJECT_ID('sust.corrective_action'))
    ALTER TABLE sust.corrective_action DROP CONSTRAINT ck_ca_source;
GO
ALTER TABLE sust.corrective_action ADD CONSTRAINT ck_ca_source CHECK (source_type IN (
    N'CLIMATE_RISK', N'GHG_RECORD', N'ANNEX_GAP', N'CMA_DISCLOSURE',
    N'TCFD_ITEM', N'SGI_COMMITMENT', N'EVIDENCE', N'VENDOR_ESG', N'MANUAL'));
GO
