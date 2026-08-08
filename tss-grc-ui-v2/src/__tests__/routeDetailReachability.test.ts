import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-07a — the population `routeReachability.test.ts` deliberately does not measure: **detail routes.**
 *
 * <p><b>Read that test first.</b> F2-07b's guard answers "is every <i>static</i> route linked from
 * somewhere?" and it does so well — it carries a 20-entry `INTENTIONALLY_OFF_NAV` allow-list where each
 * entry is a documented reason, and it holds the answer at zero unexplained orphans. It reaches that
 * answer by filtering parameterised routes out entirely:
 * <pre>all.filter(r =&gt; !r.includes(':') &amp;&amp; !r.includes('*'))</pre>
 * with the rationale <i>"Parameterised routes (/x/:id) and splats are detail views, legitimately
 * off-nav."</i>
 *
 * <p><b>That rationale is right about the sidebar and wrong about reachability, and this test measures
 * the difference.</b> A detail view is legitimately absent from a <i>menu</i> — nobody wants
 * `/risks/:id` in a sidebar. It is not legitimately absent from <b>its own list</b>. Measured
 * 2026-08-08: <b>18 detail routes, 8,900 lines of built screens, that no source navigates to at all</b>
 * — not from a list, not from a dashboard tile, not from anywhere. They can only be reached by typing a
 * URL with an id in it, which no user does.
 *
 * <p>🔑 <b>So the exclusion that made F2-07b's answer clean is what hid this.</b> Both instruments are
 * correct for the question they ask; the gap was between them. Full matrix and classification in
 * `TSS-GRC-Platform-Bible/UX/ROUTE_RECONCILIATION.md`.
 *
 * <h3>🔴 The item that matters most</h3>
 * `/bcm/exercises/run/:runId/{facilitate,observe,participate}` — 1,862 lines — have no way in. F2-161
 * fixed <i>who may drive an exercise run</i> on those exact screens on 2026-08-06. ⚙ The authorisation
 * was corrected on three screens that have no entry point: a guard audit cannot tell that nothing
 * routes to a page, and a navigation audit cannot tell that its guard was wrong.
 *
 * <h3>⚠ Traps this test inherits, and one it adds</h3>
 * <ul>
 *   <li><b>Inherited:</b> a registry-driven link is invisible to a scan for link syntax.
 *       `routeReachability.test.ts` records paying for this — its first pass reported 45 orphans and 22
 *       were the wizard estate, reached through `lib/workflowCatalog.ts`'s `route:` key. I rebuilt the
 *       scan from scratch and <b>hit the identical wall</b> (53 orphans, 19 of them wizards) because I
 *       wrote a new file instead of reading the one already there. `LINK_KEYS` below is deliberately
 *       the same shape as its.</li>
 *   <li><b>Added:</b> `${'{'}…{'}'}` holes must be normalised to a wildcard segment, or
 *       <code>navigate(`/privacy/dsrs/${'{'}id{'}'}`)</code> never matches `/privacy/dsrs/:id` and every
 *       detail screen reads as orphaned.</li>
 * </ul>
 */
describe('F2-07a — a detail route with no inbound link is unreachable, not merely off-nav', () => {
  const SRC = join(__dirname, '..');

  /**
   * Detail routes nothing links to, measured 2026-08-08. **This list may only shrink.**
   *
   * Each needs one link from the screen that owns it — for a detail page, its list. That is a
   * materially cheaper remedy than F2-07's IA redesign, and a different defect from being off-nav.
   */
  const UNLINKED_DETAIL: readonly string[] = [
    '/ai/models/:externalId',
    '/bcm/exercises/run/:runId/facilitate',
    '/bcm/exercises/run/:runId/observe',
    '/bcm/exercises/run/:runId/participate',
    '/complaints/:id',
    '/cpty/counterparties/:id',
    '/cyber/attack-surface/assets/:externalId',
    '/ethics/whistleblower/cases/:id',
    '/opres/assessments/:id',
    '/privacy/breaches/:id',
    '/privacy/dsrs/:id',
    '/privacy/ropa/:id',
    '/privacy/subprocessors/:externalId',
    '/reginspect/inspections/:id',
    '/regreporting/filings/:id',
    '/rrp/cycles/:id',
    '/workflows/erm/risk-acceptance/:riskId',
    '/workflows/erm/risk-treatment/:riskId',
  ];

  /**
   * Detail routes that are reached by a token in an emailed link, not by a click. Read from
   * `lib/publicPaths.ts` at run time rather than listed here — see `barePrefixes()`.
   */
  const LINK_KEYS: readonly RegExp[] = [
    /to:\s*'([^']+)'/g,
    /href:\s*'([^']+)'/g,
    /route:\s*'([^']+)'/g,        // lib/workflowCatalog.ts — the key both scans first missed
    /path:\s*'([^']+)'/g,
    /to="([^"]+)"/g,
    /href="([^"]+)"/g,
    /to=\{`([^`]+)`\}/g,
    /to=\{'([^']+)'\}/g,
    /navigate\('([^']+)'/g,
    /navigate\(`([^`]+)`/g,
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

  it('the scan is not vacuous — it sees the routes, the links, and the registry key', () => {
    expect(detailRoutes().length,
      'App.tsx should declare ~90 parameterised routes').toBeGreaterThan(50);
    const targets = linkTargets();
    expect(targets.size).toBeGreaterThan(300);
    // The normalisation this test adds: without it every detail route reads as orphaned.
    expect(isLinked('/workflows/requests/:id', targets),
      'a template-literal link must match its parameterised route').toBe(true);
    // The key both scans first missed.
    expect(targets.has('/workflows/pdpl/ropa')).toBe(true);
  });

  it('no NEW detail route becomes unreachable', () => {
    const unexpected = unlinked().filter(r => !UNLINKED_DETAIL.includes(r));

    expect(unexpected,
      `${unexpected.join(', ')} is a detail route that no source navigates to — it can only be `
      + 'reached by typing a URL with an id in it. Link it from the screen that owns it (for a detail '
      + 'page, its list). Being absent from the SIDEBAR is fine for a detail view; being absent from '
      + 'its own list is not. See TSS-GRC-Platform-Bible/UX/ROUTE_RECONCILIATION.md.').toEqual([]);
  });

  it('the pinned list stays honest — a detail route that gained a link must leave it', () => {
    const found = unlinked();
    const stale = UNLINKED_DETAIL.filter(r => !found.includes(r));

    expect(stale,
      `${stale.join(', ')} is pinned as unlinked but now has an inbound link (or the route was `
      + 'renamed or removed). Remove it from UNLINKED_DETAIL and decrement §2 of '
      + 'ROUTE_RECONCILIATION.md so the matrix keeps measuring what it claims.').toEqual([]);
  });

  it('the BCM exercise runtime stays named — F2-161 hardened three screens with no entry point', () => {
    // Pinned individually, not by count: this is the finding, and a bulk edit that fixed two of the
    // three would otherwise look like progress on all of them.
    const found = unlinked();
    for (const r of ['/bcm/exercises/run/:runId/facilitate',
                     '/bcm/exercises/run/:runId/observe',
                     '/bcm/exercises/run/:runId/participate']) {
      expect(UNLINKED_DETAIL.includes(r) || !found.includes(r),
        `${r} must either still be pinned or have gained a link`).toBe(true);
    }
  });
});
