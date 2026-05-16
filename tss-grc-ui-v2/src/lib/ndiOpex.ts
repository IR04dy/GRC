// NDI Operational Excellence KPI API client (slice 5 · V70).
//
// Per-cycle KPI register. Status (Rule 13) is computed server-side
// from actual vs target with direction-awareness — clients shouldn't
// set status directly; just push the actual value via update-actual.

import { api } from './api';

export type NdiKpiStatus =
  | 'ON_TRACK' | 'AT_RISK' | 'BEHIND_PLAN' | 'ACHIEVED'
  | 'DATA_MISSING' | 'NOT_STARTED';

export type NdiKpiFrequency = 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUAL' | 'ANNUAL';

export interface NdiOpexKpi {
  id?: number;
  organisationId?: number;
  cycleId: number;
  kpiCode?: string;
  category: string;

  indicatorNameEn: string;
  indicatorNameAr?: string;
  definitionEn?: string;
  definitionAr?: string;
  formula?: string;
  dataSource?: string;

  baseline?: number;
  target: number;
  actual?: number;
  unit?: string;
  frequency?: NdiKpiFrequency;

  ownerName?: string;
  ownerEmail?: string;

  status: NdiKpiStatus;
  trend?: 'IMPROVING' | 'FLAT' | 'DECLINING';
  lastActualAt?: string;

  evidenceRef?: string;
  notes?: string;
}

export interface NdiOpexAggregate {
  cycleId: number;
  total: number;
  opexScorePct: number;
  byStatus: Record<NdiKpiStatus, number>;
  byCategory: Record<string, number>;
  behindPlanCount: number;
  atRiskCount: number;
  achievedCount: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/opex';

export function listOpex(cycleId: number, opts: { status?: NdiKpiStatus; category?: string; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  p.set('cycleId', String(cycleId));
  if (opts.status)   p.set('status', opts.status);
  if (opts.category) p.set('category', opts.category);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<Page<NdiOpexKpi>>(`${BASE}?${p.toString()}`);
}

export function opexAggregate(cycleId: number) {
  return api.get<NdiOpexAggregate>(`${BASE}/aggregate?cycleId=${cycleId}`);
}

export function getOpex(id: number) { return api.get<NdiOpexKpi>(`${BASE}/${id}`); }

export function createOpex(body: Partial<NdiOpexKpi>) {
  return api.post<NdiOpexKpi>(BASE, body);
}

export function updateOpex(id: number, body: Partial<NdiOpexKpi>) {
  return api.put<NdiOpexKpi>(`${BASE}/${id}`, body);
}

export function updateOpexActual(id: number, actual: number, asOf?: string) {
  return api.post<NdiOpexKpi>(`${BASE}/${id}/update-actual`, { actual, asOf });
}

export function deleteOpex(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function kpiStatusTone(s: NdiKpiStatus):
    'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'ACHIEVED':     return 'success';
    case 'ON_TRACK':     return 'success';
    case 'AT_RISK':      return 'warning';
    case 'BEHIND_PLAN':  return 'danger';
    case 'DATA_MISSING': return 'danger';
    case 'NOT_STARTED':  return 'neutral';
  }
}

export function kpiStatusLabel(s: NdiKpiStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiKpiStatus, string> = {
    ON_TRACK: 'On track', AT_RISK: 'At risk', BEHIND_PLAN: 'Behind plan',
    ACHIEVED: 'Achieved', DATA_MISSING: 'Data missing', NOT_STARTED: 'Not started',
  };
  const ar: Record<NdiKpiStatus, string> = {
    ON_TRACK: 'على المسار', AT_RISK: 'مُعرَّض', BEHIND_PLAN: 'متأخّر',
    ACHIEVED: 'مُحقَّق', DATA_MISSING: 'بيانات مفقودة', NOT_STARTED: 'لم يبدأ',
  };
  return (lang === 'ar' ? ar : en)[s];
}

/** Direction-aware progress fraction 0..1 for the progress bar. */
export function progressFraction(k: NdiOpexKpi): number {
  if (k.actual == null) return 0;
  const baseline = k.baseline ?? 0;
  const denom = k.target - baseline;
  if (denom === 0) return k.actual === k.target ? 1 : 0;
  const raw = (k.actual - baseline) / denom;
  return Math.max(0, Math.min(1, raw));
}
