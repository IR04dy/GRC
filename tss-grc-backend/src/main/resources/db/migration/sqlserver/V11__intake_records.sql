-- DD-82: Intake substrate persistence.
--
-- Centralised front-door for every kind of new submission across the
-- platform: customer DSRs, employee security-event reports, vendor
-- onboarding, BIA submissions, change requests, etc. Each row is the
-- canonical record before the submission is triaged and promoted to
-- a kind-specific register (incident, risk, vendor, etc.).
--
-- Architecture mirrors:
--   industry-standard GRC tooling Service Catalog tables (request[item] + variables)
--   industry-standard tooling Privacy intake table
--   NAVEX EthicsPoint case table
--
-- This migration creates only the parent table. Promoted-record back-
-- references and stage-history tables ship in subsequent migrations.

CREATE SCHEMA intake;
GO

CREATE TABLE intake.intake_records (
    id                        BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id           BIGINT NOT NULL,

    -- Stable, anonymous-friendly tracking code shown to the submitter.
    -- Format: "{KIND-PREFIX}-XXXX-YYYY". Indexed for case-tracker lookup.
    reference_code            NVARCHAR(40) NOT NULL,

    -- Drives form schema, SLA, default assignee, and target register.
    -- Mirrors the IntakeKind union on the frontend.
    kind                      NVARCHAR(40) NOT NULL,

    -- SUBMITTED | TRIAGING | PROMOTED | REJECTED | CLOSED
    status                    NVARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',

    -- WEB_FORM | EMAIL | PHONE | IN_PERSON | API_INGESTION | MOBILE
    channel                   NVARCHAR(20) NOT NULL,

    submitter_is_anonymous    BIT NOT NULL DEFAULT 0,
    submitter_role            NVARCHAR(20),
    submitter_name            NVARCHAR(200),
    submitter_email           NVARCHAR(254),
    submitter_phone           NVARCHAR(50),

    -- Form payload as JSON. Validated against the kind's schema by the
    -- service layer before persistence. SQL Server 2019 stores this in
    -- NVARCHAR(MAX) and the JSON_VALUE() / OPENJSON() functions provide
    -- query support without requiring a JSON-typed column.
    payload                   NVARCHAR(MAX),

    -- Triage routing (populated on submission via routeFor()).
    assigned_to               NVARCHAR(50),
    sla_due_at                DATETIME2,
    severity                  NVARCHAR(20),

    acknowledged_at           DATETIME2 NOT NULL,

    -- Promotion outcome (set when the triager promotes to a register).
    promoted_to_entity_type   NVARCHAR(30),
    promoted_to_record_id     NVARCHAR(40),
    promoted_at               DATETIME2,

    -- Rejection reason (when triager rejects).
    rejection_reason          NVARCHAR(MAX),

    -- Reply locale at submission time. Drives email reply language.
    locale                    NVARCHAR(2),

    -- Audit columns mirroring the rest of the platform.
    created_at                DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2,
    created_by                BIGINT,
    updated_by                BIGINT,
    is_deleted                BIT NOT NULL DEFAULT 0,
    deleted_at                DATETIME2,
    deleted_by                BIGINT,
    row_version               ROWVERSION,

    CONSTRAINT uq_intake_reference_code UNIQUE (reference_code)
);
GO

CREATE INDEX ix_intake_org_status     ON intake.intake_records (organisation_id, status) WHERE is_deleted = 0;
CREATE INDEX ix_intake_org_kind       ON intake.intake_records (organisation_id, kind)   WHERE is_deleted = 0;
CREATE INDEX ix_intake_sla_open       ON intake.intake_records (sla_due_at)
    WHERE is_deleted = 0 AND status IN ('SUBMITTED', 'TRIAGING');
CREATE INDEX ix_intake_promoted       ON intake.intake_records (promoted_to_entity_type, promoted_to_record_id)
    WHERE is_deleted = 0 AND promoted_to_entity_type IS NOT NULL;
GO

-- ── intake_events ─────────────────────────────────────────────────────
-- Append-only audit trail. Every status transition (auto-acknowledged,
-- triager decision, etc.) writes a row here. Used to render the
-- case-tracker timeline for anonymous submitters.

CREATE TABLE intake.intake_events (
    id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    intake_record_id  BIGINT NOT NULL,
    at                DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    status            NVARCHAR(20) NOT NULL,
    actor             NVARCHAR(254) NOT NULL,
    note              NVARCHAR(MAX),

    CONSTRAINT fk_intake_event_record
        FOREIGN KEY (intake_record_id)
        REFERENCES intake.intake_records (id)
);
GO

CREATE INDEX ix_intake_events_record ON intake.intake_events (intake_record_id, at);
GO
