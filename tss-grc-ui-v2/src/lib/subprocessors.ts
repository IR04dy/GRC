// PDPL Sub-processor Tracking — Sprint 11.
//
// Saudi PDPL Article 30 + SDAIA Cross-Border Data Transfer Regulations
// 2023 — controller must inventory every sub-processor with its
// authorisation chain, lawful basis, and cross-border safeguards.
//
// Saudi-native bilingual page title:
//   ar: سجل معالجي البيانات الفرعيين   ← operator-natural
//   en: Sub-processor register
//
// Saudi-native terminology in labels:
//   اتفاقية معالجة البيانات (DPA)    — Data Processing Agreement
//   تقييم أثر النقل (TIA)            — Transfer Impact Assessment
//   النقل عبر الحدود                 — Cross-border transfer
//   الإذن العام / الإذن الخاص        — General / Specific authorisation

import { api } from './api';

export type LifecycleStage =
  | 'PROPOSED' | 'UNDER_DPO_REVIEW' | 'APPROVED' | 'SUSPENDED' | 'RETIRED';

export type AuthorisationType = 'GENERAL' | 'SPECIFIC';

export type LawfulBasis =
  | 'CONSENT' | 'CONTRACT' | 'LEGAL_OBLIGATION' | 'VITAL_INTEREST'
  | 'PUBLIC_INTEREST' | 'LEGITIMATE_INTEREST' | 'REGULATORY';

export type CrossBorderSafeguardType =
  | 'KSA_RESIDENT' | 'SCC_PLUS_TIA' | 'ADEQUACY_DECISION'
  | 'EXPLICIT_CONSENT' | 'OTHER';

export type DataVolumeBand = 'MINIMAL' | 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';

export type SubprocessorEventType =
  | 'ADDED' | 'MATERIAL_CHANGE' | 'SUSPENDED' | 'RESUMED' | 'RETIRED'
  | 'CROSS_BORDER_CHANGE' | 'REVIEW_COMPLETED' | 'INCIDENT_REPORTED';

export interface SubprocessorResponse {
  id: number;
  externalId: string;
  subprocessorCode: string;
  parentProcessorId?: number;
  subProcessorLegalName: string;
  subProcessorLegalNameAr?: string;
  vendorId?: number;
  countryOfProcessing: string;
  regionOfProcessing?: string;
  dataCategoriesJson?: string;
  dataSubjectCategoriesJson?: string;
  processingPurposeEn: string;
  processingPurposeAr?: string;
  lawfulBasis: LawfulBasis;
  authorisationType: AuthorisationType;
  authorisationBasisDocRef?: string;
  crossBorderSafeguardType?: CrossBorderSafeguardType;
  crossBorderSafeguardDocRef?: string;
  tiaReference?: string;
  tiaCompletedAt?: string;
  adequacyDecisionRef?: string;
  dataVolumeBand: DataVolumeBand;
  hasDpaInPlace: boolean;
  dpaSignedDate?: string;
  dpaExpiryDate?: string;
  lifecycleStage: LifecycleStage;
  dpoApprovedByUserId?: number;
  dpoApprovedAt?: string;
  lastReviewedAt?: string;
  nextReviewDueAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SubprocessorRequest {
  subprocessorCode?: string;
  parentProcessorId?: number;
  subProcessorLegalName: string;
  subProcessorLegalNameAr?: string;
  vendorId?: number;
  countryOfProcessing: string;
  regionOfProcessing?: string;
  dataCategoriesJson?: string;
  dataSubjectCategoriesJson?: string;
  processingPurposeEn: string;
  processingPurposeAr?: string;
  lawfulBasis: LawfulBasis;
  authorisationType: AuthorisationType;
  authorisationBasisDocRef?: string;
  crossBorderSafeguardType?: CrossBorderSafeguardType;
  crossBorderSafeguardDocRef?: string;
  tiaReference?: string;
  tiaCompletedAt?: string;
  adequacyDecisionRef?: string;
  dataVolumeBand?: DataVolumeBand;
  hasDpaInPlace?: boolean;
  dpaSignedDate?: string;
  dpaExpiryDate?: string;
}

export interface SubprocessorEventResponse {
  id: number;
  externalId: string;
  subprocessorId: number;
  eventType: SubprocessorEventType;
  eventDate: string;
  recordedByUserId?: number;
  descriptionEn?: string;
  descriptionAr?: string;
  dataSubjectsNotified: boolean;
  dataSubjectsNotificationDate?: string;
  createdAt?: string;
}

export interface PublicSubprocessorResponse {
  subprocessorCode: string;
  subProcessorLegalName: string;
  subProcessorLegalNameAr?: string;
  countryOfProcessing: string;
  regionOfProcessing?: string;
  processingPurposeEn: string;
  processingPurposeAr?: string;
  crossBorderSafeguardType?: CrossBorderSafeguardType;
}

// ─── REST client ─────────────────────────────────────────────────────

export const subprocessorsApi = {
  list:        ()                              => api.get<SubprocessorResponse[]>('/api/v1/privacy/subprocessors'),
  get:         (externalId: string)            => api.get<SubprocessorResponse>(`/api/v1/privacy/subprocessors/${externalId}`),
  create:      (body: SubprocessorRequest)     => api.post<SubprocessorResponse>('/api/v1/privacy/subprocessors', body),
  update:      (externalId: string, body: SubprocessorRequest) =>
    api.put<SubprocessorResponse>(`/api/v1/privacy/subprocessors/${externalId}`, body),
  transition:  (externalId: string, targetStage: LifecycleStage, note?: string) =>
    api.post<SubprocessorResponse>(`/api/v1/privacy/subprocessors/${externalId}/transition`, { targetStage, note }),
  approve:     (externalId: string)            => api.post<SubprocessorResponse>(`/api/v1/privacy/subprocessors/${externalId}/approve`, {}),
  review:      (externalId: string)            => api.post<SubprocessorResponse>(`/api/v1/privacy/subprocessors/${externalId}/review`, {}),
  materialChange: (externalId: string, description: string, descriptionAr?: string) =>
    api.post<SubprocessorResponse>(`/api/v1/privacy/subprocessors/${externalId}/material-change`, { description, descriptionAr }),
  byCountry:   (country: string)               => api.get<SubprocessorResponse[]>(`/api/v1/privacy/subprocessors/by-country/${country}`),
  overdueReviews: ()                           => api.get<SubprocessorResponse[]>('/api/v1/privacy/subprocessors/overdue-reviews'),
  listEvents:  (externalId: string)            => api.get<SubprocessorEventResponse[]>(`/api/v1/privacy/subprocessors/${externalId}/events`),
  publicList:  (orgId?: number)                =>
    api.get<PublicSubprocessorResponse[]>(`/api/v1/privacy/subprocessors/public${orgId ? `?org=${orgId}` : ''}`),
};

// ─── Saudi-native bilingual display labels ──────────────────────────

export const LIFECYCLE_LABEL: Record<LifecycleStage, { en: string; ar: string }> = {
  PROPOSED:         { en: 'Proposed',         ar: 'مُقترَح' },
  UNDER_DPO_REVIEW: { en: 'Under DPO review', ar: 'قيد مراجعة مسؤول حماية البيانات' },
  APPROVED:         { en: 'Approved',         ar: 'مُعتَمد' },
  SUSPENDED:        { en: 'Suspended',        ar: 'موقوف مؤقّتاً' },
  RETIRED:          { en: 'Retired',          ar: 'متقاعد' },
};

export const LIFECYCLE_TONE: Record<LifecycleStage, 'info' | 'warning' | 'success' | 'neutral' | 'danger'> = {
  PROPOSED:         'info',
  UNDER_DPO_REVIEW: 'warning',
  APPROVED:         'success',
  SUSPENDED:        'danger',
  RETIRED:          'neutral',
};

export const AUTH_TYPE_LABEL: Record<AuthorisationType, { en: string; ar: string }> = {
  GENERAL:  { en: 'General authorisation',  ar: 'إذن عام' },
  SPECIFIC: { en: 'Specific authorisation', ar: 'إذن خاص' },
};

export const LAWFUL_BASIS_LABEL: Record<LawfulBasis, { en: string; ar: string }> = {
  CONSENT:             { en: 'Consent',                  ar: 'الموافقة' },
  CONTRACT:            { en: 'Contract',                 ar: 'العقد' },
  LEGAL_OBLIGATION:    { en: 'Legal obligation',         ar: 'التزام قانوني' },
  VITAL_INTEREST:      { en: 'Vital interest',           ar: 'مصلحة حيوية' },
  PUBLIC_INTEREST:     { en: 'Public interest',          ar: 'مصلحة عامة' },
  LEGITIMATE_INTEREST: { en: 'Legitimate interest',      ar: 'مصلحة مشروعة' },
  REGULATORY:          { en: 'Regulatory requirement',   ar: 'مطلب رقابي' },
};

export const SAFEGUARD_LABEL: Record<CrossBorderSafeguardType, { en: string; ar: string }> = {
  KSA_RESIDENT:      { en: 'KSA-resident processing',       ar: 'معالجة داخل المملكة' },
  SCC_PLUS_TIA:      { en: 'SCC + Transfer Impact Assessment', ar: 'البنود التعاقدية القياسية + تقييم أثر النقل' },
  ADEQUACY_DECISION: { en: 'SDAIA adequacy decision',       ar: 'قرار كفاية صادر عن سدايا' },
  EXPLICIT_CONSENT:  { en: 'Explicit consent',              ar: 'موافقة صريحة' },
  OTHER:             { en: 'Other (DPO-documented)',        ar: 'أخرى (موثّقة من مسؤول حماية البيانات)' },
};

export const SAFEGUARD_TONE: Record<CrossBorderSafeguardType, 'success' | 'info' | 'warning' | 'neutral'> = {
  KSA_RESIDENT:      'success',
  SCC_PLUS_TIA:      'info',
  ADEQUACY_DECISION: 'info',
  EXPLICIT_CONSENT:  'warning',
  OTHER:             'neutral',
};

export const VOLUME_LABEL: Record<DataVolumeBand, { en: string; ar: string }> = {
  MINIMAL:   { en: 'Minimal',   ar: 'ضئيل' },
  LOW:       { en: 'Low',       ar: 'منخفض' },
  MEDIUM:    { en: 'Medium',    ar: 'متوسط' },
  HIGH:      { en: 'High',      ar: 'مرتفع' },
  VERY_HIGH: { en: 'Very high', ar: 'مرتفع جدّاً' },
};

export const EVENT_TYPE_LABEL: Record<SubprocessorEventType, { en: string; ar: string }> = {
  ADDED:               { en: 'Onboarded',              ar: 'تمّ إدخاله' },
  MATERIAL_CHANGE:     { en: 'Material change',        ar: 'تغيير جوهري' },
  SUSPENDED:           { en: 'Suspended',              ar: 'موقوف مؤقّتاً' },
  RESUMED:             { en: 'Resumed',                ar: 'استؤنف' },
  RETIRED:             { en: 'Retired',                ar: 'متقاعد' },
  CROSS_BORDER_CHANGE: { en: 'Cross-border change',    ar: 'تغيير في النقل عبر الحدود' },
  REVIEW_COMPLETED:    { en: 'Review completed',       ar: 'اكتملت المراجعة' },
  INCIDENT_REPORTED:   { en: 'Privacy incident',       ar: 'حادثة خصوصية' },
};

export const EVENT_TONE: Record<SubprocessorEventType, 'success' | 'warning' | 'info' | 'danger' | 'neutral'> = {
  ADDED:               'info',
  MATERIAL_CHANGE:     'warning',
  SUSPENDED:           'danger',
  RESUMED:             'success',
  RETIRED:             'neutral',
  CROSS_BORDER_CHANGE: 'warning',
  REVIEW_COMPLETED:    'success',
  INCIDENT_REPORTED:   'danger',
};

/**
 * Compute days until next review. Negative = overdue.
 */
export function daysUntilReview(nextReviewDueAt: string | undefined | null): number | null {
  if (!nextReviewDueAt) return null;
  const due = new Date(nextReviewDueAt).getTime();
  const now = Date.now();
  return Math.round((due - now) / (1000 * 60 * 60 * 24));
}
