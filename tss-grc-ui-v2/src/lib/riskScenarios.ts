// Risk-scenario API client. Uses the shared api.ts client so we inherit
// auth refresh, credentials, and 401 → clearSession behaviour.

import { api } from './api';

export type ScenarioStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';
export type ScenarioType   = 'MONTE_CARLO' | 'STRESS_TEST' | 'WHAT_IF';

export interface MonteCarloInput {
  pMin: number;     pMode: number;    pMax: number;
  iMin: number;     iMode: number;    iMax: number;
  trials: number;
}
export interface HistogramBucket { rangeStart: number; rangeEnd: number; count: number; }
export interface MonteCarloResult {
  expectedLoss: number; p95Loss: number; p99Loss: number; stdev: number;
  histogram: HistogramBucket[];
}
export interface StressTestFactor { name: string; shockPct: number; }
export interface StressTestInput  { factors: StressTestFactor[]; baseExposureSar?: number; }
export interface WhatIfInput      { narrative: string; assumedImpactSar?: number; degradationFactor?: number; }

export interface RiskScenario {
  id: number; scenarioCode: string; externalId?: string;
  riskId?: number; riskCode?: string; riskTitleEn?: string;
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  scenarioType: ScenarioType;
  horizonMonths: number; currency: string;
  inputJson: string; resultJson?: string;
  expectedLoss?: number; p95Loss?: number; p99Loss?: number;
  assumptionsEn?: string; assumptionsAr?: string;
  methodologyNotesEn?: string; methodologyNotesAr?: string;
  status: ScenarioStatus;
  approvedAt?: string; approverName?: string;
  rejectionReason?: string;
  archivedAt?: string; archiveReason?: string;
  createdAt: string; updatedAt: string;
}

/**
 * ⚠⚠ F2-245 — `riskCode` and `riskTitleEn` are GONE from the create request, and
 * their absence is the fix.
 *
 * The form used to collect the parent risk's code and title as free text and never
 * collected `riskId` at all — so it gathered the LABEL and never the LINK. Measured
 * in dev and prod, 11 of 11 live scenarios stored a coherent label that was not the
 * title of the risk the id named, and `riskCode` held an `RSK-2026-NNN` code space
 * that exists nowhere in `risk.risks` (whose only reference is a UUID `external_id`).
 *
 * The server now derives both from the risk it resolves, and refuses an id that does
 * not resolve inside the tenant. They stay on `RiskScenario` because the server sends
 * them back — read-only, derived, never declared here.
 */
export interface ScenarioCreateRequest {
  riskId?: number;
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  scenarioType: ScenarioType;
  horizonMonths?: number; currency?: string;
  inputJson: string; resultJson?: string;
  expectedLoss?: number; p95Loss?: number; p99Loss?: number;
  assumptionsEn?: string; assumptionsAr?: string;
  methodologyNotesEn?: string; methodologyNotesAr?: string;
}

const BASE = '/api/risk/scenarios';

export function listScenarios(status?: ScenarioStatus, page = 0, size = 50):
    Promise<{ content: RiskScenario[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}

export function scenariosByRisk(riskId: number): Promise<RiskScenario[]> {
  return api.get(`${BASE}/by-risk/${riskId}`);
}

export function getScenario(id: number): Promise<RiskScenario> {
  return api.get(`${BASE}/${id}`);
}

export function createScenario(body: ScenarioCreateRequest): Promise<RiskScenario> {
  return api.post(BASE, body);
}

export function approveScenario(id: number): Promise<RiskScenario> {
  return api.post(`${BASE}/${id}/approve`);
}

export function rejectScenario(id: number, reason: string): Promise<RiskScenario> {
  return api.post(`${BASE}/${id}/reject`, { reason });
}

export function archiveScenario(id: number, reason: string): Promise<RiskScenario> {
  return api.post(`${BASE}/${id}/archive`, { reason });
}

export function parseMonteCarloInput(json?: string): MonteCarloInput | null {
  if (!json) return null;
  try { const o = JSON.parse(json); if (typeof o.pMin !== 'number') return null; return o as MonteCarloInput; }
  catch { return null; }
}
export function parseMonteCarloResult(json?: string): MonteCarloResult | null {
  if (!json) return null;
  try {
    const o = JSON.parse(json);
    if (typeof o.expectedLoss !== 'number') return null;
    if (!Array.isArray(o.histogram)) o.histogram = [];
    return o as MonteCarloResult;
  } catch { return null; }
}

export const SCENARIO_STATUS_TONE: Record<ScenarioStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', APPROVED: 'success', REJECTED: 'danger', ARCHIVED: 'info',
};
export const SCENARIO_TYPE_LABEL_KEY: Record<ScenarioType, string> = {
  MONTE_CARLO: 'scnTypeMonteCarlo', STRESS_TEST: 'scnTypeStress', WHAT_IF: 'scnTypeWhatIf',
};

// Re-export from the central format module so every caller picks up the
// same compact-on-KPI / full-in-tables semantics. `fmtSar` was previously
// always returning the full "SAR 12,480,000" which overflowed KPI tiles;
// see lib/format.ts for the rationale.
export { formatSarCompact as fmtSar, formatSarFull as fmtSarFull } from './format';
