"""Bootstrap the regulatory-inspection schema in the dev DB.

Sprint 15 PR #1. Pairs with the JPA entities under
com.tss.grc.module.reginspect.entity.

Creates schema + 3 tables + indexes:
  reginspect.reginspect_inspection
  reginspect.reginspect_document_request
  reginspect.reginspect_inspection_update

Idempotent: drops nothing; uses IF NOT EXISTS guards.

Usage:
  python setup_reginspect_db.py
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


SCHEMA = "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'reginspect') " \
         "EXEC('CREATE SCHEMA reginspect');"

INSPECTION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'reginspect' AND t.name = 'reginspect_inspection')
BEGIN
    CREATE TABLE reginspect.reginspect_inspection (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        external_reference      NVARCHAR(64)    NOT NULL,
        regulator               NVARCHAR(16)    NOT NULL,
        title_en                NVARCHAR(256)   NOT NULL,
        title_ar                NVARCHAR(256)   NOT NULL,
        status                  NVARCHAR(32)    NOT NULL DEFAULT N'PLANNED',
        notified_at             DATE,
        entry_date              DATE,
        exit_date               DATE,
        closed_at               DATETIME2,
        lead_inspector_name     NVARCHAR(256),
        scope_en                NVARCHAR(MAX),
        scope_ar                NVARCHAR(MAX),
        coordinator_user_id     BIGINT,
        sponsor_user_id         BIGINT,
        executive_summary_en    NVARCHAR(MAX),
        executive_summary_ar    NVARCHAR(MAX),
        open_dr_count           INT             NOT NULL DEFAULT 0,
        overdue_dr_count        INT             NOT NULL DEFAULT 0,
        submitted_dr_count      INT             NOT NULL DEFAULT 0,
        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              BIGINT,
        updated_by              BIGINT,
        is_deleted              SMALLINT        NOT NULL DEFAULT 0,
        deleted_at              DATETIME2,
        deleted_by              BIGINT,
        row_version             ROWVERSION
    );
    CREATE UNIQUE INDEX ux_reginspect_external_reference
        ON reginspect.reginspect_inspection (organisation_id, external_reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_reginspect_status
        ON reginspect.reginspect_inspection (organisation_id, status);
    CREATE INDEX ix_reginspect_created
        ON reginspect.reginspect_inspection (organisation_id, created_at DESC);
END
"""

DR_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'reginspect' AND t.name = 'reginspect_document_request')
BEGIN
    CREATE TABLE reginspect.reginspect_document_request (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        inspection_id           BIGINT          NOT NULL,
        reference               NVARCHAR(64)    NOT NULL,
        title_en                NVARCHAR(256)   NOT NULL,
        title_ar                NVARCHAR(256)   NOT NULL,
        description_en          NVARCHAR(MAX),
        description_ar          NVARCHAR(MAX),
        status                  NVARCHAR(24)    NOT NULL DEFAULT N'OPEN',
        requested_at            DATETIME2       NOT NULL,
        due_date                DATE,
        submitted_at            DATETIME2,
        assigned_to_user_id     BIGINT,
        evidence_reference      NVARCHAR(512),
        submission_notes_en     NVARCHAR(MAX),
        submission_notes_ar     NVARCHAR(MAX),
        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              BIGINT,
        updated_by              BIGINT,
        is_deleted              SMALLINT        NOT NULL DEFAULT 0,
        deleted_at              DATETIME2,
        deleted_by              BIGINT,
        row_version             ROWVERSION
    );
    CREATE INDEX ix_reginspect_dr_inspection
        ON reginspect.reginspect_document_request
            (organisation_id, inspection_id, requested_at DESC);
    CREATE INDEX ix_reginspect_dr_overdue
        ON reginspect.reginspect_document_request
            (organisation_id, status, due_date);
END
"""

UPDATE_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'reginspect' AND t.name = 'reginspect_inspection_update')
BEGIN
    CREATE TABLE reginspect.reginspect_inspection_update (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id         BIGINT          NOT NULL,
        inspection_id           BIGINT          NOT NULL,
        occurred_at             DATETIME2       NOT NULL,
        update_type             NVARCHAR(32)    NOT NULL,
        actor_user_id           BIGINT,
        actor_name              NVARCHAR(256),
        status_after            NVARCHAR(32),
        note_en                 NVARCHAR(MAX),
        note_ar                 NVARCHAR(MAX),
        linked_document_request_id BIGINT,
        created_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by              BIGINT,
        updated_by              BIGINT,
        is_deleted              SMALLINT        NOT NULL DEFAULT 0,
        deleted_at              DATETIME2,
        deleted_by              BIGINT,
        row_version             ROWVERSION
    );
    CREATE INDEX ix_reginspect_update_inspection
        ON reginspect.reginspect_inspection_update
            (organisation_id, inspection_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping reginspect schema + 3 tables")
    for label, sql in [
        ("schema",   SCHEMA),
        ("inspection table",       INSPECTION_TABLE),
        ("document_request table", DR_TABLE),
        ("update table",           UPDATE_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  ✓ {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'reginspect'")
    print(f"✓ done — {cur.fetchone()[0]} table(s) in reginspect schema")


if __name__ == '__main__':
    main()
