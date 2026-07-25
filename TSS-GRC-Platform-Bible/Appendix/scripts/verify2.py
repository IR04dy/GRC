# Governance coverage per module: permissions, FE surface, guarded endpoints, DB reach.
import os, re, json, collections
ROOT = r"D:\AYMAN\GRC"
MODJ = os.path.join(ROOT, r"tss-grc-backend\src\main\java\com\tss\grc\module")
FE   = os.path.join(ROOT, r"tss-grc-ui-v2\src\pages")
MIG  = os.path.join(ROOT, r"tss-grc-backend\src\main\resources\db\migration\sqlserver")

data = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "coupling.json"), encoding="utf-8"))
mods = {m["module"]: m for m in data["modules"]}

WRITE = re.compile(r'@(Post|Put|Patch|Delete)Mapping')
PRE   = re.compile(r'@PreAuthorize')
print("=" * 100)
print("A. Per-module governance: writes vs @PreAuthorize, permission modules named, FE folder, tests")
fe_folders = {d.lower() for d in os.listdir(FE) if os.path.isdir(os.path.join(FE, d))}
rows = []
for m, s in mods.items():
    writes = pre = 0
    for dp, _, fns in os.walk(os.path.join(MODJ, m)):
        for fn in fns:
            if not fn.endswith(".java"): continue
            src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
            writes += len(WRITE.findall(src)); pre += len(PRE.findall(src))
    fe = "yes" if (m.lower() in fe_folders or m.lower().replace("_", "-") in fe_folders
                   or m.lower().replace("_", "") in fe_folders) else "-"
    rows.append((m, s["loc"], writes, pre, ",".join(s["perms"]) or "NONE", s["tests"], fe, s["entities"]))
rows.sort(key=lambda r: -r[1])
print(f"{'module':22}{'LoC':>7}{'writes':>7}{'@PreAu':>7}{'tests':>6}  {'FE':>3}  perm-modules")
for r in rows:
    flag = ""
    if r[4] == "NONE" and r[2] > 0: flag = "  <== WRITES, NO hasPermission"
    print(f"{r[0]:22}{r[1]:7}{r[2]:7}{r[3]:7}{r[5]:6}  {r[6]:>3}  {r[4][:34]}{flag}")

print("=" * 100)
print("B. Permission catalogue: distinct MODULE names used in hasPermission() across the codebase")
allperms = collections.Counter()
for dp, _, fns in os.walk(os.path.join(ROOT, r"tss-grc-backend\src\main\java")):
    for fn in fns:
        if not fn.endswith(".java"): continue
        src = open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
        for mm in re.finditer(r"hasPermission\(\s*'([A-Z_]+)'\s*,\s*'([A-Z_]+)'\s*\)", src):
            allperms[mm.group(1)] += 1
print(f"   distinct permission MODULES referenced by guards: {len(allperms)}")
print("   ", dict(allperms.most_common()))
print(f"   module PACKAGES with zero permission coverage: "
      f"{sum(1 for m, s in mods.items() if not s['perms'])} of {len(mods)}")
noperm = [m for m, s in sorted(mods.items(), key=lambda kv: -kv[1]['loc']) if not s['perms'] and s['loc'] > 300]
print("   biggest un-permissioned packages:", ", ".join(f"{m}({mods[m]['loc']})" for m in noperm[:20]))

print("=" * 100)
print("C. Database surface")
schemas = collections.Counter()
for m, s in mods.items():
    for sc in s["schemas"]: schemas[sc] += 1
print(f"   distinct JPA schemas: {len(schemas)}")
migs = sorted(f for f in os.listdir(MIG) if f.startswith("V") and f.endswith(".sql"))
nums = sorted(int(re.match(r"V(\d+)", f).group(1)) for f in migs)
print(f"   migrations on disk: {len(migs)}   range V{nums[0]}..V{nums[-1]}   gaps: "
      f"{[n for n in range(nums[0], nums[-1]) if n not in set(nums)][:12]}")
tot = sum(os.path.getsize(os.path.join(MIG, f)) for f in migs)
print(f"   migration bytes: {tot:,}")

print("=" * 100)
print("D. Saudi-differentiating modules — test coverage reality check")
saudi = ["sharia", "openbanking", "outsourcing", "regreporting", "reginspect", "aml", "cpty",
         "rrp", "fraud", "consumer", "opres", "nca_ecc", "privacy", "ndi", "data_officer",
         "dataclass", "regchange", "sust"]
print(f"{'module':16}{'LoC':>7}{'tests':>7}{'entities':>9}{'endpoints':>10}  verdict")
for m in saudi:
    if m not in mods: continue
    s = mods[m]
    v = "ZERO TESTS" if s["tests"] == 0 else ("thin" if s["tests"] < s["loc"] / 60 else "covered")
    print(f"{m:16}{s['loc']:7}{s['tests']:7}{s['entities']:9}{s['endpoints']:10}  {v}")
z = [m for m in mods if mods[m]["tests"] == 0]
print(f"\n   zero-test packages: {len(z)}  totalling {sum(mods[m]['loc'] for m in z):,} LoC "
      f"({100*sum(mods[m]['loc'] for m in z)/sum(m['loc'] for m in mods.values()):.1f}% of module code)")
