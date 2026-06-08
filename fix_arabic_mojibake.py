#!/usr/bin/env python3
"""Repair mojibake'd text (Arabic + English smart quotes / em-dashes).

Symptom: Arabic rows display as "Ø§Ø¹ØªÙ…Ø§Ø¯ Ø³ÙŠØ§Ø³Ø©"; English rows show
"â€"" for em-dashes, etc. Root cause is the same — UTF-8 bytes were
interpreted as CP1252 (or a mix of CP1252 + Latin-1) during the migration
parse, and the resulting Unicode codepoints landed in NVARCHAR storage.

This script scans every NVARCHAR column of every user table (skipping the
flyway history), and runs ftfy.fix_encoding() on any cell that smells like
mojibake. ftfy handles the mixed-encoding case correctly.

NOTE: this uses ftfy.fix_encoding(), NOT ftfy.fix_text(). fix_encoding ONLY
reverses the UTF-8<->CP1252/Latin-1 double-encoding. fix_text is a kitchen
sink that also unescapes HTML entities and applies NFC normalisation — on this
DB that would corrupt legitimate cells (e.g. JSON columns, markdown containing
"5x5") that are NOT mojibaked. Keep this on fix_encoding.
"""
from __future__ import annotations
import sys, pyodbc, ftfy

# Windows consoles default to cp1252 and cannot print ≤ / Arabic, etc.
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

CONN = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes"
)


def repair(s: str) -> str | None:
    """Reverse encoding-mojibake only. Returns the fixed string, or None when
    the cell is already clean (fix_encoding is a no-op on non-mojibaked text)."""
    if not s:
        return None
    try:
        fixed = ftfy.fix_encoding(s)
    except Exception:
        return None
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
                # Force each text param to bind as SQL_WVARCHAR (-> nvarchar(max)).
                # By default pyodbc streams long strings as SQL_WLONGVARCHAR, which
                # maps to legacy ntext — and ntext CANNOT carry this DB's _SC_UTF8
                # collation (error 4189), so long cells would silently fail to
                # update. SQL_WVARCHAR(max) holds the full Unicode value.
                cur.setinputsizes([(pyodbc.SQL_WVARCHAR, 0, 0)] * len(sets)
                                  + [(pyodbc.SQL_INTEGER, 0, 0)])
                cur.execute(
                    f"UPDATE [{schema}].[{table}] SET {', '.join(sets)} WHERE id = ?",
                    *params,
                )
                fixed_in_table += 1
            except pyodbc.Error as e:
                # Other rows in the same table can still succeed under autocommit=True.
                msg = str(e)
                if skipped_rows < 5:
                    print(f"    skip {schema}.{table} id={row_id}: {msg[:120]}")
                skipped_rows += 1
            finally:
                cur.setinputsizes(None)
        if fixed_in_table:
            print(f"  {schema}.{table}: repaired {fixed_in_table} row(s)")
            total_fixed += fixed_in_table
    cn.close()
    print(f"\nDone - repaired {total_fixed} row(s) total. "
          f"Skipped {skipped_rows} row(s) and {skipped_tables} table(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
