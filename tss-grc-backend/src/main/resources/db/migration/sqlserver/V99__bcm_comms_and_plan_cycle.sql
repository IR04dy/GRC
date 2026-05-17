-- V99: BCM-2 — communication contacts + cycle_id on legacy plans/crisis.
--
-- Two concerns in one migration:
--   1. New table bcm.communication_contacts for the call-tree
--      workflow (per BCM Improvement Plan §3.K).
--   2. Non-breaking ALTERs adding cycle_id (FK to BCM-1's
--      assessment_cycles) on the existing bcm.bcm_plans,
--      bcm.dr_plans, and bcm.crisis_events tables so future
--      sprints can group plans by cycle without another migration.
--      Existing rows leave cycle_id NULL — the column is optional.

-- ─── Communication contacts ───────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'communication_contacts' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.communication_contacts (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        name                     NVARCHAR(255)   NOT NULL,
        role_title               NVARCHAR(255)   NULL,
        department_id            BIGINT          NULL,

        primary_phone            NVARCHAR(40)    NULL,
        secondary_phone          NVARCHAR(40)    NULL,
        email                    NVARCHAR(255)   NULL,

        escalation_level         INT             NOT NULL DEFAULT 1,
        -- 1 = first responder; 2 = team lead; 3 = manager; 4 = executive; 5 = crisis manager
        related_plan_id          BIGINT          NULL,   -- soft FK to bcm.bcm_plans

        availability_status      NVARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
        -- AVAILABLE | ON_LEAVE | UNAVAILABLE | BACKUP
        last_tested_date         DATE            NULL,
        last_test_result         NVARCHAR(20)    NULL,
        -- PASSED | FAILED | NOT_REACHED

        status                   NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | INACTIVE | ARCHIVED

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT ck_contact_availability CHECK (availability_status IN
            ('AVAILABLE','ON_LEAVE','UNAVAILABLE','BACKUP')),
        CONSTRAINT ck_contact_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
        CONSTRAINT ck_contact_test_result CHECK (last_test_result IS NULL OR last_test_result IN
            ('PASSED','FAILED','NOT_REACHED')),
        CONSTRAINT ck_contact_escalation CHECK (escalation_level BETWEEN 1 AND 5)
    );
    CREATE INDEX ix_contact_org_status ON bcm.communication_contacts(organisation_id, status);
    CREATE INDEX ix_contact_plan       ON bcm.communication_contacts(related_plan_id);
    CREATE INDEX ix_contact_escalation ON bcm.communication_contacts(organisation_id, escalation_level);
END
GO

-- ─── ALTERs: add cycle_id (nullable, indexed) to legacy tables ────
IF OBJECT_ID('bcm.bcm_plans','U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bcm.bcm_plans') AND name = 'cycle_id')
BEGIN
    ALTER TABLE bcm.bcm_plans ADD cycle_id BIGINT NULL;
    CREATE INDEX ix_bcm_plan_cycle ON bcm.bcm_plans(cycle_id);
END
GO

IF OBJECT_ID('bcm.dr_plans','U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bcm.dr_plans') AND name = 'cycle_id')
BEGIN
    ALTER TABLE bcm.dr_plans ADD cycle_id BIGINT NULL;
    CREATE INDEX ix_dr_plan_cycle ON bcm.dr_plans(cycle_id);
END
GO

IF OBJECT_ID('bcm.crisis_events','U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bcm.crisis_events') AND name = 'cycle_id')
BEGIN
    ALTER TABLE bcm.crisis_events ADD cycle_id BIGINT NULL;
    CREATE INDEX ix_crisis_event_cycle ON bcm.crisis_events(cycle_id);
END
GO

-- ─── Seed two demo contacts so the call-tree UI has live rows ─────
IF NOT EXISTS (SELECT 1 FROM bcm.communication_contacts WHERE organisation_id = 1 AND email = 'bcm.manager@demo')
BEGIN
    INSERT INTO bcm.communication_contacts
      (organisation_id, name, role_title, primary_phone, email, escalation_level, status)
    VALUES
      (1, 'Demo BCM Manager', 'BCM Manager', '+966-555-1111', 'bcm.manager@demo', 3, 'ACTIVE'),
      (1, 'Demo Crisis Lead', 'Crisis Manager', '+966-555-2222', 'crisis.lead@demo', 5, 'ACTIVE');
END
GO
