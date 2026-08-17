import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  FP_CONCENTRATION_LADDER, FP_CONC_TONE, fpConcentrationRank, isConcentrationAtLeast,
  type FpConcentration,
} from '../fourthParty';

/**
 * F2-229 — "elevated concentration" must be a threshold over a declared ladder, never a second
 * inclusion list.
 *
 * Six readers each spelled the question out for themselves — `=== 'HIGH'` in three places,
 * `HIGH || MEDIUM` in two more — and every one of them silently dropped CRITICAL, which
 * `scm.fourth_party_exposures` holds on one row in both dev and prod. So `FP_CONC_TONE[tier]` was
 * `undefined` for that row, the concentration tab excluded it, its chip under-counted by one, and
 * both TPRM dashboards skipped it: the register's most concentrated fourth party was the one row none
 * of the concentration surfaces could see. F2-196's rule, on a tier instead of a status.
 */
describe('F2-229 — the concentration ladder', () => {
  const SRC = join(__dirname, '..', '..');

  it('declares CRITICAL, in ladder order, and covers it in the tone map', () => {
    expect(FP_CONCENTRATION_LADDER).toEqual(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);
    // The tone map is a Record over the union, so a missing key is a `undefined` tone on a real row.
    for (const tier of FP_CONCENTRATION_LADDER) {
      expect(FP_CONC_TONE[tier], `no tone for ${tier}`).toBeTruthy();
    }
  });

  it('is not in alphabetical order, which is why a lexical sort could not rank it', () => {
    const alphabetical = [...FP_CONCENTRATION_LADDER].sort();
    expect(alphabetical).toEqual(['CRITICAL', 'HIGH', 'LOW', 'MEDIUM']);
    expect(alphabetical).not.toEqual([...FP_CONCENTRATION_LADDER]);
  });

  it('ranks and thresholds, normalising the casing the column permits', () => {
    expect(fpConcentrationRank('LOW')).toBe(0);
    expect(fpConcentrationRank(' critical ')).toBe(3);
    expect(fpConcentrationRank(undefined)).toBe(-1);
    expect(fpConcentrationRank('SEVERE')).toBe(-1);

    expect(isConcentrationAtLeast('CRITICAL', 'HIGH')).toBe(true);
    expect(isConcentrationAtLeast('HIGH', 'HIGH')).toBe(true);
    expect(isConcentrationAtLeast('MEDIUM', 'HIGH')).toBe(false);
    expect(isConcentrationAtLeast('CRITICAL', 'MEDIUM')).toBe(true);
    expect(isConcentrationAtLeast(null, 'LOW')).toBe(false);
    // The exact pre-fix bug: CRITICAL must satisfy an "at least HIGH" question.
    expect(isConcentrationAtLeast('CRITICAL' as FpConcentration, 'HIGH')).toBe(true);
  });

  it('no surface compares concentrationRisk by equality to a single token', () => {
    // The ban is on the SHAPE, not on a list of files: what went wrong was six independent
    // spellings of one question, so a rule naming the six would not have prevented a seventh.
    const files = [
      'pages/fourth-party/FourthPartyPage.tsx',
      'components/tprm/TprmConsolidatedOverview.tsx',
      'pages/dashboard/widgets.tsx',
    ];
    const banned = /concentrationRisk\s*[=!]==?\s*['"]/;
    for (const rel of files) {
      const src = readFileSync(join(SRC, rel), 'utf8');
      expect(banned.test(src), `${rel} compares concentrationRisk by equality`).toBe(false);
      expect(src, `${rel} must ask the shared predicate`).toMatch(/isConcentrationAtLeast\s*\(/);
    }
  });

  it('positive control: the detector fires on the literal pre-fix shapes', () => {
    const banned = /concentrationRisk\s*[=!]==?\s*['"]/;
    // The three real pre-fix spellings, verbatim.
    expect(banned.test("if (r.concentrationRisk === 'HIGH') s.highConc++;")).toBe(true);
    expect(banned.test(
      "rows.filter(r => r.concentrationRisk === 'HIGH' || r.concentrationRisk === 'MEDIUM')")).toBe(true);
    expect(banned.test(
      "a.content.filter((r: any) => r.concentrationRisk === 'HIGH').length")).toBe(true);
    // ...and does not fire on the fixed shape, or on an unrelated filter-state key.
    expect(banned.test("isConcentrationAtLeast(r.concentrationRisk, 'HIGH')")).toBe(false);
    expect(banned.test("advFilters.severity === 'HIGH'")).toBe(false);
  });

  it('both labels say THRESHOLD, in both languages', () => {
    // F2-212's rule from the inside: removing an inclusion list can turn a label that accurately
    // described it into an imprecise one, and the slice that fixes the predicate is the slice that
    // has to fix the label. `f4pKpiHighConc` counted exactly the HIGH rows pre-fix, so "High
    // concentration" was exact; it now counts HIGH *and above*. Checked per LANGUAGE, because
    // F2-208 found this drift in the Arabic only and Arabic is the primary language (house rule 7).
    const en = readFileSync(join(SRC, 'translations/en.ts'), 'utf8');
    const ar = readFileSync(join(SRC, 'translations/ar.ts'), 'utf8');
    for (const [lang, src] of [['en', en], ['ar', ar]] as const) {
      const kpi = src.match(/f4pKpiHighConc:\s*'([^']*)'/)?.[1] ?? '';
      const tab = src.match(/f4pTabConcentration:\s*'([^']*)'/)?.[1] ?? '';
      expect(kpi, `${lang} KPI label must read as a threshold`).toMatch(/[≥+]/);
      expect(tab, `${lang} tab label must read as a threshold`).toMatch(/[≥+]/);
    }
  });

  it("the tab's count is the same predicate as the tab's filter", () => {
    // F2-197: a chip whose figure disagrees with the list it opens is worse than no chip. The page
    // previously summed two separately-spelled inclusion lists; it now filters once and counts that.
    const src = readFileSync(join(SRC, 'pages/fourth-party/FourthPartyPage.tsx'), 'utf8');
    expect(src).toMatch(/const\s+isElevated\s*=/);
    expect(src).toMatch(/tab === 'concentration'\)\s*return rows\.filter\(isElevated\)/);
    expect(src).toMatch(/concentrationCount\s*=\s*rows\.filter\(isElevated\)\.length/);
  });
});
