// ESG Metrics API client (Sustainability · V63 · slice 9).
//
// Unified register for Environmental / Social / Governance metrics.
// Same direction-aware progress math as SGI commitments: handles both
// raise-to-target ("renewable %") and lower-to-target ("LTIFR").

import { api } from './api';

export type MetricCategory = 'ENVIRONMENTAL' | 'SOCIAL' | 'GOVERNANCE';
export type MetricStatus = 'ACTIVE' | 'AT_TARGET' | 'OFF_TRACK' | 'ARCHIVED';

export interface EsgMetric {
  id?: number;
  organisationId?: number;
  metricCode?: string;
  reportingYear: number;
  category: MetricCategory;
  subcategory?: string;

  metricNameEn: string;
  metricNameAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  unit: string;

  baselineValue?: number;
  baselineYear?: number;
  currentValue?: number;
  currentValueAsOf?: string;
  targetValue?: number;
  targetYear?: number;

  status: MetricStatus;

  ownerName?: string;
  ownerEmail?: string;
  responsibleDepartment?: string;

  linkedSgiCommitmentId?: number;
  evidenceRef?: string;
  notes?: string;
}

export interface MetricAggregate {
  year: number;
  total: number;
  byCategory: Record<MetricCategory, number>;
  atTargetByCategory: Record<MetricCategory, number>;
  offTrackByCategory: Record<MetricCategory, number>;
  meanProgressByCategory: Record<MetricCategory, number>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/metrics';

export function listMetrics(opts: {
  year?: number; category?: MetricCategory;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  p.set('year', String(opts.year ?? 2025));
  if (opts.category) p.set('category', opts.category);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<EsgMetric>>(`${BASE}?${p.toString()}`);
}

export function metricsAggregate(year = 2025) {
  return api.get<MetricAggregate>(`${BASE}/aggregate?year=${year}`);
}

export function getMetric(id: number)  { return api.get<EsgMetric>(`${BASE}/${id}`); }
export function createMetric(b: Partial<EsgMetric>) { return api.post<EsgMetric>(BASE, b); }
export function updateMetric(id: number, b: Partial<EsgMetric>) { return api.put<EsgMetric>(`${BASE}/${id}`, b); }

export function recordMetricReading(id: number, currentValue: number, asOf?: string) {
  return api.post<EsgMetric>(`${BASE}/${id}/record-reading`, { currentValue, asOf });
}
export function archiveMetric(id: number) { return api.post<EsgMetric>(`${BASE}/${id}/archive`); }
export function deleteMetric(id: number)  { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function metricStatusTone(s: MetricStatus):
    'success' | 'info' | 'danger' | 'neutral' {
  switch (s) {
    case 'AT_TARGET': return 'success';
    case 'ACTIVE':    return 'info';
    case 'OFF_TRACK': return 'danger';
    case 'ARCHIVED':  return 'neutral';
  }
}

export function metricStatusLabel(s: MetricStatus, lang: 'en' | 'ar'): string {
  const en: Record<MetricStatus, string> = {
    ACTIVE: 'On track', AT_TARGET: 'At target', OFF_TRACK: 'Off track', ARCHIVED: 'Archived',
  };
  const ar: Record<MetricStatus, string> = {
    ACTIVE: 'على المسار', AT_TARGET: 'بلغ الهدف', OFF_TRACK: 'خارج المسار', ARCHIVED: 'مؤرشف',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function metricCategoryLabel(c: MetricCategory, lang: 'en' | 'ar'): string {
  const en: Record<MetricCategory, string> = {
    ENVIRONMENTAL: 'Environmental', SOCIAL: 'Social', GOVERNANCE: 'Governance',
  };
  const ar: Record<MetricCategory, string> = {
    ENVIRONMENTAL: 'البيئة', SOCIAL: 'الاجتماع', GOVERNANCE: 'الحوكمة',
  };
  return (lang === 'ar' ? ar : en)[c];
}

/** Direction-aware client-side progress mirror. */
export function computeMetricProgress(m: EsgMetric): number {
  const baseline = m.baselineValue ?? 0;
  const current = m.currentValue ?? 0;
  const target = m.targetValue ?? 0;
  if (target === baseline) return current === target ? 100 : 0;
  const num = target > baseline ? current - baseline : baseline - current;
  const den = target > baseline ? target - baseline : baseline - target;
  if (den === 0) return 0;
  const pct = (num / den) * 100;
  return Math.max(0, Math.min(150, Math.round(pct * 10) / 10));
}
