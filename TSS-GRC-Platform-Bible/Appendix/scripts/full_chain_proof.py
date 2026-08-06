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
"""Scratch files go on D:, deliberately, and this is not a preference.

SQL Server Express keeps its data directory under C:\\Program Files\\...\\MSSQL\\DATA, and C: on this
machine has been hostile to it twice in one day:

  1. With C: near zero, CREATE DATABASE failed with OS error 112 (out of space).
  2. After ~22 GB was freed — by NTFS-compressing C:, it turned out — CREATE DATABASE failed with
     error 5118: "the file is compressed but does not reside in a read-only database or filegroup".
     Fixing the disk broke it a second way.

Neither failure names the disk in a way that points at the disk, which is the landmine CLAUDE.md now
records. Putting the scratch files on D: (388 GB, uncompressed, where the repo already lives) removes
the whole class: this instrument no longer depends on the state of C: at all. It also means the proof
can run on a machine whose C: is locked down, which any build agent's will be.
"""
DATA_DIR = os.environ.get("CHAIN_PROOF_DATA_DIR", r"D:\AYMAN\GRC\.chain-proof-data")
os.makedirs(DATA_DIR, exist_ok=True)
mcur.execute(
    "CREATE DATABASE [{db}] ON (NAME='{db}_dat', FILENAME='{d}\\{db}.mdf', SIZE=64MB, FILEGROWTH=64MB) "
    "LOG ON (NAME='{db}_log', FILENAME='{d}\\{db}.ldf', SIZE=32MB, FILEGROWTH=32MB)".format(
        db=SCRATCH, d=DATA_DIR))
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
        # utf-8-sig, not utf-8 — and the difference is a whole instrument's credibility.
        #
        # ⚠ This script reported FIRST FAILURE at V55 with "Incorrect syntax near '﻿'" while its
        # sibling chain_proof.py reached V330 over the same files. V55 is not broken: many migrations
        # here are BOM-prefixed, and plain utf-8 keeps the BOM as a character, so it lands at the head
        # of batch 1 where SQL Server cannot parse it. chain_proof.py has always used utf-8-sig, which
        # strips it. Two instruments over one subject disagreed by 275 files, and the subject was
        # innocent both times — the reader was the variable.
        body = io.open(os.path.join(G.MIG_DIR, f), encoding="utf-8-sig", errors="replace").read()
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
    # --keep leaves the built database in place so another instrument can read it.
    #
    # F2-09 asks what a migration-built database contains versus the Python-bootstrapped dev schema,
    # and that question needs the artefact, not just the exit point. Dropping unconditionally made
    # this script answer "where does the chain stop" and nothing else; the build is the expensive part
    # (330 files, minutes) and throwing it away forced every downstream question to pay for it again.
    if "--keep" in sys.argv:
        print("\nkept %s for inspection (re-run without --keep to drop it)" % SCRATCH)
    else:
        mcur.execute("IF DB_ID(?) IS NOT NULL BEGIN ALTER DATABASE [%s] SET SINGLE_USER WITH ROLLBACK "
                     "IMMEDIATE; DROP DATABASE [%s]; END" % (SCRATCH, SCRATCH), SCRATCH)
        print("\ndropped %s" % SCRATCH)
