import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, resolve, posix, sep } from 'node:path';

/**
 * F2-198 — a page can be fabricated end to end and satisfy every de-mocking gate we have.
 *
 * Each existing instrument is keyed on a MECHANISM, and a page that uses none of them is
 * outside all of them at once:
 *
 *   • `demoFallbackContract`'s ratchet counts `/mock/i` **imports** — keyed on FILENAME, so a
 *     corpus in `ropaConsent.ts` / `assets.ts` / `controlCrosswalk.ts` is invisible to it.
 *   • the same file's F2-169 hard zero ("fabricated data only substitutes for a fabricated
 *     parent") builds its population from `corpusNamesImportedBy`, which matches the module
 *     names `mock*`, `entityAnchors`, `policyLifecycle` — a filename allow-list again. The
 *     invariant is real; its POPULATION is a filename pattern.
 *   • F2-168's `demoRows()` / `isDemoId()` tagging only reaches corpora that opted in, so an
 *     untagged corpus can never raise the demo flag it is not wearing.
 *   • the `isDemo` disclosure rule triggers on `useApiList`/`useApiResource`. A page that
 *     calls nothing is not a consumer.
 *   • F2-195's `kpiProvenance` is keyed on a literal or a `|| N` fallback inside the `<Kpi>`
 *     tag. `value={String(stats.total)}` where `stats = aggregateRopa()` is neither: a
 *     CORRECT predicate over an INVENTED corpus passes it, and F2-196's wrong-predicate gate
 *     too, because that one assumes the rows are real.
 *
 * So this gate is keyed on the page instead: does the thing rendering the claim ever ask a
 * server anything? Measured 2026-08-12 over 310 routed components: **73 routed pages make no
 * server call**, 47 of them sidebar-reachable. Most are legitimate — a wizard that posts
 * elsewhere, a label map, an options list — so the population is narrowed by the rule this
 * lineage already ratified in F2-193: a catalogue may declare what a thing IS, never what is
 * happening to it right now. A `status`, a metric or a clock stamp in a static corpus can only
 * have been invented.
 *
 * That leaves **12 corpora on 11 pages, 7 sidebar-reachable**, three of them minting their
 * timestamps from `Date.now()` at module load — F2-194's fabrication with no tell.
 *
 * ⚠ This is a RATCHET, not an invariant, and deliberately so: eleven pages remain, each needing
 * its own slice (does the platform track assets? ECC domains? approval requests?). House rule 3's
 * warning — that a ceiling makes a tolerated site indistinguishable from a reviewed one — applies
 * once a sweep has landed. No sweep has landed here, so the honest instrument is one that stops
 * the population growing and prints what is left. F2-188's failure mode was manufacturing fluent
 * reasons for a population nobody had verified; this file states counts it measured and refuses
 * to narrate the members.
 */

const SRC = resolve(__dirname, '..');

/**
 * ⚠ ONE alternation, left-to-right. The order is load-bearing and this is the assertion that
 * proves it.
 *
 * Written the obvious way — block comments stripped first, then line comments — this deleted
 * **40,247 of App.tsx's 89,522 characters**, because line 122 is a `//` prose comment whose text
 * contains a literal `/*`: the block pattern ran from there to the next closing delimiter and
 * took 45% of the file with it, including the `lazy()` declarations for the very pages being
 * measured. The census then reported 24 pages instead of 74 and **zero** sidebar-reachable
 * instances instead of eight.
 *
 * A corrupted source yields a SMALLER population, which reads exactly like "those pages are
 * fine" — the quietest way for a scan to be wrong.
 */
export function stripComments(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');
}

const APP = readFileSync(join(SRC, 'App.tsx'), 'utf8');
const APP_CODE = stripComments(APP);
const SIDEBAR = stripComments(readFileSync(join(SRC, 'components/layout/Sidebar.tsx'), 'utf8'));

/** component name -> module path, from `lazy(() => import('…'))` and plain page imports. */
function routedComponents(): Map<string, { module: string; routes: string[] }> {
  const modules = new Map<string, string>();
  for (const m of APP_CODE.matchAll(
    /const\s+(\w+)\s*=\s*lazy\(\(\)\s*=>\s*import\('([^']+)'\)/g)) {
    modules.set(m[1], m[2]);
  }
  for (const m of APP_CODE.matchAll(/import\s+\{([^}]*)\}\s+from\s+'(\.\/pages\/[^']+)'/g)) {
    for (const raw of m[1].split(',')) {
      const n = raw.trim();
      if (n && !modules.has(n)) modules.set(n, m[2]);
    }
  }
  const out = new Map<string, { module: string; routes: string[] }>();
  for (const m of APP_CODE.matchAll(/<Route\s+path="([^"]+)"[^>]*element=\{<(\w+)/g)) {
    const mod = modules.get(m[2]);
    if (!mod) continue;
    const cur = out.get(m[2]) ?? { module: mod, routes: [] };
    cur.routes.push(m[1]);
    out.set(m[2], cur);
  }
  return out;
}

/**
 * Any request to a server: an api-client call, the shared hooks, or a bare fetch.
 *
 * ⚠ The optional `<…>` was missing until F2-199 and it is load-bearing. The house idiom is a
 * TYPED wrapper — `api.get<BoardPackAggregate>('/api/v1/reporting/board-pack.json')` — so a
 * generic argument sits between the method name and the paren and `\w+\s*\(` never matches it;
 * **118 files** use that form. The error direction is over-reporting (a page that does call a
 * server reads as one that never could), so it inflates a population rather than hiding one —
 * unlike F2-198's comment-stripper bug, which shrank it. Re-measured after the fix: the eleven
 * findings below are unchanged, because none of them uses the generic form.
 */
const SERVER_CALL =
  /\b\w*[Aa]pi\.\w+\s*(?:<[^()\n]{0,200}>)?\s*\(|\buseApi(?:List|Resource)\s*[<(]|\bfetch\s*\(/;

/**
 * Fields that assert live operational state rather than identity or definition. A page may
 * legitimately hard-code a vocabulary, a label map or an options list; what it may not do is
 * hard-code what is happening to those things right now.
 */
const STATE_FIELD = new RegExp(
  '\\b(status|state|phase|stage'
  + '|count|total|active|open|overdue|breached|pending|completed'
  + '|score|percent|pct|rate|health'
  + '|lastReview\\w*|lastRun\\w*|updatedAt|reviewedAt|dueAt|nextReview\\w*'
  + '|progress|utilisation|utilization)\\s*[?]?\\s*:', 'i');

const CLOCK_MINT = /Date\.now\(\)|new Date\(\)/;

/** Bracket-match the array literal that `export const NAME = [` opens. */
function arrayBody(src: string, name: string): string | null {
  const m = new RegExp(
    String.raw`export\s+const\s+${name}\s*(?::\s*[^=]{0,200}?)?=\s*\[`).exec(src);
  if (!m) return null;
  let depth = 0;
  for (let i = m.index + m[0].length - 1; i < src.length; i++) {
    const c = src[i];
    if (c === '[' || c === '{' || c === '(') depth++;
    else if (c === ']' || c === '}' || c === ')') {
      depth--;
      if (depth === 0) return src.slice(m.index + m[0].length, i);
    }
  }
  return null;
}

interface Finding {
  routes: string[]; page: string; corpus: string;
  sidebar: boolean; clockMinted: boolean;
}

function census(): { resolved: number; zeroCall: number; zeroCallSidebar: number; findings: Finding[] } {
  const routed = routedComponents();
  const findings: Finding[] = [];
  let zeroCall = 0, zeroCallSidebar = 0;

  for (const [, { module, routes }] of routed) {
    let file = join(SRC, module.replace(/^\.\//, '') + '.tsx');
    if (!existsSync(file)) file = join(SRC, module.replace(/^\.\//, '') + '.ts');
    if (!existsSync(file)) continue;
    const body = stripComments(readFileSync(file, 'utf8'));
    if (SERVER_CALL.test(body)) continue;

    // corpora this page imports: an upper-snake identifier from a non-api module
    const imported: { module: string; name: string }[] = [];
    for (const m of body.matchAll(/import\s+(?:type\s+)?\{([^}]*)\}\s+from\s+'([^']+)'/g)) {
      if (m[2].includes('/api')) continue;
      for (const raw of m[1].split(',')) {
        const n = raw.trim().split(' as ').pop()!.trim();
        if (/^[A-Z][A-Z0-9_]{2,}$/.test(n)) imported.push({ module: m[2], name: n });
      }
    }
    if (imported.length === 0) continue;

    const inSidebar = routes.some(r => SIDEBAR.includes(`'${r}'`) || SIDEBAR.includes(`"${r}"`));
    zeroCall++;
    if (inSidebar) zeroCallSidebar++;

    for (const { module: cm, name } of imported) {
      let cf = resolve(dirname(file), cm + '.ts');
      if (!existsSync(cf)) cf = resolve(dirname(file), cm + '.tsx');
      if (!existsSync(cf)) continue;
      const csrc = stripComments(readFileSync(cf, 'utf8'));
      const literal = arrayBody(csrc, name);
      if (literal === null) continue;              // a Record<> label map, not a row corpus
      if (!STATE_FIELD.test(literal)) continue;    // declares what the thing IS — configuration
      findings.push({
        routes, page: file.slice(SRC.length + 1).split(sep).join(posix.sep),
        corpus: `${cf.slice(SRC.length + 1).split(sep).join(posix.sep)}#${name}`,
        sidebar: inSidebar, clockMinted: CLOCK_MINT.test(csrc),
      });
    }
  }
  return { resolved: routed.size, zeroCall, zeroCallSidebar, findings };
}

const CENSUS = census();

function report(fs: Finding[]): string {
  return fs.map(f => `  ${f.sidebar ? 'SIDEBAR ' : 'url-only'} ${f.routes.join(',')}`
    + `${f.clockMinted ? '  [CLOCK-MINTED]' : ''}\n      ${f.corpus}`).join('\n');
}

describe('F2-198 — a routed page that asserts operational state must ask a server', () => {
  /**
   * Measured 2026-08-12. Decrease-only: each remaining member is a separate slice, and the number
   * may never be raised. `/ropa` took it from 14 to 12 (F2-198); `/nca-ecc-export` from 12 to 11
   * and the sidebar half from 7 to 6 (F2-199); `/approvals` from 11 to 10 and 6 to 5 (F2-201) —
   * and that one is the first member of this population REWIRED rather than retired, because the
   * server side was already complete and had no client at all.
   */
  const BASELINE_STATE_CORPORA = 10;
  const BASELINE_SIDEBAR_PAGES = 5;

  it('does not grow the number of state-asserting corpora on server-less routed pages', () => {
    expect(CENSUS.findings.length,
      'A routed page is rendering a status, a metric or a clock stamp from a hard-coded corpus '
      + 'while never calling a server. Wire it to its register, or retire it as F2-198 retired '
      + `/ropa:\n${report(CENSUS.findings)}`)
      .toBeLessThanOrEqual(BASELINE_STATE_CORPORA);
  });

  it('does not grow the sidebar-reachable half of that population', () => {
    const pages = new Set(CENSUS.findings.filter(f => f.sidebar).map(f => f.page));
    expect(pages.size,
      'A sidebar entry leads to a page whose figures are invented. Sidebar-reachable is the '
      + `severe half — a user does not have to know a URL to be told a false number:\n`
      + report(CENSUS.findings.filter(f => f.sidebar)))
      .toBeLessThanOrEqual(BASELINE_SIDEBAR_PAGES);
  });

  /**
   * Anti-vacuity. Both ratchets above pass trivially if the scan resolves nothing — which is
   * exactly what the broken comment stripper did, and it looked like good news. These numbers are
   * what a working scan returns.
   */
  it('the scan is still finding the codebase it is meant to measure', () => {
    expect(CENSUS.resolved, 'routed-component resolution collapsed — App.tsx parse is broken')
      .toBeGreaterThanOrEqual(250);
    expect(CENSUS.zeroCall, 'the server-call detector has stopped working')
      .toBeGreaterThanOrEqual(40);
    expect(CENSUS.findings.length, 'the state-field detector has stopped working')
      .toBeGreaterThanOrEqual(8);
  });
});

describe('F2-198 — the comment stripper cannot be reordered', () => {
  /**
   * The instrument-integrity leg, and the only assertion here that caught a real bug: my own.
   * Both directions are pinned, because each ordering breaks the other case.
   */
  it('a // comment containing /* does not swallow the rest of the file', () => {
    const src = [
      '// prose mentioning /* a block opener */ inline',
      'const KEEP_ME = 1;',
      '/* a real block */',
      'const ALSO_KEPT = 2;',
    ].join('\n');
    const out = stripComments(src);
    expect(out).toContain('KEEP_ME');
    expect(out).toContain('ALSO_KEPT');
  });

  it('a block comment containing // is removed whole, not truncated at the //', () => {
    const src = '/* see http://example.sa for why\n   this is here */\nconst KEEP = 1;';
    const out = stripComments(src);
    expect(out).toContain('KEEP');
    expect(out).not.toContain('this is here');
  });

  it('the real App.tsx survives stripping — the regression that produced this file', () => {
    // Block-comment-first left 49,275 of 89,522 chars and lost the lazy() declarations.
    expect(APP_CODE.length).toBeGreaterThan(APP.length * 0.75);
    expect(APP_CODE).toMatch(/const\s+PrivacyPage\s*=\s*lazy/);
    expect(routedComponents().size).toBeGreaterThanOrEqual(250);
  });

  it('a URL in code is not mistaken for a comment', () => {
    expect(stripComments("const u = 'https://x.sa/a';")).toContain('https://x.sa/a');
  });
});

describe('F2-198 — /ropa resolves to the register that reads the database', () => {
  const APP_RAW = APP;

  it('the fabricated page and both of its corpora are gone from the tree', () => {
    for (const gone of ['pages/ropa/RopaPage.tsx', 'lib/ropaConsent.ts', 'lib/ropaWorkflows.ts']) {
      expect(existsSync(join(SRC, gone)), `${gone} is back`).toBe(false);
    }
  });

  it('nothing imports the retired corpora', () => {
    const offenders: string[] = [];
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const full = join(dir, name);
        if (statSync(full).isDirectory()) {
          if (name === 'node_modules' || name === 'dist') continue;
          walk(full);
        } else if (/\.tsx?$/.test(name)) {
          const body = readFileSync(full, 'utf8');
          if (/from\s+'[^']*ropa(Consent|Workflows)'/.test(body)) {
            offenders.push(full.slice(SRC.length + 1));
          }
        }
      }
    };
    walk(SRC);
    expect(offenders).toEqual([]);
  });

  it('the route redirects rather than 404ing a bookmarked URL', () => {
    // F2-137: a bookmark must land somewhere true. Asserted on the Route element, not on the
    // bare word "Navigate" — which appears in this file's own prose and in other routes.
    expect(APP_RAW).toMatch(
      /<Route\s+path="\/ropa"\s+element=\{<Navigate\s+to="\/privacy\?tab=ropa"\s+replace\s*\/>\}/);
  });

  it('the sidebar entry points at the real tab, not the retired path', () => {
    expect(SIDEBAR).toMatch(/id:\s*'ropa',\s*to:\s*'\/privacy\?tab=ropa'/);
    // and no row anywhere still routes to the retired page
    expect(SIDEBAR).not.toMatch(/to:\s*'\/ropa'/);
  });

  /**
   * ⚠ Asserted as a CALL, not as a mention. F2-194 lost a mutation here twice: a
   * `toContain('getCcmDashboard')` passed on the import statement, and a prose check passed on a
   * type name. `privacyApi.ropa(` with the open paren is the call shape.
   */
  it('the surviving ROPA surface reads the server', () => {
    const page = readFileSync(join(SRC, 'pages/privacy/PrivacyPage.tsx'), 'utf8');
    expect(stripComments(page)).toMatch(/privacyApi\.ropa\s*\(/);
    expect(stripComments(page)).toMatch(/rowHref=\{r\s*=>\s*`\/privacy\/ropa\/\$\{r\.id\}`\}/);
  });

  it('the cross-border figure is counted over the same rows the activity count uses', () => {
    // The retired page invented this number. It is now derived from `ropa.data` — the SAME array
    // the "Processing Activities" tile counts — so the two tiles cannot disagree, which is
    // F2-197's rule that a tile's figure must equal the rows its own link opens.
    const code = stripComments(readFileSync(join(SRC, 'pages/privacy/PrivacyPage.tsx'), 'utf8'));
    expect(code).toMatch(/ropa\.data\.filter\(r\s*=>\s*r\.crossBorderTransfer\)\.length/);
    expect(code).toMatch(/value=\{ropa\.data\.length\}/);
  });

  it('the new label is bilingual, like everything a user reads', () => {
    for (const lang of ['en', 'ar']) {
      expect(readFileSync(join(SRC, `translations/${lang}.ts`), 'utf8'))
        .toMatch(/^\s+privacyKpiCrossBorder:\s+'/m);
    }
  });

  it('the 45 translation keys the retired page owned are gone from both languages', () => {
    for (const lang of ['en', 'ar']) {
      const body = readFileSync(join(SRC, `translations/${lang}.ts`), 'utf8');
      for (const dead of ['ropaKpiNeedsReview', 'ropaKpiReviewed', 'ropaConsentWithdrawRate',
                          'ropaLastReviewed', 'ropaSubProcessors']) {
        expect(body, `${lang}.ts still declares ${dead}`).not.toMatch(
          new RegExp(String.raw`^\s+${dead}:`, 'm'));
      }
      // The one that survives, because the sidebar row still needs a label.
      // `\s+` after the colon, not a single space: this file column-aligns its values, and the
      // first draft of this assertion failed on `ropaSidebar:             'ROPA',`.
      expect(body).toMatch(/^\s+ropaSidebar:\s+'/m);
    }
  });
});

describe('F2-199 — /nca-ecc-export resolves to the register that reads the database', () => {
  /**
   * `/nca-ecc-export` rendered an "NCA ECC-1 Self-Assessment Submission" — a document addressed
   * to the National Cybersecurity Authority, with a cover, a 30-control matrix, CISO + CEO
   * signature lines, a CSV download and a print-to-PDF path — and it made no server call at all.
   *
   * Two things made it worse than the dashboards this lineage has been retiring:
   *   • it EXPORTS. A fabricated dashboard misleads whoever is looking at it; a fabricated export
   *     produces a file that leaves the platform, gets signed, and is filed with a regulator.
   *   • the cover named `brandName` — "TSS GRC Platform", the software product — as the filing
   *     entity, beside a hard-coded CR number `1010299292`. In a multi-tenant platform that means
   *     every tenant's ECC return identified the vendor rather than the filer, identically.
   *
   * The real register is `nca_ecc.filings`: 4 filings in dev and prod, scored per DOMAIN 1-5 as
   * the ECC-2:2024 annual return actually requires, carrying peer review, CISO approval and an
   * NCA submission reference — and `NcaEccFilingsPage` already renders all of it.
   *
   * 🔑 The fabrication was MORE GRANULAR than the platform's own filing model, which is part of
   * why it looked more finished: the real return is five domain scores, and the page invented a
   * 30-control matrix underneath them. `responses_json` is NULL on all 4 filings in both
   * databases, so per-control responses have never been captured and the matrix could not have
   * been rebuilt honestly — inventing them is F2-196's defect.
   */
  it('the fabricated submission document and its corpus are gone from the tree', () => {
    for (const gone of ['pages/nca-ecc/NcaEccExportPage.tsx', 'lib/ncaEcc.ts',
                        'pages/nca-ecc/ncaEcc.css']) {
      expect(existsSync(join(SRC, gone)), `${gone} is back`).toBe(false);
    }
  });

  it('nothing imports the retired corpus', () => {
    const offenders: string[] = [];
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const full = join(dir, name);
        if (statSync(full).isDirectory()) {
          if (name === 'node_modules' || name === 'dist') continue;
          walk(full);
        } else if (/\.tsx?$/.test(name)) {
          // the real client is `lib/ncaEccFiling` — the negative lookahead keeps it out
          if (/from\s+'[^']*\/ncaEcc'|import\s+'[^']*ncaEcc\.css'/.test(
            readFileSync(full, 'utf8'))) {
            offenders.push(full.slice(SRC.length + 1));
          }
        }
      }
    };
    walk(SRC);
    expect(offenders).toEqual([]);
  });

  it('the route redirects rather than 404ing a bookmarked URL', () => {
    // F2-137. Asserted on the whole Route element, not the bare word "Navigate", which appears
    // in this file's prose and in a dozen other routes.
    expect(APP).toMatch(
      /<Route\s+path="\/nca-ecc-export"\s+element=\{<Navigate\s+to="\/nca-ecc\/filings"\s+replace\s*\/>\}/);
  });

  it('the sidebar entry points at the real register and no longer offers an export', () => {
    expect(SIDEBAR).toMatch(/id:\s*'nca-ecc',\s*to:\s*'\/nca-ecc\/filings'/);
    expect(SIDEBAR).not.toMatch(/to:\s*'\/nca-ecc-export'/);
    // the old label literally read "NCA ECC export" — naming a page that no longer exists
    expect(SIDEBAR).not.toMatch(/labelKey:\s*'ncaSidebar'/);
  });

  /**
   * ⚠ Asserted as a CALL, not a mention — F2-194 lost two mutations to `toContain('name')`
   * passing on an import statement and on a type name.
   */
  it('the surviving ECC surface reads the server', () => {
    const page = stripComments(readFileSync(join(SRC, 'pages/nca-ecc/NcaEccFilingsPage.tsx'), 'utf8'));
    expect(page).toMatch(/ncaEccFilingApi\.list\s*\(/);
    expect(page).toMatch(/ncaEccFilingApi\.transition\s*\(/);
    // and it renders the five real domain scores rather than a control matrix
    expect(page).toMatch(/NCA_DOMAINS\.map/);
  });

  it('the 30 translation keys the retired page owned are gone from both languages', () => {
    for (const lang of ['en', 'ar']) {
      const body = readFileSync(join(SRC, `translations/${lang}.ts`), 'utf8');
      for (const dead of ['ncaCoverCr', 'ncaCoverEntity', 'ncaKpiScore', 'ncaSignoffCeo',
                          'ncaTblEvidence', 'ncaSubmissionTemplate', 'ncaSidebar']) {
        expect(body, `${lang}.ts still declares ${dead}`).not.toMatch(
          new RegExp(String.raw`^\s+${dead}:`, 'm'));
      }
      // The one that survives: the sidebar row's label, now naming the real register.
      expect(body).toMatch(/^\s+ncaFilingsSidebar:\s+'/m);
    }
  });
});

describe('F2-201 — the approval inbox decides against the server, not React state', () => {
  /**
   * `/approvals` was the first member of this population to be REWIRED rather than retired, and
   * the reason is the inverse of F2-198 and F2-199: there was a complete, hardened server side
   * with no client at all.
   *
   * `lib/approvals.ts` was a 407-line approval ENGINE in the browser — 12 hard-coded requests
   * (`AP-001`…`AP-012`, clock-minted from `Date.now()` at module load), a 16-role registry naming
   * invented individuals as the organisation's CISO / DPO / CFO / CEO, and a parallel/sequential
   * routing machine. `ApprovalInboxPage` read that corpus and "decided" it with `setRequests`, so
   * Approve produced a success toast plus a second toast claiming downstream chain rules had
   * fired, and **nothing was persisted**: an approver believed they had discharged a governance
   * duty, and a reload put the request back to PENDING.
   *
   * Measured live and identical in shape in both databases: `approvals.requests` holds **22 rows
   * in dev (12 PENDING, 9 APPROVED, 1 DELEGATED)** and **21 in prod (11 PENDING)**, assigned to
   * real dev personas across 17 real `entity_type` values — none of it visible on this page.
   *
   * 🔑 The server side was not merely present, it was HARDENED first: SEC-IAM-002 takes the
   * deciding identity from the authenticated principal and never a body field, the service refuses
   * a decision from anyone but the current assignee, and `?myInbox=true` is a real personal queue.
   * The fabrication was not filling a gap — it was shadowing a finished control.
   */
  const PAGE = () => stripComments(
    readFileSync(join(SRC, 'pages/approvals/ApprovalInboxPage.tsx'), 'utf8'));
  const CLIENT = () => stripComments(readFileSync(join(SRC, 'lib/approvalsApi.ts'), 'utf8'));

  it('the fabricated engine is gone from the tree', () => {
    expect(existsSync(join(SRC, 'lib/approvals.ts')), 'lib/approvals.ts is back').toBe(false);
  });

  it('nothing imports the retired engine', () => {
    const offenders: string[] = [];
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const full = join(dir, name);
        if (statSync(full).isDirectory()) {
          if (name === 'node_modules' || name === 'dist') continue;
          walk(full);
        } else if (/\.tsx?$/.test(name)) {
          // the real client is `approvalsApi` — the `'` terminator keeps it out
          if (/from\s+'[^']*\/approvals'/.test(readFileSync(full, 'utf8'))) {
            offenders.push(full.slice(SRC.length + 1));
          }
        }
      }
    };
    walk(SRC);
    expect(offenders).toEqual([]);
  });

  /** Asserted as CALLS, not mentions — F2-194 lost two mutations to substring checks. */
  it('the page reads the real register and decides through the real endpoint', () => {
    const page = PAGE();
    expect(page).toMatch(/approvalsApi\.list\s*\(/);
    expect(page).toMatch(/approvalsApi\.myInbox\s*\(/);
    expect(page).toMatch(/approvalsApi\.transition\s*\(/);
  });

  it('the client targets the real controller path', () => {
    expect(CLIENT()).toMatch(/const BASE = '\/api\/approvals\/requests'/);
    expect(CLIENT()).toMatch(/\$\{BASE\}\/\$\{id\}\/transition/);
  });

  /**
   * The decisive behavioural leg. The old page's defect was not that the data was invented — it
   * was that a decision never left the browser. A rewire that kept a local status write would
   * look identical on screen and be the same defect.
   */
  it('no decision is written into local state — the row comes back from the server', () => {
    const page = PAGE();
    expect(page, 'a decision must not compute the new status locally')
      .not.toMatch(/setRows\([^)]*status:\s*['"]APPROVED['"]/);
    // the only setRows in the decision path replaces by id from the server's response
    expect(page).toMatch(/setRows\(prev => prev\.map\(x => x\.id === updated\.id \? updated : x\)\)/);
  });

  /**
   * The client may not re-create the machine the server does not run. F2-199's lesson: a
   * fabrication that is MORE granular than the real model is what makes it look finished.
   */
  it('the client models no approver chain, mode or role registry', () => {
    const client = CLIENT();
    for (const gone of ['PARALLEL_ANY', 'PARALLEL_ALL', 'SEQUENTIAL',
                        'APPROVER_REGISTRY', 'approverRoles']) {
      expect(client, `approvalsApi re-introduced ${gone}, which the server has no column for`)
        .not.toContain(gone);
    }
  });

  it('the transition body carries no decidedBy — the server takes it from the principal', () => {
    // SEC-IAM-002. A `decidedBy` field would be silently ignored, which is the F2-184 decoy shape.
    expect(CLIENT()).not.toMatch(/decidedBy\??:\s*string;[\s\S]{0,400}ApprovalTransition/);
    const iface = CLIENT().slice(CLIENT().indexOf('interface ApprovalTransition'));
    expect(iface.slice(0, iface.indexOf('}'))).not.toContain('decidedBy');
  });

  it('the allowed-transition table mirrors the server, and terminal is derived from it', () => {
    const client = CLIENT();
    expect(client).toMatch(/PENDING:\s*\['APPROVED', 'REJECTED', 'DELEGATED', 'EXPIRED'\]/);
    expect(client).toMatch(/DELEGATED:\s*\['PENDING'\]/);
    // derived, never typed twice (F2-196)
    expect(client).toMatch(/APPROVAL_TERMINAL[\s\S]{0,200}filter\(s => APPROVAL_ALLOWED\[s\]\.length === 0\)/);
  });

  it('an absent SLA deadline reads as unmeasured, not as on-track', () => {
    // `sla_deadline` is NULL on 10 of 22 rows in dev, so this is a real state. F2-197's rule:
    // a missing figure must not render as health.
    expect(CLIENT()).toMatch(/if \(!r\.slaDeadline\) return 'NO_DEADLINE'/);
    expect(PAGE()).toMatch(/tone="neutral"/);
  });

  it('the queue never falls back to a demo corpus on an empty or failed load', () => {
    const page = PAGE();
    expect(page, 'useApiList prefers a demo corpus when the server returns zero rows')
      .not.toMatch(/useApiList/);
    expect(page).toMatch(/setRows\(\[\]\)/);   // the failure path shows nothing, not something
  });

  it('the new labels are bilingual, like everything a user reads', () => {
    for (const lang of ['en', 'ar']) {
      const body = readFileSync(join(SRC, `translations/${lang}.ts`), 'utf8');
      for (const k of ['approvalsKpiNoDeadline', 'approvalAssignedTo', 'approvalOverdueBy']) {
        expect(body, `${lang}.ts is missing ${k}`).toMatch(
          new RegExp(String.raw`^\s+${k}:\s+'`, 'm'));
      }
      // and the keys the fabricated page owned are gone
      for (const dead of ['approvalToastChainFired', 'approvalDelegateNoCover',
                          'approvalProgressLabel']) {
        expect(body, `${lang}.ts still declares ${dead}`).not.toMatch(
          new RegExp(String.raw`^\s+${dead}:`, 'm'));
      }
    }
  });
});
