"""Seed F8 regreporting automation rules in the dev DB.

Runs V203__regreporting_automation_rules.sql against the local TSS_GRC.
Dev profile has Flyway disabled; SQL migrations apply via Python.

Each rule INSERT is guarded by an IF NOT EXISTS on (org, rule_code),
so re-running is safe.

Usage:
  python setup_regreporting_automation_rules.py
"""
from __future__ import annotations

import os
import re
import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

MIGRATION = os.path.join(
    'tss-grc-backend', 'src', 'main', 'resources', 'db', 'migration', 'sqlserver',
    'V203__regreporting_automation_rules.sql',
)

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()

GO_PATTERN = re.compile(r'^\s*GO\s*(\d+)?\s*$', re.IGNORECASE | re.MULTILINE)


def split_batches(sql: str) -> list[str]:
    chunks = GO_PATTERN.split(sql)
    out = []
    for chunk in chunks:
        if not isinstance(chunk, str):
            continue
        trimmed = chunk.strip()
        if trimmed:
            out.append(trimmed)
    return out


def main():
    print(f">>> Seeding F8 regreporting automation rules from {MIGRATION}")
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
        "WHERE rule_code BETWEEN 'AR-211' AND 'AR-220'"
    )
    seeded = cur.fetchone()[0]
    print(f"✓ done — {seeded} regreporting rules present (AR-211..AR-220)")


if __name__ == '__main__':
    main()
