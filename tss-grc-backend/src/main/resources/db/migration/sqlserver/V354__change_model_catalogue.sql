-- V354 — S8e / CE-2: the change model catalogue. "Pre-approved" stops being a word anyone can type
-- and becomes a record with an authority and an expiry date.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE GAP, MEASURED 2026-08-04 IN BOTH DATABASES
-- ════════════════════════════════════════════════════════════════════════════════════════
-- G-2 of the uplift plan: "No change model catalogue — STANDARD is an unbacked string, so
-- 'pre-approved' is a claim with no record."  Measured:
--
--     tables that could serve as a change model/template catalogue ... 0
--       (13 hits platform-wide, every one of them another module: ai_governance.ml_model,
--        bcm.plan_templates, tprm.questionnaire_templates, admin.committee_templates …)
--     STANDARD changes ............................................... 5   (dev and prod)
--     STANDARD + LOW — the AUTO cell, i.e. no committee at all ....... 5   (dev and prod)
--     STANDARD changes ever referred to a committee .................. 0   (dev and prod)
--     what the AUTO cell requires today .............................. the matrix row, nothing else
--
-- So a change is "pre-approved" because somebody typed `changeType=STANDARD` and
-- `riskLevel=LOW`. `ck_change_type` makes STANDARD representable to any caller, the matrix maps
-- STANDARD/LOW to gate AUTO with `min_approvals = 0`, and the change then walks
-- CAB_PENDING -> CAB_APPROVED -> ... -> CLOSED with no committee involved and **no record anywhere
-- of which pre-approved procedure it was, who pre-approved it, or whether that pre-approval is
-- still valid**. This was walked end to end during the CE-16 probe: create STANDARD/LOW, advance to
-- CLOSED, zero committee involvement, entirely per the matrix, entirely unbacked.
--
-- `category` (8 values in use: INFRASTRUCTURE, SECURITY, APPLICATION, POLICY, PROCESS,
-- COMPLIANCE_REMEDIATION, DATA, NETWORK) is not a catalogue: it is an uncontrolled label with no
-- authority, no expiry and no steps.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- ⚠ THE CONSTRAINT THAT IS THE POINT OF THIS MIGRATION
-- ════════════════════════════════════════════════════════════════════════════════════════
-- `ck_cmodel_preapproval`: a row may not claim `pre_approved = 1` unless it is APPROVED and names
-- **who** authorised it, **when**, and **until when**. Pre-approval without an expiry is the
-- F2-62 shape — a control that can never come due — and pre-approval without an author is F2-103's,
-- a governance act with nobody behind it. The same reasoning produced
-- `ck_change_ratification_clock` in V345: make the incomplete state unrepresentable rather than
-- validating it in a service somebody will later bypass.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- ⚠ WHY THE ENFORCEMENT FLAG LIVES ON THE MATRIX, AND WHY IT DEFAULTS TO OFF
-- ════════════════════════════════════════════════════════════════════════════════════════
-- The obvious guard — "a STANDARD change must cite a model" — **cannot ship on by default**:
-- 5 STANDARD changes exist in each database and 3 of them are still in flight (2 CAB_PENDING,
-- 1 DRAFT/SCHEDULED). Turning it on globally would refuse work the platform is already holding,
-- which is the mistake S6c avoided for `quorum_requires_chair` (default-on would have refused 17 of
-- 18 quorate meetings) and S7e avoided by shipping a register whose guard could refuse nothing.
--
-- So `change_authorisation_matrix.requires_model` is **per tenant AND per cell**, defaults to 0, and
-- every existing row is 0 — verified below. It lives on the matrix because the matrix is where the
-- gate is decided: "this cell authorises without a committee **only** on the strength of a live
-- pre-approved model" is a statement about the cell, not about the tenant.
--
-- What IS strict from day one, because its population is empty: a model that is not APPROVED, or
-- whose `authorised_until` has passed, may not be applied to a change. Zero models exist, so that
-- guard can refuse nothing that exists — the S7e pattern.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- NOTHING CHANGES ON DEPLOY. Two empty tables and one column defaulting to 0. The catalogue ships
-- empty on purpose: a pre-approval is an act a change authority performs, and seeding one would
-- teach the reader that models are decoration — the same rule V352 applied to conditions.
--
-- REVERSE RECIPE:
--   ALTER TABLE change_mgmt.change_authorisation_matrix DROP CONSTRAINT df_cam_requires_model;
--   ALTER TABLE change_mgmt.change_authorisation_matrix DROP COLUMN requires_model;
--   DROP TABLE change_mgmt.change_model_usage;
--   DROP TABLE change_mgmt.change_model;
-- ════════════════════════════════════════════════════════════════════════════════════════

SET NOCOUNT ON;
GO

-- ── 1. the catalogue ────────────────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_model')
BEGIN
    CREATE TABLE change_mgmt.change_model (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        organisation_id     BIGINT        NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL CONSTRAINT df_cmodel_ext DEFAULT NEWID(),

        -- The catalogue key a change cites. Unique per tenant among live rows.
        code                NVARCHAR(64)  NOT NULL,
        name_en             NVARCHAR(300) NOT NULL,
        name_ar             NVARCHAR(300) NOT NULL,     -- house rule 7, enforced in the schema
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,

        -- What kind of change this model describes, and the risk it is rated for. `gate_override`
        -- is NULLable: a model that does not claim to change the gate leaves the matrix to decide.
        change_type         NVARCHAR(24)  NOT NULL,
        risk_default        NVARCHAR(16)  NULL,
        gate_override       NVARCHAR(8)   NULL,

        -- The pre-approval itself. See ck_cmodel_preapproval: these four move together or not at all.
        pre_approved        BIT           NOT NULL CONSTRAINT df_cmodel_preappr DEFAULT 0,
        authorised_by_id    BIGINT        NULL,
        authorised_by_name  NVARCHAR(200) NULL,
        authorised_at       DATETIME2(6)  NULL,
        authorised_until    DATETIME2(6)  NULL,

        -- The procedure. Free text on purpose: a runbook is prose, and forcing it into rows here
        -- would duplicate `change_tasks`, which is the per-change instance of exactly this.
        standard_steps      NVARCHAR(MAX) NULL,
        rollback_template   NVARCHAR(MAX) NULL,
        test_template       NVARCHAR(MAX) NULL,
        max_lead_time_hours INT           NULL,
        review_cadence      NVARCHAR(32)  NULL,

        status              NVARCHAR(16)  NOT NULL CONSTRAINT df_cmodel_status DEFAULT 'DRAFT',

        is_deleted          BIT           NOT NULL CONSTRAINT df_cmodel_del DEFAULT 0,
        created_at          DATETIME2(6)  NOT NULL CONSTRAINT df_cmodel_created DEFAULT SYSUTCDATETIME(),
        created_by          NVARCHAR(200) NULL,
        updated_at          DATETIME2(6)  NULL,
        updated_by          NVARCHAR(200) NULL,
        row_version         ROWVERSION,

        CONSTRAINT pk_change_model PRIMARY KEY CLUSTERED (id),
        CONSTRAINT ck_cmodel_status CHECK (status IN ('DRAFT', 'APPROVED', 'SUSPENDED', 'RETIRED')),
        CONSTRAINT ck_cmodel_type   CHECK (change_type IN ('STANDARD', 'NORMAL', 'EMERGENCY')),
        CONSTRAINT ck_cmodel_risk   CHECK (risk_default IS NULL
                                           OR risk_default IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
        CONSTRAINT ck_cmodel_gate   CHECK (gate_override IS NULL
                                           OR gate_override IN ('AUTO', 'CAB', 'ECAB')),
        CONSTRAINT ck_cmodel_lead   CHECK (max_lead_time_hours IS NULL OR max_lead_time_hours > 0),
        -- ⚠ The constraint this migration exists for. "Pre-approved" is not a label.
        CONSTRAINT ck_cmodel_preapproval CHECK (
            pre_approved = 0
            OR (status = 'APPROVED'
                AND authorised_by_name IS NOT NULL AND LEN(LTRIM(authorised_by_name)) > 0
                AND authorised_at    IS NOT NULL
                AND authorised_until IS NOT NULL
                AND authorised_until > authorised_at))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_change_model_code'
               AND object_id = OBJECT_ID('change_mgmt.change_model'))
    CREATE UNIQUE INDEX ux_change_model_code
        ON change_mgmt.change_model (organisation_id, code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_change_model_org_status'
               AND object_id = OBJECT_ID('change_mgmt.change_model'))
    CREATE INDEX ix_change_model_org_status
        ON change_mgmt.change_model (organisation_id, status, is_deleted);
GO

-- ── 2. which change applied which model, and whether it deviated ────────────────────────
--
-- A separate table rather than a column on change_requests, because the audit question is not only
-- "which model" but "did the change actually follow it". A change that cites a pre-approved model
-- and then deviates from it is NOT pre-approved, and `deviation_notes` is where that is recorded.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_model_usage')
BEGIN
    CREATE TABLE change_mgmt.change_model_usage (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        organisation_id     BIGINT        NOT NULL,
        change_request_id   BIGINT        NOT NULL,
        change_model_id     BIGINT        NOT NULL,
        applied_at          DATETIME2(6)  NOT NULL CONSTRAINT df_cmu_applied DEFAULT SYSUTCDATETIME(),
        applied_by_id       BIGINT        NULL,
        applied_by_name     NVARCHAR(200) NULL,
        -- NULL means "followed the model". Non-empty means the change departed from it, and the
        -- register reports it as a deviation rather than as clean pre-approval.
        deviation_notes     NVARCHAR(MAX) NULL,
        is_deleted          BIT           NOT NULL CONSTRAINT df_cmu_del DEFAULT 0,
        created_at          DATETIME2(6)  NOT NULL CONSTRAINT df_cmu_created DEFAULT SYSUTCDATETIME(),

        CONSTRAINT pk_change_model_usage PRIMARY KEY CLUSTERED (id),
        CONSTRAINT fk_cmu_change FOREIGN KEY (change_request_id)
            REFERENCES change_mgmt.change_requests (id),
        CONSTRAINT fk_cmu_model  FOREIGN KEY (change_model_id)
            REFERENCES change_mgmt.change_model (id)
    );
END
GO

-- One model per change. A second row would make "which model authorised this?" ambiguous, and an
-- ambiguous authority is the thing this whole slice removes.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_change_model_usage_one_per_change'
               AND object_id = OBJECT_ID('change_mgmt.change_model_usage'))
    CREATE UNIQUE INDEX ux_change_model_usage_one_per_change
        ON change_mgmt.change_model_usage (organisation_id, change_request_id) WHERE is_deleted = 0;
GO

-- ── 3. the opt-in enforcement flag, on the cell that decides the gate ───────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_authorisation_matrix'
                 AND COLUMN_NAME = 'requires_model')
    ALTER TABLE change_mgmt.change_authorisation_matrix
        ADD requires_model BIT NOT NULL CONSTRAINT df_cam_requires_model DEFAULT 0;
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS. Every one THROWs; this file is applied twice to dev by hand, so each must be
-- idempotent and each must be true on the second pass too.
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. Both tables carry every column the entities map.
DECLARE @missing NVARCHAR(MAX) = '';
SELECT @missing = @missing + c.n + ' '
FROM (VALUES ('organisation_id'), ('code'), ('name_en'), ('name_ar'), ('change_type'),
             ('risk_default'), ('gate_override'), ('pre_approved'), ('authorised_by_id'),
             ('authorised_by_name'), ('authorised_at'), ('authorised_until'), ('standard_steps'),
             ('rollback_template'), ('test_template'), ('max_lead_time_hours'), ('review_cadence'),
             ('status'), ('is_deleted')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_model'
                    AND COLUMN_NAME = c.n);
IF LEN(@missing) > 0
    THROW 54001, 'V354: change_model is missing columns the entity maps.', 1;
GO

DECLARE @missing2 NVARCHAR(MAX) = '';
SELECT @missing2 = @missing2 + c.n + ' '
FROM (VALUES ('organisation_id'), ('change_request_id'), ('change_model_id'), ('applied_at'),
             ('applied_by_id'), ('applied_by_name'), ('deviation_notes'), ('is_deleted')) AS c(n)
WHERE NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_model_usage'
                    AND COLUMN_NAME = c.n);
IF LEN(@missing2) > 0
    THROW 54002, 'V354: change_model_usage is missing columns the entity maps.', 1;
GO

-- 2. House rule 7 in the schema, not only in bean validation (F2-30 measured what prose achieves).
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_model'
             AND COLUMN_NAME IN ('name_en', 'name_ar') AND IS_NULLABLE = 'YES')
    THROW 54003, 'V354: name_en/name_ar must both be NOT NULL (house rule 7).', 1;
GO

-- 3. The pre-approval constraint exists, and is TRUSTED and ENABLED. A disabled CHECK is a comment.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = 'ck_cmodel_preapproval'
                 AND parent_object_id = OBJECT_ID('change_mgmt.change_model'))
    THROW 54004, 'V354: ck_cmodel_preapproval is missing — pre-approval could be claimed with no authority and no expiry.', 1;
GO

DECLARE @msgTrust NVARCHAR(2048) = N'V354: ck_cmodel_preapproval exists but is disabled or '
    + N'not trusted. A CHECK that is not enforced is a comment, and this is the one constraint '
    + N'that makes "pre-approved" mean something.';
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_cmodel_preapproval'
             AND parent_object_id = OBJECT_ID('change_mgmt.change_model')
             AND (is_disabled = 1 OR is_not_trusted = 1))
    THROW 54005, @msgTrust, 1;
GO

-- 4. ⚠ Prove the constraint REJECTS, rather than merely existing. Checks 3 reads the catalogue;
--    this one exercises it: insert a row claiming pre-approval with no authority, inside a
--    transaction that always rolls back. If the insert succeeds, the constraint is not doing its
--    job and the migration must fail loudly rather than report OK.
DECLARE @rejected BIT = 0;
BEGIN TRANSACTION;
BEGIN TRY
    INSERT INTO change_mgmt.change_model
        (organisation_id, code, name_en, name_ar, change_type, pre_approved, status)
    VALUES (1, '__V354_SELFCHECK__', 'self-check', N'فحص', 'STANDARD', 1, 'DRAFT');
END TRY
BEGIN CATCH
    SET @rejected = 1;
END CATCH
ROLLBACK TRANSACTION;
DECLARE @msgRej NVARCHAR(2048) = N'V354: ck_cmodel_preapproval accepted pre_approved=1 on a DRAFT '
    + N'row with no authorising name, no authorised_at and no expiry. The constraint is present but '
    + N'not enforcing the rule this migration is for.';
IF @rejected = 0
    THROW 54006, @msgRej, 1;
GO

-- 5. The uniqueness guarantees. Without ux_change_model_code two live models can share a code, and
--    "which model authorised this change?" stops having one answer.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_change_model_code'
               AND object_id = OBJECT_ID('change_mgmt.change_model'))
    THROW 54007, 'V354: ux_change_model_code is missing — two live models could share a code.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_change_model_usage_one_per_change'
               AND object_id = OBJECT_ID('change_mgmt.change_model_usage'))
    THROW 54008, 'V354: ux_change_model_usage_one_per_change is missing — a change could cite two models.', 1;
GO

-- 6. The enforcement flag exists, is NOT NULL, and is OFF on every existing row — so this migration
--    refuses no change that the platform already holds. This is the measured-safety claim in the
--    header, asserted rather than asserted-in-prose.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = 'change_mgmt' AND TABLE_NAME = 'change_authorisation_matrix'
                 AND COLUMN_NAME = 'requires_model' AND IS_NULLABLE = 'NO')
    THROW 54009, 'V354: change_authorisation_matrix.requires_model missing or nullable.', 1;
GO

DECLARE @msgOn NVARCHAR(2048) = N'V354: requires_model is already 1 on at least one matrix cell. '
    + N'This migration must arrive inert: 5 STANDARD changes exist in each database and 3 are in '
    + N'flight, so an enforcement flag switched on at deploy time would refuse work already under '
    + N'way. Turning it on is a tenant decision taken after the catalogue has models in it.';
IF EXISTS (SELECT 1 FROM change_mgmt.change_authorisation_matrix WHERE requires_model = 1)
    THROW 54010, @msgOn, 1;
GO

-- 7. `ck_change_type` is untouched. A change model is not a fourth change type: the model describes
--    HOW a STANDARD change is pre-approved, it does not create a new kind of change. If a later
--    slice wants one, it amends this check and ChangeModelCatalogueTest in the same commit.
DECLARE @typeDef NVARCHAR(MAX) = (SELECT definition FROM sys.check_constraints
                                  WHERE name = 'ck_change_type'
                                    AND parent_object_id = OBJECT_ID('change_mgmt.change_requests'));
DECLARE @msgType NVARCHAR(2048) = N'V354: ck_change_type no longer names exactly STANDARD, NORMAL '
    + N'and EMERGENCY. A change model is not a change type — see this file''s header.';
IF @typeDef IS NULL OR @typeDef NOT LIKE '%STANDARD%' OR @typeDef NOT LIKE '%NORMAL%'
   OR @typeDef NOT LIKE '%EMERGENCY%' OR @typeDef LIKE '%MODEL%'
    THROW 54011, @msgType, 1;
GO

-- 8. Both tables ship EMPTY. A pre-approval is an act a change authority performs; a seeded
--    catalogue teaches the reader that models are decoration (V352 applied the same rule to
--    conditions, and V345 to the ratification register).
DECLARE @msgSeed NVARCHAR(2048) = N'V354: change_model must ship empty. A pre-approved procedure is '
    + N'an authority somebody exercised, with a name and an expiry date — not demo data.';
IF EXISTS (SELECT 1 FROM change_mgmt.change_model WHERE code <> '__V354_SELFCHECK__')
    THROW 54012, @msgSeed, 1;
GO

IF EXISTS (SELECT 1 FROM change_mgmt.change_model_usage)
    THROW 54013, 'V354: change_model_usage must ship empty — no change has applied a model yet.', 1;
GO

-- 9. The self-check row must not have survived its rolled-back transaction.
IF EXISTS (SELECT 1 FROM change_mgmt.change_model WHERE code = '__V354_SELFCHECK__')
    THROW 54014, 'V354: the self-check row survived — its transaction did not roll back.', 1;
GO

PRINT 'V354 OK — change_model + change_model_usage created empty; requires_model added and OFF everywhere.';
GO
