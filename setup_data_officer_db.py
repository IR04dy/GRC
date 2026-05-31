"""Sprint 33 — Saudi Data Officer Toolkit schema bootstrap.

Creates the data_officer.* schema and all tables backing the 10 sub-modules:

  DSR             — Data Subject Rights (PDPL Art. 9-14, 30-day SLA)
  Consent         — Consent registry (PDPL Art. 5-6, opt-in + withdrawal)
  DPIA            — Data Protection Impact Assessment (PDPL Art. 25)
  Breach          — Breach notification with 72-hour SDAIA clock (PDPL Art. 20)
  PrivacyNotice   — Bilingual versioned notices (PDPL Art. 12)
  Retention       — Retention/disposal schedule (PDPL Art. 18)
  NDR             — National Data Register annual submission (NDMO+SDAIA NDR)
  Catalog         — Data dictionary + lineage (NDMO Data Mgmt)
  Quality         — Data quality rule + run (NDMO DQM)
  Ethics          — Data Ethics Committee review (SDAIA AI Ethics + NDMO Data Ethics)

Idempotent: every CREATE is guarded by IF NOT EXISTS / OBJECT_ID checks so the
script can run on a fresh DB or as a re-bootstrap. Seed rows under a separate
setup_data_officer_seed.py.
"""
from __future__ import annotations
import pyodbc

CONN_STR = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;"
    "Encrypt=yes;TrustServerCertificate=yes;"
)

DDL = r"""
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'data_officer')
    EXEC('CREATE SCHEMA data_officer');

-- ─── Shared timeline / audit ─────────────────────────────────────────
IF OBJECT_ID('data_officer.do_event', 'U') IS NULL
CREATE TABLE data_officer.do_event (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    entity_type         NVARCHAR(40)    NOT NULL,   -- DSR / CONSENT / DPIA / BREACH / ...
    entity_id           BIGINT          NOT NULL,
    occurred_at         DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    event_type          NVARCHAR(64)    NOT NULL,
    actor_user_id       BIGINT,
    status_after        NVARCHAR(32),
    note_en             NVARCHAR(MAX),
    note_ar             NVARCHAR(MAX),
    payload_json        NVARCHAR(MAX),
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_do_event_lookup' AND object_id=OBJECT_ID('data_officer.do_event'))
    CREATE INDEX ix_do_event_lookup ON data_officer.do_event(organisation_id, entity_type, entity_id, occurred_at DESC);

-- ─── 1. DSR — Data Subject Rights (PDPL Art. 9-14) ───────────────────
IF OBJECT_ID('data_officer.dsr_request', 'U') IS NULL
CREATE TABLE data_officer.dsr_request (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    request_type        NVARCHAR(24)    NOT NULL,   -- ACCESS / RECTIFY / ERASE / RESTRICT / PORTABILITY / OBJECT
    channel             NVARCHAR(24),               -- WEB / MOBILE / BRANCH / EMAIL / CALL / SDAIA_PORTAL
    requester_name_en   NVARCHAR(256),
    requester_name_ar   NVARCHAR(256),
    requester_national_id NVARCHAR(20),             -- masked at rest in real prod
    requester_email     NVARCHAR(256),
    requester_phone     NVARCHAR(40),
    request_summary_en  NVARCHAR(MAX),
    request_summary_ar  NVARCHAR(MAX),
    status              NVARCHAR(32)    NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, ID_VERIFIED, IN_PROGRESS, FULFILLED, PARTIAL, REJECTED, ESCALATED_TO_SDAIA, CLOSED
    received_at         DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    sla_deadline        DATETIME2,                  -- received_at + 30 days
    id_verified_at      DATETIME2,
    id_verification_method NVARCHAR(40),            -- NAFATH / IN_BRANCH_KYC / SIGNED_PDF
    fulfilled_at        DATETIME2,
    rejection_reason_en NVARCHAR(MAX),
    rejection_reason_ar NVARCHAR(MAX),
    rejection_basis     NVARCHAR(40),               -- PDPL Art.10/15/16 etc.
    assigned_user_id    BIGINT,
    linked_systems_json NVARCHAR(MAX),              -- which systems were searched
    response_text_en    NVARCHAR(MAX),
    response_text_ar    NVARCHAR(MAX),
    response_attachment_evidence_id BIGINT,         -- pointer to evidence
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_dsr_org_ref UNIQUE (organisation_id, reference)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_dsr_status' AND object_id=OBJECT_ID('data_officer.dsr_request'))
    CREATE INDEX ix_dsr_status ON data_officer.dsr_request(organisation_id, status, sla_deadline);

-- ─── 2. Consent Management (PDPL Art. 5-6) ───────────────────────────
IF OBJECT_ID('data_officer.consent_record', 'U') IS NULL
CREATE TABLE data_officer.consent_record (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    data_subject_id     NVARCHAR(64),               -- national-id / customer-no — hashed/pseudonymized
    purpose_code        NVARCHAR(40)    NOT NULL,   -- MARKETING / CREDIT_DECISION / PROFILING / etc.
    purpose_label_en    NVARCHAR(256),
    purpose_label_ar    NVARCHAR(256),
    notice_version_id   BIGINT,                     -- FK to privacy_notice.version
    channel             NVARCHAR(24),               -- WEB / MOBILE / BRANCH / IVR / ATM
    consent_text_en     NVARCHAR(MAX),
    consent_text_ar     NVARCHAR(MAX),
    granted_at          DATETIME2,
    granted_evidence    NVARCHAR(256),              -- IP+UA / signature reference / branch officer ID
    withdrawn_at        DATETIME2,
    withdrawn_reason    NVARCHAR(256),
    valid_until         DATETIME2,                  -- explicit expiry if purpose-bound
    status              NVARCHAR(24)    NOT NULL DEFAULT 'GRANTED', -- GRANTED, WITHDRAWN, EXPIRED, SUPERSEDED
    legal_basis         NVARCHAR(40),               -- CONSENT / CONTRACT / LEGAL_OBLIGATION / VITAL_INTEREST / PUBLIC_INTEREST / LEGITIMATE_INTEREST
    is_special_category BIT             NOT NULL DEFAULT 0,
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_consent_org_ref UNIQUE (organisation_id, reference)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_consent_subject' AND object_id=OBJECT_ID('data_officer.consent_record'))
    CREATE INDEX ix_consent_subject ON data_officer.consent_record(organisation_id, data_subject_id, purpose_code, status);

-- ─── 3. DPIA — Data Protection Impact Assessment (PDPL Art. 25) ──────
IF OBJECT_ID('data_officer.dpia', 'U') IS NULL
CREATE TABLE data_officer.dpia (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    title_en            NVARCHAR(256)   NOT NULL,
    title_ar            NVARCHAR(256)   NOT NULL,
    processing_description_en NVARCHAR(MAX),
    processing_description_ar NVARCHAR(MAX),
    trigger_reason      NVARCHAR(40)    NOT NULL,   -- NEW_PROCESSING / SCOPE_CHANGE / NEW_TECHNOLOGY / SPECIAL_CATEGORY / LARGE_SCALE / etc.
    is_high_risk        BIT             NOT NULL DEFAULT 0,
    status              NVARCHAR(24)    NOT NULL DEFAULT 'DRAFT', -- DRAFT, DPO_REVIEW, BUSINESS_REVIEW, APPROVED, APPROVED_WITH_CONDITIONS, REJECTED, ARCHIVED
    data_owner_user_id  BIGINT,
    dpo_user_id         BIGINT,
    submitted_at        DATETIME2,
    dpo_opinion_en      NVARCHAR(MAX),
    dpo_opinion_ar      NVARCHAR(MAX),
    dpo_opinion_at      DATETIME2,
    decided_at          DATETIME2,
    decision_rationale_en NVARCHAR(MAX),
    decision_rationale_ar NVARCHAR(MAX),
    conditions_en       NVARCHAR(MAX),
    conditions_ar       NVARCHAR(MAX),
    next_review_due     DATE,
    linked_processing_activity_id BIGINT,           -- ROPA reference
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_dpia_org_ref UNIQUE (organisation_id, reference)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_dpia_status' AND object_id=OBJECT_ID('data_officer.dpia'))
    CREATE INDEX ix_dpia_status ON data_officer.dpia(organisation_id, status, next_review_due);

-- ─── 4. Breach Notification (PDPL Art. 20 — 72-hour SDAIA clock) ─────
IF OBJECT_ID('data_officer.breach', 'U') IS NULL
CREATE TABLE data_officer.breach (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    title_en            NVARCHAR(256)   NOT NULL,
    title_ar            NVARCHAR(256)   NOT NULL,
    detected_at         DATETIME2       NOT NULL,
    discovered_at       DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    sdaia_deadline      DATETIME2,                  -- detected_at + 72h
    status              NVARCHAR(24)    NOT NULL DEFAULT 'DETECTED', -- DETECTED, ASSESSING, NOTIFIED_SDAIA, NOTIFYING_SUBJECTS, CONTAINED, REMEDIATING, CLOSED, FALSE_POSITIVE
    breach_type         NVARCHAR(40),               -- CONFIDENTIALITY / INTEGRITY / AVAILABILITY
    cause               NVARCHAR(40),               -- INSIDER / EXTERNAL_ATTACK / SYSTEM_FAILURE / MISCONFIGURATION / PROCESSOR_ERROR / LOST_DEVICE
    severity            NVARCHAR(16),               -- LOW / MEDIUM / HIGH / CRITICAL
    affected_record_count BIGINT,
    affected_subject_count BIGINT,
    affected_categories_json NVARCHAR(MAX),         -- ["FINANCIAL","HEALTH","BIOMETRIC",...]
    is_special_category BIT             NOT NULL DEFAULT 0,
    is_cross_border     BIT             NOT NULL DEFAULT 0,
    description_en      NVARCHAR(MAX),
    description_ar      NVARCHAR(MAX),
    containment_actions_en NVARCHAR(MAX),
    containment_actions_ar NVARCHAR(MAX),
    sdaia_notified_at   DATETIME2,
    sdaia_reference     NVARCHAR(64),               -- SDAIA case number
    subjects_notified_at DATETIME2,
    subject_notification_template_en NVARCHAR(MAX),
    subject_notification_template_ar NVARCHAR(MAX),
    closed_at           DATETIME2,
    closure_summary_en  NVARCHAR(MAX),
    closure_summary_ar  NVARCHAR(MAX),
    linked_incident_id  BIGINT,                     -- incident-mgmt cross-ref
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_breach_org_ref UNIQUE (organisation_id, reference)
);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_breach_status' AND object_id=OBJECT_ID('data_officer.breach'))
    CREATE INDEX ix_breach_status ON data_officer.breach(organisation_id, status, sdaia_deadline);

-- ─── 5. Privacy Notice (PDPL Art. 12 — versioned bilingual) ──────────
IF OBJECT_ID('data_officer.privacy_notice', 'U') IS NULL
CREATE TABLE data_officer.privacy_notice (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    version_label       NVARCHAR(40)    NOT NULL,   -- e.g. "v3.2-2026-Q2"
    channel             NVARCHAR(24)    NOT NULL,   -- WEB / MOBILE / BRANCH / IVR / ATM / SMS / EMAIL
    title_en            NVARCHAR(256)   NOT NULL,
    title_ar            NVARCHAR(256)   NOT NULL,
    body_en             NVARCHAR(MAX)   NOT NULL,
    body_ar             NVARCHAR(MAX)   NOT NULL,
    status              NVARCHAR(24)    NOT NULL DEFAULT 'DRAFT', -- DRAFT, IN_REVIEW, PUBLISHED, SUPERSEDED, RETIRED
    effective_from      DATE,
    published_at        DATETIME2,
    superseded_by_id    BIGINT,
    legal_review_user_id BIGINT,
    requires_re_consent BIT             NOT NULL DEFAULT 0,        -- if material change → re-consent
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_notice_org_ref UNIQUE (organisation_id, reference)
);

-- ─── 6. Retention & Disposal Schedule (PDPL Art. 18) ─────────────────
IF OBJECT_ID('data_officer.retention_rule', 'U') IS NULL
CREATE TABLE data_officer.retention_rule (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    data_category       NVARCHAR(64)    NOT NULL,   -- e.g. CUSTOMER_KYC, TRANSACTION_LOG, MARKETING_PROFILE
    description_en      NVARCHAR(MAX),
    description_ar      NVARCHAR(MAX),
    retention_months    INT             NOT NULL,
    legal_basis_en      NVARCHAR(MAX),
    legal_basis_ar      NVARCHAR(MAX),
    disposal_method     NVARCHAR(32),               -- CRYPTO_SHRED / PHYSICAL_DESTRUCTION / OVERWRITE / ANONYMISE
    review_cadence_months INT,
    next_review_due     DATE,
    owner_role          NVARCHAR(64),
    owner_user_id       BIGINT,
    linked_asset_ref    NVARCHAR(64),               -- dataclass asset reference
    status              NVARCHAR(24)    NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, UNDER_REVIEW, RETIRED
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_retention_org_ref UNIQUE (organisation_id, reference)
);

IF OBJECT_ID('data_officer.disposal_job', 'U') IS NULL
CREATE TABLE data_officer.disposal_job (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    retention_rule_id   BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    scheduled_for       DATE            NOT NULL,
    started_at          DATETIME2,
    completed_at        DATETIME2,
    status              NVARCHAR(24)    NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, ON_HOLD, IN_PROGRESS, COMPLETED, FAILED
    records_disposed    BIGINT,
    evidence_hash       NVARCHAR(128),
    legal_hold_reason   NVARCHAR(MAX),
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_disposal_org_ref UNIQUE (organisation_id, reference)
);

-- ─── 7. NDR — National Data Register annual submission ───────────────
IF OBJECT_ID('data_officer.ndr_submission', 'U') IS NULL
CREATE TABLE data_officer.ndr_submission (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    cycle_year          INT             NOT NULL,
    title_en            NVARCHAR(256),
    title_ar            NVARCHAR(256),
    status              NVARCHAR(24)    NOT NULL DEFAULT 'DRAFT', -- DRAFT, INTERNAL_REVIEW, READY, SUBMITTED, ACCEPTED, REJECTED, REVISION_REQUESTED
    dataset_count       INT             NOT NULL DEFAULT 0,
    submitted_at        DATETIME2,
    submission_ref      NVARCHAR(64),               -- SDAIA NDR portal reference
    accepted_at         DATETIME2,
    response_summary_en NVARCHAR(MAX),
    response_summary_ar NVARCHAR(MAX),
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_ndr_org_ref UNIQUE (organisation_id, reference)
);

-- ─── 8. Data Catalog + Lineage (NDMO Data Management) ────────────────
IF OBJECT_ID('data_officer.catalog_entry', 'U') IS NULL
CREATE TABLE data_officer.catalog_entry (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    dataset_name_en     NVARCHAR(256)   NOT NULL,
    dataset_name_ar     NVARCHAR(256),
    description_en      NVARCHAR(MAX),
    description_ar      NVARCHAR(MAX),
    domain              NVARCHAR(40),               -- CUSTOMER / CREDIT / PAYMENT / RISK / etc.
    source_system       NVARCHAR(128),
    storage_location    NVARCHAR(256),
    classification      NVARCHAR(16),               -- PUBLIC / INTERNAL / CONFIDENTIAL / TOP_SECRET
    contains_pii        BIT             NOT NULL DEFAULT 0,
    contains_special_category BIT       NOT NULL DEFAULT 0,
    owner_role          NVARCHAR(64),
    owner_user_id       BIGINT,
    steward_user_id     BIGINT,
    custodian_team      NVARCHAR(128),
    row_count_approx    BIGINT,
    refresh_frequency   NVARCHAR(40),               -- REAL_TIME / HOURLY / DAILY / WEEKLY / MONTHLY
    last_profiled_at    DATETIME2,
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_cat_org_ref UNIQUE (organisation_id, reference)
);

IF OBJECT_ID('data_officer.catalog_lineage', 'U') IS NULL
CREATE TABLE data_officer.catalog_lineage (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    source_entry_id     BIGINT          NOT NULL,
    target_entry_id     BIGINT          NOT NULL,
    transform_label     NVARCHAR(256),
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT
);

-- ─── 9. Data Quality (NDMO DQM domain) ───────────────────────────────
IF OBJECT_ID('data_officer.quality_rule', 'U') IS NULL
CREATE TABLE data_officer.quality_rule (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    catalog_entry_id    BIGINT          NOT NULL,
    rule_name_en        NVARCHAR(256)   NOT NULL,
    rule_name_ar        NVARCHAR(256),
    dimension           NVARCHAR(24)    NOT NULL,   -- COMPLETENESS / UNIQUENESS / ACCURACY / VALIDITY / CONSISTENCY / TIMELINESS / INTEGRITY
    rule_expression     NVARCHAR(MAX),              -- DQ rule (e.g. SQL predicate)
    threshold_pct       DECIMAL(5,2),               -- pass threshold
    severity            NVARCHAR(16),               -- LOW / MEDIUM / HIGH / CRITICAL
    is_active           BIT             NOT NULL DEFAULT 1,
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_dqr_org_ref UNIQUE (organisation_id, reference)
);

IF OBJECT_ID('data_officer.quality_run', 'U') IS NULL
CREATE TABLE data_officer.quality_run (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    quality_rule_id     BIGINT          NOT NULL,
    run_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    records_checked     BIGINT,
    records_failed      BIGINT,
    pass_pct            DECIMAL(5,2),
    band                NVARCHAR(8),                -- GREEN / AMBER / RED
    notes_en            NVARCHAR(MAX),
    notes_ar            NVARCHAR(MAX),
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT
);

-- ─── 10. Data Ethics Committee (SDAIA AI Ethics + NDMO Data Ethics) ──
IF OBJECT_ID('data_officer.ethics_review', 'U') IS NULL
CREATE TABLE data_officer.ethics_review (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id     BIGINT          NOT NULL,
    reference           NVARCHAR(64)    NOT NULL,
    subject_type        NVARCHAR(40),               -- AI_MODEL / DATA_USE_CASE / NEW_PROCESSING / OPEN_DATA_RELEASE
    subject_ref         NVARCHAR(128),              -- pointer to the AI model / dataset / use case
    title_en            NVARCHAR(256)   NOT NULL,
    title_ar            NVARCHAR(256)   NOT NULL,
    summary_en          NVARCHAR(MAX),
    summary_ar          NVARCHAR(MAX),
    requested_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    status              NVARCHAR(24)    NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, COMMITTEE_REVIEW, OPINION_DRAFTED, APPROVED, APPROVED_WITH_CONDITIONS, REJECTED, ESCALATED
    risk_dimensions_json NVARCHAR(MAX),             -- {"fairness":"HIGH","privacy":"MEDIUM",...}
    committee_meeting_at DATETIME2,
    opinion_en          NVARCHAR(MAX),
    opinion_ar          NVARCHAR(MAX),
    conditions_en       NVARCHAR(MAX),
    conditions_ar       NVARCHAR(MAX),
    decided_at          DATETIME2,
    next_review_due     DATE,
    is_deleted          BIT             NOT NULL DEFAULT 0,
    created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT uq_ethics_org_ref UNIQUE (organisation_id, reference)
);
"""

def main() -> None:
    print("Sprint 33 — Saudi Data Officer Toolkit schema bootstrap")
    with pyodbc.connect(CONN_STR, autocommit=True) as conn:
        cur = conn.cursor()
        # Execute as one batch so DDL ordering inside a single connection is preserved.
        cur.execute(DDL)
        print("OK data_officer schema + 14 tables ensured")
        # Smoke-check
        for tbl in [
            'do_event','dsr_request','consent_record','dpia','breach',
            'privacy_notice','retention_rule','disposal_job','ndr_submission',
            'catalog_entry','catalog_lineage','quality_rule','quality_run','ethics_review',
        ]:
            cur.execute(f"SELECT COUNT(*) FROM data_officer.{tbl}")
            (n,) = cur.fetchone()
            print(f"   data_officer.{tbl}: {n} rows")

if __name__ == '__main__':
    main()
