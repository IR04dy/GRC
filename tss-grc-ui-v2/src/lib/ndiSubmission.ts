// NDI Submission Package API client (slice 10 · V75).
//
// Immutable snapshot the entity submits to SDAIA at cycle close.
// Workflow: DRAFT → READY → SUBMITTED → ACKNOWLEDGED | REJECTED.
// Pre-flight (Rule 15) gates DRAFT → READY. Snapshot is immutable
// (Rule 16) once the package leaves READY.

import { api } from './api';

export type NdiSubmissionStatus =
  | 'DRAFT' | 'READY' | 'SUBMITTED' | 'ACKNOWLEDGED' | 'REJECTED' | 'ARCHIVED';

export interface NdiSubmissionPackage {
  id?: number;
  organisationId?: number;
  cycleId: number;
  packageCode?: string;
  version: number;

  snapshotJson?: string;
  manifestJson?: string;
  manifestChecksum?: string;

  overallNdiPct?: number;
  maturityScorePct?: number;
  compliancePct?: number;
  opexScorePct?: number;

  totalEvidence: number;
  totalGaps: number;
  criticalGaps: number;
  totalActions: number;
  completedActions: number;

  status: NdiSubmissionStatus;

  preparerName?: string;
  preparerEmail?: string;
  reviewerName?: string;
  approverName?: string;
  submitterName?: string;

  preparedAt?: string;
  reviewedAt?: string;
  approvedAt?: string;
  submittedAt?: string;
  acknowledgedAt?: string;

  sdaiaReference?: string;
  attestationEn?: string;
  attestationAr?: string;
  rejectionReason?: string;

  isPlaceholder: boolean;
  notes?: string;
}

export interface Preflight {
  cycleId: number;
  openCriticalGaps: number;
  criticalGapsWithoutCapa: number;
  complianceNotApproved: number;
  maturityNotApproved: number;
  expiredEvidence: number;
  canFinalize: boolean;
  blockers: string[];
  warnings: string[];
}

const BASE = '/api/ndi/submission-package';

export function listSubmissions(cycleId: number) {
  return api.get<NdiSubmissionPackage[]>(`${BASE}?cycleId=${cycleId}`);
}
export function preflight(cycleId: number) {
  return api.get<Preflight>(`${BASE}/preflight?cycleId=${cycleId}`);
}
export function getSubmission(id: number) { return api.get<NdiSubmissionPackage>(`${BASE}/${id}`); }
export function createSubmission(cycleId: number, body: { preparerName?: string; preparerEmail?: string } = {}) {
  return api.post<NdiSubmissionPackage>(`${BASE}?cycleId=${cycleId}`, body);
}
export function regenerateSubmission(id: number) { return api.post<NdiSubmissionPackage>(`${BASE}/${id}/regenerate`); }
export function finalizeSubmission(id: number)   { return api.post<NdiSubmissionPackage>(`${BASE}/${id}/finalize`); }
export function approveSubmission(id: number, approverName?: string) { return api.post<NdiSubmissionPackage>(`${BASE}/${id}/approve`, { approverName }); }
export function submitSubmission(id: number, body: { submitterName?: string; attestationEn: string; attestationAr?: string }) {
  return api.post<NdiSubmissionPackage>(`${BASE}/${id}/submit`, body);
}
export function acknowledgeSubmission(id: number, sdaiaReference: string) {
  return api.post<NdiSubmissionPackage>(`${BASE}/${id}/acknowledge`, { sdaiaReference });
}
export function rejectSubmission(id: number, reason: string) {
  return api.post<NdiSubmissionPackage>(`${BASE}/${id}/reject`, { reason });
}
export function reopenSubmission(id: number) { return api.post<NdiSubmissionPackage>(`${BASE}/${id}/reopen`); }
export function deleteSubmission(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function submissionStatusTone(s: NdiSubmissionStatus):
    'neutral' | 'info' | 'primary' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'DRAFT':        return 'neutral';
    case 'READY':        return 'info';
    case 'SUBMITTED':    return 'primary';
    case 'ACKNOWLEDGED': return 'success';
    case 'REJECTED':     return 'danger';
    case 'ARCHIVED':     return 'neutral';
  }
}

export function submissionStatusLabel(s: NdiSubmissionStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiSubmissionStatus, string> = {
    DRAFT: 'Draft', READY: 'Ready', SUBMITTED: 'Submitted',
    ACKNOWLEDGED: 'Acknowledged', REJECTED: 'Rejected', ARCHIVED: 'Archived',
  };
  const ar: Record<NdiSubmissionStatus, string> = {
    DRAFT: 'مسوّدة', READY: 'جاهز', SUBMITTED: 'مُقدَّم',
    ACKNOWLEDGED: 'مُعتمَد من سدايا', REJECTED: 'مرفوض', ARCHIVED: 'مؤرشَف',
  };
  return (lang === 'ar' ? ar : en)[s];
}
