// SAMA Open Banking Compliance — Sprint 19 PR #3.
//
// Backend: PR #255 (com.tss.grc.module.openbanking).
// F8 rules: PR #256 (AR-241..AR-250).
//
// SAMA Open Banking Framework (Phase 1 AIS Live 2022; Phase 2 PIS
// Live 2024; Phase 3 VRP + CBPII rolling 2025-2026). TPP register +
// customer consent management + FAPI 2.0 audit + OB-specific incidents.
//
// Saudi-native bilingual sidebar:
//   ar : المصرفية المفتوحة
//   en : Open Banking

import { api } from './api';

export type TppStatus = 'PENDING_KYB' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
export type TppRole = 'AISP' | 'PISP' | 'CBPII';
export type ConsentStatus = 'AUTHORISED' | 'ACTIVE' | 'EXPIRED' | 'REVOKED';
export type ConsentType =
  | 'AIS_ACCOUNT_INFO' | 'AIS_BALANCES' | 'AIS_TRANSACTIONS'
  | 'PIS_PAYMENT' | 'VRP_SETUP' | 'CBPII_CONFIRM_FUNDS';
export type RevokedBy = 'CUSTOMER' | 'BANK' | 'TPP';
export type IncidentType =
  | 'CONSENT_VIOLATION' | 'SCOPE_CREEP' | 'CERT_MISUSE'
  | 'TPP_ON_TPP_FRAUD' | 'API_ABUSE' | 'DATA_LEAK'
  | 'BULK_REVOCATION_FRAUD_SIGNAL' | 'OUTAGE' | 'OTHER';
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus =
  | 'OPEN' | 'INVESTIGATING' | 'CONTAINED' | 'RESOLVED' | 'CLOSED';
export type EventType =
  | 'TPP_ONBOARDED' | 'TPP_SUSPENDED' | 'TPP_REVOKED' | 'TPP_STATUS_CHANGED'
  | 'CONSENT_GRANTED' | 'CONSENT_REVOKED' | 'CONSENT_EXPIRED'
  | 'CERT_EXPIRING' | 'CERT_ROTATED'
  | 'INCIDENT_RAISED' | 'INCIDENT_STATUS_CHANGED' | 'INCIDENT_RESOLVED'
  | 'INCIDENT_SAMA_NOTIFIED'
  | 'API_ABUSE_DETECTED' | 'BULK_REVOCATION_DETECTED'
  | 'INTERNAL_NOTE';

export interface CreateTppRequest {
  reference: string;
  legalNameEn: string;
  legalNameAr: string;
  samaLicenceRef?: string;
  fapiClientId?: string;
  roles: string;
  country?: string;
  certFingerprint?: string;
  certExpiresAt?: string;
  rateLimitPerSec?: number;
  accountManagerUserId?: number;
  complianceOfficerUserId?: number;
}

export interface TransitionTppRequest {
  toStatus: TppStatus;
  reasonEn?: string;
  reasonAr?: string;
}

export interface GrantConsentRequest {
  tppId: number;
  consentReference: string;
  customerHash: string;
  customerLabelEn?: string;
  customerLabelAr?: string;
  consentType: ConsentType;
  scopes: string;
  expiresAt?: string;
  amountCapSar?: number;
  frequencyWindowDays?: number;
}

export interface RevokeConsentRequest {
  revokedBy: RevokedBy;
  reasonEn?: string;
  reasonAr?: string;
}

export interface RaiseIncidentRequest {
  tppId?: number;
  incidentType: IncidentType;
  severity: IncidentSeverity;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  samaReportable?: boolean;
  affectedCustomerCount?: number;
  assignedToUserId?: number;
}

export interface TransitionIncidentRequest {
  toStatus: IncidentStatus;
  noteEn?: string;
  noteAr?: string;
}

export interface NotifySamaRequest {
  samaNotificationRef: string;
}

export interface TppResponse {
  id: number;
  reference: string;
  legalNameEn: string;
  legalNameAr: string;
  samaLicenceRef?: string;
  fapiClientId?: string;
  roles: string;
  country?: string;
  status: TppStatus;
  onboardedAt?: string;
  suspendedAt?: string;
  revokedAt?: string;
  suspensionReasonEn?: string;
  suspensionReasonAr?: string;
  certFingerprint?: string;
  certExpiresAt?: string;
  rateLimitPerSec?: number;
  accountManagerUserId?: number;
  complianceOfficerUserId?: number;
  activeConsentCount?: number;
  openIncidentCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ConsentResponse {
  id: number;
  tppId: number;
  consentReference: string;
  customerHash: string;
  customerLabelEn?: string;
  customerLabelAr?: string;
  consentType: ConsentType;
  scopes: string;
  status: ConsentStatus;
  grantedAt: string;
  expiresAt?: string;
  revokedAt?: string;
  revokedBy?: RevokedBy;
  revocationReasonEn?: string;
  revocationReasonAr?: string;
  amountCapSar?: number;
  frequencyWindowDays?: number;
  callCount?: number;
  lastUsedAt?: string;
}

export interface ApiCallResponse {
  id: number;
  tppId: number;
  consentReference?: string;
  occurredAt: string;
  httpMethod: string;
  endpoint: string;
  statusCode: number;
  latencyMs?: number;
  scopesUsed?: string;
  customerHash?: string;
  sourceIpHash?: string;
  interactionId?: string;
  responseBytes?: number;
  errorReason?: string;
}

export interface IncidentResponse {
  id: number;
  reference: string;
  tppId?: number;
  incidentType: IncidentType;
  severity: IncidentSeverity;
  status: IncidentStatus;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  detectedAt: string;
  containedAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  samaReportable?: boolean;
  samaNotificationRef?: string;
  samaNotifiedAt?: string;
  affectedCustomerCount?: number;
  assignedToUserId?: number;
}

export interface EventResponse {
  id: number;
  tppId?: number;
  consentId?: number;
  incidentId?: number;
  occurredAt: string;
  eventType: EventType;
  actorUserId?: number;
  actorName?: string;
  statusAfter?: string;
  noteEn?: string;
  noteAr?: string;
}

export interface DashboardResponse {
  activeTpps: number;
  suspendedTpps: number;
  pendingKybTpps: number;
  activeConsents: number;
  revokedConsents24h: number;
  openIncidents: number;
  criticalOpenIncidents: number;
  samaReportableOpen: number;
  apiCalls24h: number;
  apiErrors24h: number;
  certExpiring30d: number;
  tppsByStatus: Record<string, number>;
  consentsByType: Record<string, number>;
  recentTpps: TppResponse[];
  openIncidentList: IncidentResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const TPP_STATUS_LABEL: Record<TppStatus, { en: string; ar: string }> = {
  PENDING_KYB: { en: 'Pending KYB', ar: 'قيد التحقّق' },
  ACTIVE:      { en: 'Active',      ar: 'نشط' },
  SUSPENDED:   { en: 'Suspended',   ar: 'موقوف' },
  REVOKED:     { en: 'Revoked',     ar: 'مُلغى' },
};

export const TPP_STATUS_TONE: Record<TppStatus,
  'neutral' | 'success' | 'warning' | 'danger'> = {
  PENDING_KYB: 'neutral',
  ACTIVE:      'success',
  SUSPENDED:   'warning',
  REVOKED:     'danger',
};

export const CONSENT_STATUS_LABEL: Record<ConsentStatus, { en: string; ar: string }> = {
  AUTHORISED: { en: 'Authorised', ar: 'مصرَّح' },
  ACTIVE:     { en: 'Active',     ar: 'نشطة' },
  EXPIRED:    { en: 'Expired',    ar: 'منتهية' },
  REVOKED:    { en: 'Revoked',    ar: 'مسحوبة' },
};

export const CONSENT_STATUS_TONE: Record<ConsentStatus,
  'info' | 'success' | 'neutral' | 'danger'> = {
  AUTHORISED: 'info',
  ACTIVE:     'success',
  EXPIRED:    'neutral',
  REVOKED:    'danger',
};

export const CONSENT_TYPE_LABEL: Record<ConsentType, { en: string; ar: string }> = {
  AIS_ACCOUNT_INFO:    { en: 'Account info (AIS)', ar: 'معلومات الحساب (AIS)' },
  AIS_BALANCES:        { en: 'Balances (AIS)',     ar: 'الأرصدة (AIS)' },
  AIS_TRANSACTIONS:    { en: 'Transactions (AIS)', ar: 'العمليات (AIS)' },
  PIS_PAYMENT:         { en: 'Payment (PIS)',      ar: 'دفعة (PIS)' },
  VRP_SETUP:           { en: 'Recurring (VRP)',    ar: 'دورية (VRP)' },
  CBPII_CONFIRM_FUNDS: { en: 'Funds check (CBPII)', ar: 'التحقّق من الرصيد (CBPII)' },
};

export const INCIDENT_TYPE_LABEL: Record<IncidentType, { en: string; ar: string }> = {
  CONSENT_VIOLATION:           { en: 'Consent violation',     ar: 'خرق الموافقة' },
  SCOPE_CREEP:                 { en: 'Scope creep',           ar: 'تجاوز النطاق' },
  CERT_MISUSE:                 { en: 'Certificate misuse',    ar: 'سوء استخدام الشهادة' },
  TPP_ON_TPP_FRAUD:            { en: 'TPP-on-TPP fraud',      ar: 'احتيال بين المزوّدين' },
  API_ABUSE:                   { en: 'API abuse',             ar: 'إساءة استخدام الواجهة' },
  DATA_LEAK:                   { en: 'Data leak',             ar: 'تسرّب بيانات' },
  BULK_REVOCATION_FRAUD_SIGNAL:{ en: 'Bulk revocation signal',ar: 'إشارة سحب جماعي' },
  OUTAGE:                      { en: 'Outage',                ar: 'انقطاع' },
  OTHER:                       { en: 'Other',                 ar: 'أخرى' },
};

export const SEVERITY_LABEL: Record<IncidentSeverity, { en: string; ar: string }> = {
  LOW:      { en: 'Low',      ar: 'منخفضة' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسطة' },
  HIGH:     { en: 'High',     ar: 'عالية' },
  CRITICAL: { en: 'Critical', ar: 'حرجة' },
};

export const SEVERITY_TONE: Record<IncidentSeverity,
  'risk-low' | 'risk-med' | 'risk-high' | 'risk-crit'> = {
  LOW:      'risk-low',
  MEDIUM:   'risk-med',
  HIGH:     'risk-high',
  CRITICAL: 'risk-crit',
};

export const INCIDENT_STATUS_LABEL: Record<IncidentStatus, { en: string; ar: string }> = {
  OPEN:           { en: 'Open',           ar: 'مفتوحة' },
  INVESTIGATING:  { en: 'Investigating',  ar: 'قيد التحقيق' },
  CONTAINED:      { en: 'Contained',      ar: 'مُحتواة' },
  RESOLVED:       { en: 'Resolved',       ar: 'محلولة' },
  CLOSED:         { en: 'Closed',         ar: 'مغلقة' },
};

export const INCIDENT_STATUS_TONE: Record<IncidentStatus,
  'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  OPEN:          'danger',
  INVESTIGATING: 'warning',
  CONTAINED:     'info',
  RESOLVED:      'success',
  CLOSED:        'neutral',
};

export const EVENT_TYPE_LABEL: Record<EventType, { en: string; ar: string }> = {
  TPP_ONBOARDED:           { en: 'TPP onboarded',          ar: 'تفعيل المزوّد' },
  TPP_SUSPENDED:           { en: 'TPP suspended',          ar: 'إيقاف المزوّد' },
  TPP_REVOKED:             { en: 'TPP revoked',            ar: 'إلغاء المزوّد' },
  TPP_STATUS_CHANGED:      { en: 'TPP status changed',     ar: 'تغيّر حالة المزوّد' },
  CONSENT_GRANTED:         { en: 'Consent granted',        ar: 'منح موافقة' },
  CONSENT_REVOKED:         { en: 'Consent revoked',        ar: 'سحب موافقة' },
  CONSENT_EXPIRED:         { en: 'Consent expired',        ar: 'انتهت الموافقة' },
  CERT_EXPIRING:           { en: 'Certificate expiring',   ar: 'اقتراب انتهاء الشهادة' },
  CERT_ROTATED:            { en: 'Certificate rotated',    ar: 'تدوير الشهادة' },
  INCIDENT_RAISED:         { en: 'Incident raised',        ar: 'رفع حادثة' },
  INCIDENT_STATUS_CHANGED: { en: 'Incident status changed',ar: 'تغيّر حالة الحادثة' },
  INCIDENT_RESOLVED:       { en: 'Incident resolved',      ar: 'حلّ الحادثة' },
  INCIDENT_SAMA_NOTIFIED:  { en: 'SAMA notified',          ar: 'إبلاغ ساما' },
  API_ABUSE_DETECTED:      { en: 'API abuse detected',     ar: 'كشف إساءة واجهة' },
  BULK_REVOCATION_DETECTED:{ en: 'Bulk revocation',        ar: 'سحب جماعي مرصود' },
  INTERNAL_NOTE:           { en: 'Internal note',          ar: 'ملاحظة داخلية' },
};

export function nextTppStatuses(s: TppStatus): TppStatus[] {
  switch (s) {
    case 'PENDING_KYB': return ['ACTIVE', 'REVOKED'];
    case 'ACTIVE':      return ['SUSPENDED', 'REVOKED'];
    case 'SUSPENDED':   return ['ACTIVE', 'REVOKED'];
    default:            return [];
  }
}

export function nextIncidentStatuses(s: IncidentStatus): IncidentStatus[] {
  switch (s) {
    case 'OPEN':          return ['INVESTIGATING', 'RESOLVED'];
    case 'INVESTIGATING': return ['CONTAINED', 'RESOLVED'];
    case 'CONTAINED':     return ['RESOLVED'];
    case 'RESOLVED':      return ['CLOSED'];
    default:              return [];
  }
}

const PREFIX = '/api/v1/openbanking';

export const obApi = {
  dashboard: () => api.get<DashboardResponse>(`${PREFIX}/dashboard`),

  listTpps: (page = 0, size = 50) =>
    api.get<Page<TppResponse>>(`${PREFIX}/tpps?page=${page}&size=${size}`),
  getTpp: (id: number) => api.get<TppResponse>(`${PREFIX}/tpps/${id}`),
  createTpp: (body: CreateTppRequest) =>
    api.post<TppResponse>(`${PREFIX}/tpps`, body),
  transitionTpp: (id: number, body: TransitionTppRequest) =>
    api.post<TppResponse>(`${PREFIX}/tpps/${id}/transition`, body),
  listConsentsForTpp: (id: number) =>
    api.get<ConsentResponse[]>(`${PREFIX}/tpps/${id}/consents`),
  listTppEvents: (id: number) =>
    api.get<EventResponse[]>(`${PREFIX}/tpps/${id}/events`),

  listConsents: (page = 0, size = 50) =>
    api.get<Page<ConsentResponse>>(`${PREFIX}/consents?page=${page}&size=${size}`),
  grantConsent: (body: GrantConsentRequest) =>
    api.post<ConsentResponse>(`${PREFIX}/consents`, body),
  revokeConsent: (id: number, body: RevokeConsentRequest) =>
    api.post<ConsentResponse>(`${PREFIX}/consents/${id}/revoke`, body),

  listApiCalls: (page = 0, size = 100) =>
    api.get<Page<ApiCallResponse>>(`${PREFIX}/api-calls?page=${page}&size=${size}`),

  listIncidents: (page = 0, size = 50) =>
    api.get<Page<IncidentResponse>>(`${PREFIX}/incidents?page=${page}&size=${size}`),
  getIncident: (id: number) =>
    api.get<IncidentResponse>(`${PREFIX}/incidents/${id}`),
  raiseIncident: (body: RaiseIncidentRequest) =>
    api.post<IncidentResponse>(`${PREFIX}/incidents`, body),
  transitionIncident: (id: number, body: TransitionIncidentRequest) =>
    api.post<IncidentResponse>(`${PREFIX}/incidents/${id}/transition`, body),
  notifySama: (id: number, body: NotifySamaRequest) =>
    api.post<IncidentResponse>(`${PREFIX}/incidents/${id}/notify-sama`, body),
};
