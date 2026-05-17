-- V95: Generic audit log — Framework Intersection plan slice F7.
--
-- Until now, audit lives inside individual modules (ndi.audit_events,
-- workflow.action_logs). This migration adds the platform-wide audit
-- ledger that any module can write to, mirroring prompt §29's
-- requirements: who/when/what/where/before/after, plus correlation
-- so a single business action (e.g. a submit) can be traced across
-- modules.
--
-- The module-specific tables stay where they are — domain rules
-- (NDI's Rule-0 placeholder banner, workflow's action stream)
-- depend on their own shapes. Going forward, services should
-- publish ALSO to platform.audit_log so the unified viewer at
-- /admin/audit-log/unified shows every event.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'audit_log' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.audit_log (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        external_id             UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- who
        user_id                 BIGINT          NULL,
        user_username           NVARCHAR(120)   NULL,
        user_role               NVARCHAR(60)    NULL,

        -- what
        action_type             NVARCHAR(40)    NOT NULL,
        -- CREATE | UPDATE | DELETE | TRANSITION | LOGIN | LOGOUT |
        -- EXPORT | IMPORT | EVIDENCE_UPLOAD | EVIDENCE_APPROVE |
        -- DIGITAL_SIGNATURE_REQUEST | DIGITAL_SIGNATURE_COMPLETE |
        -- ESCALATION | NOTIFICATION | RULE_FIRED | CUSTOM
        entity_type             NVARCHAR(120)   NULL,
        -- Java class short name (e.g. 'RiskAssessment', 'Control')
        entity_id               BIGINT          NULL,
        entity_external_id      NVARCHAR(60)    NULL,
        entity_code             NVARCHAR(120)   NULL,   -- friendly key (control_code, risk_code, …)

        -- payload — JSON blobs so the schema doesn't need to evolve
        -- per entity shape. Keep them under 8K each in normal use;
        -- NVARCHAR(MAX) accepts large bodies if a caller really needs.
        old_value_json          NVARCHAR(MAX)   NULL,
        new_value_json          NVARCHAR(MAX)   NULL,

        -- workflow context (the most common cross-cutting concern)
        workflow_status_before  NVARCHAR(40)    NULL,
        workflow_status_after   NVARCHAR(40)    NULL,

        -- where / how
        ip_address              NVARCHAR(60)    NULL,
        user_agent              NVARCHAR(500)   NULL,
        source_module           NVARCHAR(60)    NULL,   -- 'risk', 'cyber', 'policy' …
        source_endpoint         NVARCHAR(255)   NULL,   -- HTTP method + path

        -- linkage
        correlation_id          NVARCHAR(60)    NULL,
        parent_audit_id         BIGINT          NULL,
        digital_signature_ref   NVARCHAR(120)   NULL,
        severity                NVARCHAR(20)    NOT NULL DEFAULT 'INFO',
        -- INFO | WARN | ERROR
        message                 NVARCHAR(MAX)   NULL,

        created_at              DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT ck_audit_severity CHECK (severity IN ('INFO','WARN','ERROR')),
        CONSTRAINT fk_audit_parent FOREIGN KEY (parent_audit_id) REFERENCES platform.audit_log(id)
    );

    CREATE INDEX ix_audit_org_time         ON platform.audit_log(organisation_id, created_at DESC);
    CREATE INDEX ix_audit_entity           ON platform.audit_log(entity_type, entity_id);
    CREATE INDEX ix_audit_user             ON platform.audit_log(user_id, created_at DESC);
    CREATE INDEX ix_audit_action           ON platform.audit_log(action_type, created_at DESC);
    CREATE INDEX ix_audit_correlation      ON platform.audit_log(correlation_id);
    CREATE INDEX ix_audit_org_severity     ON platform.audit_log(organisation_id, severity, created_at DESC);
END
GO

-- Seed a single bootstrap row so the table is never empty on first
-- read (which the frontend's "no events" empty-state would otherwise
-- mask the fact that auditing is wired but quiet).
IF NOT EXISTS (SELECT 1 FROM platform.audit_log WHERE organisation_id = 1 AND action_type = 'CUSTOM' AND entity_code = 'V95-BOOTSTRAP')
BEGIN
    INSERT INTO platform.audit_log
      (organisation_id, action_type, entity_type, entity_code, source_module, severity, message)
    VALUES
      (1, 'CUSTOM', 'AuditLog', 'V95-BOOTSTRAP', 'platform', 'INFO',
       'platform.audit_log table created via V95 migration. Domain services begin publishing here in F7+.');
END
GO
