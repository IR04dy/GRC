// NDI Compliance Assessment API client (slice 4 · V69).
//
// Per-cycle responses to COMPLIANCE framework items. Carries 2 status
// fields: substantive complianceStatus (6 values) + workflow
// lifecycleStatus (DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW →
// APPROVED, with RETURNED looping back).

import { api } from './api';

export type NdiComplianceStatus =
  | 'COMPLIANT' | 'PARTIAL' | 'NON_COMPLIANT' | 'NOT_APPLICABLE'
  | 'EVIDENCE_REQUIRED' | 'UNDER_REVIEW';

export type NdiComplianceLifecycle =
  | 'DRAFT' | 'IN_PROGRESS' | 'SUBMITTED' | 'UNDER_REVIEW' | 'RETURNED' | 'APPROVED';

export interface NdiComplianceAssessment {
  id?: number;
  organisationId?: number;
  cycleId: number;
  frameworkItemId: number;
  requirementCode: string;

  component: string;
  domain: string;
  subdomain?: string;

  complianceStatus: NdiComplianceStatus;
  complianceScore?: number;
  weight: number;

  responseTextEn?: string;
  responseTextAr?: string;
  evidenceRef?: string;
  evidenceRequired: boolean;
  evidenceStatus?: 'ATTACHED' | 'EXPIRED' | 'REJECTED';

  needsGap: boolean;
  gapId?: number;
  correctiveActionId?: number;
  riskId?: number;

  naJustification?: string;

  controlOwnerName?: string;
  controlOwnerEmail?: string;
  evidenceOwnerName?: string;
  reviewerName?: string;
  approverName?: string;

  lifecycleStatus: NdiComplianceLifecycle;
  submittedAt?: string;
  reviewedAt?: string;
  approvedAt?: string;
  returnedAt?: string;
  returnReason?: string;

  notes?: string;
}

export interface NdiComplianceAggregate {
  cycleId: number;
  total: number;
  compliancePct: number;
  byCompliance: Record<NdiComplianceStatus, number>;
  byLifecycle: Record<NdiComplianceLifecycle, number>;
  byDomainPct: Record<string, number>;
  needsGapCount: number;
  evidenceRequiredCount: number;
}

export interface StartSummary {
  created: number;
  skipped: number;
  totalComplianceItems: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/compliance';

export function listCompliance(cycleId: number, opts: { status?: NdiComplianceStatus; domain?: string; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  p.set('cycleId', String(cycleId));
  if (opts.status) p.set('status', opts.status);
  if (opts.domain) p.set('domain', opts.domain);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<Page<NdiComplianceAssessment>>(`${BASE}?${p.toString()}`);
}

export function complianceAggregate(cycleId: number) {
  return api.get<NdiComplianceAggregate>(`${BASE}/aggregate?cycleId=${cycleId}`);
}

export function startComplianceForCycle(cycleId: number) {
  return api.post<StartSummary>(`${BASE}/start?cycleId=${cycleId}`);
}

export function setComplianceStatus(id: number, status: NdiComplianceStatus, naJustification?: string) {
  return api.post<NdiComplianceAssessment>(`${BASE}/${id}/set-status`, { status, naJustification });
}

export function attachComplianceEvidence(id: number, evidenceRef: string, evidenceOwner?: string) {
  return api.post<NdiComplianceAssessment>(`${BASE}/${id}/attach-evidence`, { evidenceRef, evidenceOwner });
}

export function updateCompliance(id: number, body: Partial<NdiComplianceAssessment>) {
  return api.put<NdiComplianceAssessment>(`${BASE}/${id}`, body);
}

export function submitCompliance(id: number) { return api.post<NdiComplianceAssessment>(`${BASE}/${id}/submit`); }
export function markUnderReviewCompliance(id: number, reviewerName?: string) {
  return api.post<NdiComplianceAssessment>(`${BASE}/${id}/mark-under-review`, { reviewerName });
}
export function returnCompliance(id: number, reason: string) {
  return api.post<NdiComplianceAssessment>(`${BASE}/${id}/return`, { reason });
}
export function approveCompliance(id: number, approverName?: string) {
  return api.post<NdiComplianceAssessment>(`${BASE}/${id}/approve`, { approverName });
}
export function reopenCompliance(id: number) { return api.post<NdiComplianceAssessment>(`${BASE}/${id}/reopen`); }
export function deleteCompliance(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function complianceStatusTone(s: NdiComplianceStatus):
    'success' | 'warning' | 'danger' | 'neutral' | 'info' {
  switch (s) {
    case 'COMPLIANT':         return 'success';
    case 'PARTIAL':           return 'warning';
    case 'NON_COMPLIANT':     return 'danger';
    case 'NOT_APPLICABLE':    return 'neutral';
    case 'EVIDENCE_REQUIRED': return 'danger';
    case 'UNDER_REVIEW':      return 'info';
  }
}

export function complianceStatusLabel(s: NdiComplianceStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiComplianceStatus, string> = {
    COMPLIANT: 'Compliant', PARTIAL: 'Partial', NON_COMPLIANT: 'Non-compliant',
    NOT_APPLICABLE: 'Not applicable', EVIDENCE_REQUIRED: 'Evidence required', UNDER_REVIEW: 'Under review',
  };
  const ar: Record<NdiComplianceStatus, string> = {
    COMPLIANT: 'ملتزم', PARTIAL: 'ملتزم جزئياً', NON_COMPLIANT: 'غير ملتزم',
    NOT_APPLICABLE: 'غير منطبق', EVIDENCE_REQUIRED: 'يتطلب دليلاً', UNDER_REVIEW: 'تحت المراجعة',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function lifecycleTone(s: NdiComplianceLifecycle):
    'neutral' | 'info' | 'primary' | 'warning' | 'success' {
  switch (s) {
    case 'DRAFT':         return 'neutral';
    case 'IN_PROGRESS':   return 'info';
    case 'SUBMITTED':     return 'primary';
    case 'UNDER_REVIEW':  return 'primary';
    case 'RETURNED':      return 'warning';
    case 'APPROVED':      return 'success';
  }
}

export function lifecycleLabel(s: NdiComplianceLifecycle, lang: 'en' | 'ar'): string {
  const en: Record<NdiComplianceLifecycle, string> = {
    DRAFT: 'Draft', IN_PROGRESS: 'In progress', SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'Under review', RETURNED: 'Returned', APPROVED: 'Approved',
  };
  const ar: Record<NdiComplianceLifecycle, string> = {
    DRAFT: 'مسودة', IN_PROGRESS: 'قيد التنفيذ', SUBMITTED: 'مُقدَّم',
    UNDER_REVIEW: 'تحت المراجعة', RETURNED: 'مُعاد', APPROVED: 'مُعتمَد',
  };
  return (lang === 'ar' ? ar : en)[s];
}
