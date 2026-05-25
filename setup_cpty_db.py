"""Bootstrap the Counterparty Credit Rating Registry schema in dev DB.

Sprint 18 PR #1. Pairs with the JPA entities under
com.tss.grc.module.cpty.entity.

Creates schema + 5 tables + indexes:
  cpty.counterparty           — register row
  cpty.credit_rating          — point-in-time ratings (INTERNAL + EXTERNAL)
  cpty.rating_action           — watchlist / downgrade / upgrade / default
  cpty.large_exposure_limit    — counterparty + group limits with utilisation
  cpty.cpty_event              — chronological timeline

Idempotent: IF NOT EXISTS guards.

Usage:
  python setup_cpty_db.py
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
    "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'cpty') "
    "EXEC('CREATE SCHEMA cpty');"
)

COUNTERPARTY_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'cpty' AND t.name = 'counterparty')
BEGIN
    CREATE TABLE cpty.counterparty (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        reference                       NVARCHAR(32)    NOT NULL,
        lei                             NVARCHAR(20),
        legal_name_en                   NVARCHAR(256)   NOT NULL,
        legal_name_ar                   NVARCHAR(256)   NOT NULL,
        short_name                      NVARCHAR(128),
        sector                          NVARCHAR(32)    NOT NULL,
        country                         NVARCHAR(2),
        industry                        NVARCHAR(128),
        parent_group_reference          NVARCHAR(64),
        gre_flag                        BIT             NOT NULL DEFAULT 0,
        sovereign_flag                  BIT             NOT NULL DEFAULT 0,
        fi_flag                         BIT             NOT NULL DEFAULT 0,
        status                          NVARCHAR(16)    NOT NULL DEFAULT N'ACTIVE',
        relationship_manager_user_id    BIGINT,
        credit_officer_user_id          BIGINT,
        kyc_refreshed_at                DATE,
        onboarded_at                    DATE,
        total_exposure_sar_m            DECIMAL(18, 2)  NOT NULL DEFAULT 0,
        current_internal_grade          NVARCHAR(8),
        current_external_rating         NVARCHAR(16),
        current_external_agency         NVARCHAR(32),
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      BIGINT,
        updated_by                      BIGINT,
        is_deleted                      SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2,
        deleted_by                      BIGINT,
        row_version                     ROWVERSION
    );
    CREATE UNIQUE INDEX ux_cpty_reference
        ON cpty.counterparty (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_cpty_status
        ON cpty.counterparty (organisation_id, status, sector);
    CREATE INDEX ix_cpty_parent_group
        ON cpty.counterparty (organisation_id, parent_group_reference);
END
"""

RATING_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'cpty' AND t.name = 'credit_rating')
BEGIN
    CREATE TABLE cpty.credit_rating (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        counterparty_id     BIGINT          NOT NULL,
        rating_type         NVARCHAR(16)    NOT NULL,
        effective_date      DATE            NOT NULL,
        grade               NVARCHAR(16)    NOT NULL,
        scale               NVARCHAR(64),
        agency              NVARCHAR(32),
        pd                  DECIMAL(10, 6),
        lgd                 DECIMAL(10, 6),
        ead_sar_m           DECIMAL(18, 2),
        outlook             NVARCHAR(16)    NOT NULL DEFAULT N'STABLE',
        rationale_en        NVARCHAR(MAX),
        rationale_ar        NVARCHAR(MAX),
        assigned_by_user_id BIGINT,
        approved_by_user_id BIGINT,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT,
        updated_by          BIGINT,
        is_deleted          SMALLINT        NOT NULL DEFAULT 0,
        deleted_at          DATETIME2,
        deleted_by          BIGINT,
        row_version         ROWVERSION
    );
    CREATE INDEX ix_rating_cpty
        ON cpty.credit_rating (organisation_id, counterparty_id, effective_date DESC);
END
"""

ACTION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'cpty' AND t.name = 'rating_action')
BEGIN
    CREATE TABLE cpty.rating_action (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        counterparty_id     BIGINT          NOT NULL,
        action_type         NVARCHAR(24)    NOT NULL,
        action_date         DATE            NOT NULL,
        grade_before        NVARCHAR(16),
        grade_after         NVARCHAR(16),
        outlook_before      NVARCHAR(16),
        outlook_after       NVARCHAR(16),
        trigger_source      NVARCHAR(24),
        rationale_en        NVARCHAR(MAX),
        rationale_ar        NVARCHAR(MAX),
        actor_user_id       BIGINT,
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT,
        updated_by          BIGINT,
        is_deleted          SMALLINT        NOT NULL DEFAULT 0,
        deleted_at          DATETIME2,
        deleted_by          BIGINT,
        row_version         ROWVERSION
    );
    CREATE INDEX ix_action_cpty
        ON cpty.rating_action (organisation_id, counterparty_id, action_date DESC);
END
"""

LIMIT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'cpty' AND t.name = 'large_exposure_limit')
BEGIN
    CREATE TABLE cpty.large_exposure_limit (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        scope                       NVARCHAR(16)    NOT NULL,
        counterparty_id             BIGINT,
        group_reference             NVARCHAR(64),
        display_name_en             NVARCHAR(256)   NOT NULL,
        display_name_ar             NVARCHAR(256)   NOT NULL,
        limit_sar_m                 DECIMAL(18, 2)  NOT NULL,
        utilised_sar_m              DECIMAL(18, 2)  NOT NULL DEFAULT 0,
        utilisation_pct             DECIMAL(8, 4)   NOT NULL DEFAULT 0,
        band                        NVARCHAR(8)     NOT NULL DEFAULT N'GREEN',
        approved_at                 DATE,
        next_review_at              DATE,
        approved_by_user_id         BIGINT,
        utilisation_refreshed_at    DATE,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE INDEX ix_limit_band
        ON cpty.large_exposure_limit (organisation_id, band, utilisation_pct DESC);
    CREATE INDEX ix_limit_cpty
        ON cpty.large_exposure_limit (organisation_id, counterparty_id);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'cpty' AND t.name = 'cpty_event')
BEGIN
    CREATE TABLE cpty.cpty_event (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        counterparty_id     BIGINT          NOT NULL,
        occurred_at         DATETIME2       NOT NULL,
        event_type          NVARCHAR(32)    NOT NULL,
        actor_user_id       BIGINT,
        actor_name          NVARCHAR(256),
        linked_rating_id    BIGINT,
        linked_action_id    BIGINT,
        linked_limit_id     BIGINT,
        band_before         NVARCHAR(8),
        band_after          NVARCHAR(8),
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
    CREATE INDEX ix_cpty_event_cpty
        ON cpty.cpty_event (organisation_id, counterparty_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping cpty schema + 5 tables")
    for label, sql in [
        ("schema",          SCHEMA),
        ("counterparty",    COUNTERPARTY_TABLE),
        ("credit_rating",   RATING_TABLE),
        ("rating_action",   ACTION_TABLE),
        ("large_exposure_limit", LIMIT_TABLE),
        ("cpty_event",      EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  ✓ {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'cpty'")
    print(f"✓ done — {cur.fetchone()[0]} table(s) in cpty schema")


if __name__ == '__main__':
    main()
