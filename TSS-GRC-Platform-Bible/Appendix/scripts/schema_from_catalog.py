"""F2-70 / F2-72 — generate runnable DDL for every mapped table the migrations never create.

WHY A GENERATOR AND NOT 67 HAND-WRITTEN MIGRATIONS
--------------------------------------------------
V338 tried the obvious thing: re-issue the existing DDL for four tables by copying V12-V14
verbatim. Hibernate rejected the result on the first call with `Invalid column name 'deleted_by'`
— those files predate today's BaseEntity by three months, so **re-issuing a stale migration
reproduces a stale schema**. Doing that 67 more times is 67 chances to ship the same defect.

This script reads `sys.schemas` / `sys.columns` / `sys.indexes` / `sys.key_constraints` /
`sys.check_constraints` / `sys.foreign_keys` from the LIVE dev database, which is the
authoritative schema (production is a restore of it, 2026-07-11). A generator that reads the
catalog **cannot be stale**.

Hibernate's own `ddl-auto` generation was considered and rejected as the WEAKER generator: it
emits what the entities declare, which would silently drop the DB-side CHECK constraints,
filtered indexes and column defaults the Python bootstrap added and the entities do not mention.

WHAT IT EMITS
-------------
Two migrations, because foreign keys must come after every table exists:

  V<n>__spine_bootstrap_schema_tables.sql   CREATE SCHEMA x11, CREATE TABLE x67 with columns,
                                            identity, defaults, PK, UNIQUE, CHECK, and indexes
  V<n+1>__spine_bootstrap_foreign_keys.sql  every FK on those tables

Both idempotent (IF NOT EXISTS guards) so dev can apply them by hand twice, per house rule.

  py TSS-GRC-Platform-Bible/Appendix/scripts/schema_from_catalog.py --emit 341

WHAT IT ALSO DOES — F2-72 falls out nearly free
-----------------------------------------------
`--compare <db>` renders the same catalog from a second database and diffs it column by column
against dev. That is the standing column-drift instrument F2-72 asks for, and it exists because
the generator already had to model columns exactly. Used two ways: against the empty scratch
database to prove the generated DDL round-trips, and against prod to watch the real exposure
(dev applies V293+ by hand via pyodbc with hand-split GO batches; prod applies the same files
through Flyway, so the two can diverge column-by-column with nothing looking).
"""
import argparse
import csv
import io
import os
import re
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
MIG_DIR = os.path.join(REPO, "tss-grc-backend", "src", "main", "resources", "db", "migration",
                       "sqlserver")
REGISTER = os.path.join(HERE, "..", "TABLE_DISPOSITION_REGISTER.csv")

DEV = "TSS_GRC"
CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?\[?(\w+)\]?\s*\.\s*\[?(\w+)\]?", re.I)
CREATE_SCHEMA = re.compile(r"CREATE\s+SCHEMA\s+\[?(\w+)\]?", re.I)


def connect(db):
    return pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes" % db, autocommit=True)


# ── what the migrations already build ────────────────────────────────────────────────────

def migration_coverage():
    tables, schemas = set(), set()
    for f in sorted(os.listdir(MIG_DIR)):
        if not f.endswith(".sql"):
            continue
        body = io.open(os.path.join(MIG_DIR, f), encoding="utf-8", errors="replace").read()
        for m in CREATE_TABLE.finditer(body):
            tables.add((m.group(1).lower(), m.group(2).lower()))
        for m in CREATE_SCHEMA.finditer(body):
            schemas.add(m.group(1).lower())
    return tables, schemas


def jpa_mapped():
    """From the committed register — `jpa_files > 0`.

    ⚠ The register's `migrations` column is NOT usable for the other half of this question: it
    counts how many migrations MENTION a table, not how many create it. `compliance.frameworks`
    reads 4 while being created by none. So creation comes from parsing CREATE TABLE (above) and
    only the mapped-ness comes from here.
    """
    out = set()
    with io.open(REGISTER, encoding="utf-8-sig") as fh:
        for r in csv.DictReader(fh):
            try:
                n = int((r["jpa_files"] or "0").strip())
            except ValueError:
                n = 0
            if n > 0:
                out.add((r["schema"].lower(), r["table"].lower()))
    return out


# ── catalog rendering ───────────────────────────────────────────────────────────────────

def live_tables(cur):
    cur.execute("""SELECT s.name, t.name FROM sys.tables t
                   JOIN sys.schemas s ON s.schema_id = t.schema_id
                   WHERE t.is_ms_shipped = 0 ORDER BY s.name, t.name""")
    return [(r[0].lower(), r[1].lower()) for r in cur.fetchall()]


def render_type(row):
    t = row.type_name.lower()
    n = row.max_length
    if t in ("nvarchar", "nchar"):
        return "%s(%s)" % (t.upper(), "MAX" if n == -1 else n // 2)
    if t in ("varchar", "char", "varbinary", "binary"):
        return "%s(%s)" % (t.upper(), "MAX" if n == -1 else n)
    if t in ("decimal", "numeric"):
        return "%s(%d,%d)" % (t.upper(), row.precision, row.scale)
    if t in ("datetime2", "time", "datetimeoffset"):
        return "%s(%d)" % (t.upper(), row.scale)
    if t == "float":
        return "FLOAT(%d)" % row.precision
    return t.upper()


def columns(cur, schema, table):
    cur.execute("""
        SELECT c.name AS col_name, ty.name AS type_name, c.max_length, c.precision, c.scale,
               c.is_nullable, c.is_identity, c.is_computed,
               -- sql_variant: pyodbc raises "ODBC SQL type -25 is not yet supported" on it
               CAST(ic.seed_value AS BIGINT) AS seed_value,
               CAST(ic.increment_value AS BIGINT) AS increment_value,
               dc.definition AS default_def, cc.definition AS computed_def, c.column_id
          FROM sys.columns c
          JOIN sys.types ty ON ty.user_type_id = c.user_type_id
          LEFT JOIN sys.identity_columns ic ON ic.object_id = c.object_id
                                           AND ic.column_id = c.column_id
          LEFT JOIN sys.default_constraints dc ON dc.parent_object_id = c.object_id
                                              AND dc.parent_column_id = c.column_id
          LEFT JOIN sys.computed_columns cc ON cc.object_id = c.object_id
                                            AND cc.column_id = c.column_id
         WHERE c.object_id = OBJECT_ID(?)
         ORDER BY c.column_id""", "%s.%s" % (schema, table))
    return cur.fetchall()


def key_constraints(cur, schema, table):
    cur.execute("""
        SELECT kc.name, kc.type, i.is_unique,
               STUFF((SELECT ', [' + col.name + ']'
                        FROM sys.index_columns xic
                        JOIN sys.columns col ON col.object_id = xic.object_id
                                            AND col.column_id = xic.column_id
                       WHERE xic.object_id = i.object_id AND xic.index_id = i.index_id
                         AND xic.is_included_column = 0
                       ORDER BY xic.key_ordinal FOR XML PATH('')), 1, 2, '') AS cols
          FROM sys.key_constraints kc
          JOIN sys.indexes i ON i.object_id = kc.parent_object_id
                            AND i.index_id = kc.unique_index_id
         WHERE kc.parent_object_id = OBJECT_ID(?)
         ORDER BY kc.type DESC, kc.name""", "%s.%s" % (schema, table))
    return cur.fetchall()


def check_constraints(cur, schema, table):
    cur.execute("""SELECT name, definition FROM sys.check_constraints
                    WHERE parent_object_id = OBJECT_ID(?) ORDER BY name""",
                "%s.%s" % (schema, table))
    return cur.fetchall()


def indexes(cur, schema, table):
    cur.execute("""
        SELECT i.name, i.is_unique, i.filter_definition,
               STUFF((SELECT ', [' + col.name + ']'
                        FROM sys.index_columns xic
                        JOIN sys.columns col ON col.object_id = xic.object_id
                                            AND col.column_id = xic.column_id
                       WHERE xic.object_id = i.object_id AND xic.index_id = i.index_id
                         AND xic.is_included_column = 0
                       ORDER BY xic.key_ordinal FOR XML PATH('')), 1, 2, '') AS keycols,
               STUFF((SELECT ', [' + col.name + ']'
                        FROM sys.index_columns xic
                        JOIN sys.columns col ON col.object_id = xic.object_id
                                            AND col.column_id = xic.column_id
                       WHERE xic.object_id = i.object_id AND xic.index_id = i.index_id
                         AND xic.is_included_column = 1
                       ORDER BY xic.index_column_id FOR XML PATH('')), 1, 2, '') AS inccols
          FROM sys.indexes i
         WHERE i.object_id = OBJECT_ID(?) AND i.is_primary_key = 0
           AND i.is_unique_constraint = 0 AND i.type_desc <> 'HEAP' AND i.name IS NOT NULL
         ORDER BY i.name""", "%s.%s" % (schema, table))
    return cur.fetchall()


def foreign_keys_touching(cur, targets):
    """Every FK with a spine table on EITHER side, collected once for the whole set.

    ⚠ Both directions, and the outbound half was nearly missed. Measured on dev: 7 FKs point OUT
    of the 67 and **15 point IN**, of which **9 exist only because the Python bootstrap made them**
    — `risk.risks`, `ethics.aml_alerts/cases/disclosures/kyc_subjects`, `privacy.dpia`,
    `compliance.controls/findings` → `compliance.frameworks`. Their parent tables ARE created by
    migrations, so a migration-built database would hold those tables and silently lack the
    referential integrity dev has. Emitting only the outbound half would have produced a schema
    that passes a table-level check and is still not the schema this platform runs on.

    Verified separately: **no pre-existing migration contains a REFERENCES clause naming any of the
    67**, so there is no ordering trap — nothing earlier needs these tables to exist.
    """
    tset = set(targets)
    seen, out = set(), []
    for schema, table in targets:
        for side in ("parent_object_id", "referenced_object_id"):
            for fk in _fks_by(cur, side, schema, table):
                if fk.name in seen:
                    continue
                # Keep only FKs whose OTHER side is a real table (it always is), and record once.
                seen.add(fk.name)
                out.append(fk)
    out.sort(key=lambda f: (f.parent_schema, f.parent_table, f.name))
    return out, tset


def _fks_by(cur, side, schema, table):
    cur.execute("""
        SELECT fk.name,
               ps.name AS parent_schema, pt.name AS parent_table,
               STUFF((SELECT ', [' + pc.name + ']'
                        FROM sys.foreign_key_columns c
                        JOIN sys.columns pc ON pc.object_id = c.parent_object_id
                                           AND pc.column_id = c.parent_column_id
                       WHERE c.constraint_object_id = fk.object_id
                       ORDER BY c.constraint_column_id FOR XML PATH('')), 1, 2, '') AS cols,
               rs.name AS ref_schema, rt.name AS ref_table,
               STUFF((SELECT ', [' + rc.name + ']'
                        FROM sys.foreign_key_columns c
                        JOIN sys.columns rc ON rc.object_id = c.referenced_object_id
                                           AND rc.column_id = c.referenced_column_id
                       WHERE c.constraint_object_id = fk.object_id
                       ORDER BY c.constraint_column_id FOR XML PATH('')), 1, 2, '') AS refcols,
               fk.delete_referential_action_desc, fk.update_referential_action_desc
          FROM sys.foreign_keys fk
          JOIN sys.tables pt ON pt.object_id = fk.parent_object_id
          JOIN sys.schemas ps ON ps.schema_id = pt.schema_id
          JOIN sys.tables rt ON rt.object_id = fk.referenced_object_id
          JOIN sys.schemas rs ON rs.schema_id = rt.schema_id
         WHERE fk.""" + side + """ = OBJECT_ID(?)
         ORDER BY fk.name""", "%s.%s" % (schema, table))
    return cur.fetchall()


# ── emit ────────────────────────────────────────────────────────────────────────────────

HDR_TABLES = """\
-- V{v}: F2-70 — the {nt} JPA-mapped tables and {ns} schemas that NO migration creates.
--
-- GENERATED. Do not hand-edit; re-run the generator:
--   py TSS-GRC-Platform-Bible/Appendix/scripts/schema_from_catalog.py --emit {v}
--
-- These tables exist in dev because the Python bootstrap (setup_ai_lifecycle_db.py,
-- setup_cpty_db.py, setup_cyber_db.py, ...) made them, and in production only because production
-- was RESTORED from a dev dump on 2026-07-11. So `flyway:migrate` against an empty database could
-- not build this platform: a genuinely new customer — the first not cloned from one laptop —
-- would be missing every table below, and every surface over them would answer 503
-- db-object-missing. That is F2-64's failure mode applied to the whole schema instead of one
-- capability.
--
-- ⚠ Read from `sys.columns`/`sys.indexes`/`sys.check_constraints`, NOT copied from the bootstrap
-- scripts and NOT generated by Hibernate. V338 copied V12-V14 verbatim and Hibernate rejected the
-- result (`Invalid column name 'deleted_by'`) because those files were three months stale — a
-- catalog read cannot be stale. Hibernate's own ddl-auto was the weaker option: it would have
-- dropped the DB-side CHECKs, filtered indexes and defaults the entities never declare.
--
-- ⚠ The {ns} schema-creation statements below are not optional padding. Driving the table ratchet
-- to zero without them still fails on the first table in a schema that does not exist.
--
-- ⚠ And the wording of that sentence matters: the first draft said "the {ns} CREATE·SCHEMA
-- statements", which SchemaContractRatchetTest's own regex read as a schema literally named
-- `statements` — this file then declared 12 schemas instead of {ns}. Prose inside a migration is
-- parsed by the scanners that read migrations. Do not write DDL keywords in a comment here.
--
-- Idempotent: safe to run twice (dev applies by hand, Flyway off).
"""

HDR_FKS = """\
-- V{v}: F2-70 — foreign keys for the tables created by V{prev}.
--
-- GENERATED. Do not hand-edit; re-run the generator (see V{prev}).
--
-- Separate from the CREATE TABLEs so declaration order never matters: every table on both sides
-- of every constraint below exists by the time this file runs, including the ones that reference
-- each other. Emitting FKs inline would make this migration order-sensitive for no benefit.
--
-- Idempotent: safe to run twice.
"""


def emit(version):
    mig_tables, mig_schemas = migration_coverage()
    mapped = jpa_mapped()
    cn = connect(DEV)
    cur = cn.cursor()

    live = live_tables(cur)
    targets = [t for t in live if t not in mig_tables and t in mapped]
    skipped = [t for t in live if t not in mig_tables and t not in mapped]

    cur.execute("""SELECT name FROM sys.schemas WHERE principal_id = 1
                    AND name NOT IN ('dbo','guest','sys','INFORMATION_SCHEMA')""")
    live_schemas = sorted(r[0].lower() for r in cur.fetchall())

    # The ratchet's number: schemas holding a mapped table that NO migration declares.
    uncovered_schemas = sorted(s for s in live_schemas if s not in mig_schemas)

    # What this file actually emits: EVERY schema its own tables live in — 14, not 11.
    #
    # ⚠ Found by the empty-database proof on its first run, and the ratchet could never have seen
    # it. Emitting only the 11 uncovered schemas left this migration silently depending on three
    # EARLIER files to have created `ai_governance`, `compliance` and `evidence`. On a database
    # holding only this pair the very first CREATE TABLE failed: *"The specified schema name
    # 'ai_governance' either does not exist"*. Correct in a full chain, fragile out of it — and
    # relying on an earlier file for a prerequisite is the same ordering fragility that made a
    # column-existence guard in V47 pass on an empty database and then fail its ALTER.
    # The guards are IF NOT EXISTS, so declaring all 14 is a no-op where three already ran.
    target_schemas = sorted({sch for sch, _ in targets})

    print("migrations create %d tables / %d schemas" % (len(mig_tables), len(mig_schemas)))
    print("dev has %d tables / %d schemas" % (len(live), len(live_schemas)))
    print("TARGETS: %d tables; %d schemas declared here (%d of them covered by NO migration: %s)"
          % (len(targets), len(target_schemas), len(uncovered_schemas),
             ", ".join(uncovered_schemas)))
    print("skipped (created by no migration but NOT JPA-mapped): %d -> %s"
          % (len(skipped), ", ".join("%s.%s" % t for t in skipped)))

    body = [HDR_TABLES.format(v=version, nt=len(targets), ns=len(target_schemas)), ""]

    body.append("-- " + "-" * 90)
    body.append("-- Schemas")
    body.append("-- " + "-" * 90)
    for s in target_schemas:
        body.append("IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = '%s')" % s)
        body.append("    EXEC('CREATE SCHEMA [%s]');" % s)
    body.append("GO")
    body.append("")

    fk_body = [HDR_FKS.format(v=version + 1, prev=version), ""]
    n_idx = n_chk = n_fk = 0

    for schema, table in targets:
        cols = columns(cur, schema, table)
        assert cols, "%s.%s has no columns — catalog read failed" % (schema, table)
        body.append("-- " + "-" * 90)
        body.append("-- %s.%s" % (schema, table))
        body.append("-- " + "-" * 90)
        body.append("IF OBJECT_ID('%s.%s', 'U') IS NULL" % (schema, table))
        body.append("BEGIN")
        body.append("    CREATE TABLE [%s].[%s] (" % (schema, table))
        lines = []
        for c in cols:
            if c.is_computed:
                lines.append("        [%s] AS %s" % (c.col_name, c.computed_def))
                continue
            piece = "        [%s] %s" % (c.col_name, render_type(c))
            if c.is_identity:
                piece += " IDENTITY(%d,%d)" % (c.seed_value or 1, c.increment_value or 1)
            piece += " NOT NULL" if not c.is_nullable else " NULL"
            if c.default_def:
                piece += " DEFAULT %s" % c.default_def
            lines.append(piece)
        for kc in key_constraints(cur, schema, table):
            kind = "PRIMARY KEY" if kc.type.strip() == "PK" else "UNIQUE"
            lines.append("        CONSTRAINT [%s] %s (%s)" % (kc.name, kind, kc.cols))
        for ck in check_constraints(cur, schema, table):
            lines.append("        CONSTRAINT [%s] CHECK %s" % (ck.name, ck.definition))
            n_chk += 1
        body.append(",\n".join(lines))
        body.append("    );")
        body.append("END")
        body.append("GO")

        for ix in indexes(cur, schema, table):
            n_idx += 1
            body.append("IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = '%s' "
                        "AND object_id = OBJECT_ID('%s.%s'))" % (ix.name, schema, table))
            stmt = "    CREATE %sINDEX [%s] ON [%s].[%s] (%s)" % (
                "UNIQUE " if ix.is_unique else "", ix.name, schema, table, ix.keycols)
            if ix.inccols:
                stmt += " INCLUDE (%s)" % ix.inccols
            if ix.filter_definition:
                stmt += " WHERE %s" % ix.filter_definition
            body.append(stmt + ";")
            body.append("GO")
        body.append("")


    fks, _ = foreign_keys_touching(cur, targets)
    for fk in fks:
        n_fk += 1
        inbound = (fk.parent_schema.lower(), fk.parent_table.lower()) not in set(targets)
        fk_body.append("-- %s.%s -> %s.%s%s" % (
            fk.parent_schema, fk.parent_table, fk.ref_schema, fk.ref_table,
            "   [INBOUND: parent is created by an existing migration, this FK existed only in "
            "the bootstrap]" if inbound else ""))
        # Guarded on BOTH endpoints existing, not just on the constraint's absence. The inbound
        # half's parents are created by EARLIER migrations, so a database holding only this pair of
        # files (the empty-database proof) does not have them — and a migration must not fail on a
        # database it may legitimately meet. Same rule V339 taught one file earlier.
        fk_body.append("IF OBJECT_ID('%s.%s', 'U') IS NOT NULL AND OBJECT_ID('%s.%s', 'U') IS NOT NULL"
                       % (fk.parent_schema, fk.parent_table, fk.ref_schema, fk.ref_table))
        fk_body.append("   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = '%s' "
                       "AND parent_object_id = OBJECT_ID('%s.%s'))"
                       % (fk.name, fk.parent_schema, fk.parent_table))
        act = ""
        if fk.delete_referential_action_desc != "NO_ACTION":
            act += " ON DELETE %s" % fk.delete_referential_action_desc.replace("_", " ")
        if fk.update_referential_action_desc != "NO_ACTION":
            act += " ON UPDATE %s" % fk.update_referential_action_desc.replace("_", " ")
        fk_body.append("    ALTER TABLE [%s].[%s] ADD CONSTRAINT [%s] FOREIGN KEY (%s) "
                       "REFERENCES [%s].[%s] (%s)%s;"
                       % (fk.parent_schema, fk.parent_table, fk.name, fk.cols,
                          fk.ref_schema, fk.ref_table, fk.refcols, act))
        fk_body.append("GO")

    # Verification blocks — THROW, not print, and shape-only so an empty database is fine.
    body.append("-- " + "-" * 90)
    body.append("-- Verification: THROW rather than print, so a silent failure cannot pass for")
    body.append("-- success. Shape only (V339's lesson): a migration may assert only what is true")
    body.append("-- of every database it will ever meet, including an empty one.")
    body.append("-- " + "-" * 90)
    body.append("DECLARE @missing NVARCHAR(MAX) = (")
    body.append("    SELECT STRING_AGG(x.n, ', ') FROM (VALUES")
    body.append(",\n".join("        ('%s.%s')" % t for t in targets))
    body.append("    ) AS x(n) WHERE OBJECT_ID(x.n, 'U') IS NULL);")
    body.append("IF @missing IS NOT NULL")
    body.append("    THROW %d, 'V%d: tables missing after this migration', 1;"
                % (51000 + version, version))
    body.append("")
    body.append("DECLARE @noschema NVARCHAR(MAX) = (")
    body.append("    SELECT STRING_AGG(x.n, ', ') FROM (VALUES")
    body.append(",\n".join("        ('%s')" % s for s in target_schemas))
    body.append("    ) AS x(n) WHERE SCHEMA_ID(x.n) IS NULL);")
    body.append("IF @noschema IS NOT NULL")
    body.append("    THROW %d, 'V%d: schemas missing after this migration', 1;"
                % (51000 + version, version))
    body.append("GO")

    fk_body.append("-- Verification: every FK whose BOTH endpoints exist must now be present.")
    fk_body.append("-- Counted by NAME, because the parents are no longer all spine tables and")
    fk_body.append("-- counting by parent table would silently miss the inbound half. Conditioned")
    fk_body.append("-- on endpoint existence, because on a database built from this pair of files")
    fk_body.append("-- alone the inbound parents legitimately do not exist yet — asserting a flat")
    fk_body.append("-- count would fail the very empty-database build this migration exists for.")
    fk_body.append("DECLARE @expected INT = (SELECT COUNT(*) FROM (VALUES")
    fk_body.append(",\n".join(
        "        ('%s', '%s.%s', '%s.%s')" % (f.name, f.parent_schema, f.parent_table,
                                             f.ref_schema, f.ref_table) for f in fks))
    fk_body.append("    ) AS x(fk, parent, ref)")
    fk_body.append("    WHERE OBJECT_ID(x.parent, 'U') IS NOT NULL")
    fk_body.append("      AND OBJECT_ID(x.ref, 'U') IS NOT NULL);")
    fk_body.append("DECLARE @actual INT = (SELECT COUNT(*) FROM sys.foreign_keys")
    fk_body.append("    WHERE name IN (%s));" % ", ".join("'%s'" % f.name for f in fks))
    fk_body.append("IF @actual < @expected")
    fk_body.append("    THROW %d, 'V%d: a spine foreign key is missing though both its tables exist', 1;"
                   % (51000 + version + 1, version + 1))
    fk_body.append("GO")

    f1 = os.path.join(MIG_DIR, "V%d__spine_bootstrap_schema_tables.sql" % version)
    f2 = os.path.join(MIG_DIR, "V%d__spine_bootstrap_foreign_keys.sql" % (version + 1))
    io.open(f1, "w", encoding="utf-8", newline="\r\n").write("\n".join(body) + "\n")
    io.open(f2, "w", encoding="utf-8", newline="\r\n").write("\n".join(fk_body) + "\n")
    print("\nwrote %s  (%d tables, %d indexes, %d checks)"
          % (os.path.basename(f1), len(targets), n_idx, n_chk))
    print("wrote %s  (%d foreign keys)" % (os.path.basename(f2), n_fk))
    return targets, target_schemas


# ── F2-72: column-level comparison ──────────────────────────────────────────────────────

def snapshot(cur, tables):
    """schema.table -> {column: (type, nullable, identity, default)} — the comparable shape."""
    out = {}
    for schema, table in tables:
        cols = {}
        for c in columns(cur, schema, table):
            cols[c.col_name.lower()] = (render_type(c), bool(c.is_nullable), bool(c.is_identity),
                                        (c.default_def or "").replace(" ", ""))
        out["%s.%s" % (schema, table)] = cols
    return out


def compare(other_db, tables):
    a = snapshot(connect(DEV).cursor(), tables)
    b = snapshot(connect(other_db).cursor(), tables)
    problems = 0
    for t in sorted(a):
        ca, cb = a[t], b.get(t)
        if cb is None or not cb:
            print("  MISSING TABLE  %s" % t)
            problems += 1
            continue
        for col in sorted(ca):
            if col not in cb:
                print("  MISSING COLUMN %s.%s" % (t, col))
                problems += 1
            elif ca[col] != cb[col]:
                print("  DRIFT          %s.%s  dev=%s  %s=%s" % (t, col, ca[col], other_db, cb[col]))
                problems += 1
        for col in sorted(cb):
            if col not in ca:
                print("  EXTRA COLUMN   %s.%s (only in %s)" % (t, col, other_db))
                problems += 1
    checked = sum(len(v) for v in a.values())
    print("\ncompared %d tables / %d columns against %s: %d problem(s)"
          % (len(a), checked, other_db, problems))
    # Anti-vacuity: a comparison of nothing is not a clean comparison.
    if checked == 0:
        print("REFUSING to report clean — zero columns were compared")
        return 1
    return problems


def spine_set():
    """The tables the spine covers.

    ⚠ Two sources, in this order, and the order is the point. Once the spine migration exists it
    CREATES all 67, so "mapped tables no migration creates" — the definition that identified them —
    becomes the empty set. `--compare` then compared nothing and the anti-vacuity guard in
    `compare()` refused to call that clean, which is exactly what it is for: **a measurement whose
    own definition dissolves when the work succeeds will read as clean forever.** So the standing
    instrument reads the spine file's own CREATE TABLE statements, and only falls back to the
    discovery definition when that file is absent (i.e. before the spine landed).
    """
    spine = [f for f in sorted(os.listdir(MIG_DIR)) if "spine_bootstrap_schema_tables" in f]
    if spine:
        body = io.open(os.path.join(MIG_DIR, spine[-1]), encoding="utf-8").read()
        return sorted({(m.group(1).lower(), m.group(2).lower())
                       for m in CREATE_TABLE.finditer(body)})
    mig_tables, _ = migration_coverage()
    mapped = jpa_mapped()
    return [t for t in live_tables(connect(DEV).cursor())
            if t not in mig_tables and t in mapped]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--emit", type=int, metavar="VERSION",
                    help="write V<VERSION>__spine_bootstrap_schema_tables.sql and V<VERSION+1>")
    ap.add_argument("--compare", metavar="DB",
                    help="diff the spine tables' columns in DB against dev (F2-72)")
    args = ap.parse_args()

    targets = spine_set()

    if args.emit:
        emit(args.emit)
    if args.compare:
        sys.exit(1 if compare(args.compare, targets) else 0)
    if not args.emit and not args.compare:
        print("targets: %d tables" % len(targets))


if __name__ == "__main__":
    main()
