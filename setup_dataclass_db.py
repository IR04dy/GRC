"""Bootstrap Data Classification + NDMO schema. Sprint 32 PR #1."""
from __future__ import annotations
import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;')
conn.autocommit = True
cur = conn.cursor()

SCHEMA = "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'dataclass') EXEC('CREATE SCHEMA dataclass');"

ASSET_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'dataclass' AND t.name = 'data_asset')
BEGIN
    CREATE TABLE dataclass.data_asset (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        reference                NVARCHAR(64) NOT NULL,
        name_en                  NVARCHAR(256) NOT NULL,
        name_ar                  NVARCHAR(256) NOT NULL,
        description_en           NVARCHAR(MAX),
        description_ar           NVARCHAR(MAX),
        domain                   NVARCHAR(32),
        classification           NVARCHAR(16),
        pdpl_special_category    NVARCHAR(16) NOT NULL DEFAULT N'NONE',
        pdpl_lawful_basis        NVARCHAR(256),
        status                   NVARCHAR(16) NOT NULL DEFAULT N'DRAFT',
        owner_role               NVARCHAR(32),
        owner_user_id            BIGINT,
        host_system_label        NVARCHAR(256),
        retention_years          INT,
        encryption_at_rest       BIT,
        encryption_in_transit    BIT,
        masking_required         BIT,
        classified_at            DATETIME2,
        approved_at              DATETIME2,
        next_review_due          DATE,
        decommissioned_at        DATE,
        row_count_approx         BIGINT,
        linked_dpia_ref          NVARCHAR(64),
        created_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT, updated_by BIGINT,
        is_deleted               SMALLINT NOT NULL DEFAULT 0,
        deleted_at               DATETIME2, deleted_by BIGINT,
        row_version              ROWVERSION
    );
    CREATE UNIQUE INDEX ux_data_asset_ref ON dataclass.data_asset (organisation_id, reference) WHERE is_deleted = 0;
    CREATE INDEX ix_data_asset_class      ON dataclass.data_asset (organisation_id, classification, pdpl_special_category);
END
"""

FLOW_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'dataclass' AND t.name = 'data_flow')
BEGIN
    CREATE TABLE dataclass.data_flow (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        data_asset_id            BIGINT NOT NULL,
        reference                NVARCHAR(64) NOT NULL,
        source_system_label      NVARCHAR(256),
        destination_system_label NVARCHAR(256),
        destination_country      NVARCHAR(8),
        destination_adequacy     NVARCHAR(16),
        transfer_mode            NVARCHAR(16),
        status                   NVARCHAR(16) NOT NULL DEFAULT N'PROPOSED',
        purpose_en               NVARCHAR(MAX),
        purpose_ar               NVARCHAR(MAX),
        pdpl_art7_transfer_ref   NVARCHAR(64),
        approved_at              DATETIME2,
        activated_at             DATE,
        suspended_at             DATE,
        terminated_at            DATE,
        rejection_reason_en      NVARCHAR(MAX),
        rejection_reason_ar      NVARCHAR(MAX),
        created_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT, updated_by BIGINT,
        is_deleted               SMALLINT NOT NULL DEFAULT 0,
        deleted_at               DATETIME2, deleted_by BIGINT,
        row_version              ROWVERSION
    );
    CREATE UNIQUE INDEX ux_data_flow_ref ON dataclass.data_flow (organisation_id, reference) WHERE is_deleted = 0;
    CREATE INDEX ix_data_flow_asset      ON dataclass.data_flow (organisation_id, data_asset_id, status);
    CREATE INDEX ix_data_flow_xborder    ON dataclass.data_flow (organisation_id, destination_country, status);
END
"""

EXCEPTION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'dataclass' AND t.name = 'data_exception')
BEGIN
    CREATE TABLE dataclass.data_exception (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        data_asset_id            BIGINT,
        data_flow_id             BIGINT,
        reference                NVARCHAR(64) NOT NULL,
        exception_type           NVARCHAR(32),
        status                   NVARCHAR(16) NOT NULL DEFAULT N'REQUESTED',
        justification_en         NVARCHAR(MAX),
        justification_ar         NVARCHAR(MAX),
        compensating_controls_en NVARCHAR(MAX),
        compensating_controls_ar NVARCHAR(MAX),
        requested_by_user_id     BIGINT,
        approver_user_id         BIGINT,
        approved_at              DATETIME2,
        valid_from               DATE,
        valid_until              DATE,
        revoked_at               DATE,
        rejection_reason_en      NVARCHAR(MAX),
        rejection_reason_ar      NVARCHAR(MAX),
        created_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT, updated_by BIGINT,
        is_deleted               SMALLINT NOT NULL DEFAULT 0,
        deleted_at               DATETIME2, deleted_by BIGINT,
        row_version              ROWVERSION
    );
    CREATE UNIQUE INDEX ux_data_exception_ref ON dataclass.data_exception (organisation_id, reference) WHERE is_deleted = 0;
    CREATE INDEX ix_data_exception_status    ON dataclass.data_exception (organisation_id, status, valid_until);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'dataclass' AND t.name = 'dataclass_event')
BEGIN
    CREATE TABLE dataclass.dataclass_event (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT NOT NULL,
        data_asset_id     BIGINT,
        data_flow_id      BIGINT,
        data_exception_id BIGINT,
        occurred_at       DATETIME2 NOT NULL,
        event_type        NVARCHAR(40) NOT NULL,
        actor_user_id     BIGINT,
        actor_name        NVARCHAR(256),
        status_after      NVARCHAR(32),
        note_en           NVARCHAR(MAX),
        note_ar           NVARCHAR(MAX),
        created_at        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by        BIGINT, updated_by BIGINT,
        is_deleted        SMALLINT NOT NULL DEFAULT 0,
        deleted_at        DATETIME2, deleted_by BIGINT,
        row_version       ROWVERSION
    );
    CREATE INDEX ix_dataclass_event_asset ON dataclass.dataclass_event (organisation_id, data_asset_id, occurred_at DESC);
END
"""


def main():
    print(">>> Bootstrapping dataclass schema + 4 tables")
    for label, sql in [
        ("schema", SCHEMA),
        ("data_asset", ASSET_TABLE),
        ("data_flow", FLOW_TABLE),
        ("data_exception", EXCEPTION_TABLE),
        ("dataclass_event", EVENT_TABLE),
    ]:
        try:
            cur.execute(sql); print(f"  + {label}")
        except pyodbc.Error as e:
            print(f"  ! {label}: {e}"); sys.exit(1)


if __name__ == '__main__':
    main()
