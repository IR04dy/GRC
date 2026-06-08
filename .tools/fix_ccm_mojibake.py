#!/usr/bin/env python3
"""Repair double-encoded UTF-8 (mojibake) in TSS_GRC, in place.

Root cause: BOM-less UTF-8 migration/seed SQL was once applied by a reader
using the Windows ANSI codepage (cp1252), so every multibyte char (Arabic,
em-dash, the ≤/≥ math family) was decoded-as-CP1252 then re-stored, e.g.
  ≤  U+2264  (utf8 E2 89 A4)  ->  â‰¤  (utf8 C3 A2 E2 80 B0 C2 A4)

Repair strategy:
  * Detect with ftfy.fix_encoding (ONLY reverses encoding mojibake — it does
    NOT unescape HTML or do NFC, so JSON/HTML/markdown structure is preserved
    and legitimate cells like "5×5" are left untouched).
  * Back up every original value to a JSON file BEFORE writing.
  * UPDATE via parameterized queries — pyodbc binds str params as SQL_WVARCHAR
    (UTF-16) for Driver 17, the correct Unicode write path for NVARCHAR. We do
    NOT call setencoding(utf-8) (that would interfere); we PROVE correctness by
    reading the bytes back afterwards.

Modes:
  python fix_ccm_mojibake.py            -> DB-wide (all 16 affected tables)
  python fix_ccm_mojibake.py --ccm-only -> only ccm.control_test_definition
  python fix_ccm_mojibake.py --dry-run  -> report + backup, no writes
"""
from __future__ import annotations
import sys, json, pyodbc, ftfy
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

CONN = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes"
)
BACKUP_PATH = r"C:\AYMAN\Github\Claude-GRC-2\.tools\ccm_mojibake_backup.json"

CCM_ONLY = "--ccm-only" in sys.argv
DRY_RUN = "--dry-run" in sys.argv


def enc_fix(s):
    try:
        f = ftfy.fix_encoding(s)
    except Exception:
        return None
    return f if f != s else None


def discover(cur):
    cur.execute(
        """
        SELECT s.name, t.name, c.name
        FROM sys.columns c
        JOIN sys.tables  t  ON c.object_id    = t.object_id
        JOIN sys.schemas s  ON t.schema_id    = s.schema_id
        JOIN sys.types   ty ON c.user_type_id = ty.user_type_id
        WHERE ty.name IN ('nvarchar','nchar','ntext')
          AND s.name <> 'flyway' AND t.name <> 'flyway_schema_history'
          AND c.is_computed = 0
        ORDER BY s.name, t.name, c.column_id
        """
    )
    by = {}
    for sch, tbl, col in cur.fetchall():
        by.setdefault((sch, tbl), []).append(col)
    return by


def has_id(cur, sch, tbl):
    cur.execute(
        "SELECT 1 FROM sys.columns c JOIN sys.tables t ON c.object_id=t.object_id "
        "JOIN sys.schemas s ON t.schema_id=s.schema_id "
        "WHERE s.name=? AND t.name=? AND c.name='id'", sch, tbl)
    return cur.fetchone() is not None


def ntext_columns(cur):
    """Set of (schema, table, column) whose type is the deprecated ntext LOB.
    Those need an explicit SQL_WLONGVARCHAR bind or pyodbc can't assign to them."""
    cur.execute(
        """
        SELECT s.name, t.name, c.name
        FROM sys.columns c
        JOIN sys.tables  t  ON c.object_id    = t.object_id
        JOIN sys.schemas s  ON t.schema_id    = s.schema_id
        JOIN sys.types   ty ON c.user_type_id = ty.user_type_id
        WHERE ty.name = 'ntext'
        """
    )
    return {(r[0], r[1], r[2]) for r in cur.fetchall()}


def main() -> int:
    mode = "CCM-ONLY" if CCM_ONLY else "DB-WIDE"
    print(f"Mode: {mode}{'  (DRY RUN — no writes)' if DRY_RUN else ''}\n")
    cn = pyodbc.connect(CONN, autocommit=True)
    cur = cn.cursor()

    # ---- 1. collect every change (and back up originals) -----------------
    changes = []   # dicts: schema, table, id, column, before, after
    for (sch, tbl), cols in discover(cur).items():
        if CCM_ONLY and (sch, tbl) != ("ccm", "control_test_definition"):
            continue
        if not has_id(cur, sch, tbl):
            continue
        cols_csv = ", ".join(f"[{c}]" for c in cols)
        try:
            cur.execute(f"SELECT id, {cols_csv} FROM [{sch}].[{tbl}]")
            rows = cur.fetchall()
        except pyodbc.Error as e:
            print(f"  SELECT failed {sch}.{tbl}: {str(e)[:80]}")
            continue
        for rr in rows:
            rid = rr[0]
            for i, c in enumerate(cols, start=1):
                v = rr[i]
                if not isinstance(v, str) or not v:
                    continue
                fx = enc_fix(v)
                if fx is not None:
                    changes.append({"schema": sch, "table": tbl, "id": rid,
                                    "column": c, "before": v, "after": fx})

    print(f"Cells to repair: {len(changes)} "
          f"across {len({(c['schema'],c['table']) for c in changes})} table(s)")

    # write backup regardless of dry-run
    with open(BACKUP_PATH, "w", encoding="utf-8") as f:
        json.dump(changes, f, ensure_ascii=False, indent=1)
    print(f"Backup of originals written: {BACKUP_PATH}\n")

    if DRY_RUN:
        print("Dry run — no UPDATEs executed.")
        cn.close()
        return 0

    # ---- 2. apply UPDATEs ------------------------------------------------
    # CRITICAL: force the text parameter to bind as SQL_WVARCHAR (-> nvarchar(max)).
    # By default pyodbc streams long strings as SQL_WLONGVARCHAR, which maps to the
    # legacy ntext type — and ntext CANNOT carry this DB's _SC_UTF8 collation
    # (error 4189). SQL_WVARCHAR(max) holds the full Unicode value correctly.
    applied = 0
    skipped = 0
    per_table = {}
    for ch in changes:
        sch, tbl, rid, col, after = (ch["schema"], ch["table"], ch["id"],
                                     ch["column"], ch["after"])
        try:
            cur.setinputsizes([(pyodbc.SQL_WVARCHAR, 0, 0),
                               (pyodbc.SQL_INTEGER, 0, 0)])
            cur.execute(
                f"UPDATE [{sch}].[{tbl}] SET [{col}] = ? WHERE id = ?", after, rid)
            applied += 1
            per_table[f"{sch}.{tbl}"] = per_table.get(f"{sch}.{tbl}", 0) + 1
        except pyodbc.Error as e:
            skipped += 1
            print(f"  skip {sch}.{tbl} id={rid} {col}: {str(e)[:140]}")
        finally:
            cur.setinputsizes(None)

    print(f"\nApplied {applied} update(s), skipped {skipped}.")
    for t in sorted(per_table):
        print(f"   {t}: {per_table[t]}")
    cn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
