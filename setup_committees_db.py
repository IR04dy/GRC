"""Bootstrap the committees module in the dev DB.

In the dev profile Flyway is disabled (CLAUDE.md), so the V154..V159
SQL migrations that create the admin.committee_templates,
admin.committee_standing_items and admin.committee_form_templates
tables — plus the 19 templates / ~150 standing items / 10 forms —
never get a chance to run.

The result the user sees: `/committees` shows "unable to load data"
and `/committees/catalog` returns Internal Server Error because
the JPA query hits a missing table.

This script ports those migrations into the Python-managed dev
workflow. It reads each V154..V159 file from
`tss-grc-backend/src/main/resources/db/migration/sqlserver/`,
splits it on the SQL Server `GO` batch separator, and executes each
batch in order.

Idempotent: every migration uses
  IF NOT EXISTS (...) CREATE TABLE
  IF NOT EXISTS (... WHERE template_code = ...) INSERT
  CREATE OR ALTER PROCEDURE
patterns, so re-running this script is safe.

Usage:
  python setup_committees_db.py
"""
from __future__ import annotations

import os
import re
import sys

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

# ── Config ──────────────────────────────────────────────────────────────
MIGRATIONS_DIR = os.path.join(
    'tss-grc-backend', 'src', 'main', 'resources', 'db', 'migration', 'sqlserver',
)

# Order matters — V154 creates tables, V155-V159 seed rows that reference them.
MIGRATIONS = [
    'V154__committee_grc_foundation.sql',
    'V155__committee_standing_items_seed.sql',
    'V156__committee_form_templates_seed.sql',
    'V157__committee_worked_examples_seed.sql',
    'V158__committee_automation_rules.sql',
    'V159__committee_demo_expansion_seed.sql',
]

# ── DB connection ───────────────────────────────────────────────────────
conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()


# ── Helpers ─────────────────────────────────────────────────────────────
# Strip GO lines (SQL Server batch separator — pyodbc can't pass it
# through). GO can sit on its own line, optionally followed by a count
# we ignore. Comment lines are kept so error messages stay readable.
GO_PATTERN = re.compile(r'^\s*GO\s*(\d+)?\s*$', re.IGNORECASE | re.MULTILINE)


def split_into_batches(sql: str) -> list[str]:
    """Split a .sql file into individual batches at GO separators."""
    chunks = GO_PATTERN.split(sql)
    # GO_PATTERN keeps the optional count group as None alongside each
    # batch — filter out non-string Nones and trim empties.
    batches = []
    for chunk in chunks:
        if not isinstance(chunk, str):
            continue
        trimmed = chunk.strip()
        if trimmed:
            batches.append(trimmed)
    return batches


def run_batch(label: str, sql: str) -> bool:
    """Execute one SQL batch. Tolerates 'already exists' / 'duplicate
    key' errors — those are expected on a re-run since every migration
    is idempotent in some form. Anything else surfaces."""
    try:
        cur.execute(sql)
        return True
    except pyodbc.Error as e:
        s = str(e).lower()
        # Treat seed-row duplicates and idempotent DDL collisions as a
        # successful no-op so re-running the script is safe.
        skip_signals = [
            'violation of primary key',
            'violation of unique key',
            'duplicate key',
            'already an object',
            'already exists',
            'cannot add constraint',
        ]
        if any(sig in s for sig in skip_signals):
            return False
        print(f"  ERR {label}: {str(e)[:200]}")
        return False


def run_migration(filename: str) -> None:
    """Read, split, execute one migration file."""
    path = os.path.join(MIGRATIONS_DIR, filename)
    if not os.path.exists(path):
        print(f"  -- {filename} (file missing — skipping)")
        return

    print(f"\n=== {filename} ===")
    with open(path, encoding='utf-8') as f:
        sql = f.read()

    batches = split_into_batches(sql)
    ok = 0
    for i, batch in enumerate(batches, 1):
        # Use the first non-comment line as the label so console output
        # is useful when something genuinely breaks.
        label_lines = [ln for ln in batch.splitlines()
                       if ln.strip() and not ln.strip().startswith('--')]
        label = (label_lines[0][:60] if label_lines else f'batch {i}')
        if run_batch(f"{filename}#{i}", batch):
            ok += 1
    print(f"  ({ok}/{len(batches)} batches ran cleanly)")


def smoke_test() -> None:
    """Confirm the catalog tables now exist and have rows."""
    print("\n=== smoke test ===")
    expected = {
        'admin.committee_templates':     19,
        'admin.committee_standing_items': 1,   # at least 1
        'admin.committee_form_templates': 10,
    }
    for table, min_rows in expected.items():
        try:
            cur.execute(f"SELECT COUNT(*) FROM {table}")
            n = cur.fetchone()[0]
            mark = 'OK ' if n >= min_rows else 'WARN'
            print(f"  {mark} {table}: {n} rows (expected >= {min_rows})")
        except pyodbc.Error as e:
            print(f"  ERR {table}: {str(e)[:120]}")


if __name__ == '__main__':
    print("Committees module bootstrap — V154..V159")
    print(f"Migrations dir: {os.path.abspath(MIGRATIONS_DIR)}")
    for fn in MIGRATIONS:
        run_migration(fn)
    smoke_test()
    print("\nDone. Restart the backend if it was running; /committees and "
          "/committees/catalog should now serve real data.")
