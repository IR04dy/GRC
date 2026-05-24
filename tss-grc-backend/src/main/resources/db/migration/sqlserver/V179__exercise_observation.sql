-- V179 — Slice 10-C: Observer Dashboard for exercise runs.
--
-- Builds on top of V175 (Slice 10-B's exercise runtime). Auditors, internal
-- audit, CISO, and regulator observers need a read-only live view of an
-- exercise as it unfolds — without the ability to dispatch injects or
-- transition state — so they can assess organisational performance in real
-- time and capture independent observations for post-exercise scoring.
--
-- SAMA BCF Annex C requires independent observers to score every full-scale
-- exercise. Today observers either crowd into the facilitator's console
-- (risking accidental writes) or watch over a participant's shoulder. This
-- slice gives them their own read-only dashboard with structured observation
-- capture and AAR feed-back.
--
-- Two new tables under the bcm schema:
--
--   1. bcm.exercise_observer_assignment
--        — the access-control gate. A facilitator / BCM_MANAGER assigns
--        a user as an observer on a specific run, tagged with the observer's
--        role (INTERNAL_AUDIT, EXTERNAL_AUDIT, COMPLIANCE_OBSERVER,
--        SAMA_LIAISON, NCA_LIAISON, BOARD_OBSERVER). Only assigned observers
--        can record observations on a run. Unique on (run, observer).
--
--   2. bcm.exercise_observation
--        — the actual structured observation. Type + severity + free-text in
--        the observer's preferred language, optionally linked to a specific
--        run-inject. is_flagged_for_aar drives After-Action Report inclusion
--        (the existing AAR draft hook in ExerciseRuntimeService.endRun() is
--        extended in this slice to pull flagged observations into the report).
--
-- All tables follow the platform conventions:
--   • organisation_id NOT NULL (multi-tenant, every read filters by tenant)
--   • Filtered indexes scoped on (organisation_id, …) with WHERE is_deleted = 0
--   • Standard BaseEntity audit + soft-delete + ROWVERSION columns
--   • QUOTED_IDENTIFIER ON, ANSI_NULLS ON
--   • Every CREATE / ALTER guarded by an existence check — idempotent on re-run

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ============================================================================
-- 1. bcm.exercise_observer_assignment — access-control gate for the run.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'exercise_observer_assignment' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_observer_assignment (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        exercise_run_id          BIGINT          NOT NULL,
        observer_user_id         BIGINT          NOT NULL,

        -- INTERNAL_AUDIT | EXTERNAL_AUDIT | COMPLIANCE_OBSERVER
        --   | SAMA_LIAISON | NCA_LIAISON | BOARD_OBSERVER
        observer_role            VARCHAR(40)     NOT NULL,

        assigned_by_user_id      BIGINT          NULL,
        assigned_at              DATETIME2       NOT NULL
            CONSTRAINT DF_exercise_observer_assignment_assigned_at DEFAULT SYSUTCDATETIME(),

        -- Standard audit + soft-delete + rowversion (BaseEntity contract)
        created_at               DATETIME2       NOT NULL
            CONSTRAINT DF_exercise_observer_assignment_created_at DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2       NULL,
        created_by               BIGINT          NULL,
        updated_by               BIGINT          NULL,
        is_deleted               BIT             NOT NULL
            CONSTRAINT DF_exercise_observer_assignment_is_deleted DEFAULT (0),
        deleted_at               DATETIME2       NULL,
        deleted_by               BIGINT          NULL,
        row_version              ROWVERSION,

        CONSTRAINT FK_exercise_observer_assignment_run
            FOREIGN KEY (exercise_run_id) REFERENCES bcm.exercise_run(id),
        CONSTRAINT CK_exercise_observer_assignment_role CHECK (observer_role IN
            ('INTERNAL_AUDIT','EXTERNAL_AUDIT','COMPLIANCE_OBSERVER',
             'SAMA_LIAISON','NCA_LIAISON','BOARD_OBSERVER'))
    );

    -- One active assignment per (run, observer) — re-assignment after soft
    -- delete works because the filtered unique index ignores deleted rows.
    CREATE UNIQUE INDEX UX_exercise_observer_assignment_run_observer
        ON bcm.exercise_observer_assignment(exercise_run_id, observer_user_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_observer_assignment_org_run
        ON bcm.exercise_observer_assignment(organisation_id, exercise_run_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_observer_assignment_org_observer
        ON bcm.exercise_observer_assignment(organisation_id, observer_user_id)
        WHERE is_deleted = 0;
END
GO

-- ============================================================================
-- 2. bcm.exercise_observation — structured observer captures.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'exercise_observation' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_observation (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        exercise_run_id          BIGINT          NOT NULL,
        observer_user_id         BIGINT          NOT NULL,

        -- NULL = observation is general (not tied to any specific inject).
        related_inject_id        BIGINT          NULL,

        -- The observer captures in their preferred language; the other
        -- column stays NULL until translated. EN is the canonical column
        -- for indexing / search.
        observation_text_en      NVARCHAR(2000)  NOT NULL,
        observation_text_ar      NVARCHAR(2000)  NULL,

        -- STRENGTH | GAP | RISK | COMMUNICATION | DECISION_QUALITY
        --   | ESCALATION | RECOVERY_TIME | COMPLIANCE | GENERAL
        observation_type         VARCHAR(40)     NOT NULL,

        -- INFO | MINOR | MODERATE | MAJOR | CRITICAL
        severity                 VARCHAR(20)     NOT NULL
            CONSTRAINT DF_exercise_observation_severity DEFAULT ('INFO'),

        recommendation_en        NVARCHAR(2000)  NULL,
        recommendation_ar        NVARCHAR(2000)  NULL,

        recorded_at              DATETIME2       NOT NULL
            CONSTRAINT DF_exercise_observation_recorded_at DEFAULT SYSUTCDATETIME(),
        -- Computed at write-time from run.started_at; stable across edits.
        time_offset_minutes      INT             NOT NULL
            CONSTRAINT DF_exercise_observation_offset DEFAULT (0),

        is_flagged_for_aar       BIT             NOT NULL
            CONSTRAINT DF_exercise_observation_flag DEFAULT (0),

        -- Standard audit + soft-delete + rowversion
        created_at               DATETIME2       NOT NULL
            CONSTRAINT DF_exercise_observation_created_at DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2       NULL,
        created_by               BIGINT          NULL,
        updated_by               BIGINT          NULL,
        is_deleted               BIT             NOT NULL
            CONSTRAINT DF_exercise_observation_is_deleted DEFAULT (0),
        deleted_at               DATETIME2       NULL,
        deleted_by               BIGINT          NULL,
        row_version              ROWVERSION,

        CONSTRAINT FK_exercise_observation_run
            FOREIGN KEY (exercise_run_id) REFERENCES bcm.exercise_run(id),
        CONSTRAINT FK_exercise_observation_inject
            FOREIGN KEY (related_inject_id) REFERENCES bcm.exercise_run_inject(id),
        CONSTRAINT CK_exercise_observation_type CHECK (observation_type IN
            ('STRENGTH','GAP','RISK','COMMUNICATION','DECISION_QUALITY',
             'ESCALATION','RECOVERY_TIME','COMPLIANCE','GENERAL')),
        CONSTRAINT CK_exercise_observation_severity CHECK (severity IN
            ('INFO','MINOR','MODERATE','MAJOR','CRITICAL'))
    );

    -- Drives the type-grouped view used for end-of-run scoring.
    CREATE INDEX IX_exercise_observation_org_run_type
        ON bcm.exercise_observation(organisation_id, exercise_run_id, observation_type)
        WHERE is_deleted = 0;

    -- Drives "my observations" filter on the observer dashboard.
    CREATE INDEX IX_exercise_observation_org_observer_recorded
        ON bcm.exercise_observation(organisation_id, observer_user_id, recorded_at)
        WHERE is_deleted = 0;

    -- Run-scoped chronological scan (used for AAR pull at endRun).
    CREATE INDEX IX_exercise_observation_org_run_recorded
        ON bcm.exercise_observation(organisation_id, exercise_run_id, recorded_at)
        WHERE is_deleted = 0;
END
GO
