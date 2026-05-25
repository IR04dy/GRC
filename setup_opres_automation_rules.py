"""Seed F8 OpRes automation rules in the dev DB.

Runs V201__opres_automation_rules.sql against the local TSS_GRC. The
dev profile has Flyway disabled (CLAUDE.md), so SQL migrations are
applied via Python scripts.

Each rule INSERT is guarded by an IF NOT EXISTS on (org, rule_code),
so re-running is safe.

Usage:
  python setup_opres_automation_rules.py
"""
from __future__ import annotations

import os
import re
import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

MIGRATION = os.path.join(
    'tss-grc-backend', 'src', 'main', 'resources', 'db', 'migration', 'sqlserver',
    'V201__opres_automation_rules.sql',
)

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()

# Strip the SQL Server GO batch separator — pyodbc can't pass GO through.
GO_PATTERN = re.compile(r'^\s*GO\s*(\d+)?\s*$', re.IGNORECASE | re.MULTILINE)


def split_batches(sql: str) -> list[str]:
    chunks = GO_PATTERN.split(sql)
    batches = []
    for chunk in chunks:
        if not isinstance(chunk, str):
            continue
        trimmed = chunk.strip()
        if trimmed:
            batches.append(trimmed)
    return batches


def main():
    print(f">>> Seeding F8 OpRes automation rules from {MIGRATION}")
    with open(MIGRATION, encoding='utf-8') as f:
        sql = f.read()
    batches = split_batches(sql)
    print(f"--- {len(batches)} batch(es)")
    for i, batch in enumerate(batches, 1):
        try:
            cur.execute(batch)
        except pyodbc.Error as e:
            preview = batch[:100].replace('\n', ' ')
            print(f"! batch {i} failed ({preview}…): {e}")
            sys.exit(1)
    cur.execute(
        "SELECT COUNT(*) FROM platform.automation_rules "
        "WHERE rule_code LIKE 'AR-19_' OR rule_code = 'AR-200'"
    )
    seeded = cur.fetchone()[0]
    print(f"✓ done — {seeded} OpRes rules present (AR-191..AR-200)")


if __name__ == '__main__':
    main()
