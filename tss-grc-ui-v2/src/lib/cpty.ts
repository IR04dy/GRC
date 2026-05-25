// Counterparty Credit Rating Registry — Sprint 18 PR #3.
//
// Backend: PR #250 (com.tss.grc.module.cpty).
// F8 rules: PR #251 (AR-231..AR-240).
//
// Credit-risk spine for SAMA's Credit Risk Management Standards:
// counterparty register, internal + external ratings, rating actions
// (watchlist / downgrade / default), large-exposure limits with RAG
// band engine.
//
// Saudi-native bilingual sidebar:
//   ar : سجل الأطراف المقابلة
//   en : Counterparty Registry

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type CounterpartyStatus =
  | 'ACTIVE' | 'WATCHLIST' | 'DEFAULT' | 'RESOLVED' | 'DORMANT';

export type Sector =
  | 'SOVEREIGN' | 'FINANCIAL_INSTITUTION' | 'CORPORATE_LARGE'
  | 'CORPORATE_SME' | 'RETAIL' | 'GOVERNMENT_RELATED_ENTITY'
  | 'MULTILATERAL';

export type RatingType = 'INTERNAL' | 'EXTERNAL';

export type Outlook = 'STABLE' | 'POSITIVE' | 'NEGATIVE' | 'DEVELOPING';

export type ActionType =
  | 'WATCHLIST_NEGATIVE' | 'WATCHLIST_POSITIVE' | 'WATCHLIST_REMOVE'
  | 'DOWNGRADE' | 'UPGRADE' | 'DEFAULT' | 'RECOVERY' | 'OUTLOOK_CHANGE';

export type TriggerSource =
  | 'INTERNAL_REVIEW' | 'EXTERNAL_AGENCY' | 'EARLY_WARNING'
  | 'KRI_BREACH' | 'MANUAL';

export type LimitScope = 'COUNTERPARTY' | 'GROUP';

export type Band = 'GREEN' | 'AMBER' | 'RED' | 'BREACH';

export type EventType =
  | 'REGISTERED' | 'RATING_ASSIGNED' | 'RATING_ACTION'
  | 'LIMIT_APPROVED' | 'LIMIT_REVISED' | 'LIMIT_BAND_CHANGED'
  | 'KYC_REFRESHED' | 'RM_HANDOFF' | 'STATUS_CHANGED' | 'INTERNAL_NOTE';

// ─── Requests ────────────────────────────────────────────────────

export interface CreateCounterpartyRequest {
  reference?: string;
  lei?: string;
  legalNameEn: string;
  legalNameAr: string;
  shortName?: string;
  sector: Sector;
  country?: string;
  industry?: string;
  parentGroupReference?: string;
  greFlag?: boolean;
  sovereignFlag?: boolean;
  fiFlag?: boolean;
  relationshipManagerUserId?: number;
  creditOfficerUserId?: number;
  onboardedAt?: string;
}

export interface UpdateCounterpartyRequest {
  legalNameEn?: string;
  legalNameAr?: string;
  shortName?: string;
  sector?: Sector;
  country?: string;
  industry?: string;
  parentGroupReference?: string;
  greFlag?: boolean;
  sovereignFlag?: boolean;
  fiFlag?: boolean;
  status?: CounterpartyStatus;
  relationshipManagerUserId?: number;
  creditOfficerUserId?: number;
  kycRefreshedAt?: string;
  totalExposureSarM?: number;
}

export interface AssignRatingRequest {
  ratingType: RatingType;
  effectiveDate: string;
  grade: string;
  scale?: string;
  agency?: string;
  pd?: number;
  lgd?: number;
  eadSarM?: number;
  outlook?: Outlook;
  rationaleEn?: string;
  rationaleAr?: string;
  approvedByUserId?: number;
}

export interface CreateRatingActionRequest {
  actionType: ActionType;
  actionDate: string;
  gradeBefore?: string;
  gradeAfter?: string;
  outlookBefore?: Outlook;
  outlookAfter?: Outlook;
  triggerSource?: TriggerSource;
  rationaleEn?: string;
  rationaleAr?: string;
}

export interface UpsertLimitRequest {
  scope: LimitScope;
  counterpartyId?: number;
  groupReference?: string;
  displayNameEn: string;
  displayNameAr: string;
  limitSarM: number;
  utilisedSarM: number;
  approvedAt?: string;
  nextReviewAt?: string;
  approvedByUserId?: number;
}

export interface RecordUtilisationRequest {
  utilisedSarM: number;
  refreshedAt: string;
  noteEn?: string;
  noteAr?: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface CounterpartyResponse {
  id: number;
  reference: string;
  lei?: string;
  legalNameEn: string;
  legalNameAr: string;
  shortName?: string;
  sector: Sector;
  country?: string;
  industry?: string;
  parentGroupReference?: string;
  greFlag: boolean;
  sovereignFlag: boolean;
  fiFlag: boolean;
  status: CounterpartyStatus;
  relationshipManagerUserId?: number;
  creditOfficerUserId?: number;
  kycRefreshedAt?: string;
  onboardedAt?: string;
  totalExposureSarM?: number;
  currentInternalGrade?: string;
  currentExternalRating?: string;
  currentExternalAgency?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RatingResponse {
  id: number;
  counterpartyId: number;
  ratingType: RatingType;
  effectiveDate: string;
  grade: string;
  scale?: string;
  agency?: string;
  pd?: number;
  lgd?: number;
  eadSarM?: number;
  outlook?: Outlook;
  rationaleEn?: string;
  rationaleAr?: string;
  assignedByUserId?: number;
  approvedByUserId?: number;
}

export interface RatingActionResponse {
  id: number;
  counterpartyId: number;
  actionType: ActionType;
  actionDate: string;
  gradeBefore?: string;
  gradeAfter?: string;
  outlookBefore?: Outlook;
  outlookAfter?: Outlook;
  triggerSource?: TriggerSource;
  rationaleEn?: string;
  rationaleAr?: string;
  actorUserId?: number;
}

export interface LimitResponse {
  id: number;
  scope: LimitScope;
  counterpartyId?: number;
  groupReference?: string;
  displayNameEn: string;
  displayNameAr: string;
  limitSarM: number;
  utilisedSarM: number;
  utilisationPct: number;
  band: Band;
  approvedAt?: string;
  nextReviewAt?: string;
  approvedByUserId?: number;
  utilisationRefreshedAt?: string;
}

export interface EventResponse {
  id: number;
  counterpartyId: number;
  occurredAt: string;
  eventType: EventType;
  actorUserId?: number;
  actorName?: string;
  linkedRatingId?: number;
  linkedActionId?: number;
  linkedLimitId?: number;
  bandBefore?: Band;
  bandAfter?: Band;
  noteEn?: string;
  noteAr?: string;
}

export interface DashboardResponse {
  totalCounterparties: number;
  activeCount: number;
  watchlistCount: number;
  defaultCount: number;
  limitsBreach: number;
  limitsRed: number;
  limitsAmber: number;
  totalExposureSarM: number;
  bySector: Record<string, number>;
  byInternalGrade: Record<string, number>;
  recentCounterparties: CounterpartyResponse[];
  topUtilisedLimits: LimitResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Labels (Saudi-native AR) ───────────────────────────────────

export const STATUS_LABEL: Record<CounterpartyStatus,
  { en: string; ar: string }> = {
  ACTIVE:    { en: 'Active',    ar: 'نشط' },
  WATCHLIST: { en: 'Watchlist', ar: 'قائمة المراقبة' },
  DEFAULT:   { en: 'Default',   ar: 'متعثّر' },
  RESOLVED:  { en: 'Resolved',  ar: 'مُسوّى' },
  DORMANT:   { en: 'Dormant',   ar: 'خامل' },
};

export const STATUS_TONE: Record<CounterpartyStatus,
  'success' | 'warning' | 'danger' | 'neutral'> = {
  ACTIVE:    'success',
  WATCHLIST: 'warning',
  DEFAULT:   'danger',
  RESOLVED:  'neutral',
  DORMANT:   'neutral',
};

export const SECTOR_LABEL: Record<Sector, { en: string; ar: string }> = {
  SOVEREIGN:                  { en: 'Sovereign',                ar: 'سيادي' },
  FINANCIAL_INSTITUTION:      { en: 'Financial institution',    ar: 'مؤسّسة مالية' },
  CORPORATE_LARGE:            { en: 'Corporate (large)',        ar: 'شركة كبرى' },
  CORPORATE_SME:              { en: 'Corporate (SME)',          ar: 'منشأة صغيرة ومتوسطة' },
  RETAIL:                     { en: 'Retail',                   ar: 'أفراد' },
  GOVERNMENT_RELATED_ENTITY:  { en: 'Government-related entity', ar: 'جهة حكومية مرتبطة' },
  MULTILATERAL:               { en: 'Multilateral',             ar: 'متعدّد الأطراف' },
};

export const BAND_LABEL: Record<Band, { en: string; ar: string }> = {
  GREEN:  { en: 'Green',  ar: 'أخضر' },
  AMBER:  { en: 'Amber',  ar: 'كهرماني' },
  RED:    { en: 'Red',    ar: 'أحمر' },
  BREACH: { en: 'Breach', ar: 'تجاوز' },
};

export const BAND_TONE: Record<Band, 'success' | 'warning' | 'danger'> = {
  GREEN:  'success',
  AMBER:  'warning',
  RED:    'danger',
  BREACH: 'danger',
};

export const ACTION_TYPE_LABEL: Record<ActionType,
  { en: string; ar: string }> = {
  WATCHLIST_NEGATIVE: { en: 'Watchlist (negative)',  ar: 'مراقبة سلبية' },
  WATCHLIST_POSITIVE: { en: 'Watchlist (positive)',  ar: 'مراقبة إيجابية' },
  WATCHLIST_REMOVE:   { en: 'Watchlist removed',     ar: 'إزالة من المراقبة' },
  DOWNGRADE:          { en: 'Downgrade',             ar: 'خفض التصنيف' },
  UPGRADE:            { en: 'Upgrade',               ar: 'رفع التصنيف' },
  DEFAULT:            { en: 'Default',               ar: 'تعثّر' },
  RECOVERY:           { en: 'Recovery',              ar: 'تعافٍ' },
  OUTLOOK_CHANGE:     { en: 'Outlook change',        ar: 'تغيير النظرة' },
};

export const ACTION_TYPE_TONE: Record<ActionType,
  'success' | 'warning' | 'danger' | 'neutral'> = {
  WATCHLIST_NEGATIVE: 'warning',
  WATCHLIST_POSITIVE: 'neutral',
  WATCHLIST_REMOVE:   'success',
  DOWNGRADE:          'danger',
  UPGRADE:            'success',
  DEFAULT:            'danger',
  RECOVERY:           'success',
  OUTLOOK_CHANGE:     'neutral',
};

export const EVENT_TYPE_LABEL: Record<EventType, { en: string; ar: string }> = {
  REGISTERED:          { en: 'Registered',          ar: 'تسجيل' },
  RATING_ASSIGNED:     { en: 'Rating assigned',     ar: 'إسناد تصنيف' },
  RATING_ACTION:       { en: 'Rating action',       ar: 'إجراء تصنيف' },
  LIMIT_APPROVED:      { en: 'Limit approved',      ar: 'اعتماد حدّ' },
  LIMIT_REVISED:       { en: 'Limit revised',       ar: 'تعديل حدّ' },
  LIMIT_BAND_CHANGED:  { en: 'Limit band changed',  ar: 'تغيّر نطاق الحدّ' },
  KYC_REFRESHED:       { en: 'KYC refreshed',       ar: 'تحديث اعرف عميلك' },
  RM_HANDOFF:          { en: 'RM handoff',          ar: 'تسليم مدير العلاقة' },
  STATUS_CHANGED:      { en: 'Status changed',      ar: 'تغيير الحالة' },
  INTERNAL_NOTE:       { en: 'Internal note',       ar: 'ملاحظة داخلية' },
};

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/cpty';

export const cptyApi = {
  dashboard: () => api.get<DashboardResponse>(`${PREFIX}/dashboard`),

  // Counterparties
  list: (page = 0, size = 50) =>
    api.get<Page<CounterpartyResponse>>(
      `${PREFIX}/counterparties?page=${page}&size=${size}`),
  get: (id: number) =>
    api.get<CounterpartyResponse>(`${PREFIX}/counterparties/${id}`),
  create: (body: CreateCounterpartyRequest) =>
    api.post<CounterpartyResponse>(`${PREFIX}/counterparties`, body),
  update: (id: number, body: UpdateCounterpartyRequest) =>
    api.put<CounterpartyResponse>(`${PREFIX}/counterparties/${id}`, body),

  // Ratings
  listRatings: (id: number) =>
    api.get<RatingResponse[]>(`${PREFIX}/counterparties/${id}/ratings`),
  assignRating: (id: number, body: AssignRatingRequest) =>
    api.post<RatingResponse>(`${PREFIX}/counterparties/${id}/ratings`, body),

  // Actions
  listActions: (id: number) =>
    api.get<RatingActionResponse[]>(`${PREFIX}/counterparties/${id}/actions`),
  recordAction: (id: number, body: CreateRatingActionRequest) =>
    api.post<RatingActionResponse>(`${PREFIX}/counterparties/${id}/actions`, body),

  // Limits
  listLimits: () => api.get<LimitResponse[]>(`${PREFIX}/limits`),
  listLimitsForCounterparty: (id: number) =>
    api.get<LimitResponse[]>(`${PREFIX}/counterparties/${id}/limits`),
  upsertLimit: (body: UpsertLimitRequest) =>
    api.post<LimitResponse>(`${PREFIX}/limits`, body),
  recordUtilisation: (limitId: number, body: RecordUtilisationRequest) =>
    api.post<LimitResponse>(`${PREFIX}/limits/${limitId}/utilisation`, body),

  // Events
  listEvents: (id: number) =>
    api.get<EventResponse[]>(`${PREFIX}/counterparties/${id}/events`),
};
