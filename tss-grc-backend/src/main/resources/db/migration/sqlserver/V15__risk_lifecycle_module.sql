-- V15 — Risk Lifecycle module backend tables.
--
-- Templates the backend pattern for the 5 lifecycle workspaces (risk,
-- audit, privacy, BCM, cyber). Risk first because treatment plans are the
-- highest-touch entity; the other 4 follow this same shape (entity table
-- + activity log + soft delete + multi-tenant via organisation_id).
--
-- Per CLAUDE.md: dev runs without Flyway (`spring.flyway.enabled=false`),
-- so this migration applies on staging/prod profiles only. The setup_*.py
-- scripts at the repo root are the dev-mode equivalent.

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'risk_lc')
    EXEC('CREATE SCHEMA risk_lc');
GO

-- ─── Treatment plans ────────────────────────────────────────────────────────
CREATE TABLE risk_lc.treatment_plans (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id     NVARCHAR(64)  NOT NULL,                  -- TP-2026-001
    organisation_id BIGINT        NOT NULL,
    risk_id         BIGINT        NOT NULL,                  -- FK to risk.risks
    strategy        NVARCHAR(20)  NOT NULL,                  -- MITIGATE/TRANSFER/AVOID/ACCEPT
    rationale_en    NVARCHAR(MAX) NOT NULL,
    rationale_ar    NVARCHAR(MAX) NOT NULL,
    status          NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/SUBMITTED/APPROVED/IN_PROGRESS/COMPLETED/REJECTED
    cost_sar_k      DECIMAL(12,2) NOT NULL DEFAULT 0,
    cost_benefit_en NVARCHAR(MAX),
    cost_benefit_ar NVARCHAR(MAX),
    created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    submitted_at    DATETIME2,
    approved_by     NVARCHAR(255),
    approved_at     DATETIME2,
    rejected_reason NVARCHAR(MAX),
    is_deleted      SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_tp_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_tp_org_status ON risk_lc.treatment_plans (organisation_id, status, is_deleted);
CREATE INDEX ix_tp_risk      ON risk_lc.treatment_plans (organisation_id, risk_id, is_deleted);
GO

-- ─── Treatment actions (1:N from treatment_plans) ──────────────────────────
CREATE TABLE risk_lc.treatment_actions (
    id                     BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id            NVARCHAR(64)  NOT NULL,
    organisation_id        BIGINT        NOT NULL,
    plan_id                BIGINT        NOT NULL,
    title_en               NVARCHAR(500) NOT NULL,
    title_ar               NVARCHAR(500) NOT NULL,
    owner_role             NVARCHAR(255) NOT NULL,
    target_date            DATE          NOT NULL,
    status                 NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/IN_PROGRESS/COMPLETED/BLOCKED
    expected_risk_reduction DECIMAL(4,3) NOT NULL DEFAULT 0,           -- 0..1
    completed_at           DATETIME2,
    evidence_ref           NVARCHAR(64),
    is_deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_ta_plan FOREIGN KEY (plan_id) REFERENCES risk_lc.treatment_plans(id),
    CONSTRAINT uk_ta_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_ta_plan ON risk_lc.treatment_actions (plan_id, is_deleted);
GO

-- ─── KRI breach events ──────────────────────────────────────────────────────
CREATE TABLE risk_lc.kri_breaches (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,
    organisation_id   BIGINT        NOT NULL,
    risk_id           BIGINT        NOT NULL,
    kri_code          NVARCHAR(64)  NOT NULL,
    kri_name_en       NVARCHAR(500) NOT NULL,
    kri_name_ar       NVARCHAR(500) NOT NULL,
    threshold_value   DECIMAL(18,4) NOT NULL,
    actual_value      DECIMAL(18,4) NOT NULL,
    direction         NVARCHAR(10)  NOT NULL,                          -- UP_BAD/DOWN_BAD
    detected_at       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    status            NVARCHAR(20)  NOT NULL DEFAULT 'OPEN',           -- OPEN/INVESTIGATING/RESOLVED/ESCALATED
    current_owner     NVARCHAR(255) NOT NULL,
    resolution_en     NVARCHAR(MAX),
    resolution_ar     NVARCHAR(MAX),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_kb_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_kb_org_status ON risk_lc.kri_breaches (organisation_id, status, is_deleted);
GO

-- ─── KRI escalation log (immutable audit chain) ────────────────────────────
CREATE TABLE risk_lc.kri_escalation_log (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    breach_id   BIGINT        NOT NULL,
    step_en     NVARCHAR(MAX) NOT NULL,
    step_ar     NVARCHAR(MAX) NOT NULL,
    to_owner    NVARCHAR(255) NOT NULL,
    at_ts       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_kel_breach FOREIGN KEY (breach_id) REFERENCES risk_lc.kri_breaches(id)
);
CREATE INDEX ix_kel_breach ON risk_lc.kri_escalation_log (breach_id, at_ts);
GO

-- ─── Acceptance requests ──────────────────────────────────────────────────
CREATE TABLE risk_lc.acceptance_requests (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(64)  NOT NULL,
    organisation_id     BIGINT        NOT NULL,
    risk_id             BIGINT        NOT NULL,
    rationale_en        NVARCHAR(MAX) NOT NULL,
    rationale_ar        NVARCHAR(MAX) NOT NULL,
    residual_score      DECIMAL(8,2)  NOT NULL,
    appetite_upper      DECIMAL(8,2)  NOT NULL,
    status              NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',        -- DRAFT/CRO_REVIEW/BOARD_APPROVAL/APPROVED/EXPIRED/WITHDRAWN
    cro_signed_by       NVARCHAR(255),
    cro_signed_at       DATETIME2,
    board_signed_by     NVARCHAR(255),
    board_signed_at     DATETIME2,
    expires_at          DATE          NOT NULL,
    expires_at_hijri    NVARCHAR(50),
    conditions_en       NVARCHAR(MAX),
    conditions_ar       NVARCHAR(MAX),
    created_at          DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted          SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ar_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_ar_org_status ON risk_lc.acceptance_requests (organisation_id, status, is_deleted);
CREATE INDEX ix_ar_expires    ON risk_lc.acceptance_requests (organisation_id, expires_at, is_deleted);
GO

-- ─── Generic activity log (template for all 5 lifecycle modules) ───────────
-- This table is shared across all lifecycle modules — entity_type discriminates.
IF OBJECT_ID('risk_lc.activity_log', 'U') IS NULL
BEGIN
    CREATE TABLE risk_lc.activity_log (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT        NOT NULL,
        entity_type     NVARCHAR(50)  NOT NULL,           -- treatment-plan, kri-breach, acceptance, finding, dsr, …
        entity_id       NVARCHAR(64)  NOT NULL,           -- external_id of the entity
        action          NVARCHAR(50)  NOT NULL,           -- CREATED/TRANSITIONED/ASSIGNED/COMMENTED/CLOSED/REOPENED/NOTIFIED/EVIDENCE_ADDED
        from_status     NVARCHAR(50),
        to_status       NVARCHAR(50),
        assignee_id     NVARCHAR(64),
        note_en         NVARCHAR(MAX),
        note_ar         NVARCHAR(MAX),
        user_id         NVARCHAR(64)  NOT NULL,           -- user that did this
        ts              DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX ix_al_entity ON risk_lc.activity_log (organisation_id, entity_type, entity_id, ts);
    CREATE INDEX ix_al_user   ON risk_lc.activity_log (organisation_id, user_id, ts);
END
GO

-- ─── Notification outbox (template for the platform-wide outbox) ──────────
IF OBJECT_ID('risk_lc.notification_outbox', 'U') IS NULL
BEGIN
    CREATE TABLE risk_lc.notification_outbox (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id     NVARCHAR(64)  NOT NULL,
        organisation_id BIGINT        NOT NULL,
        recipient_id    NVARCHAR(64)  NOT NULL,
        channels        NVARCHAR(255) NOT NULL,           -- comma-sep list IN_APP,EMAIL,SMS,...
        priority        NVARCHAR(4)   NOT NULL,           -- P1..P4
        subject_en      NVARCHAR(500) NOT NULL,
        subject_ar      NVARCHAR(500) NOT NULL,
        body_en         NVARCHAR(MAX) NOT NULL,
        body_ar         NVARCHAR(MAX) NOT NULL,
        link            NVARCHAR(500),
        source          NVARCHAR(100) NOT NULL,
        ref_id          NVARCHAR(64),
        status          NVARCHAR(20)  NOT NULL DEFAULT 'QUEUED',   -- QUEUED/SENDING/SENT/FAILED/CANCELLED
        created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        sent_at         DATETIME2,
        read_at         DATETIME2,
        failure_reason  NVARCHAR(MAX),
        CONSTRAINT uk_nox_external UNIQUE (organisation_id, external_id)
    );
    CREATE INDEX ix_nox_org_status ON risk_lc.notification_outbox (organisation_id, status, created_at);
    CREATE INDEX ix_nox_recipient  ON risk_lc.notification_outbox (organisation_id, recipient_id, read_at);
END
GO

-- ─── Sample seed (one treatment plan + one breach + one acceptance) ───────
DECLARE @org_id BIGINT = (SELECT TOP 1 id FROM platform.organisations);
IF @org_id IS NOT NULL
BEGIN
    INSERT INTO risk_lc.treatment_plans
        (external_id, organisation_id, risk_id, strategy, rationale_en, rationale_ar, status, cost_sar_k, cost_benefit_en, cost_benefit_ar, submitted_at, approved_by, approved_at)
    VALUES
        ('TP-2026-001', @org_id, 1, 'MITIGATE',
         'Reduce ransomware likelihood through MFA on all privileged accounts + EDR rollout to remaining 12% of fleet.',
         N'تقليل احتمالية الفدية عبر فرض المصادقة متعدّدة العوامل على جميع الحسابات المُميَّزة ونشر EDR على 12٪ المتبقّية.',
         'IN_PROGRESS', 1400,
         'SAR 1.4M one-time + SAR 280k/yr OpEx vs avoided expected loss ~SAR 8.2M (3-yr horizon).',
         N'1.4 مليون ريال لمرّة واحدة + 280 ألف ريال سنوياً مقابل خسارة متوقّعة مُتجنَّبة نحو 8.2 مليون ريال (3 سنوات).',
         DATEADD(day, -15, SYSUTCDATETIME()),
         'Sara Al-Zahrani (CISO) + Mohammed Al-Mahmoud (CRO)',
         DATEADD(day, -12, SYSUTCDATETIME()));
END
GO
