"""Evidence-lifecycle hardening — dev DB migration (idempotent).

Adds the single new column the hardening work needs:
  evidence.evidence_items.request_id  BIGINT NULL
    -> closes the Request<->Item linkage gap (an EvidenceItem can now point
       back to the EvidenceRequest it fulfils; on APPROVED the request's
       fulfilled_by_id / fulfilled_at get stamped).

The other hardening changes are status-string-only (NEEDS_REVISION, EXPIRED)
and fit the existing status column (length 30) — no DDL needed.

Dev DB is Python-bootstrapped (Flyway is OFF in dev); the matching Flyway
migration V211 covers staging/prod. Safe to re-run.

Usage: python setup_evidence_lifecycle_hardening.py
"""
from __future__ import annotations
import sys
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")
conn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;")
conn.autocommit = False
cur = conn.cursor()


def add_column_if_missing(schema, table, column, ddl_type):
    cur.execute(
        "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS "
        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
        schema, table, column)
    if cur.fetchone():
        print(f"= {schema}.{table}.{column} already present — skipped")
        return False
    cur.execute(f"ALTER TABLE {schema}.{table} ADD {column} {ddl_type}")
    print(f"+ added {schema}.{table}.{column} {ddl_type}")
    return True


def main():
    print(">>> Evidence-lifecycle hardening — dev DB migration")
    try:
        add_column_if_missing("evidence", "evidence_items", "request_id", "BIGINT NULL")
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"!!! ROLLED BACK: {e}")
        raise
    # Verify
    cur.execute(
        "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
        "WHERE TABLE_SCHEMA='evidence' AND TABLE_NAME='evidence_items' "
        "AND COLUMN_NAME='request_id'")
    row = cur.fetchone()
    print(f"+ verify: request_id -> {row[1]} nullable={row[2]}" if row else "! request_id NOT found")
    print(">>> done.")


if __name__ == "__main__":
    main()
