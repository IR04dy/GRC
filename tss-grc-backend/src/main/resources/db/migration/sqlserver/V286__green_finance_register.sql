-- V286 (Green-Finance uplift G1 — plan §4/§7): Green Sukuk & sustainable-finance
-- register: instruments, eligible projects, the use-of-proceeds ledger, and the
-- post-issuance report snapshots (report endpoints land in G2; table ships now).
-- BaseEntity conventions throughout (org-scoped, soft-delete, ROWVERSION).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'green_instruments' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.green_instruments (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT           NOT NULL,
        external_id             UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        instrument_code         NVARCHAR(40)     NOT NULL,
        instrument_type         NVARCHAR(30)     NOT NULL,   -- GREEN_SUKUK | GREEN_BOND | SUSTAINABILITY_SUKUK | GREEN_LOAN | SLL
        name_en                 NVARCHAR(300)    NOT NULL,
        name_ar                 NVARCHAR(300)    NULL,
        isin                    NVARCHAR(20)     NULL,
        listing                 NVARCHAR(60)     NULL,       -- e.g. Saudi Exchange / LSE / unlisted
        issue_date              DATE             NULL,
        maturity_date           DATE             NULL,
        amount_sar_mn           DECIMAL(18,4)    NOT NULL,
        issuance_costs_sar_mn   DECIMAL(18,4)    NOT NULL DEFAULT 0,
        currency                NVARCHAR(10)     NOT NULL DEFAULT 'SAR',
        framework_reference     NVARCHAR(300)    NULL,       -- own framework / sovereign GFF
        shariah_structure       NVARCHAR(20)     NOT NULL DEFAULT 'NA',  -- MUDARABA|WAKALA|IJARA|HYBRID|NA
        shariah_approval_ref    NVARCHAR(200)    NULL,
        shariah_approved        BIT              NOT NULL DEFAULT 0,
        external_reviewer       NVARCHAR(200)    NULL,
        spo_rating              NVARCHAR(60)     NULL,
        icma_categories         NVARCHAR(500)    NULL,       -- CSV of eligible-category codes
        profit_rate_pct         DECIMAL(9,4)     NULL,
        lookback_months         INT              NOT NULL DEFAULT 24,
        status                  NVARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT -> SHARIAH_REVIEW -> APPROVED -> ISSUED -> REPORTING -> MATURED

        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_green_instr_org_code ON sust.green_instruments(organisation_id, instrument_code) WHERE is_deleted = 0;
    CREATE INDEX ix_green_instr_org_status ON sust.green_instruments(organisation_id, status);
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'green_projects' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.green_projects (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT           NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        project_code                NVARCHAR(40)     NOT NULL,
        name_en                     NVARCHAR(300)    NOT NULL,
        name_ar                     NVARCHAR(300)    NULL,
        category                    NVARCHAR(40)     NOT NULL,  -- ICMA/CCE: RENEWABLE_ENERGY|ENERGY_EFFICIENCY|CLEAN_TRANSPORT|GREEN_BUILDINGS|WATER|WASTE|CCUS|OTHER_ELIGIBLE
        description_en              NVARCHAR(MAX)    NULL,
        description_ar              NVARCHAR(MAX)    NULL,
        total_cost_sar_mn           DECIMAL(18,4)    NOT NULL,
        expected_tco2e_avoided_yr   DECIMAL(18,4)    NULL,
        actual_tco2e_avoided_yr     DECIMAL(18,4)    NULL,
        expected_mwh_renewable_yr   DECIMAL(18,4)    NULL,
        actual_mwh_renewable_yr     DECIMAL(18,4)    NULL,
        expected_water_m3_saved_yr  DECIMAL(18,4)    NULL,
        actual_water_m3_saved_yr    DECIMAL(18,4)    NULL,
        status                      NVARCHAR(20)     NOT NULL DEFAULT 'PLANNED', -- PLANNED|ACTIVE|COMPLETED

        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_green_proj_org_code ON sust.green_projects(organisation_id, project_code) WHERE is_deleted = 0;
    CREATE INDEX ix_green_proj_org_cat ON sust.green_projects(organisation_id, category);
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'instrument_allocations' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.instrument_allocations (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT           NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        instrument_id       BIGINT           NOT NULL,       -- soft FK sust.green_instruments
        project_id          BIGINT           NOT NULL,       -- soft FK sust.green_projects
        amount_sar_mn       DECIMAL(18,4)    NOT NULL,
        allocation_date     DATE             NOT NULL,
        financing_type      NVARCHAR(15)     NOT NULL DEFAULT 'NEW_FINANCING', -- NEW_FINANCING|REFINANCING
        status              NVARCHAR(15)     NOT NULL DEFAULT 'PROPOSED',      -- PROPOSED|APPROVED|REJECTED
        proposed_by         BIGINT           NULL,
        approved_by         BIGINT           NULL,
        decided_at          DATETIME2        NULL,
        rejection_reason    NVARCHAR(500)    NULL,

        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE INDEX ix_alloc_org_instr ON sust.instrument_allocations(organisation_id, instrument_id, status);
    CREATE INDEX ix_alloc_org_proj  ON sust.instrument_allocations(organisation_id, project_id);
END

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'allocation_reports' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.allocation_reports (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT           NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        instrument_id               BIGINT           NOT NULL,   -- soft FK
        period_year                 INT              NOT NULL,
        report_type                 NVARCHAR(20)     NOT NULL DEFAULT 'COMBINED', -- ALLOCATION|IMPACT|COMBINED
        allocated_sar_mn            DECIMAL(18,4)    NULL,
        unallocated_sar_mn          DECIMAL(18,4)    NULL,
        allocation_ratio_pct        DECIMAL(9,4)     NULL,
        refinancing_share_pct       DECIMAL(9,4)     NULL,
        tco2e_avoided_yr            DECIMAL(18,4)    NULL,
        mwh_renewable_yr            DECIMAL(18,4)    NULL,
        water_m3_saved_yr           DECIMAL(18,4)    NULL,
        intensity_tco2e_per_sar_mn  DECIMAL(18,6)    NULL,
        narrative_en                NVARCHAR(MAX)    NULL,
        narrative_ar                NVARCHAR(MAX)    NULL,
        status                      NVARCHAR(15)     NOT NULL DEFAULT 'DRAFT',   -- DRAFT|APPROVED|PUBLISHED
        approved_by                 BIGINT           NULL,
        published_at                DATETIME2        NULL,

        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE UNIQUE INDEX ux_allocrep_org_instr_period ON sust.allocation_reports(organisation_id, instrument_id, period_year, report_type) WHERE is_deleted = 0;
END

-- ── SUST APPROVE catalogue + grants (plan §5.2) ─────────────────────────────
-- Gap found while wiring maker-checker: SUST_APPROVE was not even CATALOGUED
-- (V272 gave SUST READ/WRITE/DELETE only), so the entire approve path was
-- PLATFORM_ADMIN-only. Catalogue it, then grant per the plan matrix:
-- SUSTAINABILITY_OFFICER R/W/A (function owner approves), GRC_ADMINISTRATOR A.
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT 'SUST', 'APPROVE', 'SUST_APPROVE', 'Approve sustainability records (green-finance allocations, reports)'
WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p WHERE p.module = 'SUST' AND p.action = 'APPROVE');

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('SUSTAINABILITY_OFFICER', 'SUST', 'READ'),
    ('SUSTAINABILITY_OFFICER', 'SUST', 'WRITE'),
    ('SUSTAINABILITY_OFFICER', 'SUST', 'APPROVE'),
    ('GRC_ADMINISTRATOR',      'SUST', 'APPROVE')
) m(role_name, module, action)
JOIN iam.roles r       ON r.role_name = m.role_name AND r.is_deleted = 0
JOIN iam.permissions p ON p.module = m.module AND p.action = m.action
WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
