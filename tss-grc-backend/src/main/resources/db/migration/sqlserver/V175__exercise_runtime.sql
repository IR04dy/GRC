-- V172 — Slice 10-B: Inject-driven exercise runtime + facilitator console.
--
-- Builds on top of V171 (Slice 10-A's bcm.exercise_scenario_inject library +
-- bcm.exercises.scenario_id link). V171 lets a BCM Manager pick a library
-- scenario and pre-stage an exercise; V172 adds the *live-fire* machinery that
-- turns that pre-staged plan into a running facilitator console + participant
-- responses + after-action timeline.
--
-- Three new tables under the bcm schema:
--
--   1. bcm.exercise_run
--        — the run-time instance of a BcmExercise. One row per "session"
--        of the same exercise (so the same library scenario can be exercised
--        multiple times against different audiences). Tracks SCHEDULED →
--        IN_PROGRESS → PAUSED → COMPLETED / ABORTED state machine.
--
--   2. bcm.exercise_run_inject
--        — per-run instance of each scheduled inject. Cloned from the
--        source bcm.exercise_scenario_inject row at startRun time, with the
--        inject text DENORMALISED (copied verbatim) so editing the library
--        scenario later does not retroactively change the historical timeline.
--        source_inject_id is nullable to support on-the-fly facilitator-added
--        injects (the "Add custom inject" path).
--
--   3. bcm.exercise_run_response
--        — every response from a participant to a dispatched inject. Computes
--        a response_time_seconds at write-time relative to the inject's
--        actual_dispatched_at, so the facilitator's after-action timeline
--        can quantify decision latency.
--
-- All three tables follow the platform conventions:
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
-- 1. bcm.exercise_run — the live "session" of an exercise.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'exercise_run' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_run (
        id                         BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id            BIGINT          NOT NULL,
        exercise_id                BIGINT          NOT NULL,
        run_code                   NVARCHAR(64)    NOT NULL,

        facilitator_user_id        BIGINT          NULL,
        started_at                 DATETIME2       NULL,
        ended_at                   DATETIME2       NULL,

        -- SCHEDULED | IN_PROGRESS | PAUSED | COMPLETED | ABORTED
        status                     VARCHAR(20)     NOT NULL CONSTRAINT DF_exercise_run_status DEFAULT ('SCHEDULED'),

        participant_count          INT             NOT NULL CONSTRAINT DF_exercise_run_participant_count DEFAULT (0),

        -- IN_PERSON | VIRTUAL_MEET | HYBRID | CHAT_ONLY
        channel                    VARCHAR(40)     NOT NULL CONSTRAINT DF_exercise_run_channel DEFAULT ('IN_PERSON'),

        facilitator_notes_en       NVARCHAR(MAX)   NULL,
        facilitator_notes_ar       NVARCHAR(MAX)   NULL,

        -- Standard audit + soft-delete + rowversion (BaseEntity contract)
        created_at                 DATETIME2       NOT NULL CONSTRAINT DF_exercise_run_created_at DEFAULT SYSUTCDATETIME(),
        updated_at                 DATETIME2       NULL,
        created_by                 BIGINT          NULL,
        updated_by                 BIGINT          NULL,
        is_deleted                 BIT             NOT NULL CONSTRAINT DF_exercise_run_is_deleted DEFAULT (0),
        deleted_at                 DATETIME2       NULL,
        deleted_by                 BIGINT          NULL,
        row_version                ROWVERSION,

        CONSTRAINT FK_exercise_run_exercise
            FOREIGN KEY (exercise_id) REFERENCES bcm.exercises(id),
        CONSTRAINT CK_exercise_run_status CHECK (status IN
            ('SCHEDULED','IN_PROGRESS','PAUSED','COMPLETED','ABORTED')),
        CONSTRAINT CK_exercise_run_channel CHECK (channel IN
            ('IN_PERSON','VIRTUAL_MEET','HYBRID','CHAT_ONLY'))
    );

    CREATE UNIQUE INDEX UX_exercise_run_run_code
        ON bcm.exercise_run(organisation_id, run_code)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_run_org_status
        ON bcm.exercise_run(organisation_id, status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_run_org_exercise
        ON bcm.exercise_run(organisation_id, exercise_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_run_org_facilitator
        ON bcm.exercise_run(organisation_id, facilitator_user_id)
        WHERE is_deleted = 0;
END
GO

-- ============================================================================
-- 2. bcm.exercise_run_inject — per-run cloned inject (denormalised body).
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'exercise_run_inject' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_run_inject (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        exercise_run_id             BIGINT          NOT NULL,
        -- NULL for "custom" facilitator-injected mid-run items.
        source_inject_id            BIGINT          NULL,

        planned_time_offset_minutes INT             NOT NULL CONSTRAINT DF_exercise_run_inject_offset DEFAULT (0),
        actual_dispatched_at        DATETIME2       NULL,

        -- INFORMATION | ESCALATION | DECISION | MEDIA | REGULATOR | CUSTOM
        inject_type                 VARCHAR(40)     NOT NULL,

        -- Denormalised body — copied verbatim from the source inject at
        -- startRun (or supplied by the facilitator for custom injects), so
        -- editing the library scenario later does not rewrite history.
        inject_text_en              NVARCHAR(2000)  NOT NULL,
        inject_text_ar              NVARCHAR(2000)  NULL,
        expected_action_en          NVARCHAR(1000)  NULL,
        expected_action_ar          NVARCHAR(1000)  NULL,

        -- PENDING | DISPATCHED | ACKNOWLEDGED | COMPLETED | SKIPPED
        status                      VARCHAR(20)     NOT NULL CONSTRAINT DF_exercise_run_inject_status DEFAULT ('PENDING'),

        -- Audit + soft-delete + rowversion
        created_at                  DATETIME2       NOT NULL CONSTRAINT DF_exercise_run_inject_created_at DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL CONSTRAINT DF_exercise_run_inject_is_deleted DEFAULT (0),
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_exercise_run_inject_run
            FOREIGN KEY (exercise_run_id) REFERENCES bcm.exercise_run(id),
        CONSTRAINT FK_exercise_run_inject_source
            FOREIGN KEY (source_inject_id) REFERENCES bcm.exercise_scenario_inject(id),
        CONSTRAINT CK_exercise_run_inject_type CHECK (inject_type IN
            ('INFORMATION','ESCALATION','DECISION','MEDIA','REGULATOR','CUSTOM')),
        CONSTRAINT CK_exercise_run_inject_status CHECK (status IN
            ('PENDING','DISPATCHED','ACKNOWLEDGED','COMPLETED','SKIPPED'))
    );

    CREATE INDEX IX_exercise_run_inject_org_run_status
        ON bcm.exercise_run_inject(organisation_id, exercise_run_id, status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_run_inject_org_run_offset
        ON bcm.exercise_run_inject(organisation_id, exercise_run_id, planned_time_offset_minutes)
        WHERE is_deleted = 0;
END
GO

-- ============================================================================
-- 3. bcm.exercise_run_response — participant responses to dispatched injects.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'exercise_run_response' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.exercise_run_response (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        run_inject_id               BIGINT          NOT NULL,
        participant_user_id         BIGINT          NOT NULL,

        response_text_en            NVARCHAR(2000)  NULL,
        response_text_ar            NVARCHAR(2000)  NULL,
        decision_made_en            NVARCHAR(1000)  NULL,
        decision_made_ar            NVARCHAR(1000)  NULL,

        response_at                 DATETIME2       NOT NULL CONSTRAINT DF_exercise_run_response_at DEFAULT SYSUTCDATETIME(),
        -- Seconds elapsed between actual_dispatched_at and response_at.
        response_time_seconds       INT             NULL,

        -- Facilitator scores 0-100 in the after-action review pass.
        quality_score               DECIMAL(4,2)    NULL,
        facilitator_comment_en      NVARCHAR(1000)  NULL,
        facilitator_comment_ar      NVARCHAR(1000)  NULL,

        -- Audit + soft-delete + rowversion
        created_at                  DATETIME2       NOT NULL CONSTRAINT DF_exercise_run_response_created_at DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL CONSTRAINT DF_exercise_run_response_is_deleted DEFAULT (0),
        deleted_at                  DATETIME2       NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_exercise_run_response_inject
            FOREIGN KEY (run_inject_id) REFERENCES bcm.exercise_run_inject(id),
        CONSTRAINT CK_exercise_run_response_quality_range CHECK
            (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100))
    );

    CREATE INDEX IX_exercise_run_response_org_inject
        ON bcm.exercise_run_response(organisation_id, run_inject_id)
        WHERE is_deleted = 0;

    CREATE INDEX IX_exercise_run_response_org_participant
        ON bcm.exercise_run_response(organisation_id, participant_user_id)
        WHERE is_deleted = 0;
END
GO
