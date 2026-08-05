# -*- coding: utf-8 -*-
"""F2-34 — reconcile the module graph's two disputed counts, and PRICE the two rule readings.

WHY THIS EXISTS
===============
F2-08b (the ratcheted boundary test over every package) is blocked by F2-34, whose row says outright:
"The boundary ratchet cannot be seeded until one reading wins." Two things are unsettled:

  (1) MODULE_SEPARABILITY §2 says kernel imports do not count toward coupling, while §6c says only
      `common` / `security` / `platform` / `spi` are legal imports. Under the first reading
      `CommitteePort` (S5a's whole deliverable) is unnecessary.
  (2) The architecture seat counts the kernel in 6 bidirectional pairs; the plan's own graph reports
      13. "The two use different methods and reconciling them is part of this item."

⚠ THE AMBIGUITY IS ONE WORD, AND IT IS MEASURABLE
=================================================
There are TWO `platform` namespaces in this tree:

    com.tss.grc.platform          5 files   — a top-level package, alongside common/config/security
    com.tss.grc.module.platform   115 files — a module, holding licensing, auditlog, automation, files,
                                              sso, persistence …

Every prose statement of the rule says "platform" without saying which. `coupling.py` resolves it one
way in one branch and the other way in another: an import of `com.tss.grc.platform.*` is tallied as
`<kernel>` (line 57) while an import of `com.tss.grc.module.platform.*` is tallied as a PEER (line 54).
So the same word carries both readings inside a single script — which is very likely the whole of the
6-vs-13 discrepancy rather than a genuine methodological dispute.

This script does not choose. It reports what each reading costs, so the choice is priced rather than
argued, and so F2-08b can be seeded the moment it is made.

READINGS
========
  A  "narrow kernel"  — kernel = common, config, security, util, uploads, com.tss.grc.platform.
                        `module.platform` is a PEER, so importing it is coupling and §6c's rule bites.
  B  "wide kernel"    — reading A plus `module.platform`. The platform module IS kernel, so importing
                        it is free, and §2's "kernel imports do not count" absorbs most coupling.

Usage: py boundary_readings.py [--json out.json]
"""
import collections
import io
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = r"D:\AYMAN\GRC"
MOD = os.path.join(ROOT, r"tss-grc-backend\src\main\java\com\tss\grc\module")
IMPORT = re.compile(r"^import\s+(?:static\s+)?com\.tss\.grc\.([A-Za-z0-9_.]+);", re.M)

# Top-level packages that are kernel under BOTH readings.
TOP_KERNEL = {"common", "config", "security", "util", "uploads"}
# The disputed one. Under A it is kernel only as a TOP-LEVEL package; under B `module.platform` too.
PLATFORM_MODULE = "platform"

modules = sorted(d for d in os.listdir(MOD) if os.path.isdir(os.path.join(MOD, d)))


def classify(path, wide_kernel):
    """Return ('peer', name) | ('kernel', None) | (None, None) for one import path."""
    if path.startswith("module."):
        parts = path.split(".")
        if len(parts) < 2:
            return None, None
        other = parts[1]
        if other not in modules:
            return None, None
        if wide_kernel and other == PLATFORM_MODULE:
            return "kernel", None
        return "peer", other
    top = path.split(".")[0]
    if top in TOP_KERNEL or top == PLATFORM_MODULE:
        return "kernel", None          # com.tss.grc.platform — kernel under both readings
    return None, None


def build(wide_kernel):
    edges = collections.Counter()      # (src, dst) -> import lines
    kernel_lines = collections.Counter()
    for m in modules:
        for dp, _dn, fns in os.walk(os.path.join(MOD, m)):
            for fn in fns:
                if not fn.endswith(".java"):
                    continue
                src = io.open(os.path.join(dp, fn), encoding="utf-8", errors="replace").read()
                for path in IMPORT.findall(src):
                    kind, other = classify(path, wide_kernel)
                    if kind == "peer" and other != m:
                        edges[(m, other)] += 1
                    elif kind == "kernel":
                        kernel_lines[m] += 1
    pairs = set(edges)
    bidi = {tuple(sorted(p)) for p in pairs if (p[1], p[0]) in pairs}
    return dict(edges=edges, lines=sum(edges.values()), pairs=len(pairs), bidi=sorted(bidi),
                kernel_lines=sum(kernel_lines.values()))


print("=" * 96)
print("F2-34 — the two readings, measured at HEAD")
print("=" * 96)
print("  modules on disk: %d" % len(modules))

res = {}
for label, wide in (("A  narrow kernel (module.platform is a PEER)", False),
                    ("B  wide kernel  (module.platform IS kernel)", True)):
    g = build(wide)
    res[label[0]] = g
    print("\n%s" % label)
    print("    peer import lines .......... %4d" % g["lines"])
    print("    directed pairs ............. %4d" % g["pairs"])
    print("    BIDIRECTIONAL pairs ........ %4d" % len(g["bidi"]))
    print("    kernel import lines ........ %4d  (free under §2)" % g["kernel_lines"])

a, b = res["A"], res["B"]
print("\n" + "=" * 96)
print("WHAT THE CHOICE COSTS")
print("=" * 96)
print("  Reading B legalises %d peer import lines that reading A counts as coupling."
      % (a["lines"] - b["lines"]))
print("  It removes %d directed pairs and %d bidirectional pairs from the graph."
      % (a["pairs"] - b["pairs"], len(a["bidi"]) - len(b["bidi"])))
print("  A ratchet seeded under A therefore starts at %d/%d/%d (lines/pairs/bidi);"
      % (a["lines"], a["pairs"], len(a["bidi"])))
print("  under B it starts at %d/%d/%d." % (b["lines"], b["pairs"], len(b["bidi"])))

print("\n  ⚠ Which reading reproduces each disputed figure:")
for name, g in (("A", a), ("B", b)):
    for claim in (6, 13):
        if len(g["bidi"]) == claim:
            print("      reading %s reproduces the '%d bidirectional pairs' claim exactly" % (name, claim))
print("      (measured: A=%d, B=%d — the plan says 13, the architecture seat says 6)"
      % (len(a["bidi"]), len(b["bidi"])))

print("\n  bidirectional pairs under A (%d):" % len(a["bidi"]))
for p in a["bidi"]:
    print("      %-22s <-> %-22s  %d / %d lines"
          % (p[0], p[1], a["edges"][(p[0], p[1])], a["edges"][(p[1], p[0])]))
only_a = [p for p in a["bidi"] if p not in b["bidi"]]
print("\n  ...of which %d disappear under B (i.e. they involve module.platform): %s"
      % (len(only_a), ", ".join("%s<->%s" % p for p in only_a) or "none"))

print("\n" + "=" * 96)
print("KERNEL → BUSINESS: the inverted dependency F2-34 names (40 lines at ff087c7c)")
print("=" * 96)
# Under BOTH readings, an import FROM the platform module TO a business module is an inversion.
inversions = collections.Counter()
for (s, d), n in build(False)["edges"].items():
    if s == PLATFORM_MODULE:
        inversions[d] += n
# and the other kernel-ish modules the row names.
# ⚠ An import FROM a kernel-ish module TO a business module is the inversion. An import from
# `integrations` INTO `platform` is not — it is business depending on kernel, which is legal under
# both readings. The first version of this script summed both and reported 68; excluding the 13
# `integrations->platform` lines gives the real figure. A total that mixes both directions measures
# nothing.
BUSINESS_EXEMPT = {PLATFORM_MODULE} | TOP_KERNEL
for extra in ("integrations", "dashboard"):
    for (s, d), n in build(False)["edges"].items():
        if s == extra and d not in BUSINESS_EXEMPT:
            inversions[extra + "->" + d] += n
plat = {k: v for k, v in inversions.items() if "->" not in k}
print("  module.platform -> business: %d lines across %d modules" % (sum(plat.values()), len(plat)))
for k, v in sorted(plat.items(), key=lambda kv: -kv[1]):
    print("      platform -> %-20s %d" % (k, v))
others = {k: v for k, v in inversions.items() if "->" in k}
print("  the row also names integrations/dashboard, measured now:")
for k, v in sorted(others.items(), key=lambda kv: -kv[1])[:10]:
    print("      %-34s %d" % (k, v))
print("  TOTAL kernel-ish -> business: %d lines  (row recorded 40 at ff087c7c)"
      % (sum(plat.values()) + sum(others.values())))

print("\n" + "=" * 96)
print("WHAT THIS DOES NOT DO")
print("=" * 96)
print("  It does not choose a reading. That is F2-34's owner decision (Architecture seat), and")
print("  seeding F2-08b against a guess would pin %d or %d bidirectional pairs as tolerated —" % (len(a["bidi"]), len(b["bidi"])))
print("  a ceiling tolerates the population it measures, so the wrong seed legitimises the wrong graph.")

if "--json" in sys.argv:
    dest = sys.argv[sys.argv.index("--json") + 1]
    json.dump({k: dict(lines=v["lines"], pairs=v["pairs"], bidi=[list(x) for x in v["bidi"]],
                       kernel_lines=v["kernel_lines"]) for k, v in res.items()},
              io.open(dest, "w", encoding="utf-8"), indent=1)
    print("\n  wrote %s" % dest)
