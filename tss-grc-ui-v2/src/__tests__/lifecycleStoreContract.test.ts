import { describe, expect, it } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import {
  LIFECYCLE_STORES, BROWSER_LOCAL_LIFECYCLE_STORES, lifecycleStore,
} from '../lib/lifecycleStores';
import {
  DSR_ALLOWED, DSR_TERMINAL, isOpen, mayTransition, aggregateDsr, isPastDeadline,
  daysToDeadline, ropaIdList, type DsrCase,
} from '../lib/privacyLcApi';

const SRC = join(__dirname, '..');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');

/**
 * Strip line and block comments in ONE left-to-right alternation.
 *
 * ⚠ The order inside the alternation is load-bearing. Written the obvious way — blocks first,
 * then lines — a `//` prose comment whose text contains a literal `/*` opens a block that runs
 * to the next closing delimiter and swallows real declarations. F2-198 lost 40,247 of App.tsx's
 * 89,522 characters that way and reported a SMALLER population, which reads exactly like "those
 * files are fine". At a `//` an inner `/*` can never open a block; at a `/*` a URL cannot
 * truncate it. The `(?<!:)` keeps `https://` intact.
 */
const stripComments = (s: string) => s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');

/**
 * Blank out double-quoted spans so a QUOTATION is never read as a CLAIM.
 *
 * This repo's convention is that a correction quotes the sentence it replaces, which is what
 * makes the corrections auditable — and it means a detector matching raw text fires on the very
 * fix it was written to confirm. F2-193 met this in a backend gate and solved it this way.
 */
// ⚠ Newlines are ALLOWED inside the span: a quotation in a wrapped block comment crosses lines,
// and a `[^"\\\n]` character class silently fails to match it — which is how the first draft of
// this helper still fired on the very correction it was written to permit. Bounded so an
// unbalanced quote cannot run to the end of the file.
const unquoted = (s: string) => s.replace(/"(?:[^"\\]|\\.){0,3000}"/g, '""');

// ─────────────────────────────────────────────────────────────────────────────
// F2-202 — the DSR rewire, and the declared registry that replaced a decoy.
//
// What this file is for: `lib/platformPersistence.ts` exported three `mirror*ToBackend`
// helpers, called from five lifecycle libraries, that returned before sending anything on
// every path — the mode switch they read had no setter anywhere in the product, took no Vite
// env input, and the read path never consulted it at all. Deleting them removes a false claim;
// deleting them and recording nothing would remove the only trace that a real, populated
// register sits behind each of those workspaces. So the trace moved into `lib/lifecycleStores.ts`
// and is enforced here (shape, ratchet, behaviour) and in the backend's
// `LifecycleStoreBackingContractTest` (every declared endpoint resolves to a real controller).
// ─────────────────────────────────────────────────────────────────────────────

describe('F2-202 — the lifecycle-store registry', () => {
  it('declares every store exactly once, with a resolvable identity', () => {
    const ids = LIFECYCLE_STORES.map(s => s.id);
    expect(new Set(ids).size, 'duplicate store ids').toBe(ids.length);
    // Anti-vacuity: the assertions below are all `allSatisfy`-shaped and pass over an empty
    // array. F2-197: every detector must be shown to have a population.
    expect(LIFECYCLE_STORES.length).toBeGreaterThanOrEqual(8);
    for (const id of ids) expect(lifecycleStore(id)?.id).toBe(id);
  });

  it('the browser-local subset is derived, never a second hand-written list', () => {
    // F2-196's rule. A second list drops whatever nobody remembered — there, the token the
    // platform's own front door writes, so every DSR filed through it was invisible.
    const derived = LIFECYCLE_STORES.filter(s => s.state === 'BROWSER_LOCAL');
    expect(BROWSER_LOCAL_LIFECYCLE_STORES).toEqual(derived);
    const src = stripComments(read('lib/lifecycleStores.ts'));
    expect(src, 'BROWSER_LOCAL_LIFECYCLE_STORES must be computed from LIFECYCLE_STORES')
      .toMatch(/BROWSER_LOCAL_LIFECYCLE_STORES[^=]*=\s*\n?\s*LIFECYCLE_STORES\.filter/);
  });

  /**
   * DECREASE-ONLY. Wiring a store to its server register lowers this; nothing may raise it.
   *
   * ⚠ Not a hard zero, and deliberately so: seven workspaces cannot be rewired in one slice,
   * and a red build nobody can fix gets disabled. But unlike a tolerance, each member carries
   * its real endpoint and measured row count, and the backend gate resolves the endpoint — so
   * a tolerated entry is not indistinguishable from a reviewed one.
   */
  /**
   * ⚠ Both numbers are what the detectors actually return, checked against the running app
   * rather than assumed: 6 stores are browser-local in both directions, and 7 of the 8 are not
   * fully server-backed (the seventh is `audit-findings`, which reads the server and writes
   * locally). The first draft set the browser-local baseline to 7 by counting entries before
   * `audit-findings` was reclassified — one too loose, and F2-195's lesson is that a baseline
   * lifted from a measurement of something else is worse than no baseline, because it looks
   * measured.
   */
  const BASELINE_BROWSER_LOCAL_STORES = 6;
  const BASELINE_NOT_FULLY_SERVER_BACKED = 6;   // F2-203 wired audit-findings (was 7)

  it('no new browser-local lifecycle store appears', () => {
    expect(
      BROWSER_LOCAL_LIFECYCLE_STORES.length,
      `browser-local lifecycle stores must not increase past ${BASELINE_BROWSER_LOCAL_STORES}. `
      + 'If you wired one, LOWER the baseline in the same commit; if you added one, wire it '
      + 'instead.',
    ).toBeLessThanOrEqual(BASELINE_BROWSER_LOCAL_STORES);
  });

  it('the count of stores that cannot persist a write only decreases', () => {
    // Tracked separately because a store that READS the server and drops its writes is not
    // safer than an honestly local one — it is harder to notice, since the list it shows is
    // real. Collapsing both into one figure would let a reclassification look like progress.
    const notBacked = LIFECYCLE_STORES.filter(s => s.state !== 'SERVER_BACKED');
    expect(
      notBacked.length,
      `${notBacked.length} of ${LIFECYCLE_STORES.length} lifecycle stores still cannot persist a `
      + `write to the server; the baseline is ${BASELINE_NOT_FULLY_SERVER_BACKED} and may only go `
      + 'down. Members: ' + notBacked.map(s => `${s.id} (${s.state})`).join(', '),
    ).toBeLessThanOrEqual(BASELINE_NOT_FULLY_SERVER_BACKED);
  });

  it('exactly the DSR and audit-finding stores are server-backed today', () => {
    const backed = LIFECYCLE_STORES.filter(s => s.state === 'SERVER_BACKED').map(s => s.id);
    // Enforced in BOTH directions. The forward half stops an entry being flipped to
    // SERVER_BACKED without wiring; the reverse half means wiring the next one must update
    // this list, so the gate cannot silently describe a stale world (F2-199's lesson: the
    // reverse leg is the one nobody writes first, and the one that catches an entry that was
    // never in the population). It fired as designed when F2-203 wired audit-findings.
    expect(backed).toEqual(['privacy-dsr', 'audit-findings']);
  });

  it('the SERVER_READ_LOCAL_WRITE state has no members', () => {
    // F2-203 closed the only one. Asserted rather than left implicit, because a state with
    // zero members and a paragraph of documentation reads exactly like a state with one — and
    // that documentation described `auditLifecycle.ts` in the present tense until this slice.
    // If a future store genuinely belongs here, deleting this leg is the deliberate act.
    expect(LIFECYCLE_STORES.filter(s => s.state === 'SERVER_READ_LOCAL_WRITE').map(s => s.id))
      .toEqual([]);
  });

  /**
   * ⚠⚠ This leg found real errors in the registry on its first run, for **3 of the 5** stores
   * whose keys had been copied from the `PersistenceAdapter` definitions being deleted:
   * `grc.bcm.invocations.v1` vs the module's `grc.bcm.invocation.v1`,
   * `grc.cyber.vulnerabilities.v1` vs `grc.cyber.vuln.v1`, and a key claimed for
   * `auditLifecycle.ts`, which keeps no localStorage key at all — that module hydrates from
   * the server and caches in a module-level array, which is why it is the third state.
   *
   * The adapters were wrong about their own stores. Had the mode switch been reachable,
   * `adapterListAll` would have read keys nothing had ever written.
   */
  it('every declared localStorage key is one its module actually uses', () => {
    const withKey = LIFECYCLE_STORES.filter(s => s.localKey !== '');
    expect(withKey.length, 'no store declares a key — the population is empty')
      .toBeGreaterThanOrEqual(5);
    for (const store of withKey) {
      const src = read(store.module);
      expect(src, `${store.id} declares ${store.localKey} but ${store.module} never mentions it`)
        .toContain(store.localKey);
    }
  });

  it('a store that declares no key really keeps none', () => {
    // The reverse direction. Without it, blanking a key would silence the leg above rather
    // than fail it — F2-199's lesson that the reverse leg is the one nobody writes first.
    for (const store of LIFECYCLE_STORES.filter(s => s.localKey === '')) {
      const own = stripComments(read(store.module));
      const keys = [...own.matchAll(/'(grc\.[a-z0-9.-]+\.v\d+)'/g)].map(m => m[1]);
      expect(keys, `${store.id} declares no localStorage key but ${store.module} writes ${keys}`)
        .toEqual([]);
    }
  });
});

describe('F2-202 — the deleted persistence-mode switch stays deleted', () => {
  it('lib/platformPersistence.ts does not exist', () => {
    expect(existsSync(join(SRC, 'lib/platformPersistence.ts'))).toBe(false);
  });

  it('no product file calls a mirror-to-backend helper or reads the mode key', () => {
    // Scan the real product surface, not a hand-listed set of files.
    const files = import.meta.glob('../{lib,pages,components}/**/*.{ts,tsx}', {
      query: '?raw', import: 'default', eager: true,
    }) as Record<string, string>;
    const names = Object.keys(files);
    expect(names.length, 'globbed no product files — the scan has rotted').toBeGreaterThan(300);

    const offenders: string[] = [];
    for (const [path, raw] of Object.entries(files)) {
      if (path.includes('__tests__')) continue;
      const body = stripComments(raw);
      if (/\bmirror(Save|Transition|Delete)ToBackend\s*\(/.test(body)
        || /\bcurrentPersistenceMode\s*\(/.test(body)
        || /grc\.persistence\.mode/.test(body)) offenders.push(path);
    }
    expect(
      offenders,
      'A mirror-to-backend helper or the retired persistence-mode key is back in product code. '
      + 'Comments are stripped before this check, so these are real call sites. Any such '
      + 'mechanism needs a reachable setter AND a read path that consults it — the deleted one '
      + 'had neither, so it could not send and could not have displayed what it sent.',
    ).toEqual([]);
  });
});

describe('F2-202 — the DSR lifecycle mirrors the server and is derived', () => {
  it('the terminal set is DERIVED from the transition table', () => {
    expect([...DSR_TERMINAL].sort()).toEqual(['DELIVERED', 'REJECTED']);
    const src = stripComments(read('lib/privacyLcApi.ts'));
    // Assert the DERIVATION, not the value. A hand-typed `['DELIVERED','REJECTED']` satisfies
    // the assertion above and is exactly what F2-196 found wrong: the SLA sweep's second list
    // omitted EXTENDED, so the extended PDPL Article 4 deadline could never be enforced.
    expect(src, 'DSR_TERMINAL must be computed from DSR_ALLOWED, never typed as a second list')
      .toMatch(/DSR_TERMINAL[^=]*=\s*\n?\s*\(Object\.keys\(DSR_ALLOWED\)[^;]*\.filter\(/);
  });

  it('"open" is the COMPLEMENT of the terminal set', () => {
    // EXTENDED is the case that matters: it is a live, non-terminal state whose deadline the
    // server has already pushed out, so a case past it is unambiguously in breach.
    expect(isOpen('EXTENDED')).toBe(true);
    expect(isOpen('INTAKE')).toBe(true);
    expect(isOpen('DELIVERED')).toBe(false);
    expect(isOpen('REJECTED')).toBe(false);
    // An unrecognised status must read as OPEN — for a statutory clock, chasing an unknown is
    // the safe direction; excusing it is how a breach goes unescalated.
    expect(isOpen('SOMETHING_NOBODY_DECLARED')).toBe(true);
    const src = stripComments(read('lib/privacyLcApi.ts'));
    expect(src, 'isOpen must negate the terminal set, not test a list of open states')
      .toMatch(/return\s+!DSR_TERMINAL\.includes\(/);
  });

  it('the transition table matches the server DsrCaseLifecycle.ALLOWED edge for edge', () => {
    // Pinned against the Java source in this repo. The FE copy exists only to avoid offering a
    // button the server will 409 — never as a second opinion about what is legal (F2-190).
    expect(DSR_ALLOWED).toEqual({
      INTAKE:             ['VERIFYING_IDENTITY', 'REJECTED', 'EXTENDED'],
      VERIFYING_IDENTITY: ['COLLECTING', 'REJECTED', 'EXTENDED'],
      COLLECTING:         ['REDACTING', 'REJECTED', 'EXTENDED'],
      REDACTING:          ['DELIVERED', 'EXTENDED'],
      EXTENDED:           ['VERIFYING_IDENTITY', 'COLLECTING', 'REDACTING', 'DELIVERED', 'REJECTED'],
      DELIVERED:          [],
      REJECTED:           [],
    });
    expect(mayTransition('INTAKE', 'DELIVERED')).toBe(false);
    expect(mayTransition('REDACTING', 'DELIVERED')).toBe(true);
    expect(mayTransition('DELIVERED', 'REDACTING')).toBe(false);
  });
});

describe('F2-202 — the client mints neither a reference code nor a statutory deadline', () => {
  const client = stripComments(read('lib/privacyLcApi.ts'));

  it('the create payload type carries no id and no slaDeadline', () => {
    const iface = client.match(/interface DsrCreateRequest \{([\s\S]*?)\n\}/)?.[1] ?? '';
    expect(iface, 'DsrCreateRequest parsed empty — the parse has rotted').toContain('type');
    expect(iface, 'the server mints the DSR reference (F2-172): the client may not send an id')
      .not.toMatch(/^\s*id\s*[?:]/m);
    expect(iface, 'the server issues the PDPL Article 4 deadline: the client may not send one')
      .not.toMatch(/slaDeadline/);
  });

  it('no arithmetic in the DSR client produces a 30-day window', () => {
    // The three minters this slice removed all had this shape: `Date.now() + 30 * 86_400_000`.
    // Asserting on the arithmetic rather than on a symbol name means a renamed helper still
    // fails — F2-192's lesson that a rename must not be an exemption.
    expect(client).not.toMatch(/30\s*\*\s*86[_,]?400[_,]?000/);
    expect(client, 'no Math.random in a tracking reference — three digits of entropy collide')
      .not.toMatch(/Math\.random\s*\(/);
  });

  it('neither the page nor the overview computes a DSR deadline or reference', () => {
    for (const p of ['pages/privacy-lifecycle/PrivacyLifecyclePage.tsx',
                     'pages/lifecycle-overview/LifecycleOverviewPage.tsx']) {
      const body = stripComments(read(p));
      expect(body, `${p} recomputes a statutory deadline`)
        .not.toMatch(/30\s*\*\s*86[_,]?400[_,]?000/);
      expect(body, `${p} mints a DSR reference`).not.toMatch(/`DSR-\$\{/);
    }
  });
});

describe('F2-202 — the privacy page decides through the server', () => {
  const page = stripComments(read('pages/privacy-lifecycle/PrivacyLifecyclePage.tsx'));

  it('reads the register through the real client', () => {
    // Assert the CALL SHAPE, not the imported name. F2-194 had two assertions satisfied by an
    // import statement and a type name rather than by a call, and only the mutation harness saw it.
    expect(page).toMatch(/dsrApi\.list\s*\(/);
    expect(page).toMatch(/dsrApi\.transition\s*\(/);
    expect(page).toMatch(/dsrApi\.create\s*\(/);
  });

  it('replaces a decided row from the SERVER response, never a local guess', () => {
    // The load-bearing leg. A rewire that kept a locally-computed status write would look
    // identical on screen and be the same defect it was meant to fix.
    expect(page).toMatch(/setDsrs\(prev => prev \? prev\.map\(x => x\.id === updated\.id \? updated : x\) : prev\)/);
    expect(page, 'a decision must not compute the new status locally')
      .not.toMatch(/setDsrs\([^)]*status:\s*['"](DELIVERED|REJECTED|EXTENDED|COLLECTING)['"]/);
  });

  it('does not read or write any DSR localStorage key', () => {
    expect(page).not.toMatch(/grc\.privacy\.dsr/);
    // ⚠ Word-anchored. The first draft was `/loadDsr|saveDsr|transitionDsr/` and it FAILED on
    // this page's own `loadDsrCases()` — a substring match satisfied by something other than
    // its claim, in the gate written to catch that class of thing. `\b` after the name is what
    // separates the retired browser-local accessor from a new server-backed one.
    expect(page, 'the DSR half must not import from the browser-local privacy lifecycle module')
      .not.toMatch(/\b(loadDsr|saveDsr|transitionDsr)\b/);
  });

  it('does not use useApiList for the register', () => {
    // `useApiList` prefers a demo corpus when the server returns zero rows. That fallback is
    // what hid F2-158's 500 for the whole Role API — a de-mocked page must not use it.
    expect(page).not.toMatch(/useApiList/);
  });

  it('an unread register renders as unmeasured, not as zero', () => {
    // F2-197's rule, on a statutory clock: an invented figure invites a question, an invented
    // all-clear closes one. `Kpi`'s `value={null}` + `emptyHint` is UX-008's convention.
    expect(page).toMatch(/value=\{dsrStats \? String\(dsrStats\.open\) : null\}/);
    expect(page).toMatch(/value=\{dsrStats \? String\(dsrStats\.pastDeadline\) : null\}/);
    expect(page, 'an overdue tile may not fall to success when nothing has been measured')
      .not.toMatch(/dsrStats\.pastDeadline > 0 \? 'danger' : 'success'/);
    expect(page, 'the failed/unread state must be distinguishable — dsrs stays null')
      .toMatch(/setDsrs\(null\)/);
  });

  it('offers only transitions the server allows', () => {
    expect(page).toMatch(/DSR_ALLOWED\[d\.status\]/);
    // The old block hard-coded four `status === '…'` tests plus a three-way `!==` chain.
    expect(page, 'action buttons must come from the table, not from status equality tests')
      .not.toMatch(/d\.status === 'INTAKE' && <Button/);
    expect(page, 'no hand-written closed-status chain')
      .not.toMatch(/d\.status !== 'DELIVERED' && d\.status !== 'REJECTED'/);
  });
});

describe('F2-202 — the DSR aggregate distinguishes late from escalated', () => {
  const NOW = new Date('2026-08-13T12:00:00Z');
  const row = (over: Partial<DsrCase>): DsrCase => ({
    id: 'DSR-X', type: 'ACCESS', subjectName: 'S', nationalIdLast4: '0000',
    contactEmail: 's@example.sa', status: 'COLLECTING',
    filedAt: '2026-07-01T00:00:00Z', slaDeadline: '2026-07-31', ...over,
  });

  it('past the deadline and past-with-no-breach-stamp are separate counts', () => {
    // This is F2-196's defect made visible: the sweep excluded EXTENDED, so the cases most
    // likely to breach were the only ones never escalated. Measured live 2026-08-13: both
    // EXTENDED rows in prod still carry sla_breached_at = NULL while dev's are stamped.
    const stats = aggregateDsr([
      row({ id: 'a', status: 'EXTENDED', slaDeadline: '2026-07-01' }),                     // late, unstamped
      row({ id: 'b', status: 'COLLECTING', slaDeadline: '2026-07-01',
            slaBreachedAt: '2026-07-02T00:00:00Z' }),                                       // late, stamped
      row({ id: 'c', status: 'REDACTING', slaDeadline: '2099-01-01' }),                     // on track
      row({ id: 'd', status: 'DELIVERED', slaDeadline: '2026-07-01',
            deliveredAt: '2026-07-10T00:00:00Z' }),                                         // closed
    ], NOW);
    expect(stats.total).toBe(4);
    expect(stats.open).toBe(3);
    expect(stats.pastDeadline).toBe(2);
    expect(stats.pastDeadlineUnrecorded).toBe(1);
  });

  it('a terminal row past its deadline is not counted late', () => {
    // The mirror of F2-196's other half: `status === 'FULFILLED'` named a token this register
    // holds in 0 of 20 rows, so seven finished cases each rendered a red "N days overdue".
    expect(isPastDeadline(row({ status: 'DELIVERED', slaDeadline: '2026-01-01' }), NOW)).toBe(false);
    expect(isPastDeadline(row({ status: 'REJECTED', slaDeadline: '2026-01-01' }), NOW)).toBe(false);
    expect(isPastDeadline(row({ status: 'EXTENDED', slaDeadline: '2026-01-01' }), NOW)).toBe(true);
  });

  it('with nothing delivered the average is null, not zero', () => {
    // A 0 there reads as "we close requests the same day".
    expect(aggregateDsr([row({ status: 'INTAKE' })], NOW).avgDaysToDeliver).toBeNull();
    expect(aggregateDsr([], NOW).avgDaysToDeliver).toBeNull();
    expect(aggregateDsr([], NOW).pastDeadline).toBe(0);
  });

  it('an absent or unparseable deadline is not treated as on-track', () => {
    expect(daysToDeadline(row({ slaDeadline: '' }), NOW)).toBeNull();
    expect(daysToDeadline(row({ slaDeadline: 'not-a-date' }), NOW)).toBeNull();
    expect(isPastDeadline(row({ slaDeadline: '' }), NOW)).toBe(false);
  });

  it('ropaIds is parsed from the joined string the wire actually sends', () => {
    // The browser copy declared `string[]`; the column is NVARCHAR(MAX) and the DTO passes it
    // through verbatim, so exactly one place may know the shape.
    expect(ropaIdList(row({ ropaIds: 'ROPA-1, ROPA-2 ,ROPA-3' })))
      .toEqual(['ROPA-1', 'ROPA-2', 'ROPA-3']);
    expect(ropaIdList(row({ ropaIds: null }))).toEqual([]);
    expect(ropaIdList(row({ ropaIds: '' }))).toEqual([]);
  });
});

describe('F2-202 — the knowledge base reads its list from the server', () => {
  const kb = stripComments(read('lib/kb.ts'));

  it('listArticles calls the API, not a mode-gated adapter', () => {
    // The live defect this slice fixed: every other call in kb.ts went to the real server, but
    // listArticles went through the stuck mode switch to an empty localStorage key. Probed
    // 2026-08-13: kb.articles holds 21 PUBLISHED rows and GET /api/kb/articles returns all 21.
    expect(kb).toMatch(/api\.get<KbArticle\[\]>\s*\(\s*'\/api\/kb\/articles'\s*\)/);
    expect(kb).not.toMatch(/adapterListAll/);
    expect(kb).not.toMatch(/KB_ARTICLE_ADAPTER/);
  });

  it('the page no longer explains an empty list away as a publishing gap', () => {
    // A comment that answers the question a reader was about to ask, with a cause that is not
    // the cause, is worse than no comment (F2-184's shape, in a page's own docs).
    //
    // ⚠ `unquoted()` first. This repo's convention is that a correction QUOTES the sentence it
    // replaces — that is what makes the corrections auditable — so a detector matching raw text
    // reads the quotation as the claim and fires on the fix. It did exactly that here on the
    // first run. F2-193 hit this in a backend gate and solved it the same way: strip
    // double-quoted spans, so a quotation cannot be mistaken for an assertion.
    const page = unquoted(read('pages/knowledge-base/KbHomePage.tsx'));
    expect(page, 'the retired claim must not be asserted outside a quotation')
      .not.toMatch(/falls back to localStorage seed/);
    // Assert the retained explanation by a sentinel that appears nowhere else in the tree and
    // cannot be produced accidentally — not by a prose substring, which wraps across lines in a
    // block comment and which some other construct could satisfy (F2-194's two survivors were a
    // type name and an import statement).
    expect(read('pages/knowledge-base/KbHomePage.tsx'), 'the correction explaining why must stay')
      .toContain('F2-202-EMPTY-KEY');
  });
});
