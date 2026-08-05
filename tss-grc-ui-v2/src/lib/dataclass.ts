// Data Classification + NDMO — Sprint 32 frontend.
//
// Backend + F8 + seed: PR #270 (com.tss.grc.module.dataclass).
//
// NDMO Data Classification Policy (4-level) + PDPL Art.4 (special-category)
// + Art.6 (lawful basis) + Art.7 (cross-border gate).
//
// Saudi-native bilingual sidebar:
//   ar : تصنيف البيانات
//   en : Data Classification

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type AssetStatus =
  | 'DRAFT' | 'CLASSIFIED' | 'APPROVED' | 'IN_USE'
  | 'REVIEW_DUE' | 'RECLASSIFY' | 'DECOMMISSIONED';

export type Classification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'TOP_SECRET';

export type SpecialCategory =
  | 'NONE' | 'HEALTH' | 'BIOMETRIC' | 'GENETIC'
  | 'RELIGIOUS' | 'RACIAL' | 'POLITICAL' | 'CRIMINAL';

export type AssetDomain =
  | 'CUSTOMER_PII' | 'FINANCIAL_TRANSACTION' | 'EMPLOYEE' | 'SHARIA'
  | 'AML_KYC' | 'MARKET_DATA' | 'CONTRACT' | 'CREDIT_FILE'
  | 'OPERATIONAL' | 'OTHER';

export type FlowStatus =
  | 'PROPOSED' | 'APPROVED' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED' | 'REJECTED';

export type DestinationAdequacy = 'IN_KINGDOM' | 'GCC' | 'ADEQUATE' | 'NON_ADEQUATE';

export type ExceptionStatus =
  | 'REQUESTED' | 'APPROVED' | 'ACTIVE' | 'EXPIRED' | 'REJECTED' | 'REVOKED';

export type ExceptionType =
  | 'ENCRYPTION_AT_REST_WAIVER' | 'MASKING_WAIVER'
  | 'RETENTION_OVERRIDE' | 'CROSS_BORDER_TEMP' | 'OTHER';

// ─── Saudi-native bilingual labels ───────────────────────────────

export const CLASSIFICATION_LABEL: Record<Classification, { en: string; ar: string }> = {
  PUBLIC:       { en: 'Public',        ar: 'عام' },
  INTERNAL:     { en: 'Internal',      ar: 'داخلي' },
  CONFIDENTIAL: { en: 'Confidential',  ar: 'سرّي' },
  TOP_SECRET:   { en: 'Top secret',    ar: 'سرّي للغاية' },
};

export const CLASSIFICATION_TONE: Record<Classification, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  PUBLIC: 'neutral', INTERNAL: 'info',
  CONFIDENTIAL: 'warning', TOP_SECRET: 'danger',
};

export const SPECIAL_CATEGORY_LABEL: Record<SpecialCategory, { en: string; ar: string }> = {
  NONE:      { en: 'None',                  ar: 'لا ينطبق' },
  HEALTH:    { en: 'Health',                ar: 'صحية' },
  BIOMETRIC: { en: 'Biometric',             ar: 'بصمات حيوية' },
  GENETIC:   { en: 'Genetic',               ar: 'وراثية' },
  RELIGIOUS: { en: 'Religious',             ar: 'دينية' },
  RACIAL:    { en: 'Racial / ethnic',       ar: 'عرقية' },
  POLITICAL: { en: 'Political',             ar: 'سياسية' },
  CRIMINAL:  { en: 'Criminal record',       ar: 'سوابق جنائية' },
};

export const ASSET_STATUS_LABEL: Record<AssetStatus, { en: string; ar: string }> = {
  DRAFT:          { en: 'Draft',          ar: 'مسوّدة' },
  CLASSIFIED:     { en: 'Classified',     ar: 'مُصنَّفة' },
  APPROVED:       { en: 'Approved',       ar: 'مُعتمدة' },
  IN_USE:         { en: 'In use',         ar: 'قيد الاستخدام' },
  REVIEW_DUE:     { en: 'Review due',     ar: 'مراجعة مستحقّة' },
  RECLASSIFY:     { en: 'Reclassify',     ar: 'إعادة تصنيف' },
  DECOMMISSIONED: { en: 'Decommissioned', ar: 'مُسحَبة من الخدمة' },
};

export const ASSET_STATUS_TONE: Record<AssetStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', CLASSIFIED: 'info', APPROVED: 'info',
  IN_USE: 'success', REVIEW_DUE: 'warning', RECLASSIFY: 'warning',
  DECOMMISSIONED: 'neutral',
};

export const FLOW_STATUS_LABEL: Record<FlowStatus, { en: string; ar: string }> = {
  PROPOSED:   { en: 'Proposed',   ar: 'مُقترَح' },
  APPROVED:   { en: 'Approved',   ar: 'مُعتمد' },
  ACTIVE:     { en: 'Active',     ar: 'نشط' },
  SUSPENDED:  { en: 'Suspended',  ar: 'مُعلَّق' },
  TERMINATED: { en: 'Terminated', ar: 'مُنتهٍ' },
  REJECTED:   { en: 'Rejected',   ar: 'مرفوض' },
};

export const FLOW_STATUS_TONE: Record<FlowStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  PROPOSED: 'neutral', APPROVED: 'info', ACTIVE: 'success',
  SUSPENDED: 'warning', TERMINATED: 'neutral', REJECTED: 'danger',
};

export const ADEQUACY_LABEL: Record<DestinationAdequacy, { en: string; ar: string }> = {
  IN_KINGDOM:   { en: 'In Kingdom',          ar: 'داخل المملكة' },
  GCC:          { en: 'GCC',                 ar: 'دول الخليج' },
  ADEQUATE:     { en: 'Adequate jurisdiction', ar: 'بلد كافٍ الحماية' },
  NON_ADEQUATE: { en: 'Non-adequate',        ar: 'حماية غير كافية' },
};

export const EXCEPTION_STATUS_LABEL: Record<ExceptionStatus, { en: string; ar: string }> = {
  REQUESTED: { en: 'Requested', ar: 'مطلوب' },
  APPROVED:  { en: 'Approved',  ar: 'مُعتمد' },
  ACTIVE:    { en: 'Active',    ar: 'نشط' },
  EXPIRED:   { en: 'Expired',   ar: 'منتهي الصلاحية' },
  REJECTED:  { en: 'Rejected',  ar: 'مرفوض' },
  REVOKED:   { en: 'Revoked',   ar: 'مُلغى' },
};

export const EXCEPTION_STATUS_TONE: Record<ExceptionStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  REQUESTED: 'warning', APPROVED: 'info', ACTIVE: 'success',
  EXPIRED: 'danger', REJECTED: 'danger', REVOKED: 'neutral',
};

export const EXCEPTION_TYPE_LABEL: Record<ExceptionType, { en: string; ar: string }> = {
  ENCRYPTION_AT_REST_WAIVER: { en: 'Encryption-at-rest waiver',  ar: 'استثناء التشفير في الراحة' },
  MASKING_WAIVER:            { en: 'Masking waiver',             ar: 'استثناء الإخفاء' },
  RETENTION_OVERRIDE:        { en: 'Retention override',         ar: 'استثناء الاحتفاظ' },
  CROSS_BORDER_TEMP:         { en: 'Cross-border (temporary)',   ar: 'عبر الحدود (مؤقّت)' },
  OTHER:                     { en: 'Other',                      ar: 'أخرى' },
};

// ─── Responses ────────────────────────────────────────────────────

export interface AssetResponse {
  id: number;
  reference: string;
  nameEn: string;
  nameAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  domain?: AssetDomain;
  classification?: Classification;
  pdplSpecialCategory: SpecialCategory;
  pdplLawfulBasis?: string;
  status: AssetStatus;
  ownerRole?: string;
  ownerUserId?: number;
  hostSystemLabel?: string;
  retentionYears?: number;
  encryptionAtRest?: boolean;
  encryptionInTransit?: boolean;
  maskingRequired?: boolean;
  classifiedAt?: string;
  approvedAt?: string;
  nextReviewDue?: string;
  decommissionedAt?: string;
  rowCountApprox?: number;
  linkedDpiaRef?: string;
}

export interface FlowResponse {
  id: number;
  dataAssetId: number;
  reference: string;
  sourceSystemLabel?: string;
  destinationSystemLabel?: string;
  destinationCountry?: string;
  destinationAdequacy?: DestinationAdequacy;
  transferMode?: string;
  status: FlowStatus;
  purposeEn?: string;
  purposeAr?: string;
  pdplArt7TransferRef?: string;
  approvedAt?: string;
  activatedAt?: string;
  suspendedAt?: string;
  terminatedAt?: string;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
}

export interface ExceptionResponse {
  id: number;
  reference: string;
  dataAssetId?: number;
  dataFlowId?: number;
  exceptionType: ExceptionType;
  status: ExceptionStatus;
  justificationEn?: string;
  justificationAr?: string;
  compensatingControlsEn?: string;
  compensatingControlsAr?: string;
  requestedByUserId?: number;
  approverUserId?: number;
  approvedAt?: string;
  validFrom?: string;
  validUntil?: string;
  revokedAt?: string;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
}

export interface DashboardResponse {
  assetsTotal: number;
  assetsTopSecret: number;
  assetsConfidential: number;
  assetsInternal: number;
  assetsPublic: number;
  assetsSpecialCategory: number;
  flowsActive: number;
  flowsCrossBorder: number;
  exceptionsActive: number;
  exceptionsExpiringNext30d: number;
  /**
   * F2-128 — ACTIVE exceptions whose validity has already lapsed. Distinct from
   * `exceptionsExpiringNext30d`, whose window starts at today and so excludes these entirely.
   */
  exceptionsLapsed: number;
  assetsByStatus: Record<string, number>;
  flowsByStatus: Record<string, number>;
  exceptionsByType: Record<string, number>;
  recentAssets: AssetResponse[];
  recentCrossBorderFlows: FlowResponse[];
}

// ─── Write requests ──────────────────────────────────────────────

export interface CreateAssetRequest {
  reference: string;
  nameEn: string;
  nameAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  domain?: AssetDomain;
  hostSystemLabel?: string;
  pdplSpecialCategory?: SpecialCategory;
  pdplLawfulBasis?: string;
  ownerRole?: string;
  ownerUserId?: number;
  retentionYears?: number;
  encryptionAtRest?: boolean;
  encryptionInTransit?: boolean;
  maskingRequired?: boolean;
  rowCountApprox?: number;
  linkedDpiaRef?: string;
}

export interface ClassifyAssetRequest {
  classification: Classification;
  pdplSpecialCategory?: SpecialCategory;
  pdplLawfulBasis?: string;
  nextReviewDue?: string;
}

export interface TransitionAssetRequest {
  toStatus: AssetStatus;
  reasonEn?: string;
  reasonAr?: string;
}

export interface CreateFlowRequest {
  dataAssetId: number;
  reference: string;
  sourceSystemLabel?: string;
  destinationSystemLabel?: string;
  destinationCountry?: string;
  destinationAdequacy?: DestinationAdequacy;
  transferMode?: string;
  purposeEn?: string;
  purposeAr?: string;
  pdplArt7TransferRef?: string;
}

export interface TransitionFlowRequest {
  toStatus: FlowStatus;
  reasonEn?: string;
  reasonAr?: string;
  pdplArt7TransferRef?: string;
}

export interface CreateExceptionRequest {
  reference: string;
  dataAssetId?: number;
  dataFlowId?: number;
  exceptionType: ExceptionType;
  justificationEn?: string;
  justificationAr?: string;
  compensatingControlsEn?: string;
  compensatingControlsAr?: string;
  validFrom?: string;
  validUntil?: string;
}

export interface TransitionExceptionRequest {
  toStatus: ExceptionStatus;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
}

// ─── State-machine helpers ───────────────────────────────────────

export function nextAssetStatuses(s: AssetStatus): AssetStatus[] {
  switch (s) {
    case 'DRAFT':       return ['CLASSIFIED', 'DECOMMISSIONED'];
    case 'CLASSIFIED':  return ['APPROVED', 'RECLASSIFY'];
    case 'APPROVED':    return ['IN_USE', 'RECLASSIFY'];
    case 'IN_USE':      return ['REVIEW_DUE', 'RECLASSIFY', 'DECOMMISSIONED'];
    case 'REVIEW_DUE':  return ['RECLASSIFY', 'IN_USE', 'DECOMMISSIONED'];
    case 'RECLASSIFY':  return ['CLASSIFIED', 'DECOMMISSIONED'];
    default:            return [];
  }
}

export function nextFlowStatuses(s: FlowStatus): FlowStatus[] {
  switch (s) {
    case 'PROPOSED':  return ['APPROVED', 'REJECTED'];
    case 'APPROVED':  return ['ACTIVE', 'REJECTED'];
    case 'ACTIVE':    return ['SUSPENDED', 'TERMINATED'];
    case 'SUSPENDED': return ['ACTIVE', 'TERMINATED'];
    default:          return [];
  }
}

export function nextExceptionStatuses(s: ExceptionStatus): ExceptionStatus[] {
  switch (s) {
    case 'REQUESTED': return ['APPROVED', 'REJECTED'];
    case 'APPROVED':  return ['ACTIVE', 'REVOKED'];
    case 'ACTIVE':    return ['EXPIRED', 'REVOKED'];
    default:          return [];
  }
}

// ─── REST client ─────────────────────────────────────────────────

const BASE = '/api/v1/dataclass';

export const dataclassApi = {
  dashboard: () => api.get<DashboardResponse>(`${BASE}/dashboard`),

  listAssets: () => api.get<AssetResponse[]>(`${BASE}/assets`),
  getAsset: (id: number) => api.get<AssetResponse>(`${BASE}/assets/${id}`),
  createAsset: (req: CreateAssetRequest) =>
    api.post<AssetResponse>(`${BASE}/assets`, req),
  classifyAsset: (id: number, req: ClassifyAssetRequest) =>
    api.post<AssetResponse>(`${BASE}/assets/${id}/classify`, req),
  transitionAsset: (id: number, req: TransitionAssetRequest) =>
    api.post<AssetResponse>(`${BASE}/assets/${id}/transition`, req),

  listFlows: (assetId?: number) => {
    const q = assetId ? `?assetId=${assetId}` : '';
    return api.get<FlowResponse[]>(`${BASE}/flows${q}`);
  },
  createFlow: (req: CreateFlowRequest) =>
    api.post<FlowResponse>(`${BASE}/flows`, req),
  transitionFlow: (id: number, req: TransitionFlowRequest) =>
    api.post<FlowResponse>(`${BASE}/flows/${id}/transition`, req),

  listExceptions: () => api.get<ExceptionResponse[]>(`${BASE}/exceptions`),
  createException: (req: CreateExceptionRequest) =>
    api.post<ExceptionResponse>(`${BASE}/exceptions`, req),
  transitionException: (id: number, req: TransitionExceptionRequest) =>
    api.post<ExceptionResponse>(`${BASE}/exceptions/${id}/transition`, req),
};
