"""Load V209 — F8 automation rules for AI/ML Model Lifecycle. Sprint 31 PR #2."""
from __future__ import annotations
import sys
from pathlib import Path
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
V209 = (Path(__file__).parent
        / 'tss-grc-backend/src/main/resources/db/migration/sqlserver'
        / 'V209__ai_lifecycle_automation_rules.sql').read_text(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;')
conn.autocommit = True
cur = conn.cursor()


def main():
    print(">>> Loading V209 — AI lifecycle automation rules AR-271..AR-280")
    sql = V209.replace('\nGO\n', '\n').replace('GO\n', '\n').strip()
    try:
        cur.execute(sql)
    except pyodbc.Error as e:
        print(f"  ! failed: {e}")
        sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM platform.automation_rules "
                "WHERE rule_code BETWEEN 'AR-271' AND 'AR-280'")
    print(f"+ loaded — {cur.fetchone()[0]} rule(s) live")


if __name__ == '__main__':
    main()
