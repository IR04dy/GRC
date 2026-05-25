"""Bootstrap the Sharia Governance schema in the dev DB.

Sprint 20 PR #1. Pairs with the JPA entities under
com.tss.grc.module.sharia.entity.

Creates schema + 6 tables + indexes:
  sharia.sharia_scholar            -- SSB member register
  sharia.sharia_resolution         -- formal SSB fatwas
  sharia.sharia_audit_finding      -- internal Sharia audit findings
  sharia.sharia_nc_event           -- Sharia non-compliance + SNCI tracking
  sharia.sharia_product_approval   -- Islamic-product approval pipeline
  sharia.sharia_event              -- chronological timeline

Idempotent: IF NOT EXISTS guards.

Usage:
  python setup_sharia_db.py
"""
from __future__ import annotations

import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()


SCHEMA = (
    "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'sharia') "
    "EXEC('CREATE SCHEMA sharia');"
)

SCHOLAR_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sharia' AND t.name = 'sharia_scholar')
BEGIN
    CREATE TABLE sharia.sharia_scholar (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        full_name_en                NVARCHAR(256)   NOT NULL,
        full_name_ar                NVARCHAR(256)   NOT NULL,
        ssb_role                    NVARCHAR(32)    NOT NULL DEFAULT N'MEMBER',
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'ACTIVE',
        term_start                  DATE,
        term_end                    DATE,
        credentials_en              NVARCHAR(MAX),
        credentials_ar              NVARCHAR(MAX),
        coi_declared_at             DATE,
        coi_notes_en                NVARCHAR(MAX),
        coi_notes_ar                NVARCHAR(MAX),
        annual_fee_sar              DECIMAL(18, 2),
        meetings_attended_ytd       INT             NOT NULL DEFAULT 0,
        email                       NVARCHAR(256),
        phone                       NVARCHAR(32),
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_sharia_scholar_reference
        ON sharia.sharia_scholar (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_sharia_scholar_status
        ON sharia.sharia_scholar (organisation_id, status);
    CREATE INDEX ix_sharia_scholar_term_end
        ON sharia.sharia_scholar (organisation_id, term_end);
END
"""

RESOLUTION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sharia' AND t.name = 'sharia_resolution')
BEGIN
    CREATE TABLE sharia.sharia_resolution (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        subject_area                NVARCHAR(32)    NOT NULL,
        title_en                    NVARCHAR(512)   NOT NULL,
        title_ar                    NVARCHAR(512)   NOT NULL,
        body_en                     NVARCHAR(MAX),
        body_ar                     NVARCHAR(MAX),
        rationale_en                NVARCHAR(MAX),
        rationale_ar                NVARCHAR(MAX),
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'DRAFT',
        submitted_at                DATETIME2,
        ssb_review_started_at       DATETIME2,
        approved_at                 DATETIME2,
        rejected_at                 DATETIME2,
        effective_date              DATE,
        supersedes_resolution_id    BIGINT,
        signing_scholars            NVARCHAR(256),
        meeting_id                  BIGINT,
        rejection_reason_en         NVARCHAR(MAX),
        rejection_reason_ar         NVARCHAR(MAX),
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_sharia_resolution_reference
        ON sharia.sharia_resolution (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_sharia_resolution_status
        ON sharia.sharia_resolution (organisation_id, status, submitted_at DESC);
    CREATE INDEX ix_sharia_resolution_subject
        ON sharia.sharia_resolution (organisation_id, subject_area);
END
"""

FINDING_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sharia' AND t.name = 'sharia_audit_finding')
BEGIN
    CREATE TABLE sharia.sharia_audit_finding (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        audit_cycle_label           NVARCHAR(64),
        severity                    NVARCHAR(12)    NOT NULL DEFAULT N'MEDIUM',
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'OPEN',
        title_en                    NVARCHAR(512)   NOT NULL,
        title_ar                    NVARCHAR(512)   NOT NULL,
        description_en              NVARCHAR(MAX),
        description_ar              NVARCHAR(MAX),
        root_cause_en               NVARCHAR(MAX),
        root_cause_ar               NVARCHAR(MAX),
        remediation_plan_en         NVARCHAR(MAX),
        remediation_plan_ar         NVARCHAR(MAX),
        linked_resolution_id        BIGINT,
        product_line                NVARCHAR(128),
        raised_at                   DATETIME2,
        remediation_due_at          DATE,
        verified_at                 DATETIME2,
        closed_at                   DATETIME2,
        assigned_to_user_id         BIGINT,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_sharia_finding_reference
        ON sharia.sharia_audit_finding (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_sharia_finding_status_sev
        ON sharia.sharia_audit_finding (organisation_id, status, severity, raised_at DESC);
END
"""

NC_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sharia' AND t.name = 'sharia_nc_event')
BEGIN
    CREATE TABLE sharia.sharia_nc_event (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'DETECTED',
        severity                    NVARCHAR(12)    NOT NULL DEFAULT N'MEDIUM',
        title_en                    NVARCHAR(512)   NOT NULL,
        title_ar                    NVARCHAR(512)   NOT NULL,
        description_en              NVARCHAR(MAX),
        description_ar              NVARCHAR(MAX),
        product_line                NVARCHAR(128),
        linked_resolution_id        BIGINT,
        detected_at                 DATETIME2       NOT NULL,
        detection_source            NVARCHAR(32),
        snci_amount_sar             DECIMAL(18, 2),
        purification_destination    NVARCHAR(256),
        purified_at                 DATE,
        charity_receipt_ref         NVARCHAR(128),
        closed_at                   DATETIME2,
        affected_customer_count     BIGINT,
        assigned_to_user_id         BIGINT,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_sharia_nc_reference
        ON sharia.sharia_nc_event (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_sharia_nc_status
        ON sharia.sharia_nc_event (organisation_id, status, detected_at DESC);
END
"""

PRODUCT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sharia' AND t.name = 'sharia_product_approval')
BEGIN
    CREATE TABLE sharia.sharia_product_approval (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        product_name_en             NVARCHAR(256)   NOT NULL,
        product_name_ar             NVARCHAR(256)   NOT NULL,
        structure                   NVARCHAR(32)    NOT NULL,
        status                      NVARCHAR(24)    NOT NULL DEFAULT N'SUBMITTED',
        description_en              NVARCHAR(MAX),
        description_ar              NVARCHAR(MAX),
        linked_resolution_id        BIGINT,
        submitted_at                DATETIME2,
        ssb_review_started_at       DATETIME2,
        approved_provisional_at     DATETIME2,
        approved_final_at           DATETIME2,
        rejected_at                 DATETIME2,
        launched_at                 DATE,
        decommissioned_at           DATE,
        rejection_reason_en         NVARCHAR(MAX),
        rejection_reason_ar         NVARCHAR(MAX),
        business_owner_user_id      BIGINT,
        sharia_officer_user_id      BIGINT,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_sharia_product_reference
        ON sharia.sharia_product_approval (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_sharia_product_status
        ON sharia.sharia_product_approval (organisation_id, status, submitted_at DESC);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'sharia' AND t.name = 'sharia_event')
BEGIN
    CREATE TABLE sharia.sharia_event (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        scholar_id          BIGINT,
        resolution_id       BIGINT,
        audit_finding_id    BIGINT,
        nc_event_id         BIGINT,
        product_approval_id BIGINT,
        occurred_at         DATETIME2       NOT NULL,
        event_type          NVARCHAR(32)    NOT NULL,
        actor_user_id       BIGINT,
        actor_name          NVARCHAR(256),
        status_after        NVARCHAR(32),
        note_en             NVARCHAR(MAX),
        note_ar             NVARCHAR(MAX),
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT,
        updated_by          BIGINT,
        is_deleted          SMALLINT        NOT NULL DEFAULT 0,
        deleted_at          DATETIME2,
        deleted_by          BIGINT,
        row_version         ROWVERSION
    );
    CREATE INDEX ix_sharia_event_resolution
        ON sharia.sharia_event (organisation_id, resolution_id, occurred_at DESC);
    CREATE INDEX ix_sharia_event_finding
        ON sharia.sharia_event (organisation_id, audit_finding_id, occurred_at DESC);
    CREATE INDEX ix_sharia_event_nc
        ON sharia.sharia_event (organisation_id, nc_event_id, occurred_at DESC);
    CREATE INDEX ix_sharia_event_product
        ON sharia.sharia_event (organisation_id, product_approval_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping sharia schema + 6 tables")
    for label, sql in [
        ("schema",                  SCHEMA),
        ("sharia_scholar",          SCHOLAR_TABLE),
        ("sharia_resolution",       RESOLUTION_TABLE),
        ("sharia_audit_finding",    FINDING_TABLE),
        ("sharia_nc_event",         NC_TABLE),
        ("sharia_product_approval", PRODUCT_TABLE),
        ("sharia_event",            EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  + {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'sharia'")
    print(f"+ done -- {cur.fetchone()[0]} table(s) in sharia schema")


if __name__ == '__main__':
    main()
