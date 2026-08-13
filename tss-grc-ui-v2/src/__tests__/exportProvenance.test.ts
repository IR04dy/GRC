import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { join, dirname, resolve, posix, sep } from 'node:path';

/**
 * F2-199 — a page that EXPORTS a document must have server-derived data in scope.
 *
 * Seven instruments now guard fabricated data in this frontend, and each is keyed on a
 * different mechanism:
 *
 *   • `demoFallbackContract`'s ratchet   -> `/mock/i` imports        (a FILENAME)
 *   • its F2-169 hard zero               -> `corpusNamesImportedBy`  (a 3-entry filename list)
 *   • F2-168's `demoRows()` tagging      -> corpora that opted in
 *   • the `isDemo` disclosure rule       -> `useApiList` consumers
 *   • F2-195's `kpiProvenance`           -> a literal or `|| N` inside a `<Kpi>` tag
 *   • F2-197's lifecycle-health rule     -> a catalogue read as props
 *   • F2-198's `pageDataProvenance`      -> routed components that call nothing
 *
 * None of them asks what this file asks, and the harm here differs in KIND rather than degree.
 * A fabricated dashboard misleads whoever is looking at it, and looking again can correct it. A
 * fabricated EXPORT produces an artefact that LEAVES the platform — downloaded, signed, attached
 * to an email, filed with a regulator. Nothing downstream of the download can tell that the
 * figures were invented, and a signature line invites a human to vouch for them.
 *
 * `/nca-ecc-export` was the instance: an "NCA ECC-1 Self-Assessment Submission" addressed to the
 * National Cybersecurity Authority, with CISO and CEO signature lines, a CSV download and a
 * print-to-PDF path, whose entire content came from a hard-coded 30-control corpus — and whose
 * cover named `brandName` ("TSS GRC Platform", the software product) as the filing entity beside
 * a hard-coded CR number, so every tenant's return identified the vendor rather than the filer.
 *
 * ⚠ A HARD ZERO, not a ratchet. The exemption map is deliberately EMPTY: after retiring that page
 * the population among routed pages is zero, and house rule 3's warning applies once a sweep has
 * landed — a tolerated member would be indistinguishable from a reviewed one. The machinery is
 * kept so that a future member has to be explained rather than counted.
 */

const SRC = resolve(__dirname, '..');

/**
 * ⚠ ONE alternation, left-to-right. Written the obvious way (block comments first) this deletes
 * 45% of `App.tsx`, because a `//` prose comment there contains a literal `/*`. F2-68(c) found it
 * first — `Appendix/scripts/baseline_census.py` lines 139-142 already carry the correct ordering
 * and the explanation — and F2-198 reproduced it by writing a new scanner without reading that
 * one. A corrupted source yields a SMALLER population, which reads exactly like good news.
 */
function stripComments(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');
}

const APP = stripComments(readFileSync(join(SRC, 'App.tsx'), 'utf8'));

/**
 * ⚠⚠ F2-205 — THE HOLE THIS GATE HAD, and the reason it is worth naming precisely.
 *
 * `reportingApi.packetXlsx(body)` and `packetDocx(body)` POST a payload the CALLER assembled to
 * the server's branded-document generator, which typesets it and hands back a file. They are
 * EMITTERS. Every other `reportingApi` member is a `downloadFile` GET, where the server builds
 * the document from its own data — those are genuine provenance.
 *
 * `/cyber-lifecycle` emitted an "NCA Haseen — ECC v3 Annual Self-Assessment Filing" four ways
 * (.txt, print-to-PDF stamped CONFIDENTIAL — REGULATOR, and branded .xlsx/.docx) out of
 * `lib/cyberLifecycle.ts`'s browser constants — and this gate passed it, because the ONLY two
 * `SERVER_CALL` tokens on the page were those two packet calls. **The server call the provenance
 * rule found was the one carrying the invented figures out.**
 *
 * 🔑 The general lesson, one level up from F2-203's "a one-hop rule certifies the FILE, not the
 * FIGURE": a rule that asks *does this page talk to a server?* cannot tell an inbound READ from
 * an outbound WRITE, and at an export endpoint that difference is the entire question. Ten
 * instruments in this tree are each keyed on a different mechanism; this one was keyed on the
 * DIRECTION-BLIND fact of a call existing.
 */
const CLIENT_ASSEMBLED_EXPORT = /\breportingApi\.packet(?:Xlsx|Docx)\s*\(/g;

/** Building a file the browser will save, driving the print dialog, or POSTing one to be built. */
const EMITS_DOCUMENT =
  /\.download\s*=|createObjectURL\s*\(|new\s+Blob\s*\(|window\.print\s*\(|\breportingApi\.packet(?:Xlsx|Docx)\s*\(/;

/**
 * Any request to a server — the same rule F2-198's gate uses, with one correction.
 *
 * ⚠ The optional `<…>` is load-bearing and its absence was a real hole in BOTH gates. The house
 * idiom is a TYPED wrapper — `api.get<BoardPackAggregate>('/api/v1/reporting/board-pack.json')` —
 * and a generic argument sits between the method name and the paren, so `\w+\s*\(` never matches
 * it. **118 files** use that form. This surfaced as `BoardPackPage` being reported as having no
 * server data in scope when `lib/boardPackAggregate.ts` line 174 is a plain `api.get<T>(…)`.
 * The error direction is over-reporting — a page that does call a server looks like one that
 * cannot — which is the loud direction, and is why F2-198's eleven findings survive re-measurement
 * (none of them uses the generic form). Bounded to one line and to no parens so it cannot run past
 * the call it belongs to.
 */
const SERVER_CALL =
  /\b\w*[Aa]pi\.\w+\s*(?:<[^()\n]{0,200}>)?\s*\(|\buseApi(?:List|Resource)\s*[<(]|\bfetch\s*\(/;

const IMPORT_SPEC = /import\s+(?:type\s+)?[^;'"]*from\s+'([^']+)'/g;

/**
 * ⚠ The TRANSPORT is not evidence that a page uses it. `lib/api.ts` contains `fetch(` by
 * definition, so a one-hop rule that follows it marks every page importing an `ApiError` type or
 * a DTO as "backed" — which is how the first draft of this gate resolved a hard-coded page as
 * having server data in scope. A real call — `api.get(`, `xApi.list(` — matches SERVER_CALL in
 * the page itself and needs no hop.
 */
const TRANSPORT = /(^|\/)lib\/api$/;

/** Resolve a module base to the real file on disk, returning the path actually opened. */
function resolveModule(base: string): { path: string; body: string } | null {
  for (const ext of ['.ts', '.tsx', '/index.ts', '/index.tsx']) {
    const p = base + ext;
    if (existsSync(p) && statSync(p).isFile()) {
      return { path: p, body: readFileSync(p, 'utf8') };
    }
  }
  return null;
}

/** page module specifier -> the routes that render it, from App.tsx. */
function routedPages(): Map<string, string[]> {
  const modules = new Map<string, string>();
  for (const m of APP.matchAll(/const\s+(\w+)\s*=\s*lazy\(\(\)\s*=>\s*import\('([^']+)'\)/g)) {
    modules.set(m[1], m[2]);
  }
  for (const m of APP.matchAll(/import\s+\{([^}]*)\}\s+from\s+'(\.\/pages\/[^']+)'/g)) {
    for (const raw of m[1].split(',')) {
      const n = raw.trim();
      if (n && !modules.has(n)) modules.set(n, m[2]);
    }
  }
  const out = new Map<string, string[]>();
  for (const m of APP.matchAll(/<Route\s+path="([^"]+)"[^>]*element=\{<(\w+)/g)) {
    const mod = modules.get(m[2]);
    if (!mod) continue;
    out.set(mod, [...(out.get(mod) ?? []), m[1]]);
  }
  return out;
}

interface Emitter { page: string; routes: string[]; backedVia: string | null }

/**
 * A page is backed if it calls a server itself, or imports a non-transport module that does.
 *
 * ⚠ Resolved by following the import, NOT by matching a filename — precisely the flaw F2-198
 * found in `demoFallbackContract`'s F2-169 population, whose hard zero is real but whose
 * membership is a three-entry filename pattern, so the zero covers less than it looks like.
 */
function census(): Emitter[] {
  const out: Emitter[] = [];
  for (const [mod, routes] of routedPages()) {
    const found = resolveModule(join(SRC, mod.replace(/^\.\//, '')));
    if (!found) continue;
    const code = stripComments(found.body);
    if (!EMITS_DOCUMENT.test(code)) continue;

    // ⚠⚠ Strip the client-assembled export calls BEFORE asking whether a server is in scope.
    // A page whose only server call is `reportingApi.packetXlsx(localData)` has no server DATA —
    // it has a server PRINTER. See CLIENT_ASSEMBLED_EXPORT above.
    const forProvenance = code.replace(CLIENT_ASSEMBLED_EXPORT, 'EMITTER(');
    let backedVia: string | null = SERVER_CALL.test(forProvenance) ? 'itself' : null;
    if (backedVia === null) {
      for (const m of code.matchAll(IMPORT_SPEC)) {
        if (!m[1].startsWith('.') || TRANSPORT.test(m[1])) continue;
        const target = resolveModule(resolve(dirname(found.path), m[1]));
        if (target && SERVER_CALL.test(
              stripComments(target.body).replace(CLIENT_ASSEMBLED_EXPORT, 'EMITTER('))) {
          backedVia = m[1];
          break;
        }
      }
    }
    out.push({
      page: found.path.slice(SRC.length + 1).split(sep).join(posix.sep),
      routes,
      backedVia,
    });
  }
  return out;
}

const EMITTERS = census();
const UNBACKED = EMITTERS.filter(e => e.backedVia === null);

/**
 * ⚠⚠ F2-205: NO LONGER EMPTY, and the reason is the instrument, not a regression.
 *
 * Correcting the direction-blindness described at CLIENT_ASSEMBLED_EXPORT moved the population
 * from 9 backed / 0 unbacked to 9 backed / 2 unbacked. Neither of the two is new code: both have
 * emitted a fabricated authority document for as long as this gate has existed, and both passed
 * it for exactly the reason `/cyber-lifecycle` did — their only server call was the packet POST
 * carrying the invented figures out.
 *
 * 🔑 F2-204's rule applies and points the other way this time. There, a ratchet's number wanted
 * to go UP and the honest fix was to re-scope its QUESTION, because the question was wrong. Here
 * the question was right and the ANSWER was under-measured, so the number moves and says so. What
 * must not happen is quietly keeping a zero that no longer means anything — a hard zero over a
 * population the instrument cannot see is the most reassuring possible artefact.
 *
 * These two are DEFECTS, filed, not designs. The name of this map is honest about that: an entry
 * buys time, not absolution, and the count above may only go down.
 *
 * ⚠ The first draft of this file carried two entries, `pages/csv-import/CsvImportPage.tsx` and
 * `pages/mfa/MfaSetupPage.tsx`, with fluent multi-sentence reasons — one of them a whole security
 * narrative about browser-local MFA. Both are Sprint 4 §3.3 CULLED pages: their routes were
 * removed and only the folders remain on disk, so neither was ever in this population. That is
 * F2-188's failure mode (manufacturing reasons for members nobody verified) and the leg that
 * caught it was the REVERSE direction, not the forward one — the forward assertion was passing
 * because the population was already zero. 🔑 A reason written for something that is not in the
 * population reads exactly like a reason for something that is.
 */
const UNBACKED_EMITTERS_FILED: Record<string, string> = {
  'pages/crisis/CrisisManagementPage.tsx':
    'Emits an NCEMA crisis-management packet (a .txt download, a print-to-PDF path, and branded '
    + '.xlsx/.docx via reportingApi.packet*) built by `lib/crisis.ts`\'s `buildNcemaPacket` / '
    + '`buildNcemaSheets` from SCENARIOS, EXERCISES and the `loadActive`/`loadHistory` '
    + 'localStorage stores. Measured 2026-08-13: the page makes NO other server call, so every '
    + 'figure in a packet addressed to the National Center for Risk and Crisis Management is a '
    + 'browser constant — the same defect F2-199 found at /nca-ecc-export and F2-205 found in the '
    + 'NCA Haseen path, through the same blind spot. Not fixed here because it needs its own '
    + 'measurement of the real crisis registers first: F2-205 deliberately did not guess which '
    + 'tables back it, after four guessed-path errors in this lineage. FILED.',
  'pages/bcm-lifecycle/BcmLifecyclePage.tsx':
    'Emits a SAMA operational-resilience packet the same three ways, built by '
    + '`lib/bcmLifecycle.ts`\'s `buildOpresPacket` / `buildOpresSheets` over the BIA, recovery-'
    + 'strategy, drill and invocation stores. This one is NOT wireable by a frontend slice and '
    + 'that is the important part: `lib/lifecycleStores.ts` records three of those four registers '
    + 'as NO_SERVER_SURFACE — `bcm_lc.bia_records` (8 rows), `bcm_lc.recovery_strategies` (8) and '
    + '`bcm_lc.drills` (5) have no controller, DTO or service in either database. So the packet '
    + 'cannot be made real until that backend exists, and retiring it would remove the only '
    + 'surface those 21 rows appear on. An owner decision on the BCM backend, FILED.',
};

function report(es: Emitter[]): string {
  return es.map(e => `  ${e.page}\n      routes: ${e.routes.join(', ')}`).join('\n');
}

describe('F2-199 — an exported or printed document must derive from server data', () => {
  it('no routed page emits a document without server data in scope', () => {
    const unexplained = UNBACKED.filter(e => !(e.page in UNBACKED_EMITTERS_FILED));
    expect(unexplained.length,
      'This page builds a file the user will save, or prints one, and nothing in its scope ever '
      + 'asks a server. An exported document leaves the platform: it gets signed, attached and '
      + 'filed, and nothing downstream can tell that the figures were invented. Wire it to its '
      + `register, retire it as F2-199 retired /nca-ecc-export, or explain it here:\n${report(unexplained)}`)
      .toBe(0);
  });

  it('the filed population is named exactly, and only shrinks', () => {
    // ⚠⚠ F2-205: this leg used to assert the map was EMPTY, under house rule 3 — once a sweep has
    // landed, a tolerated member is indistinguishable from a reviewed one. Its own comment said
    // "if a member is ever added, delete this assertion in the same commit and say why", and this
    // is that commit. Why: correcting the gate's direction-blindness revealed two emitters it had
    // always mis-classified, and both need work outside a frontend slice (one needs the real
    // crisis registers measured, one needs a BCM backend that does not exist).
    //
    // Exact, not a ceiling, and in BOTH directions: a third member fails here rather than
    // silently joining a count, and fixing one of these two must delete its entry in the same
    // commit. That is the leg F2-199 learned to write after two of its own first-draft reasons
    // described culled pages that were never in the population.
    expect(Object.keys(UNBACKED_EMITTERS_FILED).sort()).toEqual([
      'pages/bcm-lifecycle/BcmLifecyclePage.tsx',
      'pages/crisis/CrisisManagementPage.tsx',
    ]);
  });

  it('every exemption names a page that is really in the population', () => {
    // The reverse direction — and the leg that caught this file's own first-draft defect, where
    // both entries named culled, unrouted pages that no route reaches.
    const inPopulation = new Set(UNBACKED.map(e => e.page));
    for (const page of Object.keys(UNBACKED_EMITTERS_FILED)) {
      expect(inPopulation.has(page),
        `${page} is not an unbacked routed emitter — delete its exemption, it explains nothing`)
        .toBe(true);
    }
  });

  it('an exemption reason must be substantial, not a label', () => {
    // F2-188: a named-map gate proves a reason EXISTS; nothing proves it is TRUE. The least it can
    // do is refuse a bare label — which is what six of my first-draft entries were in F2-187.
    for (const [page, reason] of Object.entries(UNBACKED_EMITTERS_FILED)) {
      expect(reason.length, `${page}'s reason is too short to be an argument`)
        .toBeGreaterThan(120);
    }
  });

  /**
   * Anti-vacuity. Every assertion above passes trivially if the scan resolves nothing — which is
   * exactly what F2-198's broken comment stripper did, and it read as good news. These are the
   * numbers a working scan returns, measured 2026-08-12.
   */
  it('the scan is still finding the codebase it is meant to measure', () => {
    expect(routedPages().size, 'App.tsx route parse collapsed').toBeGreaterThanOrEqual(250);
    expect(EMITTERS.length, 'the document-emission detector has stopped working')
      .toBeGreaterThanOrEqual(10);
    // ⚠ F2-205 re-measured these: correcting the direction-blindness reclassified three pages
    // whose only server call was a packet POST, so 'itself' fell from 7 to 4 (measured
    // 2026-08-13: 11 emitters, 4 direct, 5 one-hop, 2 unbacked). A floor lifted from the OLD
    // measurement would have failed on a corrected instrument and read as the fix being wrong —
    // F2-195's lesson about a baseline copied from a measurement of something else.
    expect(EMITTERS.filter(e => e.backedVia === 'itself').length,
      'the direct server-call detector has stopped working')
      .toBeGreaterThanOrEqual(3);
    expect(EMITTERS.filter(e => e.backedVia !== null && e.backedVia !== 'itself').length,
      'the one-hop resolver has stopped working — no page resolves through an imported module')
      .toBeGreaterThanOrEqual(4);
  });

  /**
   * F2-205 — the emitter list is a CLOSED enumeration, and it is checked against `lib/api.ts`
   * rather than trusted.
   *
   * The discriminator is mechanical: a `reportingApi` member that takes a caller-supplied body is
   * an emitter; one that takes only scalars is a server-side generator and therefore provenance.
   * If a third body-taking export endpoint is ever added, this fails until it is classified —
   * which is the whole point, because an unclassified one would silently re-open the hole above.
   */
  it('exactly the body-taking reporting endpoints are treated as emitters', () => {
    const api = stripComments(readFileSync(join(SRC, 'lib', 'api.ts'), 'utf8'));
    const start = api.indexOf('export const reportingApi');
    expect(start, 'reportingApi must exist').toBeGreaterThan(-1);
    const block = api.slice(start, api.indexOf('\n};', start));

    const bodyTaking: string[] = [];
    const scalarOnly: string[] = [];
    for (const m of block.matchAll(/^\s{2}(\w+)\s*:\s*\(([^)]*)\)/gm)) {
      (/\bbody\s*:/.test(m[2]) ? bodyTaking : scalarOnly).push(m[1]);
    }
    expect(bodyTaking.length, 'the reportingApi parse collapsed').toBeGreaterThan(0);
    expect(scalarOnly.length, 'the reportingApi parse collapsed').toBeGreaterThan(5);

    // Every body-taking member must be recognised as an emitter by the regex...
    for (const name of bodyTaking) {
      expect(new RegExp(CLIENT_ASSEMBLED_EXPORT.source).test(`reportingApi.${name}(payload)`),
        `reportingApi.${name} takes a caller-assembled body, so it EMITS a document rather than `
        + 'providing one. Add it to CLIENT_ASSEMBLED_EXPORT, or this gate will accept it as '
        + 'proof that a page has server data — which is exactly how the fabricated NCA Haseen '
        + 'filing passed.').toBe(true);
    }
    // ...and no scalar-only member may be, since those are server-side generators.
    for (const name of scalarOnly) {
      expect(new RegExp(CLIENT_ASSEMBLED_EXPORT.source).test(`reportingApi.${name}(lang)`),
        `reportingApi.${name} builds its document server-side from server data; treating it as `
        + 'an emitter would wrongly strip real provenance').toBe(false);
    }
  });

  /**
   * A synthetic self-test, per F2-195: the assertions above pass both when the app is clean AND
   * when the detectors have rotted, so each detector is exercised against inputs whose answer is
   * known. Without this, a regex typo silently empties the population.
   */
  it('the detectors answer correctly on inputs whose verdict is known', () => {
    expect(EMITS_DOCUMENT.test('a.download = `x.csv`;')).toBe(true);
    expect(EMITS_DOCUMENT.test('window.print();')).toBe(true);
    expect(EMITS_DOCUMENT.test('const blob = new Blob([csv]);')).toBe(true);
    expect(EMITS_DOCUMENT.test('const x = downloadFlag;')).toBe(false);

    // F2-205's discriminator, both ways. The first line is a page whose ONLY server call is the
    // outbound packet POST: it must read as an EMITTER and NOT as provenance.
    const onlyPacket = "await reportingApi.packetXlsx({ sheets: buildLocalSheets() });";
    expect(EMITS_DOCUMENT.test(onlyPacket)).toBe(true);
    expect(SERVER_CALL.test(onlyPacket.replace(
      new RegExp(CLIENT_ASSEMBLED_EXPORT.source, 'g'), 'EMITTER('))).toBe(false);
    // ...while a server-side generator of the same family IS provenance and must survive.
    const serverBuilt = "await reportingApi.boardPackPdf(lang);";
    expect(SERVER_CALL.test(serverBuilt.replace(
      new RegExp(CLIENT_ASSEMBLED_EXPORT.source, 'g'), 'EMITTER('))).toBe(true);

    expect(SERVER_CALL.test('const r = await ncaEccFilingApi.list();')).toBe(true);
    expect(SERVER_CALL.test('const r = useApiList<Row>(() => x());')).toBe(true);
    expect(SERVER_CALL.test('await fetch(url);')).toBe(true);
    // a mention is not a call — the mistake F2-194 made twice, on an import and on a type name
    expect(SERVER_CALL.test("import { ncaEccFilingApi } from '../../lib/ncaEccFiling';"))
      .toBe(false);

    // the transport must be excluded as a hop, and only the transport
    expect(TRANSPORT.test('../../lib/api')).toBe(true);
    expect(TRANSPORT.test('./lib/api')).toBe(true);
    expect(TRANSPORT.test('../../lib/apiError')).toBe(false);
    expect(TRANSPORT.test('../../lib/boardPackAggregate')).toBe(false);
    expect(TRANSPORT.test('../../lib/ncaEccFiling')).toBe(false);
  });

  /**
   * The retired page asserted from the other side: a regulator-facing document may not name a
   * hard-coded commercial-registration number, because in a multi-tenant platform only the
   * tenant's own record can supply one.
   */
  it('no document-emitting page hard-codes a commercial-registration number', () => {
    for (const e of EMITTERS) {
      const code = stripComments(readFileSync(join(SRC, e.page), 'utf8'));
      expect(code, `${e.page} hard-codes a CR number into an exported document`)
        .not.toMatch(/\b101\d{7}\b/);
    }
  });
});
