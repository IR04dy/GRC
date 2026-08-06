# -*- coding: utf-8 -*-
"""F2-77 — can the migration folder actually build the platform?

Committed to Appendix/scripts on 2026-08-06 because the previous copy lived in a
session-scoped scratchpad and was lost, which is a large part of why this row stalled: the
instrument that makes it checkable had to be rebuilt before any progress could be made.

Creates a throwaway database, runs V1 -> head in Flyway's numeric order, and stops at the FIRST
failure, naming the file, the batch and the error. That is the whole instrument: the row's claim is
that the folder has been maintained as *a description of the current schema* rather than as *an
ordered history*, and nothing ever ran it in order to notice.

⚠ Only ever touches the database named below, which it creates. It does not read or write TSS_GRC
or TSS_GRC_Deployment.

Usage:  py chain_proof.py [--stop-after V123] [--keep]
"""
import os
import re
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROOF_DB = "TSS_GRC_ChainProof"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.environ.get("GRC_ROOT") or os.path.abspath(os.path.join(HERE, "..", "..", ".."))
MIG = os.path.join(ROOT, "tss-grc-backend", "src", "main", "resources",
                   "db", "migration", "sqlserver")
BASE = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes;DATABASE=%s")
GO = re.compile(r"^\s*GO\s*(?:--.*)?$", re.I)

stop_after = None
if "--stop-after" in sys.argv:
    stop_after = int(sys.argv[sys.argv.index("--stop-after") + 1].lstrip("Vv"))


def versions():
    out = []
    for f in os.listdir(MIG):
        m = re.match(r"V(\d+)__(.+)\.sql$", f)
        if m:
            out.append((int(m.group(1)), f))
    return sorted(out)


def batches(sql):
    """Split on standalone GO, the way sqlcmd does. pyodbc cannot execute GO itself."""
    cur, out = [], []
    for line in sql.split("\n"):
        if GO.match(line):
            if any(x.strip() for x in cur):
                out.append("\n".join(cur))
            cur = []
        else:
            cur.append(line)
    if any(x.strip() for x in cur):
        out.append("\n".join(cur))
    return out


# ── fresh database ──────────────────────────────────────────────────────────────────────────────
master = pyodbc.connect(BASE % "master", autocommit=True)
mc = master.cursor()
mc.execute("IF DB_ID(?) IS NOT NULL BEGIN ALTER DATABASE [" + PROOF_DB
           + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [" + PROOF_DB + "]; END",
           PROOF_DB)
mc.execute("CREATE DATABASE [" + PROOF_DB + "] COLLATE Latin1_General_100_CI_AS_SC_UTF8")
print("created %s (dropped any previous copy)" % PROOF_DB)

cn = pyodbc.connect(BASE % PROOF_DB, autocommit=True)
cu = cn.cursor()

vs = versions()
print("migrations on disk: %d  (V%d .. V%d)\n" % (len(vs), vs[0][0], vs[-1][0]))

applied = 0
for ver, fn in vs:
    if stop_after is not None and ver > stop_after:
        print("\n-- stopping after V%d as asked" % stop_after)
        break
    sql = open(os.path.join(MIG, fn), encoding="utf-8-sig", errors="replace").read()
    for i, b in enumerate(batches(sql), 1):
        try:
            cu.execute(b)
            while cu.nextset():
                pass
        except Exception as e:
            msg = str(e)
            print("=" * 100)
            print("FIRST FAILURE at V%d  (batch %d of %d)" % (ver, i, len(batches(sql))))
            print("  file : %s" % fn)
            print("  error: %s" % msg[:600])
            first = "\n".join(ln for ln in b.split("\n") if ln.strip())[:500]
            print("  batch starts:\n    %s" % first.replace("\n", "\n    ")[:500])
            print("=" * 100)
            print("chain reached: V%d applied cleanly, then V%d failed" % (vs[vs.index((ver, fn)) - 1][0] if vs.index((ver, fn)) else 0, ver))
            cu.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'")
            print("tables created so far: %d" % cu.fetchone()[0])
            if "--keep" not in sys.argv:
                cn.close()
                mc.execute("ALTER DATABASE [" + PROOF_DB + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE")
                mc.execute("DROP DATABASE [" + PROOF_DB + "]")
                print("dropped %s" % PROOF_DB)
            sys.exit(1)
    applied += 1
    if applied % 25 == 0:
        print("  ... V%d ok (%d applied)" % (ver, applied))

cu.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'")
tables = cu.fetchone()[0]
print("\n" + "=" * 100)
print("THE WHOLE CHAIN RAN: %d migrations applied, %d tables created" % (applied, tables))
print("=" * 100)
if "--keep" not in sys.argv:
    cn.close()
    mc.execute("ALTER DATABASE [" + PROOF_DB + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE")
    mc.execute("DROP DATABASE [" + PROOF_DB + "]")
    print("dropped %s" % PROOF_DB)
