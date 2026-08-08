import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-163 — the population `routeReachability.test.ts` deliberately does not measure: **detail
 * routes.** No detail route may be unreachable.
 *
 * <p><b>Read that test first.</b> F2-07b's guard answers "is every <i>static</i> route linked from
 * somewhere?" and holds the answer at zero unexplained orphans. It reaches that answer by filtering
 * parameterised routes out entirely:
 * <pre>all.filter(r =&gt; !r.includes(':') &amp;&amp; !r.includes('*'))</pre>
 * with the rationale <i>"Parameterised routes (/x/:id) and splats are detail views, legitimately
 * off-nav."</i> That is right about the <b>sidebar</b> and wrong about <b>reachability</b>: a detail
 * view is legitimately absent from a <i>menu</i>, not from <b>its own list</b>. This test measures
 * the difference, and F2-163 closed it: **5 detail routes had no inbound link; all 5 now have one.**
 *
 * <h3>⚠⚠ The first version of this test asserted 18, and 13 of those were its own false positives</h3>
 * It matched `to:`/`href:`/`route:`/`path:`/`to=`/`href=`/`navigate(` — and was blind to
 * <b>`rowHref`</b>, `DataTable`'s per-row navigation prop, whose value is a <i>function returning a
 * path</i>:
 * <pre>rowHref={(r) =&gt; `/ai/models/${'${'}r.externalId}`}</pre>
 * `DataTable` turns that into `onClick -&gt; navigate(href)` plus an Enter handler, and it is <b>the</b>
 * canonical way this codebase links a list to its detail screen — 34 sites. Thirteen of the eighteen
 * were linked all along, from exactly the list the finding said should link them. Four register pages
 * also destructure `useNavigate()` as `nav`, which no `navigate\(` pattern sees.
 *
 * <p>🔑 <b>The lesson is the one this test was written to teach, turned on its author.</b> F2-07 was
 * filed on a figure ~7× too large because navigation has several surfaces and it counted one. This
 * test then counted three and missed the fourth — the dominant one for detail screens. ⚙ <b>A
 * plausible answer that confirms the story you are already telling is where measurement stops.</b>
 * All 18 were `:param` routes and the narrative was tidy, so the 13 counter-examples were never
 * opened one by one. Eighteen is a small enough population to adjudicate by hand; it was not.
 *
 * <p>⚙ <b>And note what could not have caught it.</b> The vacuity check below passed (the scan saw
 * 300+ targets), and mutation testing the gate passed 7/7. <b>Mutation proves a gate notices changes
 * to itself; it cannot prove the gate's definition matches the world.</b> What caught it was opening
 * the first list page the finding accused. The defence now in place is a second implementation
 * (`Appendix/scripts/route_reconciliation.py`) using a deliberately different definition — any
 * `'/…'` literal outside `/api/` — which must agree; plus the two witnesses below.
 *
 * <h3>🔴 What survived the correction, and it is the item that mattered</h3>
 * `/bcm/exercises/run/:runId/{facilitate,observe,participate}` — 1,862 lines — had <b>no textual
 * reference anywhere in `src/`</b>, not merely no link: `listRunsForExercise` and `startRun` were
 * imported by those three pages and by nothing else, so <b>no screen listed a run</b>. F2-161 fixed
 * <i>who may drive an exercise run</i> on those exact screens on 2026-08-06. ⚙ The authorisation was
 * corrected on three screens that had no entry point: a guard audit cannot tell that nothing routes
 * to a page, and a navigation audit cannot tell that its guard was wrong. `ExerciseRunsPage` is now
 * their list.
 *
 * <p>The other two survivors were worse than unreachable — they were the <i>governed</i> path.
 * `/workflows/erm/risk-{treatment,acceptance}/:riskId` are the only UI routes that write a canonical
 * `risk_lc.treatment_plans` row (which `RiskService.close` requires before a HIGH or over-appetite
 * risk may close) and a formal time-boxed acceptance. Unbound, both wizards save a generic inbox
 * draft and toast <i>"open from a risk to record it"</i> — which nothing let the user do. Both are
 * now launched from `RiskDetailPage`. See F2-164 for the bypass that remains.
 */
describe('F2-163 — a detail route with no inbound link is unreachable, not merely off-nav', () => {
  const SRC = join(__dirname, '..');

  /**
   * Detail routes that are unreachable **on purpose**, each with the reason. **Empty, and that is
   * the point** — F2-163 took the population to zero, so this is an invariant, not a ceiling.
   *
   * ⚙ A ceiling tolerates the population it measures; once a sweep lands, leaving the ceiling in
   * place makes a tolerated orphan indistinguishable from a reviewed one. Same conversion
   * `BARE_EXPORT_CEILING` got when F2-61(c) swept it to zero. Adding an entry here is a claim that a
   * detail screen is *meant* to be openable only by URL — write the reason, not a suppression.
   */
  const UNREACHABLE_BY_DESIGN: Record<string, string> = {};

  /**
   * Every way this codebase expresses "somewhere the user can go".
   *
   * ⚠ Order of business when adding a navigation idiom: add it HERE and to
   * `route_reconciliation.py` in the same commit, or the two implementations stop agreeing and the
   * quieter one wins.
   */
  const LINK_KEYS: readonly RegExp[] = [
    /to:\s*'([^']+)'/g,
    /href:\s*'([^']+)'/g,
    /route:\s*'([^']+)'/g,        // lib/workflowCatalog.ts — the key both scans first missed
    /path:\s*'([^']+)'/g,
    /to="([^"]+)"/g,
    /href="([^"]+)"/g,
    /to=\{\s*['"`](\/[^'"`]+)/g,  // <Link to={`/x/${id}`}> — 11 routes hang off this alone
    // ⚠⚠ The surface the first version of this test was blind to: a JSX prop whose VALUE IS A
    // FUNCTION returning a path. `rowHref` (DataTable, 34 sites) is how a list links its detail
    // screen here; `viewLink`, `newLink`, `backTo` and `goTo` are the same shape. The suffix
    // alternation is case-SENSITIVE, which is why lowercase `to={` needs its own pattern above —
    // dropping it moved 11 genuinely-linked routes into the orphan list.
    /\b\w*(?:Href|Link|To|Route|Path)=\{[^}]*?['"`](\/[^'"`]+)/g,
    // Any alias of useNavigate(): four register pages destructure it as `nav`.
    /\b(?:nav|navigate|go|push)\(\s*['"`](\/[^'"`]+)/g,
  ];

  function walk(dir: string, out: string[] = []): string[] {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        if (name === 'node_modules' || name === 'dist') continue;
        walk(full, out);
      } else if (/\.tsx?$/.test(name)) {
        out.push(full);
      }
    }
    return out;
  }

  const rel = (f: string) => f.slice(SRC.length + 1).split('\\').join('/');
  const files = walk(SRC).map(f => ({ path: rel(f), body: readFileSync(f, 'utf8') }));

  /** `${…}` -> `:p`, query/hash dropped. Without this, no detail route ever matches its own link. */
  const norm = (t: string) =>
    (t.replace(/\$\{[^}]*\}/g, ':p').split('?')[0].split('#')[0].replace(/\/$/, '') || '/');

  /** The declared no-shell set — token-entry screens have no list to be linked from. */
  function barePrefixes(): string[] {
    const src = readFileSync(join(SRC, 'lib', 'publicPaths.ts'), 'utf8');
    // ⚠ Anchored on `export const`: `/BARE_PREFIXES[^=]*=\s*\[/` still matched after the declaration
    // was renamed away, because line 3 of that file is a COMMENT mentioning the name and `[^=]*`
    // spanned from there to the array. Mutation caught it. A textual scan cannot tell a declaration
    // from a mention.
    const m = /export\s+const\s+BARE_PREFIXES\b[^=]*=\s*\[([\s\S]*?)\]/.exec(src);
    expect(m, 'BARE_PREFIXES not found in lib/publicPaths.ts — this test refuses to guess which '
      + 'routes are entered by token rather than by click').toBeTruthy();
    return [...m![1].matchAll(/'([^']+)'/g)].map(x => x[1]);
  }

  function detailRoutes(): string[] {
    const app = files.find(f => f.path === 'App.tsx');
    expect(app, 'App.tsx moved — this test needs repointing').toBeDefined();
    const bare = barePrefixes();
    const out = new Set<string>();
    for (const m of app!.body.matchAll(/<Route\s+([^>]*?)path="([^"]+)"([^>]*?)\/?>/g)) {
      const p = m[2];
      if (!p.includes(':')) continue;                                  // F2-07b owns the static set
      const el = /element=\{<(\w+)/.exec(m[1] + m[3]);
      if (el && (el[1] === 'Navigate' || el[1] === 'RedirectPreservingQuery')) continue;
      if (bare.some(b => p === b || p.startsWith(b))) continue;        // token entry, no list
      out.add(p);
    }
    return [...out];
  }

  function linkTargets(): Set<string> {
    const found = new Set<string>();
    for (const { path, body } of files) {
      if (path === 'App.tsx' || /\.test\.tsx?$/.test(path)) continue;
      for (const re of LINK_KEYS) {
        for (const m of body.matchAll(re)) {
          const t = norm(m[1]);
          if (t.startsWith('/') && !t.startsWith('/api/')) found.add(t);
        }
      }
    }
    return found;
  }

  /** A `:param` segment accepts any single segment; `:p` is a normalised template hole. */
  function isLinked(route: string, targets: Set<string>): boolean {
    const a = route.replace(/\/$/, '').split('/');
    for (const t of targets) {
      const b = t.split('/');
      if (a.length !== b.length) continue;
      if (a.every((seg, i) => seg.startsWith(':') || b[i] === ':p' || seg === b[i])) return true;
    }
    return false;
  }

  const unlinked = () => {
    const targets = linkTargets();
    return detailRoutes().filter(r => !isLinked(r, targets)).sort();
  };

  it('the scan is not vacuous — and it sees the two surfaces that fooled its first version', () => {
    expect(detailRoutes().length,
      'App.tsx should declare ~60 parameterised routes').toBeGreaterThan(50);
    const targets = linkTargets();
    expect(targets.size).toBeGreaterThan(300);
    // The normalisation this test adds: without it every detail route reads as orphaned.
    expect(isLinked('/workflows/requests/:id', targets),
      'a template-literal link must match its parameterised route').toBe(true);
    // The registry key both scans first missed.
    expect(targets.has('/workflows/pdpl/ropa')).toBe(true);

    // ⚠⚠ WITNESSES for the blind spot that made this test's first answer wrong by 13. Neither of
    // these is reachable by any pattern except the two added in the correction, so if a future edit
    // drops `rowHref` or the nav-alias key from LINK_KEYS, these fail instead of the orphan count
    // silently inflating.
    expect(targets.has('/ai/models/:p'),
      'rowHref={r => `/ai/models/${r.externalId}`} in AiModelRegisterPage is DataTable row '
      + 'navigation — 34 sites use it and it is the only link 13 detail screens have. If this is '
      + 'false, LINK_KEYS lost the function-valued-prop pattern and the orphan list is fiction.')
      .toBe(true);
    expect(targets.has('/opres/assessments/:p'),
      'OpResDashboardPage navigates with `nav(...)`, an alias of useNavigate() that no '
      + '`navigate\\(` pattern matches.').toBe(true);
  });

  it('no detail route is unreachable', () => {
    const orphans = unlinked().filter(r => !(r in UNREACHABLE_BY_DESIGN));

    expect(orphans,
      `${orphans.join(', ')} is a detail route that no source navigates to — it can only be `
      + 'reached by typing a URL with an id in it. Link it from the screen that owns it (for a '
      + 'detail page, its list; `rowHref` on the list\'s DataTable is the house idiom). Being '
      + 'absent from the SIDEBAR is fine for a detail view; being absent from its own list is not. '
      + 'See TSS-GRC-Platform-Bible/UX/ROUTE_RECONCILIATION.md.').toEqual([]);
  });

  it('the by-design exemption map stays honest', () => {
    // Two ways an exemption rots: it names a route that no longer exists, or it exempts a route
    // that is in fact linked — which reads as "reviewed and accepted" when it is neither.
    const declared = new Set(detailRoutes());
    const keys = Object.keys(UNREACHABLE_BY_DESIGN);
    expect(keys.filter(r => !declared.has(r)),
      'UNREACHABLE_BY_DESIGN names routes that no longer exist').toEqual([]);
    const stillUnlinked = new Set(unlinked());
    expect(keys.filter(r => !stillUnlinked.has(r)),
      'UNREACHABLE_BY_DESIGN exempts a route that now has an inbound link — drop the entry')
      .toEqual([]);
  });

  it('the five F2-163 routes stay reachable — pinned individually, not by count', () => {
    // Named one by one: a bulk nav refactor that dropped two of the five would otherwise look
    // like a count of three, which reads as progress.
    const targets = linkTargets();
    for (const [route, via] of [
      ['/bcm/exercises/run/:runId/facilitate',   'ExerciseRunsPage — the run list that did not exist'],
      ['/bcm/exercises/run/:runId/observe',      'ExerciseRunsPage'],
      ['/bcm/exercises/run/:runId/participate',  'ExerciseRunsPage'],
      ['/workflows/erm/risk-treatment/:riskId',  'RiskDetailPage — the only path writing risk_lc.treatment_plans'],
      ['/workflows/erm/risk-acceptance/:riskId', 'RiskDetailPage — the only path writing a formal acceptance'],
    ] as const) {
      expect(isLinked(route, targets), `${route} is unreachable again (was linked from ${via})`)
        .toBe(true);
    }
  });
});
