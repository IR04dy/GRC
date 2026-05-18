-- V110: BCM plan review attestation — audit Tier-2 #10.
--
-- The audit (§4 row 9) flagged quarterly attestation campaigns as the
-- single most efficient way to keep BCPs/DRPs fresh. The platform has a
-- frontend AttestationCampaignsPage but no backend engine, and the BCP/DRP
-- plans had `next_review_date` but nothing wrote to it after creation —
-- nothing nagged when a plan went stale.
--
-- This migration adds two things:
--   1) Two columns on bcm.bcp_plans: last_attested_at + last_attested_by_name
--      so the builder UI can show "last reviewed by X on date Y".
--   2) A new audit-trail table bcm.plan_review_attestations capturing every
--      attestation event. Per-plan history; not aggregated into the parent
--      so an auditor can reconstruct "who said the plan was current and when".
--
-- Workflow surface lives in BcpPlanService:
--   - attest(planId, notes)  — owner confirms still-current, advances
--                              next_review_date by exercise_frequency_months_*
--   - reviewQueue(daysAhead) — list plans whose next_review_date falls in the
--                              window. Frontend shows this as KPI + list on
--                              the plans page.
--
-- Plus one F8 rule seed: BCM_PLAN_REVIEW_OVERDUE → ESCALATE to plan_owner.
-- The scheduler that flips plans into "overdue" state is a follow-up; for
-- now the controller surfaces a /sweep endpoint admins can call manually.

-- ─── 1. Augment bcm.bcp_plans ──────────────────────────────────────
IF COL_LENGTH('bcm.bcp_plans', 'last_attested_at') IS NULL
BEGIN
    ALTER TABLE bcm.bcp_plans ADD last_attested_at        DATETIME2     NULL;
    ALTER TABLE bcm.bcp_plans ADD last_attested_by_name   NVARCHAR(255) NULL;
    ALTER TABLE bcm.bcp_plans ADD last_attested_by_id     BIGINT        NULL;
    ALTER TABLE bcm.bcp_plans ADD review_state            NVARCHAR(20)  NOT NULL DEFAULT 'CURRENT';
    -- CURRENT | DUE_SOON | OVERDUE — denormalised from next_review_date so
    -- queries don't have to recompute the bucket every time.
END
GO

-- ─── 2. Attestation history table ──────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'plan_review_attestations' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.plan_review_attestations (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        plan_id                     BIGINT          NOT NULL,
        plan_version                INT             NOT NULL,

        attested_at                 DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        attested_by_id              BIGINT          NULL,
        attested_by_name            NVARCHAR(255)   NOT NULL,
        attestation_outcome         NVARCHAR(20)    NOT NULL DEFAULT 'STILL_CURRENT',
        -- STILL_CURRENT — owner confirms no material change
        -- CHANGES_REQUIRED — owner flags content that needs an update
        -- REOPENED_TO_EDIT — owner already opened the plan for editing

        notes                       NVARCHAR(1000)  NULL,

        previous_next_review_date   DATE            NULL,
        new_next_review_date        DATE            NULL,

        is_deleted                  BIT             NOT NULL DEFAULT 0,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),

        CONSTRAINT FK_plan_review_attestations_plan
            FOREIGN KEY (plan_id) REFERENCES bcm.bcp_plans(id),
        CONSTRAINT ck_plan_review_outcome CHECK
            (attestation_outcome IN ('STILL_CURRENT','CHANGES_REQUIRED','REOPENED_TO_EDIT'))
    );
    CREATE INDEX IX_plan_review_attestations_plan_time
        ON bcm.plan_review_attestations(plan_id, attested_at DESC);
    CREATE INDEX IX_plan_review_attestations_org_outcome
        ON bcm.plan_review_attestations(organisation_id, attestation_outcome);
END
GO

-- ─── 3. F8 rule: plan review overdue → escalate ───────────────────
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = 1 AND rule_code = 'BCM-T12')
BEGIN
    INSERT INTO platform.automation_rules
      (organisation_id, rule_code, name_en, name_ar, description_en,
       trigger_event, condition_json, action_json, priority, source)
    VALUES
      (1, 'BCM-T12',
       'BCM plan review overdue → escalate to plan owner',
       N'تأخر مراجعة خطة BCM → تصعيد لمالك الخطة',
       'When a BCP/DRP next_review_date passes without an attestation, escalate to the plan owner so the plan stays current per ISO 22301 §7.4.',
       'BCM_PLAN_REVIEW_OVERDUE',
       NULL,
       '{"type":"ESCALATE","to_role":"BCM_MANAGER","after_minutes":0}',
       10, 'BCM_MODULE');
END
GO

-- ─── 4. Backfill: set review_state on existing rows + a demo
--                  attestation entry on each PUBLISHED plan ─────────
-- Initial review_state classification using a 30-day "due soon" window.
UPDATE bcm.bcp_plans
   SET review_state = CASE
            WHEN next_review_date IS NULL                              THEN 'CURRENT'
            WHEN next_review_date < CAST(SYSUTCDATETIME() AS DATE)     THEN 'OVERDUE'
            WHEN next_review_date < DATEADD(day, 30, CAST(SYSUTCDATETIME() AS DATE)) THEN 'DUE_SOON'
            ELSE 'CURRENT'
       END
 WHERE organisation_id = 1;
GO

-- Seed one historical attestation on each PUBLISHED plan so the builder
-- page renders a populated "Last reviewed" timestamp.
IF NOT EXISTS (SELECT 1 FROM bcm.plan_review_attestations WHERE organisation_id = 1)
BEGIN
    INSERT INTO bcm.plan_review_attestations
      (organisation_id, plan_id, plan_version, attested_at, attested_by_name,
       attestation_outcome, notes, previous_next_review_date, new_next_review_date)
    SELECT 1, p.id, p.current_version,
           DATEADD(month, -2, SYSUTCDATETIME()),
           'Khalid Al-Mutairi',
           'STILL_CURRENT',
           'Quarterly attestation completed; no material change since last review.',
           DATEADD(month, -2, p.next_review_date),
           p.next_review_date
      FROM bcm.bcp_plans p
     WHERE p.organisation_id = 1
       AND p.status = 'PUBLISHED';

    UPDATE p
       SET p.last_attested_at      = a.attested_at,
           p.last_attested_by_name = a.attested_by_name
      FROM bcm.bcp_plans p
      JOIN bcm.plan_review_attestations a
        ON a.plan_id = p.id
       AND a.organisation_id = p.organisation_id
     WHERE p.organisation_id = 1;
END
GO

PRINT 'V110 plan review attestation complete.';
GO
