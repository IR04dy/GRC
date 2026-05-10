# -*- coding: utf-8 -*-
"""Bootstrap the five lifecycle schemas (risk_lc, audit_lc, privacy_lc, bcm_lc, cyber_lc).

In dev profile Flyway is OFF — these migrations need to be applied manually.
This script reads the V15-V19 SQL migration files, splits on GO batch
separators, and executes each batch against the dev DB.

Idempotent: re-running drops the tables first, so it's safe to re-run after
you've changed a migration. Drops are inside try/except so a missing table
is not an error.

Usage:
    python setup_lifecycle_db.py

Requires: pyodbc, ODBC Driver 17 for SQL Server, dev DB at localhost / sa / tss.123.
"""
import os
import re
import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)

MIGRATION_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    'tss-grc-backend', 'src', 'main', 'resources', 'db', 'migration', 'sqlserver'
)

# Migration files in apply order. Each is a Flyway migration that we run by
# hand because dev profile has spring.flyway.enabled=false.
MIGRATIONS = [
    ('V15__risk_lifecycle_module.sql',    'risk_lc'),
    ('V16__audit_lifecycle_module.sql',   'audit_lc'),
    ('V17__privacy_lifecycle_module.sql', 'privacy_lc'),
    ('V18__bcm_lifecycle_module.sql',     'bcm_lc'),
    ('V19__cyber_lifecycle_module.sql',   'cyber_lc'),
]

# Tables to drop per schema (most-dependent first). If a future migration adds
# more tables, add them here. Drops use IF OBJECT_ID(...) IS NOT NULL so missing
# tables are silently ignored.
DROP_ORDER = {
    'risk_lc': [
        'risk_lc.notification_outbox',
        'risk_lc.activity_log',
        'risk_lc.acceptance_requests',
        'risk_lc.kri_escalation_log',
        'risk_lc.kri_breaches',
        'risk_lc.treatment_actions',
        'risk_lc.treatment_plans',
    ],
    'audit_lc': [
        'audit_lc.capa_tasks',
        'audit_lc.findings',
        'audit_lc.working_papers',
        'audit_lc.annual_plans',
    ],
    'privacy_lc': [
        'privacy_lc.dsr_cases',
        'privacy_lc.cross_border_transfers',
        'privacy_lc.dpia_assessments',
        'privacy_lc.ropa_activities',
    ],
    'bcm_lc': [
        'bcm_lc.invocations',
        'bcm_lc.drills',
        'bcm_lc.recovery_strategies',
        'bcm_lc.bia_records',
    ],
    'cyber_lc': [
        'cyber_lc.cyber_incidents',
        'cyber_lc.vulnerabilities',
        'cyber_lc.threat_intel',
        'cyber_lc.assets',
    ],
}


def split_on_go(sql_text):
    """Split a Flyway SQL file into batches on `GO` separators.

    GO must be on its own line (case-insensitive, optional whitespace). This
    matches what sqlcmd does. Comments and blank batches are skipped.
    """
    pattern = re.compile(r'^\s*GO\s*$', re.IGNORECASE | re.MULTILINE)
    batches = pattern.split(sql_text)
    return [b.strip() for b in batches if b.strip()]


def run_quiet(cur, sql):
    """Execute DDL, treat 'does not exist' as not-an-error."""
    try:
        cur.execute(sql)
    except pyodbc.Error as e:
        msg = str(e).lower()
        # Common "already exists" and "doesn't exist" outcomes during cleanup.
        benign = ('already', 'cannot drop', 'does not exist', 'is not a valid')
        if not any(w in msg for w in benign):
            raise


def apply_migration(cur, sql_path, schema):
    print(f"\n── {os.path.basename(sql_path)}  →  schema [{schema}] ──")

    # 1) Drop tables in dependency order so we can re-run cleanly.
    for tbl in DROP_ORDER.get(schema, []):
        run_quiet(cur, f"IF OBJECT_ID('{tbl}','U') IS NOT NULL DROP TABLE {tbl}")
        print(f"  dropped (if existed): {tbl}")

    # 2) Read and execute the migration in GO-delimited batches.
    with open(sql_path, 'r', encoding='utf-8') as f:
        sql_text = f.read()

    batches = split_on_go(sql_text)
    for i, batch in enumerate(batches, 1):
        try:
            cur.execute(batch)
        except pyodbc.Error as e:
            preview = batch.strip().split('\n', 1)[0][:80]
            print(f"  ERR in batch {i}/{len(batches)} starting `{preview}`")
            print(f"  -> {str(e)[:300]}")
            raise

    print(f"  applied {len(batches)} batches")


def main():
    print(f"Connecting to {CONN_STR.split(';')[1]}…")
    conn = pyodbc.connect(CONN_STR)
    conn.autocommit = True
    cur = conn.cursor()

    for filename, schema in MIGRATIONS:
        path = os.path.join(MIGRATION_DIR, filename)
        if not os.path.isfile(path):
            print(f"SKIP — migration not found: {path}")
            continue
        apply_migration(cur, path, schema)

    print("\n" + "=" * 60)
    print("All lifecycle schemas applied.")
    print("Next: run `python setup_lifecycle_seed.py` for demo data.")
    print("=" * 60)


if __name__ == '__main__':
    main()
