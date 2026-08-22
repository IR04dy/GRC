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

/**
 * Every way this codebase expresses “somewhere the user can go”.
 *
 * ⚠⚠ F2-274 — this list said exactly that and omitted the TEMPLATE LITERAL, which is how
 * this codebase writes every parameterised link. A STATIC path written with backticks was therefore
 * invisible to every leg below — and `DataSharingPage` wrote `to={`/ropa`}`, so the
 * retired-redirect leg was GREEN over a live inbound link to a page F2-198 retired. Measured: **6
 * static** template destinations across 148 template links, exactly **1** of them pointing at a
 * retired redirect.
 *
 * 🔑 A pattern list whose comment claims completeness is the hardest kind to doubt (F2-184's
 * shape, in a detector's own documentation). Its sibling gate `tabLinkTargets.test.ts` (F2-207)
 * DOES resolve template literals, so two link scanners in this directory disagreed about what a
 * link is — F2-190's two-mechanisms shape, and the newer one was right.
 *
 * Static only, deliberately: a dynamic target such as `/tprm/vendors/${id}` is not a static route
 * and `declaredStaticRoutes()` excludes parameterised ones, so admitting it would add strings that
 * match nothing. A route reachable ONLY through a dynamic link still reads as an orphan, which
 * over-reports and fails loudly — the safe direction, and the existing behaviour.
 */
const LINK_KEYS = [
  /to:\s*'([^']+)'/g,
  /href:\s*'([^']+)'/g,
  /route:\s*'([^']+)'/g,        // lib/workflowCatalog.ts — the one the first scan missed
  /to="([^"]+)"/g,
  /href="([^"]+)"/g,
  /navigate\('([^']+)'/g,
  /to=\{'([^']+)'\}/g,
  // F2-274 — the template-literal forms, static only (no `$`).
  /(?:to|href)=\{`([^`$]+)`\}/g,
  /navigate\(`([^`$]+)`/g,
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

/**
 * ⚠⚠ F2-274 — comments are STRIPPED, and they were not before.
 *
 * The gate read raw source, so a route merely MENTIONED in a comment counted as a link to it.
 * For the retired-redirect leg that is a false positive; for the ORPHAN leg it is a false negative
 * in the dangerous direction — a genuinely unlinked route reads as linked, which is precisely
 * what “no route is shipped invisible” exists to prevent. This codebase documents its own
 * retirements by quoting the link they removed, so the hazard is live rather than theoretical: the
 * comment F2-274 wrote to explain repointing `/ropa` kept that leg red on a fixed tree. 🔑 **The
 * epitaph reads as the corpse** (F2-193/F2-202/F2-205), for the Nth time in this tree.
 *
 * ONE left-to-right alternation, and newlines preserved so line numbers survive: written as two
 * sequential passes it deletes from a line comment containing a block-open delimiter to the next block-close, costing
 * F2-198 45% of `App.tsx` and reported a SMALLER population — and `(?<!:)` so a `https://`
 * inside a string cannot open a comment.
 */
const STRIP_COMMENTS = /(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g;

export function stripComments(src: string): string {
  return src.replace(STRIP_COMMENTS, m => m.replace(/[^\n]/g, ' '));
}

const ALL = walk(SRC).map(f => ({
  path: rel(f),
  body: stripComments(readFileSync(f, 'utf8')),
}));

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

describe('F2-274 — the link detector sees a template literal, and not a comment', () => {
  /**
   * The two holes this closes were found one after the other, and only the second was mine.
   *
   * LINK_KEYS listed seven shapes under a comment calling them every way this codebase
   * expresses a destination, and omitted the template literal — so `DataSharingPage` wrote
   * to={`/ropa`} and the retired-redirect leg above was GREEN over a live inbound link to a
   * page F2-198 retired. Measured: 6 static template destinations across 148 template links,
   * exactly 1 pointing at a retired redirect.
   *
   * Then the comment written to explain the repointing kept that leg RED, because the gate read
   * raw source and a quoted link counts as a link. Measured after stripping: 398 raw destinations
   * against 396, i.e. 2 comment-only, of which 1 is a declared route — so the stripper is a
   * LATENT-hazard closure rather than a defect discovery, and it is worth having only because
   * this codebase documents a retirement by quoting the link it removed.
   */
  it('finds a STATIC template-literal destination', () => {
    const body = 'const a = <Link to={' + '`' + '/ropa' + '`' + '}>x</Link>;';
    const found = new Set<string>();
    for (const re of LINK_KEYS) for (const m of body.matchAll(re)) found.add(m[1]);
    expect([...found],
      'the template-literal pattern has stopped matching; a static backtick link is invisible again and a retired page can be relinked without this suite noticing')
      .toContain('/ropa');
  });

  it('does NOT invent a destination from a parameterised template literal', () => {
    // 142 of the 148 template links are dynamic. Admitting them would add strings containing
    // ${...} that match no declared route — noise that reads like a destination.
    const body = 'to={' + '`' + '/tprm/vendors/${id}' + '`' + '}';
    const found = new Set<string>();
    for (const re of LINK_KEYS) for (const m of body.matchAll(re)) found.add(m[1]);
    expect([...found], 'a dynamic template link must not be reported as a static destination')
      .toEqual([]);
  });

  it('strips comments without moving a single line', () => {
    const src = ['const a = 1;',
                 '// to="/ropa" was here',
                 'const b = 2;'].join('\n');
    const out = stripComments(src);
    expect(out.split('\n')).toHaveLength(3);
    expect(out).not.toContain('/ropa');
    expect(out.split('\n')[2]).toBe('const b = 2;');
  });

  it('is ONE alternation, so a line comment cannot open a block', () => {
    // The negative control is the TWO-PASS form, not a reordered alternation: within one
    // alternation the branch order is a no-op, because // and /* can never match at the same
    // position (F2-211 measured this). Two passes is what cost F2-198 45% of App.tsx.
    const src = ['// prose mentioning a block-open delimiter: ' + '/' + '*',
                 'const keep = ' + JSON.stringify('/keep-me') + ';',
                 'const x = 1; ' + '/' + '* real block ' + '*' + '/'].join('\n');
    const oneAlternation = stripComments(src);
    expect(oneAlternation, 'the correct form must keep code that follows a line comment containing a block-open delimiter').toContain('/keep-me');

    const twoPass = src
      .replace(/\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '))
      .replace(/(?<!:)\/\/[^\n]*/g, m => m.replace(/[^\n]/g, ' '));
    expect(twoPass, 'the two-pass form must LOSE it — that is the bug being controlled for, and if this ever stops being true the control has gone vacuous')
      .not.toContain('/keep-me');
  });

  it('the production path uses the tested helper, once', () => {
    // ⚠ M6 first SURVIVED because the legs above tested the constant while the
    // mutation replaced ALL's expression: the production path can stop using the thing
    // under test and every assertion stays green. 🔑 A leg that exercises a primitive
    // says nothing about whether the code under review calls it (F2-205), so assert the
    // call site too.
    const self = readFileSync(new URL(import.meta.url), 'utf8');
    expect(self, 'ALL no longer builds its bodies through stripComments()')
      .toMatch(/body:\s*stripComments\(readFileSync\(/);
    // The HELPER's body must be the single-pass form. Scoped to the helper rather than the file: a
    // file-wide count of stripping passes reads this suite's own two-pass negative control as a
    // production pass and fails on correct code (F2-192 — scope a scan to the shape of its claim,
    // which my first draft of this very leg did not).
    const helper = self.match(/function stripComments\([^)]*\)[^{]*\{([\s\S]*?)\n\}/);
    expect(helper, 'stripComments() moved or was renamed — this leg needs repointing').toBeTruthy();
    expect(helper![1].replace(/\s+/g, ' ').trim(),
      'stripComments() is no longer a single pass over one alternation — two sequential passes '
      + 'delete from a line comment containing a block-open delimiter to the next block-close, '
      + 'which cost F2-198 45% of App.tsx and reported a SMALLER population')
      .toBe(String.raw`return src.replace(STRIP_COMMENTS, m => m.replace(/[^\n]/g, ' '));`);
  });

  it('the data-sharing ROPA chip points at the page that can filter by ROPA code', () => {
    // Asserted by SHAPE, not by an identifier, so a rename cannot break it (F2-197) and a
    // silent revert to the retired path cannot pass. `/data-lifecycle` reads ?tab and
    // ?ropaCode; `/privacy?tab=ropa` (what /ropa redirects to) has no ROPA-code filter, and
    // <Navigate> carries neither search string nor state (F2-207).
    const dsp = ALL.find(f => f.path.endsWith('data-sharing/DataSharingPage.tsx'));
    expect(dsp, 'DataSharingPage moved — this leg needs repointing').toBeDefined();
    expect(dsp!.body).toMatch(/\/data-lifecycle\?tab=cbt&ropaCode=\$\{encodeURIComponent/);
    expect(dsp!.body, 'the ROPA chip is pointing at the retired page again')
      .not.toMatch(/to=\{`\/ropa`\}/);
  });
});
