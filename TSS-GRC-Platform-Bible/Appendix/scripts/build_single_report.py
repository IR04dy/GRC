# Build the consolidated single-file assessment report from its source documents.
# Output: TSS_GRC_PLATFORM_ASSESSMENT.md at the repository root.
#
# Why generated rather than hand-written: the sources stay authoritative and this
# file cannot drift from them. Re-run after editing any source.
import os, re, io

ROOT  = r"D:\AYMAN\GRC"
BIBLE = os.path.join(ROOT, "TSS-GRC-Platform-Bible")
OUT   = os.path.join(ROOT, "TSS_GRC_PLATFORM_ASSESSMENT.md")

# (part title, source path relative to the Bible, short blurb for the TOC)
PARTS = [
    ("Independent Acceptance Verdict", "Executive/INDEPENDENT_ACCEPTANCE_VERDICT.md",
     "Three verdicts — acceptance committee, enterprise customer, investor DD — 13-dimension scorecard, four conditions, 30-day plan."),
    ("Findings Register", "Registers/FINDINGS_REGISTER_V2.md",
     "22 findings in full 18-field format, severity-ranked, each anchored to measured evidence."),
    ("Measured Baseline — the evidence base", "Appendix/MEASURED_BASELINE.md",
     "Every number in this report, with the script that reproduces it. Cite this, not prose."),
    ("Module Separability & SKU Model", "Architecture/MODULE_SEPARABILITY_AND_SKU_MODEL.md",
     "All 58 modules tiered A/B/C/K from the coupling graph; 14-SKU catalogue; what actually blocks per-module sale."),
    ("UX Redesign Blueprint", "UX/UX_REDESIGN_BLUEPRINT.md",
     "Why a visual redesign is the wrong call, the information-architecture redesign that is the right one, Arabic convergence, accessibility."),
    ("Documentation Register & Audit Support", "Appendix/DOCUMENTATION_REGISTER_AND_AUDIT_SUPPORT.md",
     "331 tracked documents across 8 corpora; what governs vs what is historic; a re-verification command for every finding."),
    ("Validation of the Existing Corpus", "Governance/BIBLE_VALIDATION_LOG.md",
     "Per-document verdict on the pre-existing 46-file reference pack, and the three claims this review got wrong."),
    ("ADR-009 — Module Registry as a Build Artefact", "Decisions/ADR-009-MODULE-REGISTRY.md", None),
    ("ADR-010 — Permission-Only Authorisation", "Decisions/ADR-010-PERMISSION-ONLY-AUTHORISATION.md", None),
    ("ADR-011 — Schema Ownership Equals Module Ownership", "Decisions/ADR-011-SCHEMA-OWNERSHIP.md", None),
    ("ADR-012 — Boundary Enforcement and Consumer-Owned Ports", "Decisions/ADR-012-BOUNDARY-ENFORCEMENT-AND-PORTS.md", None),
]

def anchor(text):
    """GitHub-style slug. Part headings use 'Part N: Title' — a colon, deliberately:
    it is stripped without leaving a double space, so the slug has no ambiguous
    double hyphen and TOC links, cross-references and headings all agree."""
    s = text.lower()
    s = re.sub(r"[^\w\s-]", "", s, flags=re.UNICODE)
    return re.sub(r"\s+", "-", s.strip())

def part_title(i, title):
    return f"Part {i}: {title}"

def demote(body):
    """Add one level to every ATX heading outside fenced code blocks, and drop the
    source's own H1 (the part heading replaces it)."""
    out, fence, dropped_h1 = [], False, False
    for line in body.splitlines():
        if re.match(r"^\s*(```|~~~)", line):
            fence = not fence
            out.append(line); continue
        if not fence and re.match(r"^#{1,6}\s", line):
            if line.startswith("# ") and not dropped_h1:
                dropped_h1 = True          # replaced by the generated part heading
                continue
            out.append("#" + line); continue
        out.append(line)
    return "\n".join(out)

# Link rewrites: every intra-corpus reference becomes an in-document anchor.
# Derived from PARTS so the mapping can never disagree with the headings.
LINK_TARGETS = {os.path.basename(rel): anchor(part_title(i, title))
                for i, (title, rel, _b) in enumerate(PARTS, start=1)}

def rewrite_links(text, src_rel):
    """Inlined parts become anchors; everything else is resolved against the source
    document's own directory and re-expressed relative to the repository root, so it
    still works from the root-level output file."""
    src_dir = os.path.dirname(os.path.join("TSS-GRC-Platform-Bible", src_rel))

    def repl(m):
        label, target = m.group(1), m.group(2)
        base = target.split("/")[-1].split("#")[0]
        if base in LINK_TARGETS:
            return f"[{label}](#{LINK_TARGETS[base]})"
        frag = ""
        if "#" in target:
            target, frag = target.split("#", 1)
            frag = "#" + frag
        resolved = os.path.normpath(os.path.join(src_dir, target)).replace("\\", "/")
        return f"[{label}]({resolved}{frag})"

    return re.sub(r"\[([^\]]+)\]\(((?!https?://|#)[^)]+\.(?:md|csv|html|py)(?:#[^)]*)?)\)",
                  repl, text)

bodies = []
for i, (title, rel, _blurb) in enumerate(PARTS, start=1):
    src = os.path.join(BIBLE, rel)
    raw = open(src, encoding="utf-8").read()
    head = f"## {part_title(i, title)}\n\n*Source: [`{rel}`](TSS-GRC-Platform-Bible/{rel})*\n"
    bodies.append((i, title, head + "\n" + rewrite_links(demote(raw), rel).strip() + "\n"))

# ── front matter ────────────────────────────────────────────────────────────────
toc = ["## Contents\n"]
for i, (title, rel, blurb) in enumerate(PARTS, start=1):
    a = anchor(part_title(i, title))
    toc.append(f"{i}. **[{title}](#{a})**" + (f" — {blurb}" if blurb else ""))
toc.append("\n" + "\n".join([
    "Appendices, kept as separate files because they are data rather than prose:",
    "",
    "- [`DOCUMENT_REGISTER.csv`](TSS-GRC-Platform-Bible/Appendix/DOCUMENT_REGISTER.csv) — all 331 tracked Markdown files with git provenance",
    "- [`Appendix/scripts/`](TSS-GRC-Platform-Bible/Appendix/scripts/) — the six measurement scripts that reproduce every number here",
    "- `audit/` — the tracked 98-file evidence vault from the 2026-06-18 audit (k6, axe, Lighthouse, gitleaks, trivy, bandit, jscpd, JaCoCo)",
]))

FRONT = """# TSS GRC Platform — Independent Assessment

**Single-file consolidation of the 2026-07-25 independent review.**

| | |
|---|---|
| **Date** | 2026-07-25 |
| **Tree assessed** | `master` `99a719da` (measurements) · report generated at `3128a629` |
| **Standpoint** | Independent enterprise product review — CPO · Enterprise Architect · CTO · Principal UX · GRC SME · Security Architect · Saudi regulatory consultant · government acceptance committee · enterprise customer · investor technical due diligence |
| **Method** | Static measurement of the source tree, live inspection of running code, and re-derivation of every checkable claim in the platform's existing documentation. Not a penetration test, not a WCAG conformance audit, not a load-test campaign — see Part 3 §8 for the explicit limits |
| **Verdict** | **Conditional acceptance · 5.5 / 10** — "strong engineering, unpackaged product, one unenforceable core control" |
| **Regenerate** | `py TSS-GRC-Platform-Bible/Appendix/scripts/build_single_report.py` |

> **Evidence rule.** No claim in this report is asserted without provenance. Every quantitative statement
> traces to **Part 3 (Measured Baseline)**, which names the script that reproduces it; every finding in
> **Part 2** carries a re-verification command in **Part 6 §4**. Statements that are judgement rather than
> measurement are marked **OPINION**. Competitor statements describe publicly observable licensing and
> positioning behaviour, not verified internals.

---

## The five things that matter

1. **Committee votes are forgeable.** `recordVote` takes the voter's name from the request body, with no
   check that it is the caller or even a member of the committee. Recusal, conflict-of-interest, quorum
   and segregation of duties all evaluate the *claimed* name; another member's cast vote can be
   overwritten; the audit ledger records the forged name; and the SHA-256 e-signed minutes attest to it.
   The correct pattern exists one file away in `approveMinutes`. **~2 days to fix, and it is the platform's
   central control claim.** (F2-21)

2. **The Saudi differentiators are the cleanest code and the least assured.** `sharia`, `openbanking`,
   `outsourcing`, `rrp`, `cpty`, `regreporting`, `reginspect`, `fraud`, `opres`, `dataclass`, `nca_ecc`
   have **zero peer-module dependencies** — and **zero tests**. Nothing blocks selling them except tests
   and a permission catalogue. The moat exists in code and is unproven in CI. (F2-03, Part 4)

3. **The entitlement subsystem is built and 8.6% wired.** A complete licensing layer exists — tier, seats,
   validity, a fail-closed AOP guard, a JWT claim, an admin screen — applied to 22 of 255 controllers, all
   in one module. Dev tenants 2 and 3 are licensed for `SOCOPS` alone and can use all 58 modules. The
   per-module business model is days of rollout, not weeks of build. (F2-04)

4. **Three executive-facing surfaces render fabricated data** — global search, the executive dashboard and
   the board pack. A governance product may not invent records. Days to fix. (F2-02)

5. **The frontend's assets are better than its documentation claims, and its navigation is worse.**
   99% shared-component adoption and exact 5,677-key Arabic/English parity mean a visual redesign would
   destroy value; meanwhile **~144 of 328 routed screens are absent from navigation**. Redesign the
   information architecture, keep the design system. (Part 5)

**What this review got wrong, three times.** "Not found" is not "does not exist": the entitlement
subsystem, the `reporting` export toolkit, and an entire tracked 98-file audit evidence vault (k6 load
tests, axe/Lighthouse accessibility, gitleaks, trivy, bandit, jscpd, JaCoCo coverage) were each asserted
absent before being located. Every correction is recorded in place rather than quietly fixed — Part 3
§7.1, Part 7 §1. A reader should weight the corrections when judging the rest.

---
"""

with io.open(OUT, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(FRONT + "\n")
    fh.write("\n".join(toc) + "\n\n---\n\n")
    for _i, _t, body in bodies:
        fh.write(body + "\n---\n\n")
    fh.write(
        "## Document control\n\n"
        "This file is **generated**. Edit the sources under `TSS-GRC-Platform-Bible/` and re-run\n"
        "`py TSS-GRC-Platform-Bible/Appendix/scripts/build_single_report.py`. The sources remain\n"
        "authoritative; this consolidation exists so the assessment can be read, reviewed or sent as one\n"
        "document.\n\n"
        "The pre-existing 46-file reference corpus (product constitution, solution architecture, PDS,\n"
        "business-process design, data architecture, state machines, integration and security architecture,\n"
        "Saudi readiness, ADR-001…008, backlog) is **not** inlined here: much of it is superseded on\n"
        "measured facts, and Part 7 records a per-document verdict on all of it. It stays under\n"
        "`TSS-GRC-Platform-Bible/`, indexed by its own\n"
        "[README](TSS-GRC-Platform-Bible/README.md).\n")

lines = open(OUT, encoding="utf-8").read().count("\n") + 1
print(f"wrote {OUT}")
print(f"  {lines:,} lines · {os.path.getsize(OUT):,} bytes · {len(PARTS)} parts")
