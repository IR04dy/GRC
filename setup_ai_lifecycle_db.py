"""Bootstrap AI/ML Model Lifecycle schema — Sprint 31 PR #1.

Adds 4 tables under the existing ai_governance schema (created by
Sprint 11's V92):
  ai_governance.model_validation
  ai_governance.retraining_event
  ai_governance.recertification
  ai_governance.ai_lifecycle_event

Idempotent — IF NOT EXISTS guards.
"""
from __future__ import annotations
import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;')
conn.autocommit = True
cur = conn.cursor()

SCHEMA = "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ai_governance') EXEC('CREATE SCHEMA ai_governance');"

VALIDATION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'ai_governance' AND t.name = 'model_validation')
BEGIN
    CREATE TABLE ai_governance.model_validation (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        model_id                 BIGINT NOT NULL,
        reference                NVARCHAR(64) NOT NULL,
        status                   NVARCHAR(24) NOT NULL DEFAULT N'SCHEDULED',
        validation_type          NVARCHAR(24),
        validator_user_id        BIGINT,
        validator_org_label      NVARCHAR(256),
        scheduled_at             DATE,
        started_at               DATETIME2,
        completed_at             DATETIME2,
        metrics_json             NVARCHAR(MAX),
        ethics_scores_json       NVARCHAR(MAX),
        fairness_disparity_pct   DECIMAL(6,2),
        findings_en              NVARCHAR(MAX),
        findings_ar              NVARCHAR(MAX),
        conditions_en            NVARCHAR(MAX),
        conditions_ar            NVARCHAR(MAX),
        linked_capa_id           BIGINT,
        created_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT, updated_by BIGINT,
        is_deleted               SMALLINT NOT NULL DEFAULT 0,
        deleted_at               DATETIME2, deleted_by BIGINT,
        row_version              ROWVERSION
    );
    CREATE UNIQUE INDEX ux_model_validation_ref ON ai_governance.model_validation (organisation_id, reference) WHERE is_deleted = 0;
    CREATE INDEX ix_model_validation_model     ON ai_governance.model_validation (organisation_id, model_id, status);
END
"""

RETRAINING_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'ai_governance' AND t.name = 'retraining_event')
BEGIN
    CREATE TABLE ai_governance.retraining_event (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        model_id                 BIGINT NOT NULL,
        reference                NVARCHAR(64) NOT NULL,
        status                   NVARCHAR(16) NOT NULL DEFAULT N'PROPOSED',
        trigger_reason           NVARCHAR(24),
        rationale_en             NVARCHAR(MAX),
        rationale_ar             NVARCHAR(MAX),
        from_version_label       NVARCHAR(64),
        to_version_label         NVARCHAR(64),
        approved_at              DATETIME2,
        started_at               DATETIME2,
        deployed_at              DATE,
        rolled_back_at           DATE,
        post_deploy_metrics_json NVARCHAR(MAX),
        rollback_reason_en       NVARCHAR(MAX),
        rollback_reason_ar       NVARCHAR(MAX),
        owner_user_id            BIGINT,
        created_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT, updated_by BIGINT,
        is_deleted               SMALLINT NOT NULL DEFAULT 0,
        deleted_at               DATETIME2, deleted_by BIGINT,
        row_version              ROWVERSION
    );
    CREATE UNIQUE INDEX ux_retraining_event_ref ON ai_governance.retraining_event (organisation_id, reference) WHERE is_deleted = 0;
    CREATE INDEX ix_retraining_event_model     ON ai_governance.retraining_event (organisation_id, model_id, status);
END
"""

RECERTIFICATION_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'ai_governance' AND t.name = 'recertification')
BEGIN
    CREATE TABLE ai_governance.recertification (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT NOT NULL,
        model_id                    BIGINT NOT NULL,
        reference                   NVARCHAR(64) NOT NULL,
        cycle_label                 NVARCHAR(32),
        status                      NVARCHAR(32) NOT NULL DEFAULT N'DUE',
        due_date                    DATE,
        started_at                  DATETIME2,
        decided_at                  DATETIME2,
        next_due_date               DATE,
        decision_rationale_en       NVARCHAR(MAX),
        decision_rationale_ar       NVARCHAR(MAX),
        conditions_en               NVARCHAR(MAX),
        conditions_ar               NVARCHAR(MAX),
        approver_user_id            BIGINT,
        linked_validation_id        BIGINT,
        created_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT, updated_by BIGINT,
        is_deleted                  SMALLINT NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2, deleted_by BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_recertification_ref ON ai_governance.recertification (organisation_id, reference) WHERE is_deleted = 0;
    CREATE INDEX ix_recertification_model     ON ai_governance.recertification (organisation_id, model_id, status, due_date);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'ai_governance' AND t.name = 'ai_lifecycle_event')
BEGIN
    CREATE TABLE ai_governance.ai_lifecycle_event (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        model_id                 BIGINT,
        validation_id            BIGINT,
        retraining_id            BIGINT,
        recertification_id       BIGINT,
        occurred_at              DATETIME2 NOT NULL,
        event_type               NVARCHAR(40) NOT NULL,
        actor_user_id            BIGINT,
        actor_name               NVARCHAR(256),
        status_after             NVARCHAR(32),
        note_en                  NVARCHAR(MAX),
        note_ar                  NVARCHAR(MAX),
        created_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT, updated_by BIGINT,
        is_deleted               SMALLINT NOT NULL DEFAULT 0,
        deleted_at               DATETIME2, deleted_by BIGINT,
        row_version              ROWVERSION
    );
    CREATE INDEX ix_ai_lifecycle_event_model ON ai_governance.ai_lifecycle_event (organisation_id, model_id, occurred_at DESC);
END
"""


def main():
    print(">>> Bootstrapping AI lifecycle tables under ai_governance schema")
    for label, sql in [
        ("schema", SCHEMA),
        ("model_validation", VALIDATION_TABLE),
        ("retraining_event", RETRAINING_TABLE),
        ("recertification", RECERTIFICATION_TABLE),
        ("ai_lifecycle_event", EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  + {label}")
        except pyodbc.Error as e:
            print(f"  ! {label}: {e}")
            sys.exit(1)


if __name__ == '__main__':
    main()
