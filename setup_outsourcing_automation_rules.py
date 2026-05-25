"""Load V208 — F8 automation rules for Outsourcing & Cloud Governance.

Sprint 30 PR #2. Seeds AR-261..AR-270 against the SAMA Outsourcing
Regulation + SAMA Cloud Computing Framework state changes emitted by
OutsourcingService.

Idempotent — every INSERT is guarded by `IF NOT EXISTS`.

Usage:  python setup_outsourcing_automation_rules.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

V208 = (Path(__file__).parent
        / 'tss-grc-backend/src/main/resources/db/migration/sqlserver'
        / 'V208__outsourcing_automation_rules.sql').read_text(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()


def main() -> None:
    print(">>> Loading V208 — outsourcing automation rules AR-261..AR-270")
    # Strip the GO separator that's TSQL-only; pyodbc executes the rest in one batch.
    sql = V208.replace('\nGO\n', '\n').replace('GO\n', '\n').strip()
    try:
        cur.execute(sql)
    except pyodbc.Error as e:
        print(f"  ! failed: {e}")
        sys.exit(1)
    cur.execute(
        "SELECT COUNT(*) FROM platform.automation_rules "
        "WHERE rule_code BETWEEN 'AR-261' AND 'AR-270'")
    print(f"+ loaded — {cur.fetchone()[0]} rule(s) live")


if __name__ == '__main__':
    main()
