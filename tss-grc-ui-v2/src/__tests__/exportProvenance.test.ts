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

/** Building a file the browser will save, or driving the print dialog. */
const EMITS_DOCUMENT = /\.download\s*=|createObjectURL\s*\(|new\s+Blob\s*\(|window\.print\s*\(/;

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

    let backedVia: string | null = SERVER_CALL.test(code) ? 'itself' : null;
    if (backedVia === null) {
      for (const m of code.matchAll(IMPORT_SPEC)) {
        if (!m[1].startsWith('.') || TRANSPORT.test(m[1])) continue;
        const target = resolveModule(resolve(dirname(found.path), m[1]));
        if (target && SERVER_CALL.test(stripComments(target.body))) {
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
 * Deliberately EMPTY — see the header. A member would have to be read and explained here, and
 * the reverse-direction legs below would then keep the entry honest.
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
const NO_SERVER_DATA_BY_DESIGN: Record<string, string> = {};

function report(es: Emitter[]): string {
  return es.map(e => `  ${e.page}\n      routes: ${e.routes.join(', ')}`).join('\n');
}

describe('F2-199 — an exported or printed document must derive from server data', () => {
  it('no routed page emits a document without server data in scope', () => {
    const unexplained = UNBACKED.filter(e => !(e.page in NO_SERVER_DATA_BY_DESIGN));
    expect(unexplained.length,
      'This page builds a file the user will save, or prints one, and nothing in its scope ever '
      + 'asks a server. An exported document leaves the platform: it gets signed, attached and '
      + 'filed, and nothing downstream can tell that the figures were invented. Wire it to its '
      + `register, retire it as F2-199 retired /nca-ecc-export, or explain it here:\n${report(unexplained)}`)
      .toBe(0);
  });

  it('the exemption map is empty, so a tolerated page cannot pass for a reviewed one', () => {
    // House rule 3, applied to this gate: the sweep has landed, so the honest instrument is a
    // hard zero. If a member is ever added, delete this assertion in the same commit and say why.
    expect(Object.keys(NO_SERVER_DATA_BY_DESIGN)).toEqual([]);
  });

  it('every exemption names a page that is really in the population', () => {
    // The reverse direction — and the leg that caught this file's own first-draft defect, where
    // both entries named culled, unrouted pages that no route reaches.
    const inPopulation = new Set(UNBACKED.map(e => e.page));
    for (const page of Object.keys(NO_SERVER_DATA_BY_DESIGN)) {
      expect(inPopulation.has(page),
        `${page} is not an unbacked routed emitter — delete its exemption, it explains nothing`)
        .toBe(true);
    }
  });

  it('an exemption reason must be substantial, not a label', () => {
    // F2-188: a named-map gate proves a reason EXISTS; nothing proves it is TRUE. The least it can
    // do is refuse a bare label — which is what six of my first-draft entries were in F2-187.
    for (const [page, reason] of Object.entries(NO_SERVER_DATA_BY_DESIGN)) {
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
    expect(EMITTERS.filter(e => e.backedVia === 'itself').length,
      'the direct server-call detector has stopped working')
      .toBeGreaterThanOrEqual(6);
    expect(EMITTERS.filter(e => e.backedVia !== null && e.backedVia !== 'itself').length,
      'the one-hop resolver has stopped working — no page resolves through an imported module')
      .toBeGreaterThanOrEqual(1);
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
