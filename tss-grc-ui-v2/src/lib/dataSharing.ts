// Data Sharing Agreements API client (gap #2 — PDPL data sharing / disclosure).

import { api } from './api';

export type DsaStatus       = 'DRAFT' | 'IN_REVIEW' | 'ACTIVE' | 'EXPIRED' | 'TERMINATED';
export type DsaRecipientType = 'REGULATOR' | 'CREDIT_BUREAU' | 'GROUP_AFFILIATE'
                             | 'JOINT_VENTURE' | 'PUBLIC_AUTHORITY' | 'RESEARCH' | 'COMMERCIAL_PARTNER';
export type DsaLegalRole    = 'CONTROLLER' | 'JOINT_CONTROLLER' | 'PROCESSOR';
export type DsaLawfulBasis  = 'CONSENT' | 'LEGAL_OBLIGATION' | 'CONTRACT' | 'VITAL_INTEREST'
                            | 'PUBLIC_INTEREST' | 'LEGITIMATE_INTEREST';

export interface DataSharingAgreement {
  id: number;
  agreementCode?: string;
  titleEn: string; titleAr?: string;
  recipientType: DsaRecipientType;
  recipientName: string;
  recipientCountry?: string;
  recipientLegalRole: DsaLegalRole;
  dataCategories: string;
  dataSubjects: string;
  purposeEn: string;  purposeAr?: string;
  lawfulBasis: DsaLawfulBasis;
  crossBorderMechanism: string;
  retentionPeriodDays?: number;
  subDisclosureAllowed: number;
  subjectNotified: number;
  linkedRopaCodes?: string;
  linkedPrivacyNoticeId?: number;
  linkedCbtId?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  signedAt?: string;
  signerName?: string;
  counterpartySigner?: string;
  status: DsaStatus;
  terminationReasonEn?: string;
  createdAt: string;
  updatedAt: string;
}

const BASE = '/api/privacy/data-sharing';

export function listDsas(status?: DsaStatus, page = 0, size = 200):
    Promise<{ content: DataSharingAgreement[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}
export function submitDsa(id: number) { return api.post<DataSharingAgreement>(`${BASE}/${id}/submit`); }
// F2-236 — no signerName: OUR signatory is the authenticated principal, because activate stamps
// signedAt from the server's clock and so records THIS act. counterpartySigner stays, and is the
// one field here that legitimately comes from the caller: the other party's signatory is a person
// at another organisation and can never be a principal in this platform.
export function activateDsa(id: number, counterpartySigner?: string) {
  return api.post<DataSharingAgreement>(`${BASE}/${id}/activate`, { counterpartySigner });
}
export function expireDsa(id: number) { return api.post<DataSharingAgreement>(`${BASE}/${id}/expire`); }
export function terminateDsa(id: number, reasonEn: string) {
  return api.post<DataSharingAgreement>(`${BASE}/${id}/terminate`, { reasonEn });
}

export const DSA_STATUS_TONE: Record<DsaStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', IN_REVIEW: 'info', ACTIVE: 'success', EXPIRED: 'primary', TERMINATED: 'danger',
};
