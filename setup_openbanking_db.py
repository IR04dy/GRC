"""Bootstrap the Open Banking Compliance schema in the dev DB.

Sprint 19 PR #1. Pairs with the JPA entities under
com.tss.grc.module.openbanking.entity.

Creates schema + 5 tables + indexes:
  openbanking.ob_tpp           — Third-Party Provider register
  openbanking.ob_consent        — customer consents
  openbanking.ob_api_call       — append-only API audit log
  openbanking.ob_incident       — OB-specific incidents
  openbanking.ob_event          — chronological timeline

Idempotent: IF NOT EXISTS guards.

Usage:
  python setup_openbanking_db.py
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
    "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'openbanking') "
    "EXEC('CREATE SCHEMA openbanking');"
)

TPP_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'openbanking' AND t.name = 'ob_tpp')
BEGIN
    CREATE TABLE openbanking.ob_tpp (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,
        reference                       NVARCHAR(64)    NOT NULL,
        legal_name_en                   NVARCHAR(256)   NOT NULL,
        legal_name_ar                   NVARCHAR(256)   NOT NULL,
        sama_licence_ref                NVARCHAR(64),
        fapi_client_id                  NVARCHAR(128),
        roles                           NVARCHAR(32)    NOT NULL,
        country                         NVARCHAR(2),
        status                          NVARCHAR(16)    NOT NULL DEFAULT N'PENDING_KYB',
        onboarded_at                    DATE,
        suspended_at                    DATETIME2,
        revoked_at                      DATETIME2,
        suspension_reason_en            NVARCHAR(MAX),
        suspension_reason_ar            NVARCHAR(MAX),
        cert_fingerprint                NVARCHAR(128),
        cert_expires_at                 DATE,
        rate_limit_per_sec              INT,
        account_manager_user_id         BIGINT,
        compliance_officer_user_id      BIGINT,
        active_consent_count            INT             NOT NULL DEFAULT 0,
        open_incident_count             INT             NOT NULL DEFAULT 0,
        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      BIGINT,
        updated_by                      BIGINT,
        is_deleted                      SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                      DATETIME2,
        deleted_by                      BIGINT,
        row_version                     ROWVERSION
    );
    CREATE UNIQUE INDEX ux_ob_tpp_reference
        ON openbanking.ob_tpp (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ob_tpp_status
        ON openbanking.ob_tpp (organisation_id, status);
    CREATE INDEX ix_ob_tpp_cert_expiry
        ON openbanking.ob_tpp (organisation_id, cert_expires_at);
END
"""

CONSENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'openbanking' AND t.name = 'ob_consent')
BEGIN
    CREATE TABLE openbanking.ob_consent (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        tpp_id                      BIGINT          NOT NULL,
        consent_reference           NVARCHAR(96)    NOT NULL,
        customer_hash               NVARCHAR(128)   NOT NULL,
        customer_label_en           NVARCHAR(256),
        customer_label_ar           NVARCHAR(256),
        consent_type                NVARCHAR(32)    NOT NULL,
        scopes                      NVARCHAR(512)   NOT NULL,
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'AUTHORISED',
        granted_at                  DATETIME2       NOT NULL,
        expires_at                  DATE,
        revoked_at                  DATETIME2,
        revoked_by                  NVARCHAR(16),
        revocation_reason_en        NVARCHAR(MAX),
        revocation_reason_ar        NVARCHAR(MAX),
        amount_cap_sar              DECIMAL(18, 2),
        frequency_window_days       INT,
        call_count                  BIGINT          NOT NULL DEFAULT 0,
        last_used_at                DATETIME2,
        created_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT,
        updated_by                  BIGINT,
        is_deleted                  SMALLINT        NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2,
        deleted_by                  BIGINT,
        row_version                 ROWVERSION
    );
    CREATE UNIQUE INDEX ux_ob_consent_reference
        ON openbanking.ob_consent (organisation_id, consent_reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ob_consent_tpp
        ON openbanking.ob_consent (organisation_id, tpp_id, granted_at DESC);
    CREATE INDEX ix_ob_consent_status
        ON openbanking.ob_consent (organisation_id, status);
END
"""

APICALL_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'openbanking' AND t.name = 'ob_api_call')
BEGIN
    CREATE TABLE openbanking.ob_api_call (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        tpp_id              BIGINT          NOT NULL,
        consent_reference   NVARCHAR(96),
        occurred_at         DATETIME2       NOT NULL,
        http_method         NVARCHAR(8)     NOT NULL,
        endpoint            NVARCHAR(512)   NOT NULL,
        status_code         INT             NOT NULL,
        latency_ms          INT,
        scopes_used         NVARCHAR(512),
        customer_hash       NVARCHAR(128),
        source_ip_hash      NVARCHAR(64),
        interaction_id      NVARCHAR(64),
        response_bytes      INT,
        error_reason        NVARCHAR(512),
        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT,
        updated_by          BIGINT,
        is_deleted          SMALLINT        NOT NULL DEFAULT 0,
        deleted_at          DATETIME2,
        deleted_by          BIGINT,
        row_version         ROWVERSION
    );
    CREATE INDEX ix_ob_call_tpp_time
        ON openbanking.ob_api_call (organisation_id, tpp_id, occurred_at DESC);
    CREATE INDEX ix_ob_call_status
        ON openbanking.ob_api_call (organisation_id, status_code, occurred_at DESC);
END
"""

INCIDENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'openbanking' AND t.name = 'ob_incident')
BEGIN
    CREATE TABLE openbanking.ob_incident (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        reference                   NVARCHAR(64)    NOT NULL,
        tpp_id                      BIGINT,
        incident_type               NVARCHAR(32)    NOT NULL,
        severity                    NVARCHAR(12)    NOT NULL DEFAULT N'MEDIUM',
        status                      NVARCHAR(16)    NOT NULL DEFAULT N'OPEN',
        title_en                    NVARCHAR(256)   NOT NULL,
        title_ar                    NVARCHAR(256)   NOT NULL,
        description_en              NVARCHAR(MAX),
        description_ar              NVARCHAR(MAX),
        detected_at                 DATETIME2       NOT NULL,
        contained_at                DATETIME2,
        resolved_at                 DATETIME2,
        closed_at                   DATETIME2,
        sama_reportable             BIT             NOT NULL DEFAULT 0,
        sama_notification_ref       NVARCHAR(128),
        sama_notified_at            DATETIME2,
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
    CREATE UNIQUE INDEX ux_ob_incident_reference
        ON openbanking.ob_incident (organisation_id, reference)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ob_incident_status
        ON openbanking.ob_incident (organisation_id, status, severity, detected_at DESC);
END
"""

EVENT_TABLE = """
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'openbanking' AND t.name = 'ob_event')
BEGIN
    CREATE TABLE openbanking.ob_event (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        tpp_id              BIGINT,
        consent_id          BIGINT,
        incident_id         BIGINT,
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
    CREATE INDEX ix_ob_event_tpp
        ON openbanking.ob_event (organisation_id, tpp_id, occurred_at DESC);
    CREATE INDEX ix_ob_event_consent
        ON openbanking.ob_event (organisation_id, consent_id, occurred_at DESC);
    CREATE INDEX ix_ob_event_incident
        ON openbanking.ob_event (organisation_id, incident_id, occurred_at DESC);
END
"""


def main() -> None:
    print(">>> Bootstrapping openbanking schema + 5 tables")
    for label, sql in [
        ("schema",        SCHEMA),
        ("ob_tpp",        TPP_TABLE),
        ("ob_consent",    CONSENT_TABLE),
        ("ob_api_call",   APICALL_TABLE),
        ("ob_incident",   INCIDENT_TABLE),
        ("ob_event",      EVENT_TABLE),
    ]:
        try:
            cur.execute(sql)
            print(f"  ✓ {label}")
        except pyodbc.Error as e:
            print(f"  ! {label} failed: {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'openbanking'")
    print(f"✓ done — {cur.fetchone()[0]} table(s) in openbanking schema")


if __name__ == '__main__':
    main()
