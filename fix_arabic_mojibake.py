#!/usr/bin/env python3
"""Repair mojibake'd text (Arabic + English smart quotes / em-dashes).

Symptom: Arabic rows display as "Ø§Ø¹ØªÙ…Ø§Ø¯ Ø³ÙŠØ§Ø³Ø©"; English rows show
"â€"" for em-dashes, etc. Root cause is the same — UTF-8 bytes were
interpreted as CP1252 (or a mix of CP1252 + Latin-1) during the migration
parse, and the resulting Unicode codepoints landed in NVARCHAR storage.

This script scans every NVARCHAR column of every user table (skipping the
flyway history), and runs ftfy.fix_text() on any cell that smells like
mojibake. ftfy handles the mixed-encoding case correctly.
"""
from __future__ import annotations
import sys, pyodbc, ftfy

CONN = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes"
)

# Markers we use to flag candidate cells. Any one of these implies likely
# mojibake. The Ø/Ù pair catches UTF-8 Arabic decoded as CP1252; â€ catches
# UTF-8 em-dash / quote chars decoded as CP1252; the others catch the
# CP1252-specific replacements that show up alongside.
MARKERS = ("Ø", "Ù", "â€", "Ã", "‚", "ˆ", "—", "Ŝ", "Ÿ", "Â")


def looks_mojibaked(s: str) -> bool:
    return any(ch in s for ch in MARKERS)


def repair(s: str) -> str | None:
    if not s or not looks_mojibaked(s):
        return None
    fixed = ftfy.fix_text(s)
    return fixed if fixed != s else None


def discover_columns(cn: pyodbc.Connection) -> list[tuple[str, str, list[str]]]:
    """Return [(schema, table, [text_cols])] for every user table."""
    cur = cn.cursor()
    cur.execute(
        """
        SELECT s.name, t.name, c.name
        FROM sys.columns c
        JOIN sys.tables  t  ON c.object_id    = t.object_id
        JOIN sys.schemas s  ON t.schema_id    = s.schema_id
        JOIN sys.types   ty ON c.user_type_id = ty.user_type_id
        WHERE ty.name IN ('nvarchar','nchar','ntext')
          AND s.name <> 'flyway'
          AND t.name <> 'flyway_schema_history'
          AND c.is_computed = 0
        ORDER BY s.name, t.name, c.column_id
        """
    )
    by_table: dict[tuple[str, str], list[str]] = {}
    for sch, tbl, col in cur.fetchall():
        by_table.setdefault((sch, tbl), []).append(col)
    return [(s, t, cols) for (s, t), cols in by_table.items()]


def main() -> int:
    # autocommit=True so a single broken row (e.g. legacy ntext column that
    # the SC_UTF8 collation can't bind) doesn't roll back everything else.
    # Repair is row-idempotent — re-running is safe.
    cn = pyodbc.connect(CONN, autocommit=True)
    cur = cn.cursor()
    total_fixed = 0
    skipped_rows = 0
    skipped_tables = 0
    for schema, table, cols in discover_columns(cn):
        # Skip tables without an integer id (we can't address rows otherwise).
        cur.execute(
            """
            SELECT 1 FROM sys.columns c
            JOIN sys.tables t ON c.object_id = t.object_id
            JOIN sys.schemas s ON t.schema_id = s.schema_id
            WHERE s.name = ? AND t.name = ? AND c.name = 'id'
            """,
            schema, table,
        )
        if not cur.fetchone():
            continue
        cols_csv = ", ".join(f"[{c}]" for c in cols)
        try:
            cur.execute(f"SELECT id, {cols_csv} FROM [{schema}].[{table}]")
            rows = cur.fetchall()
        except pyodbc.Error as e:
            print(f"  {schema}.{table}: SELECT failed - {str(e)[:120]}")
            skipped_tables += 1
            continue
        fixed_in_table = 0
        for row in rows:
            row_id = row[0]
            sets: list[str] = []
            params: list = []
            for i, c in enumerate(cols, start=1):
                v = row[i]
                if v is None or not isinstance(v, str):
                    continue
                f = repair(v)
                if f is None:
                    continue
                sets.append(f"[{c}] = ?")
                params.append(f)
            if not sets:
                continue
            params.append(row_id)
            try:
                cur.execute(
                    f"UPDATE [{schema}].[{table}] SET {', '.join(sets)} WHERE id = ?",
                    *params,
                )
                fixed_in_table += 1
            except pyodbc.Error as e:
                # Skip rows whose update fails (e.g. legacy ntext columns that
                # can't bind under SC_UTF8). Other rows in the same table can
                # still succeed under autocommit=True.
                msg = str(e)
                if skipped_rows < 5:
                    print(f"    skip {schema}.{table} id={row_id}: {msg[:120]}")
                skipped_rows += 1
        if fixed_in_table:
            print(f"  {schema}.{table}: repaired {fixed_in_table} row(s)")
            total_fixed += fixed_in_table
    cn.close()
    print(f"\nDone - repaired {total_fixed} row(s) total. "
          f"Skipped {skipped_rows} row(s) and {skipped_tables} table(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
