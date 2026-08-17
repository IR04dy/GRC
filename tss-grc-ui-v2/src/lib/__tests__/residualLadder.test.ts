import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  TPC_RESIDUAL_LADDER, TPC_RESIDUAL_TONE, tpcResidualRank, isResidualAtLeast,
  type TpcResidual,
} from '../thirdPartyCyber';

/**
 * F2-230 — "a vendor that needs attention" must be a threshold over a declared ladder, never an
 * inclusion list; and no reader may hold a different threshold from its neighbour.
 *
 * Six readers each spelled the question out for themselves. The two on `TprmConsolidatedOverview`
 * were the sharp pair: `tpcHigh` counted HIGH|CRITICAL and `tpcOk` counted LOW|MODERATE, so with 16
 * real rows (LOW 7 / MODERATE 7 / ELEVATED 2, identical in dev and prod) the two ELEVATED assessments
 * were in NEITHER bucket and the KPIs did not sum to the register. Meanwhile the page's own
 * "high-risk" tab DID include ELEVATED — two surfaces, two thresholds, each self-consistent.
 */
describe('F2-230 — the residual-risk ladder', () => {
  const SRC = join(__dirname, '..', '..');

  it('declares all five rungs in order and covers every one in the tone map', () => {
    expect(TPC_RESIDUAL_LADDER).toEqual(['LOW', 'MODERATE', 'ELEVATED', 'HIGH', 'CRITICAL']);
    for (const tier of TPC_RESIDUAL_LADDER) {
      expect(TPC_RESIDUAL_TONE[tier], `no tone for ${tier}`).toBeTruthy();
    }
  });

  it('is not alphabetical, which is why the server sort was a false ranking', () => {
    const alphabetical = [...TPC_RESIDUAL_LADDER].sort();
    expect(alphabetical).toEqual(['CRITICAL', 'ELEVATED', 'HIGH', 'LOW', 'MODERATE']);
    expect(alphabetical).not.toEqual([...TPC_RESIDUAL_LADDER]);
    // Descending alphabetically the top rung lands LAST and the bottom rung SECOND — the arithmetic
    // of the defect, stated so the finding does not depend on a database to reproduce.
    const lexicalDesc = [...TPC_RESIDUAL_LADDER].sort().reverse();
    expect(lexicalDesc).toEqual(['MODERATE', 'LOW', 'HIGH', 'ELEVATED', 'CRITICAL']);
  });

  it('ranks and thresholds, normalising the casing the column permits', () => {
    expect(tpcResidualRank('LOW')).toBe(0);
    expect(tpcResidualRank(' elevated ')).toBe(2);
    expect(tpcResidualRank('CRITICAL')).toBe(4);
    expect(tpcResidualRank(undefined)).toBe(-1);
    expect(tpcResidualRank('SEVERE')).toBe(-1);

    expect(isResidualAtLeast('CRITICAL', 'ELEVATED')).toBe(true);
    expect(isResidualAtLeast('ELEVATED', 'ELEVATED')).toBe(true);
    expect(isResidualAtLeast('MODERATE', 'ELEVATED')).toBe(false);
    expect(isResidualAtLeast(null, 'LOW')).toBe(false);
    // The exact pre-fix hole: ELEVATED satisfied neither bucket.
    expect(isResidualAtLeast('ELEVATED' as TpcResidual, 'HIGH')).toBe(false);
    expect(isResidualAtLeast('ELEVATED' as TpcResidual, 'ELEVATED')).toBe(true);
  });

  it('the two KPI buckets PARTITION the register — no rung falls between them', () => {
    // The measured live shape. Under a threshold and its complement, every row is in exactly one
    // bucket by construction; under two inclusion lists, ELEVATED was in zero.
    const stored: TpcResidual[] = [
      ...Array(7).fill('LOW'), ...Array(7).fill('MODERATE'), 'ELEVATED', 'ELEVATED',
    ] as TpcResidual[];
    expect(stored).toHaveLength(16);

    const high = stored.filter(t => isResidualAtLeast(t, 'ELEVATED')).length;
    const ok = stored.length - high;
    expect(high).toBe(2);
    expect(ok).toBe(14);
    expect(high + ok).toBe(stored.length);

    // What the pre-fix pair produced on the same rows.
    const oldHigh = stored.filter(t => t === 'HIGH' || t === 'CRITICAL').length;
    const oldOk = stored.filter(t => t === 'LOW' || t === 'MODERATE').length;
    expect(oldHigh).toBe(0);
    expect(oldHigh + oldOk).toBe(14);
    expect(oldHigh + oldOk).not.toBe(stored.length);
  });

  it('no surface compares residualRiskTier by equality to a single token', () => {
    // The ban is on the SHAPE, not a list of files: what went wrong was six independent spellings of
    // one question, so a rule naming the six would not have prevented a seventh.
    const files = [
      'pages/third-party-cyber/ThirdPartyCyberPage.tsx',
      'components/tprm/TprmConsolidatedOverview.tsx',
      'pages/dashboard/widgets.tsx',
    ];
    const banned = /residualRiskTier\s*[=!]==?\s*['"]/;
    for (const rel of files) {
      const src = readFileSync(join(SRC, rel), 'utf8');
      expect(banned.test(src), `${rel} compares residualRiskTier by equality`).toBe(false);
      expect(src, `${rel} must ask the shared predicate`).toMatch(/isResidualAtLeast\s*\(/);
    }
  });

  it('positive control: the detector fires on the literal pre-fix shapes', () => {
    const banned = /residualRiskTier\s*[=!]==?\s*['"]/;
    expect(banned.test("if (r.residualRiskTier === 'HIGH') s.high++;")).toBe(true);
    expect(banned.test(
      "tpc.filter(r => r.residualRiskTier === 'LOW' || r.residualRiskTier === 'MODERATE')")).toBe(true);
    expect(banned.test(
      "b.content.filter((r: any) => r.residualRiskTier === 'HIGH' || r.residualRiskTier === 'CRITICAL')"))
      .toBe(true);
    // ...and not on the fixed shape, nor on an unrelated filter-state key.
    expect(banned.test("isResidualAtLeast(r.residualRiskTier, 'ELEVATED')")).toBe(false);
    expect(banned.test("advFilters.residual === 'HIGH'")).toBe(false);
  });

  it("the tab's count and filter are ONE predicate, and the KPI reads the same one", () => {
    // F2-197: a tile whose figure disagrees with the list it opens is worse than no tile. Pre-fix the
    // two tiles read 0 and 0 on 16 real rows while the tab beneath them showed 2.
    const src = readFileSync(join(SRC, 'pages/third-party-cyber/ThirdPartyCyberPage.tsx'), 'utf8');
    expect(src).toMatch(/const\s+needsAttention\s*=/);
    expect(src).toMatch(/tab === 'high-risk'\)\s*return rows\.filter\(needsAttention\)/);
    expect(src).toMatch(/highRiskCount\s*=\s*rows\.filter\(needsAttention\)\.length/);
    expect(src).toMatch(/if \(needsAttention\(r\)\) s\.elevated\+\+/);
    // The tile that counts the TOP rung must NOT navigate, because the only list this page opens is
    // the ≥ ELEVATED tab and a tile that navigates somewhere its figure does not describe is the
    // defect this rule exists to stop.
    const criticalTile = src.match(/label=\{t\('tpcKpiCritical'\)\}[^\n]*/)?.[0] ?? '';
    expect(criticalTile).not.toMatch(/onClick/);
  });

  it('the KPI label says THRESHOLD, in both languages', () => {
    // F2-212 from the inside: the slice that changes a predicate is the slice that must fix the label
    // that accurately described the old one. `tpcKpiHigh` counted exactly HIGH pre-fix, so "High
    // residual" was exact; it now counts ELEVATED and above. Checked per LANGUAGE, because F2-208
    // found this drift in the Arabic only and Arabic is the primary language (house rule 7).
    const en = readFileSync(join(SRC, 'translations/en.ts'), 'utf8');
    const ar = readFileSync(join(SRC, 'translations/ar.ts'), 'utf8');
    for (const [lang, src] of [['en', en], ['ar', ar]] as const) {
      const kpi = src.match(/tpcKpiHigh:\s*'([^']*)'/)?.[1] ?? '';
      expect(kpi, `${lang} KPI label must read as a threshold, not a single rung`).toMatch(/[≥+]/);
    }
    // The TAB label is deliberately unchanged: its predicate already included ELEVATED before this
    // finding, so "High-risk" described it then and describes it now. Only the changed predicate's
    // label needed changing — a slice that rewords every nearby label loses that distinction.
    expect(en).toMatch(/tpcTabHighRisk:\s*'High-risk'/);
  });
});
