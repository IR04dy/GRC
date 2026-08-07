# Re-measure every mechanical claim in Appendix/MEASURED_BASELINE.md, and say what drifted.
#
# WHY THIS EXISTS (F2-69). MEASURED_BASELINE.md is the artefact that enforces the Bible's standing
# rule — "no claim enters any document unless it is reproducible from this baseline" — and it went
# stale by a week of remediation without anything noticing. Worse, two of its numbers were never
# right: the reproduction scripts count annotations with a SUBSTRING test over RAW source, so
#
#     @EntityGraph       counts as @Entity        (iam/repository/UserRepository.java)
#     " * @Entity …"     counts as @Entity        (Javadoc prose in three files: risk_lc/package-info
#                                                 and two SCM DTO files)
#
# Four phantoms inside `module/`, which is the census scope. `common/model/BaseEntity.java`
# (@EntityListeners) is a fifth real phantom but sits OUTSIDE that scope, so it never contributed to
# the published 417 — a distinction worth keeping, because conflating the two scopes is how a finding
# gets filed with the wrong count.
#
# ...which is RC-20, the regex-over-Javadoc failure, inside the document whose own rule is that
# nothing enters unmeasured. Regenerating from the old scripts would have re-published the wrong
# number, so this script measures strictly and reports BOTH readings side by side: the strict count
# is the truth, and the loose count is kept only to explain where the published figure came from.
#
# Counting rules (identical to the doc's stated rules except where marked STRICT):
#   LoC       raw line count of .java under the module package, comments and blanks included.
#   Entities  STRICT: `@Entity` with no identifier character after it, over COMMENT-STRIPPED source.
#   Endpoints @(Get|Post|Put|Patch|Delete|Request)Mapping over COMMENT-STRIPPED source, minus one
#             class-level @RequestMapping("…") per file that has one.
#   Writes    @(Post|Put|Patch|Delete)Mapping, comment-stripped.
#   Tests     @Test|@ParameterizedTest|@RepeatedTest under src/test, comment-stripped, attributed by
#             package path, else by the module the file imports most.
#
# Usage:
#   py baseline_census.py                 # full report + drift vs the published figures
#   py baseline_census.py --no-db         # skip SQL Server (tree-only measurements)
#   py baseline_census.py --check         # exit 1 if any load-bearing figure drifted
#   py baseline_census.py --json out.json
import argparse
import collections
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))


def _paths(root):
    """Re-point every scan at `root`. --root lets the census run against an EXPORTED OLD TREE, which
    is how you tell "the number went stale" from "the number was never reproducible at the commit the
    document names". Both happened here."""
    global ROOT, MODJ, MAINJ, TESTJ, MIG, FE
    ROOT = root
    MODJ = os.path.join(root, "tss-grc-backend", "src", "main", "java", "com", "tss", "grc", "module")
    MAINJ = os.path.join(root, "tss-grc-backend", "src", "main", "java")
    TESTJ = os.path.join(root, "tss-grc-backend", "src", "test", "java", "com", "tss", "grc")
    MIG = os.path.join(root, "tss-grc-backend", "src", "main", "resources", "db", "migration", "sqlserver")
    FE = os.path.join(root, "tss-grc-ui-v2", "src")


_paths(ROOT)

CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=%s;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
DEV_DB = "TSS_GRC"
PROD_DB = "TSS_GRC_Deployment"

# ── the published figures this script exists to check ────────────────────────────────────────────
# Every entry is "what MEASURED_BASELINE.md says today". Updating the doc means updating this dict
# in the same commit; that pairing is the whole point — a number with no checker goes stale silently.
PUBLISHED_AS_OF = "2026-08-05"          # ← bump ONLY when the doc is regenerated
PUBLISHED = {
    "module_packages": 59,
    "module_loc": 204_611,
    "entities_strict": 419,             # FILES declaring an entity (the doc published 417: a substring
    "entity_decls": 432,                # count). Entity CLASSES — 13 more, all in data_officer.
    "endpoints": 2_213,
    "writes": 1_239,
    "main_preauthorize": 2_201,
    "perm_modules": 37,
    "packages_no_haspermission": 12,
    "module_tests": 3_199,
    "suite_tests": 3_398,
    # F2-03 floored all seven Tier-1 regulator-facing modules (2026-08-05/06), so this
    # fell 15 -> 8. The --check mode caught the drift the moment it was first run in
    # anger, which is F2-19b in one line: the guard existed and nothing ran it.
    "zero_test_packages": 7,
    "jpa_schemas": 50,                  # code census; the DB holds 51 — `audit` is unknown to JPA
    "db_schemas_dev": 51,
    "migration_files": 361,
    "migration_head": 373,
    "flyway_baseline": 292,             # read from prod's flyway_schema_history, never from prose
    "fe_pages": 340,          # F2-142 retired 3 fabricated pages
    "fe_page_folders": 111,   # each was the only file in its folder, so -3 folders too
    "fe_routes": 329,
    "fe_nav_entries": 194,
    "mock_import_statements": 30,
    "mock_import_files": 26,
    "mock_files": 15,
    "mock_file_lines": 3_550,
}
# Figures that must match exactly for --check, mirrored by MeasuredBaselineFreshnessTest in the backend
# suite. The rest are reported but NOT gated: LoC, test counts and coupling edges move on almost every
# commit, and a ratchet that fires constantly is one somebody disables — F2-70's lesson.
GATED = ("module_packages", "entities_strict", "entity_decls", "jpa_schemas", "db_schemas_dev",
         "migration_files", "migration_head", "flyway_baseline", "perm_modules",
         "packages_no_haspermission", "zero_test_packages", "fe_pages", "fe_routes")

# ── sound comment stripping ─────────────────────────────────────────────────────────────────────
# One ordered alternation, so a construct can never be entered from inside another. Getting this
# wrong in the OTHER direction is what F2-68(c) found in the frontend scanner: strip block comments
# first and a `//` line containing a block-OPEN marker swallows the file to the next CLOSE marker.
JAVA_TOKENS = re.compile(
    r'"""(?:[^"\\]|\\.|"(?!""))*"""'      # text block
    r"|\"(?:[^\"\\\n]|\\.)*\""            # string
    r"|'(?:[^'\\\n]|\\.)*'"               # char
    r"|/\*[\s\S]*?\*/"                    # block comment
    r"|//[^\n]*")                         # line comment


def strip_java(src):
    """Blank out comments, keeping literals (which may legitimately contain '@Entity' etc.)."""
    return JAVA_TOKENS.sub(lambda m: "" if m.group(0)[0] == "/" else m.group(0), src)


TS_TOKENS = re.compile(
    r"'(?:[^'\\\n]|\\.)*'"
    r'|"(?:[^"\\\n]|\\.)*"'
    r"|`(?:[^`\\]|\\.)*`"
    r"|/\*[\s\S]*?\*/"
    r"|//[^\n]*")


def strip_ts(src):
    return TS_TOKENS.sub(lambda m: "" if m.group(0)[0] == "/" else m.group(0), src)


ENTITY_STRICT = re.compile(r"@Entity(?![A-Za-z0-9_])")
ENTITY_LOOSE = re.compile(r"@Entity")          # what coupling.py used to do, on raw source
MAPPING = re.compile(r"@(?:Get|Post|Put|Patch|Delete|Request)Mapping")
CLASS_RM = re.compile(r'@RequestMapping\s*\(\s*"')
WRITE = re.compile(r"@(?:Post|Put|Patch|Delete)Mapping")
PREAUTH = re.compile(r"@PreAuthorize")
HASPERM = re.compile(r"hasPermission\(\s*'([A-Z_]+)'\s*,\s*'([A-Z_]+)'\s*\)")
TESTM = re.compile(r"@(?:Test|ParameterizedTest|RepeatedTest)\b")
IMPORT = re.compile(r"^import\s+(?:static\s+)?com\.tss\.grc\.([A-Za-z0-9_.]+);", re.M)
SCHEMA = re.compile(r'schema\s*=\s*"([^"]+)"')


def read(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def walk(root, *exts):
    for dp, _, fns in os.walk(root):
        for fn in fns:
            if fn.endswith(exts):
                yield os.path.join(dp, fn)


# ── backend module census ───────────────────────────────────────────────────────────────────────
def backend():
    mods = sorted(d for d in os.listdir(MODJ) if os.path.isdir(os.path.join(MODJ, d)))
    stat = {m: dict(loc=0, files=0, entities=0, entities_loose=0, entity_decls=0, endpoints=0,
                    writes=0, preauth=0, schemas=set(), perms=set(), out=collections.Counter(),
                    tests=0, phantom=[]) for m in mods}
    for m in mods:
        for path in walk(os.path.join(MODJ, m), ".java"):
            raw = read(path)
            code = strip_java(raw)
            s = stat[m]
            s["loc"] += raw.count("\n") + 1
            s["files"] += 1
            # `entities` counts FILES, which is what the published 417 counted (coupling.py did
            # `if "@Entity" in src: entities += 1`). `entity_decls` counts occurrences — higher,
            # because a few files declare more than one @Entity class.
            strict = len(ENTITY_STRICT.findall(code))
            loose = len(ENTITY_LOOSE.findall(raw))
            s["entities"] += 1 if strict else 0
            s["entities_loose"] += 1 if loose else 0
            s["entity_decls"] += strict
            if loose and not strict:
                s["phantom"].append(os.path.relpath(path, MODJ).replace("\\", "/"))
            s["endpoints"] += len(MAPPING.findall(code)) - (1 if CLASS_RM.search(code) else 0)
            s["writes"] += len(WRITE.findall(code))
            s["preauth"] += len(PREAUTH.findall(code))
            for tbl in re.findall(r"@Table\s*\(([^)]*)\)", code, re.S):
                s["schemas"].update(SCHEMA.findall(tbl))
            for mod_, _act in HASPERM.findall(code):
                s["perms"].add(mod_)
            for path_ in IMPORT.findall(code):
                if path_.startswith("module."):
                    other = path_.split(".")[1]
                    if other != m and other in stat:
                        s["out"][other] += 1
    # tests, attributed as the doc documents
    suite_tests = 0
    for path in walk(TESTJ, ".java"):
        code = strip_java(read(path))
        n = len(TESTM.findall(code))
        suite_tests += n
        rel = os.path.relpath(path, TESTJ).replace("\\", "/")
        owner = rel.split("/")[1] if rel.startswith("module/") else None
        if owner not in stat:
            hits = collections.Counter()
            for p in IMPORT.findall(code):
                if p.startswith("module."):
                    o = p.split(".")[1]
                    if o in stat:
                        hits[o] += 1
            owner = hits.most_common(1)[0][0] if hits else None
        if owner in stat:
            stat[owner]["tests"] += n

    fan_in = collections.Counter()
    for m in mods:
        for o in stat[m]["out"]:
            fan_in[o] += 1

    # permission modules named by ANY guard in main (not just module packages), and the whole-main
    # @PreAuthorize count — which is what the published 2,268 measured, so it is measured the same way
    all_perms = collections.Counter()
    main_preauth = 0
    for path in walk(MAINJ, ".java"):
        code = strip_java(read(path))
        main_preauth += len(PREAUTH.findall(code))
        for mod_, _act in HASPERM.findall(code):
            all_perms[mod_] += 1

    jpa_schemas = set()
    for s in stat.values():
        jpa_schemas |= s["schemas"]

    return dict(mods=mods, stat=stat, fan_in=fan_in, suite_tests=suite_tests,
                all_perms=all_perms, jpa_schemas=sorted(jpa_schemas), main_preauth=main_preauth)


# ── frontend census ─────────────────────────────────────────────────────────────────────────────
def frontend():
    pages, folders, mock_stmts, mock_files, lib_files = 0, set(), [], set(), 0
    mock_shipped = []
    for path in walk(FE, ".ts", ".tsx"):
        rel = os.path.relpath(path, FE).replace("\\", "/")
        raw = read(path)
        code = strip_ts(raw)
        if rel.startswith("pages/") and rel.endswith(".tsx"):
            pages += 1
            folders.add("/".join(rel.split("/")[1:-1]) or "<root>")
        if rel.startswith("lib/"):
            lib_files += 1
        if "mock" in os.path.basename(rel).lower():
            mock_shipped.append((rel, raw.count("\n") + 1))
        for m in re.finditer(r"(?:^|\n)\s*(?:import|export)[^;\n]*?from\s+['\"]([^'\"]+)['\"]", code):
            if "mock" in m.group(1).lower():
                mock_stmts.append((rel, m.group(1)))
                mock_files.add(rel)
    app = strip_ts(read(os.path.join(FE, "App.tsx")))
    routes = len(re.findall(r"<Route[^>]*path=", app))
    sidebar = read(os.path.join(FE, "components", "layout", "Sidebar.tsx"))
    nav = len(re.findall(r"(?:to|path|href)\s*:\s*['\"]/", strip_ts(sidebar)))
    # Test-case CALL SITES, not the runner's case count: `describe.each`/`it.each` expand at runtime,
    # so a static count can never equal `vitest run`. The authoritative FE test count is the runner's.
    fe_sites, fe_test_files = 0, 0
    for path in walk(FE, ".ts", ".tsx"):
        rel = os.path.relpath(path, FE).replace("\\", "/")
        if "__tests__" not in rel and not re.search(r"\.(?:test|spec)\.tsx?$", rel):
            continue
        fe_test_files += 1
        fe_sites += len(re.findall(r"(?:^|[\s;{(])(?:it|test)(?:\.(?:each|skip|only|todo|fails)\b[^(]*)?\s*\(",
                                   strip_ts(read(path))))
    return dict(pages=pages, folders=len(folders), routes=routes, nav=nav,
                mock_stmts=mock_stmts, mock_import_files=sorted(mock_files),
                mock_shipped=sorted(mock_shipped), lib_files=lib_files,
                fe_test_sites=fe_sites, fe_test_files=fe_test_files)


# ── migrations ──────────────────────────────────────────────────────────────────────────────────
def migrations():
    files = sorted(f for f in os.listdir(MIG) if re.match(r"^V\d", f) and f.endswith(".sql"))
    nums = sorted(int(re.match(r"V(\d+)", f).group(1)) for f in files)
    have = set(nums)
    gaps = [n for n in range(nums[0], nums[-1]) if n not in have]
    size = sum(os.path.getsize(os.path.join(MIG, f)) for f in files)
    dupes = [n for n, c in collections.Counter(nums).items() if c > 1]
    return dict(files=len(files), lo=nums[0], hi=nums[-1], gaps=gaps, bytes=size,
                dupes=sorted(dupes), nums=nums)


# ── database ────────────────────────────────────────────────────────────────────────────────────
def database():
    try:
        import pyodbc
    except ImportError:
        return {"error": "pyodbc not installed"}
    out = {}
    for label, db in (("dev", DEV_DB), ("prod", PROD_DB)):
        try:
            cur = pyodbc.connect(CONN % db, timeout=8).cursor()
        except Exception as exc:                                     # noqa: BLE001
            out[label] = {"error": str(exc)[:120]}
            continue
        rows = cur.execute("""
            SELECT s.name, t.name, ISNULL(SUM(p.rows), 0)
            FROM sys.tables t
            JOIN sys.schemas s ON s.schema_id = t.schema_id
            LEFT JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
            GROUP BY s.name, t.name""").fetchall()
        tables = {"%s.%s" % (r[0], r[1]): int(r[2]) for r in rows}
        schemas = sorted({r[0] for r in rows})
        fly = None
        try:
            hist = cur.execute("SELECT version, script, type, success FROM platform.flyway_schema_history "
                               "WHERE version IS NOT NULL").fetchall()
            vers = sorted(int(h[0]) for h in hist if str(h[0]).isdigit())
            base = [h for h in hist if str(h[2]).upper().startswith("BASELINE")]
            fly = dict(applied=len(hist), head=vers[-1] if vers else None,
                       baseline=int(base[0][0]) if base else None,
                       failed=[h[0] for h in hist if not h[3]])
        except Exception:                                            # noqa: BLE001
            fly = None
        out[label] = dict(schemas=schemas, n_schemas=len(schemas), n_tables=len(tables),
                          tables=tables, flyway=fly)
    # clone evidence: how much of prod is byte-for-byte dev
    if "error" not in out.get("dev", {}) and "error" not in out.get("prod", {}):
        d, p = out["dev"]["tables"], out["prod"]["tables"]
        shared = set(d) & set(p)
        same = [t for t in shared if d[t] == p[t]]
        out["clone"] = dict(shared=len(shared), identical_rows=len(same),
                            dev_only=sorted(set(d) - set(p)), prod_only=sorted(set(p) - set(d)))
    return out


# ── report ──────────────────────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-db", action="store_true")
    ap.add_argument("--check", action="store_true", help="exit 1 when a gated figure drifted")
    ap.add_argument("--json")
    ap.add_argument("--root", help="measure an exported tree instead of this one (provenance check)")
    ap.add_argument("--table", action="store_true", help="emit the doc's §1/§3/§5 tables as markdown")
    args = ap.parse_args()
    if args.root:
        _paths(os.path.abspath(args.root))

    be, fe, mg = backend(), frontend(), migrations()
    db = {} if args.no_db else database()
    stat, mods = be["stat"], be["mods"]

    measured = {
        "module_packages": len(mods),
        "module_loc": sum(s["loc"] for s in stat.values()),
        "entities_strict": sum(s["entities"] for s in stat.values()),
        "entities_loose": sum(s["entities_loose"] for s in stat.values()),
        "endpoints": sum(s["endpoints"] for s in stat.values()),
        "writes": sum(s["writes"] for s in stat.values()),
        "preauthorize": sum(s["preauth"] for s in stat.values()),
        "perm_modules": len(be["all_perms"]),
        "packages_no_haspermission": sum(1 for s in stat.values() if not s["perms"]),
        "module_tests": sum(s["tests"] for s in stat.values()),
        "suite_tests": be["suite_tests"],
        "zero_test_packages": sum(1 for s in stat.values() if s["tests"] == 0),
        "jpa_schemas": len(be["jpa_schemas"]),
        "migration_files": mg["files"],
        "migration_head": mg["hi"],
        "fe_pages": fe["pages"],
        "fe_page_folders": fe["folders"],
        "fe_routes": fe["routes"],
        "fe_nav_entries": fe["nav"],
        "mock_import_statements": len(fe["mock_stmts"]),
        "mock_import_files": len(fe["mock_import_files"]),
        "mock_files": len(fe["mock_shipped"]),
        "mock_file_lines": sum(n for _f, n in fe["mock_shipped"]),
        "fe_test_sites": fe["fe_test_sites"],
        "main_preauthorize": be["main_preauth"],
        "entity_decls": sum(s["entity_decls"] for s in stat.values()),
    }
    if db.get("dev", {}).get("n_schemas"):
        measured["db_schemas_dev"] = db["dev"]["n_schemas"]
    if db.get("prod", {}).get("flyway"):
        measured["flyway_baseline"] = db["prod"]["flyway"]["baseline"]

    print("=" * 96)
    print("MEASURED BASELINE CENSUS — tree @ %s" % ROOT)
    print("=" * 96)
    w = max(len(k) for k in set(PUBLISHED) | set(measured))
    print(f"{'figure':{w}}  {'published':>10}  {'measured':>10}   drift")
    drift = []
    for k in sorted(set(PUBLISHED) | set(measured)):
        pub, mea = PUBLISHED.get(k), measured.get(k)
        if mea is None:
            print(f"{k:{w}}  {pub!s:>10}  {'(not measured)':>10}")
            continue
        if pub is None:
            print(f"{k:{w}}  {'—':>10}  {mea:>10,}   new")
            continue
        delta = mea - pub
        mark = "" if delta == 0 else ("  <== DRIFT" if k in GATED else "  (ungated)")
        if delta:
            drift.append((k, pub, mea))
        print(f"{k:{w}}  {pub:>10,}  {mea:>10,}   {delta:+,}{mark}")

    print("\n-- entity count: strict vs the substring test the old script used " + "-" * 30)
    phantoms = [(m, p) for m in mods for p in stat[m]["phantom"]]
    print("   strict @Entity: %d      loose (substring, raw source): %d      phantoms: %d"
          % (measured["entities_strict"], measured["entities_loose"], len(phantoms)))
    for m, p in phantoms:
        raw = read(os.path.join(MODJ, p.replace("/", os.sep)))
        why = ("@EntityListeners" if "@EntityListeners" in raw else
               "@EntityGraph" if "@EntityGraph" in raw else "comment/Javadoc prose")
        print(f"      {p:64} {why}")

    print("\n-- migrations " + "-" * 80)
    print("   files=%d  range V%d..V%d  bytes=%s  gaps=%s%s"
          % (mg["files"], mg["lo"], mg["hi"], f"{mg['bytes']:,}", mg["gaps"][:14],
             ("  DUPLICATE VERSIONS: %s" % mg["dupes"]) if mg["dupes"] else ""))

    # What the deployed Flyway baseline actually decides. Anything at or below it is recorded as
    # applied without ever executing — so for those versions prod's schema came from the restored
    # dump, not from the migration. This is the number RC-A rests on, and the doc had it wrong.
    fly = (db.get("prod") or {}).get("flyway") if db else None
    if fly and fly.get("baseline"):
        base, head = fly["baseline"], fly["head"]
        nums = mg["nums"]
        below = [n for n in nums if n <= base]
        between = [n for n in nums if base < n <= head]
        pending = [n for n in nums if n > head]
        applied_versions = set(range(base + 1, head + 1))
        print("   prod baseline V%d, head V%d:" % (base, head))
        print("      %3d file(s) at or below the baseline  -> NEVER executed in prod (dump provenance)"
              % len(below))
        print("      %3d file(s) between baseline and head -> executed" % len(between))
        print("      %3d file(s) above the head            -> PENDING the next jar: %s"
              % (len(pending), ", ".join("V%d" % n for n in pending) or "none"))
        ghost = sorted(v for v in applied_versions if v not in set(nums)
                       and v not in set(mg["gaps"]))
        if ghost:
            print("      ⚠ applied in prod with no file on disk: %s" % ghost)

    if db and "error" not in db:
        for label in ("dev", "prod"):
            d = db.get(label, {})
            if "error" in d:
                print("\n-- %s DB: %s" % (label, d["error"]))
                continue
            f = d.get("flyway")
            print("\n-- %s DB: %d schemas-with-tables, %d tables%s"
                  % (label, d["n_schemas"], d["n_tables"],
                     ("   flyway: baseline=%s head=%s applied=%d failed=%s"
                      % (f["baseline"], f["head"], f["applied"], f["failed"] or "none")) if f
                     else "   flyway: table absent (Flyway off)"))
        if "clone" in db:
            c = db["clone"]
            print("\n-- prod-vs-dev: %d shared tables, %d with IDENTICAL row counts (%.0f%%); "
                  "dev-only=%d prod-only=%d"
                  % (c["shared"], c["identical_rows"], 100.0 * c["identical_rows"] / max(c["shared"], 1),
                     len(c["dev_only"]), len(c["prod_only"])))
            if c["prod_only"]:
                print("   prod-only: %s" % ", ".join(c["prod_only"][:8]))
            if c["dev_only"]:
                print("   dev-only:  %s" % ", ".join(c["dev_only"][:8]))
        jpa = set(be["jpa_schemas"])
        dev_schemas = set(db.get("dev", {}).get("schemas", []))
        if dev_schemas:
            sysish = {"dbo", "guest", "sys", "INFORMATION_SCHEMA"}
            print("\n-- schemas JPA does not know: %s"
                  % (sorted(dev_schemas - jpa - sysish) or "none"))
            print("   schemas JPA declares that the DB lacks: %s" % (sorted(jpa - dev_schemas) or "none"))

    print("\n-- module census (strict) " + "-" * 68)
    print(f"{'#':>3} {'module':22}{'LoC':>7}{'ent':>5}{'endp':>6}{'wr':>5}{'tests':>6}"
          f"{'out':>5}{'in':>4}  perms")
    for i, m in enumerate(sorted(mods, key=lambda x: -stat[x]["loc"]), 1):
        s = stat[m]
        print(f"{i:>3} {m:22}{s['loc']:7,}{s['entities']:5}{s['endpoints']:6}{s['writes']:5}"
              f"{s['tests']:6}{len(s['out']):5}{be['fan_in'][m]:4}  "
              f"{','.join(sorted(s['perms']))[:30] or 'ROLE/none'}")

    zero = [m for m in sorted(mods, key=lambda x: -stat[x]["loc"]) if stat[m]["tests"] == 0]
    print("\n   zero-test packages: %d  (%s LoC, %.1f%% of module code)"
          % (len(zero), f"{sum(stat[m]['loc'] for m in zero):,}",
             100.0 * sum(stat[m]["loc"] for m in zero) / measured["module_loc"]))
    print("   " + ", ".join(zero))

    if args.json:
        payload = dict(measured=measured, published=PUBLISHED, published_as_of=PUBLISHED_AS_OF,
                       drift=drift, migrations={k: v for k, v in mg.items() if k != "nums"},
                       phantoms=[p for _m, p in phantoms], fe={k: v for k, v in fe.items()},
                       modules={m: {k: (sorted(v) if isinstance(v, set) else
                                        dict(v) if isinstance(v, collections.Counter) else v)
                                    for k, v in stat[m].items()} for m in mods},
                       db={k: {kk: vv for kk, vv in v.items() if kk != "tables"}
                           for k, v in db.items() if isinstance(v, dict)} if db else {})
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=1, default=str)
        print("\n   wrote %s" % args.json)

    if args.table:
        fe_folders = {d.lower() for d in os.listdir(os.path.join(FE, "pages"))
                      if os.path.isdir(os.path.join(FE, "pages", d))}
        print("\n-- §1 module census, as markdown " + "-" * 60)
        print("| # | Module | LoC | Ent | Endpts | Writes | Tests | out | edges | in | Auth style | FE | Schema |")
        print("|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|:--:|---|")
        for i, m in enumerate(sorted(mods, key=lambda x: -stat[x]["loc"]), 1):
            s = stat[m]
            low = m.lower()
            fe_flag = "✅" if (low in fe_folders or low.replace("_", "-") in fe_folders
                              or low.replace("_", "") in fe_folders) else "—"
            perms = sorted(s["perms"])
            auth = ("**ROLE**" if not perms else
                    "multi" if len(perms) > 2 else ",".join(perms))
            # Same foreign-schema rule as §3 below, so the two sections cannot disagree — they did
            # while this used a looser prefix test, which quietly un-flagged `workflow_submission`.
            schemas = ",".join(("⚠ `%s`" % sc) if (sc != m and not sc.startswith(m)) else ("`%s`" % sc)
                               for sc in sorted(s["schemas"])) or "—"
            print("| %d | `%s` | %s | %d | %d | %d | %s | %d | %d | %s | %s | %s | %s |"
                  % (i, m, f"{s['loc']:,}", s["entities"], s["endpoints"], s["writes"],
                     ("**%d**" % s["tests"]) if s["tests"] == 0 else str(s["tests"]),
                     len(s["out"]), sum(s["out"].values()), be["fan_in"][m], auth, fe_flag, schemas))

        print("\n-- §3 modules persisting into a schema they do not own " + "-" * 30)
        for m in sorted(mods):
            foreign = {sc for sc in stat[m]["schemas"] if sc != m and not sc.startswith(m)}
            if foreign:
                print("   %-22s owns=%s  FOREIGN=%s  (entity files: %d)"
                      % (m, sorted(stat[m]["schemas"]), sorted(foreign), stat[m]["entities"]))

        print("\n-- §5.1 top mock importers " + "-" * 62)
        by_file = collections.Counter(f for f, _mod in fe["mock_stmts"])
        for f, n in by_file.most_common(12):
            print("   %2d  %s" % (n, f))
        print("\n-- §5 mock files still shipped: %d files / %s lines"
              % (len(fe["mock_shipped"]), f"{sum(n for _f, n in fe['mock_shipped']):,}"))

    gated_drift = [d for d in drift if d[0] in GATED]
    if gated_drift:
        print("\n⚠ %d gated figure(s) drifted from MEASURED_BASELINE.md:" % len(gated_drift))
        for k, pub, mea in gated_drift:
            print("     %-28s doc says %s, tree says %s" % (k, pub, mea))
        print("   Update the doc AND the PUBLISHED dict at the top of this script in the same commit.")
    if args.check:
        sys.exit(1 if gated_drift else 0)


if __name__ == "__main__":
    main()
