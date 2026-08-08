# -*- coding: utf-8 -*-
"""F2-07a — measure route reachability and regenerate UX/ROUTE_RECONCILIATION.md.

Run from anywhere:  py TSS-GRC-Platform-Bible/Appendix/scripts/route_reconciliation.py

WHAT THIS ANSWERS. [[F2-07]] is filed P1 Critical on the claim that "roughly 144 routed screens are
reachable only by typing a URL", and that "nobody can tell which is which". This is the artefact its
recommendation asks for. Measured 2026-08-08, the genuinely unreachable population is **20**.

WHY THE FIGURE WAS ~7x TOO LARGE, AND IT IS STRUCTURAL RATHER THAN ARITHMETICAL: navigation in this
application has THREE surfaces and the finding counts one. Besides `Sidebar.tsx` there are
module-dashboard link tiles (`href:` props) and ordinary in-page links, plus registries such as
`lib/workflowCatalog.ts` whose `route:` field `WorkflowHubPage` renders as `<Link to={w.route!}>`.

THE MEASUREMENT TOOK FOUR PASSES AND EACH MOVED THE ANSWER. Recorded because every intermediate
answer was plausible and three were wrong:

    pass 1  sidebar only                        -> 139   (dashboard tiles + detail links miscounted;
                                                          4 `RedirectPreservingQuery` missed)
    pass 2  + navigation SYNTAX                 ->  53   (19 FALSE POSITIVES: the wizard estate is
                                                          linked through a REGISTRY, not syntax)
    pass 3  + any '/...' string literal         ->   6   (FALSE NEGATIVES: an auth-allowlist entry
                                                          in publicPaths.ts is not a way in)
    pass 4  navigational refs only; allowlists
            named; bare set read from the code  ->  20

Three transferable lessons:
  * A registry-driven link is invisible to a scan for link syntax. That was a third of the answer.
  * When the code already declares the classification you are inferring, infer nothing. A hand-written
    pattern for "anonymous by design" missed two ~400-line screens; `BARE_PREFIXES` declares the set.
  * The same file can be authoritative for one question and misleading for another: `publicPaths.ts`
    is EXCLUDED as a navigation source and TRUSTED as the anonymous authority.

The CI gate is `tss-grc-ui-v2/src/__tests__/routeReachability.test.ts` — an independent TypeScript
implementation that agrees with this one, and mutation-proven 7/7.
"""
import io
import os
import re
import sys
import json
from collections import Counter, defaultdict

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
FE = os.path.join(ROOT, "tss-grc-ui-v2", "src")
APP = os.path.join(FE, "App.tsx")
SIDEBAR = os.path.join(FE, "components", "layout", "Sidebar.tsx")
OUT = os.path.join(ROOT, "TSS-GRC-Platform-Bible", "UX", "ROUTE_RECONCILIATION.md")

REDIRECT_COMPONENTS = {"Navigate", "RedirectPreservingQuery"}
# Named, not globbed: these hold route paths as DATA for a purpose other than navigation.
NON_NAVIGATIONAL = {"lib/publicPaths.ts", "lib/help.ts"}

NAV_PATTERNS = [
    # ⚠⚠ The first version of this list omitted the next TWO entries and the script reported 18
    # unreachable detail screens. THIRTEEN of them were linked, from exactly the list the finding
    # said should link them. `rowHref` is `DataTable`'s per-row navigation prop -- its value is a
    # FUNCTION returning a path (`rowHref={r: `/ai/models/${r.externalId}`}`), which `DataTable`
    # turns into `onClick -> navigate(href)`. 34 sites; the canonical list->detail idiom here.
    # `nav(...)` is `useNavigate()` destructured under another name, in four register pages.
    re.compile(r"\b\w*(?:Href|Link|To|Route|Path)=\{[^}]*?['\"`](/[^'\"`]+)"),
    re.compile(r"\b(?:nav|navigate|go|push)\(\s*['\"`](/[^'\"`]+)"),
    re.compile(r"navigate\(\s*['\"`](/[^'\"`]*)['\"`]"),
    re.compile(r"\bto=\{?\s*['\"`](/[^'\"`]*)['\"`]"),
    re.compile(r"\b(?:to|href|route|path)\s*:\s*['\"`](/[^'\"`]*)['\"`]"),
    re.compile(r"\bhref=\{?\s*['\"`](/[^'\"`]*)['\"`]"),
    re.compile(r"location\.(?:href|assign|replace)\s*=?\s*\(?\s*['\"`](/[^'\"`]*)['\"`]"),
    re.compile(r"window\.open\(\s*['\"`](/[^'\"`]*)['\"`]"),
]

# Witnesses for the blind spot above: each is reachable ONLY through one of the two patterns added
# in the correction, so losing either fails the run instead of quietly inflating the orphan count.
NAV_PATTERN_WITNESSES = {
    "/ai/models/:p": "rowHref on AiModelRegisterPage -- DataTable row navigation",
    "/opres/assessments/:p": "nav(...) on OpResDashboardPage -- a useNavigate alias",
}


def read(p):
    return io.open(p, encoding="utf-8", errors="replace", newline="").read()


def walk(root):
    for dp, _d, fs in os.walk(root):
        for f in fs:
            if f.endswith((".ts", ".tsx")):
                yield os.path.join(dp, f)


def norm(t):
    t = re.sub(r"\$\{[^}]*\}", ":p", t).split("?")[0].split("#")[0]
    return t.rstrip("/") or "/"


def bare_prefixes():
    """The declared no-shell route set.

    WARNING: the pattern is anchored on `export const`. `BARE_PREFIXES[^=]*=\\s*\\[` matched even after
    the declaration was renamed away, because line 3 of the file is a COMMENT mentioning the name and
    `[^=]*` spanned from there to the array. Mutation caught it; a textual scan cannot tell a
    declaration from a mention.
    """
    src = read(os.path.join(FE, "lib", "publicPaths.ts"))
    m = re.search(r"export\s+const\s+BARE_PREFIXES\b[^=]*=\s*\[(.*?)\]", src, re.S)
    assert m, "BARE_PREFIXES not found - refusing to guess which routes are anonymous by design"
    out = [x.group(1) for x in re.finditer(r"'([^']+)'", m.group(1))]
    assert len(out) >= 8, "only %d bare prefixes parsed" % len(out)
    return out


def intentionally_off_nav():
    """F2-07b's documented allow-list, read from its test rather than duplicated here.

    WARNING: this exists because I duplicated a judgement already made. My first pass classified
    `/crisis-management` and `/bcm-dependency/legacy` as "needs a product decision". Both were already
    decided and documented in `routeReachability.test.ts` - "crisis console entry, opened by direct
    link / incident comms" and "legacy alias retained for old bookmarks". Reading the existing
    allow-list is the same discipline as reading BARE_PREFIXES: when the repo already records the
    decision, do not re-make it.
    """
    src = read(os.path.join(FE, "__tests__", "routeReachability.test.ts"))
    m = re.search(r"const\s+INTENTIONALLY_OFF_NAV[^=]*=\s*\{(.*?)\n\};", src, re.S)
    assert m, "INTENTIONALLY_OFF_NAV not found in routeReachability.test.ts"
    out = {x.group(1) for x in re.finditer(r"^\s*'([^']+)':", m.group(1), re.M)}
    # A FLOOR, not the exact count: the list is F2-07b's to grow or shrink, and asserting a precise
    # number here would make this script fail on somebody else's legitimate edit. 14 entries today.
    assert len(out) >= 10, "only %d allow-list entries parsed - the regex has probably rotted" % len(out)
    return out


def parse_routes():
    app = read(APP)
    comp_file = {}
    for m in re.finditer(r"const\s+(\w+)\s*=\s*lazy\([\s\S]{0,200}?import\('([^']+)'\)", app):
        comp_file.setdefault(m.group(1), m.group(2))
    for m in re.finditer(r"^import\s+(?:\{\s*)?(\w+)(?:\s*\})?\s+from\s+'([^']+)'", app, re.M):
        comp_file.setdefault(m.group(1), m.group(2))

    out = []
    for m in re.finditer(r'<Route\s+([^>]*?)path="([^"]+)"([^>]*?)/?>', app, re.S):
        el = re.search(r"element=\{<(\w+)", m.group(1) + m.group(3))
        comp = el.group(1) if el else None
        r = {"path": m.group(2), "component": comp,
             "redirect": comp in REDIRECT_COMPONENTS,
             "file": None, "folder": None, "lines": None}
        rel = (comp_file.get(comp or "") or "").replace("./", "").lstrip("/")
        for cand in (os.path.join(FE, rel + ".tsx"), os.path.join(FE, rel + ".ts")):
            if rel and os.path.isfile(cand):
                r["file"] = os.path.relpath(cand, FE).replace("\\", "/")
                r["folder"] = os.path.dirname(r["file"])
                r["lines"] = read(cand).count("\n") + 1
                break
        out.append(r)
    return out


def nav_targets():
    return {norm(m.group(1)) for m in re.finditer(r"\bto:\s*'([^']+)'", read(SIDEBAR))}


def inbound():
    links = defaultdict(set)
    for path in walk(FE):
        rel = os.path.relpath(path, FE).replace("\\", "/")
        if path in (APP, SIDEBAR) or rel in NON_NAVIGATIONAL:
            continue
        if re.search(r"\.(?:test|spec)\.tsx?$", rel):
            continue
        src = read(path)
        for pat in NAV_PATTERNS:
            for m in pat.finditer(src):
                t = norm(m.group(1))
                if not t.startswith("/api/"):
                    links[t].add(rel)
    for w, why in NAV_PATTERN_WITNESSES.items():
        assert w in links, (
            "%s is not in the inbound set, so NAV_PATTERNS has lost the pattern that finds it (%s). "
            "That is the exact defect that made this script's first answer 18 instead of 5 -- the "
            "orphan list would now be fiction. Fix NAV_PATTERNS, do not relax this assertion."
            % (w, why))
    return links


def reaches(route_path, target):
    rp = route_path.rstrip("/") or "/"
    if rp == target:
        return True
    if "*" in rp:
        return target.startswith(rp.split("*")[0].rstrip("/"))
    a, b = rp.split("/"), target.split("/")
    return len(a) == len(b) and all(x.startswith(":") or y == ":p" or x == y for x, y in zip(a, b))


# ⚠⚠ The 18 routes this script's FIRST version reported as unreachable. THIRTEEN were its own false
# positives -- see the rowHref note on NAV_PATTERNS. Five were real and F2-163 linked them. Kept as a
# dict rather than deleted because the record of a wrong measurement is worth more than a clean file,
# and because main() asserts every one of the 18 now measures as reachable: the false positives prove
# the patterns were the bug, and the five prove the fix landed.
WAS_REPORTED_ORPHAN = {
    "/ai/models/:externalId": (
        "FALSE POSITIVE", "`rowHref` on `AiModelRegisterPage:367` - linked all along"),
    "/rrp/cycles/:id": (
        "FALSE POSITIVE", "`rowHref` on `RrpCyclesPage:228` **and** `RrpDashboardPage:215`"),
    "/cpty/counterparties/:id": (
        "FALSE POSITIVE", "`rowHref` on `CptyCounterpartiesPage:263` + `CptyDashboardPage:285`"),
    "/complaints/:id": ("FALSE POSITIVE", "`rowHref` on `ComplaintsRegisterPage:218`"),
    "/cyber/attack-surface/assets/:externalId": (
        "FALSE POSITIVE", "`rowHref` on `AttackSurfacePage:519`"),
    "/regreporting/filings/:id": (
        "FALSE POSITIVE", "`rowHref` on `RegReportingFilingsPage:146` + the dashboard, twice"),
    "/opres/assessments/:id": (
        "FALSE POSITIVE", "`rowHref` on `OpResAssessmentsPage:238`; `nav(...)` on the dashboard"),
    "/reginspect/inspections/:id": (
        "FALSE POSITIVE", "`rowHref` on `RegInspectInspectionsPage:296` + the dashboard"),
    "/privacy/subprocessors/:externalId": (
        "FALSE POSITIVE", "`rowHref` on `SubprocessorsPage:235`"),
    "/ethics/whistleblower/cases/:id": (
        "FALSE POSITIVE", "`rowHref` on `WhistleblowerCasesPage:229`"),
    "/privacy/dsrs/:id": (
        "FALSE POSITIVE", "`rowHref` on `PrivacyPage:384` - the tab table DOES link it"),
    "/privacy/breaches/:id": ("FALSE POSITIVE", "`rowHref` on `PrivacyPage:402`"),
    "/privacy/ropa/:id": ("FALSE POSITIVE", "`rowHref` on `PrivacyPage:414`"),
    "/bcm/exercises/run/:runId/facilitate": (
        "REAL - FIXED", "\U0001f534 `ExerciseRunsPage` - the run list that did not exist"),
    "/bcm/exercises/run/:runId/observe": (
        "REAL - FIXED", "\U0001f534 `ExerciseRunsPage`"),
    "/bcm/exercises/run/:runId/participate": (
        "REAL - FIXED", "\U0001f534 `ExerciseRunsPage`"),
    "/workflows/erm/risk-treatment/:riskId": (
        "REAL - FIXED", "`RiskDetailPage` - the only path writing `risk_lc.treatment_plans`"),
    "/workflows/erm/risk-acceptance/:riskId": (
        "REAL - FIXED", "`RiskDetailPage` - the only path writing a formal acceptance"),
}

DOC = """# Route reconciliation matrix — F2-07a

**Measured 2026-08-08** against `tss-grc-ui-v2/src/App.tsx`, `components/layout/Sidebar.tsx` and every
non-test source in `src/`. Regenerate with `Appendix/scripts/route_reconciliation.py`; **every figure
below is interpolated from that run, not transcribed.** Gated by
`tss-grc-ui-v2/src/__tests__/routeReachability.test.ts`, an independent TypeScript implementation that
agrees with this one and fails if a new orphan appears.

---

## 0. The headline: F2-07's population is %(orphan)d screens, not 144

[[F2-07]] is filed **P1 Critical** on the claim that *"roughly 144 routed screens are reachable only by
typing a URL"*, and that *"nobody can tell which is which"*. The second half was true — there was no
artefact. The first half is **not**.

| | Count |
|---|---:|
| Routes registered in `App.tsx` | **%(total)d** |
| − redirects (`<Navigate>`, `RedirectPreservingQuery`) — not screens | %(redirects)d |
| − bare routes: rendered without the authenticated shell, **by design** | %(bare)d |
| **= authenticated screens** | **%(auth)d** |
|   reachable from the **sidebar** | **%(sidebar)d** |
|   reachable from a **link or a dashboard tile** only | **%(linked)d** |
|   **no way in at all** | **%(orphan)d** |

**Why the finding was ~7× too large: navigation in this application has FOUR surfaces, and F2-07
counts one.** Besides `Sidebar.tsx` there are module-dashboard link tiles (`href:` props on `Kpi` and
action components), ordinary in-page links, registries such as `lib/workflowCatalog.ts` whose `route:`
field the workflow hub renders as `<Link to={w.route!}>` — and **`rowHref`**, `DataTable`'s per-row
navigation prop, which is how a list links its own detail screen (34 sites). A screen opened by a tile
on its module dashboard, or by clicking its row in a table, is reachable; it is simply not a menu item,
which for a detail screen is correct design rather than a defect.

⚠ **This does not vacate F2-07.** Its *other* half stands untouched and is the more serious one: a
**%(sidebar)d-item sidebar is beyond navigable**, and the workspace IA in the recommendation is still
the right remedy. What changes is the size and shape of the remediation list — from *"classify 144
screens"* to *"add 5 missing links and take 0 product decisions"*, all 5 of which F2-163 has added.

---

## 1. How the measurement was arrived at — six passes, and pass 4 SHIPPED WRONG

Recorded because the intermediate answers were each plausible, and **the one that got written into a
finding, a register row and a CI gate was among the wrong ones.**

| Pass | Definition of "reachable" | Answer | What it got wrong |
|---|---|---:|---|
| 1 | a `Sidebar.tsx` entry | 139 | counted dashboard tiles and detail links as unreachable; missed 4 `RedirectPreservingQuery` redirects |
| 2 | + navigation **syntax** (`navigate()`, `to=`) | 53 | **19 false positives** — the `/workflows/*` wizards are linked through a *registry*, not link syntax |
| 3 | + **any** `'/…'` string literal | 6 | dismissed for false negatives (an entry in the `publicPaths.ts` auth allowlist is not a way in) — but **this was the closest answer, and tightening moved away from it** |
| 4 | navigational references only, allowlists named, bare set from `BARE_PREFIXES` | ~~18~~ | ⚠⚠ **SHIPPED, AND WRONG BY 13** — blind to `rowHref`, the prop that links a list row to its detail screen |
| 5 | + `rowHref`-shaped props and `useNavigate()` aliases | **5** | — |
| 6 | pass 5 cross-checked against an independent wide definition | **5** | both agree; 11 disagreements in between were all genuine `<Link to={`…`}>` and each was opened by hand |

🔑🔑 **The correction is the same error the finding it corrects was filed for.** F2-07 was ~7× too
large because it counted one navigation surface. Pass 4 counted three and missed the fourth — the
*dominant* one for detail screens. ⚙ **A plausible answer that confirms the story you are already
telling is where measurement stops.** All 18 were `:param` routes and the narrative was tidy
("detail screens absent from their own list"), so the 13 counter-examples were never opened one at a
time. Eighteen is a small enough population to adjudicate by hand; it was not.

⚙ **What could not have caught it, and this bounds what the two techniques are worth.** The gate's
non-vacuity check passed — it saw 300+ link targets. Mutation testing the gate passed 7/7.
**Mutation proves a gate notices changes to itself; it cannot prove the gate's definition matches the
world.** What caught it was opening the first list page the finding accused, before writing a link
into it. Two defences are now in place: a **witness assertion** per fragile pattern (a route reachable
only via `rowHref`, and one only via a `nav()` alias — losing either pattern now fails the run instead
of inflating the orphan count), and **two implementations with deliberately different definitions**
that must agree.

Three further transferable lessons:

* \U0001f511 **A registry-driven link is invisible to a scan for link syntax.** Pass 2 reported every workflow
  wizard as stranded; `lib/workflowCatalog.ts` holds the path as data and `WorkflowHubPage.tsx` renders
  a `<Link>` over it. That was a third of the answer.
* \U0001f511 **When the code already declares the classification you are inferring, infer nothing.** A
  hand-written pattern for "anonymous by design" missed two ~400-line screens
  (`/ethics/whistleblower/submit`, `/bcm/crisis/mobile`). `lib/publicPaths.ts` declares the set as
  `BARE_PREFIXES` — %(nbare)d prefixes — and is the authority used here.
* ⚠ **The same file can be authoritative for one question and misleading for another.**
  `publicPaths.ts` is excluded as a *navigation* source (an allowlist is not a link) and trusted as the
  *anonymous* authority. Both treatments are named in the script rather than left to a glob.

➕ A fourth lesson came from mutation-testing the gate: `/BARE_PREFIXES[^=]*=\\s*\\[/` **still matched
after the declaration was renamed away**, because line 3 of `publicPaths.ts` is a comment mentioning
the name. **A textual scan cannot tell a declaration from a mention** — the pattern is anchored on
`export const` in both implementations.

---

## 2. Orphaned screens today: **%(orphan)d**

%(orphan_table)s

### 2a. The 5 that were real — %(real_lines)s lines, now linked

| Route | Component | Lines | Verdict | Reached from |
|---|---|---:|---|---|
%(real_rows)s

\U0001f534 **The BCM exercise runtime was the sharpest item, because of what happened to it two days before
the measurement.** `FacilitatorConsolePage` (769), `ObserverDashboardPage` (742) and
`ParticipantViewPage` (351) — 1,862 lines — had **no textual reference anywhere in `src/`**, not merely
no link: `listRunsForExercise` and `startRun` were imported by those three pages and nothing else, so
**no screen listed a run**. [[F2-161]] fixed *who may drive an exercise run* on these exact screens on
2026-08-06, converting the guard so a facilitator could reach their own run instead of only a platform
administrator. ⚙ **The authorisation was corrected on three screens that had no entry point.** Two
instruments looked at the same code from opposite ends and neither could see what the other saw: a
guard audit cannot tell that nothing routes to the page, and a navigation audit cannot tell the guard
was wrong. `ExerciseRunsPage` is now their list, reached from the exercise row in `/bcm?tab=exercises`.

\U0001f534 **The other two were worse than unreachable — they were the *governed* path.**
`/workflows/erm/risk-{treatment,acceptance}/:riskId` are the only UI routes that write a canonical
`risk_lc.treatment_plans` row and a formal, time-boxed, CRO-approved acceptance. `RiskService.close`
**requires** such a plan before a HIGH or over-appetite risk may close. Unbound, both wizards save a
generic inbox draft and toast *"open from a risk to record it"* — **which nothing in the UI let the
user do.** Both are now launched from `RiskDetailPage`. ⚠ The asymmetry that remains is filed as
**F2-164**: the plain *Accept* button on the same page is a bare status transition at `RISK:WRITE` and
it **exempts** the risk from the close gate, so the reachable path bypasses the control the
unreachable one enforced.

### 2b. The 13 that were this script's own false positives

Every one is linked from exactly the list the finding said should link it — by `rowHref`, which the
first pass could not see. Kept in the record because a wrong measurement that is quietly deleted
teaches nothing.

| Route | Component | Lines | Verdict | The link that was always there |
|---|---|---:|---|---|
%(fp_rows)s

---

## 3. The %(linked)d link-only screens are reachable — here is what reaches them

This is the population F2-07 counts as URL-only. Top sources:

| Navigation surface | Screens it reaches |
|---|---:|
%(top_sources)s

`lib/workflowCatalog.ts` alone accounts for %(wf)d — the F8 wizard estate, launched from the workflow
hub. Any future reconciliation must treat a registry as a navigation surface.

---

## 4. What this changes in the register

* [[F2-07]] — the *"144 reachable only by URL"* figure is **superseded by 5**, now **%(orphan)d**; the
  %(sidebar)d-item sidebar and the workspace-IA recommendation are unaffected and remain open.
* [[F2-07a]] — **delivered**: this matrix, the generator, and the CI gate.
* [[F2-07b]] — already landed 8 socops pages into the sidebar. Its inline comment cites
  `/socops/awareness` as *"in the nav"*; it is in the socops **dashboard**, not the sidebar. Loose
  wording rather than a defect — recorded so the next reader does not re-open it.
* [[F2-163]] — **filed at 18, corrected to 5, and closed.** ⚠ Read §1 before quoting any figure from
  this document: the finding shipped with a headline that this script's own patterns had inflated by
  13, into a register row and a CI gate, and it was caught by opening a file rather than by any test.
* [[F2-164]] — **filed by this slice, not fixed.** Making the treatment/acceptance wizards reachable
  exposed that the *reachable* `Accept` button bypasses the very close gate the newly-reachable
  wizard satisfies. A navigation fix surfaced an authorisation defect; see §2a.
* [[F2-161]] — cross-reference the runtime entry-point gap in §2a.
"""


def main():
    BARE = bare_prefixes()
    routes = parse_routes()
    assert len(routes) > 250, "only %d routes parsed" % len(routes)
    nav = nav_targets()
    assert len(nav) > 150, "only %d nav targets parsed" % len(nav)
    links = inbound()
    assert len(links) > 200, "only %d inbound targets parsed" % len(links)

    OFF_NAV = intentionally_off_nav()
    print("F2-07b's INTENTIONALLY_OFF_NAV allow-list: %d documented routes" % len(OFF_NAV))

    for r in routes:
        r["nav"] = any(reaches(r["path"], n) for n in nav)
        r["documented"] = r["path"] in OFF_NAV
        r["linked_from"] = sorted({f for t, fs in links.items() if reaches(r["path"], t) for f in fs})
        r["bare"] = any(r["path"] == p or r["path"].startswith(p) for p in BARE)

    screens = [r for r in routes if not r["redirect"]]
    bare = [r for r in screens if r["bare"]]
    auth = [r for r in screens if not r["bare"]]
    sidebar = [r for r in auth if r["nav"]]
    linked = [r for r in auth if not r["nav"] and r["linked_from"]]
    orphan = [r for r in auth
              if not r["nav"] and not r["linked_from"] and not r["documented"]]
    documented = [r for r in auth if not r["nav"] and not r["linked_from"] and r["documented"]]

    by_path = {r["path"]: r for r in routes}
    # Every one of the 18 the first pass accused must now measure as reachable -- the 13 because the
    # patterns were the bug, the 5 because F2-163 linked them. This is the assertion that would have
    # failed on the day the wrong answer was written, had the patterns been right.
    still_orphan = {r["path"] for r in orphan}
    regressed = sorted(p for p in WAS_REPORTED_ORPHAN if p in still_orphan)
    assert not regressed, (
        "%s was reported unreachable, then measured reachable, and is unreachable again. Either a "
        "link was deleted or NAV_PATTERNS lost a shape." % regressed)
    vanished = sorted(p for p in WAS_REPORTED_ORPHAN if p not in by_path)
    assert not vanished, "WAS_REPORTED_ORPHAN names routes that no longer exist: %s" % vanished

    def rows_for(verdict_prefix):
        out = []
        for p, (verdict, note) in sorted(
                WAS_REPORTED_ORPHAN.items(), key=lambda kv: -(by_path[kv[0]]["lines"] or 0)):
            if not verdict.startswith(verdict_prefix):
                continue
            r = by_path[p]
            out.append("| `%s` | `%s` | %s | **%s** | %s |"
                       % (p, r["component"], r["lines"], verdict, note))
        return "\n".join(out)

    real_lines = sum(by_path[p]["lines"] or 0
                     for p, (v, _) in WAS_REPORTED_ORPHAN.items() if v.startswith("REAL"))
    stranded = sum(r["lines"] or 0 for r in orphan)

    body = DOC % {
        "total": len(routes), "redirects": len(routes) - len(screens), "bare": len(bare),
        "auth": len(auth), "sidebar": len(sidebar), "linked": len(linked), "orphan": len(orphan),
        "documented": len(documented), "offnav": len(OFF_NAV), "nbare": len(BARE),
        "stranded": "{:,}".format(stranded),
        "real_lines": "{:,}".format(real_lines),
        "real_rows": rows_for("REAL"),
        "fp_rows": rows_for("FALSE"),
        "orphan_table": (
            "**Zero.** Every parameterised and static screen in the application is reachable from the "
            "sidebar, a dashboard tile, an in-page link, a registry or its list row. The five that "
            "were not are in §2a; the thirteen that only appeared to be are in §2b."
            if not orphan else
            "⚠ **%d unreachable screen(s):**\n\n| Route | Component | Lines |\n|---|---|---:|\n%s"
            % (len(orphan), "\n".join(
                "| `%s` | `%s` | %s |" % (r["path"], r["component"], r["lines"])
                for r in sorted(orphan, key=lambda x: -(x["lines"] or 0))))),
        "top_sources": "\n".join(
            "| `%s` | %d |" % (f, n) for f, n in
            Counter(f for r in linked for f in r["linked_from"]).most_common(12)),
        "wf": sum(1 for r in linked if "lib/workflowCatalog.ts" in r["linked_from"]),
    }
    io.open(OUT, "w", encoding="utf-8", newline="\r\n").write(body)

    print("routes %d | redirects %d | bare %d | authenticated %d"
          % (len(routes), len(routes) - len(screens), len(bare), len(auth)))
    print("sidebar %d | link-only %d | documented off-nav %d | ORPHANED %d"
          % (len(sidebar), len(linked), len(documented), len(orphan)))
    print("stranded %d lines | the 18 first reported: all reachable (%d were false positives)"
          % (stranded, sum(1 for v, _ in WAS_REPORTED_ORPHAN.values() if v.startswith("FALSE"))))
    print("-> %s" % os.path.relpath(OUT, ROOT))
    return json.dumps({"orphans": sorted(r["path"] for r in orphan)}, ensure_ascii=False)


if __name__ == "__main__":
    main()
