# -*- coding: utf-8 -*-
"""F2-444 — the provenance of a chain-built scratch database, so its reader cannot guess.

WHY THIS EXISTS

This directory holds a BUILDER/READER pair coupled by nothing but a database name:

  * ``chain_proof.py`` / ``full_chain_proof.py``  build ``TSS_GRC_FULL_CHAIN`` (or
    ``TSS_GRC_ChainProof``) by applying V1 -> head in Flyway's order.
  * ``schema_convergence.py``                     READS that database and diffs it against dev.

The reader had no way to ask **which head the build reached** or **when it was built**, and both
builders DROP the database on a successful run — so the only database the reader can ever find is
one left behind by an aborted run or by ``--keep``. It defaulted to reading whatever was there.

WHAT THAT COST, MEASURED

On 2026-09-03 ``schema_convergence.py`` was run and reported, against a scratch database built
**twenty-seven days earlier at V372**:

    COLUMNS missing from the build: 18
    CHECK:       dev 445 · built 414 · missing 31
      [2] named by a migration at or below the stop and ABSENT from the build: 28
          V375 consumer.complaint.ck_complaint_category ... V377 scm.fourth_party_exposures...
    FOREIGN KEY: dev 185 · built 178 · missing 7

Every one of the 28 CHECKs and 18 columns was simply **newer than the build** — V375/V376/V377 and
V387/V388/V389, migrations written by the slices that added them. Rebuilt at V398 the honest answer
is **0 columns and 0 in bucket [2]**.

The reader's own second line said *"could not determine the chain's stopping point"*. It was read
past, because bucket [2]'s heading asserts a conclusion instead of a condition:

    "The migration ran and the constraint is not there."

That sentence is only true if the build REACHED that migration, which the reader cannot know. The
TABLE partition prints an explicit caveat for exactly the same ambiguity; the CONSTRAINT partition
asserts the opposite. One script, two partitions, one honest.

WHAT THIS FIXES

The builders now stamp what they know into the scratch database, and the reader reads it — so the
stop is a **measurement** rather than a flag an operator has to remember, and a stale build is
reported as stale rather than as thirty-five defects.

⚠ The marker records the head the build **reached**, not the head it was asked for: on an aborted
run that is the last version applied cleanly, which is exactly the number the reader needs.
"""
import datetime
import glob
import os
import re

TABLE = "dbo.chain_build_marker"


def folder_head(mig_dir):
    """The highest V number on disk. The reader compares the marker against this to spot staleness."""
    best = 0
    for f in glob.glob(os.path.join(mig_dir, "V*__*.sql")):
        m = re.match(r"V(\d+)__", os.path.basename(f))
        if m:
            best = max(best, int(m.group(1)))
    return best


def stamp(cursor, head_reached, migrations_applied, mig_dir, complete):
    """Record the build's provenance. Idempotent: the marker is replaced, never appended to."""
    cursor.execute(
        "IF OBJECT_ID('%s') IS NOT NULL DROP TABLE %s" % (TABLE, TABLE))
    cursor.execute(
        "CREATE TABLE %s ("
        " head_reached INT NOT NULL,"
        " migrations_applied INT NOT NULL,"
        " folder_head INT NOT NULL,"
        " chain_complete BIT NOT NULL,"
        " built_at_utc DATETIME2 NOT NULL)" % TABLE)
    cursor.execute(
        "INSERT INTO %s (head_reached, migrations_applied, folder_head, chain_complete, "
        "built_at_utc) VALUES (?, ?, ?, ?, ?)" % TABLE,
        int(head_reached), int(migrations_applied), int(folder_head(mig_dir)),
        1 if complete else 0, datetime.datetime.utcnow())


def read(cursor):
    """The marker, or None when the database predates this mechanism.

    ⚠ Returning None is the honest answer for a database built before F2-444, and the reader must
    say so rather than treating an unknown head as 'the current one' — that substitution is the
    whole defect this module exists to remove.
    """
    cursor.execute("SELECT CASE WHEN OBJECT_ID('%s') IS NULL THEN 0 ELSE 1 END" % TABLE)
    if not cursor.fetchone()[0]:
        return None
    cursor.execute("SELECT head_reached, migrations_applied, folder_head, chain_complete, "
                   "built_at_utc FROM %s" % TABLE)
    row = cursor.fetchone()
    if row is None:
        return None
    return {
        "head_reached": int(row[0]),
        "migrations_applied": int(row[1]),
        "folder_head": int(row[2]),
        "chain_complete": bool(row[3]),
        "built_at_utc": row[4],
    }


def describe(marker, current_folder_head):
    """One line for the reader to print, and a stale flag it must act on."""
    if marker is None:
        return (True,
                "build provenance UNKNOWN — this database carries no chain_build_marker, so it "
                "predates F2-444 or was not built by chain_proof/full_chain_proof. Pass --stop "
                "<version>, or rebuild with --keep. 'missing' below CANNOT be read as a defect.")
    stale = marker["head_reached"] < current_folder_head
    line = ("build provenance: head V%d %s, %d migrations applied, built %s UTC (folder head is "
            "now V%d)" % (marker["head_reached"],
                          "complete" if marker["chain_complete"] else "ABORTED",
                          marker["migrations_applied"],
                          marker["built_at_utc"].strftime("%Y-%m-%d %H:%M"),
                          current_folder_head))
    if stale:
        line += ("\n⚠⚠ STALE BUILD: the folder is %d version(s) ahead of this database. "
                 "Objects first named above V%d are EXPECTED-MISSING and are not defects — "
                 "rebuild with `full_chain_proof.py --keep` before believing any 'missing' count."
                 % (current_folder_head - marker["head_reached"], marker["head_reached"]))
    return stale, line
