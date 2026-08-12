import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

/**
 * F2-197 — a process tile may not assert a count nobody computed.
 *
 * <p>Sibling of `kpiProvenance.test.ts`, aimed at the surface that gate cannot see.
 * F2-195 refused fabricated values inside `<Kpi>` tags; the same fabrication was
 * sitting one component along, in `ProcessLifecycleBar`'s tiles, where the numbers
 * came from a shared catalogue rather than from the JSX — so nothing keyed on the
 * tag could find them. 🔑 A provenance gate sees the shape it was written for.
 *
 * <p>Three things are held here:
 *   1. the catalogue declares NO counts (a hard zero, no allow-list);
 *   2. every count a page passes is an expression, not a literal;
 *   3. the three render surfaces route their colour through `HEALTH_STRIPE` /
 *      `lifecycleHealth` rather than re-deriving `overdue > 0 ? … : success`, which
 *      is the expression that would quietly turn 56 unmeasured tiles green.
 *
 * <p>⚠ The corpus assertions below pass both when the app is clean AND when a regex
 * has rotted, so `theDetectorsActuallyDetect` runs every one of them against
 * synthetic sources with the defect deliberately reintroduced. Without that leg a
 * green run says nothing — F2-195's lesson, applied to its own successor.
 */

const SRC = resolve(__dirname, '..');
const CATALOGUE = join(SRC, 'lib', 'moduleLifecycles.ts');
const BAR = join(SRC, 'components', 'workflows', 'ProcessLifecycleBar.tsx');
const MODAL = join(SRC, 'components', 'workflows', 'ProcessDetailModal.tsx');

const read = (p: string) => readFileSync(p, 'utf8');

/**
 * Strip `//` and block comments. Load-bearing, and the reason is recorded twice in
 * this programme: F2-193's detector read a *quotation* of the sentence it replaced
 * as an assertion of it, and F2-196's page scan fired on its own correction comment
 * three methods from the helper written for that trap. Every comment added by this
 * slice quotes the fabricated code it removed.
 */
function stripComments(src: string): string {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1');
}

function tsFilesUnder(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) { out.push(...tsFilesUnder(full)); continue; }
    if (/\.(ts|tsx)$/.test(entry) && !/\.test\.tsx?$/.test(entry)) out.push(full);
  }
  return out;
}

/** Every `activeCount:`/`overdueCount:` assignment whose value is a bare integer. */
function hardCodedCounts(src: string): string[] {
  return [...stripComments(src).matchAll(/\b(activeCount|overdueCount)\s*:\s*(-?\d+)\b/g)]
    .map(m => `${m[1]}: ${m[2]}`);
}

/** Every `activeCount:`/`overdueCount:` assignment of any kind. */
function anyCounts(src: string): string[] {
  return [...stripComments(src).matchAll(/\b(activeCount|overdueCount)\s*:\s*([^,\n}]+)/g)]
    .map(m => `${m[1]}: ${m[2].trim()}`);
}

describe('the lifecycle catalogue declares no counts', () => {
  it('holds zero activeCount/overdueCount declarations of any kind', () => {
    // Measured 2026-08-12 before the fix: 56 `activeCount` + 56 `overdueCount`
    // literals, summing to 130 "active" items and 29 overdue ones. A hard zero and
    // not a ratchet, because the population is meant to be empty: a tolerated count
    // in a shared catalogue is indistinguishable from a reviewed one.
    expect(anyCounts(read(CATALOGUE))).toEqual([]);
  });

  it('still declares the things a catalogue legitimately owns', () => {
    // Negative control: the sweep must have removed the counts and nothing else. If
    // this file were emptied the assertion above would also pass.
    const cat = read(CATALOGUE);
    expect((cat.match(/^\s{4}name:/gm) ?? []).length).toBe(56);
    expect((cat.match(/export const \w+_LIFECYCLES/g) ?? []).length).toBe(11);
    expect(cat).toContain('reference:');
    expect(cat).toContain('stages:');
    expect(cat).toContain('steward:');
  });
});

describe('no page hard-codes a process count', () => {
  it('finds no integer literal assigned to either count anywhere in src/', () => {
    const offenders = tsFilesUnder(SRC)
      .map(f => ({ f, hits: hardCodedCounts(read(f)) }))
      .filter(x => x.hits.length > 0);
    expect(offenders.map(o => `${o.f}: ${o.hits.join(', ')}`)).toEqual([]);
  });

  it('passes counts on the pages that have a real figure, and only as expressions', () => {
    // Measured as shipped: 31 count expressions on 10 pages, covering 24 of the 56
    // tiles. Decrease-only — wiring another page raises it and needs no edit here,
    // but the pages may not silently fall back to bare catalogue spreads. The number
    // is the count of what THIS assertion measures (expressions, not tiles): a floor
    // borrowed from a different question is worse than none, because it looks
    // measured (F2-195 set one to 16 that way).
    const passed = tsFilesUnder(join(SRC, 'pages'))
      .flatMap(f => anyCounts(read(f)));
    expect(passed.length).toBeGreaterThanOrEqual(31);
    for (const p of passed) {
      // Anything that is not a bare number: a call, a member access, an identifier.
      expect(p).toMatch(/[A-Za-z_$][\w$]*/);
      expect(p).not.toMatch(/:\s*-?\d+$/);
    }
  });
});

describe('every render surface asks the shared health predicate', () => {
  const bar = stripComments(read(BAR));

  it('derives all four stripe colours from one table', () => {
    expect(bar).toMatch(/HEALTH_STRIPE\s*:\s*Record<LifecycleHealth, string>/);
    expect(bar).toMatch(/unmeasured:\s*'var\(--border\)'/);
    // Both surfaces read the table; neither re-derives the ladder.
    expect((bar.match(/HEALTH_STRIPE\[health\]/g) ?? []).length).toBe(2);
  });

  it('holds no re-derived "otherwise success" stripe ladder', () => {
    // The exact expression the fix removed, in either surface, in any spacing. This
    // is the mutation that turns 56 unmeasured tiles into a green all-clear.
    expect(bar).not.toMatch(/overdue\s*>\s*0\s*\?[\s\S]{0,80}?var\(--success\)/);
    expect(bar).not.toMatch(/activeCount\s*>\s*0\s*\?[\s\S]{0,60}?var\(--success\)/);
  });

  it('guards every count read with a typeof check, never a truthiness check', () => {
    // `!lc.activeCount` on a possibly-undefined field collapses "not measured" into
    // "zero" — the Kpi `isNoData` rule (F2-195) applied to this component.
    //
    // The ban is UNIFORM, and deliberately wider than the defect: at the compact
    // chip, `!!active` and `typeof active === 'number' && active > 0` happen to agree
    // for every value, so that one site is behaviourally equivalent. The point is
    // that a reader cannot tell which sites are the safe ones, and the two that are
    // not — `lifecycleHealth` and the tile's counter — are the whole finding.
    //
    // ⚠ The mutation harness caught this list being too narrow: the component aliases
    // `lc.activeCount` to a local `active`, so a rule keyed on the FIELD name could
    // not see `!!active` and that mutation SURVIVED. Both spellings are covered now.
    for (const field of ['activeCount', 'overdueCount', 'active', 'overdue']) {
      const truthy = new RegExp(`(?<![\\w.])!\\s*(lc\\.)?${field}\\b`);
      expect(bar).not.toMatch(truthy);
    }
    expect(bar).toMatch(/typeof active === 'number'/);
    expect(bar).toMatch(/typeof overdue === 'number'/);
  });

  it('ranks by health before volume, so an unmeasured tile cannot be promoted', () => {
    expect(bar).toMatch(/HEALTH_RANK\[lifecycleHealth\(a\)\]\s*-\s*HEALTH_RANK\[lifecycleHealth\(b\)\]/);
    // …and the promotion itself is conditional, not `ranked[0]`.
    expect(bar).toMatch(/const promote\s*=[\s\S]{0,120}?ACTIONABLE\.includes\(lifecycleHealth\(ranked\[0\]\)\)/);
    expect(bar).toMatch(/\{primaryLc\s*&&\s*<ProcessChip/);
  });

  it('gives the detail modal an em-dash path on both counters', () => {
    const modal = stripComments(read(MODAL));
    // The overdue counter is the sharp one: it fell to `success` + CheckCircle2
    // whenever the number was not > 0, so an absent count asserted "nothing is late".
    expect(modal).not.toMatch(/tone=\{lc\.overdueCount > 0 \? 'danger' : 'success'\}/);
    expect(modal).toMatch(/typeof lc\.overdueCount !== 'number' \? 'neutral'/);
    expect((modal.match(/emptyHint=\{t\('processCounterNotComputed'\)\}/g) ?? []).length).toBe(2);
    expect(modal).toMatch(/typeof value === 'number'/);
  });

  it('translates the disclosure in both languages', () => {
    for (const lang of ['en', 'ar']) {
      expect(read(join(SRC, 'translations', `${lang}.ts`))).toContain('processCounterNotComputed:');
    }
    // The bar's own inline hint is bilingual too — it is not routed through t()
    // because the component takes `lang` as a prop rather than using the hook.
    expect(bar).toMatch(/lang === 'ar'[\s\S]{0,120}?'This process has no counter yet/);
  });
});

describe('the detectors actually detect', () => {
  it('fires on each defect this slice removed, reintroduced synthetically', () => {
    // ⚠ Without this leg every assertion above is also satisfied by a rotted regex.
    // Each case is the code as it really was before the fix.
    expect(hardCodedCounts('  activeCount: 4, overdueCount: 1,\n')).toEqual(
      ['activeCount: 4', 'overdueCount: 1']);
    expect(hardCodedCounts('activeCount: openCount, overdueCount: x.length')).toEqual([]);
    expect(anyCounts('activeCount: openCount,')).toEqual(['activeCount: openCount']);

    const oldStripe = `const stripe =
      overdue > 0 ? 'var(--danger)' :
      active  > 0 ? 'var(--accent-700)' :
      'var(--success)';`;
    expect(oldStripe).toMatch(/overdue\s*>\s*0\s*\?[\s\S]{0,80}?var\(--success\)/);

    const oldModalTone = "tone={lc.overdueCount > 0 ? 'danger' : 'success'}";
    expect(oldModalTone).toMatch(/tone=\{lc\.overdueCount > 0 \? 'danger' : 'success'\}/);

    // …and the comment-stripping is load-bearing on the claim side only: a comment
    // quoting the removed ladder must NOT read as the ladder still being present.
    const quoted = "// was: overdue > 0 ? 'var(--danger)' : 'var(--success)'\nconst x = 1;";
    expect(stripComments(quoted)).not.toMatch(/var\(--success\)/);
    expect(quoted).toMatch(/var\(--success\)/); // …it is only invisible after stripping
  });

  it('does not mistake a URL for a comment when stripping', () => {
    // `(^|[^:])//` protects `https://` — a naive `//.*` would delete the rest of any
    // line containing a link, including the code after it.
    const src = "const u = 'https://x/y'; const stripe = HEALTH_STRIPE[health];";
    expect(stripComments(src)).toContain('HEALTH_STRIPE[health]');
  });
});
