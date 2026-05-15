// Saudi Green Initiative API client (Sustainability · V59).
//
// Wraps /api/sustainability/sgi-commitments/*. Powers the promoted SGI
// tab on /esg — replaces the static SGI_COMMITMENTS constant in esg.ts.
//
// Status machine: ACTIVE → ACHIEVED | RETIRED (both terminal).
//
// Workflow rules (mirrored client-side for immediate feedback; final
// enforcement is in the service):
//   • Rule A — ACHIEVED requires current_value to meet target within
//     the commitment's direction (raise-to vs lower-to).
//   • Rule B — RETIRED requires a written reason.

import { api } from './api';

export type SgiStatus = 'ACTIVE' | 'ACHIEVED' | 'RETIRED';

export type SgiSector =
  | 'ENERGY' | 'INDUSTRY' | 'TRANSPORT' | 'WASTE'
  | 'FORESTRY' | 'WATER' | 'LAND' | 'OTHER';

export type SgiTier = 'ON_TRACK' | 'AT_RISK' | 'DELAYED' | 'ACHIEVED' | 'RETIRED';

export interface SgiCommitment {
  id?: number;
  organisationId?: number;
  commitmentCode: string;
  sector: SgiSector;

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  baselineValue?: number;
  baselineYear?: number;
  currentValue?: number;
  currentValueAsOf?: string;     // yyyy-MM-dd
  targetValue: number;
  targetYear: number;
  unit: string;

  status: SgiStatus;

  ownerName?: string;
  ownerEmail?: string;
  nextMilestoneDate?: string;
  nextMilestoneEn?: string;
  nextMilestoneAr?: string;

  notesEn?: string;
  notesAr?: string;
  evidenceRef?: string;

  achievedAt?: string;
  achievedBy?: string;
  retiredAt?: string;
  retiredBy?: string;
  retiredReason?: string;
}

export interface SgiProgress {
  progressPct: number;
  expectedPct: number;
  tier: SgiTier;
  direction: 'INCREASE' | 'DECREASE';
}

export interface SgiAggregate {
  activeTotal: number;
  achievedTotal: number;
  retiredTotal: number;
  byTier: Record<SgiTier, number>;
  bySector: Record<string, number>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/sgi-commitments';

export function listSgi(opts: {
  status?: SgiStatus; sector?: SgiSector;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  if (opts.status) p.set('status', opts.status);
  if (opts.sector) p.set('sector', opts.sector);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<SgiCommitment>>(`${BASE}?${p.toString()}`);
}

export function sgiAggregate() {
  return api.get<SgiAggregate>(`${BASE}/aggregate`);
}

export function getSgi(id: number) {
  return api.get<SgiCommitment>(`${BASE}/${id}`);
}

export function getSgiByCode(code: string) {
  return api.get<SgiCommitment>(`${BASE}/by-code/${encodeURIComponent(code)}`);
}

export function getSgiProgress(id: number) {
  return api.get<SgiProgress>(`${BASE}/${id}/progress`);
}

export function createSgi(body: Partial<SgiCommitment>) {
  return api.post<SgiCommitment>(BASE, body);
}

export function updateSgi(id: number, body: Partial<SgiCommitment>) {
  return api.put<SgiCommitment>(`${BASE}/${id}`, body);
}

export function achieveSgi(id: number, achievedBy: string) {
  return api.post<SgiCommitment>(`${BASE}/${id}/achieve`, { achievedBy });
}

export function retireSgi(id: number, retiredBy: string, reason: string) {
  return api.post<SgiCommitment>(`${BASE}/${id}/retire`, { retiredBy, reason });
}

export function deleteSgi(id: number) {
  return api.delete<void>(`${BASE}/${id}`);
}

// ─── UI helpers ───────────────────────────────────────────────────────

/** Tone for a status badge. */
export function sgiStatusTone(s: SgiStatus): 'success' | 'neutral' | 'primary' {
  switch (s) {
    case 'ACHIEVED': return 'success';
    case 'RETIRED':  return 'neutral';
    case 'ACTIVE':   return 'primary';
  }
}

/** Tone for the computed progress tier. */
export function sgiTierTone(t: SgiTier):
    'success' | 'warning' | 'danger' | 'neutral' {
  switch (t) {
    case 'ON_TRACK': return 'success';
    case 'AT_RISK':  return 'warning';
    case 'DELAYED':  return 'danger';
    case 'ACHIEVED': return 'success';
    case 'RETIRED':  return 'neutral';
  }
}

/** Bilingual sector labels. */
export function sgiSectorLabel(s: SgiSector, lang: 'en' | 'ar'): string {
  const en: Record<SgiSector, string> = {
    ENERGY:    'Energy',
    INDUSTRY:  'Industry',
    TRANSPORT: 'Transport',
    WASTE:     'Waste',
    FORESTRY:  'Forestry',
    WATER:     'Water',
    LAND:      'Land use',
    OTHER:     'Other',
  };
  const ar: Record<SgiSector, string> = {
    ENERGY:    'الطاقة',
    INDUSTRY:  'الصناعة',
    TRANSPORT: 'النقل',
    WASTE:     'النفايات',
    FORESTRY:  'الغابات',
    WATER:     'المياه',
    LAND:      'استخدام الأرض',
    OTHER:     'أخرى',
  };
  return (lang === 'ar' ? ar : en)[s];
}

/** Bilingual tier labels. */
export function sgiTierLabel(t: SgiTier, lang: 'en' | 'ar'): string {
  const en: Record<SgiTier, string> = {
    ON_TRACK: 'On track',
    AT_RISK:  'At risk',
    DELAYED:  'Delayed',
    ACHIEVED: 'Achieved',
    RETIRED:  'Retired',
  };
  const ar: Record<SgiTier, string> = {
    ON_TRACK: 'على المسار',
    AT_RISK:  'مُعرَّض للخطر',
    DELAYED:  'متأخّر',
    ACHIEVED: 'مُنجَز',
    RETIRED:  'مُتقاعَد',
  };
  return (lang === 'ar' ? ar : en)[t];
}

export function sgiStatusLabel(s: SgiStatus, lang: 'en' | 'ar'): string {
  const en: Record<SgiStatus, string> = {
    ACTIVE: 'Active', ACHIEVED: 'Achieved', RETIRED: 'Retired',
  };
  const ar: Record<SgiStatus, string> = {
    ACTIVE: 'نشط', ACHIEVED: 'مُنجَز', RETIRED: 'مُتقاعَد',
  };
  return (lang === 'ar' ? ar : en)[s];
}

/**
 * Compute progress percentage client-side — same algorithm as the
 * service. Used by the tab to render progress bars without hitting
 * /progress for every row.
 */
export function computeProgressPct(c: SgiCommitment): number {
  const baseline = c.baselineValue ?? 0;
  const current = c.currentValue ?? 0;
  const target = c.targetValue;
  if (target === baseline) return current === target ? 100 : 0;
  const num = target > baseline
    ? current - baseline           // increase-to
    : baseline - current;          // decrease-to
  const den = target > baseline
    ? target - baseline
    : baseline - target;
  if (den === 0) return 0;
  const pct = (num / den) * 100;
  return Math.max(0, Math.min(150, Math.round(pct * 10) / 10));
}

/** Rule-A client mirror — disables Achieve until the current value
 *  actually meets the target within direction. */
export function canAchieve(c: SgiCommitment): boolean {
  if (c.status !== 'ACTIVE') return false;
  if (c.currentValue == null || c.targetValue == null) return false;
  const dir = c.targetValue >= (c.baselineValue ?? 0) ? 'INCREASE' : 'DECREASE';
  return dir === 'INCREASE'
    ? c.currentValue >= c.targetValue
    : c.currentValue <= c.targetValue;
}
