# -*- coding: utf-8 -*-
"""Introspect the dev DB and emit a Flyway V1 baseline migration.

Reads schemas + tables + columns + indexes from the live dev DB, generates
DDL that recreates the same shape. Only includes Sprint-1 schemas (everything
that the V3-V19 migrations don't cover).

Output goes to:
  tss-grc-backend/src/main/resources/db/migration/sqlserver/V1__core_schemas.sql

Usage: python .tools/introspect_schema.py
"""
import os, sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Encrypt=No;'
)

# Schemas managed by Vxx (V3+) Flyway migrations — exclude from baseline
FLYWAY_MANAGED_SCHEMAS = {
    # V3 — compliance
    'compliance',
    # V4, V6 — incident
    'incident',
    # V7 — tprm
    'tprm',
    # V8, V9, V10 — evidence
    'evidence',
    # V11 — intake
    'intake',
    # V12 — platform.persistence
    # V13 — file_metadata
    # V14 — sso
    # V15-V19 — *_lc lifecycle modules
    'risk_lc', 'audit_lc', 'privacy_lc', 'bcm_lc', 'cyber_lc',
    # V20 — bilingual_reporting_schedule
}

SYSTEM_SCHEMAS = {
    'sys', 'INFORMATION_SCHEMA', 'guest', 'dbo',
    'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin',
    'db_backupoperator', 'db_datareader', 'db_datawriter',
    'db_denydatareader', 'db_denydatawriter',
}

OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    'tss-grc-backend', 'src', 'main', 'resources', 'db', 'migration', 'sqlserver',
    'V1__core_schemas.sql'
)


def baseline_schemas(cur):
    """Return list of schemas to include in V1 baseline."""
    cur.execute("SELECT name FROM sys.schemas")
    all_schemas = [r.name for r in cur.fetchall()]
    return sorted(s for s in all_schemas
                  if s not in SYSTEM_SCHEMAS
                  and s not in FLYWAY_MANAGED_SCHEMAS
                  and not s.startswith('db_'))


def tables_in_schema(cur, schema):
    cur.execute("""
        SELECT t.name FROM sys.tables t
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE s.name = ?
        ORDER BY t.name
    """, schema)
    return [r.name for r in cur.fetchall()]


def columns_for_table(cur, schema, table):
    """Return [(name, type_definition, is_nullable, default, is_identity)]."""
    cur.execute("""
        SELECT
            c.name,
            tp.name AS type_name,
            c.max_length,
            c.precision,
            c.scale,
            c.is_nullable,
            c.is_identity,
            CAST(ic.seed_value      AS BIGINT) AS seed_value,
            CAST(ic.increment_value AS BIGINT) AS increment_value,
            dc.definition AS default_def
        FROM sys.columns c
        JOIN sys.tables t ON t.object_id = c.object_id
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        JOIN sys.types tp ON tp.user_type_id = c.user_type_id
        LEFT JOIN sys.identity_columns ic ON ic.object_id = c.object_id AND ic.column_id = c.column_id
        LEFT JOIN sys.default_constraints dc ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
        WHERE s.name = ? AND t.name = ?
        ORDER BY c.column_id
    """, schema, table)

    rows = []
    for r in cur.fetchall():
        # Build type definition
        t = r.type_name.upper()
        if t in ('NVARCHAR', 'NCHAR'):
            length = r.max_length // 2 if r.max_length > 0 else -1
            type_def = f"{t}({length if length > 0 else 'MAX'})"
        elif t in ('VARCHAR', 'CHAR', 'BINARY', 'VARBINARY'):
            length = r.max_length
            type_def = f"{t}({length if length > 0 else 'MAX'})"
        elif t in ('DECIMAL', 'NUMERIC'):
            type_def = f"{t}({r.precision},{r.scale})"
        elif t == 'TIMESTAMP' or t == 'ROWVERSION':
            type_def = 'ROWVERSION'
        else:
            type_def = t
        rows.append({
            'name': r.name,
            'type': type_def,
            'nullable': bool(r.is_nullable),
            'identity': bool(r.is_identity),
            'identity_seed': r.seed_value if r.is_identity else None,
            'identity_inc': r.increment_value if r.is_identity else None,
            'default': r.default_def,
        })
    return rows


def primary_key_for_table(cur, schema, table):
    cur.execute("""
        SELECT c.name
        FROM sys.indexes i
        JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
        JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        JOIN sys.tables t ON t.object_id = i.object_id
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE i.is_primary_key = 1 AND s.name = ? AND t.name = ?
        ORDER BY ic.key_ordinal
    """, schema, table)
    return [r.name for r in cur.fetchall()]


def non_pk_indexes(cur, schema, table):
    """Return [(name, [cols], is_unique, filter_def)] for non-PK, non-UC indexes."""
    cur.execute("""
        SELECT i.name, i.is_unique, i.filter_definition
        FROM sys.indexes i
        JOIN sys.tables t ON t.object_id = i.object_id
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE s.name = ? AND t.name = ?
          AND i.is_primary_key = 0
          AND i.is_unique_constraint = 0
          AND i.type > 0   -- skip heap
        ORDER BY i.name
    """, schema, table)
    indexes = []
    for ix_row in cur.fetchall():
        if ix_row.name is None: continue
        cur.execute("""
            SELECT c.name
            FROM sys.indexes i
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            JOIN sys.tables t ON t.object_id = i.object_id
            JOIN sys.schemas s ON s.schema_id = t.schema_id
            WHERE i.name = ? AND s.name = ? AND t.name = ?
            ORDER BY ic.key_ordinal
        """, ix_row.name, schema, table)
        cols = [r.name for r in cur.fetchall()]
        indexes.append({
            'name': ix_row.name,
            'cols': cols,
            'unique': bool(ix_row.is_unique),
            'filter': ix_row.filter_definition,
        })
    return indexes


def emit_table(schema, table, cols, pk, indexes):
    """Emit CREATE TABLE + indexes for one table."""
    out = []
    out.append(f"-- ─── {schema}.{table} ─────────────────────────────────────────")
    out.append(f"IF OBJECT_ID('{schema}.{table}','U') IS NULL")
    out.append(f"BEGIN")
    out.append(f"    CREATE TABLE {schema}.{table} (")
    col_lines = []
    for c in cols:
        line = f"        [{c['name']}] {c['type']}"
        if c['identity']:
            line += f" IDENTITY({c['identity_seed']},{c['identity_inc']})"
        if not c['nullable']:
            line += " NOT NULL"
        if c['default']:
            line += f" DEFAULT {c['default']}"
        col_lines.append(line)
    if pk:
        pk_cols = ', '.join(f"[{c}]" for c in pk)
        col_lines.append(f"        CONSTRAINT [pk_{table}] PRIMARY KEY ({pk_cols})")
    out.append(',\n'.join(col_lines))
    out.append(f"    );")
    out.append(f"END;")
    out.append("GO")

    for ix in indexes:
        unique_kw = 'UNIQUE ' if ix['unique'] else ''
        cols_str = ', '.join(f"[{c}]" for c in ix['cols'])
        filter_clause = f" WHERE {ix['filter']}" if ix['filter'] else ''
        out.append(f"IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = '{ix['name']}' AND object_id = OBJECT_ID('{schema}.{table}'))")
        out.append(f"    CREATE {unique_kw}INDEX [{ix['name']}] ON {schema}.{table} ({cols_str}){filter_clause};")
        out.append("GO")
    return '\n'.join(out)


def main():
    print("Connecting to dev DB…")
    conn = pyodbc.connect(CONN_STR)
    cur = conn.cursor()

    schemas = baseline_schemas(cur)
    print(f"Baseline schemas: {schemas}")

    out_lines = [
        "-- V1 — Core schema baseline (Sprint 1 + Sprint 2 schemas).",
        "--",
        "-- This file is generated by .tools/introspect_schema.py from the live dev DB.",
        "-- It re-creates the schemas that were originally bootstrapped by Python scripts",
        "-- (setup_cyber_db.py, setup_bcm_db.py, etc.) so a fresh prod deploy can use",
        "-- Flyway from V1 onwards instead of needing the Python scripts.",
        "--",
        "-- Schemas managed by V3+ migrations (compliance, incident, tprm, evidence,",
        "-- intake, risk_lc, audit_lc, privacy_lc, bcm_lc, cyber_lc) are NOT in this",
        "-- file — they're owned by their respective Vxx migrations.",
        "--",
        "-- Idempotent: every CREATE is wrapped in IF NOT EXISTS.",
        "",
    ]

    # Create schemas
    for s in schemas:
        out_lines.append(f"IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '{s}')")
        out_lines.append(f"    EXEC('CREATE SCHEMA {s}');")
        out_lines.append("GO")
    out_lines.append("")

    # Create tables per schema
    table_count = 0
    for s in schemas:
        tables = tables_in_schema(cur, s)
        if not tables: continue
        out_lines.append(f"-- ════════ Schema [{s}] ({len(tables)} tables) ═══════════════════════")
        out_lines.append("")
        for t in tables:
            cols = columns_for_table(cur, s, t)
            pk = primary_key_for_table(cur, s, t)
            indexes = non_pk_indexes(cur, s, t)
            out_lines.append(emit_table(s, t, cols, pk, indexes))
            out_lines.append("")
            table_count += 1

    output = '\n'.join(out_lines)
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        f.write(output)
    print(f"\n✓ Wrote {OUTPUT_PATH}")
    print(f"  {len(schemas)} schemas, {table_count} tables, {len(output.splitlines())} lines")


if __name__ == '__main__':
    main()
