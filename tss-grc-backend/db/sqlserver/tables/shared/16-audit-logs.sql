-- ============================================================================
-- TSS GRC Platform - Audit Logs Table
-- Schema: audit
-- Purpose: Immutable event log for all user and system actions
-- Notes:
--   - This table is APPEND-ONLY. No updates or soft deletes are permitted.
--   - For production, consider partitioning by event_timestamp using monthly
--     or quarterly ranges on filegroup FG_Audit for optimal query performance
--     and simplified data lifecycle management (partition switching for archive).
--   - Example partition function:
--       CREATE PARTITION FUNCTION pf_audit_monthly (DATETIME2)
--       AS RANGE RIGHT FOR VALUES ('2026-01-01', '2026-02-01', ...);
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('audit.audit_logs', 'U') IS NOT NULL
    DROP TABLE audit.audit_logs;
GO

CREATE TABLE audit.audit_logs
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    event_timestamp DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    user_id         BIGINT                          NULL,
    username        NVARCHAR(200)                   NULL,
    session_id      NVARCHAR(200)                   NULL,
    org_id          BIGINT                          NULL,
    module_code     NVARCHAR(50)                    NULL,
    entity_type     NVARCHAR(200)                   NULL,
    entity_id       BIGINT                          NULL,
    action          NVARCHAR(50)                    NOT NULL,
    old_values      NVARCHAR(MAX)                   NULL,
    new_values      NVARCHAR(MAX)                   NULL,
    ip_address      NVARCHAR(50)                    NULL,
    user_agent      NVARCHAR(500)                   NULL,
    correlation_id  NVARCHAR(100)                   NULL,
    additional_info NVARCHAR(MAX)                   NULL,
    status          NVARCHAR(50)                    NOT NULL    DEFAULT N'SUCCESS',

    CONSTRAINT PK_audit_logs PRIMARY KEY CLUSTERED (id),

    CONSTRAINT CK_audit_logs_action CHECK (action IN (
        N'CREATE', N'UPDATE', N'DELETE', N'READ',
        N'LOGIN', N'LOGOUT', N'APPROVE', N'REJECT',
        N'EXPORT', N'IMPORT', N'CONFIGURE', N'LOCK', N'UNLOCK'
    ))
)
ON [FG_Audit];
GO

PRINT 'Table audit.audit_logs created successfully.';
GO
