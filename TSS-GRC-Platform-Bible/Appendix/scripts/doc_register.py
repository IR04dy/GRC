# Generate the complete documentation register for audit support.
# Output: TSS-GRC-Platform-Bible/Appendix/DOCUMENT_REGISTER.csv (every tracked .md,
# with git provenance and a corpus/currency classification).
import os, subprocess, csv, collections

# ⚠ Derived from this file's own location, never hardcoded. F2-19b: three of the four
# measurement scripts pinned ROOT to "D:\AYMAN\GRC", so none of them could run on a CI
# runner — the reason the guards existed and nothing enforced them was a path, not a
# database. baseline_census.py already did it this way; the pattern simply had not spread.
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.environ.get("GRC_ROOT") or os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT  = os.path.join(ROOT, "TSS-GRC-Platform-Bible", "Appendix", "DOCUMENT_REGISTER.csv")
os.chdir(ROOT)

def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout

# newest + oldest commit date and commit count per .md path, in one history pass
newest, oldest, commits = {}, {}, collections.Counter()
date = None
for line in git("log", "--date=short", "--format=@%ad", "--name-only", "--", "*.md").splitlines():
    if line.startswith("@"):
        date = line[1:]
    elif line.strip().endswith(".md"):
        p = line.strip()
        newest.setdefault(p, date)
        oldest[p] = date
        commits[p] += 1

tracked = [l for l in git("ls-files", "*.md").splitlines() if l]

# Corpus classification — by location, which is what determines authority here.
def corpus(p):
    if p.startswith("TSS-GRC-Platform-Bible/"):            return "BIBLE"
    if p.startswith("docs/Saudi-GRC-Platform-Integration/"): return "SAUDI-PACK-B"
    if p.startswith("Saudi-GRC-Platform-Integration/"):    return "SAUDI-PACK-A"
    if p.startswith("docs/"):                              return "DOCS-V1"
    if p.startswith("review/"):                            return "REVIEW-ARTEFACT"
    if p.startswith("sprints/"):                           return "SPRINT-V1"
    if "/" not in p:                                       return "ROOT"
    return "OTHER"

PLAN_HINTS  = ("_PLAN.md", "_PROGRAM.md", "REMEDIATION", "UPLIFT", "REVIEW_AND")
ASSESS_HINTS = ("ASSESSMENT", "FINDINGS", "VERDICT", "VALIDATION", "AUDIT")
OPS_HINTS   = ("DEPLOYMENT", "LOCAL_SETUP", "CLAUDE.md", "AGENTS.md")
HIST_HINTS  = ("STATUS.md", "SESSION_SUMMARY", "PROJECT_BUILD_HISTORY", "summary.md")

def role(p):
    b = os.path.basename(p)
    if p.startswith("TSS-GRC-Platform-Bible/"):                 return "reference-corpus"
    if any(h in b for h in HIST_HINTS):                         return "historic-snapshot"
    if any(h in b for h in OPS_HINTS):                          return "operational"
    if any(h in b.upper() for h in ASSESS_HINTS):               return "assessment"
    if any(h in b for h in PLAN_HINTS):                         return "plan"
    if b in ("PROJECT_CHRONICLE.md",):                          return "master-reference"
    if b in ("README.md", "PLATFORM_USER_STORIES.md",
             "PLATFORM_ARCHITECTURE_PACK.md", "REVIEW_MODULE_COMMAND.md"): return "reference"
    return "supporting"

# Currency: anything not touched since the remediation programmes began (2026-07) is
# a staleness candidate; that is a fact about the file, not a judgement on its content.
#
# ⚠ W0-6 broke that heuristic and had to supersede it. Stamping the point-in-time corpus
# `HISTORIC` rewrote 217 documents in a single commit, so their newest-commit date became
# today and the classifier called every one of them `current` — the act of marking a
# snapshot as historic made this script report it as fresh. A document that declares its
# own status outranks anything inferred from when it was last touched, so the banner wins.
# (This is why the banner text is a stable sentinel: GovernanceDocumentIntegrityTest and
# w06_stamp.py match the same string.)
HISTORIC_BANNER = "**HISTORIC"

def declares_itself_historic(p):
    try:
        with open(p, encoding="utf-8", errors="replace") as fh:
            return HISTORIC_BANNER in fh.read(1500)
    except OSError:
        return False

def currency(d, p):
    if declares_itself_historic(p): return "historic"
    if d is None: return "unknown"
    if d >= "2026-07-01": return "current"
    if d >= "2026-06-01": return "recent"
    return "stale-candidate"

rows = []
for p in sorted(tracked):
    try:
        lines = open(p, encoding="utf-8", errors="replace").read().count("\n") + 1
    except OSError:
        lines = 0
    n = newest.get(p)
    rows.append(dict(path=p, corpus=corpus(p), role=role(p), lines=lines,
                     bytes=os.path.getsize(p) if os.path.exists(p) else 0,
                     newest_commit=n or "", oldest_commit=oldest.get(p, ""),
                     commits=commits.get(p, 0), currency=currency(n, p)))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", newline="", encoding="utf-8") as fh:
    w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)

print(f"wrote {OUT}  ({len(rows)} tracked .md files)")
print(f"total lines: {sum(r['lines'] for r in rows):,}   bytes: {sum(r['bytes'] for r in rows):,}")
for field in ("corpus", "role", "currency"):
    c = collections.Counter(r[field] for r in rows)
    print(f"\nby {field}:")
    for k, v in c.most_common():
        ln = sum(r["lines"] for r in rows if r[field] == k)
        print(f"   {v:4} files  {ln:7,} lines  {k}")

# on-disk vs tracked (the stale-worktree inflation)
disk = 0
SKIP = {"node_modules", ".git", "dist", "target", "build", ".vite", "coverage"}
for dp, dns, fns in os.walk(ROOT):
    dns[:] = [d for d in dns if d not in SKIP]
    disk += sum(1 for f in fns if f.lower().endswith(".md"))
print(f"\non-disk .md: {disk}   tracked: {len(rows)}   untracked/duplicated: {disk - len(rows)}")
