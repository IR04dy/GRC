import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { CAB_KPI_KEYS, CHANGE_KPI_KEYS, formatKpi } from '../pages/change/ChangeKpiPack';
import { en } from '../translations/en';
import { ar } from '../translations/ar';
import type { ChangeKpi } from '../lib/api';

/**
 * S8b / CE-12 — the KPI pack's frontend half.
 *
 * <p>The failure mode here is specific and quiet: a metric the server sends that the frontend has no
 * label or blocker sentence for renders as a blank tile, which is indistinguishable from a metric
 * nobody thought about. So the FE's key list is checked against the **Java** service's key list, and
 * every `blockedBy` code the backend can emit is checked against the translations.
 */

const BACKEND = join(__dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java',
                     'com', 'tss', 'grc', 'module', 'change_mgmt', 'service');
const KPI_SERVICE = join(BACKEND, 'ChangeKpiService.java');
const KPI_RECORD = join(BACKEND, 'ChangeKpi.java');

/** `public static final String X = "value";` → the published values. */
function javaConstants(file: string): string[] {
  const src = readFileSync(file, 'utf8');
  const out: string[] = [];
  const re = /public static final String \w+\s*=\s*"([A-Za-z0-9_]+)"/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) out.push(m[1]);
  return out;
}

const d = (dict: unknown) => dict as Record<string, string>;

describe('S8b/CE-12 — the KPI pack vocabulary', () => {
  it('the FE renders every metric key the service publishes, and no others', () => {
    const feKeys = [...CHANGE_KPI_KEYS, ...CAB_KPI_KEYS];
    const src = readFileSync(KPI_SERVICE, 'utf8');
    // ⚠ Slice from AFTER the opening token. Including `ALL_KEYS = List.of(` makes the first
    // comma-separated element `ALL_KEYS = List.of(\n EMERGENCY_RATIO`, which resolves to no literal —
    // so the count came back 18 of 19 and the test failed for a reason that was not the code's.
    const open = src.indexOf('ALL_KEYS = List.of(') + 'ALL_KEYS = List.of('.length;
    const block = src.slice(open, src.indexOf(');', open));
    // ALL_KEYS lists the constant NAMES, so resolve each to its literal.
    const literals = new Map<string, string>();
    const re = /public static final String (\w+)\s*=\s*"([A-Za-z0-9_]+)"/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(src)) !== null) literals.set(m[1], m[2]);
    const backendKeys = block.split(',').map(s => s.trim()).filter(Boolean)
      .map(name => literals.get(name)).filter(Boolean) as string[];

    expect(backendKeys.length, 'ALL_KEYS did not parse').toBe(19);
    expect(feKeys.sort()).toEqual(backendKeys.sort());
  });

  it('every metric has a label in both languages, and the Arabic is Arabic', () => {
    for (const key of [...CHANGE_KPI_KEYS, ...CAB_KPI_KEYS]) {
      const k = 'changeKpi_' + key;
      expect(d(en)[k], `${k} missing from en`).toBeTruthy();
      expect(d(ar)[k], `${k} missing from ar`).toBeTruthy();
      expect(d(ar)[k], `${k} is not translated`).toMatch(/[؀-ۿ]/);
    }
  });

  it('every basis, provenance and blocker the backend can emit has a sentence in both languages', () => {
    const consts = javaConstants(KPI_RECORD);
    const bases = ['COMPUTED', 'COMPUTED_PARTIAL', 'NO_DATA', 'NOT_MEASURABLE'];
    const provs = ['MEASURED', 'ASSERTED', 'MIXED'];
    // Blockers are the SCREAMING_SNAKE constants that are not bases, provenances or units.
    const units = ['PCT', 'DAYS', 'COUNT', 'RATIO_PER_MEETING'];
    const blockers = consts.filter(c => !bases.includes(c) && !provs.includes(c) && !units.includes(c));

    expect(blockers.length, 'no blocker codes parsed — the scan is broken').toBeGreaterThanOrEqual(8);
    for (const [prefix, values] of [['changeKpiBasis_', bases],
                                    ['changeKpiProvenance_', provs],
                                    ['changeKpiBlocked_', blockers]] as const) {
      for (const v of values) {
        const k = prefix + v;
        // ⚠ A missing key here is a BLANK TILE at runtime, not an error — the exact failure this
        // test exists for, because nothing else would ever report it.
        expect(d(en)[k], `${k} missing from en`).toBeTruthy();
        expect(d(ar)[k], `${k} missing from ar`).toBeTruthy();
        expect(d(ar)[k], `${k} is not translated`).toMatch(/[؀-ۿ]/);
      }
    }
  });

  it('the basis label describes COVERAGE and never claims measurement', () => {
    // ⚠ Found in the browser, not in review: `changeKpiBasis_COMPUTED` was "Measured", so the CAB
    // quorum tile rendered "Asserted, not computed" next to a badge reading "Measured" — the two axes
    // contradicting each other on one tile, which is the conflation they exist to prevent. The basis
    // vocabulary must stay about coverage; only `provenance` may speak about measurement.
    for (const dict of [en, ar]) {
      const basis = [d(dict).changeKpiBasis_COMPUTED, d(dict).changeKpiBasis_COMPUTED_PARTIAL,
                     d(dict).changeKpiBasis_NO_DATA, d(dict).changeKpiBasis_NOT_MEASURABLE];
      for (const label of basis) {
        expect(label, 'a coverage label must not use the provenance vocabulary')
          .not.toBe(d(dict).changeKpiProvenance_MEASURED);
      }
    }
    expect(d(en).changeKpiBasis_COMPUTED).toMatch(/coverage/i);
    expect(d(en).changeKpiProvenance_MEASURED).toBe('Computed');
  });

  it('a null value never formats as a number', () => {
    const base: ChangeKpi = { key: 'x', value: null, unit: 'PCT', basis: 'NOT_MEASURABLE' };
    expect(formatKpi(base)).toBe('—');
    expect(formatKpi({ ...base, basis: 'NO_DATA' })).toBe('—');
    // And a real zero is not hidden: 0% measured is a legitimate, different answer.
    expect(formatKpi({ ...base, value: 0, basis: 'COMPUTED' })).toBe('0%');
    expect(formatKpi({ ...base, value: 41.5, unit: 'DAYS', basis: 'COMPUTED' })).toBe('41.5d');
  });

  it('the tile shows the denominator and marks asserted figures on the tile itself', () => {
    const src = readFileSync(join(__dirname, '..', 'pages', 'change', 'ChangeKpiPack.tsx'), 'utf8');
    // Strip comments — a structural assertion a comment can satisfy is not a test.
    const code = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
    expect(code, 'the denominator is no longer rendered').toContain("t('changeKpiOver')");
    expect(code, 'the asserted marker left the tile').toContain('changes-kpi-asserted');
    expect(code, 'NOT_MEASURABLE tiles must render their reason, not vanish')
      .toContain("t('changeKpiBlocked_'");
    expect(code, 'the partial-coverage warning is gone').toContain("t('changeKpiPartial')");
  });

  it('the success-rate basis sentence names both figures F2-107 turned on', () => {
    for (const dict of [en, ar]) {
      expect(d(dict).changeSuccessRateBasis).toContain('{d}');
      expect(d(dict).changeSuccessRateBasis).toContain('{r}');
    }
    const panel = readFileSync(join(__dirname, '..', 'pages', 'change', 'ChangeAnalyticsPanel.tsx'), 'utf8');
    expect(panel).toContain('successRateDenominator');
    expect(panel).toContain('successRateOutcomesRecorded');
  });
});
