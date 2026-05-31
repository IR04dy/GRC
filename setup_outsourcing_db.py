"""Bootstrap the Outsourcing & Cloud Governance schema in the dev DB.

Sprint 30 PR #1. Pairs with JPA entities under
com.tss.grc.module.outsourcing.entity.

Creates schema + 5 tables + indexes:
  outsourcing.outsourcing_arrangement  -- master SAMA Outsourcing record
  outsourcing.cloud_service            -- cloud subscription / SaaS deployment
  outsourcing.ongoing_review           -- SAMA §6 periodic oversight
  outsourcing.exit_plan                -- SAMA §7 exit-and-contingency
  outsourcing.outsourcing_event        -- chronological timeline

Idempotent — IF NOT EXISTS guards.

Usage:  python setup_outsourcing_db.py
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
    "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'outsourcing') "
    "EXEC('CREATE SCHEMA outsourcing');"
)

ARRANGEMENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'outsourcing' AND t.name = 'outsourcing_arrangement')
BEGIN
    CREATE TABLE outsourcing.outsourcing_arrangement (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        provider_legal_name_en      NVARCHAR(256)   NOT NULL,
        provider_legal_name_ar      NVARCHAR(256)   NOT NULL,
        provider_country            NVARCHAR(8),
        provider_jurisdiction       NVARCHAR(16),
        service_title_en            NVARCHAR(256)   NOT NULL,
        service_title_ar            NVARCHAR(256)   NOT NULL,
        service_description_en      NVARCHAR(MAX),
        service_description_ar      NVARCHAR(MAX),
        service_category            NVARCHAR(40)    NOT NULL,
        materiality                 NVARCHAR(16)    NOT NULL DEFAULT N'NON_MATERIAL',
        is_core_activity            BIT             NOT NULL DEFAULT 0,
        status                      NVARCHAR(24)    NOT NULL DEFAULT N'PROPOSED',
        risk_rating                 NVARCHAR(16),
        risk_assessed_at            DATETIME2,
        board_approved_at           DATETIME2,
        sama_notified_at            DATETIME2,
        sama_non_objection_ref      NVARCHAR(64),
        live_at                     DATE,
        exited_at                   DATE,
        suspended_at                DATETIME2,
        rejected_at                 DATETIME2,
        rejection_reason_en         NVARCHAR(MAX),
        rejection_reason_ar         NVARCHAR(MAX),
        contract_start              DATE,
        contract_end                DATE,
        renewal_notice_days         INT,
        annual_spend_sar            DECIMAL(18, 2),
        next_review_due             DATE,
        review_cadence_months       INT,
        relationship_owner_user_id  BIGINT,
        risk_owner_user_id          BIGINT,
        linked_vendor_id            BIGINT,
        created_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_outsourcing_arrangement_ref
        ON outsourcing.outsourcing_arrangement (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_outsourcing_arrangement_status
        ON outsourcing.outsourcing_arrangement (organisation_id, status, materiality);
    CREATE INDEX ix_outsourcing_arrangement_review
        ON outsourcing.outsourcing_arrangement (organisation_id, next_review_due);
END
"""

CLOUD_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'outsourcing' AND t.name = 'cloud_service')
BEGIN
    CREATE TABLE outsourcing.cloud_service (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        arrangement_id              BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        service_name_en             NVARCHAR(256)   NOT NULL,
        service_name_ar             NVARCHAR(256)   NOT NULL,
        deployment_model            NVARCHAR(16)    NOT NULL,
        service_model               NVARCHAR(16),
        csp_legal_name_en           NVARCHAR(256)   NOT NULL,
        csp_legal_name_ar           NVARCHAR(256)   NOT NULL,
        csp_brand                   NVARCHAR(32),
        data_classification         NVARCHAR(12),
        data_residency              NVARCHAR(12),
        csp_region_label            NVARCHAR(64),
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'ONBOARDING',
        classified_at               DATETIME2,
        approved_at                 DATETIME2,
        in_use_at                   DATE,
        deprecated_at               DATE,
        decommissioned_at           DATE,
        block_reason_en             NVARCHAR(MAX),
        block_reason_ar             NVARCHAR(MAX),
        approver_role               NVARCHAR(32),
        supports_pdpl_dpia_ref      NVARCHAR(64),
        created_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_cloud_service_ref
        ON outsourcing.cloud_service (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_cloud_service_classification
        ON outsourcing.cloud_service (organisation_id, data_classification, data_residency);
    CREATE INDEX ix_cloud_service_status
        ON outsourcing.cloud_service (organisation_id, status);
END
"""

REVIEW_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'outsourcing' AND t.name = 'ongoing_review')
BEGIN
    CREATE TABLE outsourcing.ongoing_review (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        arrangement_id              BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        review_period_label         NVARCHAR(32),
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'SCHEDULED',
        due_date                    DATE,
        started_at                  DATETIME2,
        completed_at                DATETIME2,
        outcome_rag                 NVARCHAR(8),
        findings_summary_en         NVARCHAR(MAX),
        findings_summary_ar         NVARCHAR(MAX),
        linked_capa_id              BIGINT,
        reviewer_user_id            BIGINT,
        created_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_ongoing_review_ref
        ON outsourcing.ongoing_review (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ongoing_review_arr
        ON outsourcing.ongoing_review (organisation_id, arrangement_id, status);
END
"""

EXIT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'outsourcing' AND t.name = 'exit_plan')
BEGIN
    CREATE TABLE outsourcing.exit_plan (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        arrangement_id              BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'DRAFT',
        summary_en                  NVARCHAR(MAX),
        summary_ar                  NVARCHAR(MAX),
        target_migration_days       INT,
        alternative_provider_en     NVARCHAR(256),
        alternative_provider_ar     NVARCHAR(256),
        approved_at                 DATETIME2,
        rehearsed_at                DATE,
        executed_at                 DATE,
        rejection_reason_en         NVARCHAR(MAX),
        rejection_reason_ar         NVARCHAR(MAX),
        created_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_exit_plan_ref
        ON outsourcing.exit_plan (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_exit_plan_arr
        ON outsourcing.exit_plan (organisation_id, arrangement_id, status);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'outsourcing' AND t.name = 'outsourcing_event')
BEGIN
    CREATE TABLE outsourcing.outsourcing_event (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        arrangement_id      BIGINT,
        cloud_service_id    BIGINT,
        review_id           BIGINT,
        exit_plan_id        BIGINT,
        occurred_at         DATETIME2       NOT NULL,
        event_type          NVARCHAR(40)    NOT NULL,
        actor_user_id       BIGINT,
        actor_name          NVARCHAR(256),
        status_after        NVARCHAR(32),
        note_en             NVARCHAR(MAX),
        note_ar             NVARCHAR(MAX),
        created_at          DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT,
        updated_by          BIGINT,
        is_deleted          SMALLINT NOT NULL DEFAULT 0,
        deleted_at          DATETIME2,
        deleted_by          BIGINT,
        row_version         ROWVERSION
    );
    CREATE INDEX ix_outsourcing_event_arr
        ON outsourcing.outsourcing_event (organisation_id, arrangement_id, occurred_at DESC);
    CREATE INDEX ix_outsourcing_event_cloud
        ON outsourcing.outsourcing_event (organisation_id, cloud_service_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping outsourcing schema + 5 tables")
    for label, sql in [
        ("schema",                 SCHEMA),
        ("outsourcing_arrangement", ARRANGEMENT_TABLE),
        ("cloud_service",           CLOUD_TABLE),
        ("ongoing_review",          REVIEW_TABLE),
        ("exit_plan",               EXIT_TABLE),
        ("outsourcing_event",       EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  + {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'outsourcing'")
    print(f"+ done -- {cur.fetchone()[0]} table(s) in outsourcing schema")


if __name__ == '__main__':
    main()
