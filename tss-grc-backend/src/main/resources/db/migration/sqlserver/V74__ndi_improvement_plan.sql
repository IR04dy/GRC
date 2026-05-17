-- V74 — NDI Improvement Plan (slice 9)
--
-- Aggregates corrective actions from slice 8 into a time-bound roadmap.
-- One plan per cycle (typically), each with many plan_items that
-- reference a CAPA + place it on a quarterly track with priority,
-- budget, and a sequence order.
--
-- Rule 10 (plan totals)   : plan totals (total_actions, completed_actions,
--                            progress_pct) are recomputed server-side from
--                            the underlying CAPA + plan_item state.
-- Rule 11 (sequence)      : sequence_order is unique within a plan and
--                            survives reordering operations.
-- Rule 12 (quarter)       : target_quarter format is Y####-Q# (e.g. Y2026-Q2).

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'improvement_plan' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.improvement_plan (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,
        external_id         NVARCHAR(64)  NULL,

        cycle_id            BIGINT        NOT NULL,
        plan_code           NVARCHAR(40)  NULL,

        name_en             NVARCHAR(255) NOT NULL,
        name_ar             NVARCHAR(255) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        period_start        DATE          NOT NULL,
        period_end          DATE          NOT NULL,

        owner_name          NVARCHAR(255) NULL,
        owner_email         NVARCHAR(255) NULL,
        approver_name       NVARCHAR(255) NULL,
        approved_at         DATETIME2     NULL,

        status              NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
                                          -- DRAFT | APPROVED | IN_FLIGHT | COMPLETED | SUSPENDED | ARCHIVED

        total_items         INT           NOT NULL DEFAULT 0,
        completed_items     INT           NOT NULL DEFAULT 0,
        progress_pct        INT           NOT NULL DEFAULT 0,

        budget_total        DECIMAL(20,2) NULL,
        budget_currency     NVARCHAR(10)  NOT NULL DEFAULT N'SAR',

        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_plan_status   CHECK (status IN
            (N'DRAFT', N'APPROVED', N'IN_FLIGHT', N'COMPLETED', N'SUSPENDED', N'ARCHIVED')),
        CONSTRAINT ck_ndi_plan_progress CHECK (progress_pct BETWEEN 0 AND 100),
        CONSTRAINT ck_ndi_plan_period   CHECK (period_end >= period_start)
    );

    CREATE INDEX ix_ndi_plan_org_cycle ON ndi.improvement_plan(organisation_id, cycle_id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'improvement_plan_item' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.improvement_plan_item (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT        NOT NULL,

        plan_id             BIGINT        NOT NULL,
        capa_id             BIGINT        NOT NULL,        -- ndi.corrective_action

        sequence_order      INT           NOT NULL DEFAULT 100,
        priority            NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',  -- CRITICAL | HIGH | MEDIUM | LOW
        target_quarter      NVARCHAR(10)  NULL,                       -- Y2026-Q2
        allocated_budget    DECIMAL(20,2) NULL,
        dependencies        NVARCHAR(500) NULL,                       -- pipe-separated capa_ids

        notes               NVARCHAR(MAX) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(100) NULL,
        updated_at          DATETIME2     NULL,
        updated_by          NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_pi_priority CHECK (priority IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW'))
    );

    CREATE INDEX ix_ndi_pi_plan     ON ndi.improvement_plan_item(plan_id);
    CREATE INDEX ix_ndi_pi_capa     ON ndi.improvement_plan_item(capa_id);
    CREATE UNIQUE INDEX ux_ndi_pi_plan_capa
        ON ndi.improvement_plan_item(plan_id, capa_id) WHERE is_deleted = 0;
END
GO

-- ─── Seed — one demo plan for cycle 1, all 7 CAPAs included ────────

IF NOT EXISTS (SELECT 1 FROM ndi.improvement_plan WHERE organisation_id = 1)
BEGIN
    DECLARE @plan_id BIGINT;

    INSERT INTO ndi.improvement_plan (
        organisation_id, cycle_id, plan_code,
        name_en, name_ar, description_en, description_ar,
        period_start, period_end, owner_name, status, budget_total, budget_currency
    )
    VALUES (
        1, 1, N'PLAN-2026-01',
        N'NDI Readiness Plan — 2026',
        N'خطة جاهزية نضيء — 2026',
        N'Time-bound plan of corrective actions targeting the gaps identified in the 2026 NDI readiness cycle.',
        N'خطة محددة المدة للإجراءات التصحيحية التي تستهدف الفجوات المُحدَّدة في دورة جاهزية نضيء 2026.',
        '2026-04-01', '2026-12-31',
        N'Chief Data Officer', N'IN_FLIGHT',
        2400000.00, N'SAR');

    SET @plan_id = SCOPE_IDENTITY();

    -- Add every CAPA from slice 8 as a plan item, mapped to a quarter
    -- + priority that mirrors the parent gap's severity.
    INSERT INTO ndi.improvement_plan_item (
        organisation_id, plan_id, capa_id, sequence_order, priority, target_quarter, allocated_budget
    )
    SELECT
        1,
        @plan_id,
        c.id,
        ROW_NUMBER() OVER (ORDER BY
            CASE g.severity WHEN N'CRITICAL' THEN 1 WHEN N'HIGH' THEN 2 WHEN N'MEDIUM' THEN 3 ELSE 4 END,
            c.target_date) * 10,
        CASE g.severity
            WHEN N'CRITICAL' THEN N'CRITICAL'
            WHEN N'HIGH'     THEN N'HIGH'
            ELSE N'MEDIUM'
        END,
        CASE
            WHEN c.target_date <= '2026-06-30' THEN N'Y2026-Q2'
            WHEN c.target_date <= '2026-09-30' THEN N'Y2026-Q3'
            ELSE                                    N'Y2026-Q4'
        END,
        CASE g.severity
            WHEN N'CRITICAL' THEN 500000.00
            WHEN N'HIGH'     THEN 350000.00
            ELSE                  200000.00
        END
    FROM ndi.corrective_action c
    INNER JOIN ndi.gap g ON g.id = c.gap_id
    WHERE c.is_deleted = 0 AND g.is_deleted = 0 AND g.cycle_id = 1;

    -- Backfill plan rollup from current CAPA state.
    UPDATE ndi.improvement_plan SET
        total_items     = (SELECT COUNT(*) FROM ndi.improvement_plan_item WHERE plan_id = @plan_id AND is_deleted = 0),
        completed_items = (
            SELECT COUNT(*)
            FROM ndi.improvement_plan_item pi
            INNER JOIN ndi.corrective_action c ON c.id = pi.capa_id
            WHERE pi.plan_id = @plan_id AND pi.is_deleted = 0 AND c.status = N'COMPLETED'
        ),
        progress_pct = (
            SELECT ISNULL(AVG(CAST(c.progress_pct AS INT)), 0)
            FROM ndi.improvement_plan_item pi
            INNER JOIN ndi.corrective_action c ON c.id = pi.capa_id
            WHERE pi.plan_id = @plan_id AND pi.is_deleted = 0
        )
    WHERE id = @plan_id;
END
GO
