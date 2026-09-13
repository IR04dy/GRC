import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { RiskResponse } from '../lib/api';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * JRN-11 — the risk lifecycle resolves its record from the REGISTER, not the corpus.
 *
 * `/risk-lifecycle` contained zero `api.`/`Api.` calls of its own. Its selector was seeded
 * `useState<number>(MOCK_RISKS[0]?.id ?? 1)` — from the demo corpus — so `riskId` was a demo id
 * (900000001 after the `demoIds` shift) for every user, signed in or not. `risk` therefore always
 * resolved to a fabricated record, and `plansForRisk(riskId)`, which filters the REAL
 * server-hydrated register the page did fetch one line above, could never match.
 *
 * So the page showed a fabricated risk beside a permanently empty plan list, and the real fetch
 * it performed is what made that emptiness look like a tenant with no data. F2-198's
 * routed-page-that-fabricates crossed with F2-205's live-wire-that-terminates-nowhere, and worse
 * than JRN-01: there the fallback fired only on an error, here it was the page's only path.
 *
 * ⚠⚠ The measurement also found the sharper half, which the finding text did not name: the
 * headline KPI rendered `MOCK_RISKS.length` under a tile labelled simply "Risks" that LINKS TO
 * `/risk`, the real register. F2-197's rule verbatim — a tile whose figure disagrees with the
 * list it opens is worse than a tile with no figure. `kpiProvenance` cannot see this one, because
 * the value arrives as a prop from a library two files away rather than as a literal inside the
 * `<Kpi>` tag: that gate's own recorded blind spot.
 *
 * ⚠ THE LOAD-BEARING LEGS ARE BEHAVIOURAL. A rewire that fetched the register and then still
 * fell back to the corpus on an error would look identical on screen and be the same defect, so
 * "reports a failed fetch instead of substituting the corpus" and "counts the REGISTER in
 * risksTotal" are the assertions that matter; the structural ones only stop the shape returning.
 *
 * ⚠ Every structural scan strips comments first. This codebase explains a deletion by naming
 * what was deleted, and both files under test quote `MOCK_RISKS[0].id` in their headers — so a
 * raw-text scan reads the epitaph as the corpse (F2-193, F2-196, F2-202, F2-205, F2-207).
 * ─────────────────────────────────────────────────────────────────────────────
 */

const SRC = join(__dirname, '..');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');
const STRIP = /(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
const strip = (s: string) => s.replace(STRIP, '');

const PAGE = 'pages/risk-lifecycle/RiskLifecyclePage.tsx';
const LIB = 'lib/riskLifecycle.ts';

// ─── behavioural harness ─────────────────────────────────────────────────────

const hoisted = vi.hoisted(() => ({
  signedIn: true,
  list: null as null | (() => Promise<{ content: RiskResponse[] }>),
}));

vi.mock('../lib/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/auth')>()),
  isAuthenticated: () => hoisted.signedIn,
}));

vi.mock('../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/api')>();
  return { ...actual, riskApi: { ...actual.riskApi, list: () => hoisted.list!() } };
});

/** Risks that exist ONLY on the server — no corpus row carries either id. */
const SERVER_RISKS: RiskResponse[] = [
  {
    id: 41, riskCode: 'R-0041', titleEn: 'Server-only cyber risk',
    categoryNameEn: 'Cyber', status: 'OPEN', riskLevel: 'HIGH',
    inherentLikelihood: 4, inherentImpact: 4, inherentScore: 16,
  },
  {
    id: 42, riskCode: 'R-0042', titleEn: 'Server-only operational risk',
    categoryNameEn: 'Operational', status: 'OPEN', riskLevel: 'MEDIUM',
    inherentLikelihood: 2, inherentImpact: 3, inherentScore: 6,
  },
];

/**
 * A fresh module graph per test. `_risks`, `_risksPromise` and the failure flag are
 * module-level, so a shared instance would let one test's hydration decide the next one's
 * answer — the kind of cross-talk that makes a green suite mean nothing.
 */
async function fresh() {
  vi.resetModules();
  const lib = await import('../lib/riskLifecycle');
  const corpus = (await import('../pages/risk/mockRisks')).MOCK_RISKS;
  return { lib, corpus };
}

describe('JRN-11 — what the lifecycle page is looking at', () => {
  beforeEach(() => {
    hoisted.signedIn = true;
    hoisted.list = async () => ({ content: SERVER_RISKS });
    window.localStorage.clear();
  });
  afterEach(() => vi.restoreAllMocks());

  it('hydrates the real register for a signed-in tenant, and says it is not demo', async () => {
    const { lib, corpus } = await fresh();
    await lib.hydrateRisks();
    expect(lib.loadRisks().map(r => r.id)).toEqual([41, 42]);
    expect(lib.risksAreDemo()).toBe(false);
    expect(lib.risksLoadFailed()).toBe(false);
    expect(lib.areRisksHydrated()).toBe(true);
    // Anti-vacuity: "equals the server rows" would be trivially true against an empty or
    // unshifted corpus, and the shift is what made the old ids unmatchable.
    expect(corpus.length).toBeGreaterThan(5);
    expect(corpus[0].id).toBeGreaterThan(900_000_000);
  });

  it('counts the REGISTER in risksTotal — the tile opens /risk and must agree with it', async () => {
    const { lib, corpus } = await fresh();
    await lib.hydrateRisks();
    expect(lib.aggregateRiskLifecycle().risksTotal).toBe(SERVER_RISKS.length);
    expect(lib.aggregateRiskLifecycle().risksTotal).not.toBe(corpus.length);
  });

  it('reports a failed fetch instead of substituting the corpus', async () => {
    // The defect class in one leg. A silent mock fallback on an error path is the load-bearing
    // decoy F2-158 measured and F2-169 removed; `RiskRegisterPage` still has one, which is why
    // this module's split was written rather than copied from it.
    const { lib, corpus } = await fresh();
    const { ApiError } = await import('../lib/api');
    hoisted.list = async () => { throw new ApiError(503, 'Service Unavailable'); };
    await lib.hydrateRisks();
    expect(lib.risksLoadFailed()).toBe(true);
    expect(lib.loadRisks()).toEqual([]);
    expect(lib.loadRisks().length).not.toBe(corpus.length);
    expect(lib.aggregateRiskLifecycle().risksTotal).toBe(0);
  });

  it('treats a 403 as "nothing to show here", not as a failure to report', async () => {
    const { lib } = await fresh();
    const { ApiError } = await import('../lib/api');
    hoisted.list = async () => { throw new ApiError(403, 'Forbidden'); };
    await lib.hydrateRisks();
    expect(lib.risksLoadFailed()).toBe(false);
    expect(lib.loadRisks()).toEqual([]);
  });

  it('serves the corpus, disclosed, when there is no session', async () => {
    hoisted.signedIn = false;
    const { lib, corpus } = await fresh();
    await lib.hydrateRisks();
    expect(lib.risksAreDemo()).toBe(true);
    expect(lib.loadRisks().map(r => r.id)).toEqual(corpus.map(r => r.id));
  });
});

describe('JRN-11 — the per-risk derivations follow the register', () => {
  beforeEach(() => {
    hoisted.signedIn = true;
    hoisted.list = async () => ({ content: SERVER_RISKS });
    window.localStorage.clear();
  });
  afterEach(() => vi.restoreAllMocks());

  it('derives control linkages from the register row, not from a corpus row', async () => {
    const { lib, corpus } = await fresh();
    await lib.hydrateRisks();
    // A server row resolves, and its own category drives the linkage set.
    const linked = lib.controlsForRisk(41);
    expect(linked.map(c => c.controlCode)).toContain('ECC-2-2-1');
    // A corpus id is not in this tenant's register, so it derives nothing. Before JRN-11 it
    // returned a full set of control linkages for a record the tenant does not have.
    expect(lib.controlsForRisk(corpus[0].id)).toEqual([]);
  });

  it('does not hang a fabricated KRI breach off a demo id for a signed-in tenant', async () => {
    const { lib, corpus } = await fresh();
    await lib.hydrateRisks();
    expect(lib.loadBreachEvents()).toEqual([]);
    expect(lib.breachesForRisk(corpus[0].id)).toEqual([]);
  });

  it('still seeds that breach on the demo path, where it belongs', async () => {
    hoisted.signedIn = false;
    const { lib, corpus } = await fresh();
    await lib.hydrateRisks();
    // Anti-vacuity for the leg above: the seed exists, so its absence there is a decision and
    // not a corpus that quietly emptied.
    expect(lib.breachesForRisk(corpus[0].id).length).toBe(1);
  });
});

describe('JRN-11 — the shape cannot come back', () => {
  it('the page does not import the demo corpus at all', () => {
    const src = strip(read(PAGE));
    expect(src).not.toMatch(/import[^;\n]*from\s+['"][^'"]*mock[^'"]*['"]/i);
    expect(src).not.toContain('MOCK_RISKS');
  });

  it('the selector is not seeded from any corpus', () => {
    const src = strip(read(PAGE));
    expect(src).not.toMatch(/useState<number>\(\s*[A-Z_]*MOCK/);
    expect(src).toMatch(/useState<number \| null>\(null\)/);
  });

  it('the blank div is gone, and the three states it stood for are each named', () => {
    const src = strip(read(PAGE));
    expect(src).not.toMatch(/<Shell[^>]*>\s*<div\s*\/>\s*<\/Shell>/);
    // Still loading, unreachable, and "this tenant has no risks" are different facts. A blank
    // div asserted the third while meaning any of them.
    expect(src).toContain('areRisksHydrated()');
    expect(src).toContain('risksLoadFailed()');
    expect(src).toMatch(/Loading the risk register/);
    expect(src).toMatch(/failed to load from the server/);
    expect(src).toMatch(/holds no risks/);
  });

  it('a demo register is disclosed on the page that renders its KPIs', () => {
    const src = strip(read(PAGE));
    expect(src).toContain('risksAreDemo()');
    expect(src).toContain('DemoDataNotice');
  });

  it('the library reads the corpus only where a corpus belongs', () => {
    const src = strip(read(LIB));
    const lines = src.split('\n').filter(l => l.includes('MOCK_RISKS')).map(l => l.trim());
    // Exactly three: the import, the no-session branch of `hydrateRisks`, and the demo breach
    // seed's risk id. Any other read is a resolution path that bypasses the register.
    expect(lines, 'a new MOCK_RISKS read appeared in lib/riskLifecycle.ts').toEqual([
      "import { MOCK_RISKS } from '../pages/risk/mockRisks';",
      '_risks = MOCK_RISKS;',
      'riskId: MOCK_RISKS[0]?.id ?? 1,',
    ]);
  });

  it('the dark export is gone and stays gone', () => {
    // F2-201: an unused client method should not ship. `pickRiskById` had zero consumers
    // outside its own file, so it went with the sweep rather than being rewired.
    expect(strip(read(LIB))).not.toContain('pickRiskById');
  });

  it('the scans are actually reading the files they claim to', () => {
    // A path typo would make every structural leg above vacuously green.
    expect(read(PAGE)).toContain('RiskLifecyclePage');
    expect(read(LIB)).toContain('hydrateRisks');
    expect(strip(read(LIB)).length).toBeGreaterThan(5_000);
  });
});
