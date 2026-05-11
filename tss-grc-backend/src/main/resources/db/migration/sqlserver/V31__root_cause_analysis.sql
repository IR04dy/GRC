-- V31 — Root Cause Analysis (Saudi GRC prompt §3.8 + §7.6 #6).
--
-- Free-text `lessonsLearned` on incidents/breaches is too thin for the
-- regulatory evidence package. An RCA captures the methodology
-- (5-whys / fishbone / pareto / other), facilitator + participants, the
-- contributing factors, the identified root cause(s), and the corrective
-- recommendations — bound to a parent entity (incident, data breach, or
-- BCM invocation).
--
-- Lifecycle: DRAFT → IN_REVIEW → APPROVED | REJECTED
--   DRAFT     — facilitator drafting; can update freely
--   IN_REVIEW — submitted for sign-off
--   APPROVED  — locked; CAPAs spawned from recommendations
--   REJECTED  — terminal; reason captured for audit
--
-- Lives under schema `incident` because the dominant link is to incidents,
-- but `related_entity_type` allows other modules to attach RCAs too.

SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'incident.root_cause_analyses', N'U') IS NULL
BEGIN
    CREATE TABLE incident.root_cause_analyses (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        -- Parent record. Free-form type lets data-breach + BCM modules attach RCAs
        -- without an FK explosion. Common values:
        --   incident, data_breach, bcm_invocation, supplier_incident
        related_entity_type      NVARCHAR(50)  NOT NULL,
        related_entity_id        BIGINT        NOT NULL,
        related_entity_label     NVARCHAR(255) NULL,

        -- Methodology drives the UI affordance shown to the facilitator.
        -- FIVE_WHYS  — populates five_whys_json
        -- FISHBONE   — populates contributing_factors_json grouped by 6Ms
        -- PARETO     — populates contributing_factors_json with frequency weights
        -- OTHER      — free-form narrative only
        methodology              NVARCHAR(20)  NOT NULL DEFAULT N'FIVE_WHYS',

        problem_statement_en     NVARCHAR(MAX) NOT NULL,
        problem_statement_ar     NVARCHAR(MAX) NULL,

        /*
         * JSON shape varies by methodology:
         *   five_whys_json: [{ "question": "...", "answer": "..." }, ...]
         *   contributing_factors_json (fishbone):
         *     [{ "category": "PEOPLE|PROCESS|TECHNOLOGY|...", "factor": "..." }, ...]
         *   contributing_factors_json (pareto):
         *     [{ "factor": "...", "frequency": 12 }, ...]
         */
        five_whys_json           NVARCHAR(MAX) NULL,
        contributing_factors_json NVARCHAR(MAX) NULL,

        root_cause_en            NVARCHAR(MAX) NULL,
        root_cause_ar            NVARCHAR(MAX) NULL,
        recommendations_en       NVARCHAR(MAX) NULL,
        recommendations_ar       NVARCHAR(MAX) NULL,

        -- People + timing.
        facilitator              NVARCHAR(255) NULL,
        participants             NVARCHAR(MAX) NULL,
        conducted_at             DATETIME2(3)  NULL,

        -- Linked CAPA refs (CSV of CAPA codes) — denormalised for fast read.
        capa_refs                NVARCHAR(MAX) NULL,

        -- Lifecycle.
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        submitted_at             DATETIME2(3)  NULL,
        submitted_by             BIGINT        NULL,
        approved_at              DATETIME2(3)  NULL,
        approved_by              BIGINT        NULL,
        approver_name            NVARCHAR(255) NULL,
        rejection_reason         NVARCHAR(500) NULL,

        -- BaseEntity audit fields.
        external_id              NVARCHAR(64)  NULL,
        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_rca_status CHECK (status IN (N'DRAFT', N'IN_REVIEW', N'APPROVED', N'REJECTED')),
        CONSTRAINT CK_rca_method CHECK (methodology IN (N'FIVE_WHYS', N'FISHBONE', N'PARETO', N'OTHER')),
        CONSTRAINT CK_rca_rejection_reason CHECK (status <> N'REJECTED' OR rejection_reason IS NOT NULL)
    );

    CREATE INDEX IX_rca_org_status     ON incident.root_cause_analyses (organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_rca_related_entity ON incident.root_cause_analyses (organisation_id, related_entity_type, related_entity_id);
END;
GO
