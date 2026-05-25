"""Bootstrap the mandatory regulatory reporting schema in the dev DB.

Sprint 16 PR #1. Pairs with the JPA entities under
com.tss.grc.module.regreporting.entity.

Creates schema + 3 tables + indexes:
  regreporting.regreport_obligation   — catalog of recurring returns
  regreporting.regreport_filing       — per-cycle filing records
  regreporting.regreport_event        — filing timeline

Idempotent: uses IF NOT EXISTS guards; safe to re-run.

Usage:
  python setup_regreporting_db.py
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
    "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'regreporting') "
    "EXEC('CREATE SCHEMA regreporting');"
)

OBLIGATION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'regreporting' AND t.name = 'regreport_obligation')
BEGIN
    CREATE TABLE regreporting.regreport_obligation (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        code                        NVARCHAR(64)    NOT NULL,
        regulator                   NVARCHAR(16)    NOT NULL,
        frequency                   NVARCHAR(16)    NOT NULL,
        title_en                    NVARCHAR(256)   NOT NULL,
        title_ar                    NVARCHAR(256)   NOT NULL,
        description_en              NVARCHAR(MAX),
        description_ar              NVARCHAR(MAX),
        due_offset_business_days    INT             NOT NULL DEFAULT 5,
        statutory_basis             NVARCHAR(256),
        owner_role                  NVARCHAR(64),
        reviewer_role               NVARCHAR(64),
        filing_channel              NVARCHAR(256),
        reminder_ladder             NVARCHAR(64)    NOT NULL DEFAULT N'7,3,1',
        active                      BIT             NOT NULL DEFAULT 1,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_regreport_obligation_code
        ON regreporting.regreport_obligation (organisation_id, code)
        WHERE is_deleted = 0;
    CREATE INDEX ix_regreport_obligation_active
        ON regreporting.regreport_obligation (organisation_id, active, regulator);
END
"""

FILING_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'regreporting' AND t.name = 'regreport_filing')
BEGIN
    CREATE TABLE regreporting.regreport_filing (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        obligation_id               BIGINT          NOT NULL,
        reference                   NVARCHAR(96)    NOT NULL,
        status                      NVARCHAR(24)    NOT NULL DEFAULT N'SCHEDULED',
        period_start                DATE            NOT NULL,
        period_end                  DATE            NOT NULL,
        due_date                    DATE            NOT NULL,
        internal_target_date        DATE,
        preparer_user_id            BIGINT,
        reviewer_user_id            BIGINT,
        approver_user_id            BIGINT,
        submitted_at                DATETIME2,
        regulator_ack_reference     NVARCHAR(256),
        acknowledged_at             DATETIME2,
        closed_at                   DATETIME2,
        filing_artifact_reference   NVARCHAR(512),
        preparation_notes_en        NVARCHAR(MAX),
        preparation_notes_ar        NVARCHAR(MAX),
        rejection_reason_en         NVARCHAR(MAX),
        rejection_reason_ar         NVARCHAR(MAX),
        exemption_reason_en         NVARCHAR(MAX),
        exemption_reason_ar         NVARCHAR(MAX),
        exemption_reference         NVARCHAR(256),
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_regreport_filing_reference
        ON regreporting.regreport_filing (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_regreport_filing_due
        ON regreporting.regreport_filing (organisation_id, due_date);
    CREATE INDEX ix_regreport_filing_status
        ON regreporting.regreport_filing (organisation_id, status, due_date);
    CREATE INDEX ix_regreport_filing_obligation
        ON regreporting.regreport_filing (organisation_id, obligation_id, period_end DESC);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'regreporting' AND t.name = 'regreport_event')
BEGIN
    CREATE TABLE regreporting.regreport_event (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        filing_id                   BIGINT          NOT NULL,
        occurred_at                 DATETIME2       NOT NULL,
        event_type                  NVARCHAR(32)    NOT NULL,
        actor_user_id               BIGINT,
        actor_name                  NVARCHAR(256),
        status_after                NVARCHAR(32),
        note_en                     NVARCHAR(MAX),
        note_ar                     NVARCHAR(MAX),
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE INDEX ix_regreport_event_filing
        ON regreporting.regreport_event (organisation_id, filing_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping regreporting schema + 3 tables")
    for label, sql in [
        ("schema",          SCHEMA),
        ("obligation table", OBLIGATION_TABLE),
        ("filing table",     FILING_TABLE),
        ("event table",      EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  ✓ {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'regreporting'")
    print(f"✓ done — {cur.fetchone()[0]} table(s) in regreporting schema")


if __name__ == '__main__':
    main()
