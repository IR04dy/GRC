# Measure the real module graph of the TSS GRC backend + FE mock debt.
# Output: JSON + markdown tables for the Bible's Module Independence Matrix.
#
# ⚠ F2-69 corrected the entity count here. This script used to detect entities with
# `if "@Entity" in src` — a SUBSTRING test over RAW source, so `@EntityListeners`, `@EntityGraph` and
# Javadoc prose all counted, and MEASURED_BASELINE.md published 417 for a tree with 413. It now uses
# the strict, comment-stripped predicate from baseline_census.py, and reports entity CLASSES as well
# as entity FILES — one `@Entity` per file is not one entity per file (`data_officer/entity/Entities.java`
# declares 14). Prefer `baseline_census.py` for headline figures; this script exists for the coupling
# graph and for `coupling.json`, which verify2.py consumes.
import os, re, json, collections

from baseline_census import strip_java, ENTITY_STRICT

ROOT = r"D:\AYMAN\GRC"
MOD  = os.path.join(ROOT, r"tss-grc-backend\src\main\java\com\tss\grc\module")
TEST = os.path.join(ROOT, r"tss-grc-backend\src\test\java\com\tss\grc")
FE   = os.path.join(ROOT, r"tss-grc-ui-v2\src")

IMPORT   = re.compile(r"^import\s+(static\s+)?com\.tss\.grc\.([A-Za-z0-9_.]+);", re.M)
TABLE    = re.compile(r'@Table\s*\(([^)]*)\)', re.S)
SCHEMA   = re.compile(r'schema\s*=\s*"([^"]+)"')
MAPPING  = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping')
PREAUTH  = re.compile(r"hasPermission\(\s*'([A-Z_]+)'\s*,\s*'([A-Z_]+)'\s*\)")
TESTM    = re.compile(r'@(Test|ParameterizedTest|RepeatedTest)\b')

mods = sorted(d for d in os.listdir(MOD) if os.path.isdir(os.path.join(MOD, d)))
stat = {m: dict(loc=0, files=0, entities=0, entity_classes=0, controllers=0, endpoints=0, repos=0,
                services=0, schemas=set(), perms=set(), out=collections.Counter(),
                tests=0, testfiles=0) for m in mods}

for m in mods:
    base = os.path.join(MOD, m)
    for dp, _, fns in os.walk(base):
        for fn in fns:
            if not fn.endswith(".java"): continue
            p = os.path.join(dp, fn)
            raw = open(p, encoding="utf-8", errors="replace").read()
            src = strip_java(raw)          # F2-69: every annotation predicate below is code-only now
            s = stat[m]
            s["loc"] += raw.count("\n") + 1                 # LoC is deliberately raw (documented rule)
            s["files"] += 1
            n_ent = len(ENTITY_STRICT.findall(src))
            if n_ent: s["entities"] += 1                    # files declaring an entity
            s["entity_classes"] += n_ent                    # entity classes declared
            if "@RestController" in src or "@Controller" in src: s["controllers"] += 1
            if "@Repository" in src or "extends JpaRepository" in src: s["repos"] += 1
            if "@Service" in src: s["services"] += 1
            s["endpoints"] += len(MAPPING.findall(src)) - (1 if re.search(r'@RequestMapping\s*\(\s*"', src) else 0)
            for t in TABLE.findall(src):
                for sc in SCHEMA.findall(t): s["schemas"].add(sc)
            for mod_, act in PREAUTH.findall(src): s["perms"].add(mod_)
            for _st, path in IMPORT.findall(src):
                if path.startswith("module."):
                    other = path.split(".")[1]
                    if other != m and other in stat: s["out"][other] += 1
                elif path.startswith(("common.", "config.", "platform.", "security.", "util.")):
                    s["out"]["<kernel>"] += 1

# tests per module (by package path under src/test)
for dp, _, fns in os.walk(TEST):
    for fn in fns:
        if not fn.endswith(".java"): continue
        src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
        n = len(TESTM.findall(src))
        rel = os.path.relpath(os.path.join(dp, fn), TEST).replace("\\", "/")
        owner = None
        if rel.startswith("module/"):
            owner = rel.split("/")[1]
        if owner not in stat:  # attribute by any module import
            hits = collections.Counter()
            for _st, path in IMPORT.findall(src):
                if path.startswith("module."):
                    o = path.split(".")[1]
                    if o in stat: hits[o] += 1
            owner = hits.most_common(1)[0][0] if hits else None
        if owner in stat:
            stat[owner]["tests"] += n
            stat[owner]["testfiles"] += 1

fan_in = collections.Counter()
for m in mods:
    for o, c in stat[m]["out"].items():
        if o != "<kernel>": fan_in[o] += 1

rows = []
for m in mods:
    s = stat[m]
    peers = {k: v for k, v in s["out"].items() if k != "<kernel>"}
    rows.append(dict(module=m, loc=s["loc"], files=s["files"], entities=s["entities"],
                     entity_classes=s["entity_classes"],
                     controllers=s["controllers"], endpoints=s["endpoints"], repos=s["repos"],
                     services=s["services"], schemas=sorted(s["schemas"]),
                     perms=sorted(s["perms"]), tests=s["tests"], testfiles=s["testfiles"],
                     fan_out=len(peers), fan_out_edges=sum(peers.values()),
                     fan_in=fan_in[m], peers=dict(sorted(peers.items(), key=lambda kv: -kv[1])),
                     kernel=s["out"].get("<kernel>", 0)))

# ── FE mock debt + page inventory ────────────────────────────────────────────
mock_sites, mock_files, pages, api_files = [], set(), 0, 0
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if not fn.endswith((".ts", ".tsx")): continue
        p = os.path.join(dp, fn)
        rel = os.path.relpath(p, FE).replace("\\", "/")
        src = open(p, encoding="utf-8", errors="replace").read()
        if rel.startswith("pages/") and fn.endswith(".tsx"): pages += 1
        if rel.startswith("lib/"): api_files += 1
        for mm in re.finditer(r"from\s+['\"]([^'\"]*mock[A-Za-z0-9_]*)['\"]", src):
            mock_sites.append((rel, mm.group(1))); mock_files.add(rel)
        for mm in re.finditer(r"\bimport\s+.*\bmock[A-Z][A-Za-z0-9_]*\b", src):
            mock_sites.append((rel, "named-mock")); mock_files.add(rel)

out = dict(modules=rows,
           fe=dict(pages=pages, api_files=api_files,
                   mock_sites=len(mock_sites), mock_files=sorted(mock_files)))
sp = os.path.join(os.path.dirname(os.path.abspath(__file__)), "coupling.json")
json.dump(out, open(sp, "w", encoding="utf-8"), indent=1, default=str)

print(f"modules={len(rows)}  FE pages={pages}  mock-import-sites={len(mock_sites)} in {len(mock_files)} files")
print(f"{'module':22} {'LoC':>6} {'ent':>4} {'ep':>4} {'tests':>6} {'out':>4} {'edges':>6} {'in':>3}  schemas")
for r in sorted(rows, key=lambda r: -r["loc"]):
    print(f"{r['module']:22} {r['loc']:6} {r['entities']:4} {r['endpoints']:4} {r['tests']:6} "
          f"{r['fan_out']:4} {r['fan_out_edges']:6} {r['fan_in']:3}  {','.join(r['schemas'])[:46]}")
print("\nTOTALS  LoC=%d entity-files=%d entity-classes=%d endpoints=%d tests=%d" % (
    sum(r['loc'] for r in rows), sum(r['entities'] for r in rows),
    sum(r['entity_classes'] for r in rows),
    sum(r['endpoints'] for r in rows), sum(r['tests'] for r in rows)))
print("zero-test modules: %d -> %s" % (
    sum(1 for r in rows if r['tests'] == 0),
    ", ".join(r['module'] for r in sorted(rows, key=lambda r:-r['loc']) if r['tests'] == 0)))
