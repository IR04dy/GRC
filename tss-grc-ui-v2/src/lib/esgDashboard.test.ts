// S-P — unit tests for the ESG dashboard/trajectory mappers + tab guard.
// Pure functions, no DOM or live server needed.

import { describe, it, expect } from 'vitest';
import {
  mapDashboardToStats, mapTrajectory, isEsgTab, EMPTY_ESG_STATS, ESG_TABS,
} from './esgDashboard';

describe('mapDashboardToStats', () => {
  it('maps a full live dashboard payload into the KPI-strip stats shape', () => {
    const stats = mapDashboardToStats({
      year: 2025,
      overall: { score: 72, band: 'ADEQUATE' },
      ghg: { scope1Total: 14220, scope2Total: 38910, scope3Total: 142480, grandTotal: 195610 },
      ghgYoy: { totalYoY: -3.4 },
      cma: { readinessPct: 82 },
      tcfd: { alignmentPct: 64 },
      sgi: { byTier: { ON_TRACK: 5, AT_RISK: 2, DELAYED: 1 } },
    });
    expect(stats.totalEmissions).toBe(195610);
    expect(stats.scope1).toBe(14220);
    expect(stats.scope2).toBe(38910);
    expect(stats.scope3).toBe(142480);
    expect(stats.yoyTotal).toBe(-3.4);
    expect(stats.cmaCoveragePct).toBe(82);
    expect(stats.tcfdCoveragePct).toBe(64);
    expect(stats.sgiOnTrackCount).toBe(5);
    expect(stats.sgiTotal).toBe(8); // 5 + 2 + 1
  });

  it('rounds percentages and defaults missing branches to zero (no NaN)', () => {
    const stats = mapDashboardToStats({ cma: { readinessPct: 79.6 }, tcfd: {} });
    expect(stats.cmaCoveragePct).toBe(80);
    expect(stats.tcfdCoveragePct).toBe(0);
    expect(stats.totalEmissions).toBe(0);
    expect(stats.sgiTotal).toBe(0);
  });

  it('EMPTY_ESG_STATS is fully zeroed (placeholder shows no mock values)', () => {
    expect(EMPTY_ESG_STATS.totalEmissions).toBe(0);
    expect(EMPTY_ESG_STATS.cmaCoveragePct).toBe(0);
    expect(EMPTY_ESG_STATS.sgiOnTrackCount).toBe(0);
  });
});

describe('mapTrajectory', () => {
  it('maps the /ghg/trajectory series into chart points', () => {
    const pts = mapTrajectory({
      series: [
        { year: 2024, scope1Total: 100, scope2Total: 200, scope3Total: 300 },
        { year: 2025, scope1Total: 90, scope2Total: 180, scope3Total: 270 },
      ],
    });
    expect(pts).toHaveLength(2);
    expect(pts[0]).toEqual({ year: 2024, scope1: 100, scope2: 200, scope3: 300 });
    expect(pts[1].scope3).toBe(270);
  });

  it('returns an empty array when there is no series', () => {
    expect(mapTrajectory({})).toEqual([]);
  });
});

describe('isEsgTab', () => {
  it('accepts every declared tab id', () => {
    for (const tab of ESG_TABS) expect(isEsgTab(tab)).toBe(true);
  });
  it('rejects unknown / null / empty values', () => {
    expect(isEsgTab('bogus')).toBe(false);
    expect(isEsgTab(null)).toBe(false);
    expect(isEsgTab(undefined)).toBe(false);
    expect(isEsgTab('')).toBe(false);
  });
});
