// Policy Management API client (V79).

import { api } from './api';

export type PolicyStatus    = 'DRAFT' | 'REVIEW' | 'APPROVED' | 'PUBLISHED' | 'EXPIRED' | 'ARCHIVED';
export type ExceptionStatus = 'REQUESTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'REVOKED';
export type AttestationStatus = 'PENDING' | 'ATTESTED' | 'DECLINED' | 'EXPIRED';
export type ReviewCadence   = 'ANNUAL' | 'BIENNIAL' | 'TRIENNIAL' | 'AD_HOC';

export interface PolicyDocument {
  id?: number;
  organisationId?: number;
  policyCode: string;
  version: string;
  titleEn: string;
  titleAr?: string;
  summaryEn?: string;
  summaryAr?: string;
  bodyUrl?: string;
  contentMd?: string;   // CH-10 inline structured content
  category: string;
  applicabilityScope?: string;
  ownerName?: string;
  ownerEmail?: string;
  approverName?: string;
  approverEmail?: string;
  effectiveDate?: string;
  expiryDate?: string;
  nextReviewDate?: string;
  reviewCadence?: ReviewCadence;
  status: PolicyStatus;
  publishedAt?: string;
  retiredReason?: string;
  relatedFramework?: string;
  relatedControls?: string;
  frameworkId?: number;
  notes?: string;
}

export interface PolicyException {
  id?: number;
  organisationId?: number;
  policyId: number;
  exceptionCode?: string;
  titleEn: string;
  titleAr?: string;
  justificationEn: string;
  justificationAr?: string;
  compensatingControlsEn?: string;
  compensatingControlsAr?: string;
  requestorName?: string;
  requestorEmail?: string;
  reviewerName?: string;
  approverName?: string;
  riskLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  riskId?: number;
  requestedAt?: string;
  reviewedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  effectiveDate?: string;
  expiryDate?: string;
  status: ExceptionStatus;
  notes?: string;
}

export interface PolicyAttestation {
  id?: number;
  organisationId?: number;
  policyId: number;
  policyVersion?: string;
  userUsername: string;
  userFullName?: string;
  userRole?: string;
  userDepartment?: string;
  status: AttestationStatus;
  attestedAt?: string;
  declinedAt?: string;
  declinedReason?: string;
  evidenceRef?: string;
  notes?: string;
}

export interface PolicyAggregate {
  total: number;
  published: number;
  inReview: number;
  draft: number;
  expired: number;
  expiringSoon: number;
  openExceptions: number;
  approvedExceptions: number;
  pendingAttestations: number;
  byCategory: Record<string, number>;
}

const BASE = '/api/policy';

// ─── Documents ───────────────────────────────────────────────────────

export function listPolicies(opts: { status?: PolicyStatus; category?: string; latestOnly?: boolean } = {}) {
  const p = new URLSearchParams();
  if (opts.status)   p.set('status', opts.status);
  if (opts.category) p.set('category', opts.category);
  if (opts.latestOnly) p.set('latestOnly', 'true');
  return api.get<PolicyDocument[]>(`${BASE}/documents?${p.toString()}`);
}
export function getPolicy(id: number)                  { return api.get<PolicyDocument>(`${BASE}/documents/${id}`); }
export function createPolicy(body: Partial<PolicyDocument>) { return api.post<PolicyDocument>(`${BASE}/documents`, body); }
export function updatePolicy(id: number, body: Partial<PolicyDocument>) { return api.put<PolicyDocument>(`${BASE}/documents/${id}`, body); }
export function transitionPolicy(id: number, to: PolicyStatus) {
  return api.post<PolicyDocument>(`${BASE}/documents/${id}/transition`, { to });
}
// Track D: route policy approval through a committee (mirror of the change re-route).
export interface PolicyCommitteeReferral {
  referralId?: number; committeeId?: number; gate?: string; status?: string;
  meetingId?: number | null; resolutionId?: number | null;
}
export function referPolicyToCommittee(id: number, committeeId?: number) {
  return api.post<PolicyCommitteeReferral>(`${BASE}/documents/${id}/refer-to-committee`,
    committeeId != null ? { committeeId } : {});
}
export function policyCommitteeReferral(id: number) {
  return api.get<PolicyCommitteeReferral>(`${BASE}/documents/${id}/committee`);
}
export function newPolicyVersion(policyCode: string, newVersion: string) {
  return api.post<PolicyDocument>(`${BASE}/documents/${encodeURIComponent(policyCode)}/new-version`, { newVersion });
}

// CH-12: field-level diff between two versions of a policy code.
export interface PolicyVersionDiff {
  policyCode: string;
  fromVersion: string;
  toVersion: string;
  changes: { field: string; from: string | null; to: string | null }[];
}
export function diffPolicyVersions(policyCode: string, from: string, to: string) {
  return api.get<PolicyVersionDiff>(
    `${BASE}/documents/${encodeURIComponent(policyCode)}/diff?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}
export function deletePolicy(id: number)               { return api.delete<void>(`${BASE}/documents/${id}`); }
export function runPolicyExpirySweep()                 { return api.post<{flippedCount: number; flippedIds: number[]}>(`${BASE}/documents/expiry-sweep`); }
export function policyAggregate()                      { return api.get<PolicyAggregate>(`${BASE}/aggregate`); }

// ─── Evidence package (C-11) ─────────────────────────────────────────
export interface PolicyAttachment {
  id: number; docType: string; titleEn?: string; fileName: string; fileSize?: number;
  mimeType?: string; sha256?: string; validTo?: string; version?: number; downloadUrl?: string;
}
// C-12: each free-text control code resolved against the real control library.
export interface ResolvedControl {
  code: string;
  matched: boolean;
  controlId?: number;
  nameEn?: string;
  nameAr?: string;
  framework?: string;
}
export interface PolicyEvidencePackage {
  policy: PolicyDocument;
  attachmentCount: number;
  attachments: PolicyAttachment[];
  relatedFramework?: string;
  relatedControls?: string;
  resolvedControls: ResolvedControl[];
  generatedAt: string;
}
export function getPolicyEvidencePackage(id: number) {
  return api.get<PolicyEvidencePackage>(`${BASE}/documents/${id}/evidence-package`);
}
export function getPolicyResolvedControls(id: number) {
  return api.get<ResolvedControl[]>(`${BASE}/documents/${id}/resolved-controls`);
}

// C-12: control library for the picker + setting a policy's FK control links.
export interface ControlCatalogItem {
  id: number;
  code: string;
  nameEn: string;
  nameAr?: string;
  framework?: string;
}
export function listControlCatalog() {
  return api
    .get<{ content: ControlCatalogItem[] }>('/api/v1/compliance/controls?page=0&size=500')
    .then(r => r.content ?? []);
}
export function setPolicyControlLinks(id: number, controlIds: number[]) {
  return api.put<ResolvedControl[]>(`${BASE}/documents/${id}/controls`, { controlIds });
}

// C-12: framework library for the create-form picker (sets framework_id + name).
export interface FrameworkOption { id: number; nameEn: string; nameAr?: string; }
export function listFrameworks() {
  return api.get<FrameworkOption[]>('/api/v1/compliance/frameworks');
}

// ─── Exceptions ──────────────────────────────────────────────────────

export function listExceptions(opts: { policyId?: number; status?: ExceptionStatus } = {}) {
  const p = new URLSearchParams();
  if (opts.policyId != null) p.set('policyId', String(opts.policyId));
  if (opts.status)           p.set('status', opts.status);
  return api.get<PolicyException[]>(`${BASE}/exceptions?${p.toString()}`);
}
export function createException(body: Partial<PolicyException>) { return api.post<PolicyException>(`${BASE}/exceptions`, body); }
export function updateException(id: number, body: Partial<PolicyException>) { return api.put<PolicyException>(`${BASE}/exceptions/${id}`, body); }
export function transitionException(id: number, to: ExceptionStatus, reason?: string) {
  return api.post<PolicyException>(`${BASE}/exceptions/${id}/transition`, { to, reason });
}
export function deleteException(id: number)            { return api.delete<void>(`${BASE}/exceptions/${id}`); }
export function runExceptionExpirySweep()              { return api.post<{flippedCount: number; flippedIds: number[]}>(`${BASE}/exceptions/expiry-sweep`); }

// ─── Attestations ────────────────────────────────────────────────────

export function listAttestations(opts: { policyId?: number; username?: string; status?: AttestationStatus } = {}) {
  const p = new URLSearchParams();
  if (opts.policyId != null) p.set('policyId', String(opts.policyId));
  if (opts.username)         p.set('username', opts.username);
  if (opts.status)           p.set('status', opts.status);
  return api.get<PolicyAttestation[]>(`${BASE}/attestations?${p.toString()}`);
}
export function createAttestation(body: Partial<PolicyAttestation>) { return api.post<PolicyAttestation>(`${BASE}/attestations`, body); }
export function attestPolicy(id: number, evidenceRef?: string) {
  return api.post<PolicyAttestation>(`${BASE}/attestations/${id}/attest`, { evidenceRef });
}
export function declineAttestation(id: number, reason: string) {
  return api.post<PolicyAttestation>(`${BASE}/attestations/${id}/decline`, { reason });
}

// ─── UI helpers ──────────────────────────────────────────────────────

export function policyStatusTone(s: PolicyStatus): 'neutral' | 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  switch (s) {
    case 'DRAFT':     return 'neutral';
    case 'REVIEW':    return 'info';
    case 'APPROVED':  return 'primary';
    case 'PUBLISHED': return 'success';
    case 'EXPIRED':   return 'warning';
    case 'ARCHIVED':  return 'neutral';
  }
}

export function policyStatusLabel(s: PolicyStatus, lang: 'en' | 'ar'): string {
  const en: Record<PolicyStatus, string> = {
    DRAFT: 'Draft', REVIEW: 'In review', APPROVED: 'Approved',
    PUBLISHED: 'Published', EXPIRED: 'Expired', ARCHIVED: 'Archived',
  };
  const ar: Record<PolicyStatus, string> = {
    DRAFT: 'مسوّدة', REVIEW: 'قيد المراجعة', APPROVED: 'مُعتمَدة',
    PUBLISHED: 'منشورة', EXPIRED: 'منتهية', ARCHIVED: 'مؤرشَفة',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function exceptionStatusTone(s: ExceptionStatus): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'REQUESTED':    return 'info';
    case 'UNDER_REVIEW': return 'warning';
    case 'APPROVED':     return 'success';
    case 'REJECTED':     return 'danger';
    case 'EXPIRED':      return 'neutral';
    case 'REVOKED':      return 'danger';
  }
}

export function exceptionStatusLabel(s: ExceptionStatus, lang: 'en' | 'ar'): string {
  const en: Record<ExceptionStatus, string> = {
    REQUESTED: 'Requested', UNDER_REVIEW: 'Under review', APPROVED: 'Approved',
    REJECTED: 'Rejected', EXPIRED: 'Expired', REVOKED: 'Revoked',
  };
  const ar: Record<ExceptionStatus, string> = {
    REQUESTED: 'مطلوب', UNDER_REVIEW: 'قيد المراجعة', APPROVED: 'مُعتمَد',
    REJECTED: 'مرفوض', EXPIRED: 'منتهٍ', REVOKED: 'مُلغى',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function attestationStatusTone(s: AttestationStatus): 'neutral' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'PENDING':  return 'warning';
    case 'ATTESTED': return 'success';
    case 'DECLINED': return 'danger';
    case 'EXPIRED':  return 'neutral';
  }
}

export function attestationStatusLabel(s: AttestationStatus, lang: 'en' | 'ar'): string {
  const en: Record<AttestationStatus, string> = {
    PENDING: 'Pending', ATTESTED: 'Attested', DECLINED: 'Declined', EXPIRED: 'Expired',
  };
  const ar: Record<AttestationStatus, string> = {
    PENDING: 'قيد الانتظار', ATTESTED: 'مُقَر', DECLINED: 'مرفوض', EXPIRED: 'منتهٍ',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function nextPolicyStatuses(s: PolicyStatus): PolicyStatus[] {
  switch (s) {
    case 'DRAFT':     return ['REVIEW', 'ARCHIVED'];
    case 'REVIEW':    return ['APPROVED', 'DRAFT'];
    case 'APPROVED':  return ['PUBLISHED', 'REVIEW'];
    case 'PUBLISHED': return ['EXPIRED', 'ARCHIVED'];
    case 'EXPIRED':   return ['ARCHIVED'];
    case 'ARCHIVED':  return [];
  }
}

export function nextExceptionStatuses(s: ExceptionStatus): ExceptionStatus[] {
  switch (s) {
    case 'REQUESTED':    return ['UNDER_REVIEW', 'REJECTED'];
    case 'UNDER_REVIEW': return ['APPROVED', 'REJECTED', 'REQUESTED'];
    case 'APPROVED':     return ['EXPIRED', 'REVOKED'];
    default: return [];
  }
}
