// NDI Maturity Assessment API client (slice 3 · V68).
//
// Per-cycle responses to MATURITY framework items with 0..5 CMMI-style
// levels. The cycle id is required on every list/aggregate call — a
// maturity row has no meaning outside a cycle.

import { api } from './api';

export type NdiMaturityStatus =
  | 'DRAFT' | 'IN_PROGRESS' | 'SUBMITTED' | 'UNDER_REVIEW' | 'RETURNED' | 'APPROVED';

export interface NdiMaturityAssessment {
  id?: number;
  organisationId?: number;
  cycleId: number;
  frameworkItemId: number;

  component: string;
  domain: string;
  subdomain?: string;

  currentLevel?: number;
  targetLevel?: number;
  score?: number;
  weight: number;

  responseTextEn?: string;
  responseTextAr?: string;
  evidenceRef?: string;
  evidenceRequired: boolean;

  improvementActionSuggested: boolean;
  justification?: string;

  assessorName?: string;
  assessorEmail?: string;
  domainOwnerName?: string;
  reviewerName?: string;
  reviewerEmail?: string;
  approverName?: string;

  status: NdiMaturityStatus;
  submittedAt?: string;
  reviewedAt?: string;
  approvedAt?: string;
  returnedAt?: string;
  returnReason?: string;

  notes?: string;
}

export interface NdiMaturityAggregate {
  cycleId: number;
  total: number;
  unresponded: number;
  maturityScorePct: number;
  byStatus: Record<NdiMaturityStatus, number>;
  byLevel: Record<string, number>;          // "0".."5"
  byDomainPct: Record<string, number>;
  improvementSuggestedCount: number;
  rule2ViolationCount: number;
}

export interface StartSummary {
  created: number;
  skipped: number;
  totalMaturityItems: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/maturity';

export function listMaturity(cycleId: number, opts: { status?: NdiMaturityStatus; domain?: string; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  p.set('cycleId', String(cycleId));
  if (opts.status) p.set('status', opts.status);
  if (opts.domain) p.set('domain', opts.domain);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<Page<NdiMaturityAssessment>>(`${BASE}?${p.toString()}`);
}

export function maturityAggregate(cycleId: number) {
  return api.get<NdiMaturityAggregate>(`${BASE}/aggregate?cycleId=${cycleId}`);
}

export function getMaturity(id: number) { return api.get<NdiMaturityAssessment>(`${BASE}/${id}`); }

export function startMaturityForCycle(cycleId: number) {
  return api.post<StartSummary>(`${BASE}/start?cycleId=${cycleId}`);
}

export function updateMaturity(id: number, body: Partial<NdiMaturityAssessment>) {
  return api.put<NdiMaturityAssessment>(`${BASE}/${id}`, body);
}

export function submitMaturity(id: number) { return api.post<NdiMaturityAssessment>(`${BASE}/${id}/submit`); }
// F2-236 — no reviewerName: the server takes the reviewer from the authenticated principal.
export function markUnderReviewMaturity(id: number) {
  return api.post<NdiMaturityAssessment>(`${BASE}/${id}/mark-under-review`);
}
export function returnMaturity(id: number, reason: string) {
  return api.post<NdiMaturityAssessment>(`${BASE}/${id}/return`, { reason });
}
// F2-236 — no approverName: the server takes the approver from the authenticated principal.
export function approveMaturity(id: number) {
  return api.post<NdiMaturityAssessment>(`${BASE}/${id}/approve`);
}
export function reopenMaturity(id: number) { return api.post<NdiMaturityAssessment>(`${BASE}/${id}/reopen`); }
export function deleteMaturity(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function maturityStatusTone(s: NdiMaturityStatus):
    'neutral' | 'info' | 'primary' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'DRAFT':         return 'neutral';
    case 'IN_PROGRESS':   return 'info';
    case 'SUBMITTED':     return 'primary';
    case 'UNDER_REVIEW':  return 'primary';
    case 'RETURNED':      return 'warning';
    case 'APPROVED':      return 'success';
  }
}

export function maturityStatusLabel(s: NdiMaturityStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiMaturityStatus, string> = {
    DRAFT: 'Draft', IN_PROGRESS: 'In progress', SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'Under review', RETURNED: 'Returned', APPROVED: 'Approved',
  };
  const ar: Record<NdiMaturityStatus, string> = {
    DRAFT: 'مسودة', IN_PROGRESS: 'قيد التنفيذ', SUBMITTED: 'مُقدَّم',
    UNDER_REVIEW: 'تحت المراجعة', RETURNED: 'مُعاد للتعديل', APPROVED: 'مُعتمَد',
  };
  return (lang === 'ar' ? ar : en)[s];
}

/** Bilingual CMMI level label. */
export function levelLabel(level: number | undefined, lang: 'en' | 'ar'): string {
  if (level == null) return lang === 'ar' ? '—' : '—';
  const en = ['Not Started', 'Initial', 'Managed', 'Defined', 'Measured', 'Optimized'];
  const ar = ['غير مطبّق', 'أولي', 'مُدار', 'مُعرَّف', 'مُقاس', 'مُحسَّن'];
  return (lang === 'ar' ? ar : en)[level] ?? String(level);
}

/** Color for a maturity level dot — green = mature, red = not started. */
export function levelColor(level: number | undefined): string {
  if (level == null) return 'var(--surface-2)';
  if (level >= 4) return 'var(--success)';
  if (level >= 3) return 'var(--info)';
  if (level >= 2) return 'var(--primary)';
  if (level >= 1) return 'var(--warning)';
  return 'var(--danger)';
}
