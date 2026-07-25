# Verify the surprising measurements before they go into the Bible.
import os, re, json, collections
ROOT = r"D:\AYMAN\GRC"
MODJ = os.path.join(ROOT, r"tss-grc-backend\src\main\java\com\tss\grc\module")
FE   = os.path.join(ROOT, r"tss-grc-ui-v2\src")

print("=" * 78)
print("1. datagov — CLAUDE.md lists it as a module. What is actually in it?")
for dp, _, fns in os.walk(os.path.join(MODJ, "datagov")):
    for fn in fns:
        p = os.path.join(dp, fn)
        print("   ", os.path.relpath(p, MODJ), open(p, encoding="utf-8").read().count("\n") + 1, "lines")

print("=" * 78)
print("2. Which modules persist into a schema NOT named after themselves?")
SCHEMA = re.compile(r'schema\s*=\s*"([^"]+)"')
own = collections.defaultdict(collections.Counter)
for m in sorted(os.listdir(MODJ)):
    if not os.path.isdir(os.path.join(MODJ, m)): continue
    for dp, _, fns in os.walk(os.path.join(MODJ, m)):
        for fn in fns:
            if not fn.endswith(".java"): continue
            src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
            if "@Entity" not in src: continue
            for sc in SCHEMA.findall(src): own[m][sc] += 1
for m, c in sorted(own.items()):
    foreign = {s: n for s, n in c.items() if s != m and not s.startswith(m)}
    if foreign:
        print(f"   {m:22} -> {dict(c)}   FOREIGN: {foreign}")

print("=" * 78)
print("3. FE mock debt — distinct import statements (reconcile 53 vs 97)")
files = collections.Counter(); stmts = []
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if not fn.endswith((".ts", ".tsx")): continue
        p = os.path.join(dp, fn); rel = os.path.relpath(p, FE).replace("\\", "/")
        src = open(p, encoding="utf-8", errors="replace").read()
        # one hit per import STATEMENT that pulls from a mock module
        for mm in re.finditer(r"(?:^|\n)\s*(?:import|export)[^;\n]*?from\s+['\"]([^'\"]+)['\"]", src):
            if "mock" in mm.group(1).lower():
                files[rel] += 1; stmts.append((rel, mm.group(1)))
print(f"   distinct mock-import STATEMENTS: {len(stmts)}   in {len(files)} files")
for f, n in files.most_common(15): print(f"      {n:3}  {f}")
mockmods = collections.Counter(s[1] for s in stmts)
print("   mock modules imported:", dict(mockmods.most_common()))

print("=" * 78)
print("4. Do mock files still exist, and how big are they?")
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if "mock" in fn.lower():
            p = os.path.join(dp, fn)
            print("   ", os.path.relpath(p, FE).replace("\\", "/"),
                  open(p, encoding="utf-8", errors="replace").read().count("\n") + 1, "lines")

print("=" * 78)
print("5. audit_committee (1,581 LoC) reaches into 10 modules — which?")
IMPORT = re.compile(r"^import\s+(?:static\s+)?com\.tss\.grc\.module\.([A-Za-z0-9_]+)", re.M)
for m in ("audit_committee", "reporting", "governance", "socops", "dashboard"):
    c = collections.Counter()
    for dp, _, fns in os.walk(os.path.join(MODJ, m)):
        for fn in fns:
            if not fn.endswith(".java"): continue
            src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
            for o in IMPORT.findall(src):
                if o != m: c[o] += 1
    print(f"   {m:18} -> {dict(c.most_common())}")

print("=" * 78)
print("6. FE pages per top-level folder (329 pages — how are they organised?)")
pf = collections.Counter()
for dp, _, fns in os.walk(os.path.join(FE, "pages")):
    for fn in fns:
        if fn.endswith(".tsx"):
            rel = os.path.relpath(os.path.join(dp, fn), os.path.join(FE, "pages")).replace("\\", "/")
            pf["/".join(rel.split("/")[:-1]) or "<root>"] += 1
for k, v in pf.most_common(30): print(f"      {v:4}  pages/{k}")
print("      folders:", len(pf), " root-level pages:", pf.get("<root>", 0))

print("=" * 78)
print("7. Nav/route count vs pages — is every page reachable?")
for cand in ("App.tsx", "routes.tsx", "router.tsx", "main.tsx"):
    p = os.path.join(FE, cand)
    if os.path.exists(p):
        src = open(p, encoding="utf-8", errors="replace").read()
        print(f"   {cand}: <Route path= count = {len(re.findall(r'<Route[^>]*path=', src))}, lines={src.count(chr(10))+1}")
nav = []
for dp, _, fns in os.walk(FE):
    for fn in fns:
        if re.search(r"(nav|sidebar|menu)", fn, re.I) and fn.endswith((".ts", ".tsx")):
            p = os.path.join(dp, fn)
            src = open(p, encoding="utf-8", errors="replace").read()
            nav.append((os.path.relpath(p, FE).replace("\\", "/"), src.count("\n") + 1,
                        len(re.findall(r"(?:path|to|href)\s*:\s*['\"]/", src))))
for n in nav: print(f"   nav file {n[0]}  lines={n[1]}  route-entries={n[2]}")
