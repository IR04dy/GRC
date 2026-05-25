"""Bootstrap the Recovery & Resolution Plan schema in the dev DB.

Sprint 17 PR #1. Pairs with the JPA entities under
com.tss.grc.module.rrp.entity.

Creates schema + 5 tables + indexes:
  rrp.rrp_cycle              — annual cycle carrier
  rrp.rrp_recovery_option     — option menu (capital / liquidity / etc.)
  rrp.rrp_critical_function   — critical functions inventory
  rrp.rrp_indicator           — live RAG-banded recovery indicators
  rrp.rrp_event               — chronological timeline

Idempotent: IF NOT EXISTS guards on every CREATE.

Usage:
  python setup_rrp_db.py
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
    "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'rrp') "
    "EXEC('CREATE SCHEMA rrp');"
)

CYCLE_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'rrp' AND t.name = 'rrp_cycle')
BEGIN
    CREATE TABLE rrp.rrp_cycle (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        reference                       NVARCHAR(64)    NOT NULL,
        fiscal_year                     INT             NOT NULL,
        title_en                        NVARCHAR(256)   NOT NULL,
        title_ar                        NVARCHAR(256)   NOT NULL,
        status                          NVARCHAR(24)    NOT NULL DEFAULT N'DRAFT',
        submission_due_date             DATE,
        submitted_at                    DATETIME2,
        regulator_ack_reference         NVARCHAR(256),
        acknowledged_at                 DATETIME2,
        closed_at                       DATETIME2,
        owner_user_id                   BIGINT,
        sponsor_user_id                 BIGINT,
        board_approval_date             DATE,
        board_resolution_reference      NVARCHAR(128),
        executive_summary_en            NVARCHAR(MAX),
        executive_summary_ar            NVARCHAR(MAX),
        filing_artifact_reference       NVARCHAR(512),
        critical_function_count         INT             NOT NULL DEFAULT 0,
        recovery_option_count           INT             NOT NULL DEFAULT 0,
        indicator_count                 INT             NOT NULL DEFAULT 0,
        indicators_amber                INT             NOT NULL DEFAULT 0,
        indicators_red                  INT             NOT NULL DEFAULT 0,
        total_recovery_capacity_sar_m   BIGINT          NOT NULL DEFAULT 0,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      BIGINT,
        updated_by                      BIGINT,
        is_deleted                      SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2,
        deleted_by                      BIGINT,
        row_version                     ROWVERSION
    );
    CREATE UNIQUE INDEX ux_rrp_cycle_reference
        ON rrp.rrp_cycle (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_rrp_cycle_status
        ON rrp.rrp_cycle (organisation_id, status, fiscal_year DESC);
END
"""

OPTION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'rrp' AND t.name = 'rrp_recovery_option')
BEGIN
    CREATE TABLE rrp.rrp_recovery_option (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        cycle_id                    BIGINT          NOT NULL,
        option_code                 NVARCHAR(32)    NOT NULL,
        category                    NVARCHAR(24)    NOT NULL,
        title_en                    NVARCHAR(256)   NOT NULL,
        title_ar                    NVARCHAR(256)   NOT NULL,
        description_en              NVARCHAR(MAX),
        description_ar              NVARCHAR(MAX),
        capacity_sar_m              BIGINT          NOT NULL DEFAULT 0,
        time_to_execute_days        INT             NOT NULL DEFAULT 30,
        feasibility                 NVARCHAR(12)    NOT NULL DEFAULT N'MEDIUM',
        impact_en                   NVARCHAR(MAX),
        impact_ar                   NVARCHAR(MAX),
        preconditions_en            NVARCHAR(MAX),
        preconditions_ar            NVARCHAR(MAX),
        limitations_en              NVARCHAR(MAX),
        limitations_ar              NVARCHAR(MAX),
        accountable_role            NVARCHAR(64),
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE INDEX ix_rrp_option_cycle
        ON rrp.rrp_recovery_option (organisation_id, cycle_id, option_code);
END
"""

CF_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'rrp' AND t.name = 'rrp_critical_function')
BEGIN
    CREATE TABLE rrp.rrp_critical_function (
        id                                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                     BIGINT          NOT NULL,
        cycle_id                            BIGINT          NOT NULL,
        function_code                       NVARCHAR(32)    NOT NULL,
        title_en                            NVARCHAR(256)   NOT NULL,
        title_ar                            NVARCHAR(256)   NOT NULL,
        description_en                      NVARCHAR(MAX),
        description_ar                      NVARCHAR(MAX),
        materiality                         NVARCHAR(16)    NOT NULL DEFAULT N'MEDIUM',
        max_tolerable_downtime_hours        INT,
        customer_count_estimate             BIGINT,
        daily_volume_estimate               BIGINT,
        critical_provider_dependencies_en   NVARCHAR(MAX),
        critical_provider_dependencies_ar   NVARCHAR(MAX),
        continuity_strategy_en              NVARCHAR(MAX),
        continuity_strategy_ar              NVARCHAR(MAX),
        owner_role                          NVARCHAR(64),
        created_at                          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                          BIGINT,
        updated_by                          BIGINT,
        is_deleted                          SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                          DATETIME2,
        deleted_by                          BIGINT,
        row_version                         ROWVERSION
    );
    CREATE INDEX ix_rrp_cf_cycle
        ON rrp.rrp_critical_function (organisation_id, cycle_id, function_code);
END
"""

IND_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'rrp' AND t.name = 'rrp_indicator')
BEGIN
    CREATE TABLE rrp.rrp_indicator (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        cycle_id            BIGINT          NOT NULL,
        indicator_code      NVARCHAR(32)    NOT NULL,
        category            NVARCHAR(24)    NOT NULL,
        title_en            NVARCHAR(256)   NOT NULL,
        title_ar            NVARCHAR(256)   NOT NULL,
        description_en      NVARCHAR(MAX),
        description_ar      NVARCHAR(MAX),
        unit                NVARCHAR(16),
        direction           NVARCHAR(20)    NOT NULL DEFAULT N'HIGHER_IS_BETTER',
        green_threshold     DECIMAL(18,4),
        amber_threshold     DECIMAL(18,4),
        current_value       DECIMAL(18,4),
        current_band        NVARCHAR(8)     NOT NULL DEFAULT N'GREEN',
        measured_at         DATE,
        data_source         NVARCHAR(256),
        owner_role          NVARCHAR(64),
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT,
        updated_by          BIGINT,
        is_deleted          SMALLINT        NOT NULL DEFAULT 0,
        deleted_at          DATETIME2,
        deleted_by          BIGINT,
        row_version         ROWVERSION
    );
    CREATE INDEX ix_rrp_indicator_cycle
        ON rrp.rrp_indicator (organisation_id, cycle_id, indicator_code);
    CREATE INDEX ix_rrp_indicator_band
        ON rrp.rrp_indicator (organisation_id, current_band, cycle_id);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'rrp' AND t.name = 'rrp_event')
BEGIN
    CREATE TABLE rrp.rrp_event (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        cycle_id                        BIGINT          NOT NULL,
        occurred_at                     DATETIME2       NOT NULL,
        event_type                      NVARCHAR(32)    NOT NULL,
        actor_user_id                   BIGINT,
        actor_name                      NVARCHAR(256),
        status_after                    NVARCHAR(32),
        linked_option_id                BIGINT,
        linked_indicator_id             BIGINT,
        linked_critical_function_id     BIGINT,
        band_before                     NVARCHAR(8),
        band_after                      NVARCHAR(8),
        note_en                         NVARCHAR(MAX),
        note_ar                         NVARCHAR(MAX),
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      BIGINT,
        updated_by                      BIGINT,
        is_deleted                      SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2,
        deleted_by                      BIGINT,
        row_version                     ROWVERSION
    );
    CREATE INDEX ix_rrp_event_cycle
        ON rrp.rrp_event (organisation_id, cycle_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping rrp schema + 5 tables")
    for label, sql in [
        ("schema",            SCHEMA),
        ("cycle table",       CYCLE_TABLE),
        ("recovery option",   OPTION_TABLE),
        ("critical function", CF_TABLE),
        ("indicator table",   IND_TABLE),
        ("event table",       EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  ✓ {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'rrp'")
    print(f"✓ done — {cur.fetchone()[0]} table(s) in rrp schema")


if __name__ == '__main__':
    main()
