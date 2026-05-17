// CMA Articles 91–94 disclosure API client (Sustainability · V60).
//
// Wraps /api/sustainability/cma-disclosures/*. Powers the promoted
// CmaTab on /esg — replaces the static CMA_ARTICLES constant.
//
// Workflow rules surfaced for client-side mirroring (final enforcement
// is in the service):
//   • Rule 5 — PARTIAL or NOT_DISCLOSED requires a gap description.
//   • Rule 6 — DISCLOSED/PARTIAL with empty evidenceRef is auto-tagged
//     EVIDENCE_REQUIRED on save.

import { api } from './api';

export type CmaStatus =
  | 'DISCLOSED' | 'PARTIAL' | 'NOT_DISCLOSED' | 'NOT_APPLICABLE'
  | 'UNDER_REVIEW' | 'EVIDENCE_REQUIRED';

export type CmaApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface CmaDisclosure {
  id?: number;
  organisationId?: number;

  reportingYear: number;
  article: '91' | '92' | '93' | '94';
  clause?: string;

  titleEn: string;
  titleAr?: string;
  requirementEn?: string;
  requirementAr?: string;

  disclosureStatus: CmaStatus;

  evidenceRef?: string;
  evidenceOwner?: string;
  responsibleDepartment?: string;

  gapDescriptionEn?: string;
  gapDescriptionAr?: string;

  linkedCapaId?: number;
  reportSectionReference?: string;

  reviewerName?: string;
  reviewerEmail?: string;
  lastReviewedAt?: string;
  approvalStatus?: CmaApprovalStatus;
  approvedBy?: string;
  approvedAt?: string;

  notes?: string;
}

export interface CmaReadiness {
  year: number;
  total: number;
  applicable: number;
  byStatus: Record<CmaStatus, number>;
  withEvidence: number;
  withGap: number;
  readinessPct: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/cma-disclosures';

export function listCma(opts: {
  year?: number; status?: CmaStatus;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  p.set('year', String(opts.year ?? 2025));
  if (opts.status) p.set('status', opts.status);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 50));
  return api.get<Page<CmaDisclosure>>(`${BASE}?${p.toString()}`);
}

export function cmaReadiness(year = 2025) {
  return api.get<CmaReadiness>(`${BASE}/readiness?year=${year}`);
}

export function getCma(id: number) { return api.get<CmaDisclosure>(`${BASE}/${id}`); }

export function createCma(body: Partial<CmaDisclosure>) {
  return api.post<CmaDisclosure>(BASE, body);
}
export function updateCma(id: number, body: Partial<CmaDisclosure>) {
  return api.put<CmaDisclosure>(`${BASE}/${id}`, body);
}

// Status transitions ─────────────────────────────────────────────────
export function markDisclosed(id: number)     { return api.post<CmaDisclosure>(`${BASE}/${id}/mark-disclosed`); }
export function markPartial(id: number)        { return api.post<CmaDisclosure>(`${BASE}/${id}/mark-partial`); }
export function markNotDisclosed(id: number)   { return api.post<CmaDisclosure>(`${BASE}/${id}/mark-not-disclosed`); }
export function markNotApplicable(id: number)  { return api.post<CmaDisclosure>(`${BASE}/${id}/mark-not-applicable`); }
export function markUnderReview(id: number)    { return api.post<CmaDisclosure>(`${BASE}/${id}/mark-under-review`); }

export function attachCmaEvidence(id: number, evidenceRef: string, owner?: string) {
  return api.post<CmaDisclosure>(`${BASE}/${id}/attach-evidence`, { evidenceRef, owner });
}

export function recordCmaGap(id: number, gapEn?: string, gapAr?: string) {
  return api.post<CmaDisclosure>(`${BASE}/${id}/record-gap`, { gapEn, gapAr });
}

export function approveCma(id: number, approvedBy: string) {
  return api.post<CmaDisclosure>(`${BASE}/${id}/approve`, { approvedBy });
}

export function deleteCma(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function cmaStatusTone(s: CmaStatus):
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

export function cmaStatusLabel(s: CmaStatus, lang: 'en' | 'ar'): string {
  const en: Record<CmaStatus, string> = {
    DISCLOSED:         'Disclosed',
    PARTIAL:           'Partial',
    NOT_DISCLOSED:     'Not disclosed',
    NOT_APPLICABLE:    'Not applicable',
    UNDER_REVIEW:      'Under review',
    EVIDENCE_REQUIRED: 'Evidence required',
  };
  const ar: Record<CmaStatus, string> = {
    DISCLOSED:         'مُفصَح',
    PARTIAL:           'جزئي',
    NOT_DISCLOSED:     'غير مُفصَح',
    NOT_APPLICABLE:    'غير قابل للتطبيق',
    UNDER_REVIEW:      'قيد المراجعة',
    EVIDENCE_REQUIRED: 'يتطلّب دليلاً',
  };
  return (lang === 'ar' ? ar : en)[s];
}

/** Composite article label, e.g. "Art. 91(a)" or "المادة ٩١(أ)". */
export function articleLabel(d: CmaDisclosure, lang: 'en' | 'ar'): string {
  const prefix = lang === 'ar' ? 'المادة' : 'Art.';
  return `${prefix} ${d.article}${d.clause ?? ''}`;
}

/** Rule-5 client mirror — refuses transition to PARTIAL/NOT_DISCLOSED
 *  without a gap description. */
export function hasGap(d: CmaDisclosure): boolean {
  return !!(d.gapDescriptionEn && d.gapDescriptionEn.trim())
      || !!(d.gapDescriptionAr && d.gapDescriptionAr.trim());
}

export function hasEvidence(d: CmaDisclosure): boolean {
  return !!(d.evidenceRef && d.evidenceRef.trim());
}
