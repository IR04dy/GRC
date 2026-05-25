"""Bootstrap the COI Disclosure Register schema in the dev DB.

Applies V198__coi_register.sql (Sprint 13). Dev profile has Flyway
disabled, so all module migrations apply via Python.

Idempotent: V198 itself uses IF NOT EXISTS guards on every CREATE,
so re-running is safe.

Usage:
  python setup_coi_db.py
"""
from __future__ import annotations

import os
import re
import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

MIGRATION = os.path.join(
    'tss-grc-backend', 'src', 'main', 'resources', 'db', 'migration', 'sqlserver',
    'V198__coi_register.sql',
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
    for c in chunks:
        if not isinstance(c, str):
            continue
        t = c.strip()
        if t:
            out.append(t)
    return out


def main() -> None:
    print(f">>> Bootstrapping COI register from {MIGRATION}")
    with open(MIGRATION, encoding='utf-8') as f:
        sql = f.read()
    batches = split_batches(sql)
    print(f"--- {len(batches)} batch(es)")
    for i, b in enumerate(batches, 1):
        try:
            cur.execute(b)
        except pyodbc.Error as e:
            preview = b[:120].replace('\n', ' ')
            print(f"! batch {i} failed ({preview}…): {e}")
            sys.exit(1)
    cur.execute("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                "ON t.schema_id = s.schema_id WHERE s.name = 'ethics' "
                "AND t.name LIKE 'coi_%'")
    print(f"✓ done — {cur.fetchone()[0]} coi_* tables in ethics schema")


if __name__ == '__main__':
    main()
