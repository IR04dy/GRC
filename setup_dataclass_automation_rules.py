"""Load V210 — F8 automation rules for Data Classification. Sprint 32 PR #2."""
from __future__ import annotations
import sys
from pathlib import Path
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
V210 = (Path(__file__).parent
        / 'tss-grc-backend/src/main/resources/db/migration/sqlserver'
        / 'V210__dataclass_automation_rules.sql').read_text(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;')
conn.autocommit = True
cur = conn.cursor()


def main():
    print(">>> Loading V210 — dataclass automation rules AR-281..AR-290")
    sql = V210.replace('\nGO\n', '\n').replace('GO\n', '\n').strip()
    try:
        cur.execute(sql)
    except pyodbc.Error as e:
        print(f"  ! failed: {e}"); sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM platform.automation_rules "
                "WHERE rule_code BETWEEN 'AR-281' AND 'AR-290'")
    print(f"+ loaded — {cur.fetchone()[0]} rule(s) live")


if __name__ == '__main__':
    main()
