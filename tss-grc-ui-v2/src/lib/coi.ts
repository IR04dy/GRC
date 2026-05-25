// Conflict of Interest (COI) Disclosure Register — Sprint 13.
//
// SAMA CG Art.18 + CMA CG Art.41–43 — annual conflict-of-interest
// declarations for board, executives, and high-risk roles.
//
// Saudi-native bilingual page titles:
//   ar (sidebar)  : تضارب المصالح
//   ar (cycle)    : دورة الإقرار السنوي
//   en           : Conflict of Interest Register
//
// Saudi-native terminology:
//   إقرار سنوي                    — annual declaration
//   تنحٍّ                          — recusal
//   اشتباه في تضارب — يستوجب التنحّي  — suspected conflict — recusal required
//   الطرف ذو العلاقة              — related party
//   هدايا ومجاملات                 — gifts & hospitality
//   عضوية مجلس إدارة              — directorship

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type CampaignStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'ARCHIVED';

export type CampaignScope =
  | 'BOARD' | 'EXEC' | 'ALL_STAFF' | 'HIGH_RISK_ROLES';

export type ReviewOutcome =
  | 'PENDING' | 'CLEARED' | 'MITIGATION_REQUIRED'
  | 'RECUSAL_REQUIRED' | 'ESCALATED';

export type ItemType =
  | 'DIRECTORSHIP' | 'SHAREHOLDING' | 'RELATED_PARTY'
  | 'GIFT_HOSPITALITY' | 'OUTSIDE_EMPLOYMENT' | 'POLITICAL_OFFICE'
  | 'FAMILY_RELATIONSHIP' | 'OTHER';

// ─── Requests ────────────────────────────────────────────────────

export interface CampaignCreateRequest {
  code: string;
  titleEn: string;
  titleAr: string;
  fiscalYear: number;
  opensOn: string;   // ISO date
  closesOn: string;  // ISO date
  scope: CampaignScope;
  targetUserCount?: number;
}

export interface DisclosedItemRequest {
  itemType: ItemType;
  counterpartyEn: string;
  counterpartyAr: string;
  relationshipDescriptionEn?: string;
  relationshipDescriptionAr?: string;
  financialValueSar?: number;
  startDate?: string;
  endDate?: string;
  isActive?: boolean;
  mitigationActionEn?: string;
  mitigationActionAr?: string;
}

export interface DeclarationSubmissionRequest {
  campaignId: number;
  employeeNo: string;
  fullNameEn: string;
  fullNameAr: string;
  positionTitleEn: string;
  positionTitleAr: string;
  departmentEn: string;
  departmentAr: string;
  hasConflict: boolean;
  hasPotentialConflict: boolean;
  attestationText: string;
  items: DisclosedItemRequest[];
}

export interface DeclarationReviewRequest {
  reviewOutcome: ReviewOutcome;
  reviewNotesEn: string;
  reviewNotesAr: string;
}

export interface RecusalRequest {
  declarationId?: number;
  userId: number;
  meetingReference: string;
  committeeCode: string;
  recusalDate: string;
  recusalReasonEn: string;
  recusalReasonAr: string;
  agendaItems?: string;
}

// ─── Responses ────────────────────────────────────────────────────

export interface CampaignResponse {
  id: number;
  organisationId: number;
  code: string;
  titleEn: string;
  titleAr: string;
  fiscalYear: number;
  opensOn: string;
  closesOn: string;
  scope: CampaignScope;
  status: CampaignStatus;
  targetUserCount?: number;
  declaredCount?: number;
  openedAt?: string;
  closedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DisclosedItemResponse {
  id: number;
  declarationId: number;
  itemType: ItemType;
  counterpartyEn: string;
  counterpartyAr: string;
  relationshipDescriptionEn?: string;
  relationshipDescriptionAr?: string;
  financialValueSar?: number;
  startDate?: string;
  endDate?: string;
  isActive: boolean;
  mitigationActionEn?: string;
  mitigationActionAr?: string;
}

export interface DeclarationResponse {
  id: number;
  organisationId: number;
  campaignId: number;
  employeeNo: string;
  fullNameEn: string;
  fullNameAr: string;
  positionTitleEn: string;
  positionTitleAr: string;
  departmentEn: string;
  departmentAr: string;
  hasConflict: boolean;
  hasPotentialConflict: boolean;
  attestationText: string;
  submittedAt: string;
  reviewOutcome: ReviewOutcome;
  reviewedByUserId?: number;
  reviewedAt?: string;
  reviewNotesEn?: string;
  reviewNotesAr?: string;
  items: DisclosedItemResponse[];
  createdAt?: string;
  updatedAt?: string;
}

export interface RecusalResponse {
  id: number;
  organisationId: number;
  declarationId?: number;
  userId: number;
  userNameEn?: string;
  userNameAr?: string;
  meetingReference: string;
  committeeCode: string;
  recusalDate: string;
  recusalReasonEn: string;
  recusalReasonAr: string;
  agendaItems?: string;
  createdAt?: string;
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/coi';

export const coiApi = {
  // Campaigns
  listCampaigns: () =>
    api.get<CampaignResponse[]>(`${PREFIX}/campaigns`),
  getCampaign: (id: number) =>
    api.get<CampaignResponse>(`${PREFIX}/campaigns/${id}`),
  createCampaign: (body: CampaignCreateRequest) =>
    api.post<CampaignResponse>(`${PREFIX}/campaigns`, body),
  openCampaign: (id: number) =>
    api.post<CampaignResponse>(`${PREFIX}/campaigns/${id}/open`, {}),
  closeCampaign: (id: number) =>
    api.post<CampaignResponse>(`${PREFIX}/campaigns/${id}/close`, {}),

  // Declarations
  listDeclarations: (campaignId: number) =>
    api.get<DeclarationResponse[]>(`${PREFIX}/campaigns/${campaignId}/declarations`),
  getDeclaration: (id: number) =>
    api.get<DeclarationResponse>(`${PREFIX}/declarations/${id}`),
  submitDeclaration: (body: DeclarationSubmissionRequest) =>
    api.post<DeclarationResponse>(`${PREFIX}/declarations`, body),
  reviewDeclaration: (id: number, body: DeclarationReviewRequest) =>
    api.post<DeclarationResponse>(`${PREFIX}/declarations/${id}/review`, body),

  // Recusals
  recordRecusal: (body: RecusalRequest) =>
    api.post<RecusalResponse>(`${PREFIX}/recusals`, body),
  listRecusalsByUser: (userId: number) =>
    api.get<RecusalResponse[]>(`${PREFIX}/recusals/user/${userId}`),
};

// Convenience aliases matching the spec's function-name list.
export const listCampaigns       = coiApi.listCampaigns;
export const getCampaign         = coiApi.getCampaign;
export const createCampaign      = coiApi.createCampaign;
export const openCampaign        = coiApi.openCampaign;
export const closeCampaign       = coiApi.closeCampaign;
export const listDeclarations    = coiApi.listDeclarations;
export const getDeclaration      = coiApi.getDeclaration;
export const submitDeclaration   = coiApi.submitDeclaration;
export const reviewDeclaration   = coiApi.reviewDeclaration;
export const recordRecusal       = coiApi.recordRecusal;
export const listRecusalsByUser  = coiApi.listRecusalsByUser;

// ─── Saudi-native bilingual display labels ────────────────────────

export const CAMPAIGN_STATUS_LABEL: Record<CampaignStatus, { en: string; ar: string }> = {
  DRAFT:    { en: 'Draft',    ar: 'مسوّدة' },
  OPEN:     { en: 'Open',     ar: 'مفتوحة' },
  CLOSED:   { en: 'Closed',   ar: 'مغلقة' },
  ARCHIVED: { en: 'Archived', ar: 'مؤرشفة' },
};

export const CAMPAIGN_STATUS_TONE: Record<CampaignStatus, 'info' | 'success' | 'neutral' | 'warning'> = {
  DRAFT:    'neutral',
  OPEN:     'success',
  CLOSED:   'info',
  ARCHIVED: 'neutral',
};

export const CAMPAIGN_SCOPE_LABEL: Record<CampaignScope, { en: string; ar: string }> = {
  BOARD:           { en: 'Board members',  ar: 'أعضاء مجلس الإدارة' },
  EXEC:            { en: 'Executives',     ar: 'الإدارة التنفيذية' },
  ALL_STAFF:       { en: 'All staff',      ar: 'جميع الموظفين' },
  HIGH_RISK_ROLES: { en: 'High-risk roles', ar: 'الوظائف عالية المخاطر' },
};

export const REVIEW_OUTCOME_LABEL: Record<ReviewOutcome, { en: string; ar: string }> = {
  PENDING:             { en: 'Pending review',          ar: 'قيد المراجعة' },
  CLEARED:             { en: 'Cleared',                 ar: 'خالٍ من التضارب' },
  MITIGATION_REQUIRED: { en: 'Mitigation required',     ar: 'مطلوب إجراء تخفيف' },
  RECUSAL_REQUIRED:    { en: 'Recusal required',        ar: 'يستوجب التنحّي' },
  ESCALATED:           { en: 'Escalated',               ar: 'مُحالة للإدارة العليا' },
};

// Color tokens: CLEARED green, MITIGATION_REQUIRED yellow,
// RECUSAL_REQUIRED red, ESCALATED purple (mapped to closest available tone).
export const REVIEW_OUTCOME_TONE: Record<ReviewOutcome, 'info' | 'success' | 'warning' | 'danger' | 'neutral'> = {
  PENDING:             'info',
  CLEARED:             'success',
  MITIGATION_REQUIRED: 'warning',
  RECUSAL_REQUIRED:    'danger',
  ESCALATED:           'neutral',   // visually distinct via Badge variant
};

export const ITEM_TYPE_LABEL: Record<ItemType, { en: string; ar: string }> = {
  DIRECTORSHIP:        { en: 'Directorship',       ar: 'عضوية مجلس إدارة' },
  SHAREHOLDING:        { en: 'Shareholding',       ar: 'حصص مساهمة' },
  RELATED_PARTY:       { en: 'Related party',      ar: 'الطرف ذو العلاقة' },
  GIFT_HOSPITALITY:    { en: 'Gifts & hospitality', ar: 'هدايا ومجاملات' },
  OUTSIDE_EMPLOYMENT:  { en: 'Outside employment', ar: 'عمل خارج البنك' },
  POLITICAL_OFFICE:    { en: 'Political office',   ar: 'منصب سياسي/عام' },
  FAMILY_RELATIONSHIP: { en: 'Family relationship', ar: 'علاقة عائلية' },
  OTHER:               { en: 'Other',              ar: 'أخرى' },
};
