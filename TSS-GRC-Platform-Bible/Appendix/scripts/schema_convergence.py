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
import os
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEV = os.environ.get("CONV_DEV_DB", "TSS_GRC")
BUILT = os.environ.get("CONV_BUILT_DB", "TSS_GRC_FULL_CHAIN")

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


def stop_point():
    """Which migration the built database reached.

    full_chain_proof.py prints its stopping point but records it nowhere, so there is no marker table
    to read — pass it in with --stop 330 or CONV_STOP=330. ⚠ It is deliberately NOT inferred:
    guessing the stop from the highest-numbered object present would make the caveat depend on the
    same migrations it is meant to qualify, and a wrong stop turns 30 unrun files into 30 phantom
    defects.
    """
    for i, a in enumerate(sys.argv):
        if a == "--stop" and i + 1 < len(sys.argv):
            return sys.argv[i + 1].lstrip("Vv")
    return os.environ.get("CONV_STOP")


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

    stop = stop_point()
    print("F2-09 schema convergence:  built=%s (%d tables)  vs  dev=%s (%d tables)"
          % (BUILT, len(built_t), DEV, len(dev_t)))
    if stop:
        print("built database reached V%s — anything a LATER migration creates is expected-missing"
              % stop)
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


if __name__ == "__main__":
    main()
