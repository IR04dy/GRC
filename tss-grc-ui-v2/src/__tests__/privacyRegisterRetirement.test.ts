import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * F2-207 — the three PDPL statutory registers that lived in `localStorage` stay retired.
 *
 * `lib/privacyLifecycle.ts` held `SEED_ROPA` (Art. 31 processing activities), `SEED_DPIA`
 * (Art. 25 impact assessments) and `SEED_CBT` (Art. 29 cross-border transfers) under
 * `grc.privacy.ropa.v1` / `.dpia.v1` / `.cbt.v1`, and `PrivacyLifecyclePage` rendered and
 * MUTATED all three: approving a ROPA, signing off a DPIA and approving a transfer out of the
 * Kingdom each wrote to the operator's own browser and nowhere else.
 *
 * All three real registers are populated and already had a complete server-backed surface, so
 * this was a RETIRE (ADR-017 §D-5), not a rewire — the answer F2-42, F2-198 and F2-199 each
 * reached. A second real client for one statutory object is F2-196's own defect.
 *
 * WHY A GATE AND NOT JUST A DELETION. F2-202's lesson: deleting a decoy removes a
 * fingerprint. The three entries that recorded these corpora in `lib/lifecycleStores.ts` are
 * removed too, because that registry's population is DERIVED from the modules' own
 * `grc.*.v1` literals (F2-204) and a retired module has no corpus to declare. So the trace
 * has to live somewhere a scan can check it, which is here.
 *
 * ⚠ Every scan below strips comments first. This codebase explains a deletion by naming what
 * was deleted, so a raw-text scan reads the epitaph as the corpse — F2-193, F2-196, F2-202 and
 * F2-205 all recorded that, and it bit this slice too: the page's own header comment names the
 * four retired KPI keys, and the dead-translation-key detector reported them as still in use
 * until it stripped comments. Note the direction: comment-blind OVER-reports usage here, so it
 * fails loudly rather than shrinking a population (the opposite of F2-198's stripper bug).
 * ─────────────────────────────────────────────────────────────────────────────
 */

const SRC = join(__dirname, '..');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');

/** F2-198's alternation: line comments FIRST, in one left-to-right pass. */
const stripComments = (s: string) => s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');

const PAGE = 'pages/privacy-lifecycle/PrivacyLifecyclePage.tsx';
const pageCode = () => stripComments(read(PAGE));

/** The three localStorage keys the retired corpora used. */
const RETIRED_KEYS = ['grc.privacy.ropa.v1', 'grc.privacy.dpia.v1', 'grc.privacy.cbt.v1'];

/**
 * The five invented officers the retired handlers passed as the AUTHOR of statutory decisions.
 *
 * ⚠ This is the sharpest reason the tabs could not simply be wired. `dpoSignedBy` and
 * `approvedBy` are persisted fields on the real registers, so pointing the old handlers at the
 * real endpoints would have written a nonexistent Data Protection Officer into the database as
 * having signed a PDPL Article 25 assessment. F2-204 caught the identical trap one commit
 * before shipping (an invented CRO plus a literal rejection reason, which the server persists).
 */
const INVENTED_OFFICERS = [
  'Layla Al-Ghamdi',
  'Salman Al-Dossari',
  'Hessa Al-Rasheed',
  'Khalid Al-Falih',
  'DPO + CRO',
];

/**
 * ⚠⚠ THE PRIVACY SURFACE — and why the two legs below are scoped to it rather than app-wide.
 *
 * My first draft banned the five officers and the reference-minter shape across the whole tree.
 * Both fired on correct, pre-existing code, and measuring what they had found produced two
 * populations far larger than this slice:
 *
 *   • **65 distinct invented person-names across 39 product files.** The five names on the
 *     retired PDPL tabs are not this page's own invention — they are a SHARED CAST, most
 *     heavily in `lib/userDirectory.ts` (18) and `lib/crisis.ts` (16). ⚠ Most of those files
 *     are NOT named `mock*`, so the population sits largely outside the de-mocking boundary
 *     F2-194 measured — the same blind spot, one level up.
 *   • **14 client-side business-reference minters in 12 files**, entropy from `Math.random`:
 *     `NCA-ACK-…` and `SAMA-CTI-…` (regulator-facing acknowledgements), `WP-…` (IIA working
 *     papers), `CAPA-…`, `ACC-…` (risk acceptances), `INV-…`, `IR-…`, `SOC-…`, `KRI-BR-…`.
 *     F2-172(d)'s rule — only the server may issue a business reference — has been applied
 *     one instance at a time (F2-202's DSR minter, F2-203's finding refs) without anyone
 *     measuring the frontend population.
 *
 * Both are filed. Neither is fixable by a retirement slice, and a gate that fails on code this
 * slice did not touch is a gate nobody can keep green. 🔑 So these legs assert a HARD ZERO over
 * the surface whose claim they are — F2-192's rule: scope a scan to the shape of its claim, or
 * it invents findings.
 */
const PRIVACY_SURFACE = [PAGE, 'lib/privacyLcApi.ts', 'lib/pdplExtensions.ts'];

/** Every product source file (never the tests — a test may legitimately name what it bans). */
function productFiles(): string[] {
  const out: string[] = [];
  const walk = (rel: string) => {
    for (const e of readdirSync(join(SRC, rel), { withFileTypes: true })) {
      const child = rel ? `${rel}/${e.name}` : e.name;
      if (e.isDirectory()) {
        if (e.name === '__tests__' || e.name === 'node_modules') continue;
        walk(child);
      } else if (/\.tsx?$/.test(e.name) && !/\.test\.tsx?$/.test(e.name)) {
        out.push(child);
      }
    }
  };
  walk('');
  return out;
}

describe('F2-207 — the retired PDPL registers do not come back', () => {
  it('lib/privacyLifecycle.ts does not exist', () => {
    // The whole module was the fabrication plus the helpers serving it: three seed corpora,
    // three save/load pairs, `createDpia`'s Math.random() reference minter,
    // `upgradeCbtSafeguard`'s invented residual-risk arithmetic, `ADEQUACY_BY_COUNTRY` (an
    // SDAIA regulatory determination presented as platform data) and an aggregate over all of
    // it. Nothing in it had a consumer that survived the retirement.
    expect(existsSync(join(SRC, 'lib/privacyLifecycle.ts')),
      'lib/privacyLifecycle.ts is retired — a ROPA/DPIA/cross-border client belongs on the '
      + 'real surfaces (/privacy?tab=ropa, /privacy?tab=dpia, /data-lifecycle?tab=cbt)')
      .toBe(false);
  });

  it('no product file imports it or names its exports', () => {
    // Enforced across the tree rather than on the page alone, because the module also had a
    // second consumer: `LifecycleOverviewPage` imported `subscribePrivacy` purely as a
    // re-render trigger for corpora it never read.
    const offenders: string[] = [];
    for (const f of productFiles()) {
      const src = stripComments(read(f));
      if (/privacyLifecycle/.test(src)) offenders.push(f);
    }
    expect(offenders, 'these files still reference the retired module').toEqual([]);
  });

  it('no product file reads or writes the three retired localStorage keys', () => {
    const offenders: string[] = [];
    for (const f of productFiles()) {
      const src = stripComments(read(f));
      for (const k of RETIRED_KEYS) if (src.includes(k)) offenders.push(`${f} -> ${k}`);
    }
    expect(offenders, 'a statutory register must not be kept in the browser').toEqual([]);
  });

  it('the privacy surface names no invented officer', () => {
    // ⚠⚠ The load-bearing leg. A name here is not cosmetic: `dpoSignedBy` / `approvedBy` /
    // `rejectedReason` are persisted columns on the real registers, so pointing the retired
    // handlers at the real endpoints would have recorded a person who does not exist as having
    // signed a PDPL Article 25 assessment. F2-204 caught the identical trap one commit before
    // shipping. See PRIVACY_SURFACE above for why this is scoped and what the wider population is.
    const offenders: string[] = [];
    for (const f of PRIVACY_SURFACE) {
      const src = stripComments(read(f));
      for (const who of INVENTED_OFFICERS) if (src.includes(who)) offenders.push(`${f} -> ${who}`);
    }
    expect(offenders,
      'an approver, a DPO sign-off and a rejection reason come from the acting officer or not '
      + 'at all — never from a browser constant').toEqual([]);
  });

  it('the privacy surface mints no statutory register reference', () => {
    // F2-172(d)'s frontend half: only the server may issue a business reference. `createDpia`
    // minted `DPIA-<year>-<4 base-36 chars of Math.random()>` for an Article 25 assessment.
    //
    // ⚠ Banned as the SHAPE THAT WAS THE DEFECT — entropy inside a reference template — not as
    // the prefix. A first draft banned `DPIA-${` outright and fired on `searchIndex.ts`, where
    // `ref: \`DPIA-${d.id}\`` is a display LABEL over a real server id. Whether a value is
    // stored or rendered is a dataflow question a regex cannot answer (F2-182), so the regex
    // must ask about the entropy instead: a label built from a server id has none.
    const MINTER = /`[A-Z]{2,6}-[^`]*\$\{[^}]*Math\.random[^}]*\}[^`]*`/;
    const offenders = PRIVACY_SURFACE.filter(f => MINTER.test(stripComments(read(f))));
    expect(offenders, 'the server mints statutory register references').toEqual([]);
  });

  it('no product file carries an SDAIA country adequacy table', () => {
    // `ADEQUACY_BY_COUNTRY` classified Iran and Syria as PROHIBITED and the US, Singapore,
    // India, Canada and Australia as NOT_ADEQUATE, under a comment calling itself
    // "illustrative — actual list maintained by SDAIA". A regulatory determination shipped as
    // a browser constant is a claim with no source, and the real register's
    // `destination_country` is ISO alpha-3 anyway, so those country names could never match it.
    const offenders = productFiles().filter(f => /ADEQUACY_BY_COUNTRY/.test(stripComments(read(f))));
    expect(offenders, 'an adequacy decision is the regulator\'s, not a constant\'s').toEqual([]);
  });
});

describe('F2-207 — the page keeps only the two DSR surfaces, and points at the rest', () => {
  it('declares exactly the two tabs it can serve', () => {
    const code = pageCode();
    const m = code.match(/const PRC_TABS:\s*readonly PrcTab\[\]\s*=\s*\[([^\]]*)\]/);
    expect(m, 'PRC_TABS must be a declared literal so an inbound ?tab= can be validated')
      .not.toBeNull();
    const tabs = [...(m![1].matchAll(/'([a-z]+)'/g))].map(x => x[1]);
    // Exact, in both directions: adding a tab back means declaring it here, and a tab declared
    // but not rendered would be selectable and blank.
    expect(tabs).toEqual(['pipeline', 'dsr']);
    // And the union type agrees with the runtime list — a member in one and not the other is
    // either a selectable blank tab or a tab the validator rejects.
    const union = code.match(/type PrcTab\s*=\s*([^;]+);/);
    expect(union, 'type PrcTab not found').not.toBeNull();
    expect([...union![1].matchAll(/'([a-z]+)'/g)].map(x => x[1])).toEqual(['pipeline', 'dsr']);
    // The comparison shape is what would actually reintroduce a retired tab's content.
    for (const gone of ['ropa', 'dpia', 'cbt']) {
      expect(code, `tab === '${gone}' means a retired register is rendered here again`)
        .not.toMatch(new RegExp(`tab\\s*===\\s*'${gone}'`));
    }
  });

  it('renders a link to each of the three real registers', () => {
    // A retirement must still leave a reader somewhere true — F2-137's rule for routes, applied
    // to a tab. Asserted as the full `to=` target so repointing one at a page that does not
    // serve that register fails here.
    const code = pageCode();
    for (const to of ['/privacy?tab=ropa', '/privacy?tab=dpia', '/data-lifecycle?tab=cbt']) {
      expect(code, `the retired ${to} register must still be reachable from this page`)
        .toContain(to);
    }
  });

  it('the pointer card states no count for any of the three', () => {
    // ⚠ The trap this leg exists for. This page does not fetch those three registers, so any
    // figure beside one of these links would have to be invented — F2-198's "correct predicate
    // over an invented corpus", the shape that passes F2-195's kpiProvenance gate because the
    // value comes from a function rather than a literal. The pointer card is deliberately
    // count-free; the linked pages each show their own real figures.
    const code = pageCode();
    const start = code.indexOf('function PdplRegisterLinks');
    expect(start, 'PdplRegisterLinks not found — this page must still point somewhere true')
      .toBeGreaterThan(-1);
    // The component's own closing brace: the first line that is exactly `}` after the start,
    // since everything inside the function body is indented.
    const end = code.indexOf('\n}', start);
    expect(end, 'could not find the end of PdplRegisterLinks').toBeGreaterThan(start);
    const body = code.slice(start, end);
    for (const banned of ['.length', 'aggregate', 'filter(', 'useState', 'api.', 'Kpi']) {
      expect(body, `PdplRegisterLinks must not compute or fetch anything — found ${banned}`)
        .not.toContain(banned);
    }
  });

  /*
   * ⚠⚠ THE PAGE'S OWN HEADER STILL CLAIMED THE THREE RETIRED REGISTERS, and only the browser
   * found it — every gate above was green first. `prcEyebrow` read
   * "Privacy · PDPL Article 22 / 25 / 29 / 4" and `prcPageSub` described "ROPA Article 22
   * register, DPIA Article 25 high-risk assessment, Article 29 cross-border transfer adequacy"
   * on a page that now serves none of them.
   *
   * 🔑 F2-205's lesson, second instance: a claim about what a surface DOES can live anywhere a
   * user reads text, and a gate scoped to code cannot see the place written specifically to be
   * read. Retiring a tab means retiring the sentence that advertised it.
   *
   * ⚠ It also exposed a wrong citation. Four places said ROPA was PDPL "Article 22" — the three
   * strings here plus a comment — while every other citation in the codebase says Article 31,
   * including `lib/moduleAnchors.ts`, the regulator-citation catalogue W2-1a-i deliberately
   * keeps, whose row reads `PDPL / Article 31 / "Records of processing activities."` So the
   * correction follows the codebase's settled convention rather than adjudicating Saudi law.
   *
   * Scoped to the `prc*` keys — this page's own — because other modules legitimately cite their
   * own articles (F2-205's rule for exactly this shape of leg).
   */
  it('the page header advertises only the register it actually serves', () => {
    const HEADER_KEYS = ['prcEyebrow', 'prcSub', 'prcPageSub'];
    for (const lang of ['en', 'ar']) {
      const dict = read(`translations/${lang}.ts`);
      for (const key of HEADER_KEYS) {
        const m = dict.match(new RegExp(`^\\s+${key}:\\s*'([^']*)'`, 'm'));
        expect(m, `${lang}.ts declares no ${key}`).not.toBeNull();
        const text = m![1];
        // The retired registers must not be advertised as this page's content.
        for (const claim of ['ROPA', 'DPIA', 'cross-border', 'النقل العابر', 'سجل المعالجة']) {
          expect(text, `${lang}.${key} still advertises "${claim}", which this page no longer `
            + 'serves — the three registers are on their own screens')
            .not.toContain(claim);
        }
        // And the citation the whole codebase agrees on, not the outlier this page carried.
        for (const wrong of ['Article 22', 'المادة 22']) {
          expect(text, `${lang}.${key} cites PDPL ${wrong} for the processing-activity register; `
            + 'moduleAnchors.ts and every other citation in the tree say Article 31')
            .not.toContain(wrong);
        }
      }
    }
  });

  it('every KPI tile on the page counts the DSR register the server returned', () => {
    // The four retired tiles counted `aggregatePrivacy()` over the three browser corpora. What
    // survives must read `dsrStats`, which is `aggregateDsr(rows from /api/privacy-lc/dsr-cases)`.
    const code = pageCode();
    const tiles = [...code.matchAll(/<Kpi[\s\S]*?\/>/g)].map(m => m[0]);
    expect(tiles.length, 'no Kpi tiles found — the scan has rotted').toBeGreaterThanOrEqual(3);
    for (const tile of tiles) {
      expect(tile, `a KPI tile on this page must derive from dsrStats: ${tile.slice(0, 90)}`)
        .toMatch(/dsrStats/);
    }
    // And the fabricated aggregate is gone entirely.
    expect(code).not.toContain('aggregatePrivacy');
  });
});
