-- ============================================================================
-- TSS GRC — BCM Exercise table (ISO 22301 §8.5 — Exercise programme)
-- ============================================================================
-- Replaces the in-memory exercise list previously held in the React state of
-- BcmPage with a real persisted entity, so exercises survive reload, can be
-- linked to a plan, scored, and rolled into KPI tiles and exercise-pass-rate
-- reporting.
--
-- Apply via SSMS or sqlcmd:
--   sqlcmd -S localhost -d TSS_GRC -U sa -P tss.123 -i V_bcm_exercises_ddl.sql
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

-- ── Table ───────────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = 'bcm' AND t.name = 'exercises')
BEGIN
  CREATE TABLE bcm.exercises (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_bcm_exercises_extid DEFAULT NEWID(),
    organisation_id   BIGINT NOT NULL,
    plan_id           BIGINT NULL,
    exercise_name_en  NVARCHAR(255) NOT NULL,
    exercise_name_ar  NVARCHAR(255) NULL,
    exercise_type     NVARCHAR(50) NOT NULL CONSTRAINT CK_bcm_exer_type
                      CHECK (exercise_type IN ('TABLETOP','FUNCTIONAL','FULL','COMMUNICATION','TECHNICAL_RECOVERY')),
    scenario          NVARCHAR(MAX) NULL,
    scheduled_date    DATE NULL,
    executed_date     DATE NULL,
    score             INT NULL CONSTRAINT CK_bcm_exer_score CHECK (score IS NULL OR (score BETWEEN 0 AND 100)),
    status            NVARCHAR(30) NOT NULL CONSTRAINT DF_bcm_exer_status DEFAULT 'SCHEDULED'
                      CONSTRAINT CK_bcm_exer_status
                      CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    findings          NVARCHAR(MAX) NULL,
    capa_count        INT NOT NULL CONSTRAINT DF_bcm_exer_capa DEFAULT 0,
    facilitator       NVARCHAR(255) NULL,
    is_deleted        BIT NOT NULL CONSTRAINT DF_bcm_exer_isdel DEFAULT 0,
    created_at        DATETIME2 NOT NULL CONSTRAINT DF_bcm_exer_created DEFAULT SYSUTCDATETIME(),
    updated_at        DATETIME2 NULL,
    CONSTRAINT FK_bcm_exer_plan FOREIGN KEY (plan_id) REFERENCES bcm.bcm_plans(id)
  );

  CREATE INDEX IX_bcm_exer_org      ON bcm.exercises(organisation_id) WHERE is_deleted = 0;
  CREATE INDEX IX_bcm_exer_plan     ON bcm.exercises(plan_id)         WHERE is_deleted = 0;
  CREATE INDEX IX_bcm_exer_sched    ON bcm.exercises(scheduled_date)  WHERE is_deleted = 0;

  PRINT N'Created bcm.exercises';
END
ELSE
BEGIN
  PRINT N'bcm.exercises already exists — DDL skipped';
END

-- ── Seed three sample exercises (only if table is empty) ───────────────────
IF NOT EXISTS (SELECT 1 FROM bcm.exercises WHERE organisation_id = 1 AND is_deleted = 0)
BEGIN
  INSERT INTO bcm.exercises
    (organisation_id, plan_id, exercise_name_en, exercise_name_ar, exercise_type,
     scenario, scheduled_date, executed_date, score, status, findings, facilitator)
  VALUES
    (1, NULL, N'Tabletop · Q1 2026',    N'تمرين نظري - الربع الأول 2026',  N'TABLETOP',
     N'Ransomware in core banking environment; failover to DR region.',
     '2026-03-12', '2026-03-12', 92,  N'COMPLETED',
     N'DR runbook needed minor updates; communications cascade was effective.',
     N'CISO Office'),
    (1, NULL, N'Functional · Q1 2026',  N'تمرين وظيفي - الربع الأول 2026', N'FUNCTIONAL',
     N'Loss of primary data centre; failover and partial application recovery.',
     '2026-03-26', '2026-03-26', 78,  N'COMPLETED',
     N'RTO target missed by 12 minutes for tier-2 applications.',
     N'BCM Team'),
    (1, NULL, N'Tabletop · Q2 2026',    N'تمرين نظري - الربع الثاني 2026', N'TABLETOP',
     N'Wide-area sandstorm impacting Riyadh regional operations.',
     '2026-06-02', NULL, NULL, N'SCHEDULED',
     NULL,
     N'NCEMA Liaison');

  PRINT N'Seeded 3 sample exercises';
END
ELSE
BEGIN
  PRINT N'Exercises already present for org 1 — seed skipped';
END

GO
