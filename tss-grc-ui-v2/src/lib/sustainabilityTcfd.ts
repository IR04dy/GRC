// TCFD alignment API client (Sustainability · V61).
//
// Wraps /api/sustainability/tcfd/*. Promotes the TcfdTab from the
// static TCFD_ITEMS constant. Shares the disclosure-status set with
// CMA (V60) so a single mental model + UI pattern serves both.
//
// Rule 7 surface — when a STRATEGY-pillar resilience recommendation
// (recommendation_code='c') is PARTIAL or NOT_DISCLOSED, readiness()
// returns strategyResilienceGap=true + the offending item's id.

import { api } from './api';

export type TcfdStatus =
  | 'DISCLOSED' | 'PARTIAL' | 'NOT_DISCLOSED' | 'NOT_APPLICABLE'
  | 'UNDER_REVIEW' | 'EVIDENCE_REQUIRED';

export type TcfdPillar = 'GOVERNANCE' | 'STRATEGY' | 'RISK_MGMT' | 'METRICS_TARGETS';

export interface TcfdItem {
  id?: number;
  organisationId?: number;
  reportingYear: number;
  pillar: TcfdPillar;
  recommendationCode: string;

  titleEn: string;
  titleAr?: string;
  recommendationEn?: string;
  recommendationAr?: string;

  disclosureStatus: TcfdStatus;
  evidenceRef?: string;
  evidenceOwner?: string;

  gapDescriptionEn?: string;
  gapDescriptionAr?: string;

  relatedClimateRiskId?: number;
  relatedMetricId?: number;
  linkedCapaId?: number;

  ownerName?: string;
  ownerEmail?: string;
  reviewerName?: string;
  lastReviewedAt?: string;
  approvalStatus?: 'PENDING' | 'APPROVED' | 'REJECTED';
  approvedBy?: string;
  approvedAt?: string;

  notes?: string;
}

export interface TcfdReadiness {
  year: number;
  total: number;
  applicable: number;
  byStatus: Record<TcfdStatus, number>;
  byPillar: Record<TcfdPillar, number>;
  disclosedByPillar: Record<TcfdPillar, number>;
  withEvidence: number;
  withGap: number;
  alignmentPct: number;
  /** Rule 7 — true when a Strategy-pillar resilience recommendation is
   *  Partial / Not Disclosed / Evidence Required. */
  strategyResilienceGap: boolean;
  strategyResilienceItemId: number | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/tcfd';

export function listTcfd(opts: { year?: number; pillar?: TcfdPillar; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  p.set('year', String(opts.year ?? 2025));
  if (opts.pillar) p.set('pillar', opts.pillar);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 50));
  return api.get<Page<TcfdItem>>(`${BASE}?${p.toString()}`);
}

export function tcfdReadiness(year = 2025) {
  return api.get<TcfdReadiness>(`${BASE}/readiness?year=${year}`);
}

export function getTcfd(id: number)        { return api.get<TcfdItem>(`${BASE}/${id}`); }
export function createTcfd(b: Partial<TcfdItem>) { return api.post<TcfdItem>(BASE, b); }
export function updateTcfd(id: number, b: Partial<TcfdItem>) { return api.put<TcfdItem>(`${BASE}/${id}`, b); }

export function tcfdMarkDisclosed(id: number)    { return api.post<TcfdItem>(`${BASE}/${id}/mark-disclosed`); }
export function tcfdMarkPartial(id: number)       { return api.post<TcfdItem>(`${BASE}/${id}/mark-partial`); }
export function tcfdMarkNotDisclosed(id: number)  { return api.post<TcfdItem>(`${BASE}/${id}/mark-not-disclosed`); }
export function tcfdMarkNotApplicable(id: number) { return api.post<TcfdItem>(`${BASE}/${id}/mark-not-applicable`); }
export function tcfdMarkUnderReview(id: number)   { return api.post<TcfdItem>(`${BASE}/${id}/mark-under-review`); }

export function tcfdAttachEvidence(id: number, evidenceRef: string, owner?: string) {
  return api.post<TcfdItem>(`${BASE}/${id}/attach-evidence`, { evidenceRef, owner });
}
export function tcfdRecordGap(id: number, gapEn?: string, gapAr?: string) {
  return api.post<TcfdItem>(`${BASE}/${id}/record-gap`, { gapEn, gapAr });
}
export function tcfdLinkClimateRisk(id: number, climateRiskId: number) {
  return api.post<TcfdItem>(`${BASE}/${id}/link-climate-risk`, { climateRiskId });
}

export function deleteTcfd(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function tcfdStatusTone(s: TcfdStatus):
    'success' | 'warning' | 'danger' | 'neutral' | 'info' {
  switch (s) {
    case 'DISCLOSED':         return 'success';
    case 'PARTIAL':           return 'warning';
    case 'NOT_DISCLOSED':     return 'danger';
    case 'NOT_APPLICABLE':    return 'neutral';
    case 'UNDER_REVIEW':      return 'info';
    case 'EVIDENCE_REQUIRED': return 'danger';
  }
}

export function tcfdStatusLabel(s: TcfdStatus, lang: 'en' | 'ar'): string {
  const en: Record<TcfdStatus, string> = {
    DISCLOSED: 'Disclosed', PARTIAL: 'Partial', NOT_DISCLOSED: 'Not disclosed',
    NOT_APPLICABLE: 'Not applicable', UNDER_REVIEW: 'Under review',
    EVIDENCE_REQUIRED: 'Evidence required',
  };
  const ar: Record<TcfdStatus, string> = {
    DISCLOSED: 'مُفصَح', PARTIAL: 'جزئي', NOT_DISCLOSED: 'غير مُفصَح',
    NOT_APPLICABLE: 'غير قابل للتطبيق', UNDER_REVIEW: 'قيد المراجعة',
    EVIDENCE_REQUIRED: 'يتطلّب دليلاً',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function tcfdPillarLabel(p: TcfdPillar, lang: 'en' | 'ar'): string {
  const en: Record<TcfdPillar, string> = {
    GOVERNANCE: 'Governance', STRATEGY: 'Strategy',
    RISK_MGMT: 'Risk management', METRICS_TARGETS: 'Metrics & targets',
  };
  const ar: Record<TcfdPillar, string> = {
    GOVERNANCE: 'الحوكمة', STRATEGY: 'الاستراتيجية',
    RISK_MGMT: 'إدارة المخاطر', METRICS_TARGETS: 'المقاييس والأهداف',
  };
  return (lang === 'ar' ? ar : en)[p];
}

export function hasTcfdGap(t: TcfdItem): boolean {
  return !!(t.gapDescriptionEn && t.gapDescriptionEn.trim())
      || !!(t.gapDescriptionAr && t.gapDescriptionAr.trim());
}

export function hasTcfdEvidence(t: TcfdItem): boolean {
  return !!(t.evidenceRef && t.evidenceRef.trim());
}
