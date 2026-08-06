import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { join, posix, sep } from 'node:path';

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
   * Modules that hold submissions in the browser and never call the network. Membership is
   * *measured* below, not asserted — a store that grows a real fetch should leave this list, and a
   * store on this list that turns out to call the network would make every exemption below bogus.
   */
  const CLIENT_ONLY_STORES = ['lib/intake'];

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
  const BLOCKED_ON_MISSING_ENDPOINT: Record<string, string> = {
    '/portal/dsr': 'F2-138 — no anonymous DSR submit endpoint exists to point at',
    '/portal/track': 'F2-138 — tracks any intake kind by reference code; the public submit side does not exist',
  };

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

  it('lib/intake really does make zero network calls, so the premise here is measured', () => {
    const src = readFileSync(join(SRC, 'lib', 'intake.ts'), 'utf8');
    const network = src.match(/\bfetch\s*\(|\bapi\.(get|post|put|patch|delete)\b|XMLHttpRequest|axios/g);
    expect(network, 'lib/intake now calls the network — remove it from CLIENT_ONLY_STORES and '
      + 're-examine every exemption below, which only made sense while it could not reach a server')
      .toBeNull();
    expect(src, 'lib/intake no longer persists to localStorage — has it been rewired?')
      .toMatch(/localStorage/);
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
