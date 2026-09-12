import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-195 — a KPI tile may not assert a metric that nothing computes.
 *
 * <p>The module KPI strip is the first thing anyone reads on a GRC screen, and it is where this
 * platform's fabrications survived longest, because each fabricated tile sat beside three real ones.
 * Measured on the tree this test was written against: **9 of 1,069 `<Kpi>` tags** asserted a figure
 * no server produced, on 5 module pages, and **every one was in the flattering direction** —
 *
 *   - `cyberKpiCompliance` "NCA ECC Score" = `"87%"`, while all 29 NCA ECC controls in the register
 *     were NOT_IMPLEMENTED. The real score is **0%**: a 87-point overstatement of a Saudi
 *     regulator's compliance score, rendered in `success` green.
 *   - `incidentsKpiMttr` = `"4.2h"`, while `/incidents/stats` already returned `avgDays: 4.5`. Not
 *     merely 26× low — the wrong **unit**.
 *   - `complianceKpiEvidence` = `"1,283"` against 78 real evidence items (16×), and
 *     `complianceKpiScore` = `"87%"` fifteen lines above a real coverage gauge reading 66%.
 *   - `bcmKpiExercises` = `"92%"`, which is exactly exercise #1's `score`, presented as an aggregate.
 *   - `privacyKpiActivities` = `ropa.data.length || 142` and `bcmKpiProcesses` = `.length || 38`.
 *
 * <p>🔑 The `|| N` shape is the sharp one: `0 || 142` is 142, so the fallback fires on **exactly**
 * the tenant whose register is empty — a PDPL Article 31 record-of-processing tile showing 142
 * activities for an organisation that has recorded none. [[F2-52]] found and fixed this shape once,
 * on `AdminPage`'s `roles.length || 7`, and left a comment stating the rule: *"The real count is the
 * real count, including 0."* Its two siblings survived, which is the recurring lesson here — fixing
 * the instance you noticed is not closing the shape.
 *
 * <p>Three detectable shapes, all of which this test refuses:
 *   1. **LITERAL** — `value={87}` or `value="87%"` / `"1,283"` / `"4.2h"`. A metric with no source.
 *   2. **FALLBACK** — `value={x.length || 38}`. `|| 0` and `?? 0` are fine: zero is the honest answer
 *      for a missing count, and the shape that matters substitutes a *plausible* number for nothing.
 *   3. **TREND** — `trend={{ dir: 'up', value: '+4%' }}`. A trend is a **second, independent claim**:
 *      it asserts a prior-period figure. Four of the fabricated tiles carried one, and nothing in
 *      the platform stores a KPI time series — so wiring a value while keeping its literal trend
 *      would leave in place the half of the fabrication that implies history exists.
 *
 * <p>⚠ A scan that reports a small number may be reporting it because the scan is broken — three
 * findings in this lineage produced phantom counts over source regexes ([[F2-187]] 1,763 endpoints
 * from a javadoc sentence; [[F2-184]]'s phantom 75 types; [[F2-193]]'s 0-of-389). So this file
 * carries `theDetectorActuallyDetects`, which runs the same predicates over **synthetic** tags of
 * each shape. The corpus assertions could pass vacuously if the regexes rotted; that one cannot.
 */

const SRC = join(process.cwd(), 'src');

/**
 * The KPI tiles whose literal value is a **product fact rather than a measurement**, each with the
 * reason it is not a fabrication. Enforced in BOTH directions ([[F2-187]]'s named-map shape): an
 * entry whose site stops being literal must be deleted, so this list cannot quietly become a
 * tolerated population — which is what [[F2-193]]'s ceiling-versus-invariant note is about.
 */
const NOT_A_MEASUREMENT: Record<string, string> = {
  'src/pages/reporting/ReportsCentrePage.tsx:rcKpiLanguages':
    'The number of languages the export engine renders is a property of the BUILD, not of the '
    + 'tenant: OpenPDF ships with the bundled Amiri font for Arabic and the Latin default for '
    + 'English, and every generator emits exactly those two. There is no per-organisation value '
    + 'for a server to compute and nothing that could make it 3 without a code change.',
};

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (/\.tsx?$/.test(name) && !name.includes('.test.')) out.push(p);
  }
  return out;
}

/** `<Kpi ... />`, tolerating one level of nested braces in props. */
const KPI = /<Kpi\b(?:[^<>]|\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\})*?\/>/gs;
const VALUE = /\bvalue=(\{(?:[^{}]|\{[^{}]*\})*\}|"[^"]*")/s;
const TREND = /\btrend=(\{\{(?:[^{}]|\{[^{}]*\})*\}\})/s;
const LABEL = /\blabel=(?:\{t\('([^']+)'\)[^}]*\}|"([^"]+)"|\{([^{}]{0,60})\})/;

/** A bare numeric literal, or a quoted string carrying a digit ("87%", "1,283", "4.2h"). */
function isLiteralMetric(value: string): boolean {
  const inner = value.startsWith('{') ? value.slice(1, -1).trim() : value;
  if (/^-?\d+(\.\d+)?$/.test(inner)) return true;
  if (value.startsWith('"') && /\d/.test(value)) return true;
  if (/^'[^']*'$/.test(inner) && /\d/.test(inner)) return true;
  return false;
}

/** `|| N` / `?? N` where N >= 2 — a plausible number standing in for "nothing". */
function fallbackNumber(value: string): string | null {
  const inner = value.startsWith('{') ? value.slice(1, -1).trim() : value;
  const m = /(\|\||\?\?)\s*(\d+(?:\.\d+)?)\s*$/.exec(inner);
  return m && Number(m[2]) >= 2 ? m[2] : null;
}

interface Finding { key: string; file: string; line: number; label: string; value: string; kinds: string[] }

function scan(): { findings: Finding[]; tags: number } {
  const findings: Finding[] = [];
  let tags = 0;
  for (const abs of walk(SRC)) {
    const rel = abs.slice(process.cwd().length + 1).replace(/\\/g, '/');
    const src = readFileSync(abs, 'utf-8');
    for (const m of src.matchAll(KPI)) {
      tags++;
      const tag = m[0];
      const vm = VALUE.exec(tag);
      const lm = LABEL.exec(tag);
      const label = lm ? (lm[1] ?? lm[2] ?? lm[3] ?? '?') : '?';
      const kinds: string[] = [];
      if (vm) {
        if (isLiteralMetric(vm[1])) kinds.push('LITERAL');
        const fb = fallbackNumber(vm[1]);
        if (fb) kinds.push(`FALLBACK(${fb})`);
      }
      if (TREND.test(tag)) kinds.push('LITERAL_TREND');
      if (kinds.length > 0) {
        findings.push({
          key: `${rel}:${label}`, file: rel,
          line: src.slice(0, m.index).split('\n').length,
          value: (vm?.[1] ?? '').replace(/\s+/g, ' ').slice(0, 60),
          label, kinds,
        });
      }
    }
  }
  return { findings, tags };
}

const { findings, tags } = scan();

describe('F2-195 — no KPI tile asserts a metric nothing computes', () => {
  it('scans a real population, so a pass is not an empty scan', () => {
    // If the tag regex broke, every assertion below would pass over nothing. The count is the
    // guard: it was 1,069 when this was written and only moves when tiles are added or removed.
    expect(tags, 'the <Kpi> regex matched almost nothing — the scan, not the app, is broken')
      .toBeGreaterThan(900);
  });

  it('every literal, fallback or literal-trend KPI is a declared product fact', () => {
    const undeclared = findings.filter(f => !(f.key in NOT_A_MEASUREMENT));
    const detail = undeclared
      .map(f => `${f.file}:${f.line}  ${f.label}  value=${f.value}  [${f.kinds.join(' ')}]`)
      .join('\n  ');
    expect(undeclared, 'A KPI tile is asserting a figure no server produces. Wire it to a real '
      + 'source, or render `value={null}` with an `emptyHint` so "not measured" cannot read as a '
      + `measurement. If it is a build-time product fact, declare it in NOT_A_MEASUREMENT.\n  ${detail}`)
      .toEqual([]);
  });

  it('...and no declared exception has quietly been fixed', () => {
    // The other direction. Without this the map is a ratchet: a site that gained a real source
    // would keep its exemption, and the next literal added under the same key would pass.
    const live = new Set(findings.map(f => f.key));
    const stale = Object.keys(NOT_A_MEASUREMENT).filter(k => !live.has(k));
    expect(stale, `NOT_A_MEASUREMENT names KPI tiles that are no longer literal: ${stale.join(', ')}`
      + ' — delete the entries in the same commit that fixed them.').toEqual([]);
  });

  it('every exception states WHY the literal cannot come from a server', () => {
    // [[F2-188]]: a named-map gate proves a reason exists, never that it is true — but a bare label
    // ("product fact") is not even a claim, and that check fired on six of my own first drafts.
    for (const [key, reason] of Object.entries(NOT_A_MEASUREMENT)) {
      expect(reason.length, `${key}: the reason is a label, not an argument`).toBeGreaterThan(120);
      expect(reason, `${key}: the reason must say what would have to change for the number to `
        + 'change — that is the test of whether it is tenant data')
        .toMatch(/build|code change|compile|bundle|per-organisation|per-tenant/i);
    }
  });

  it('the detector actually detects — synthetic tags of each shape', () => {
    // ⚠ The load-bearing test in this file. The corpus assertions above pass when the app is clean
    // AND when the regexes have rotted; only this one distinguishes those. Each synthetic tag is
    // the exact shape F2-195 removed from a real page.
    const cases: Array<[string, string]> = [
      ['LITERAL', '<Kpi Icon={X} label={t(\'k\')} value="87%" tone="success" />'],
      ['LITERAL', '<Kpi Icon={X} label={t(\'k\')} value={1283} tone="info" />'],
      ['FALLBACK(142)', '<Kpi Icon={X} label={t(\'k\')} value={ropa.data.length || 142} tone="primary" />'],
      ['LITERAL_TREND', '<Kpi Icon={X} label={t(\'k\')} value={real} trend={{ dir: \'up\', value: \'+4%\' }} />'],
    ];
    for (const [expected, tag] of cases) {
      const vm = VALUE.exec(tag);
      const kinds: string[] = [];
      if (vm) {
        if (isLiteralMetric(vm[1])) kinds.push('LITERAL');
        const fb = fallbackNumber(vm[1]);
        if (fb) kinds.push(`FALLBACK(${fb})`);
      }
      if (TREND.test(tag)) kinds.push('LITERAL_TREND');
      expect(kinds, `the detector no longer recognises ${expected}: ${tag}`).toContain(expected);
      expect(KPI.test(tag) || new RegExp(KPI.source, 's').test(tag),
        `the <Kpi> tag regex no longer matches: ${tag}`).toBe(true);
    }
  });

  it('...and does NOT flag the honest shapes it must leave alone', () => {
    // The negative control. A detector that flags everything would also pass the assertions above
    // once every site was exempted, so its precision is part of the contract.
    const honest = [
      '<Kpi Icon={X} label={t(\'k\')} value={risks.length} tone="primary" />',
      '<Kpi Icon={X} label={t(\'k\')} value={stats.REQUESTED || 0} tone="warning" />',
      '<Kpi Icon={X} label={t(\'k\')} value={pct === null ? null : `${pct}%`} emptyHint={t(\'e\')} />',
      '<Kpi Icon={X} label={t(\'k\')} value={null} emptyHint={t(\'e\')} tone="neutral" />',
      '<Kpi Icon={X} label={t(\'k\')} value={mean} trend={computedTrend} />',
    ];
    for (const tag of honest) {
      const vm = VALUE.exec(tag);
      const kinds: string[] = [];
      if (vm) {
        if (isLiteralMetric(vm[1])) kinds.push('LITERAL');
        const fb = fallbackNumber(vm[1]);
        if (fb) kinds.push(`FALLBACK(${fb})`);
      }
      if (TREND.test(tag)) kinds.push('LITERAL_TREND');
      expect(kinds, `false positive — this tile is honest: ${tag}`).toEqual([]);
    }
  });

  it('a KPI that can render null offers an emptyHint, so an em-dash says why', () => {
    // `Kpi` renders null/undefined as the `.empty-inline-compact` em-dash with `emptyHint` as its
    // tooltip (UX-008), precisely "so 'not yet measured' never reads as a real 0". A nullable value
    // without the hint gets the dash and no explanation, which is how a reader concludes the screen
    // is broken rather than the metric unmeasured.
    //
    // ⚠ The first draft of this assertion tested `/\bnull\b/` anywhere in the value expression and
    // fired on 16 tiles that pass a STRING, matching only the `null` inside their `x == null`
    // comparison. Fifth instance in this lineage of a check satisfied by something other than its
    // claim ([[F2-192]]'s `list`, [[F2-193]]'s quotation, [[F2-194]]'s type name and import). What
    // matters is whether the expression can EVALUATE to null, so the patterns are positional.
    const missing: string[] = [];
    for (const abs of walk(SRC)) {
      const rel = abs.slice(process.cwd().length + 1).replace(/\\/g, '/');
      const src = readFileSync(abs, 'utf-8');
      for (const m of src.matchAll(KPI)) {
        const tag = m[0];
        const vm = VALUE.exec(tag);
        if (!vm) continue;
        const inner = vm[1].startsWith('{') ? vm[1].slice(1, -1).trim() : vm[1];
        const yieldsNull = /^(null|undefined)$/.test(inner)          // value={null}
          || /\?\s*(null|undefined)\s*:/.test(inner)                 // cond ? null : x
          || /:\s*(null|undefined)\s*$/.test(inner)                  // cond ? x : null
          || /(\?\?|\|\|)\s*(null|undefined)\s*$/.test(inner);       // x ?? null
        if (yieldsNull && !/\bemptyHint=/.test(tag)) {
          missing.push(`${rel}:${src.slice(0, m.index).split('\n').length}`);
        }
      }
    }
    expect(missing, `these KPI tiles can render an unexplained em-dash: ${missing.join(', ')}`)
      .toEqual([]);
  });

  /**
   * The population the corrected detector above revealed, held as a decrease-only count rather than
   * a hard zero — because closing it means writing a bilingual reason for each, and manufacturing 16
   * of those without reading each page is [[F2-188]]'s failure mode.
   *
   * These tiles hand-roll the dash — `value={x == null ? '—' : …}` — passing the em-dash as a
   * STRING. It looks identical on screen and is not the same thing: it bypasses `Kpi`'s
   * `.empty-inline-compact` treatment and, more importantly, its `emptyHint` tooltip, so the reader
   * gets a dash with no way to learn whether the metric is unmeasured or the screen is broken. It is
   * a strictly weaker defect than a fabricated number — nothing false is asserted — which is why
   * F2-195 fixed the 9 fabrications and filed these.
   *
   * ⚠ This baseline was first written as **16**, taken from the output of the buggy detector two
   * assertions up — a number that answered a different question ("tiles with `null` anywhere in the
   * value expression and no hint"). The real count is **71 across 32 files**. 🔑 A baseline lifted
   * from a measurement of something else is worse than no baseline: it looks measured. The failing
   * run is the only reason it is right now, which is the argument for asserting a count at all.
   */
  const HAND_ROLLED_DASH_BASELINE = 71;

  it('the hand-rolled em-dash population does not grow', () => {
    const sites: string[] = [];
    for (const abs of walk(SRC)) {
      const rel = abs.slice(process.cwd().length + 1).replace(/\\/g, '/');
      const src = readFileSync(abs, 'utf-8');
      for (const m of src.matchAll(KPI)) {
        const vm = VALUE.exec(m[0]);
        // The literal em-dash character passed as the value, i.e. an empty state rendered as data.
        if (vm && /['"`]—['"`]/.test(vm[1])) {
          sites.push(`${rel}:${src.slice(0, m.index).split('\n').length}`);
        }
      }
    }
    expect(sites.length, `hand-rolled em-dash KPI tiles went from ${HAND_ROLLED_DASH_BASELINE} to `
      + `${sites.length}. New tiles must pass \`value={null}\` with an \`emptyHint\` instead of the `
      + `dash as a string, so the empty state carries its explanation:\n  ${sites.join('\n  ')}`)
      .toBeLessThanOrEqual(HAND_ROLLED_DASH_BASELINE);
  });

  /*
   * ── JRN-03 ────────────────────────────────────────────────────────────────
   * A literal trend passed as an object PROPERTY, not as a `<Kpi>` prop.
   *
   * The LITERAL_TREND kind above is real and its POPULATION is `trend={{…}}` on
   * a `<Kpi>` tag. `ExecutiveDashboard` builds its own `KpiTile` and passes the
   * same claim as `delta: { value: -2, tone: 'success' }` inside a TS array, so
   * it sat outside this gate by construction — the shape this register keeps
   * recording (F2-198), here narrowed by a prop SPELLING.
   *
   * Three such chips were live: `-2` on Open risks, `+3` on Compliance score and
   * `+1` on Active incidents. 🔑 The first and third tiles fetch a REAL 12-week
   * series from `/api/v1/dashboard/kpi-trend` and render it as the sparkline
   * directly beside the invented chip — so each tile disagreed with its own
   * graph (F2-197), which is worse than a figure with no provenance at all.
   *
   * Hard zero, no exemption map: a trend must be DERIVED from a series, and
   * where no series exists the prop is simply omitted — both alternatives are
   * always available, so there is no correct code for this leg to fail on
   * (F2-214). The conditional form is covered too, because the invented value
   * survives the ternary that gates it.
   */
  const LITERAL_DELTA_PROP =
    /\b(?:delta|trend)\s*:\s*(?:[^,{}]*\?\s*)?\{\s*value\s*:\s*[+-]?\d+(?:\.\d+)?/g;

  it('no tile passes a hand-typed trend as an object property', () => {
    const offenders: string[] = [];
    for (const abs of walk(SRC)) {
      const rel = abs.slice(process.cwd().length + 1).replace(/\\/g, '/');
      const src = readFileSync(abs, 'utf-8');
      for (const m of src.matchAll(LITERAL_DELTA_PROP)) {
        offenders.push(`${rel}:${src.slice(0, m.index).split('\n').length}  ${m[0].replace(/\s+/g, ' ')}`);
      }
    }
    expect(offenders, 'a period-over-period change is typed in rather than derived. A trend is a '
      + 'SECOND, independent claim: it asserts a prior period. Derive it from a real series, or omit '
      + 'the prop — never type the number:\n  ' + offenders.join('\n  ')).toEqual([]);
  });

  it('...and that detector fires on the shapes it is for, and not on derived ones', () => {
    const mustFlag = [
      "delta: { value: -2, tone: 'success' },",
      "delta: { value: +3, tone: 'success' },",
      "delta: stats.openIncidents > 0 ? { value: +1, tone: 'danger' } : undefined,",
      "trend: { value: 4.2, dir: 'up' },",
    ];
    for (const s of mustFlag) {
      expect([...s.matchAll(LITERAL_DELTA_PROP)].length, `should flag: ${s}`).toBe(1);
    }
    const mustNotFlag = [
      'delta: deriveDelta(riskTrend),',
      "delta: { value: last - first, tone: last <= first ? 'success' : 'danger' },",
      'delta: series.length >= 2 ? { value: series.at(-1)! - series[0], tone } : undefined,',
      'delta,',
      'const delta = { value: n };',
    ];
    for (const s of mustNotFlag) {
      expect([...s.matchAll(LITERAL_DELTA_PROP)].length, `should NOT flag: ${s}`).toBe(0);
    }
  });
});
