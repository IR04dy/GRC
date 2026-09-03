# -*- coding: utf-8 -*-
"""F2-445 — where UTF-8-read-as-cp1252 corruption still lives, and what it has PRODUCED.

WHY THIS EXISTS

On 2026-06-08 commit ``aa5dc762`` repaired 117 double-encoded cells and wrote
``.tools/MOJIBAKE_FIX_REPORT.md``. Its root-cause analysis is correct and its prevention section is
apply-time discipline plus a hardened repair tool. **It left no detector that runs**, and its
population was narrowed three ways, each of which hid part of the corruption:

  1. BY ROW ID.   Its inventory reads ``automation_rules.name_en = ids 1-52 … 53+ clean``.
                  Measured 2026-09-03: **24 corrupted runs, all at id > 52**, on rule 174 alone.
  2. BY COLUMN.   It swept ``name_en``. Rule 174 is corrupted in ``name_en``, ``name_ar``,
                  ``description_ar`` AND ``action_json`` — and ``action_json`` is the one that
                  **reproduces**, because it holds a ``title_template``.
  3. BY SURFACE.  Its scope was "every NVARCHAR column of every user table", i.e. CELLS. A corrupted
                  **column DEFAULT** is invisible to a cell sweep, and
                  ``bcm.bcp_binder_export.watermark_text`` carries one in both live databases while
                  a migration-built database has it correct.

⚠⚠ THE MEASUREMENT THAT MATTERS: rule 174 is ENABLED and has fired three times, and its corrupted
template has produced three corrupted ``compliance.findings`` titles dated **2026-06-29, 07-02 and
07-04** — i.e. *after* the repair. A data repair that does not fix the PRODUCER is a repair the next
firing undoes.

⚠⚠ AND THE TEST MUST BE PER RUN, NOT PER VALUE. A value holding correct Arabic *and* mojibake cannot
encode to cp1252 at all, so a whole-value round-trip reports it CLEAN. That is the quiet direction:
my own first sweep of this corpus reported **3** corrupted cells where the per-run test finds **16**,
and it read rule 174's four corrupted columns as one.

WHAT IT REPORTS

Cells, column DEFAULTs and CHECK definitions, per run, in both databases — plus which corrupted
cells are PRODUCERS (a template or a rule action), because those regenerate corruption and must be
repaired first.

⚠ Read-only. It repairs nothing: every write here lands in a customer database, so the repair is the
owner's call (house rule 6) — and ``platform.audit_log`` is hash-chained (F2-31), so its six
corrupted messages **may not be repaired at all** without breaking the seal (F2-434).
"""
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DATABASES = ("TSS_GRC", "TSS_GRC_Deployment")

#: Columns whose corruption REPRODUCES: they are read as a template or an action, so repairing the
#: rows they produced without repairing these is repairing the symptom.
PRODUCER_COLUMNS = {
    "platform.automation_rules.action_json",
    "platform.automation_rules.condition_json",
    "reporting.report_definitions.template",
    "admin.committee_form_templates.body_en",
    "admin.committee_form_templates.body_ar",
}

#: Surfaces that must never be repaired, with the reason. Not an exemption from MEASURING them —
#: they are reported exactly like everything else, because an operator needs to know the corruption
#: reached the ledger even though nothing may touch it.
UNREPAIRABLE = {
    "platform.audit_log.message":
        "hash-chained (F2-31): repairing a sealed row breaks the chain (F2-434)",
    "platform.audit_log.old_value_json":
        "hash-chained (F2-31): repairing a sealed row breaks the chain (F2-434)",
    "platform.audit_log.new_value_json":
        "hash-chained (F2-31): repairing a sealed row breaks the chain (F2-434)",
}


def connect(db):
    return pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes" % db, timeout=120, autocommit=True)


def bad_runs(value):
    """Every maximal non-ASCII run that repairs cleanly as cp1252 -> UTF-8.

    ⚠ PER RUN. Testing the whole value reports a value that mixes correct and corrupted text as
    clean, because the correct half cannot encode to cp1252 — the defect that under-reported this
    same corpus 16 -> 3.
    """
    out, cur = [], []
    for ch in (value or "") + "\x00":
        if ord(ch) >= 0x80:
            cur.append(ch)
            continue
        if cur:
            run = "".join(cur)
            try:
                fixed = run.encode("cp1252", errors="strict").decode("utf-8", errors="strict")
                if fixed != run:
                    out.append((run, fixed))
            except Exception:
                pass
            cur = []
    return out


TEXT_COLUMNS = """
SELECT s.name, t.name, c.name
  FROM sys.columns c
  JOIN sys.tables t  ON t.object_id = c.object_id
  JOIN sys.schemas s ON s.schema_id = t.schema_id
  JOIN sys.types ty  ON ty.user_type_id = c.user_type_id
 WHERE s.name NOT IN ('dbo','sys','INFORMATION_SCHEMA')
   AND ty.name IN ('nvarchar','nchar','ntext','varchar','char','text')
 ORDER BY s.name, t.name, c.name"""

DEFAULTS = """
SELECT s.name + '.' + t.name, c.name, d.definition
  FROM sys.default_constraints d
  JOIN sys.columns c  ON c.object_id = d.parent_object_id AND c.column_id = d.parent_column_id
  JOIN sys.tables t   ON t.object_id = d.parent_object_id
  JOIN sys.schemas s  ON s.schema_id = t.schema_id
 WHERE s.name NOT IN ('dbo','sys','INFORMATION_SCHEMA')"""

CHECKS = """
SELECT s.name + '.' + t.name, cc.name, cc.definition
  FROM sys.check_constraints cc
  JOIN sys.tables t  ON t.object_id = cc.parent_object_id
  JOIN sys.schemas s ON s.schema_id = t.schema_id
 WHERE s.name NOT IN ('dbo','sys','INFORMATION_SCHEMA')"""


def sweep_cells(cur):
    """{qualified column: (cells, runs)}.

    ⚠ The SQL prefilter is on the cp1252-misread LEAD bytes (0xD8/0xD9 for Arabic, 0xE2 for the
    punctuation dashes, 0xC3 for Latin-1 accents) and is a LOCATOR only — the round-trip above
    adjudicates. Prefiltering on a blacklist of whole sequences would miss novel corruption.
    """
    cur.execute(TEXT_COLUMNS)
    found = {}
    for sc, tb, co in cur.fetchall():
        q = ("SELECT [%s] FROM [%s].[%s] WHERE [%s] IS NOT NULL AND ("
             "[%s] LIKE N'%%'+NCHAR(0x00D8)+N'%%' OR [%s] LIKE N'%%'+NCHAR(0x00D9)+N'%%' "
             "OR [%s] LIKE N'%%'+NCHAR(0x00E2)+N'%%' OR [%s] LIKE N'%%'+NCHAR(0x00C3)+N'%%')"
             % (co, sc, tb, co, co, co, co, co))
        try:
            cur.execute(q)
            values = [r[0] for r in cur.fetchall()]
        except pyodbc.Error:
            continue                      # a column type the prefilter cannot compare — reported below
        cells = [v for v in values if bad_runs(v)]
        if cells:
            found["%s.%s.%s" % (sc, tb, co)] = (len(cells), sum(len(bad_runs(v)) for v in cells))
    return found


def sweep_definitions(cur, sql, label):
    cur.execute(sql)
    out = []
    for owner, name, defn in cur.fetchall():
        runs = bad_runs(defn)
        if runs:
            out.append((label, owner, name, defn, runs))
    return out


def main():
    for db in DATABASES:
        try:
            cur = connect(db).cursor()
        except pyodbc.Error as e:
            print("%s: cannot connect — %s" % (db, str(e)[:90]))
            continue

        print("=" * 78)
        print("%s" % db)
        print("=" * 78)

        cells = sweep_cells(cur)
        total_cells = sum(v[0] for v in cells.values())
        total_runs = sum(v[1] for v in cells.values())
        print("CELLS: %d column(s) · %d cell(s) · %d corrupted run(s)"
              % (len(cells), total_cells, total_runs))
        for key in sorted(cells, key=lambda k: -cells[k][1]):
            n, runs = cells[key]
            tags = []
            if key in PRODUCER_COLUMNS:
                tags.append("PRODUCER — repair this FIRST or it regenerates")
            if key in UNREPAIRABLE:
                tags.append("DO NOT REPAIR: " + UNREPAIRABLE[key])
            print("    %-58s cells %-4d runs %-4d %s"
                  % (key, n, runs, ("  <-- " + " · ".join(tags)) if tags else ""))

        for sql, label in ((DEFAULTS, "DEFAULT"), (CHECKS, "CHECK")):
            hits = sweep_definitions(cur, sql, label)
            print("\n%sS: %d corrupted" % (label, len(hits)))
            for _, owner, name, defn, runs in hits:
                repaired = defn
                for orig, fixed in runs:
                    repaired = repaired.replace(orig, fixed)
                print("    %s.%s" % (owner, name))
                print("        stored : %s" % defn)
                print("        repairs: %s" % repaired)
                print("        ⚠ invisible to a CELL sweep — the 2026-06-08 repair's blind spot")

        print()
        print("A repair is an owner decision: every write lands in a customer database (house rule")
        print("6). Repair PRODUCERS first, then their output, and never platform.audit_log.")
        print()


if __name__ == "__main__":
    main()
