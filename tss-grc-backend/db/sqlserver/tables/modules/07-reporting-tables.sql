-- ============================================================================
-- TSS GRC Platform - Reporting & Analytics Module Tables
-- Schema: reporting
-- Compatible: SQL Server 2019 Express Edition
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- reporting.report_definitions
-- ============================================================================
CREATE TABLE reporting.report_definitions (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    code                NVARCHAR(100)           NOT NULL,
    name_en             NVARCHAR(300)           NOT NULL,
    name_ar             NVARCHAR(300)           NULL,
    description_en      NVARCHAR(MAX)           NULL,
    module_code         NVARCHAR(50)            NULL,
    report_type         NVARCHAR(50)            NOT NULL DEFAULT 'TABULAR'
        CONSTRAINT CK_rd_type CHECK (report_type IN (
            'TABULAR','CHART','DASHBOARD','EXECUTIVE','AUDIT','COMPLIANCE')),
    query_source        NVARCHAR(MAX)           NULL,
    parameters_schema   NVARCHAR(MAX)           NULL,   -- JSON schema for parameters
    output_formats      NVARCHAR(200)           NULL,   -- CSV of: PDF,EXCEL,WORD
    display_order       INT                     NOT NULL DEFAULT 0,
    is_system           BIT                     NOT NULL DEFAULT 0,
    is_active           BIT                     NOT NULL DEFAULT 1,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_report_definitions PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_rd_code UNIQUE (code)
);
GO

-- ============================================================================
-- reporting.report_schedules
-- ============================================================================
CREATE TABLE reporting.report_schedules (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    report_def_id       BIGINT                  NOT NULL,
    org_id              BIGINT                  NOT NULL,
    name                NVARCHAR(300)           NOT NULL,
    cron_expression     NVARCHAR(100)           NOT NULL,  -- Standard cron: "0 8 * * 1"
    output_format       NVARCHAR(20)            NOT NULL DEFAULT 'PDF'
        CONSTRAINT CK_rs_format CHECK (output_format IN ('PDF','EXCEL','WORD')),
    parameters          NVARCHAR(MAX)           NULL,      -- JSON parameters for report
    recipients          NVARCHAR(MAX)           NOT NULL,  -- JSON array of email addresses
    subject_en          NVARCHAR(500)           NULL,
    subject_ar          NVARCHAR(500)           NULL,
    is_active           BIT                     NOT NULL DEFAULT 1,
    last_run_at         DATETIME2(7)            NULL,
    next_run_at         DATETIME2(7)            NULL,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_report_schedules PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_rs_report FOREIGN KEY (report_def_id) REFERENCES reporting.report_definitions(id),
    CONSTRAINT FK_rs_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

-- ============================================================================
-- reporting.report_executions
-- ============================================================================
CREATE TABLE reporting.report_executions (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    report_def_id       BIGINT                  NOT NULL,
    schedule_id         BIGINT                  NULL,
    org_id              BIGINT                  NOT NULL,
    requested_by        BIGINT                  NULL,
    executed_at         DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    completed_at        DATETIME2(7)            NULL,
    status              NVARCHAR(50)            NOT NULL DEFAULT 'PENDING'
        CONSTRAINT CK_re_status CHECK (status IN (
            'PENDING','IN_PROGRESS','COMPLETED','FAILED','CANCELLED')),
    output_format       NVARCHAR(20)            NULL,
    output_path         NVARCHAR(1000)          NULL,
    parameters_used     NVARCHAR(MAX)           NULL,     -- JSON
    duration_ms         BIGINT                  NULL,
    row_count           BIGINT                  NULL,
    error_message       NVARCHAR(MAX)           NULL,
    file_size_bytes     BIGINT                  NULL,
    is_deleted          BIT                     NOT NULL DEFAULT 0,
    deleted_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_report_executions PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_re_report FOREIGN KEY (report_def_id) REFERENCES reporting.report_definitions(id),
    CONSTRAINT FK_re_schedule FOREIGN KEY (schedule_id) REFERENCES reporting.report_schedules(id),
    CONSTRAINT FK_re_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id)
);
GO

-- ============================================================================
-- reporting.dashboard_configurations
-- ============================================================================
CREATE TABLE reporting.dashboard_configurations (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    org_id              BIGINT                  NULL,
    user_id             BIGINT                  NULL,
    role_code           NVARCHAR(100)           NULL,
    module_code         NVARCHAR(50)            NULL,
    name_en             NVARCHAR(200)           NOT NULL,
    name_ar             NVARCHAR(200)           NULL,
    layout              NVARCHAR(MAX)           NOT NULL,  -- JSON grid layout
    widgets             NVARCHAR(MAX)           NOT NULL,  -- JSON widget configurations
    is_default          BIT                     NOT NULL DEFAULT 0,
    is_system           BIT                     NOT NULL DEFAULT 0,
    created_by          BIGINT                  NULL,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT                  NULL,
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_dashboard_configurations PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_dc_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_dc_user FOREIGN KEY (user_id) REFERENCES iam.users(id)
);
GO

-- ============================================================================
-- reporting.saved_filters
-- ============================================================================
CREATE TABLE reporting.saved_filters (
    id                  BIGINT IDENTITY(1,1)    NOT NULL,
    user_id             BIGINT                  NOT NULL,
    module_code         NVARCHAR(50)            NOT NULL,
    entity_type         NVARCHAR(100)           NULL,
    name                NVARCHAR(200)           NOT NULL,
    filter_criteria     NVARCHAR(MAX)           NOT NULL,  -- JSON filter object
    is_default          BIT                     NOT NULL DEFAULT 0,
    is_shared           BIT                     NOT NULL DEFAULT 0,
    created_at          DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(7)            NULL,
    CONSTRAINT PK_saved_filters PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_sf_user FOREIGN KEY (user_id) REFERENCES iam.users(id)
);
GO

PRINT 'Reporting module tables created successfully.';
GO
