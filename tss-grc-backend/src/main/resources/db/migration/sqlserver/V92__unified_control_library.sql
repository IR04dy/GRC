-- V92: Unified Control Library (Framework Intersection plan, slice F2).
--
-- The platform spine: ONE control table that maps to many citations
-- across many authority documents (NCA, PDPL, SAMA, ISO, …). Domain
-- modules (cyber, bcm, privacy, …) eventually point to controls here
-- instead of carrying their own parallel control catalogs.
--
-- Schema layout mirrors ServiceNow IRM and Archer naming for
-- transferability:
--   controls              ↔ "Control"
--   control_objectives    ↔ "Control Objective" (group of controls)
--   control_mappings      ↔ "Citation → Control" cross-reference
--   control_assessments   ↔ "Control Test" / "Control Assessment"
--   risk_control_links    ↔ "Risk-Control Matrix" (R1 from GRC review)
--
-- See docs/grc/framework-intersection-plan.md §5.

-- ─── Control Objectives ───────────────────────────────────────────
-- Groups of controls that collectively satisfy a higher-level goal
-- (e.g. "Privileged Access Management" objective groups 5 controls).
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'control_objectives' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.control_objectives (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id    BIGINT          NOT NULL,
        external_id        UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        objective_code     NVARCHAR(60)    NOT NULL,    -- e.g. 'OBJ-IAM-PAM'
        domain             NVARCHAR(40)    NOT NULL,    -- RISK | CYBER | BCM | PRIVACY | TPRM | COMPLIANCE | GENERAL
        title_en           NVARCHAR(255)   NOT NULL,
        title_ar           NVARCHAR(255)   NULL,
        description_en     NVARCHAR(MAX)   NULL,
        description_ar     NVARCHAR(MAX)   NULL,
        owner_user_id      BIGINT          NULL,
        status             NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        is_deleted         BIT             NOT NULL DEFAULT 0,
        created_at         DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at         DATETIME2(0)    NULL,

        CONSTRAINT uq_objective_org_code UNIQUE (organisation_id, objective_code),
        CONSTRAINT ck_objective_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
    );
    CREATE INDEX ix_objective_org_domain ON platform.control_objectives(organisation_id, domain);
END
GO

-- ─── Controls (the spine) ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'controls' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.controls (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id    BIGINT          NOT NULL,
        external_id        UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        control_code       NVARCHAR(60)    NOT NULL,    -- 'C-IAM-014' / 'C-BCM-003'
        version            NVARCHAR(20)    NOT NULL DEFAULT '1.0',
        domain             NVARCHAR(40)    NOT NULL,    -- RISK | CYBER | BCM | PRIVACY | TPRM | COMPLIANCE | GENERAL
        objective_id       BIGINT          NULL,        -- optional parent objective

        title_en           NVARCHAR(255)   NOT NULL,
        title_ar           NVARCHAR(255)   NULL,
        description_en     NVARCHAR(MAX)   NULL,
        description_ar     NVARCHAR(MAX)   NULL,

        -- Control taxonomy (NIST/COBIT lineage)
        control_type       NVARCHAR(20)    NOT NULL DEFAULT 'PREVENTIVE',
        -- PREVENTIVE | DETECTIVE | CORRECTIVE | DETERRENT | RECOVERY
        automation_level   NVARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
        -- MANUAL | SEMI | AUTOMATED
        frequency          NVARCHAR(20)    NULL,
        -- CONTINUOUS | DAILY | WEEKLY | MONTHLY | QUARTERLY | ANNUAL | ON_EVENT
        owner_user_id      BIGINT          NULL,
        status             NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        -- DRAFT | ACTIVE | RETIRED

        -- Migration support: lets the unified library coexist with the
        -- legacy per-module control tables. F3-F4 migrations populate
        -- these so reverse lookup works during cutover.
        legacy_source_module NVARCHAR(40)  NULL,        -- 'cyber' | 'compliance' | 'ndi' …
        legacy_id            BIGINT        NULL,

        is_deleted         BIT             NOT NULL DEFAULT 0,
        created_at         DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         NVARCHAR(100)   NULL,
        updated_at         DATETIME2(0)    NULL,
        updated_by         NVARCHAR(100)   NULL,

        CONSTRAINT uq_control_org_code_version UNIQUE (organisation_id, control_code, version),
        CONSTRAINT ck_control_type   CHECK (control_type IN ('PREVENTIVE','DETECTIVE','CORRECTIVE','DETERRENT','RECOVERY')),
        CONSTRAINT ck_control_auto   CHECK (automation_level IN ('MANUAL','SEMI','AUTOMATED')),
        CONSTRAINT ck_control_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
        CONSTRAINT fk_control_objective FOREIGN KEY (objective_id) REFERENCES platform.control_objectives(id)
    );
    CREATE INDEX ix_control_org_domain   ON platform.controls(organisation_id, domain);
    CREATE INDEX ix_control_org_status   ON platform.controls(organisation_id, status);
    CREATE INDEX ix_control_objective    ON platform.controls(objective_id);
    CREATE INDEX ix_control_legacy       ON platform.controls(legacy_source_module, legacy_id);
END
GO

-- ─── Control ↔ Citation mappings ──────────────────────────────────
-- One control can satisfy many citations across many authority
-- documents. This table is the heart of the framework intersection
-- design — without it, "one control satisfies NCA ECC + ISO 27001 +
-- SAMA Cyber" cannot be expressed as data.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'control_mappings' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.control_mappings (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        control_id               BIGINT          NOT NULL,
        citation_id              BIGINT          NOT NULL,

        coverage                 NVARCHAR(20)    NOT NULL DEFAULT 'FULL',
        -- FULL | PARTIAL | COMPENSATING
        rationale_en             NVARCHAR(MAX)   NULL,
        rationale_ar             NVARCHAR(MAX)   NULL,
        last_reviewed_at         DATETIME2(0)    NULL,
        reviewer_user_id         BIGINT          NULL,

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT uq_mapping_control_citation UNIQUE (control_id, citation_id),
        CONSTRAINT ck_mapping_coverage CHECK (coverage IN ('FULL','PARTIAL','COMPENSATING')),
        CONSTRAINT fk_mapping_control  FOREIGN KEY (control_id)  REFERENCES platform.controls(id),
        CONSTRAINT fk_mapping_citation FOREIGN KEY (citation_id) REFERENCES platform.authority_document_citations(id)
    );
    CREATE INDEX ix_mapping_control  ON platform.control_mappings(control_id);
    CREATE INDEX ix_mapping_citation ON platform.control_mappings(citation_id);
    CREATE INDEX ix_mapping_org      ON platform.control_mappings(organisation_id);
END
GO

-- ─── Risk ↔ Control links (GRC-expert review R1) ──────────────────
-- "Risk-Control Matrix" — what every auditor asks for first. Stays in
-- platform schema so any module's risk register can write to it.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'risk_control_links' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.risk_control_links (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        risk_id                  BIGINT          NOT NULL,    -- soft FK to risk.risks.id
        control_id               BIGINT          NOT NULL,

        link_type                NVARCHAR(20)    NOT NULL DEFAULT 'MITIGATES',
        -- MITIGATES | DETECTS | TRANSFERS | AVOIDS | COMPENSATES
        effectiveness            NVARCHAR(30)    NULL,
        -- EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED
        rationale_en             NVARCHAR(MAX)   NULL,
        rationale_ar             NVARCHAR(MAX)   NULL,
        last_reviewed_at         DATETIME2(0)    NULL,

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT uq_rcl_risk_control UNIQUE (risk_id, control_id),
        CONSTRAINT ck_rcl_link_type  CHECK (link_type IN ('MITIGATES','DETECTS','TRANSFERS','AVOIDS','COMPENSATES')),
        CONSTRAINT ck_rcl_effect     CHECK (effectiveness IS NULL OR effectiveness IN
            ('EFFECTIVE','NEEDS_IMPROVEMENT','INEFFECTIVE','NOT_TESTED')),
        CONSTRAINT fk_rcl_control FOREIGN KEY (control_id) REFERENCES platform.controls(id)
    );
    CREATE INDEX ix_rcl_risk    ON platform.risk_control_links(risk_id);
    CREATE INDEX ix_rcl_control ON platform.risk_control_links(control_id);
    CREATE INDEX ix_rcl_org     ON platform.risk_control_links(organisation_id);
END
GO

-- ─── Control assessments ──────────────────────────────────────────
-- Single assessment table replaces per-module ones over time. Carries
-- assessor + assessment_date + effectiveness + evidence pointer +
-- workflow state. Linked to a finding/CAPA if it fails.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'control_assessments' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.control_assessments (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        control_id               BIGINT          NOT NULL,
        assessment_cycle_id      BIGINT          NULL,        -- optional soft FK

        effectiveness            NVARCHAR(30)    NOT NULL DEFAULT 'NOT_TESTED',
        -- EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED
        assessor_user_id         BIGINT          NULL,
        assessor_name            NVARCHAR(120)   NULL,
        assessment_date          DATE            NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATE),
        evidence_id              BIGINT          NULL,        -- soft FK to evidence.evidence_items.id
        notes_en                 NVARCHAR(MAX)   NULL,
        notes_ar                 NVARCHAR(MAX)   NULL,

        finding_id               BIGINT          NULL,        -- soft FK
        capa_id                  BIGINT          NULL,        -- soft FK

        workflow_status          NVARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | UNDER_REVIEW | APPROVED | REJECTED

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT ck_ca_effectiveness CHECK (effectiveness IN
            ('EFFECTIVE','NEEDS_IMPROVEMENT','INEFFECTIVE','NOT_TESTED')),
        CONSTRAINT ck_ca_workflow      CHECK (workflow_status IN
            ('DRAFT','UNDER_REVIEW','APPROVED','REJECTED')),
        CONSTRAINT fk_ca_control FOREIGN KEY (control_id) REFERENCES platform.controls(id)
    );
    CREATE INDEX ix_ca_control      ON platform.control_assessments(control_id);
    CREATE INDEX ix_ca_org_status   ON platform.control_assessments(organisation_id, workflow_status);
    CREATE INDEX ix_ca_cycle        ON platform.control_assessments(assessment_cycle_id);
END
GO

-- ─── Seed: 1 objective + 8 demo controls + 5 mappings ─────────────
-- Enough to prove F2 end-to-end and give F5 (per-domain tabs) and
-- F6 (Compliance Posture Hub) something live to render.

IF NOT EXISTS (SELECT 1 FROM platform.control_objectives WHERE organisation_id = 1 AND objective_code = 'OBJ-CYBER-GOV')
BEGIN
    INSERT INTO platform.control_objectives
      (organisation_id, objective_code, domain, title_en, title_ar, description_en, description_ar, status)
    VALUES
      (1, 'OBJ-CYBER-GOV', 'CYBER',
       'Cybersecurity Governance',
       N'حوكمة الأمن السيبراني',
       'Strategy, policy and oversight controls that anchor the cybersecurity programme.',
       N'الاستراتيجية والسياسات والإشراف التي تُرسي برنامج الأمن السيبراني.',
       'ACTIVE');
END
GO
-- ⚠ F2-77 — THE platform.controls SEED THAT USED TO LIVE HERE HAS BEEN REMOVED. V93 OWNS IT.
--
-- It could never have run on any database that applied V1. Batch 2 above guards its
-- CREATE TABLE platform.controls with IF NOT EXISTS (sys.tables ...), and V1 already
-- created a 12-column sprint-0 stub of that name — so on every ordered run the guard skips,
-- the unified 23-column schema never lands here, and the seed below then failed at COMPILE
-- time on organisation_id / domain / objective_id / description_ar. A column that does not
-- exist on a table that does is a binding error for the whole batch, so no IF could have
-- guarded it; only removing it or deferring it through dynamic SQL can.
--
-- V93__fix_platform_controls_collision.sql exists precisely for this and says so in its own
-- header: it detects the V1 stub by its framework_id column, drops it, recreates the table
-- with this file's schema, and re-runs this seed. Verified rather than assumed: V93 seeds
-- all EIGHT control codes this file did (C-ASSET-001, C-BCM-003, C-BCM-005, C-CYBER-001,
-- C-CYBER-002, C-IAM-014, C-PRIV-002, C-TPRM-004), so the end state is unchanged and the
-- platform now has ONE authority for that seed instead of two copies.
--
-- The control_OBJECTIVES seed in the batch above is NOT duplicated in V93 and stays here.
GO
-- ⚠ F2-77 — THE control_mappings SEED THAT USED TO LIVE HERE HAS BEEN REMOVED. V93 OWNS IT.
--
-- Same cause as the controls seed above: it reads platform.controls by organisation_id and
-- control_code, columns the V1 stub does not have, so the batch failed at COMPILE time on
-- every ordered run. And it could never have produced anything anyway, because the controls
-- it maps were themselves seeded by the batch above that never ran.
--
-- V93 re-runs it. Verified by comparing the statements rather than trusting the header: the
-- two INSERT INTO platform.control_mappings blocks are byte-identical — the same five rows
-- against the same five NCA-ECC-V2 citations (1-1, 1-2, 2-1, 2-2, 4) with the same coverage
-- and rationale.
--
-- ⚠ A first pass at that check compared TOKEN SETS and reported that V93 was missing
-- ISO-27001, which would have meant moving seed data between files. It was a false gap: this
-- file DECLAREd @iso27001 and never used it — the identifier appears exactly once, in its
-- own DECLARE. No ISO mapping was ever seeded here, so none is lost.
GO
