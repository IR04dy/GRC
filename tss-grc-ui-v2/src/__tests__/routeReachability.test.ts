import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, sep, posix } from 'node:path';

/**
 * F2-07b — every static route must be reachable, or be listed here with a reason.
 *
 * <p>A route the app declares but never links to is a screen that was built, wired to real APIs,
 * and then shipped invisible. That is what this test found: **eight SOC-Ops pages** —
 * appsec, dlp, forensics, iam-jml, zt-maturity, brand-monitoring, cyber-insurance and
 * ma-due-diligence — each with its own API calls, tables and detail links, reachable from nowhere.
 * Their sibling {@code /socops/awareness} <em>was</em> in the sidebar, which is what showed the
 * omission to be an oversight rather than a decision. All eight are now in the SOC-Ops nav group.
 *
 * <h3>Why the scan looks for so many key shapes</h3>
 * The first version of this scan checked {@code to:}, {@code href:}, {@code to=} and
 * {@code navigate(} and reported <b>45</b> unreachable routes. It was wrong: 22 of those are the
 * per-workflow screens, which {@code lib/workflowCatalog.ts} reaches through a {@code route:} key
 * the scan did not know about. Adding the missing key shapes took the figure to <b>17</b>.
 *
 * <p>The lesson is worth stating because it is the mirror image of the usual one: a scan that
 * matches too little does not merely under-report, it <b>invents defects</b>. Four backend regex
 * scans under-counted this month and hid real problems; this one over-counted and would have sent
 * me rewiring navigation that already worked.
 */

const SRC = join(__dirname, '..');

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

const rel = (f: string) => f.slice(SRC.length + 1).split(sep).join(posix.sep);

/** Every way this codebase expresses "somewhere the user can go". */
const LINK_KEYS = [
  /to:\s*'([^']+)'/g,
  /href:\s*'([^']+)'/g,
  /route:\s*'([^']+)'/g,        // lib/workflowCatalog.ts — the one the first scan missed
  /to="([^"]+)"/g,
  /href="([^"]+)"/g,
  /navigate\('([^']+)'/g,
  /to=\{'([^']+)'\}/g,
];

/**
 * Routes that are not a destination in the authenticated sidebar nav. Each entry is a reason, not
 * a suppression: if a new orphan appears, or an entry here stops being a declared route, the tests
 * below say which.
 *
 * ⚠ This comment used to claim that the test also "says which" entry "ever becomes reachable".
 * F2-199's mutation harness disproved it — reverting a repointed `/ropa` link SURVIVED — and
 * measuring then showed the claim could not simply be turned into an assertion either: **2 of the
 * 16 entries are legitimately linked** (`/login` from `Topbar`'s sign-out, `/portal/trust-center`
 * from `PublicShell`'s public footer), because "off-nav" here means "not a sidebar destination",
 * not "nothing links it". So the enforceable half is narrower and lives in RETIRED_MUST_STAY_
 * UNLINKED below.
 * 🔑 A comment describing a check that does not exist is worse than no comment, because it answers
 * the question a reader was about to ask — the F2-184 shape, in a test's own documentation.
 */
const INTENTIONALLY_OFF_NAV: Record<string, string> = {
  '/':                              'router entry — redirects to /dashboard or /login',
  '/login':                         'reached by redirect when unauthenticated, never by nav',

  // Public, UNAUTHENTICATED surfaces. These must be reachable without a login — a whistleblower
  // or a data subject cannot be asked to sign in — so they are linked externally, not from a
  // sidebar that only exists behind auth.
  '/ethics/whistleblower/submit':   'public whistleblower intake — external link, no auth',
  '/public/complaint':              'public consumer complaint intake — external link, no auth',
  '/public/track-complaint':        'public complaint tracker — external link, no auth',
  '/privacy/public/subprocessors':  'public sub-processor register (PDPL transparency) — external link',
  '/portal/trust-center':           'public trust centre — external link',

  // Deliberate aliases kept so existing bookmarks and emailed links do not 404.
  '/pdpl':                          'legacy alias for the privacy workspace',
  '/bcm-dependency/legacy':         'legacy alias retained for old bookmarks',

  // F2-142 — retired screens, kept as redirects to the REAL inbox. They are deliberately NOT linked:
  // linking them back would restore the nav entries whose whole problem was advertising a delivery
  // subsystem that had no server. A redirect with no inbound link is the intended end state.
  //
  // ⚠ This comment used to add that `/notifications/channels` was "absent from this list only
  // because nothing ever linked it either". That was wrong, and this test proved it: the crisis
  // dispatch wizard's `Channels` KPI carried `to="/notifications/channels"`, so the route had
  // exactly one inbound link. F2-147 removed it — the drill-through landed on the notification
  // inbox, where a cascade's channel count does not live — and this assertion fired immediately.
  // A negative claim in a comment is not a measurement; the guard beside it was the measurement.
  '/notifications/outbox':          'F2-142 — retired fake outbox; redirect only, must stay unlinked',
  '/notifications/dispatch':        'F2-142 — retired fake dispatch console; redirect only, unlinked',
  '/notifications/channels':        'F2-142 — retired fake channel console; redirect only. Its one '
                                  + 'inbound link (the crisis wizard KPI) was removed by F2-147',

  // Opened by direct/QR link during an incident, when the operator is not browsing a sidebar.
  '/bcm/crisis/mobile':             'mobile crisis view — opened by direct link during an incident',
  '/crisis-management':             'crisis console entry — opened by direct link / incident comms',

  // F2-199 — same shape as the F2-142 entries above: a retired screen kept as a redirect so a
  // bookmark still lands somewhere true, deliberately NOT linked. Linking it back would restore
  // a nav entry offering to generate an NCA ECC-1 submission document, which was the defect.
  //
  // ⚠ `/ropa` was absent from this list until F2-199, and asking why found a gap in F2-198: that
  // route passed this test not by exemption but because FOUR in-app links still pointed at it —
  // two KPI tiles on the data-flow graph, one on the privacy lifecycle page, and `RopaWizard`'s
  // post-publish `navigate('/ropa')`. F2-198 repointed the sidebar and left those, so they went on
  // bouncing through the redirect. F2-199 repointed all four at `/privacy?tab=ropa`, which is what
  // makes `/ropa` an orphan and earns it the entry below.
  // 🔑 A retirement is not finished when the sidebar is repointed. A redirect keeps every stale
  // link working, and that is exactly what stops anyone noticing they are stale — so this test
  // could not see them either.
  '/nca-ecc-export':                'F2-199 — retired fabricated NCA ECC-1 submission document; '
                                  + 'redirect to /nca-ecc/filings only, must stay unlinked',
  '/ropa':                          'F2-198 — retired fabricated PDPL Art. 31 register; redirect '
                                  + 'to /privacy?tab=ropa only. F2-199 repointed the last four '
                                  + 'in-app links, so it is now unlinked as intended',
};

const ALL = walk(SRC).map(f => ({ path: rel(f), body: readFileSync(f, 'utf8') }));

function declaredStaticRoutes(): string[] {
  const app = ALL.find(f => f.path === 'App.tsx');
  expect(app, 'App.tsx moved — this test needs repointing').toBeDefined();
  const all = [...app!.body.matchAll(/path="([^"]+)"/g)].map(m => m[1]);
  // Parameterised routes (/x/:id) and splats are detail views, legitimately off-nav.
  return [...new Set(all.filter(r => !r.includes(':') && !r.includes('*')))];
}

function linkTargets(): Set<string> {
  const found = new Set<string>();
  for (const { path, body } of ALL) {
    if (path === 'App.tsx' || /\.test\.tsx?$/.test(path)) continue;
    for (const re of LINK_KEYS) {
      for (const m of body.matchAll(re)) found.add(m[1]);
    }
  }
  return found;
}

/**
 * Retired screens kept only as redirects, so a bookmark lands somewhere true (F2-137). These must
 * have ZERO inbound links: every one was retired because it advertised something the platform
 * could not do, and a link is the advertisement. Unlike the rest of INTENTIONALLY_OFF_NAV, the
 * claim here is absolute and therefore assertable.
 *
 * ⚠ The gap this closes is subtle and it is why F2-198's retirement was incomplete: a redirect
 * keeps every stale link WORKING, so nothing breaks and nobody notices they are stale. `/ropa`
 * still had four inbound links for a whole slice — two data-flow KPI tiles, a privacy-lifecycle
 * tile, and `RopaWizard`'s post-publish `navigate()` — and the orphan test could not see them
 * because being linked is what it checks FOR.
 */
const RETIRED_MUST_STAY_UNLINKED = [
  '/notifications/outbox', '/notifications/dispatch', '/notifications/channels',  // F2-142
  '/ropa',                                                                        // F2-198
  '/nca-ecc-export',                                                              // F2-199
];

describe('F2-07b — no route is shipped invisible', () => {
  it('a retired redirect has no inbound link anywhere', () => {
    const reachable = linkTargets();
    const relinked = RETIRED_MUST_STAY_UNLINKED.filter(r => reachable.has(r));
    expect(relinked,
      'A retired screen has been linked again. These routes exist only so an old bookmark does '
      + 'not 404; each was retired because it advertised a capability the platform does not have, '
      + 'and a link is that advertisement. Point the link at the real register instead:\n  '
      + relinked.join('\n  '))
      .toEqual([]);
  });

  it('every retired redirect is still a declared route, so bookmarks still land', () => {
    // The reverse direction: deleting the Route instead of the link would turn a redirect into a
    // 404, which is the thing F2-137 exists to prevent.
    const declared = new Set(declaredStaticRoutes());
    for (const r of RETIRED_MUST_STAY_UNLINKED) {
      expect(declared.has(r), `${r} is no longer declared — a bookmarked link now 404s`).toBe(true);
    }
  });

  it('every static route is linked from somewhere, or listed with a reason', () => {
    const reachable = linkTargets();
    const orphans = declaredStaticRoutes()
      .filter(r => !reachable.has(r))
      .filter(r => !(r in INTENTIONALLY_OFF_NAV));

    expect(orphans,
      'These routes are declared but reachable from nowhere — a screen built, wired and shipped '
      + 'invisible. Either link it from the nav, or add it to INTENTIONALLY_OFF_NAV with the '
      + 'reason it is deliberately off-nav:\n  ' + orphans.join('\n  '))
      .toEqual([]);
  });

  it('the eight SOC-Ops pages this test was written for are now reachable', () => {
    const reachable = linkTargets();
    // Regression pins: these were the finding. Named individually so a bulk nav refactor that
    // drops one of them fails loudly rather than quietly restoring the original defect.
    for (const r of ['/socops/appsec', '/socops/dlp', '/socops/forensics', '/socops/iam-jml',
                     '/socops/zt-maturity', '/socops/brand-monitoring',
                     '/socops/cyber-insurance', '/socops/ma-due-diligence']) {
      expect(reachable.has(r), `${r} is unreachable again`).toBe(true);
    }
  });

  it('the off-nav allow-list stays honest — no entry for a route that no longer exists', () => {
    // An allow-list that outlives its routes quietly grants exemptions to nothing, and hides the
    // fact that it is stale. Every key must still name a declared route.
    const declared = new Set(declaredStaticRoutes());
    const stale = Object.keys(INTENTIONALLY_OFF_NAV).filter(r => !declared.has(r));
    expect(stale, 'INTENTIONALLY_OFF_NAV names routes that no longer exist:\n  ' + stale.join('\n  '))
      .toEqual([]);
  });

  it('the scan is not vacuous — it must find the routes and the links', () => {
    // Guard against the failure mode that makes the assertions above meaningless: a scan that
    // matches nothing is green for the wrong reason.
    expect(declaredStaticRoutes().length).toBeGreaterThan(200);
    const reachable = linkTargets();
    expect(reachable.size).toBeGreaterThan(300);
    expect(reachable.has('/dashboard')).toBe(true);
    // ...and it must see the `route:` key whose absence made the first scan over-report by 28.
    expect(reachable.has('/workflows/pdpl/ropa')).toBe(true);
  });
});
