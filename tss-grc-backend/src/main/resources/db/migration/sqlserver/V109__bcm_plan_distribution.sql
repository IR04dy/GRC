-- V109: Plan distribution + acknowledgement tracking — audit Tier-2 #11.
--
-- Why: ISO 22301 §7.4 and SAMA BCM §6.4 expect evidence that approved BCPs/
-- DRPs were distributed to every key person on the response team, and that
-- each recipient explicitly acknowledged the version they received. The audit
-- (§4 row 6) flagged this as a standard control GRC peers ship out of the
-- box. The platform had no entity for it.
--
-- Shape: one row per (plan, version, recipient). Recipients are typically
-- identified by user_id (FK to iam.users) when known, but the schema also
-- stores name + email so a row can be created for a contact who is not yet a
-- platform user (common when a BCP is shared with an external auditor or
-- non-user staffer).
--
-- Acknowledgement status:
--   PENDING       — distribution row exists, recipient hasn't acted
--   ACKNOWLEDGED  — recipient confirmed they read & accept the version
--   DECLINED      — recipient pushed back (rare; auditor evidence either way)
--   STALE         — auto-flipped by the service when a newer version of the
--                   plan is published; the recipient must re-acknowledge.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'plan_acknowledgements' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.plan_acknowledgements (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        external_id             UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        plan_id                 BIGINT          NOT NULL,
        plan_version            INT             NOT NULL,

        -- Recipient identity. user_id is preferred; name/email cover the
        -- external-recipient case (e.g. auditor, non-user staffer).
        recipient_user_id       BIGINT          NULL,
        recipient_name          NVARCHAR(255)   NOT NULL,
        recipient_email         NVARCHAR(255)   NULL,
        recipient_role          NVARCHAR(120)   NULL,

        -- Distribution event
        distributed_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        distributed_by_id       BIGINT          NULL,
        distributed_by_name     NVARCHAR(255)   NULL,
        distribution_channel    NVARCHAR(40)    NOT NULL DEFAULT 'EMAIL',
        -- EMAIL | PORTAL | IN_PERSON | PRINT | OTHER
        distribution_notes      NVARCHAR(1000)  NULL,

        -- Acknowledgement
        status                  NVARCHAR(20)    NOT NULL DEFAULT 'PENDING',
        -- PENDING | ACKNOWLEDGED | DECLINED | STALE
        acknowledged_at         DATETIME2       NULL,
        acknowledgement_notes   NVARCHAR(1000)  NULL,

        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2       NULL,
        is_deleted              BIT             NOT NULL DEFAULT 0,

        CONSTRAINT FK_plan_acks_plan FOREIGN KEY (plan_id) REFERENCES bcm.bcp_plans(id),
        CONSTRAINT ck_plan_ack_status   CHECK (status IN ('PENDING','ACKNOWLEDGED','DECLINED','STALE')),
        CONSTRAINT ck_plan_ack_channel  CHECK (distribution_channel IN ('EMAIL','PORTAL','IN_PERSON','PRINT','OTHER'))
    );
    CREATE INDEX IX_plan_acks_plan_ver   ON bcm.plan_acknowledgements(plan_id, plan_version);
    CREATE INDEX IX_plan_acks_org_status ON bcm.plan_acknowledgements(organisation_id, status);
    CREATE INDEX IX_plan_acks_recipient  ON bcm.plan_acknowledgements(organisation_id, recipient_user_id);

    -- Each (plan, version, recipient) pair is unique. A user re-distributing
    -- to the same person resets the existing row rather than creating a duplicate.
    CREATE UNIQUE INDEX UX_plan_acks_plan_ver_recipient
        ON bcm.plan_acknowledgements(plan_id, plan_version, recipient_user_id, recipient_email)
        WHERE is_deleted = 0;
END
GO

-- ─── Seed: demo distribution + acknowledgement rows ─────────────────
-- Two published BCPs (Core Banking + Treasury) × four typical recipients.
-- Demonstrates the four status states the UI must render.
IF NOT EXISTS (SELECT 1 FROM bcm.plan_acknowledgements WHERE organisation_id = 1)
BEGIN
    DECLARE @bcp1 BIGINT = (SELECT TOP 1 id FROM bcm.bcp_plans WHERE organisation_id = 1 AND plan_code = 'BCP-2026-000001');
    DECLARE @bcp2 BIGINT = (SELECT TOP 1 id FROM bcm.bcp_plans WHERE organisation_id = 1 AND plan_code = 'BCP-2026-000002');

    IF @bcp1 IS NOT NULL
    BEGIN
        INSERT INTO bcm.plan_acknowledgements
          (organisation_id, plan_id, plan_version, recipient_name, recipient_email,
           recipient_role, distributed_by_name, distribution_channel,
           status, acknowledged_at, acknowledgement_notes)
        VALUES
          (1, @bcp1, 2, 'Khalid Al-Mutairi',  'khalid@example.com',
           'BCM Manager',         'Khalid Al-Mutairi', 'EMAIL',
           'ACKNOWLEDGED', '2026-02-23 08:30:00', 'Acknowledged v2 after Q1 exercise findings.'),
          (1, @bcp1, 2, 'Sara Al-Anezi',      'sara@example.com',
           'IT Recovery Owner',   'Khalid Al-Mutairi', 'EMAIL',
           'ACKNOWLEDGED', '2026-02-23 10:15:00', NULL),
          (1, @bcp1, 2, 'Mohammed Al-Faraj',  'mohammed@example.com',
           'Core Banking Lead',   'Khalid Al-Mutairi', 'EMAIL',
           'PENDING', NULL, NULL),
          (1, @bcp1, 2, 'Layla Al-Harbi',     'layla@example.com',
           'Comms Lead',          'Khalid Al-Mutairi', 'EMAIL',
           'ACKNOWLEDGED', '2026-02-24 09:00:00', NULL),
          (1, @bcp1, 1, 'Mohammed Al-Faraj',  'mohammed@example.com',
           'Core Banking Lead',   'Khalid Al-Mutairi', 'EMAIL',
           'STALE', '2026-02-21 12:00:00', 'Superseded by v2.');
    END

    IF @bcp2 IS NOT NULL
    BEGIN
        INSERT INTO bcm.plan_acknowledgements
          (organisation_id, plan_id, plan_version, recipient_name, recipient_email,
           recipient_role, distributed_by_name, distribution_channel,
           status, acknowledged_at, acknowledgement_notes)
        VALUES
          (1, @bcp2, 1, 'Khalid Al-Mutairi',  'khalid@example.com',
           'BCM Manager',         'Khalid Al-Mutairi', 'EMAIL',
           'ACKNOWLEDGED', '2026-02-27 09:00:00', NULL),
          (1, @bcp2, 1, 'Yasser Al-Otaibi',   'yasser@example.com',
           'Treasury Lead',       'Khalid Al-Mutairi', 'EMAIL',
           'PENDING', NULL, NULL),
          (1, @bcp2, 1, 'Reem Al-Saif',       'reem@example.com',
           'Risk Lead',           'Khalid Al-Mutairi', 'EMAIL',
           'DECLINED', '2026-02-28 14:00:00', 'Requested clarification on Section 6 before acknowledging.');
    END
END
GO

PRINT 'V109 plan distribution table created and seeded.';
GO
