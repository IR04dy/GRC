// BCM Continuity Readiness Score client — audit Tier-2.
//
// Wraps /api/v1/bcm/readiness-score/* with the four shape variants the
// backend exposes: per-BIA, per-cycle, leaderboard, tenant headline.

import { api } from './api';

export interface ScoreBreakdown {
  biaId: number;
  biaCode: string;
  serviceId?: number;
  processId?: number;
  criticality: string;
  totalScore: number;                       // 0..100
  biaApproved: boolean;
  bcpApproved: boolean;
  drpApproved: boolean;
  drTestPassed: boolean;
  manualWorkaroundDocumented: boolean;
  lowestDimension: string;
}

export interface CycleScore {
  cycleId: number;
  averageScore: number;
  biaCount: number;
  rows: ScoreBreakdown[];
}

export interface TenantScore {
  averageScore: number;                     // 0..100
  biaCount: number;
  biasByCriticality: Record<string, number>;
}

export async function scoreForBia(biaId: number): Promise<ScoreBreakdown> {
  return (await api.get<ScoreBreakdown>(`/api/v1/bcm/readiness-score/bia/${biaId}`)).data;
}
export async function scoreForCycle(cycleId: number): Promise<CycleScore> {
  return (await api.get<CycleScore>(`/api/v1/bcm/readiness-score/cycle/${cycleId}`)).data;
}
export async function readinessLeaderboard(): Promise<ScoreBreakdown[]> {
  return (await api.get<ScoreBreakdown[]>(`/api/v1/bcm/readiness-score/leaderboard`)).data;
}
export async function tenantReadiness(): Promise<TenantScore> {
  return (await api.get<TenantScore>(`/api/v1/bcm/readiness-score/tenant`)).data;
}

/** Map a 0..100 score to a tone for badge / progress rendering. */
export function scoreTone(score: number): 'success' | 'info' | 'warning' | 'danger' {
  if (score >= 85) return 'success';
  if (score >= 60) return 'info';
  if (score >= 30) return 'warning';
  return 'danger';
}
