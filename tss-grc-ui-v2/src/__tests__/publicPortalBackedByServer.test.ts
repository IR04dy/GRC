import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { join, posix, sep } from 'node:path';
import { stripComments } from './support/sourceScan';

/**
 * F2-137 — a public portal must talk to the server, not to the visitor's own browser.
 *
 * `/portal/speakup` was a second whistleblower intake. It rendered a convincing form, returned a
 * reference code, and wrote the report to `localStorage` through `lib/intake`'s overlay. No ethics
 * officer on any other machine could see it, it never appeared in `ethics.whistleblower_case`, it
 * started no SLA clock, and it died with the browser's site data — while a real, tested, tenant-
 * correct endpoint sat one import away at `/api/v1/ethics/whistleblower/public/submit`.
 *
 * <b>Why the existing ratchets could not see it.</b> W2-1b's `demoFallbackContract` matches
 * `/mock/i` in the import *path*, and `lib/intake.ts` is not named "mock" — it is named after the
 * domain concept it fakes. F2-07b's `routeReachability` asks whether a route is *reachable*, and
 * this one was perfectly reachable. Both guards were green. Neither asked the question that
 * mattered: does this page reach a server?
 *
 * <b>Scope, stated honestly.</b> This checks *direct* imports, not the transitive graph. A page
 * that imported a wrapper that imported `lib/intake` would slip past. That is a real limit,
 * accepted because the defect class is a page calling `useIntake()` itself, and because a
 * transitive walk over this tree costs more false positives than it buys.
 */
describe('F2-137 — public portal routes are backed by the server, not by localStorage', () => {
  const SRC = join(__dirname, '..');
  const APP = readFileSync(join(SRC, 'App.tsx'), 'utf8');

  /**
   * Modules that hold submissions in the browser and never call the network.
   *
   * ⚠ **Empty since F2-172, and that is this guard's own success condition firing.** The list
   * held `lib/intake`, and the comment below already said membership was *measured* so that a
   * store growing a real fetch would leave it. `lib/intake` now calls `POST /api/v1/intake`,
   * `GET /api/v1/intake` and `PUT /api/v1/intake/{id}/triage`, so it left. Because an empty list
   * would make the route scan below vacuous — the failure mode this file is otherwise careful
   * about — that scan was replaced by two properties that stay decidable with no members.
   */
  const CLIENT_ONLY_STORES: string[] = [];

  /** Route prefixes served without a login. */
  const PUBLIC_PREFIXES = ['/portal/', '/public/'];

  /**
   * Public routes still on a client-only store, each with the reason it cannot move yet.
   *
   * Both are blocked on the same missing thing, and it is not front-end work: **there is no
   * anonymous DSR submission endpoint at all.** `POST /api/v1/data-officer/dsr` requires
   * `PRIVACY:WRITE` and `POST /api/v1/privacy/dsrs` requires the same, so a data subject cannot
   * exercise a PDPL Article 4/22 right without a staff account — while `GET /api/v1/intake/track/**`
   * is `permitAll`, a public tracker for submissions nobody can publicly make. See F2-138. Pointing
   * these pages at a server means building that endpoint first, with tenancy, rate limiting and the
   * Article 10 clock, which is a decision rather than a rewire.
   */
  const BLOCKED_ON_MISSING_ENDPOINT: Record<string, string> = {};

  /**
   * F2-172 — what replaced those two exemptions, and why it is not the same claim.
   *
   * `/portal/track` is fixed outright: it calls `GET /api/v1/intake/track/{code}`, which is
   * `permitAll`, and renders the server's PII-free projection.
   *
   * `/portal/dsr` is **still blocked by F2-138** — no anonymous submit endpoint exists, so
   * `POST /api/v1/intake` refuses a data subject. What changed is that the page now *shows* that
   * refusal instead of writing to `localStorage` and handing out a reference code. So the property
   * worth enforcing is no longer "which store does it import" but the one that actually harmed
   * people: **a public page must not mint an acknowledgement the server did not issue.** Unlike an
   * exemption list, that cannot be satisfied by moving the fabrication into a new file.
   */
  const REFERENCE_CODE_MINTING = [
    /generateReferenceCode\s*\(/,
    /\bMath\s*\.\s*random\s*\(\)[\s\S]{0,120}toString\s*\(\s*36\s*\)/,
  ];

  function lazyImports(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const m of APP.matchAll(/const\s+(\w+)\s*=\s*lazy\(\(\)\s*=>\s*import\('([^']+)'/g)) {
      out[m[1]] = m[2];
    }
    for (const m of APP.matchAll(/import\s+\{([^}]*)\}\s+from\s+'(\.[^']+)'/g)) {
      for (const name of m[1].split(',').map(s => s.trim()).filter(Boolean)) {
        if (/^[A-Z]/.test(name) && !(name in out)) out[name] = m[2];
      }
    }
    return out;
  }

  function publicRoutes(): { path: string; component: string }[] {
    const out: { path: string; component: string }[] = [];
    for (const m of APP.matchAll(/<Route\s+path="([^"]+)"\s+element=\{<(\w+)/g)) {
      if (PUBLIC_PREFIXES.some(p => m[1].startsWith(p))) {
        out.push({ path: m[1], component: m[2] });
      }
    }
    return out;
  }

  /**
   * ⚠ Anchored on the `from '…'` clause, NOT on the whole import statement.
   *
   * The first version of this test matched the statement, which cannot cross a newline — so
   * `CaseTrackerPage`'s multi-line `import { useIntake, INTAKE_CATALOG, … } from '../../lib/intake'`
   * was invisible to it. That showed up as the exemption check reporting a live exemption as spent,
   * but the offender check shared the hole, and it was wide enough to drive the very defect this
   * test exists to catch straight through: a page importing `useIntake` across two lines would have
   * passed clean. A `from '…'` clause is always on one line, so this form sees both shapes. A regex
   * that matches too little does not under-report, it invents results.
   */
  function importsStore(src: string, store: string): boolean {
    const needle = store.replace(/\//g, '\\/');
    return new RegExp(`from\\s+['"][^'"]*${needle}['"]`).test(src);
  }

  function resolve(importPath: string): string | null {
    const base = join(SRC, importPath.replace(/^\.\//, '').split(posix.sep).join(sep));
    for (const ext of ['.tsx', '.ts']) {
      if (existsSync(base + ext)) return base + ext;
    }
    return null;
  }

  function pageSource(component: string): string | null {
    const importPath = lazyImports()[component];
    if (!importPath) return null;
    const file = resolve(importPath);
    return file ? readFileSync(file, 'utf8') : null;
  }

  /**
   * The premise, INVERTED by F2-172. It used to assert that `lib/intake` makes zero network calls
   * — true when written, and the sentence that made every exemption here defensible. The store is
   * now server-backed, so the same fact is asserted the other way round: it must reach the server,
   * and it must no longer hold submissions in the browser. Keeping the assertion rather than
   * deleting it with the list it justified is the point: reintroduce a local overlay for intake
   * and this fails.
   *
   * ⚠ `stripComments`, and not as a nicety. The first version of this scanned the raw file and
   * failed on the JAVADOC — the block explaining that the localStorage overlay was *removed*
   * contains the word twice. Fourth time on this programme that a matcher has read prose as code
   * (F2-137's newline, F2-165's regex literal, F2-170's javadoc-as-class), and the remedy is the
   * repo helper that already exists rather than a more careful regex.
   */
  it('lib/intake is server-backed, and holds no submission store of its own', () => {
    const src = stripComments(readFileSync(join(SRC, 'lib', 'intake.ts'), 'utf8'));
    expect(src, 'lib/intake no longer reaches the server — the F2-172 rewire was reverted, and '
      + 'every submission is going back into the submitter\'s own browser')
      .toMatch(/intakeApi\.(submit|list|triage|track|get)/);
    expect(src.match(/localStorage/g),
      'lib/intake persists to localStorage again. The intake substrate is the platform front '
      + 'door: a local overlay there means submissions no triager can see (F2-171/F2-172).')
      .toBeNull();
    expect(CLIENT_ONLY_STORES,
      'lib/intake is back on CLIENT_ONLY_STORES — if that is deliberate, the two assertions above '
      + 'must be re-inverted with it').not.toContain('lib/intake');
  });

  /**
   * The replacement for the client-store route scan, decidable with zero CLIENT_ONLY_STORES
   * because it inspects the pages rather than their imports.
   *
   * A reference code is what a submitter keeps, quotes and trusts; the only body entitled to issue
   * one is the one that also stored the submission. `/portal/dsr` minted its own and showed a
   * 30-day PDPL clock over a record that existed in nobody's database.
   */
  it('no public page mints its own reference code', () => {
    const offenders: string[] = [];
    for (const { path, component } of publicRoutes()) {
      const src = pageSource(component);
      if (src === null) continue;
      for (const pattern of REFERENCE_CODE_MINTING) {
        if (pattern.test(src)) offenders.push(`${path} -> ${component} matches ${pattern}`);
      }
    }
    expect(offenders, 'a public page is generating its own reference code. Only the server may '
      + 'issue one, because only the server has the row it identifies — otherwise the submitter is '
      + 'handed a tracking code for a submission nobody received.\n  ' + offenders.join('\n  '))
      .toEqual([]);
  });

  /** Anti-vacuity for the rule above: the matcher must reject the shape that was there. */
  it('the reference-code matcher recognises the shape it was written against', () => {
    const fabricating = 'const code = generateReferenceCode(kind);';
    const alsoFabricating = 'const seg = () => Math.random().toString(36).toUpperCase();';
    const honest = 'const rec = await intakeApi.submitPublic(body); setCode(rec.referenceCode);';
    expect(REFERENCE_CODE_MINTING.some(r => r.test(fabricating))).toBe(true);
    expect(REFERENCE_CODE_MINTING.some(r => r.test(alsoFabricating))).toBe(true);
    expect(REFERENCE_CODE_MINTING.some(r => r.test(honest))).toBe(false);
  });

  /**
   * A public form whose action button cannot submit it — found by *clicking* the DSR portal's
   * Submit during F2-172's browser pass, and dead since the page was written.
   *
   * The shared `Button` defaults to `type="button"` deliberately (its own comment: a Button inside
   * a form must not auto-submit, so pages needing it "pass type=submit explicitly").
   * `DsrPortalPage` did not, and its `onClick` was an empty function annotated "form onSubmit
   * handles it". A `type="button"` click never reaches `onSubmit`, so pressing Submit on the
   * public PDPL rights form did nothing at all; only Enter in a field ever worked.
   *
   * ⚙ Why nothing caught it: every guard on this page asked what the submit path *does*, and none
   * asked whether it can be *entered*. Route reachability was measured (F2-07b); a screen's own
   * primary action was not. Measured across the tree: 19 files render a `<form onSubmit>`, 16 pass
   * `type="submit"`, and of the three that do not the other two call their handler from `onClick`.
   */
  it('every public form can actually be submitted by its action button', () => {
    /** An action button with a do-nothing handler: it can only work via form submission. */
    const EMPTY_HANDLER = /onClick=\{\(\)\s*=>\s*\{\s*(?:\/\*[\s\S]*?\*\/)?\s*\}\}/;
    const offenders: string[] = [];
    let buttonsScanned = 0;
    for (const { path, component } of publicRoutes()) {
      const src = pageSource(component);
      if (src === null || !/<form\s+onSubmit/.test(src)) continue;
      // ⚠⚠ Judge each <Button …> element on its OWN attributes, never on the file. The first
      // version of this asked whether the FILE contained `type="submit"` and passed with the dead
      // button re-inserted — because the javadoc explaining why this button needs `type="submit"`
      // says so three times, and `stripComments` does not remove a JSX `{/* … */}` block either.
      // Fifth instance of a matcher reading prose as code on this programme, and the third remedy
      // tried for it: not a better regex and not a stripper, but a matcher scoped to the element
      // it is judging, where surrounding prose cannot reach.
      // ⚠ `[^>]*?` cannot capture JSX attributes: the `>` in `=>` truncates them, so an
      // inline arrow handler was invisible and every button read as alive. The alternation admits
      // `=>` as a unit and the lookbehind refuses a cut inside one.
      for (const m of src.matchAll(/<Button((?:[^>]|=>)*?)>(?<!=>)/g)) {
        const attrs = m[1];
        buttonsScanned++;
        if (EMPTY_HANDLER.test(attrs) && !/type="submit"/.test(attrs)) {
          offenders.push(`${path} -> ${component}: <Button${attrs.replace(/\s+/g, ' ').trimEnd()}> `
            + 'has a do-nothing onClick and no type="submit", so clicking it does nothing');
        }
      }
    }
    expect(buttonsScanned, 'no <Button> inside a public <form onSubmit> was found — the scan broke, '
      + 'so this assertion is vacuous').toBeGreaterThanOrEqual(3);
    expect(offenders, 'a public form has an action button that cannot submit it. Either pass '
      + 'type="submit" or call the handler from onClick — the shared Button defaults to '
      + 'type="button", so an empty onClick beside a form onSubmit is a dead button.\n  '
      + offenders.join('\n  ')).toEqual([]);
  });

  /** Anti-vacuity: the element-local matcher must recognise the exact shape that was live, must
   *  clear both legitimate alternatives, and must NOT be rescued by prose on a neighbouring line —
   *  that last case is the regression the file-wide version shipped with. */
  it('the dead-button matcher recognises the shape DsrPortalPage had', () => {
    const EMPTY_HANDLER = /onClick=\{\(\)\s*=>\s*\{\s*(?:\/\*[\s\S]*?\*\/)?\s*\}\}/;
    const judge = (el: string): 'dead' | 'alive' | 'unparsed' => {
      const m = /<Button((?:[^>]|=>)*?)>(?<!=>)/.exec(el);
      if (!m) return 'unparsed';
      return EMPTY_HANDLER.test(m[1]) && !/type="submit"/.test(m[1]) ? 'dead' : 'alive';
    };
    expect(judge('<Button variant="primary" Icon={Send} onClick={() => {/* form onSubmit handles it */}}>'),
      'the exact shape DsrPortalPage shipped must read dead').toBe('dead');
    expect(judge('<Button onClick={() => { }}>')).toBe('dead');
    expect(judge('<Button variant="primary" type="submit" Icon={Send} disabled={busy}>')).toBe('alive');
    expect(judge('<Button variant="primary" onClick={() => lookup()}>'),
      'a button that calls its handler directly is fine — CaseTrackerPage and VendorPortalPage do')
      .toBe('alive');
    // The regression the file-wide check shipped with: prose nearby must not rescue the element.
    const proseThenDeadButton = '{/* pass type="submit" here */}'
      + '<Button variant="primary" onClick={() => {/* form onSubmit handles it */}}>';
    expect(judge(proseThenDeadButton.slice(proseThenDeadButton.indexOf('<Button'))),
      'the element must be judged alone, not with its documentation').toBe('dead');
  });

  it('the import matcher sees a multi-line import, which the first version of it did not', () => {
    // Mutation-proofing the helper itself: this is the exact shape that slipped through, so if the
    // matcher is ever "simplified" back to a statement-anchored regex, this fails rather than the
    // whole guard going quietly blind.
    const multiline = "import {\n  useIntake, INTAKE_CATALOG,\n} from '../../lib/intake';\n";
    const single = "import { useIntake } from '../../lib/intake';\n";
    expect(importsStore(multiline, 'lib/intake'), 'multi-line import missed').toBe(true);
    expect(importsStore(single, 'lib/intake'), 'single-line import missed').toBe(true);
    expect(importsStore("import { api } from '../../lib/api';\n", 'lib/intake')).toBe(false);
  });

  it('finds the public routes at all, so a silent parse failure cannot pass as clean', () => {
    // F2-69's lesson: a scan that finds nothing must never read as a clean one.
    expect(publicRoutes().length,
      'no /portal or /public routes parsed out of App.tsx — the regex broke')
      .toBeGreaterThanOrEqual(5);
  });

  it('no public route renders a page that stores submissions in the browser', () => {
    const offenders: string[] = [];
    for (const { path, component } of publicRoutes()) {
      if (path in BLOCKED_ON_MISSING_ENDPOINT) continue;
      const src = pageSource(component);
      if (src === null) continue;   // inline element (e.g. a redirect) — nothing to read
      for (const store of CLIENT_ONLY_STORES) {
        if (importsStore(src, store)) offenders.push(`${path} -> ${component} imports ${store}`);
      }
    }
    expect(offenders, 'these public routes accept submissions that never leave the visitor\'s '
      + 'browser. The reporter is shown a reference code, so the failure is silent on both sides: '
      + 'nobody is notified, no row is written, no SLA starts, and clearing site data destroys it. '
      + 'Point the page at the real client, or — if no server endpoint exists yet — add it to '
      + 'BLOCKED_ON_MISSING_ENDPOINT naming the finding that tracks building one.\n  '
      + offenders.join('\n  ')).toEqual([]);
  });

  it('the blocked-on-endpoint exemptions still exist and still import the store', () => {
    const routes = publicRoutes();
    const stale: string[] = [];

    for (const [path, reason] of Object.entries(BLOCKED_ON_MISSING_ENDPOINT)) {
      const route = routes.find(r => r.path === path);
      if (!route) {
        stale.push(`${path} is no longer a public route (${reason})`);
        continue;
      }
      const src = pageSource(route.component);
      if (src === null) {
        stale.push(`${path} -> ${route.component} cannot be resolved`);
        continue;
      }
      if (!CLIENT_ONLY_STORES.some(s => importsStore(src, s))) {
        stale.push(`${path} no longer imports a client-only store — exemption is spent`);
      }
    }

    expect(stale, 'an exemption list that outlives its entries stops describing the code and starts '
      + 'hiding it. If one of these was fixed, delete its entry — that is the win being recorded.\n  '
      + stale.join('\n  ')).toEqual([]);
  });
});
