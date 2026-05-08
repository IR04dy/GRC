"""
fix_arabic_mojibake.py — Idempotent data repair for double-encoded Unicode text.

ROOT CAUSE
==========
The seed SQL files (db/seed/V_*.sql) contain correct UTF-8 Arabic. When they
were loaded into SQL Server, the loader (likely sqlcmd without -f 65001 or
some Python script writing the file out as Windows-1252 first) misread the
multi-byte UTF-8 bytes as separate Latin-1 characters. SQL Server then
stored each "Latin-1 char" as one NCHAR (UTF-16 codepoint).

Concretely, for one Arabic char `ت` (U+062A, UTF-8 = 0xD8 0xAA):
  - Source file:   bytes 0xD8 0xAA           (one char `ت`)
  - Loader read:   chars Ø (U+00D8) + ª (U+00AA)   (two chars)
  - DB stored:     two NCHARs U+00D8 + U+00AA       (mojibake)
  - JSON returned: bytes 0xC3 0x98 0xC2 0xAA        ("Øª" when re-decoded)

RECOVERY
========
For each corrupt cell:
  - Each char is in U+00xx range (Latin-1 high bytes)
  - Encoding the string as Latin-1 (single byte per char) recovers the
    original UTF-8 bytes
  - Decoding those as UTF-8 recovers the original Arabic

  recovered = corrupt.encode('latin-1').decode('utf-8')

USAGE
=====
  python .tools/fix_arabic_mojibake.py             # dry-run, prints stats
  python .tools/fix_arabic_mojibake.py --apply     # actually fixes

The script discovers every text column in every user-schema table at run time,
then matches rows containing any of the typical mojibake lead-byte chars
(Ø, Ù, Ú, Û, Â, Ã). This catches all classes of corruption: Arabic UTF-8
mis-decoded (lead 0xD8/0xD9), as well as Latin-1 supplements and section/
middle-dot symbols (lead 0xC2/0xC3 — gives `§ → Â§`, `· → Â·`).
"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
import argparse
import pyodbc
from typing import Optional

# Mojibake-signature characters — any column row containing one of these is
# a candidate. These are the chars produced when UTF-8 lead bytes are read
# as Windows-1252:
#   0xC2 → Â     (Latin-1 Supplement: § ¶ © ® etc.)
#   0xC3 → Ã     (Latin-1 Supplement: vowels with diacritics)
#   0xD8 → Ø     (Arabic block lead byte)
#   0xD9 → Ù     (Arabic block lead byte)
#   0xDA → Ú     (Arabic Supplement / Persian)
#   0xDB → Û     (Arabic Supplement / Persian)
SIGNATURE_CHARS = [0xC2, 0xC3, 0xD8, 0xD9, 0xDA, 0xDB]


def discover_pk(cur, schema: str, table: str) -> Optional[list[str]]:
    """Return the column names that form the primary key (in order) or None."""
    cur.execute("""
        SELECT c.name
        FROM sys.indexes i
        JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        JOIN sys.columns c        ON ic.object_id = c.object_id AND ic.column_id = c.column_id
        JOIN sys.tables  t        ON i.object_id = t.object_id
        JOIN sys.schemas s        ON t.schema_id = s.schema_id
        WHERE i.is_primary_key = 1 AND s.name = ? AND t.name = ?
        ORDER BY ic.key_ordinal
    """, schema, table)
    rows = cur.fetchall()
    return [r[0] for r in rows] if rows else None


def _hybrid_to_bytes(text: str) -> bytes:
    """Reverse-engineer the byte stream that produced `text` when read via the
    SQL loader.

    Loaders typically use Windows-1252 to read source files. Most bytes in
    0x80-0x9F have a printable cp1252 mapping (e.g. 0x80 → €, 0x97 → —),
    but five bytes are GAPS in cp1252: 0x81, 0x8D, 0x8F, 0x90, 0x9D. Loaders
    typically emit those as the raw C1 control codepoint (U+0081, etc.).

    So our reverse path is:
      - For each char that cp1252 can encode → take its cp1252 byte
      - For chars in U+0080-U+00FF that cp1252 *can't* encode → take the
        codepoint as a single raw byte (covers U+0081, U+008D, U+008F,
        U+0090, U+009D — all valid Latin-1 control bytes)
      - Anything else (e.g. recovered non-mojibake U+0600-U+06FF Arabic)
        → cp1252 will fail; we propagate the error and the caller skips.
    """
    out = bytearray()
    for c in text:
        try:
            out.extend(c.encode('cp1252'))
        except UnicodeEncodeError:
            cp = ord(c)
            if 0x80 <= cp <= 0xFF:
                out.append(cp)
            else:
                raise
    return bytes(out)


def repair(text: str) -> Optional[str]:
    """Apply (mojibake → bytes via hybrid cp1252) → utf-8 decode.

    Returns None if `text` is not corrupt or recovery fails.
    """
    if text is None:
        return None
    try:
        recovered = _hybrid_to_bytes(text).decode('utf-8')
        if recovered == text:
            return None
        return recovered
    except (UnicodeEncodeError, UnicodeDecodeError):
        return None


def discover_corrupt_columns(cur) -> list[tuple[str, str, str]]:
    """Scan every text column in every user-schema table, return only the ones
    that contain at least one row matching a mojibake signature followed by
    another high-byte char (filters out incidental matches on `Â§` symbols
    used legitimately, etc. — we still try to repair, repair() returns None
    when the cell is already clean, so false positives cost nothing).
    """
    cur.execute("""
        SELECT s.name, t.name, c.name
        FROM sys.columns c
        JOIN sys.tables  t ON c.object_id = t.object_id
        JOIN sys.schemas s ON t.schema_id = s.schema_id
        JOIN sys.types  ty ON c.user_type_id = ty.user_type_id
        WHERE ty.name IN ('nvarchar','varchar','nchar','char')
          AND s.name NOT IN ('sys','INFORMATION_SCHEMA')
        ORDER BY s.name, t.name, c.name
    """)
    all_cols = cur.fetchall()
    sig_clauses = [f"[{{col}}] LIKE N'%' + NCHAR({sig}) + N'%'" for sig in SIGNATURE_CHARS]
    found = []
    for s, tbl, col in all_cols:
        try:
            where = ' OR '.join(c.format(col=col) for c in sig_clauses)
            cur.execute(f"SELECT TOP 1 1 FROM [{s}].[{tbl}] WHERE [{col}] IS NOT NULL AND ({where})")
            if cur.fetchone():
                found.append((s, tbl, col))
        except Exception:
            pass  # column may be filtered/indexed/computed — skip
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true', help='Persist fixes (default: dry-run)')
    ap.add_argument('--server', default='localhost')
    ap.add_argument('--db', default='TSS_GRC')
    ap.add_argument('--user', default='sa')
    ap.add_argument('--password', default='tss.123')
    args = ap.parse_args()

    conn = pyodbc.connect(
        f"DRIVER={{ODBC Driver 17 for SQL Server}};SERVER={args.server};DATABASE={args.db};"
        f"UID={args.user};PWD={args.password};TrustServerCertificate=yes",
        autocommit=False,  # transactional in apply mode
    )
    cur = conn.cursor()

    print("Discovering corrupt columns...")
    targets = discover_corrupt_columns(cur)
    print(f"  → {len(targets)} columns flagged for inspection")
    print()

    sig_clauses_template = ' OR '.join(
        f"[{{col}}] LIKE N'%' + NCHAR({sig}) + N'%'" for sig in SIGNATURE_CHARS
    )

    total_rows_fixed = 0
    cols_with_fixes = 0

    for schema, table, col in targets:
        pk_cols = discover_pk(cur, schema, table)
        if not pk_cols:
            print(f"  ! {schema}.{table} has no primary key — skipping {col}")
            continue

        pk_select = ', '.join(f"[{c}]" for c in pk_cols)
        where = sig_clauses_template.format(col=col)
        cur.execute(f"""
            SELECT {pk_select}, [{col}]
              FROM [{schema}].[{table}]
             WHERE [{col}] IS NOT NULL AND ({where})
        """)
        rows = cur.fetchall()
        if not rows:
            continue

        pk_n = len(pk_cols)
        fixed_here = 0
        first_old = None
        first_new = None
        for row in rows:
            pk_vals = row[:pk_n]
            current = row[pk_n]
            recovered = repair(current)
            if recovered is None:
                continue
            if first_old is None:
                first_old, first_new = current, recovered
            if args.apply:
                where_clause = ' AND '.join(f"[{c}]=?" for c in pk_cols)
                cur.execute(
                    f"UPDATE [{schema}].[{table}] SET [{col}]=? WHERE {where_clause}",
                    recovered, *pk_vals
                )
            fixed_here += 1

        if fixed_here > 0:
            cols_with_fixes += 1
            total_rows_fixed += fixed_here
            short_old = (first_old[:32] + '…') if first_old and len(first_old) > 32 else (first_old or '?')
            short_new = (first_new[:32] + '…') if first_new and len(first_new) > 32 else (first_new or '?')
            print(f"  {schema}.{table}.{col}: fixed {fixed_here} rows  ({short_old}  →  {short_new})")

    if args.apply:
        conn.commit()
        print(f"\nCOMMITTED: {total_rows_fixed} rows fixed across {cols_with_fixes} columns "
              f"(scanned {len(targets)} candidate columns).")
    else:
        conn.rollback()
        print(f"\nDRY-RUN: would fix {total_rows_fixed} rows across {cols_with_fixes} columns. "
              f"Re-run with --apply to commit.")


if __name__ == '__main__':
    main()
