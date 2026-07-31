# Schema drift — what dev has that prod does not, what prod has that nothing will ever remove,
# and which packaged migrations have not run.
#
# WHY THIS EXISTS
#
# This platform keeps two SQL Server databases on one instance: TSS_GRC (dev, Flyway OFF, schema
# bootstrapped by Python) and TSS_GRC_Deployment (what the deployed jar serves, Flyway ON, baselined
# at 292). Nothing reconciles them. Three failure modes follow, and all three are live today:
#
#   1. A FIXED FINDING IS STILL OPEN IN PRODUCTION. Grant migrations land in dev by hand the day
#      they are written and in prod only when someone restarts the service. Measured 2026-07-31:
#      REPORTING:WRITE is held by 2 roles in dev and ZERO in prod, because F2-43's fix (V321) is
#      packaged but unapplied. Any audit that reads dev reports it fixed.
#
#   2. A TABLE EXISTS IN PROD THAT FLYWAY WILL NEVER DROP. Prod was restored from a dev dump on
#      2026-07-11 and then baselined at 292, so every migration numbered below 292 is marked
#      as-already-applied whether it ran or not. V247 drops three legacy KRI tables; they are
#      present in prod, absent from dev, and V247 will never execute. Same mechanism, opposite
#      direction: V172 CREATES bcm.crisis_comms_cascade and crisis_comms_template_v2, and those
#      tables exist in NEITHER database while JPA entities, two controllers and three routed UI
#      pages sit on top of them. F2-49's MigrationFilenameRatchetTest cannot catch this: the files
#      are well-formed, correctly numbered and simply never run.
#
#   3. THE PROD DATA IS THE DEMO DATA. 428 of 458 shared tables carry identical row counts, there
#      are zero dev-only tables, and the four prod-only ones are flyway_schema_history plus those
#      three legacy KRI tables. So per-row provenance analysis of prod is meaningless — the only
#      trustworthy provenance evidence is a clean-tenant probe.
#
# Read-only. Issues SELECTs against sys.* and platform.flyway_schema_history, reads the migration
# folder off disk, and writes one markdown report. It changes nothing in either database.
#
#   py TSS-GRC-Platform-Bible/Appendix/scripts/schema_drift.py
#
# Companion to deployment_identity.py, which answers "what does the RUNNING process contain" —
# this answers "what do the two SCHEMAS contain, and what is packaged but unapplied".

import argparse
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

try:
    import pyodbc
except ImportError:
    sys.exit("pyodbc is required: py -m pip install pyodbc")

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
MIGRATIONS = os.path.join(ROOT, "tss-grc-backend", "src", "main", "resources",
                          "db", "migration", "sqlserver")
REPORT = os.path.join(ROOT, "TSS-GRC-Platform-Bible", "Appendix", "SCHEMA_DRIFT.md")

CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
DEV = "TSS_GRC"
# FLYWAY_BASELINE_VERSION in the deployed external.properties. Anything at or below this is
# recorded as applied whether it ever executed.
BASELINE = 292
PROD = "TSS_GRC_Deployment"

# Row counts come from sys.partitions, which is exact for anything with a clustered index and
# approximate for heaps. Every table this platform defines has a primary key, so the numbers below
# are exact — but the caveat is recorded because it stops being true the moment someone adds a heap.
TABLE_SQL = """
SELECT s.name, t.name, ISNULL(SUM(p.rows), 0), t.create_date
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
LEFT JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY s.name, t.name, t.create_date
"""


def tables(cur):
    out = {}
    for schema, table, rows, created in cur.execute(TABLE_SQL).fetchall():
        out["%s.%s" % (schema, table)] = {"rows": int(rows), "created": created}
    return out


def applied(cur):
    """Flyway history, or None when the table is absent (dev, where Flyway is off)."""
    try:
        rows = cur.execute("SELECT version, script, success FROM platform.flyway_schema_history "
                           "WHERE version IS NOT NULL").fetchall()
    except pyodbc.Error:
        return None
    return {r[0]: {"script": r[1], "success": bool(r[2])} for r in rows}


def on_disk():
    if not os.path.isdir(MIGRATIONS):
        sys.exit("migration folder not found: %s" % MIGRATIONS)
    found = {}
    for name in os.listdir(MIGRATIONS):
        m = re.match(r"^V(\d+(?:\.\d+)*)__", name)
        if m and name.endswith(".sql"):
            found[m.group(1)] = name
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", default=REPORT)
    ap.add_argument("--dev", default=DEV)
    ap.add_argument("--prod", default=PROD)
    args = ap.parse_args()

    out = []

    def emit(line=""):
        print(line)
        out.append(line)

    try:
        dev = tables(pyodbc.connect(CONN % args.dev).cursor())
    except pyodbc.Error as exc:
        sys.exit("cannot read %s: %s" % (args.dev, exc))
    prod_cur = pyodbc.connect(CONN % args.prod).cursor()
    prod = tables(prod_cur)
    history = applied(prod_cur)
    disk = on_disk()

    shared = sorted(set(dev) & set(prod))
    dev_only = sorted(set(dev) - set(prod))
    prod_only = sorted(set(prod) - set(dev))
    same_rows = [k for k in shared if dev[k]["rows"] == prod[k]["rows"]]

    emit("# Schema drift — dev vs production, and packaged vs applied")
    emit()
    emit("Generated by `Appendix/scripts/schema_drift.py`. Read-only.")
    emit()
    emit("| | count |")
    emit("|---|---|")
    emit("| tables in `%s` (dev) | %d |" % (args.dev, len(dev)))
    emit("| tables in `%s` (prod) | %d |" % (args.prod, len(prod)))
    emit("| shared | %d |" % len(shared))
    emit("| shared **with identical row counts** | %d (%.0f%%) |"
         % (len(same_rows), 100.0 * len(same_rows) / max(1, len(shared))))
    emit("| dev-only | %d |" % len(dev_only))
    emit("| prod-only | %d |" % len(prod_only))
    emit("| migration files on disk | %d |" % len(disk))
    emit("| applied in prod (Flyway history) | %s |"
         % ("%d" % len(history) if history is not None else "no history table"))
    emit()

    # ── 1. the clone signature ───────────────────────────────────────────────
    pct = 100.0 * len(same_rows) / max(1, len(shared))
    emit("## 1. Is production a copy of development?")
    emit()
    if pct > 80 and not dev_only:
        emit("**Yes, on the evidence.** %.0f%% of shared tables carry identical row counts and there"
             % pct)
        emit("are **no dev-only tables** — a schema that had evolved independently would have both.")
        emit("Consequence for any orphan or provenance audit: **prod's data IS the demo data**, so")
        emit("classifying existing rows by provenance proves nothing. The only trustworthy evidence")
        emit("is a clean-tenant probe.")
    else:
        emit("**Not on this evidence** — %.0f%% identical row counts, %d dev-only tables."
             % (pct, len(dev_only)))
    emit()

    # ── 2. prod-only tables: Flyway cannot remove what it thinks it applied ──
    emit("## 2. Prod-only tables (%d)" % len(prod_only))
    emit()
    emit("A table here is either legitimately prod-side (Flyway's own history) or a **leftover no")
    emit("migration will ever drop**, because prod is baselined at 292 and every migration numbered")
    emit("below that is recorded as applied whether it ran or not.")
    emit()
    if prod_only:
        emit("| table | rows | created | note |")
        emit("|---|---|---|---|")
        for k in prod_only:
            note = "Flyway's own bookkeeping" if k.endswith("flyway_schema_history") \
                else "**no migration will drop this** — see §4"
            emit("| `%s` | %d | %s | %s |"
                 % (k, prod[k]["rows"], prod[k]["created"].strftime("%Y-%m-%d %H:%M"), note))
    else:
        emit("_none_")
    emit()

    # ── 3. packaged but unapplied ────────────────────────────────────────────
    emit("## 3. Packaged on disk but not applied in production")
    emit()
    if history is None:
        emit("_No Flyway history in `%s` — cannot compute._" % args.prod)
    else:
        def as_int(v):
            try:
                return int(str(v).split(".")[0])
            except ValueError:
                return -1

        heads = [as_int(v) for v in history if as_int(v) >= 0]
        head = max(heads) if heads else 0
        missing = sorted((v for v in disk if v not in history), key=as_int)
        emit("Prod Flyway head is **V%d**; highest file on disk is **V%s**." % (head, max(disk, key=as_int)))
        emit()
        above = [v for v in missing if as_int(v) > BASELINE]
        below = [v for v in missing if as_int(v) <= BASELINE]
        emit("**%d migration files have no history row in production**, and the split matters more"
             % len(missing))
        emit("than the total:")
        emit()
        emit("* **%d numbered ABOVE the %d baseline** — simply pending; they run on the next restart:"
             % (len(above), BASELINE))
        emit("  %s" % (", ".join("V%s" % v for v in above) or "_none_"))
        emit("* **%d numbered AT OR BELOW the baseline — these will NEVER run.** Flyway treats"
             % len(below))
        emit("  anything at or under the baseline as already applied. Most are harmless: the restored")
        emit("  dump already contains their effect. Listing all %d would be noise, so §3b lists only"
             % len(below))
        emit("  the ones whose **stated intent disagrees with the schema that exists**.")
        emit()

        # ── 3b. the actionable subset ────────────────────────────────────────
        emit("### 3b. Baseline-skipped migrations whose intent the schema contradicts")
        emit()
        emit("For each never-running file, the `CREATE TABLE` / `DROP TABLE` targets are parsed and")
        emit("checked against both databases. A row here is a migration that believes something about")
        emit("the schema which is not true — the only subset worth a human's time.")
        emit()
        creates = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?\[?(\w+)\]?\.\[?(\w+)\]?",
                             re.I)
        drops = re.compile(r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?\[?(\w+)\]?\.\[?(\w+)\]?", re.I)
        contradictions = []
        for v in below:
            path = os.path.join(MIGRATIONS, disk[v])
            try:
                with open(path, encoding="utf-8", errors="replace") as fh:
                    body = fh.read()
            except OSError:
                continue
            dev_keys = {k.lower() for k in dev}
            prod_keys = {k.lower() for k in prod}
            created_here = {"%s.%s" % (s.lower(), t.lower()) for s, t in creates.findall(body)}
            dropped_here = {"%s.%s" % (s.lower(), t.lower()) for s, t in drops.findall(body)}

            for key in created_here:
                if key not in dev_keys and key not in prod_keys:
                    contradictions.append((v, disk[v], "CREATE", key,
                                           "absent from BOTH databases"))
            # A table this file DROPS *and* re-CREATEs is being repaired, not retired, so its
            # presence afterwards is the intended end state — not a contradiction. Without this,
            # V93 (drop the V1 stub `platform.controls`, re-create it with the V92 schema, re-run
            # the seed) reports as "should be gone, still present in prod" against a table holding
            # 32 legitimate rows in both databases. That was this script's first false positive,
            # and it is the failure mode the repo has hit repeatedly: a regex that reads a verb
            # without its context. Retirement is DROP with no matching CREATE.
            for key in dropped_here - created_here:
                if key in prod_keys:
                    contradictions.append((v, disk[v], "DROP", key,
                                           "still present in prod%s" %
                                           ("" if key in dev_keys else " (already gone from dev)")))
        if contradictions:
            emit("| migration | intent | table | contradiction |")
            emit("|---|---|---|---|")
            for v, fname, verb, key, note in sorted(contradictions, key=lambda r: as_int(r[0])):
                emit("| `%s` | %s | `%s` | **%s** |" % (fname, verb, key, note))
            emit()
            emit("⚠ **This is the defect class no filename ratchet can catch.** F2-49's")
            emit("`MigrationFilenameRatchetTest` checks that names are well-formed and unique; these")
            emit("files are both. Only comparing intent against the live schema finds them.")
            emit()
            emit("A `CREATE` contradiction means a capability may be shipped over a table that does")
            emit("not exist — check for entities and routes before assuming it is dead code. A `DROP`")
            emit("contradiction means a table the platform decided to retire is still carrying data")
            emit("in production, and needs an idempotent re-issue above the baseline.")
        else:
            emit("_None — every never-running migration's intent matches the schema that exists._")
    emit()

    # ── 4. tables absent from BOTH databases that code still maps ────────────
    emit("## 4. Cross-check to run next")
    emit()
    emit("This script compares two databases against each other and against the migration folder.")
    emit("It cannot see the third party to the contract: **the code**. A table can be absent from")
    emit("both databases while entities, controllers and UI routes sit on top of it — the")
    emit("`bcm.crisis_comms_cascade` / `crisis_comms_template_v2` case, where `V172` creates them,")
    emit("dev never ran Flyway and prod baselined past it, so a routed screen returns 503")
    emit("`db-object-missing` for every caller. Run `table_xref.py` for that direction.")
    emit()
    emit("_Row counts come from `sys.partitions`, exact for tables with a clustered index. Every")
    emit("table here has a primary key; the caveat matters only if a heap is ever added._")

    with open(args.md, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    print()
    print("wrote %s" % args.md)


if __name__ == "__main__":
    main()
