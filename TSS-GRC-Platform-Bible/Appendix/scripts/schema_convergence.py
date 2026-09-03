"""F2-09 — does a migration-built database converge on the dev schema the platform actually runs?

WHY THIS EXISTS, AND WHY IT IS A DIFFERENT QUESTION FROM THE TWO SCRIPTS BESIDE IT

This repository already measures two axes and neither is this one:

  * ``schema_drift.py``     — dev (TSS_GRC) vs prod (TSS_GRC_Deployment). Two databases that both
                              exist, diverging because prod was baselined at 292 and grant migrations
                              land in dev by hand.
  * ``full_chain_proof.py`` — can the migration folder run at all, V1 -> head, on an empty database.
                              Answers "where does it stop", not "what did it produce".

F2-09 is the third: **a fresh install is built by Flyway, and dev is built by Python.** Nothing has
ever compared the two. If they disagree, then every finding verified against dev is verified against
a schema no customer will ever have — which is not a hypothetical, it is what F2-143 found the moment
the chain first reached V320: a migration-built database had 79 of 115 permissions and 54 of 58 roles,
missing ORG_ADMIN, so F2-81's onboarding provisions into a role that does not exist on a fresh install.

WHAT IT COMPARES

Tables and columns, per schema, in both directions:

  * **missing from the build** — dev has it, the migrations do not create it. This is the direction
    that ships broken installs. Each one is either an object the bootstrap scripts create and no
    migration does, or an object created by a migration above the chain's stopping point.
  * **extra in the build** — the migrations create it and dev has never had it. Usually a dropped
    object whose DROP lives in a bootstrap script, or drift in the other direction.

⚠ WHAT IT DOES NOT PROVE

The chain currently stops at V331 on its own verification THROW, so the built database is V1 -> V330
of 360. **Everything V331+ would create is reported as missing and is not a defect** — the script
prints the stopping point alongside the counts so the two are never read apart. A run that reached
head would make "missing from the build" mean exactly "the migrations do not create it"; until then
it means "not created at or below the stop". That distinction is the whole difference between a
measurement and a scare, so it is printed, not footnoted.

USAGE
  py full_chain_proof.py --keep        # build the chain database first (minutes)
  py schema_convergence.py            # then diff it against dev
"""
import io
import os
import re
import sys

import pyodbc

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chain_marker                                    # noqa: E402

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEV = os.environ.get("CONV_DEV_DB", "TSS_GRC")
BUILT = os.environ.get("CONV_BUILT_DB", "TSS_GRC_FULL_CHAIN")

# F2-444 — needed to say whether the build is BEHIND the folder, which is the whole
# difference between "3 objects missing" and "35 objects newer than this database".
MIG_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "tss-grc-backend", "src", "main", "resources", "db", "migration", "sqlserver")

# Schemas that exist only to serve the migration mechanism or SQL Server itself. Comparing them
# reports noise: Flyway's own history table is present in a built database by construction and absent
# from dev, where Flyway is switched off entirely.
IGNORED_SCHEMAS = {"dbo", "sys", "INFORMATION_SCHEMA", "guest",
                   "db_owner", "db_accessadmin", "db_securityadmin", "db_ddladmin",
                   "db_backupoperator", "db_datareader", "db_datawriter",
                   "db_denydatareader", "db_denydatawriter"}


def connect(db):
    return pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes" % db, autocommit=True)


def objects(db):
    """(tables, columns) — tables as {schema.table}, columns as {schema.table.column}."""
    cur = connect(db).cursor()
    cur.execute("""
        SELECT s.name, t.name, c.name
          FROM sys.tables t
          JOIN sys.schemas s ON s.schema_id = t.schema_id
          LEFT JOIN sys.columns c ON c.object_id = t.object_id
         ORDER BY s.name, t.name, c.name""")
    tables, columns = set(), set()
    for schema, table, column in cur.fetchall():
        if schema in IGNORED_SCHEMAS:
            continue
        tables.add("%s.%s" % (schema, table))
        if column:
            columns.add("%s.%s.%s" % (schema, table, column))
    return tables, columns


def stop_point(cursor=None):
    """Which migration the built database reached, and where that answer came from.

    ⚠ It is still deliberately NOT INFERRED, and that reasoning was always right: guessing the stop
    from the highest-numbered object present would make the caveat depend on the same migrations it
    is meant to qualify, and a wrong stop turns 30 unrun files into 30 phantom defects.

    ⚠⚠ F2-444 — what changed is that the builders now RECORD it. This docstring used to read
    *"full_chain_proof.py prints its stopping point but records it nowhere, so there is no marker
    table to read"*, which was true and was the whole defect: the operator had to remember `--stop`,
    and forgetting it produced a report that looked like findings. Measured on 2026-09-03 against a
    twenty-seven-day-old build: **28 CHECKs, 7 FKs and 18 columns reported as missing**, every one
    merely newer than the build. Reading a recorded head is not inferring one.

    An explicit --stop or CONV_STOP still wins, so a database built by something else can be
    qualified by hand.
    """
    for i, a in enumerate(sys.argv):
        if a == "--stop" and i + 1 < len(sys.argv):
            return sys.argv[i + 1].lstrip("Vv"), "--stop"
    env = os.environ.get("CONV_STOP")
    if env:
        return env, "CONV_STOP"
    if cursor is not None:
        marker = chain_marker.read(cursor)
        if marker is not None:
            return str(marker["head_reached"]), "chain_build_marker"
    return None, None


def report(label, items, cap=30):
    print("\n%s: %d" % (label, len(items)))
    for x in sorted(items)[:cap]:
        print("    %s" % x)
    if len(items) > cap:
        print("    ... and %d more" % (len(items) - cap))


def main():
    try:
        dev_t, dev_c = objects(DEV)
    except pyodbc.Error as e:
        sys.exit("cannot read dev database %s: %s" % (DEV, e))
    try:
        built_t, built_c = objects(BUILT)
    except pyodbc.Error as e:
        sys.exit("cannot read built database %s — run `py full_chain_proof.py --keep` first.\n  %s"
                 % (BUILT, e))

    built_cur = connect(BUILT).cursor()
    marker = chain_marker.read(built_cur)
    stop, source = stop_point(built_cur)
    print("F2-09 schema convergence:  built=%s (%d tables)  vs  dev=%s (%d tables)"
          % (BUILT, len(built_t), DEV, len(dev_t)))

    # F2-444 — the build's provenance, printed FIRST, because every 'missing' count below is
    # meaningless without it.
    stale, line = chain_marker.describe(marker, chain_marker.folder_head(MIG_DIR))
    print(line)
    if stop:
        print("built database reached V%s (from %s) — anything a LATER migration creates is "
              "expected-missing" % (stop, source))
    else:
        print("⚠ could not determine the chain's stopping point; read 'missing' as "
              "'not created at or below wherever it stopped', not as 'never created'")

    missing_t = dev_t - built_t
    extra_t = built_t - dev_t
    report("TABLES in dev but NOT created by the migrations", missing_t)
    report("TABLES the migrations create that dev has never had", extra_t)

    # Columns only for tables present on both sides — a column diff on a table that is wholly absent
    # restates the table diff once per column and buries it.
    shared = dev_t & built_t
    missing_c = {c for c in dev_c - built_c if c.rsplit(".", 1)[0] in shared}
    extra_c = {c for c in built_c - dev_c if c.rsplit(".", 1)[0] in shared}
    report("COLUMNS missing from the build (on tables that DO exist in both)", missing_c)
    report("COLUMNS the build adds that dev lacks (on tables in both)", extra_c)

    print("\nsummary: %d tables missing, %d extra, %d columns missing, %d extra"
          % (len(missing_t), len(extra_t), len(missing_c), len(extra_c)))
    print("a fresh install differs from dev in %d objects"
          % (len(missing_t) + len(extra_t) + len(missing_c) + len(extra_c)))

    report_partition(missing_t, int(stop) if stop else None)
    report_column_partition(missing_c, int(stop) if stop else None)
    report_constraints(int(stop) if stop else None)


MIG_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "..",
                       "tss-grc-backend", "src", "main", "resources", "db", "migration", "sqlserver")

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"--[^\n]*")
CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?\[?(\w+)\]?\s*\.\s*\[?(\w+)\]?", re.I)


def creators():
    """{schema.table: lowest migration version whose file contains its CREATE TABLE}.

    ⚠ Comments are stripped first, and that is not defensive tidiness — it has produced false
    positives four separate times in this programme (a V233 header, the whole of V312, register prose,
    and a javadoc's example guard). A migration that *documents* a table in a header comment does not
    create it.
    """
    out = {}
    for f in sorted(os.listdir(MIG_DIR)):
        m = re.match(r"^V(\d+)__", f)
        if not m:
            continue
        version = int(m.group(1))
        body = io.open(os.path.join(MIG_DIR, f), encoding="utf-8-sig", errors="replace").read()
        body = LINE_COMMENT.sub(" ", BLOCK_COMMENT.sub(" ", body))
        for schema, table in CREATE_TABLE.findall(body):
            key = "%s.%s" % (schema.lower(), table.lower())
            if key not in out or version < out[key]:
                out[key] = version
        # A table is also "created" by a migration that renames one into place.
    return out


def partition(missing, stop):
    """Split the missing tables into the three things 'missing' can actually mean."""
    made = creators()
    later, skipped, never = [], [], []
    for t in sorted(missing):
        v = made.get(t.lower())
        if v is None:
            never.append(t)
        elif stop is not None and v > stop:
            later.append((v, t))
        else:
            skipped.append((v, t))
    return later, skipped, never


def report_partition(missing, stop):
    later, skipped, never = partition(missing, stop)

    print("\n" + "=" * 78)
    print("PARTITION — what each 'missing' table actually means")
    print("=" * 78)

    print("\n[1] Created by a migration ABOVE the stop (V%s) — expected-missing, NOT a defect: %d"
          % (stop, len(later)))
    for v, t in later[:12]:
        print("    V%-4d %s" % (v, t))
    if len(later) > 12:
        print("    ... and %d more" % (len(later) - 12))

    if stop:
        print("\n[2] ⚠ A migration AT OR BELOW the stop contains its CREATE TABLE, and the table "
              "is NOT in the built database: %d" % len(skipped))
        print("    These are the interesting ones. The file ran (the chain got past it) and the "
              "table is absent, so an idempotency guard skipped the CREATE — the shape F2-112 and "
              "the V121 demo inserts had: a control that cannot fire over a statement that cannot "
              "run.")
    else:
        # ⚠ Without a stop this bucket holds everything a migration creates, whether that
        # migration ran or not, so calling it "the interesting ones" asserts a defect class the
        # run cannot distinguish. Measured 2026-08-06: with no --stop the whole population landed
        # here and read as suspicious; with `--stop 320` it was 0 and the one item was
        # expected-missing.
        print("\n[2] A migration creates it and the built database lacks it: %d" % len(skipped))
        print("    ⚠ NO STOP GIVEN, so this cannot be read as a defect: it mixes migrations that "
              "ran and had their CREATE skipped with migrations that never ran at all. Re-run "
              "with --stop <version> to separate them.")
    for v, t in skipped[:20]:
        print("    V%-4d %s" % (v, t))
    if len(skipped) > 20:
        print("    ... and %d more" % (len(skipped) - 20))

    print("\n[3] No migration anywhere creates it — bootstrap-only, absent from every fresh "
          "install: %d" % len(never))
    for t in never[:25]:
        print("    %s" % t)
    if len(never) > 25:
        print("    ... and %d more" % (len(never) - 25))

    print("\npartition summary: %d expected-missing · %d guard-skipped · %d never-created"
          % (len(later), len(skipped), len(never)))


ALTER_ADD = re.compile(
    r"ALTER\s+TABLE\s+\[?(\w+)\]?\s*\.\s*\[?(\w+)\]?\s+ADD\s+(.*?)(?=\bGO\b|;|\Z)", re.I | re.S)
CREATE_BODY = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?\[?(\w+)\]?\s*\.\s*\[?(\w+)\]?\s*\((.*?)\)\s*(?:;|\bGO\b)",
    re.I | re.S)


def column_sources():
    """{schema.table: [(version, text)]} for every ALTER…ADD and CREATE TABLE body.

    Why ALTER…ADD is the precise instrument here, not a heuristic: these columns sit on tables that
    exist in BOTH databases, so the table's CREATE already ran. A column present in dev and absent
    from the build was therefore added afterwards — by an ALTER — or the two CREATEs disagree. Those
    are the only two possibilities, and both are covered below.

    ⚠ Still a text search WITHIN the matched clause, not a column-list parse. A clause adding
    ``recusal_user_ids NVARCHAR(MAX)`` also contains the word ``NVARCHAR``; that is harmless because
    we only ever ask about names we already know are real columns in dev. It would NOT be safe to run
    this in reverse to discover column names.
    """
    out = {}
    for f in sorted(os.listdir(MIG_DIR)):
        m = re.match(r"^V(\d+)__", f)
        if not m:
            continue
        version = int(m.group(1))
        body = io.open(os.path.join(MIG_DIR, f), encoding="utf-8-sig", errors="replace").read()
        body = LINE_COMMENT.sub(" ", BLOCK_COMMENT.sub(" ", body))
        for schema, table, clause in ALTER_ADD.findall(body):
            out.setdefault("%s.%s" % (schema.lower(), table.lower()), []).append((version, clause))
        for schema, table, clause in CREATE_BODY.findall(body):
            out.setdefault("%s.%s" % (schema.lower(), table.lower()), []).append((version, clause))
    return out


def report_column_partition(missing_c, stop):
    src = column_sources()
    later, below, never = [], [], []
    word = {}
    for full in sorted(missing_c):
        table, col = full.rsplit(".", 1)
        versions = [v for v, text in src.get(table.lower(), [])
                    if re.search(r"\b%s\b" % re.escape(col), text, re.I)]
        if not versions:
            never.append(full)
        elif stop is not None and min(versions) > stop:
            later.append((min(versions), full))
        else:
            below.append((min(versions), full))
        word[full] = versions

    print("\n" + "=" * 78)
    print("PARTITION — the missing COLUMNS (on tables present in both databases)")
    print("=" * 78)

    print("\n[1] Added by a migration ABOVE the stop (V%s) — expected-missing: %d" % (stop, len(later)))
    for v, c in later[:15]:
        print("    V%-4d %s" % (v, c))
    if len(later) > 15:
        print("    ... and %d more" % (len(later) - 15))

    if stop:
        print("\n[2] ⚠ A migration AT OR BELOW the stop names it and the column is absent: %d"
              % len(below))
        print("    Either an idempotency guard skipped the ALTER, or the migration's CREATE TABLE "
              "and the bootstrap's disagree about the column set. Both ship a fresh install that "
              "the code expects to have this column.")
    else:
        print("\n[2] A migration names it and the built database lacks it: %d" % len(below))
        print("    ⚠ NO STOP GIVEN, so this cannot be read as a defect. This is exactly how one "
              "run reported V363's bcm.crisis_comms_cascade.simulated as suspicious: the column "
              "is expected-missing because the chain database predates that migration entirely.")
    for v, c in below[:20]:
        print("    V%-4d %s" % (v, c))
    if len(below) > 20:
        print("    ... and %d more" % (len(below) - 20))

    print("\n[3] No migration anywhere adds it — bootstrap-only column on a migration-created "
          "table: %d" % len(never))
    for c in never[:25]:
        print("    %s" % c)
    if len(never) > 25:
        print("    ... and %d more" % (len(never) - 25))

    print("\ncolumn partition summary: %d expected-missing · %d below-the-stop · %d never-added"
          % (len(later), len(below), len(never)))



# ── constraint bodies ───────────────────────────────────────────────────────────────────────────
# The third axis, and the one that took longest to add because it looked unmeasurable. It is not:
# every CHECK and every foreign key in BOTH databases is user-named (0 system-named out of 416 and
# 385), so they can be compared by name, and of the 384 CHECKs present on both sides **0 differ in
# definition**. DEFAULT constraints are the opposite — 1,910 of dev's 2,259 are system-named
# (DF__table__col__ABC123), so a name comparison there is noise and is deliberately NOT attempted.

CONSTRAINT_SQL = {
    "CHECK": """SELECT LOWER(s.name + '.' + t.name), LOWER(cc.name), cc.definition
                  FROM sys.check_constraints cc
                  JOIN sys.tables t ON t.object_id = cc.parent_object_id
                  JOIN sys.schemas s ON s.schema_id = t.schema_id
                 WHERE s.name NOT IN ('dbo','sys') AND cc.is_system_named = 0""",
    "FOREIGN KEY": """SELECT LOWER(s.name + '.' + t.name), LOWER(fk.name), NULL
                        FROM sys.foreign_keys fk
                        JOIN sys.tables t ON t.object_id = fk.parent_object_id
                        JOIN sys.schemas s ON s.schema_id = t.schema_id
                       WHERE s.name NOT IN ('dbo','sys') AND fk.is_system_named = 0""",
}


def constraints(db, sql):
    """{(table, name): definition} for the user-named constraints of one kind."""
    with connect(db) as cn:
        cur = cn.cursor()
        cur.execute(sql)
        return {(r[0], r[1]): r[2] for r in cur.fetchall()}


def migration_corpus():
    """version -> stripped, lowered SQL. Comments out FIRST: this repo names constraints in prose
    constantly (reverse recipes, 'was ck_x', finding narratives), and V261 mentions all three
    CK_incidents_* names in comments alone. Attributing a constraint to a migration that only talks
    about it is the comment-as-code trap this programme has paid for repeatedly."""
    out = {}
    for f in sorted(os.listdir(MIG_DIR)):
        m = re.match(r"^V(\d+)__.*\.sql$", f)
        if not m:
            continue
        body = io.open(os.path.join(MIG_DIR, f), encoding="utf-8-sig", errors="replace").read()
        out[int(m.group(1))] = LINE_COMMENT.sub(" ", BLOCK_COMMENT.sub(" ", body)).lower()
    return out


def report_constraints(stop):
    print("\n" + "=" * 78)
    print("CONSTRAINT BODIES — CHECK and FOREIGN KEY (user-named only)")
    print("=" * 78)
    corpus = migration_corpus()
    if len(corpus) < 300:
        print("⚠ only %d migrations parsed from %s — skipping, the partition would be meaningless"
              % (len(corpus), MIG_DIR))
        return

    for kind, sql in CONSTRAINT_SQL.items():
        try:
            dev = constraints(DEV, sql)
            built = constraints(BUILT, sql)
        except pyodbc.Error as e:
            print("cannot read %s constraints: %s" % (kind, e))
            continue

        missing = sorted(set(dev) - set(built))
        extra = sorted(set(built) - set(dev))
        shared = set(dev) & set(built)
        differ = sorted(k for k in shared
                        if "".join((dev[k] or "").split()).lower()
                        != "".join((built[k] or "").split()).lower())

        later, skipped, never = [], [], []
        for tbl, name in missing:
            vs = sorted(v for v, txt in corpus.items() if name in txt)
            if not vs:
                never.append("%s.%s" % (tbl, name))
            elif stop is not None and min(vs) > stop:
                later.append((min(vs), "%s.%s" % (tbl, name)))
            else:
                skipped.append((min(vs), "%s.%s" % (tbl, name)))

        print("\n%s: dev %d · built %d · missing %d · extra %d · definition differs %d"
              % (kind, len(dev), len(built), len(missing), len(extra), len(differ)))
        if stop is not None:
            print("  [1] first named ABOVE V%s — expected-missing: %d" % (stop, len(later)))
        else:
            print("  [1] (no --stop given, cannot separate expected-missing): %d" % len(later))
        for v, n in sorted(later)[:8]:
            print("        V%-4d %s" % (v, n))

        print("  [2] ⚠ named by a migration at or below the stop and ABSENT from the build: %d"
              % len(skipped))
        if skipped and stop is None:
            print("      ⚠⚠ NO STOP KNOWN, so this CANNOT be read as a defect. It mixes"
                  " constraints whose migration ran and had its CREATE skipped with constraints"
                  " from migrations that never ran at all. F2-444: run"
                  " `full_chain_proof.py --keep` so the head is stamped, or pass --stop <version>."
                  "\n      🔑 This partition used to assert *\"The migration ran and the"
                  " constraint is not there\"* unconditionally, while the TABLE and COLUMN"
                  " partitions above printed exactly this caveat — one script, three partitions,"
                  " two honest. That sentence is what turned a stale build into 28 phantom"
                  " findings.")
        if skipped and stop is not None:
            print("      The migration ran and the constraint is not there. The usual cause is an"
                  " idempotency-guarded CREATE TABLE that was skipped because an EARLIER migration"
                  " already made the table — every inline CONSTRAINT in that CREATE goes with it."
                  " Worked example: V1 creates cyber.incidents with no value CHECKs, V4 re-creates"
                  " it with three, V4's IF NOT EXISTS skips, and a fresh install accepts"
                  " severity='BANANA'.")
        for v, n in sorted(skipped)[:15]:
            print("        V%-4d %s" % (v, n))

        print("  [3] ⚠ NO migration anywhere names it — a fresh install never enforces it: %d"
              % len(never))
        for n in never[:20]:
            print("        %s" % n)
        if len(never) > 20:
            print("        ... and %d more" % (len(never) - 20))

        if extra:
            print("  [4] the migrations create it and DEV lacks it — dev is the lenient one: %d"
                  % len(extra))
            for tbl, name in extra[:10]:
                print("        %s.%s" % (tbl, name))
        for k in differ[:10]:
            print("  ⚠ definition differs: %s.%s" % k)


if __name__ == "__main__":
    main()
