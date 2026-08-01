"""Run EVERY migration V1 -> head against an empty database, in Flyway's order, and report exactly
where it stops.

The plan estimated this "aborts no later than V47", whose guard is
`IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_versions'…))`
— on an empty database OBJECT_ID is NULL, the subquery is empty, NOT EXISTS passes, and the ALTER
beneath it fails. An estimate is not a measurement, and this is the measurement: it stops at the
first failure and prints the file, the batch and the error, so the remaining work has a size instead
of an adjective.
"""
import io
import os
import re
import sys

import pyodbc

sys.path.insert(0, r"D:\AYMAN\GRC\TSS-GRC-Platform-Bible\Appendix\scripts")
import schema_from_catalog as G                                     # noqa: E402

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
SCRATCH = "TSS_GRC_FULL_CHAIN"
STOP_AFTER = 10 ** 9
CONTINUE = "--continue" in sys.argv


def master():
    return pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=master;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes", autocommit=True)


mcur = master().cursor()
mcur.execute("IF DB_ID(?) IS NOT NULL BEGIN ALTER DATABASE [%s] SET SINGLE_USER WITH ROLLBACK "
             "IMMEDIATE; DROP DATABASE [%s]; END" % (SCRATCH, SCRATCH), SCRATCH)
mcur.execute("CREATE DATABASE [%s]" % SCRATCH)
print("empty database %s created\n" % SCRATCH)

files = []
for f in os.listdir(G.MIG_DIR):
    m = re.match(r"^V(\d+)__(.*)\.sql$", f)
    if m:
        files.append((int(m.group(1)), f))
files.sort()
print("%d migration files, V%d -> V%d\n" % (len(files), files[0][0], files[-1][0]))

cur = G.connect(SCRATCH).cursor()
ok, failures = 0, []
try:
    for ver, f in files:
        if ver > STOP_AFTER:
            break
        body = io.open(os.path.join(G.MIG_DIR, f), encoding="utf-8", errors="replace").read()
        batches = [b.strip() for b in re.split(r"(?im)^\s*GO\s*$", body) if b.strip()]
        for i, b in enumerate(batches, 1):
            try:
                cur.execute(b)
                while cur.nextset():
                    pass
            except Exception as ex:
                msg = str(ex)
                # Flyway stops at the first failing migration. Report and stop, like it would.
                if CONTINUE:
                    print("  FAIL V%-4d %-52s %s" % (ver, f, msg.split("]")[-1].strip()[:88]))
                    failures.append((ver, f, i, msg))
                    break
                print("FIRST FAILURE at V%d (%s), batch %d/%d:" % (ver, f, i, len(batches)))
                print("   %s" % msg[:500])
                first = next((ln for ln in b.splitlines() if ln.strip()
                              and not ln.strip().startswith("--")), "")
                print("   first non-comment line: %s" % first.strip()[:150])
                failures.append((ver, f, i, msg))
                raise SystemExit(0)
        ok += 1
        if ok % 50 == 0:
            print("   ... V%d applied (%d files so far)" % (ver, ok))
    print("\nALL %d MIGRATIONS APPLIED to an empty database" % ok)
    cur.execute("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0")
    t = cur.fetchone()[0]
    cur.execute("""SELECT COUNT(*) FROM sys.schemas WHERE principal_id = 1
                    AND name NOT IN ('dbo','guest','sys','INFORMATION_SCHEMA')""")
    s = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sys.foreign_keys")
    fk = cur.fetchone()[0]
    dev = G.connect(G.DEV).cursor()
    dev.execute("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0")
    dt = dev.fetchone()[0]
    dev.execute("""SELECT COUNT(*) FROM sys.schemas WHERE principal_id = 1
                    AND name NOT IN ('dbo','guest','sys','INFORMATION_SCHEMA')""")
    ds = dev.fetchone()[0]
    print("   built: %d tables / %d schemas / %d FKs" % (t, s, fk))
    print("   dev:   %d tables / %d schemas" % (dt, ds))

    # Which mapped tables are still missing? That is the honest remaining number.
    built = set(G.live_tables(cur))
    missing = sorted(x for x in G.jpa_mapped() if x not in built)
    print("\n   JPA-mapped tables STILL missing from the built database: %d" % len(missing))
    for x in missing[:25]:
        print("      %s.%s" % x)
finally:
    mcur.execute("IF DB_ID(?) IS NOT NULL BEGIN ALTER DATABASE [%s] SET SINGLE_USER WITH ROLLBACK "
                 "IMMEDIATE; DROP DATABASE [%s]; END" % (SCRATCH, SCRATCH), SCRATCH)
    print("\ndropped %s" % SCRATCH)
