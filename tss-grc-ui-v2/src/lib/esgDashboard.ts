// ESG dashboard + trajectory API client — S-P (frontend truth).
//
// Powers the /esg Overview KPIs + emissions-trend chart from the REAL backend
// (/api/sustainability/dashboard + /ghg/trajectory), replacing the lib/esg.ts
// mock (aggregateEsg / EMISSIONS_TIMESERIES). The mappers are pure so they can
// be unit-tested without a live server.

import { api } from './api';
import type { EsgStats } from './esg';

// ─── ESG tab identifiers (shared for deep-linking) ──────────────────
export const ESG_TABS = [
  'overview', 'annex', 'capa', 'calculator', 'cma', 'tcfd', 'materiality',
  'metrics', 'evidence', 'sgi', 'ghg', 'climate', 'survey', 'settings',
  'targets', 'ifrs', 'env', 'assurance',
] as const;
export type EsgTab = typeof ESG_TABS[number];

/** Guard for a deep-linked ?tab= value → a valid tab (else the caller defaults). */
export function isEsgTab(v: string | null | undefined): v is EsgTab {
  return !!v && (ESG_TABS as readonly string[]).includes(v);
}

// ─── Dashboard ──────────────────────────────────────────────────────
// Partial view of /api/sustainability/dashboard — only the fields the
// Overview KPI strip maps (the endpoint returns much more).
export interface EsgDashboardRaw {
  year?: number;
  overall?: { score?: number; band?: string };
  ghg?: { scope1Total?: number; scope2Total?: number; scope3Total?: number; grandTotal?: number };
  ghgYoy?: { totalYoY?: number };
  cma?: { readinessPct?: number };
  tcfd?: { alignmentPct?: number };
  sgi?: { byTier?: Record<string, number> };
  topGaps?: string[];
}

const num = (v: unknown): number =>
  typeof v === 'number' && Number.isFinite(v) ? v : (Number(v) || 0);

/**
 * Map the live dashboard payload into the EsgStats shape the Overview KPI strip
 * already consumes — so the strip's JSX is unchanged, only its data source
 * flips from mock to live. survey* are 0 here (the Survey tab owns its own live
 * data since S-O; the Overview strip does not render them).
 */
export function mapDashboardToStats(d: EsgDashboardRaw): EsgStats {
  const ghg = d.ghg ?? {};
  const byTier = d.sgi?.byTier ?? {};
  const onTrack = num(byTier.ON_TRACK);
  const sgiTotal = onTrack + num(byTier.AT_RISK) + num(byTier.DELAYED);
  return {
    totalEmissions: num(ghg.grandTotal),
    scope1: num(ghg.scope1Total),
    scope2: num(ghg.scope2Total),
    scope3: num(ghg.scope3Total),
    yoyTotal: num(d.ghgYoy?.totalYoY),
    tcfdTotal: 0,
    tcfdDisclosed: 0,
    tcfdCoveragePct: Math.round(num(d.tcfd?.alignmentPct)),
    cmaTotal: 0,
    cmaDisclosed: 0,
    cmaCoveragePct: Math.round(num(d.cma?.readinessPct)),
    sgiOnTrackCount: onTrack,
    sgiTotal,
    surveyTotal: 0,
    surveySubmitted: 0,
    surveyOverdue: 0,
  };
}

/** Zeroed stats used as the pre-fetch placeholder (no mock values on screen). */
export const EMPTY_ESG_STATS: EsgStats = mapDashboardToStats({});

export const esgDashboardApi = {
  get: (year: number) => api.get<EsgDashboardRaw>(`/api/sustainability/dashboard?year=${year}`),
};

// ─── Multi-year emissions trajectory (trend chart) ──────────────────
export interface TrajectoryRaw {
  series?: Array<{ year?: number; scope1Total?: number; scope2Total?: number; scope3Total?: number }>;
}
export interface EsgTrajectoryPoint { year: number; scope1: number; scope2: number; scope3: number }

/** Map the /ghg/trajectory series into the trend-chart point shape. */
export function mapTrajectory(raw: TrajectoryRaw): EsgTrajectoryPoint[] {
  return (raw.series ?? []).map(p => ({
    year: num(p.year),
    scope1: num(p.scope1Total),
    scope2: num(p.scope2Total),
    scope3: num(p.scope3Total),
  }));
}

export const esgTrajectoryApi = {
  get: (fromYear: number, toYear: number) =>
    api.get<TrajectoryRaw>(`/api/sustainability/ghg/trajectory?fromYear=${fromYear}&toYear=${toYear}`),
};
