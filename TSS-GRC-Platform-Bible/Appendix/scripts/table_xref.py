# Table cross-reference — every table in either database, joined to every place the
# platform could possibly touch it, classified, and emitted as a GENERATED register.
#
# F2-68(a)/(b). Read-only: SELECTs against sys.* plus a filesystem walk. Writes two files.
#
#   py TSS-GRC-Platform-Bible/Appendix/scripts/table_xref.py
#
# WHY A GENERATED REGISTER, AND NOT A MAINTAINED ONE
#
# The reachability plan asked for a 20-field hand-maintained "Master Capability Register".
# This repo has already measured what happens to hand-maintained ledgers: §4 of
# UNIFIED_IMPLEMENTATION_PLAN.md was "renumbered by delivery ten times" WHILE being actively
# maintained, and stopped reserving migration numbers because of it. So the register here is
# rebuilt from live evidence on every run, and the only hand-written artefact is a small
# exceptions file that the generator VALIDATES IN BOTH DIRECTIONS:
#
#   * a table with no exception entry and no automatic classification lands as UNKNOWN — it
#     cannot hide by being unlisted; and
#   * an exception naming a table that no longer exists in either database is an ERROR — a
#     stale allowlist entry is exactly how a control quietly stops covering anything.
#
# That is the INTENTIONALLY_OFF_NAV pattern from routeReachability.test.ts, which this repo has
# already proven works.
#
# WHAT IT CAN AND CANNOT CONCLUDE
#
# This scans the DATA plane. It can say "no code maps this table and nothing inserts into it".
# It CANNOT say a screen is unreachable or an endpoint is dark — that needs the route plane,
# which routeReachability.test.ts (F2-07b) already owns and which found the 8 unlinked SOC Ops
# pages. Codes this script will not assign are listed in NOT_ASSIGNABLE_HERE below, because a
# classifier that guesses beyond its evidence is worse than one that admits a gap.
#
# ONE DELIBERATE PRECISION CHOICE
#
# Java comments are STRIPPED before looking for `schema.table` references. Without that, a table
# named only in Javadoc counts as referenced — and a false reference makes an orphan look alive,
# which is the direction that loses findings. This repo has been bitten by regex-over-Javadoc
# repeatedly (the 418-vs-413 entity count; F2-27's 8 missed guards), so the cheap fix is taken.

import argparse
import csv
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

try:
    import pyodbc
except ImportError:
    sys.exit("pyodbc is required: py -m pip install pyodbc")

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
BACKEND = os.path.join(ROOT, "tss-grc-backend", "src")
MIGRATIONS = os.path.join(ROOT, "tss-grc-backend", "src", "main", "resources",
                          "db", "migration", "sqlserver")
APPENDIX = os.path.join(ROOT, "TSS-GRC-Platform-Bible", "Appendix")
REGISTER = os.path.join(APPENDIX, "TABLE_DISPOSITION_REGISTER.csv")
REPORT = os.path.join(APPENDIX, "TABLE_DISPOSITION.md")
EXCEPTIONS = os.path.join(APPENDIX, "table_disposition_exceptions.csv")

CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
DEV, PROD = "TSS_GRC", "TSS_GRC_Deployment"

# Codes this script assigns from data-plane evidence alone.
CODES = ("MAPPED_NO_TABLE", "OPERATING", "WIRED_UNPROVEN", "LEGACY", "SEED_ONLY",
         "ORPHAN_CONFIRMED", "PROD_ONLY_LEFTOVER", "INTENTIONAL_TECHNICAL",
         "DORMANT", "UNKNOWN")
# Codes that need the route/UI plane and are therefore NOT guessed here.
# DEMO_ONLY became SEED_ONLY and IS assignable: a py_insert with no Java reference is
# direct evidence, not a guess. The rest still need the route plane.
NOT_ASSIGNABLE_HERE = ("DARK_UI", "DARK_API", "BROKEN_CHAIN")

# Known positives the scan MUST rediscover, or it does not work. Anti-vacuity in the house
# style: a green scan that found nothing is indistinguishable from a broken one.
# Anchors must be STABLE. `risk.kri_definitions` was an anchor until it was pointed out that
# V337 drops it on the next prod restart — after which the table leaves the register, the anchor
# misses, and the scan fails forever for a reason that is not a defect. An anti-vacuity check
# that expires is worse than none, because it trains people to ignore a red scan.
MUST_FIND = {
    "privacy_lc": "the documented orphan family — 0 rows, orphaned schema, not scheduled to change",
    "platform.audit_log": "the busiest table in the platform; if this is missing the census failed",
    "iam.roles": "authorisation core; present in every environment by definition",
}

ANN_START = re.compile(r"@(Table|JoinTable|CollectionTable|SecondaryTable)\s*\(")
NAME = re.compile(r'\bname\s*=\s*"([^"]+)"')
SCHEMA_ATTR = re.compile(r'\bschema\s*=\s*"([^"]+)"')

# `spring.jpa.properties.hibernate.default_schema` in application.yml. A @Table that omits
# schema= resolves to this at runtime, so the mapping IS knowable — the schema is simply
# declared in configuration instead of in the annotation.
# Flyway's baseline on the deployed server: anything at or below it is recorded as applied
# whether it ever ran. A DROP down there retires a table on paper only — see F2-65.
FLYWAY_BASELINE = 292


def mig_version(filename):
    """V337__x.sql -> 337; anything unparseable sorts below the baseline (fail safe)."""
    m = re.match(r"V(\d+)__", filename)
    return int(m.group(1)) if m else -1


DEFAULT_SCHEMA = "platform"


def annotation_bodies(text):
    """Yield the body of every @Table-family annotation, matching parentheses properly.

    The first version of this used `\\(([^)]*)\\)`, which stops at the first ')' — so
    `@Table(name = "x", indexes = @Index(...))` was truncated at the inner ')' and any
    schema= after it was lost. Combined with requiring schema= to be present, that skipped
    four entities entirely and reported MAPPED_NO_TABLE = 0 when the true answer was 4:
    platform.file_metadata, persistence_row, nafath_transaction and sso_provider_config are
    absent from EVERY schema in BOTH databases, their DDL is V12/V13/V14 (below the Flyway
    baseline, so it can never run), and /api/v1/files and /api/v1/persistence are live
    controllers over them. The scanner built to find F2-64 had F2-64's own blind spot.
    """
    for m in ANN_START.finditer(text):
        depth, i, n = 1, m.end(), len(text)
        while i < n and depth:
            ch = text[i]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        yield text[m.end():i - 1]
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text):
    """Remove block and line comments. See the header: a table named only in Javadoc
    would otherwise count as a live reference, which hides orphans."""
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def db_tables(db):
    try:
        c = pyodbc.connect(CONN % db).cursor()
    except pyodbc.Error as exc:
        sys.exit("cannot read %s: %s" % (db, exc))
    out = {}
    rows = c.execute("""
        SELECT s.name, t.name, ISNULL(SUM(p.rows), 0)
        FROM sys.tables t
        JOIN sys.schemas s ON s.schema_id = t.schema_id
        LEFT JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
        GROUP BY s.name, t.name""").fetchall()
    for schema, table, n in rows:
        out[(schema.lower(), table.lower())] = {"schema": schema, "table": table, "rows": int(n)}
    return out


def load_exceptions():
    if not os.path.exists(EXCEPTIONS):
        return {}
    out = {}
    with open(EXCEPTIONS, encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            key = (row["schema"].strip().lower(), row["table"].strip().lower())
            out[key] = {"code": row["code"].strip(), "reason": row["reason"].strip(),
                        "owner": row.get("owner", "").strip(), "date": row.get("date", "").strip()}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--register", default=REGISTER)
    ap.add_argument("--md", default=REPORT)
    ap.add_argument("--exceptions", default=EXCEPTIONS)
    args = ap.parse_args()

    dev, prod = db_tables(DEV), db_tables(PROD)
    keys = set(dev) | set(prod)
    schemas = {s for s, _ in keys}

    rec = {}
    for k in keys:
        src = dev.get(k) or prod[k]
        rec[k] = {"schema": src["schema"], "table": src["table"],
                  "rows_dev": dev[k]["rows"] if k in dev else None,
                  "rows_prod": prod[k]["rows"] if k in prod else None,
                  "jpa": [], "code_ref": [], "test_ref": [], "mig": [], "mig_insert": [],
                  "mig_drop": [], "py": [], "py_insert": []}

    # ── JPA mappings (@Table / @JoinTable / @CollectionTable / @SecondaryTable) ──
    java = []
    for base, _dirs, files in os.walk(BACKEND):
        for fn in files:
            if fn.endswith(".java"):
                java.append(os.path.join(base, fn))

    for path in java:
        raw = open(path, encoding="utf-8", errors="replace").read()
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        for body in annotation_bodies(raw):   # annotations are not comments; use the raw text
            nm, sc = NAME.search(body), SCHEMA_ATTR.search(body)
            if not nm:
                continue                       # no table name: nothing to resolve
            schema = sc.group(1) if sc else DEFAULT_SCHEMA
            k = (schema.lower(), nm.group(1).lower())
            if k not in rec:                  # mapped in code, absent from BOTH databases
                rec[k] = {"schema": schema, "table": nm.group(1),
                          "rows_dev": None, "rows_prod": None,
                          "jpa": [], "code_ref": [], "test_ref": [], "mig": [],
                          "mig_insert": [], "mig_drop": [], "py": [], "py_insert": []}
                schemas.add(schema.lower())
            if rel not in rec[k]["jpa"]:
                rec[k]["jpa"].append(rel)

    # ── `schema.table` references, comments stripped ────────────────────────
    REF = re.compile(r"\b(" + "|".join(sorted(schemas)) + r")\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)", re.I)
    INS = re.compile(r"INSERT\s+INTO\s+\[?(" + "|".join(sorted(schemas))
                     + r")\]?\s*\.\s*\[?([A-Za-z_][A-Za-z0-9_]*)\]?", re.I)
    # A real DROP, from the statement -- not from the filename. See DROP_APPLIES below.
    DROP = re.compile(r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?\[?(" + "|".join(sorted(schemas))
                      + r")\]?\s*\.\s*\[?([A-Za-z_][A-Za-z0-9_]*)\]?", re.I)

    def note(k, bucket, rel):
        if k in rec and rel not in rec[k][bucket]:
            rec[k][bucket].append(rel)

    for path in java:
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        body = strip_comments(open(path, encoding="utf-8", errors="replace").read())
        bucket = "test_ref" if "/test/" in rel or "\\test\\" in path else "code_ref"
        for m in REF.finditer(body):
            note((m.group(1).lower(), m.group(2).lower()), bucket, rel)

    for fn in sorted(os.listdir(MIGRATIONS)):
        if not fn.endswith(".sql"):
            continue
        body = open(os.path.join(MIGRATIONS, fn), encoding="utf-8", errors="replace").read()
        for m in REF.finditer(body):
            note((m.group(1).lower(), m.group(2).lower()), "mig", fn)
        for m in INS.finditer(body):
            note((m.group(1).lower(), m.group(2).lower()), "mig_insert", fn)
        for m in DROP.finditer(strip_comments(body)):
            note((m.group(1).lower(), m.group(2).lower()), "mig_drop", fn)

    py_files = []
    for d in (ROOT, os.path.join(APPENDIX, "scripts")):
        if os.path.isdir(d):
            py_files += [os.path.join(d, f) for f in os.listdir(d) if f.endswith(".py")]
    for path in py_files:
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        body = open(path, encoding="utf-8", errors="replace").read()
        for m in REF.finditer(body):
            note((m.group(1).lower(), m.group(2).lower()), "py", rel)
        for m in INS.finditer(body):
            note((m.group(1).lower(), m.group(2).lower()), "py_insert", rel)

    # ── classify ────────────────────────────────────────────────────────────
    exc = load_exceptions()
    for k, t in rec.items():
        if k in exc:
            t["code"], t["reason"] = exc[k]["code"], exc[k]["reason"]
            t["source"] = "exceptions-file"
            continue
        t["source"] = "generated"
        dev_rows, prod_rows = t["rows_dev"], t["rows_prod"]
        absent_both = dev_rows is None and prod_rows is None
        rows = (dev_rows or 0) + (prod_rows or 0)
        # A Java reference and a seed-script INSERT are opposite evidence, and the first
        # version of this classifier conflated them under one `touched` flag. That put 16
        # tables — compliance.evidence_items (83 rows), privacy.consents (36),
        # workflow.workflow_* — into UNKNOWN with the reason "referenced by code", while the
        # register showed code_refs = 0 for every one of them. The output contradicted itself,
        # which is how the bug surfaced. A table whose ONLY writer is seed_real_data.py is not
        # alive; it is ballast, and saying so is the finding.
        touched_by_code = bool(t["code_ref"])
        seeded_only = bool(t["py_insert"]) and not touched_by_code and not t["jpa"]
        if absent_both:
            t["code"] = "MAPPED_NO_TABLE"
            t["reason"] = "JPA-mapped in %d file(s); table exists in neither database" % len(t["jpa"])
        elif t["table"].lower() == "flyway_schema_history":
            t["code"] = "INTENTIONAL_TECHNICAL"; t["reason"] = "Flyway bookkeeping"
        elif dev_rows is None:
            t["code"] = "PROD_ONLY_LEFTOVER"
            # ⚠ TWO generations of wrongness here, both worth keeping as a comment because the
            # second looked like the fix for the first.
            #   (1) It asserted "no migration removes it" — already false when first printed:
            #       V337 was on disk dropping all three risk.kri_* tables.
            #   (2) The repair derived the answer from the migration FILENAME (`"drop" in name`),
            #       which credits any file whose name happens to contain "drop" and misses
            #       `V340__retire_legacy.sql`; and it then said the drop "applies on the next
            #       Flyway run" while listing **V247**, which is numbered below the baseline and
            #       therefore can NEVER apply. That is F2-65 itself, restated wrongly by the
            #       scanner that reports F2-65.
            # Now: real DROP TABLE statements, split by the baseline, because only one side of
            # that line is a pending retirement — the other is the defect.
            will = sorted(m for m in t["mig_drop"] if mig_version(m) > FLYWAY_BASELINE)
            never = sorted(m for m in t["mig_drop"] if mig_version(m) <= FLYWAY_BASELINE)
            if will:
                how = ("dropped by %s, above the V%d baseline, so it applies on the next Flyway run"
                       % (", ".join(will), FLYWAY_BASELINE))
                if never:
                    how += (" (%s also drops it but is at or below the baseline and can never run)"
                            % ", ".join(never))
            elif never:
                how = ("the only DROP is %s, at or below the V%d baseline — Flyway records it as "
                       "applied without running it, so this table is retired on paper only (F2-65)"
                       % (", ".join(never), FLYWAY_BASELINE))
            else:
                how = "no migration in this repo drops it"
            t["reason"] = "absent from dev, %d row(s) in prod; %s" % (prod_rows or 0, how)
        elif t["jpa"]:
            t["code"] = "OPERATING" if rows else "WIRED_UNPROVEN"
            # NOT "never exercised". House rule 5 deletes test rows with CHARINDEX after every
            # probe, so an exercised table reads 0 afterwards — bcm.crisis_comms_cascade was
            # dispatched twice today and cleaned up. And prod is a restore of dev, so the two row
            # counts are ONE observation, not two. Only a clean-tenant probe settles usage.
            t["reason"] = ("mapped and carrying data" if rows
                           else "mapped; no rows in either database today (probes clean up after "
                                "themselves and prod is a restore of dev, so this is one "
                                "observation, not two)")
        elif seeded_only:
            t["code"] = "SEED_ONLY"
            t["reason"] = ("no JPA mapping and no Java reference; the only writer is a seed script "
                           "(%s) — %d row(s) of ballast, present in prod because it was restored "
                           "from a dev dump" % (", ".join(t["py_insert"][:2]), rows))
        elif rows and not touched_by_code:
            t["code"] = "LEGACY"
            t["reason"] = ("no JPA mapping, no Java reference and no seed insert, yet %d row(s) — "
                           "rows arrived from a migration or by hand" % rows)
        elif not rows and not touched_by_code:
            t["code"] = "ORPHAN_CONFIRMED"
            t["reason"] = "no JPA mapping, no Java reference, 0 rows in both databases"
        else:
            t["code"] = "UNKNOWN"
            t["reason"] = ("no JPA mapping but referenced by %d Java file(s) — native SQL or a "
                           "join table; needs a decision" % len(t["code_ref"]))

    # ── the exceptions file must not rot ────────────────────────────────────
    stale = [k for k in exc if k not in rec]
    bad_code = [(k, v["code"]) for k, v in exc.items() if v["code"] not in CODES]

    # ── emit ────────────────────────────────────────────────────────────────
    with open(args.register, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["schema", "table", "code", "source", "rows_dev", "rows_prod",
                    "jpa_files", "code_refs", "test_refs", "migrations", "mig_inserts",
                    "py_refs", "py_inserts", "reason"])
        for k in sorted(rec):
            t = rec[k]
            w.writerow([t["schema"], t["table"], t["code"], t["source"],
                        "ABSENT" if t["rows_dev"] is None else t["rows_dev"],
                        "ABSENT" if t["rows_prod"] is None else t["rows_prod"],
                        len(t["jpa"]), len(t["code_ref"]), len(t["test_ref"]),
                        len(t["mig"]), len(t["mig_insert"]), len(t["py"]), len(t["py_insert"]),
                        t["reason"]])

    by_code = {}
    for t in rec.values():
        by_code.setdefault(t["code"], []).append(t)

    out = []
    def emit(s=""):
        print(s); out.append(s)

    emit("# Table disposition — generated")
    emit()
    emit("Generated by `Appendix/scripts/table_xref.py`. **Do not edit.** The only hand-written")
    emit("input is `table_disposition_exceptions.csv`, validated in both directions.")
    emit()
    emit("| | count |")
    emit("|---|---|")
    emit("| tables in dev / prod | %d / %d |" % (len(dev), len(prod)))
    emit("| rows in the register | %d |" % len(rec))
    emit("| classified from the exceptions file | %d |"
         % len([t for t in rec.values() if t["source"] == "exceptions-file"]))
    emit()
    emit("| code | count | meaning |")
    emit("|---|---|---|")
    meaning = {
        "MAPPED_NO_TABLE": "**code maps a table that exists in neither database** — the worst class",
        "OPERATING": "mapped and carrying data",
        "WIRED_UNPROVEN": "mapped, 0 rows in both — shipped, never exercised",
        "LEGACY": "no mapping, no Java reference, no seed insert, yet holds rows",
        "SEED_ONLY": "**the only writer is a seed script** — ballast, shipped to prod by the restore",
        "ORPHAN_CONFIRMED": "no mapping, no reference, no rows",
        "PROD_ONLY_LEFTOVER": "in prod only; no migration will remove it",
        "INTENTIONAL_TECHNICAL": "infrastructure by design",
        "DORMANT": "empty by design (rare-event or awaiting connector config)",
        "UNKNOWN": "referenced by code without a mapping — needs a decision",
    }
    for code in sorted(by_code, key=lambda c: -len(by_code[c])):
        emit("| `%s` | %d | %s |" % (code, len(by_code[code]), meaning.get(code, "")))
    emit()
    emit("Codes deliberately **not** assigned here, because a data-plane scan cannot justify")
    emit("them: %s. The route plane is owned by" % ", ".join("`%s`" % c for c in NOT_ASSIGNABLE_HERE))
    emit("`routeReachability.test.ts` (F2-07b).")
    emit()

    for code in ("MAPPED_NO_TABLE", "PROD_ONLY_LEFTOVER", "ORPHAN_CONFIRMED", "SEED_ONLY",
                 "LEGACY", "WIRED_UNPROVEN", "UNKNOWN"):
        items = sorted(by_code.get(code, []), key=lambda t: (t["schema"], t["table"]))
        if not items:
            continue
        emit("## %s (%d)" % (code, len(items)))
        emit()
        emit("| table | dev | prod | jpa | code refs | reason |")
        emit("|---|---|---|---|---|---|")
        for t in items:
            emit("| `%s.%s` | %s | %s | %d | %d | %s |"
                 % (t["schema"], t["table"],
                    "ABSENT" if t["rows_dev"] is None else t["rows_dev"],
                    "ABSENT" if t["rows_prod"] is None else t["rows_prod"],
                    len(t["jpa"]), len(t["code_ref"]), t["reason"]))
        emit()

    with open(args.md, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")

    # ── gates ───────────────────────────────────────────────────────────────
    print()
    print("wrote %s" % args.register)
    print("wrote %s" % args.md)
    problems = 0

    if stale:
        problems += 1
        print("\nERROR: %d exception(s) name a table that exists in neither database:" % len(stale))
        for s_, t_ in stale:
            print("   %s.%s — remove it, or the allowlist is covering nothing" % (s_, t_))
    if bad_code:
        problems += 1
        print("\nERROR: exception(s) use a code outside %s: %s" % (list(CODES), bad_code))

    # anti-vacuity
    print("\nanti-vacuity — the known positives this scan must rediscover:")
    for needle, why in MUST_FIND.items():
        if "." in needle:
            s_, t_ = needle.split(".", 1)
            hit = (s_, t_) in rec
        else:
            hit = any(k[0] == needle for k in rec)
        print("   [%s] %-28s %s" % ("OK " if hit else "MISS", needle, why))
        if not hit:
            problems += 1
    if not by_code:
        problems += 1
        print("   [MISS] the register is empty")

    print("\n%s" % ("PROBLEMS: %d" % problems if problems else "OK — register generated, exceptions valid, known positives found"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
