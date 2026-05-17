-- V100: BCM-3 — lessons learned + gaps + corrective actions +
-- BCM-specific automation rule seeds.
--
-- Three new tables:
--   bcm.lessons_learned        — improvements from incidents, crises,
--                                exercises, audits.
--   bcm.gaps                   — BCM-specific gap register.
--   bcm.corrective_actions     — BCM-specific CAPA register (separate
--                                from the platform-wide capa.* schema
--                                so BCM keeps its own SLA + close-out
--                                rules; cross-link via gap_id +
--                                soft FK to capa.capa_tasks).
--
-- Plus four BCM-specific automation rules added to
-- platform.automation_rules with source='BCM_MODULE' so the F8
-- engine fires them when BCM-2 services publish their domain events.

-- ─── Lessons learned ──────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'lessons_learned' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.lessons_learned (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        cycle_id                 BIGINT          NULL,

        source_type              NVARCHAR(40)    NOT NULL,
        -- INCIDENT | CRISIS | EXERCISE | AUDIT | MANUAL
        source_id                BIGINT          NULL,

        title_en                 NVARCHAR(255)   NOT NULL,
        title_ar                 NVARCHAR(255)   NULL,
        description              NVARCHAR(MAX)   NULL,
        root_cause               NVARCHAR(MAX)   NULL,
        recommendation           NVARCHAR(MAX)   NULL,

        related_plan_id          BIGINT          NULL,
        related_risk_id          BIGINT          NULL,
        related_control_id       BIGINT          NULL,
        corrective_action_id     BIGINT          NULL,

        owner_id                 BIGINT          NULL,
        status                   NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | UNDER_REVIEW | APPROVED | REJECTED | CLOSED

        approved_by              NVARCHAR(120)   NULL,
        approved_at              DATETIME2(0)    NULL,

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT ck_lesson_source CHECK (source_type IN ('INCIDENT','CRISIS','EXERCISE','AUDIT','MANUAL')),
        CONSTRAINT ck_lesson_status CHECK (status IN ('DRAFT','UNDER_REVIEW','APPROVED','REJECTED','CLOSED'))
    );
    CREATE INDEX ix_lesson_org_status ON bcm.lessons_learned(organisation_id, status);
    CREATE INDEX ix_lesson_source     ON bcm.lessons_learned(source_type, source_id);
END
GO

-- ─── Gaps ─────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'gaps' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.gaps (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        cycle_id                 BIGINT          NULL,

        source                   NVARCHAR(40)    NOT NULL,
        -- BIA | BCP | DRP | EXERCISE | CALL_TREE | AUDIT | RISK | MANUAL
        related_record_type      NVARCHAR(120)   NULL,
        related_record_id        BIGINT          NULL,

        title_en                 NVARCHAR(255)   NOT NULL,
        title_ar                 NVARCHAR(255)   NULL,
        description              NVARCHAR(MAX)   NULL,
        severity                 NVARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',
        -- LOW | MEDIUM | HIGH | CRITICAL
        root_cause               NVARCHAR(MAX)   NULL,
        impact                   NVARCHAR(MAX)   NULL,
        related_risk_id          BIGINT          NULL,
        owner_id                 BIGINT          NULL,
        due_date                 DATE            NULL,

        status                   NVARCHAR(30)    NOT NULL DEFAULT 'OPEN',
        -- OPEN | ASSIGNED | IN_PROGRESS | UNDER_REVIEW | CLOSED | ACCEPTED_RISK | REOPENED

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT ck_gap_source   CHECK (source IN ('BIA','BCP','DRP','EXERCISE','CALL_TREE','AUDIT','RISK','MANUAL')),
        CONSTRAINT ck_gap_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
        CONSTRAINT ck_gap_status   CHECK (status IN
            ('OPEN','ASSIGNED','IN_PROGRESS','UNDER_REVIEW','CLOSED','ACCEPTED_RISK','REOPENED'))
    );
    CREATE INDEX ix_gap_org_status  ON bcm.gaps(organisation_id, status);
    CREATE INDEX ix_gap_severity    ON bcm.gaps(organisation_id, severity);
    CREATE INDEX ix_gap_due_date    ON bcm.gaps(due_date);
END
GO

-- ─── Corrective actions ───────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'corrective_actions' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.corrective_actions (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        cycle_id                 BIGINT          NULL,
        gap_id                   BIGINT          NULL,

        title_en                 NVARCHAR(255)   NOT NULL,
        title_ar                 NVARCHAR(255)   NULL,
        action_plan              NVARCHAR(MAX)   NULL,
        owner_id                 BIGINT          NULL,
        priority                 NVARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',
        -- LOW | MEDIUM | HIGH | CRITICAL
        due_date                 DATE            NULL,
        progress_percent         INT             NOT NULL DEFAULT 0,
        evidence_id              BIGINT          NULL,
        reviewer_id              BIGINT          NULL,
        closure_date             DATE            NULL,

        status                   NVARCHAR(30)    NOT NULL DEFAULT 'OPEN',
        -- OPEN | ASSIGNED | IN_PROGRESS | EVIDENCE_SUBMITTED | UNDER_REVIEW |
        -- CLOSED | OVERDUE | REOPENED

        platform_capa_id         BIGINT          NULL,   -- soft FK to capa.capa_tasks

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT ck_ca_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
        CONSTRAINT ck_ca_status   CHECK (status IN
            ('OPEN','ASSIGNED','IN_PROGRESS','EVIDENCE_SUBMITTED','UNDER_REVIEW','CLOSED','OVERDUE','REOPENED')),
        CONSTRAINT ck_ca_progress CHECK (progress_percent BETWEEN 0 AND 100)
    );
    CREATE INDEX ix_ca_org_status ON bcm.corrective_actions(organisation_id, status);
    CREATE INDEX ix_ca_gap        ON bcm.corrective_actions(gap_id);
    CREATE INDEX ix_ca_due_date   ON bcm.corrective_actions(due_date);
END
GO

-- ─── Seed BCM-specific automation rules ───────────────────────────
-- Hook into the F8 engine. These fire when BCM services publish
-- the corresponding DomainEvent. AR-T1 / AR-T2 / AR-T3 / AR-T4 use
-- BCM_ prefixed codes so they don't collide with the prompt-§14
-- rules already seeded as AR-01..AR-20.

IF NOT EXISTS (SELECT 1 FROM platform.automation_rules WHERE organisation_id = 1 AND rule_code = 'BCM-T1')
BEGIN
    INSERT INTO platform.automation_rules
      (organisation_id, rule_code, name_en, name_ar, description_en,
       trigger_event, condition_json, action_json, priority, source)
    VALUES
      (1, 'BCM-T1',
       'Call-tree test failed → corrective action',
       N'فشل اختبار شجرة الاتصال → إجراء تصحيحي',
       'When a call-tree test fails for any contact, raise a BCM corrective action (high priority) so the call tree is repaired before the next exercise.',
       'BCM_CALL_TREE_FAILED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"BcmCorrectiveAction","template":"CALL_TREE_REPAIR","priority":"HIGH"}',
       10, 'BCM_MODULE'),

      (1, 'BCM-T2',
       'BCM risk treatment planned → link to ERM if high/critical',
       N'تخطيط علاج خطر BCM → ربط بـERM إذا كان عالياً/حرجاً',
       'When a BCM risk moves into TREATMENT_PLANNED with residual score ≥ 12 (high/critical band), require an ERM risk linkage or formal acceptance.',
       'BCM_RISK_TREATMENT_PLANNED',
       '{"linked_erm":false}',
       '{"type":"AUDIT","severity":"WARN","message":"High/critical BCM risk lacks ERM linkage — require acceptance or link."}',
       10, 'BCM_MODULE'),

      (1, 'BCM-T3',
       'BCM exercise failed → gap + corrective action',
       N'فشل تمرين BCM → فجوة وإجراء تصحيحي',
       'When a BCM exercise is marked FAILED, automatically open a gap and a corrective action so the plan is fixed before the next exercise window.',
       'BCM_EXERCISE_FAILED',
       NULL,
       '{"type":"CREATE_RECORD","entity":"BcmGap","also":["BcmCorrectiveAction"]}',
       10, 'BCM_MODULE'),

      (1, 'BCM-T4',
       'BCM corrective action overdue → escalate',
       N'تأخر إجراء تصحيحي → تصعيد',
       'When a BCM corrective action passes its due_date without closure, escalate to the BCM manager.',
       'BCM_CAPA_OVERDUE',
       NULL,
       '{"type":"ESCALATE","to_role":"BCM_MANAGER","after_minutes":0}',
       15, 'BCM_MODULE');
END
GO
