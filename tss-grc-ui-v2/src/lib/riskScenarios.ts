// Risk-scenario API client.
//
// Backend: /api/risk/scenarios — see RiskScenarioController.
// State machine on the server (RiskScenarioService.java):
//   DRAFT     → APPROVED (approve)
//             → REJECTED (reject — reason required)
//             → ARCHIVED (archive — reason required)
//   APPROVED  → ARCHIVED (archive — reason required)
// REJECTED and ARCHIVED are terminal; HTTP 409 + IllegalTransition.

export type ScenarioStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';
export type ScenarioType   = 'MONTE_CARLO' | 'STRESS_TEST' | 'WHAT_IF';

/** Triangular-distribution input for a Monte-Carlo run. */
export interface MonteCarloInput {
  pMin: number;     pMode: number;    pMax: number;
  iMin: number;     iMode: number;    iMax: number;
  trials: number;
}

export interface HistogramBucket { rangeStart: number; rangeEnd: number; count: number; }

/** Aggregated Monte-Carlo result — feeds the dashboard KPIs + histogram. */
export interface MonteCarloResult {
  expectedLoss: number;
  p95Loss:      number;
  p99Loss:      number;
  stdev:        number;
  histogram:    HistogramBucket[];
}

/** Stress-test deterministic input: one or more named shocks. */
export interface StressTestFactor { name: string; shockPct: number; }
export interface StressTestInput {
  factors:           StressTestFactor[];
  baseExposureSar?:  number;
}

/** What-if narrative-only input — the analyst names the assumption. */
export interface WhatIfInput {
  narrative:         string;
  assumedImpactSar?: number;
  degradationFactor?: number;
}

export interface RiskScenario {
  id:                   number;
  scenarioCode:         string;
  externalId?:          string;
  riskId?:              number;
  riskCode?:            string;
  riskTitleEn?:         string;
  titleEn:              string;
  titleAr?:             string;
  descriptionEn?:       string;
  descriptionAr?:       string;
  scenarioType:         ScenarioType;
  horizonMonths:        number;
  currency:             string;
  inputJson:            string;
  resultJson?:          string;
  expectedLoss?:        number;
  p95Loss?:             number;
  p99Loss?:             number;
  assumptionsEn?:       string;
  assumptionsAr?:       string;
  methodologyNotesEn?:  string;
  methodologyNotesAr?:  string;
  status:               ScenarioStatus;
  approvedAt?:          string;
  approverName?:        string;
  rejectionReason?:     string;
  archivedAt?:          string;
  archiveReason?:       string;
  createdAt:            string;
  updatedAt:            string;
}

export interface ScenarioCreateRequest {
  riskId?:              number;
  riskCode?:            string;
  riskTitleEn?:         string;
  titleEn:              string;
  titleAr?:             string;
  descriptionEn?:       string;
  descriptionAr?:       string;
  scenarioType:         ScenarioType;
  horizonMonths?:       number;
  currency?:            string;
  inputJson:            string;
  resultJson?:          string;
  expectedLoss?:        number;
  p95Loss?:             number;
  p99Loss?:             number;
  assumptionsEn?:       string;
  assumptionsAr?:       string;
  methodologyNotesEn?:  string;
  methodologyNotesAr?:  string;
}

const BASE = '/api/risk/scenarios';

function authHeaders(extra?: Record<string, string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return {
    ...(extra ?? {}),
    ...(tok ? { Authorization: `Bearer ${tok}` } : {}),
  };
}

export async function listScenarios(
  status?: ScenarioStatus, page = 0, size = 50,
): Promise<{ content: RiskScenario[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${BASE}?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listScenarios HTTP ${res.status}`);
  return res.json();
}

export async function scenariosByRisk(riskId: number): Promise<RiskScenario[]> {
  const res = await fetch(`${BASE}/by-risk/${riskId}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`scenariosByRisk HTTP ${res.status}`);
  return res.json();
}

export async function getScenario(id: number): Promise<RiskScenario> {
  const res = await fetch(`${BASE}/${id}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`getScenario HTTP ${res.status}`);
  return res.json();
}

export async function createScenario(body: ScenarioCreateRequest): Promise<RiskScenario> {
  const res = await fetch(BASE, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createScenario HTTP ${res.status}`);
  return res.json();
}

export async function approveScenario(id: number): Promise<RiskScenario> {
  const res = await fetch(`${BASE}/${id}/approve`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`approveScenario HTTP ${res.status}`);
  return res.json();
}

export async function rejectScenario(id: number, reason: string): Promise<RiskScenario> {
  const res = await fetch(`${BASE}/${id}/reject`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`rejectScenario HTTP ${res.status}`);
  return res.json();
}

export async function archiveScenario(id: number, reason: string): Promise<RiskScenario> {
  const res = await fetch(`${BASE}/${id}/archive`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`archiveScenario HTTP ${res.status}`);
  return res.json();
}

/** Safely parse the input_json blob into typed Monte-Carlo input. */
export function parseMonteCarloInput(json?: string): MonteCarloInput | null {
  if (!json) return null;
  try {
    const o = JSON.parse(json);
    if (typeof o.pMin !== 'number') return null;
    return o as MonteCarloInput;
  } catch { return null; }
}

/** Safely parse the result_json blob into a typed MC result. */
export function parseMonteCarloResult(json?: string): MonteCarloResult | null {
  if (!json) return null;
  try {
    const o = JSON.parse(json);
    if (typeof o.expectedLoss !== 'number') return null;
    if (!Array.isArray(o.histogram)) o.histogram = [];
    return o as MonteCarloResult;
  } catch { return null; }
}

export const SCENARIO_STATUS_TONE: Record<
  ScenarioStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'
> = {
  DRAFT:    'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  ARCHIVED: 'info',
};

export const SCENARIO_TYPE_LABEL_KEY: Record<ScenarioType, string> = {
  MONTE_CARLO: 'scnTypeMonteCarlo',
  STRESS_TEST: 'scnTypeStress',
  WHAT_IF:     'scnTypeWhatIf',
};

/** Format SAR amount with thousand separators and the currency symbol. */
export function fmtSar(amount?: number): string {
  if (amount == null) return '—';
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'SAR', maximumFractionDigits: 0 })
    .format(amount);
}
