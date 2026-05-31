// Outsourcing & Cloud Governance — Sprint 30 frontend.
//
// Backend: PR #265 (com.tss.grc.module.outsourcing).
// F8 rules: PR #266 (AR-261..AR-270).
// Saudi seed: PR #267.
//
// Distinct from the Sprint-13 `outsourcing.ts` lightweight register —
// this module is the SAMA Outsourcing Regulation §3-§8 + SAMA Cloud
// Computing Regulatory Framework regulator-grade surface.
//
// Saudi-native bilingual sidebar:
//   ar : حوكمة الإسناد والسحابة
//   en : Outsourcing & Cloud

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type ArrangementStatus =
  | 'PROPOSED' | 'RISK_ASSESSED' | 'BOARD_APPROVED' | 'SAMA_NOTIFIED'
  | 'LIVE' | 'UNDER_REVIEW' | 'SUSPENDED' | 'EXITED' | 'REJECTED';

export type Materiality = 'MATERIAL' | 'NON_MATERIAL';

export type ServiceCategory =
  | 'CORE_BANKING' | 'PAYMENTS' | 'CARDS' | 'CUSTODY' | 'MARKET_DATA'
  | 'IT_INFRASTRUCTURE' | 'CLOUD' | 'CALL_CENTRE' | 'COLLECTIONS'
  | 'PRINTING' | 'HR' | 'LEGAL' | 'AUDIT' | 'MARKETING' | 'OTHER';

export type CloudStatus =
  | 'ONBOARDING' | 'CLASSIFIED' | 'APPROVED' | 'IN_USE'
  | 'DEPRECATED' | 'DECOMMISSIONED' | 'BLOCKED';

export type DataClassification = 'T1' | 'T2' | 'T3' | 'T4';
export type DataResidency = 'IN_KINGDOM' | 'GCC' | 'GLOBAL';
export type DeploymentModel = 'SAAS' | 'PAAS' | 'IAAS' | 'FAAS' | 'HYBRID';
export type CspBrand =
  | 'AWS' | 'AZURE' | 'GOOGLE_CLOUD' | 'STC_CLOUD' | 'MOBILY_CLOUD'
  | 'SALESFORCE' | 'ORACLE_CLOUD' | 'OTHER';

export type ReviewStatus =
  | 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'WITH_FINDINGS' | 'ESCALATED';

export type ExitStatus =
  | 'DRAFT' | 'APPROVED' | 'REHEARSED' | 'READY' | 'EXECUTED' | 'REJECTED';

// ─── Saudi-native bilingual labels ───────────────────────────────

export const ARRANGEMENT_STATUS_LABEL: Record<ArrangementStatus, { en: string; ar: string }> = {
  PROPOSED:       { en: 'Proposed',         ar: 'مُقترَح' },
  RISK_ASSESSED:  { en: 'Risk assessed',    ar: 'مُقيَّم المخاطر' },
  BOARD_APPROVED: { en: 'Board-approved',   ar: 'مُعتمد من المجلس' },
  SAMA_NOTIFIED:  { en: 'SAMA notified',    ar: 'مُبلَّغ ساما' },
  LIVE:           { en: 'Live',             ar: 'نافذ' },
  UNDER_REVIEW:   { en: 'Under review',     ar: 'قيد المراجعة' },
  SUSPENDED:      { en: 'Suspended',        ar: 'مُعلَّق' },
  EXITED:         { en: 'Exited',           ar: 'مُنتهٍ' },
  REJECTED:       { en: 'Rejected',         ar: 'مرفوض' },
};

export const ARRANGEMENT_STATUS_TONE: Record<ArrangementStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  PROPOSED: 'neutral', RISK_ASSESSED: 'info', BOARD_APPROVED: 'info',
  SAMA_NOTIFIED: 'info', LIVE: 'success', UNDER_REVIEW: 'warning',
  SUSPENDED: 'danger', EXITED: 'neutral', REJECTED: 'danger',
};

export const MATERIALITY_LABEL: Record<Materiality, { en: string; ar: string }> = {
  MATERIAL:     { en: 'Material',     ar: 'جوهري' },
  NON_MATERIAL: { en: 'Non-material', ar: 'غير جوهري' },
};

export const SERVICE_CATEGORY_LABEL: Record<ServiceCategory, { en: string; ar: string }> = {
  CORE_BANKING:      { en: 'Core banking',         ar: 'النواة المصرفية' },
  PAYMENTS:          { en: 'Payments',             ar: 'المدفوعات' },
  CARDS:             { en: 'Cards',                ar: 'البطاقات' },
  CUSTODY:           { en: 'Custody',              ar: 'الحفظ' },
  MARKET_DATA:       { en: 'Market data',          ar: 'بيانات السوق' },
  IT_INFRASTRUCTURE: { en: 'IT infrastructure',    ar: 'البنية التقنية' },
  CLOUD:             { en: 'Cloud',                ar: 'السحابة' },
  CALL_CENTRE:       { en: 'Call centre',          ar: 'مركز الاتصال' },
  COLLECTIONS:       { en: 'Collections',          ar: 'التحصيل' },
  PRINTING:          { en: 'Printing',             ar: 'الطباعة' },
  HR:                { en: 'HR',                   ar: 'الموارد البشرية' },
  LEGAL:             { en: 'Legal',                ar: 'القانوني' },
  AUDIT:             { en: 'Audit',                ar: 'التدقيق' },
  MARKETING:         { en: 'Marketing',            ar: 'التسويق' },
  OTHER:             { en: 'Other',                ar: 'أخرى' },
};

export const CLOUD_STATUS_LABEL: Record<CloudStatus, { en: string; ar: string }> = {
  ONBOARDING:     { en: 'Onboarding',     ar: 'قيد الإدراج' },
  CLASSIFIED:     { en: 'Classified',     ar: 'مُصنَّفة' },
  APPROVED:       { en: 'Approved',       ar: 'مُعتمدة' },
  IN_USE:         { en: 'In use',         ar: 'قيد الاستخدام' },
  DEPRECATED:     { en: 'Deprecated',     ar: 'مُقاعَدة' },
  DECOMMISSIONED: { en: 'Decommissioned', ar: 'مُسحَبة من الخدمة' },
  BLOCKED:        { en: 'Blocked',        ar: 'محظورة' },
};

export const CLOUD_STATUS_TONE: Record<CloudStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  ONBOARDING: 'neutral', CLASSIFIED: 'info', APPROVED: 'info',
  IN_USE: 'success', DEPRECATED: 'warning', DECOMMISSIONED: 'neutral',
  BLOCKED: 'danger',
};

export const DATA_CLASSIFICATION_LABEL: Record<DataClassification, { en: string; ar: string }> = {
  T1: { en: 'T1 — Public',        ar: 'الفئة ١ — عام' },
  T2: { en: 'T2 — Internal',      ar: 'الفئة ٢ — داخلي' },
  T3: { en: 'T3 — Confidential',  ar: 'الفئة ٣ — سرّي' },
  T4: { en: 'T4 — Restricted',    ar: 'الفئة ٤ — مقيَّد' },
};

export const DATA_RESIDENCY_LABEL: Record<DataResidency, { en: string; ar: string }> = {
  IN_KINGDOM: { en: 'In Kingdom', ar: 'داخل المملكة' },
  GCC:        { en: 'GCC',        ar: 'دول الخليج' },
  GLOBAL:     { en: 'Global',     ar: 'عالمي' },
};

// ─── State-machine helpers ────────────────────────────────────────

export function nextArrangementStatuses(s: ArrangementStatus): ArrangementStatus[] {
  switch (s) {
    case 'PROPOSED':       return ['RISK_ASSESSED', 'REJECTED'];
    case 'RISK_ASSESSED':  return ['BOARD_APPROVED', 'REJECTED'];
    case 'BOARD_APPROVED': return ['SAMA_NOTIFIED', 'LIVE'];
    case 'SAMA_NOTIFIED':  return ['LIVE'];
    case 'LIVE':           return ['UNDER_REVIEW', 'SUSPENDED', 'EXITED'];
    case 'UNDER_REVIEW':   return ['LIVE', 'SUSPENDED', 'EXITED'];
    case 'SUSPENDED':      return ['LIVE', 'EXITED'];
    default:               return [];
  }
}

export function nextCloudStatuses(s: CloudStatus): CloudStatus[] {
  switch (s) {
    case 'ONBOARDING':     return ['CLASSIFIED'];
    case 'CLASSIFIED':     return ['APPROVED', 'BLOCKED'];
    case 'APPROVED':       return ['IN_USE'];
    case 'IN_USE':         return ['DEPRECATED', 'DECOMMISSIONED'];
    case 'DEPRECATED':     return ['DECOMMISSIONED'];
    case 'BLOCKED':        return ['CLASSIFIED'];
    default:               return [];
  }
}

// ─── Types ────────────────────────────────────────────────────────

export interface CreateArrangementRequest {
  reference: string;
  providerLegalNameEn: string;
  providerLegalNameAr: string;
  providerCountry?: string;
  providerJurisdiction?: string;
  serviceTitleEn: string;
  serviceTitleAr: string;
  serviceDescriptionEn?: string;
  serviceDescriptionAr?: string;
  serviceCategory: ServiceCategory;
  materiality: Materiality;
  isCoreActivity?: boolean;
  contractStart?: string;
  contractEnd?: string;
  annualSpendSar?: number;
  reviewCadenceMonths?: number;
}

export interface TransitionArrangementRequest {
  toStatus: ArrangementStatus;
  reasonEn?: string;
  reasonAr?: string;
  samaNonObjectionRef?: string;
  riskRating?: string;
}

export interface CreateCloudServiceRequest {
  arrangementId: number;
  reference: string;
  serviceNameEn: string;
  serviceNameAr: string;
  deploymentModel: DeploymentModel;
  cspLegalNameEn: string;
  cspLegalNameAr: string;
  cspBrand?: CspBrand;
  dataClassification?: DataClassification;
  dataResidency?: DataResidency;
  cspRegionLabel?: string;
}

export interface ClassifyCloudServiceRequest {
  dataClassification: DataClassification;
  dataResidency: DataResidency;
  cspRegionLabel?: string;
}

export interface CloudServiceTransitionRequest {
  toStatus: CloudStatus;
  blockReasonEn?: string;
  blockReasonAr?: string;
  approverRole?: string;
}

export interface ArrangementResponse {
  id: number;
  reference: string;
  providerLegalNameEn: string;
  providerLegalNameAr: string;
  providerCountry?: string;
  providerJurisdiction?: string;
  serviceTitleEn: string;
  serviceTitleAr: string;
  serviceCategory: ServiceCategory;
  materiality: Materiality;
  isCoreActivity?: boolean;
  status: ArrangementStatus;
  riskRating?: string;
  samaNonObjectionRef?: string;
  liveAt?: string;
  exitedAt?: string;
  annualSpendSar?: number;
  nextReviewDue?: string;
  reviewCadenceMonths?: number;
}

export interface CloudServiceResponse {
  id: number;
  arrangementId: number;
  reference: string;
  serviceNameEn: string;
  serviceNameAr: string;
  deploymentModel: DeploymentModel;
  cspLegalNameEn: string;
  cspLegalNameAr: string;
  cspBrand?: CspBrand;
  dataClassification?: DataClassification;
  dataResidency?: DataResidency;
  cspRegionLabel?: string;
  status: CloudStatus;
  blockReasonEn?: string;
  blockReasonAr?: string;
  approverRole?: string;
}

export interface ReviewResponse {
  id: number;
  arrangementId: number;
  reference: string;
  status: ReviewStatus;
  dueDate?: string;
  outcomeRag?: string;
  findingsSummaryEn?: string;
  findingsSummaryAr?: string;
}

export interface ExitPlanResponse {
  id: number;
  arrangementId: number;
  reference: string;
  status: ExitStatus;
  summaryEn?: string;
  summaryAr?: string;
  targetMigrationDays?: number;
  alternativeProviderEn?: string;
  alternativeProviderAr?: string;
}

export interface DashboardResponse {
  arrangementsTotal: number;
  materialActive: number;
  arrangementsLive: number;
  arrangementsUnderReview: number;
  cloudServicesTotal: number;
  cloudServicesT3T4: number;
  cloudServicesBlocked: number;
  reviewsOverdue: number;
  exitPlansReady: number;
  arrangementsByStatus: Record<string, number>;
  cloudServicesByClassification: Record<string, number>;
  cloudServicesByResidency: Record<string, number>;
  recentArrangements: ArrangementResponse[];
  overdueReviews: ArrangementResponse[];
}

// ─── REST client ─────────────────────────────────────────────────

const BASE = '/api/v1/outsourcing';

export const outsourcingCloudApi = {
  dashboard: () => api.get<DashboardResponse>(`${BASE}/dashboard`),
  listArrangements: () => api.get<ArrangementResponse[]>(`${BASE}/arrangements`),
  getArrangement: (id: number) => api.get<ArrangementResponse>(`${BASE}/arrangements/${id}`),
  createArrangement: (req: CreateArrangementRequest) =>
    api.post<ArrangementResponse>(`${BASE}/arrangements`, req),
  transitionArrangement: (id: number, req: TransitionArrangementRequest) =>
    api.post<ArrangementResponse>(`${BASE}/arrangements/${id}/transition`, req),

  listCloudServices: (arrangementId?: number) => {
    const q = arrangementId ? `?arrangementId=${arrangementId}` : '';
    return api.get<CloudServiceResponse[]>(`${BASE}/cloud-services${q}`);
  },
  createCloudService: (req: CreateCloudServiceRequest) =>
    api.post<CloudServiceResponse>(`${BASE}/cloud-services`, req),
  classifyCloudService: (id: number, req: ClassifyCloudServiceRequest) =>
    api.post<CloudServiceResponse>(`${BASE}/cloud-services/${id}/classify`, req),
  transitionCloudService: (id: number, req: CloudServiceTransitionRequest) =>
    api.post<CloudServiceResponse>(`${BASE}/cloud-services/${id}/transition`, req),
};
