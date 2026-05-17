// PDPL extensions API client (Saudi GRC prompt §3.5).
//
// Three sibling capabilities under one lib:
//   - cross-border transfer (PDPL Art. 29)
//   - disposal certificate (PDPL Art. 18 retention)
//   - privacy notice templating (PDPL Art. 12)
//
// All are per-tenant, status-driven, and share the page surface at
// /pdpl-extensions with one tab per capability.

import { api } from './api';

// ─── Cross-border transfer ───────────────────────────────────────────

export type CbtStatus = 'REQUESTED' | 'ASSESSED' | 'APPROVED' | 'REJECTED' | 'EXPIRED';
export type TransferMechanism = 'ADEQUACY' | 'SCC' | 'BCR' | 'EXPLICIT_CONSENT'
  | 'CONTRACT_NECESSITY' | 'VITAL_INTEREST' | 'PUBLIC_INTEREST';

export interface CrossBorderTransfer {
  id: number;
  ropaCode: string;
  ropaTitleEn?: string;
  sourceCountry: string;
  destinationCountry: string;
  destinationOrgName: string;
  destinationOrgRole?: string;
  dataCategories: string;
  dataSubjectsCountEst?: number;
  transferMechanism: TransferMechanism;
  safeguardsEn?: string;
  safeguardsAr?: string;
  justificationEn: string;
  justificationAr?: string;
  residencyCheckOutcome?: 'PASS' | 'PASS_WITH_CONDITIONS' | 'FAIL';
  status: CbtStatus;
  rejectionReason?: string;
  approverName?: string;
  effectiveFrom?: string;
  expiryDate?: string;
  createdAt: string;
  updatedAt: string;
}

export function listCrossBorderTransfers(page = 0, size = 50) {
  return api.get<{ content: CrossBorderTransfer[]; totalElements: number }>(
    `/api/privacy/cross-border-transfers?page=${page}&size=${size}`);
}
export function createCrossBorderTransfer(body: Partial<CrossBorderTransfer>) {
  return api.post<CrossBorderTransfer>('/api/privacy/cross-border-transfers', body);
}
export function cbtAssess(id: number, residencyCheckBy: string, outcome: 'PASS' | 'PASS_WITH_CONDITIONS' | 'FAIL') {
  return api.post<CrossBorderTransfer>(`/api/privacy/cross-border-transfers/${id}/assess`,
    { residencyCheckBy, residencyCheckOutcome: outcome });
}
export function cbtApprove(id: number) {
  return api.post<CrossBorderTransfer>(`/api/privacy/cross-border-transfers/${id}/approve`);
}
export function cbtReject(id: number, rejectionReason: string) {
  return api.post<CrossBorderTransfer>(`/api/privacy/cross-border-transfers/${id}/reject`,
    { rejectionReason });
}

export const CBT_STATUS_TONE: Record<CbtStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  REQUESTED: 'warning', ASSESSED: 'info', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'primary',
};

// ─── Disposal certificate ────────────────────────────────────────────

export type DisposalStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'VERIFIED' | 'CANCELLED';
export type DisposalMethod = 'DELETE' | 'ANONYMIZE' | 'ARCHIVE' | 'DESTROY';

export interface DisposalCertificate {
  id: number;
  relatedEntityType: string;
  relatedEntityId: string;
  relatedEntityLabel?: string;
  retentionRuleRef?: string;
  scopeDescriptionEn: string;
  recordsCountEst?: number;
  disposalMethod: DisposalMethod;
  status: DisposalStatus;
  scheduledDate?: string;
  executedAt?: string;
  executorName?: string;
  verifiedAt?: string;
  verifierName?: string;
  evidenceRef?: string;
  signatureTxId?: number;
  ownerRole?: string;
  ownerName?: string;
  createdAt: string;
  updatedAt: string;
}

export function listDisposals(page = 0, size = 50) {
  return api.get<{ content: DisposalCertificate[]; totalElements: number }>(
    `/api/privacy/disposal-certificates?page=${page}&size=${size}`);
}
export function createDisposal(body: Partial<DisposalCertificate>) {
  return api.post<DisposalCertificate>('/api/privacy/disposal-certificates', body);
}
export function disposalStart(id: number) {
  return api.post<DisposalCertificate>(`/api/privacy/disposal-certificates/${id}/start`);
}
export function disposalComplete(id: number, executorName?: string, evidenceRef?: string) {
  return api.post<DisposalCertificate>(`/api/privacy/disposal-certificates/${id}/complete`,
    { executorName, evidenceRef });
}
export function disposalVerify(id: number, verifierName?: string, signatureTxId?: number) {
  return api.post<DisposalCertificate>(`/api/privacy/disposal-certificates/${id}/verify`,
    { verifierName, signatureTxId });
}

export const DISPOSAL_STATUS_TONE: Record<DisposalStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  SCHEDULED: 'warning', IN_PROGRESS: 'info', COMPLETED: 'primary', VERIFIED: 'success', CANCELLED: 'danger',
};

// ─── Privacy notice ──────────────────────────────────────────────────

export type NoticeStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface PrivacyNotice {
  id: number;
  noticeCode: string;
  version: number;
  titleEn: string;
  titleAr: string;
  purposeEn: string;
  purposeAr?: string;
  dataCategories?: string;
  recipients?: string;
  retentionPeriodEn?: string;
  retentionPeriodAr?: string;
  dpoContact?: string;
  lawfulBasis?: string;
  bodyEn: string;
  bodyAr?: string;
  linkedRopaCodes?: string;
  status: NoticeStatus;
  effectiveFrom?: string;
  effectiveTo?: string;
  publishedAt?: string;
  supersedesId?: number;
  createdAt: string;
  updatedAt: string;
}

export function listNotices(page = 0, size = 50) {
  return api.get<{ content: PrivacyNotice[]; totalElements: number }>(
    `/api/privacy/notices?page=${page}&size=${size}`);
}
export function createNotice(body: Partial<PrivacyNotice>) {
  return api.post<PrivacyNotice>('/api/privacy/notices', body);
}
export function publishNotice(id: number) {
  return api.post<PrivacyNotice>(`/api/privacy/notices/${id}/publish`);
}
export function archiveNotice(id: number, archiveReasonEn?: string) {
  return api.post<PrivacyNotice>(`/api/privacy/notices/${id}/archive`, { archiveReasonEn });
}
export function supersedeNotice(id: number) {
  return api.post<PrivacyNotice>(`/api/privacy/notices/${id}/supersede`);
}

export const NOTICE_STATUS_TONE: Record<NoticeStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  DRAFT: 'warning', PUBLISHED: 'success', ARCHIVED: 'primary',
};
