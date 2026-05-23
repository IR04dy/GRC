// Continuity Strategy ROI client — Slice 9-D.
//
// Typed wrappers around /api/v1/bcm/roi/analyses/* plus a handful of
// helpers (recommendation tone, strategy-type labels, ROI bar colour)
// that the page reads. Mirrors the shape used by ../lib/biaQuantitative
// — keep the two aligned so a developer can switch between them without
// learning a new convention.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

export type StrategyType =
  | 'ACTIVE_ACTIVE'
  | 'WARM_DR'
  | 'COLD_DR'
  | 'VENDOR_FAILOVER'
  | 'MANUAL_WORKAROUND'
  | 'INSURANCE_TRANSFER';

export type Recommendation =
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'DEFERRED';

export interface StrategyRoiAnalysis {
  id: number;
  externalId: string;
  organisationId: number;
  analysisCode: string;
  biaQuantitativeId: number;
  strategyId?: number;
  strategyNameEn: string;
  strategyNameAr: string;
  strategyType: StrategyType;
  planningHorizonYears: number;
  capexSar: number | string;
  opexAnnualSar: number | string;
  expectedRtoHours: number;
  expectedIncidentsPerYear: number | string;
  avoidableLossPerIncidentSar: number | string;
  totalAvoidableLossSar: number | string;
  totalCostSar: number | string;
  roiPercent: number | string;
  paybackPeriodMonths?: number | string | null;
  npvSar?: number | string | null;
  discountRatePercent: number | string;
  recommendation: Recommendation;
  recommendationRationaleEn?: string;
  recommendationRationaleAr?: string;
  decidedByUserId?: number;
  decidedByName?: string;
  decidedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface StrategyRoiRequest {
  analysisCode?: string;
  biaQuantitativeId: number;
  strategyId?: number;
  strategyNameEn: string;
  strategyNameAr: string;
  strategyType: StrategyType;
  planningHorizonYears: number;
  capexSar: number | string;
  opexAnnualSar: number | string;
  expectedRtoHours: number;
  expectedIncidentsPerYear?: number | string;
  discountRatePercent?: number | string;
}

export interface RecommendationRequest {
  recommendation: Recommendation;
  decidedByName?: string;
  decidedByUserId?: number;
  recommendationRationaleEn?: string;
  recommendationRationaleAr?: string;
}

export interface StrategyRoiComparison {
  biaQuantitativeId: number;
  analyses: StrategyRoiAnalysis[];
}

// ─── Static catalogues ────────────────────────────────────────────────

export const STRATEGY_TYPES: StrategyType[] = [
  'ACTIVE_ACTIVE', 'WARM_DR', 'COLD_DR',
  'VENDOR_FAILOVER', 'MANUAL_WORKAROUND', 'INSURANCE_TRANSFER',
];

export const STRATEGY_TYPE_LABELS: Record<StrategyType, { en: string; ar: string }> = {
  ACTIVE_ACTIVE:      { en: 'Active-active',     ar: 'نشط-نشط' },
  WARM_DR:            { en: 'Warm DR site',      ar: 'موقع تعافٍ دافئ' },
  COLD_DR:            { en: 'Cold DR site',      ar: 'موقع تعافٍ بارد' },
  VENDOR_FAILOVER:    { en: 'Vendor failover',   ar: 'تحويل إلى مورّد بديل' },
  MANUAL_WORKAROUND:  { en: 'Manual workaround', ar: 'إجراء يدوي بديل' },
  INSURANCE_TRANSFER: { en: 'Insurance transfer', ar: 'تحويل عبر التأمين' },
};

export const RECOMMENDATION_LABELS: Record<Recommendation, { en: string; ar: string }> = {
  PENDING_REVIEW: { en: 'Pending review', ar: 'بانتظار المراجعة' },
  APPROVED:       { en: 'Approved',       ar: 'مُعتمَد' },
  REJECTED:       { en: 'Rejected',       ar: 'مرفوض' },
  DEFERRED:       { en: 'Deferred',       ar: 'مُؤجَّل' },
};

export function recommendationTone(r: Recommendation): BadgeTone {
  switch (r) {
    case 'APPROVED':       return 'success';
    case 'DEFERRED':       return 'warning';
    case 'REJECTED':       return 'danger';
    case 'PENDING_REVIEW': return 'info';
  }
}

/** Bar-chart fill colour for the ROI bar (matches the recommendation badge). */
export function recommendationBarColor(r: Recommendation): string {
  switch (r) {
    case 'APPROVED':       return '#16a34a';   // green-600
    case 'DEFERRED':       return '#d97706';   // amber-600
    case 'REJECTED':       return '#dc2626';   // red-600
    case 'PENDING_REVIEW': return '#2563eb';   // blue-600
  }
}

/** Format a SAR amount compactly (e.g. SAR 12.5M / SAR 940K). */
export function fmtSar(amount?: number | string | null, isAr = false): string {
  if (amount === null || amount === undefined || amount === '') return '—';
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (Number.isNaN(n)) return '—';
  const sym = isAr ? 'ر.س' : 'SAR';
  const abs = Math.abs(n);
  if (abs >= 1_000_000_000) return `${sym} ${(n / 1_000_000_000).toFixed(2)}B`;
  if (abs >= 1_000_000)     return `${sym} ${(n / 1_000_000).toFixed(2)}M`;
  if (abs >= 1_000)         return `${sym} ${(n / 1_000).toFixed(1)}K`;
  return `${sym} ${n.toFixed(0)}`;
}

/** Format a ROI percentage (e.g. "108.87%"). */
export function fmtPercent(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(n)) return '—';
  return `${n.toFixed(2)}%`;
}

/** Format a payback period in months (or "—" when null = no payback within horizon). */
export function fmtPaybackMonths(months?: number | string | null, isAr = false): string {
  if (months === null || months === undefined || months === '') {
    return isAr ? 'لا استرداد ضمن المدى' : 'No payback in horizon';
  }
  const n = typeof months === 'string' ? Number(months) : months;
  if (Number.isNaN(n)) return '—';
  if (n < 12) return isAr ? `${n.toFixed(1)} شهر` : `${n.toFixed(1)} mo`;
  const years = n / 12;
  return isAr ? `${years.toFixed(1)} سنة` : `${years.toFixed(1)} yr`;
}

// ─── API calls ────────────────────────────────────────────────────────

const BASE = '/api/v1/bcm/roi/analyses';

export function listStrategyRoi(biaId?: number): Promise<StrategyRoiAnalysis[]> {
  const url = biaId != null ? `${BASE}?biaId=${biaId}` : BASE;
  return api.get<StrategyRoiAnalysis[]>(url);
}

export function getStrategyRoi(externalId: string): Promise<StrategyRoiAnalysis> {
  return api.get<StrategyRoiAnalysis>(`${BASE}/${externalId}`);
}

export function createStrategyRoi(body: StrategyRoiRequest): Promise<StrategyRoiAnalysis> {
  return api.post<StrategyRoiAnalysis>(BASE, body);
}

export function updateStrategyRoi(
  externalId: string, body: StrategyRoiRequest,
): Promise<StrategyRoiAnalysis> {
  return api.put<StrategyRoiAnalysis>(`${BASE}/${externalId}`, body);
}

export function recommendStrategyRoi(
  externalId: string, body: RecommendationRequest,
): Promise<StrategyRoiAnalysis> {
  return api.post<StrategyRoiAnalysis>(`${BASE}/${externalId}/recommend`, body);
}

export function archiveStrategyRoi(externalId: string): Promise<StrategyRoiAnalysis> {
  return api.delete<StrategyRoiAnalysis>(`${BASE}/${externalId}`);
}

export function compareStrategyRoi(ids: number[]): Promise<StrategyRoiComparison> {
  return api.get<StrategyRoiComparison>(`${BASE}/comparison?ids=${ids.join(',')}`);
}
