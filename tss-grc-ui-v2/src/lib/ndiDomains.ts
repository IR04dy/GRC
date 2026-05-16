// NDI Data Governance Domains API client (slice 6 · V71).
//
// Master catalog of governance domains + cross-slice rollup that
// combines maturity / compliance / opex scores into a single per-domain
// (and overall) NDI index. All domains seeded by V71 are placeholders
// pending an official SDAIA/NDI domain library import.

import { api } from './api';

export interface NdiGovernanceDomain {
  id?: number;
  organisationId?: number;

  code: string;
  nameEn: string;
  nameAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  /** Pipe-separated list of upstream domain / category strings matched to this domain. */
  domainAliases?: string;

  weight: number;
  sortOrder: number;

  weightMaturity: number;
  weightCompliance: number;
  weightOpex: number;

  ownerName?: string;
  ownerEmail?: string;

  isActive: boolean;
  isPlaceholder: boolean;

  notes?: string;
}

export interface DomainRollup {
  domainId: number;
  code: string;
  nameEn: string;
  nameAr?: string;
  weight: number;
  isPlaceholder: boolean;

  maturityCount: number;
  maturityAvgLevel?: number;
  maturityScorePct?: number;

  complianceCount: number;
  compliancePct?: number;

  opexCount: number;
  opexScorePct?: number;

  blendedScorePct?: number;

  /** Only the first record carries the global list of upstream domain strings with no mapping. */
  unmatchedDomains: string[];
}

export interface CycleRollup {
  cycleId: number;
  overallNdiPct: number;
  totalWeight: number;
  domains: DomainRollup[];
}

const BASE = '/api/ndi/domains';

export function listDomains(activeOnly = false) {
  return api.get<NdiGovernanceDomain[]>(`${BASE}?activeOnly=${activeOnly}`);
}

export function getDomain(id: number) {
  return api.get<NdiGovernanceDomain>(`${BASE}/${id}`);
}

export function createDomain(body: Partial<NdiGovernanceDomain>) {
  return api.post<NdiGovernanceDomain>(BASE, body);
}

export function updateDomain(id: number, body: Partial<NdiGovernanceDomain>) {
  return api.put<NdiGovernanceDomain>(`${BASE}/${id}`, body);
}

export function deleteDomain(id: number) {
  return api.delete<void>(`${BASE}/${id}`);
}

export function markDomainOfficial(id: number) {
  return api.post<NdiGovernanceDomain>(`${BASE}/${id}/mark-official`);
}

export function markDomainPlaceholder(id: number) {
  return api.post<NdiGovernanceDomain>(`${BASE}/${id}/mark-placeholder`);
}

export function rollupForCycle(cycleId: number) {
  return api.get<CycleRollup>(`${BASE}/rollup?cycleId=${cycleId}`);
}

// ─── UI helpers ───────────────────────────────────────────────────────

export function ndiScoreTone(pct: number | null | undefined):
    'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  if (pct == null) return 'neutral';
  if (pct >= 80) return 'success';
  if (pct >= 60) return 'info';
  if (pct >= 40) return 'warning';
  return 'danger';
}

export function ndiScoreLabel(pct: number | null | undefined, lang: 'en' | 'ar'): string {
  if (pct == null) return lang === 'ar' ? 'بدون بيانات' : 'No data';
  if (pct >= 80) return lang === 'ar' ? 'متقدّم'  : 'Advanced';
  if (pct >= 60) return lang === 'ar' ? 'مستقر'   : 'On track';
  if (pct >= 40) return lang === 'ar' ? 'يحتاج تحسين' : 'Needs work';
  return lang === 'ar' ? 'منخفض' : 'Lagging';
}

export function parseAliases(aliases?: string): string[] {
  if (!aliases) return [];
  return aliases.split('|').map(s => s.trim()).filter(Boolean);
}

export function joinAliases(aliases: string[]): string {
  return aliases.map(a => a.trim()).filter(Boolean).join('|');
}
