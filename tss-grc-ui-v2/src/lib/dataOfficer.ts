// Sprint 33 — Saudi Data Officer Toolkit API client + types.
// Single lib file because the 10 sub-modules share one base URL
// (/api/v1/data-officer) and one mental model.
import { api } from './api';

// ─── Common ─────────────────────────────────────────────────────────
export interface EventResponse {
  id: number;
  entityType: string;
  entityId: number;
  occurredAt: string;
  eventType: string;
  actorUserId?: number;
  statusAfter?: string;
  noteEn?: string;
  noteAr?: string;
}

// ─── 1. DSR ─────────────────────────────────────────────────────────
export type DsrStatus =
  | 'RECEIVED' | 'ID_VERIFIED' | 'IN_PROGRESS'
  | 'FULFILLED' | 'PARTIAL' | 'REJECTED'
  | 'ESCALATED_TO_SDAIA' | 'CLOSED';
export type DsrRequestType =
  | 'ACCESS' | 'RECTIFY' | 'ERASE' | 'RESTRICT' | 'PORTABILITY' | 'OBJECT';

export const DSR_STATUS_LABEL: Record<DsrStatus, { en: string; ar: string }> = {
  RECEIVED:           { en: 'Received',             ar: 'مُستلَم' },
  ID_VERIFIED:        { en: 'ID verified',          ar: 'تحقّق الهوية' },
  IN_PROGRESS:        { en: 'In progress',          ar: 'قيد المعالجة' },
  FULFILLED:          { en: 'Fulfilled',            ar: 'مُنجَز' },
  PARTIAL:            { en: 'Partial',              ar: 'جزئي' },
  REJECTED:           { en: 'Rejected',             ar: 'مرفوض' },
  ESCALATED_TO_SDAIA: { en: 'Escalated to SDAIA',   ar: 'مُحال إلى سدايا' },
  CLOSED:             { en: 'Closed',               ar: 'مُغلَق' },
};
export const DSR_STATUS_TONE: Record<DsrStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  RECEIVED: 'neutral', ID_VERIFIED: 'info', IN_PROGRESS: 'info',
  FULFILLED: 'success', PARTIAL: 'warning', REJECTED: 'danger',
  ESCALATED_TO_SDAIA: 'danger', CLOSED: 'neutral',
};
export const DSR_TYPE_LABEL: Record<DsrRequestType, { en: string; ar: string }> = {
  ACCESS:      { en: 'Access',      ar: 'الاطّلاع' },
  RECTIFY:     { en: 'Rectify',     ar: 'التصحيح' },
  ERASE:       { en: 'Erase',       ar: 'المحو' },
  RESTRICT:    { en: 'Restrict',    ar: 'التقييد' },
  PORTABILITY: { en: 'Portability', ar: 'النقل' },
  OBJECT:      { en: 'Object',      ar: 'الاعتراض' },
};

export interface DsrResponse {
  id: number;
  reference: string;
  requestType: DsrRequestType;
  channel?: string;
  requesterNameEn?: string;
  requesterNameAr?: string;
  requesterEmail?: string;
  requesterPhone?: string;
  requestSummaryEn?: string;
  requestSummaryAr?: string;
  status: DsrStatus;
  receivedAt: string;
  slaDeadline?: string;
  idVerifiedAt?: string;
  idVerificationMethod?: string;
  fulfilledAt?: string;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
  rejectionBasis?: string;
  assignedUserId?: number;
  responseTextEn?: string;
  responseTextAr?: string;
  responseAttachmentEvidenceId?: number;
  hoursLeft?: number;
}
export interface CreateDsrRequest {
  reference: string;
  requestType: DsrRequestType;
  channel?: string;
  requesterNameEn?: string;
  requesterNameAr?: string;
  requesterNationalId?: string;
  requesterEmail?: string;
  requesterPhone?: string;
  requestSummaryEn?: string;
  requestSummaryAr?: string;
  assignedUserId?: number;
}
export interface TransitionDsrRequest {
  toStatus: DsrStatus;
  idVerificationMethod?: string;
  linkedSystemsJson?: string;
  responseTextEn?: string;
  responseTextAr?: string;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
  rejectionBasis?: string;
  responseAttachmentEvidenceId?: number;
}
export function nextDsrStatuses(s: DsrStatus): DsrStatus[] {
  switch (s) {
    case 'RECEIVED':           return ['ID_VERIFIED', 'REJECTED'];
    case 'ID_VERIFIED':        return ['IN_PROGRESS', 'REJECTED'];
    case 'IN_PROGRESS':        return ['FULFILLED', 'PARTIAL', 'REJECTED', 'ESCALATED_TO_SDAIA'];
    case 'FULFILLED':          return ['CLOSED'];
    case 'PARTIAL':            return ['CLOSED', 'IN_PROGRESS'];
    case 'REJECTED':           return ['CLOSED'];
    case 'ESCALATED_TO_SDAIA': return ['CLOSED', 'IN_PROGRESS'];
    default:                   return [];
  }
}

// ─── 2. Consent ────────────────────────────────────────────────────
export type ConsentStatus = 'GRANTED' | 'WITHDRAWN' | 'EXPIRED' | 'SUPERSEDED';
export const CONSENT_STATUS_LABEL: Record<ConsentStatus, { en: string; ar: string }> = {
  GRANTED:    { en: 'Granted',    ar: 'مُمنوحة' },
  WITHDRAWN:  { en: 'Withdrawn',  ar: 'مسحوبة' },
  EXPIRED:    { en: 'Expired',    ar: 'مُنتهية' },
  SUPERSEDED: { en: 'Superseded', ar: 'مُستبدَلة' },
};
export const CONSENT_STATUS_TONE: Record<ConsentStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  GRANTED: 'success', WITHDRAWN: 'warning', EXPIRED: 'neutral', SUPERSEDED: 'neutral',
};
export interface ConsentResponse {
  id: number;
  reference: string;
  dataSubjectId?: string;
  purposeCode: string;
  purposeLabelEn?: string;
  purposeLabelAr?: string;
  noticeVersionId?: number;
  channel?: string;
  consentTextEn?: string;
  consentTextAr?: string;
  grantedAt?: string;
  grantedEvidence?: string;
  withdrawnAt?: string;
  withdrawnReason?: string;
  validUntil?: string;
  status: ConsentStatus;
  legalBasis?: string;
  isSpecialCategory: boolean;
}
export interface CreateConsentRequest {
  reference: string;
  dataSubjectId?: string;
  purposeCode: string;
  purposeLabelEn?: string;
  purposeLabelAr?: string;
  noticeVersionId?: number;
  channel?: string;
  consentTextEn?: string;
  consentTextAr?: string;
  grantedAt?: string;
  grantedEvidence?: string;
  validUntil?: string;
  legalBasis?: string;
  isSpecialCategory?: boolean;
}

// ─── 3. DPIA ───────────────────────────────────────────────────────
export type DpiaStatus =
  | 'DRAFT' | 'DPO_REVIEW' | 'BUSINESS_REVIEW'
  | 'APPROVED' | 'APPROVED_WITH_CONDITIONS' | 'REJECTED' | 'ARCHIVED';
export const DPIA_STATUS_LABEL: Record<DpiaStatus, { en: string; ar: string }> = {
  DRAFT:                    { en: 'Draft',                    ar: 'مسوّدة' },
  DPO_REVIEW:               { en: 'DPO review',               ar: 'مراجعة مسؤول الحماية' },
  BUSINESS_REVIEW:          { en: 'Business review',          ar: 'مراجعة الأعمال' },
  APPROVED:                 { en: 'Approved',                 ar: 'مُعتمد' },
  APPROVED_WITH_CONDITIONS: { en: 'Approved with conditions', ar: 'مُعتمد بشروط' },
  REJECTED:                 { en: 'Rejected',                 ar: 'مرفوض' },
  ARCHIVED:                 { en: 'Archived',                 ar: 'مُؤرشَف' },
};
export const DPIA_STATUS_TONE: Record<DpiaStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', DPO_REVIEW: 'info', BUSINESS_REVIEW: 'info',
  APPROVED: 'success', APPROVED_WITH_CONDITIONS: 'warning',
  REJECTED: 'danger', ARCHIVED: 'neutral',
};
export interface DpiaResponse {
  id: number;
  reference: string;
  titleEn: string;
  titleAr: string;
  processingDescriptionEn?: string;
  processingDescriptionAr?: string;
  triggerReason: string;
  isHighRisk: boolean;
  status: DpiaStatus;
  dataOwnerUserId?: number;
  dpoUserId?: number;
  submittedAt?: string;
  dpoOpinionEn?: string;
  dpoOpinionAr?: string;
  dpoOpinionAt?: string;
  decidedAt?: string;
  decisionRationaleEn?: string;
  decisionRationaleAr?: string;
  conditionsEn?: string;
  conditionsAr?: string;
  nextReviewDue?: string;
  linkedProcessingActivityId?: number;
}
export interface CreateDpiaRequest {
  reference: string;
  titleEn: string;
  titleAr: string;
  processingDescriptionEn?: string;
  processingDescriptionAr?: string;
  triggerReason: string;
  isHighRisk?: boolean;
  dataOwnerUserId?: number;
  dpoUserId?: number;
  linkedProcessingActivityId?: number;
}
export interface TransitionDpiaRequest {
  toStatus: DpiaStatus;
  dpoOpinionEn?: string;
  dpoOpinionAr?: string;
  decisionRationaleEn?: string;
  decisionRationaleAr?: string;
  conditionsEn?: string;
  conditionsAr?: string;
  nextReviewDue?: string;
}
export function nextDpiaStatuses(s: DpiaStatus): DpiaStatus[] {
  switch (s) {
    case 'DRAFT':                    return ['DPO_REVIEW'];
    case 'DPO_REVIEW':               return ['BUSINESS_REVIEW', 'REJECTED', 'DRAFT'];
    case 'BUSINESS_REVIEW':          return ['APPROVED', 'APPROVED_WITH_CONDITIONS', 'REJECTED', 'DPO_REVIEW'];
    case 'APPROVED':                 return ['ARCHIVED'];
    case 'APPROVED_WITH_CONDITIONS': return ['APPROVED', 'ARCHIVED'];
    case 'REJECTED':                 return ['ARCHIVED'];
    default:                         return [];
  }
}

// ─── 4. Breach ─────────────────────────────────────────────────────
export type BreachStatus =
  | 'DETECTED' | 'ASSESSING' | 'NOTIFIED_SDAIA' | 'NOTIFYING_SUBJECTS'
  | 'CONTAINED' | 'REMEDIATING' | 'CLOSED' | 'FALSE_POSITIVE';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export const BREACH_STATUS_LABEL: Record<BreachStatus, { en: string; ar: string }> = {
  DETECTED:           { en: 'Detected',            ar: 'مكتشف' },
  ASSESSING:          { en: 'Assessing',           ar: 'قيد التقييم' },
  NOTIFIED_SDAIA:     { en: 'Notified to SDAIA',   ar: 'أُبلِغت سدايا' },
  NOTIFYING_SUBJECTS: { en: 'Notifying subjects',  ar: 'إبلاغ المتأثرين' },
  CONTAINED:          { en: 'Contained',           ar: 'مُحتوى' },
  REMEDIATING:        { en: 'Remediating',         ar: 'قيد المعالجة' },
  CLOSED:             { en: 'Closed',              ar: 'مُغلَق' },
  FALSE_POSITIVE:     { en: 'False positive',      ar: 'إنذار كاذب' },
};
export const BREACH_STATUS_TONE: Record<BreachStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DETECTED: 'danger', ASSESSING: 'warning', NOTIFIED_SDAIA: 'info',
  NOTIFYING_SUBJECTS: 'info', CONTAINED: 'info',
  REMEDIATING: 'warning', CLOSED: 'success', FALSE_POSITIVE: 'neutral',
};
export interface BreachResponse {
  id: number;
  reference: string;
  titleEn: string;
  titleAr: string;
  detectedAt: string;
  discoveredAt: string;
  sdaiaDeadline?: string;
  status: BreachStatus;
  breachType?: string;
  cause?: string;
  severity?: Severity;
  affectedRecordCount?: number;
  affectedSubjectCount?: number;
  affectedCategoriesJson?: string;
  isSpecialCategory: boolean;
  isCrossBorder: boolean;
  descriptionEn?: string;
  descriptionAr?: string;
  containmentActionsEn?: string;
  containmentActionsAr?: string;
  sdaiaNotifiedAt?: string;
  sdaiaReference?: string;
  subjectsNotifiedAt?: string;
  subjectNotificationTemplateEn?: string;
  subjectNotificationTemplateAr?: string;
  closedAt?: string;
  closureSummaryEn?: string;
  closureSummaryAr?: string;
  linkedIncidentId?: number;
  hoursToSdaiaDeadline?: number;
}
export interface CreateBreachRequest {
  reference: string;
  titleEn: string;
  titleAr: string;
  detectedAt: string;
  breachType?: string;
  cause?: string;
  severity?: Severity;
  affectedRecordCount?: number;
  affectedSubjectCount?: number;
  affectedCategoriesJson?: string;
  isSpecialCategory?: boolean;
  isCrossBorder?: boolean;
  descriptionEn?: string;
  descriptionAr?: string;
  linkedIncidentId?: number;
}
export interface TransitionBreachRequest {
  toStatus: BreachStatus;
  severity?: Severity;
  containmentActionsEn?: string;
  containmentActionsAr?: string;
  sdaiaReference?: string;
  subjectNotificationTemplateEn?: string;
  subjectNotificationTemplateAr?: string;
  closureSummaryEn?: string;
  closureSummaryAr?: string;
}
export function nextBreachStatuses(s: BreachStatus): BreachStatus[] {
  switch (s) {
    case 'DETECTED':           return ['ASSESSING', 'FALSE_POSITIVE'];
    case 'ASSESSING':          return ['NOTIFIED_SDAIA', 'CONTAINED', 'FALSE_POSITIVE'];
    case 'NOTIFIED_SDAIA':     return ['NOTIFYING_SUBJECTS', 'CONTAINED'];
    case 'NOTIFYING_SUBJECTS': return ['CONTAINED'];
    case 'CONTAINED':          return ['REMEDIATING'];
    case 'REMEDIATING':        return ['CLOSED'];
    default:                   return [];
  }
}

// ─── 5. Privacy Notice ─────────────────────────────────────────────
export type NoticeStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'SUPERSEDED' | 'RETIRED';
export const NOTICE_STATUS_LABEL: Record<NoticeStatus, { en: string; ar: string }> = {
  DRAFT:      { en: 'Draft',      ar: 'مسوّدة' },
  IN_REVIEW:  { en: 'In review',  ar: 'قيد المراجعة' },
  PUBLISHED:  { en: 'Published',  ar: 'مَنشور' },
  SUPERSEDED: { en: 'Superseded', ar: 'مُستبدَل' },
  RETIRED:    { en: 'Retired',    ar: 'مُنتهٍ' },
};
export const NOTICE_STATUS_TONE: Record<NoticeStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', IN_REVIEW: 'info', PUBLISHED: 'success',
  SUPERSEDED: 'warning', RETIRED: 'neutral',
};
export interface NoticeResponse {
  id: number; reference: string; versionLabel: string; channel: string;
  titleEn: string; titleAr: string; bodyEn: string; bodyAr: string;
  status: NoticeStatus; effectiveFrom?: string; publishedAt?: string;
  supersededById?: number; legalReviewUserId?: number; requiresReConsent: boolean;
}
export interface CreateNoticeRequest {
  reference: string; versionLabel: string; channel: string;
  titleEn: string; titleAr: string; bodyEn: string; bodyAr: string;
  effectiveFrom?: string; legalReviewUserId?: number; requiresReConsent?: boolean;
}
export interface TransitionNoticeRequest {
  toStatus: NoticeStatus;
  supersededById?: number;
}
export function nextNoticeStatuses(s: NoticeStatus): NoticeStatus[] {
  switch (s) {
    case 'DRAFT':      return ['IN_REVIEW'];
    case 'IN_REVIEW':  return ['PUBLISHED', 'DRAFT'];
    case 'PUBLISHED':  return ['SUPERSEDED', 'RETIRED'];
    case 'SUPERSEDED': return ['RETIRED'];
    default:           return [];
  }
}

// ─── 6. Retention ──────────────────────────────────────────────────
export interface RetentionRuleResponse {
  id: number; reference: string; dataCategory: string;
  descriptionEn?: string; descriptionAr?: string; retentionMonths: number;
  legalBasisEn?: string; legalBasisAr?: string;
  disposalMethod?: string; reviewCadenceMonths?: number;
  nextReviewDue?: string; ownerRole?: string; ownerUserId?: number;
  linkedAssetRef?: string; status: string;
}
export interface CreateRetentionRuleRequest {
  reference: string; dataCategory: string;
  descriptionEn?: string; descriptionAr?: string; retentionMonths: number;
  legalBasisEn?: string; legalBasisAr?: string;
  disposalMethod?: string; reviewCadenceMonths?: number;
  ownerRole?: string; ownerUserId?: number; linkedAssetRef?: string;
}
export interface DisposalJobResponse {
  id: number; reference: string; retentionRuleId: number;
  scheduledFor: string; startedAt?: string; completedAt?: string;
  status: string; recordsDisposed?: number; evidenceHash?: string;
  legalHoldReason?: string;
}
export interface CreateDisposalJobRequest {
  reference: string; retentionRuleId: number; scheduledFor: string;
}

// ─── 7. NDR ─────────────────────────────────────────────────────────
export type NdrStatus = 'DRAFT' | 'INTERNAL_REVIEW' | 'READY' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED' | 'REVISION_REQUESTED';
export const NDR_STATUS_LABEL: Record<NdrStatus, { en: string; ar: string }> = {
  DRAFT:               { en: 'Draft',               ar: 'مسوّدة' },
  INTERNAL_REVIEW:     { en: 'Internal review',     ar: 'مراجعة داخلية' },
  READY:               { en: 'Ready to submit',     ar: 'جاهز للتقديم' },
  SUBMITTED:           { en: 'Submitted',           ar: 'مُقدَّم' },
  ACCEPTED:            { en: 'Accepted',            ar: 'مقبول' },
  REJECTED:            { en: 'Rejected',            ar: 'مرفوض' },
  REVISION_REQUESTED:  { en: 'Revision requested',  ar: 'مطلوب تعديل' },
};
export const NDR_STATUS_TONE: Record<NdrStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', INTERNAL_REVIEW: 'info', READY: 'info', SUBMITTED: 'warning',
  ACCEPTED: 'success', REJECTED: 'danger', REVISION_REQUESTED: 'warning',
};
/** SDAIA NDR annual-submission lifecycle — mirrors backend NDR_ALLOWED. */
export function nextNdrStatuses(s: NdrStatus): NdrStatus[] {
  switch (s) {
    case 'DRAFT':               return ['INTERNAL_REVIEW'];
    case 'INTERNAL_REVIEW':     return ['READY', 'DRAFT'];
    case 'READY':               return ['SUBMITTED'];
    case 'SUBMITTED':           return ['ACCEPTED', 'REJECTED', 'REVISION_REQUESTED'];
    case 'REVISION_REQUESTED':  return ['DRAFT'];
    case 'REJECTED':            return ['DRAFT'];
    default:                    return []; // ACCEPTED is terminal
  }
}
export interface NdrResponse {
  id: number; reference: string; cycleYear: number;
  titleEn?: string; titleAr?: string; status: NdrStatus;
  datasetCount: number; submittedAt?: string; submissionRef?: string;
  acceptedAt?: string; responseSummaryEn?: string; responseSummaryAr?: string;
}
export interface CreateNdrRequest {
  reference: string; cycleYear: number; titleEn?: string; titleAr?: string;
}
export interface TransitionNdrRequest {
  toStatus: NdrStatus;
  datasetCount?: number; submissionRef?: string;
  responseSummaryEn?: string; responseSummaryAr?: string;
}

// ─── 8. Catalog ────────────────────────────────────────────────────
export interface CatalogEntryResponse {
  id: number; reference: string;
  datasetNameEn: string; datasetNameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  domain?: string; sourceSystem?: string; storageLocation?: string;
  classification?: string; containsPii: boolean; containsSpecialCategory: boolean;
  ownerRole?: string; ownerUserId?: number; stewardUserId?: number; custodianTeam?: string;
  rowCountApprox?: number; refreshFrequency?: string; lastProfiledAt?: string;
}
export interface CreateCatalogEntryRequest {
  reference: string; datasetNameEn: string; datasetNameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  domain?: string; sourceSystem?: string; storageLocation?: string;
  classification?: string; containsPii?: boolean; containsSpecialCategory?: boolean;
  ownerRole?: string; ownerUserId?: number; stewardUserId?: number; custodianTeam?: string;
  rowCountApprox?: number; refreshFrequency?: string;
}
export interface LineageResponse { id: number; sourceEntryId: number; targetEntryId: number; transformLabel?: string; }

// ─── 9. Quality ────────────────────────────────────────────────────
export type Dimension = 'COMPLETENESS' | 'UNIQUENESS' | 'ACCURACY' | 'VALIDITY' | 'CONSISTENCY' | 'TIMELINESS' | 'INTEGRITY';
export interface QualityRuleResponse {
  id: number; reference: string; catalogEntryId: number;
  ruleNameEn: string; ruleNameAr?: string;
  dimension: Dimension; ruleExpression?: string;
  thresholdPct?: number; severity?: string; isActive: boolean;
}
export interface CreateQualityRuleRequest {
  reference: string; catalogEntryId: number;
  ruleNameEn: string; ruleNameAr?: string;
  dimension: Dimension; ruleExpression?: string;
  thresholdPct?: number; severity?: string;
}
export interface QualityRunResponse {
  id: number; qualityRuleId: number; runAt: string;
  recordsChecked?: number; recordsFailed?: number;
  passPct?: number; band?: string; notesEn?: string; notesAr?: string;
}
export interface RecordQualityRunRequest {
  qualityRuleId: number;
  recordsChecked?: number; recordsFailed?: number;
  passPct?: number; band?: string; notesEn?: string; notesAr?: string;
}

// ─── 10. Ethics ────────────────────────────────────────────────────
export type EthicsStatus = 'REQUESTED' | 'COMMITTEE_REVIEW' | 'OPINION_DRAFTED' | 'APPROVED' | 'APPROVED_WITH_CONDITIONS' | 'REJECTED' | 'ESCALATED';
export const ETHICS_STATUS_LABEL: Record<EthicsStatus, { en: string; ar: string }> = {
  REQUESTED:                { en: 'Requested',                ar: 'مُقدَّم' },
  COMMITTEE_REVIEW:         { en: 'Committee review',         ar: 'مراجعة اللجنة' },
  OPINION_DRAFTED:          { en: 'Opinion drafted',          ar: 'مسوّدة الرأي' },
  APPROVED:                 { en: 'Approved',                 ar: 'مُعتمد' },
  APPROVED_WITH_CONDITIONS: { en: 'Approved with conditions', ar: 'مُعتمد بشروط' },
  REJECTED:                 { en: 'Rejected',                 ar: 'مرفوض' },
  ESCALATED:                { en: 'Escalated',                ar: 'مُصعَّد' },
};
export const ETHICS_STATUS_TONE: Record<EthicsStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  REQUESTED: 'neutral', COMMITTEE_REVIEW: 'info', OPINION_DRAFTED: 'info',
  APPROVED: 'success', APPROVED_WITH_CONDITIONS: 'success', REJECTED: 'danger', ESCALATED: 'warning',
};
/** SDAIA AI-ethics / NDMO data-ethics committee lifecycle — mirrors backend ETHICS_ALLOWED. */
export function nextEthicsStatuses(s: EthicsStatus): EthicsStatus[] {
  switch (s) {
    case 'REQUESTED':                return ['COMMITTEE_REVIEW', 'REJECTED'];
    case 'COMMITTEE_REVIEW':         return ['OPINION_DRAFTED', 'REJECTED', 'ESCALATED'];
    case 'OPINION_DRAFTED':          return ['APPROVED', 'APPROVED_WITH_CONDITIONS', 'REJECTED'];
    case 'APPROVED_WITH_CONDITIONS': return ['APPROVED'];
    case 'ESCALATED':                return ['COMMITTEE_REVIEW'];
    default:                         return []; // APPROVED / REJECTED are terminal
  }
}
export interface EthicsResponse {
  id: number; reference: string; subjectType?: string; subjectRef?: string;
  titleEn: string; titleAr: string; summaryEn?: string; summaryAr?: string;
  requestedAt: string; status: EthicsStatus;
  riskDimensionsJson?: string; committeeMeetingAt?: string;
  opinionEn?: string; opinionAr?: string;
  conditionsEn?: string; conditionsAr?: string;
  decidedAt?: string; nextReviewDue?: string;
}
export interface CreateEthicsRequest {
  reference: string; subjectType?: string; subjectRef?: string;
  titleEn: string; titleAr: string; summaryEn?: string; summaryAr?: string;
  riskDimensionsJson?: string;
}
export interface TransitionEthicsRequest {
  toStatus: EthicsStatus;
  committeeMeetingAt?: string;
  opinionEn?: string; opinionAr?: string;
  conditionsEn?: string; conditionsAr?: string;
  nextReviewDue?: string;
}

// ─── Dashboard ─────────────────────────────────────────────────────
export interface DashboardResponse {
  dsrOpen: number; dsrOverdue: number;
  consentActive: number; consentWithdrawnLast30: number;
  dpiaOpen: number; dpiaApprovedYtd: number;
  breachOpen: number; breachOverdueSdaia: number;
  noticesPublished: number; noticesNeedingReConsent: number;
  retentionDue: number; disposalScheduled: number;
  ndrCurrentYearStatusInt: number;
  catalogEntries: number; catalogPiiEntries: number;
  qualityRules: number; qualityRedRunsLast30: number;
  ethicsOpen: number;
  dsrByStatus: Record<string, number>;
  breachBySeverity: Record<string, number>;
  dpiaByStatus: Record<string, number>;
  recentDsr: DsrResponse[];
  recentBreaches: BreachResponse[];
}

// ─── REST client ───────────────────────────────────────────────────
const BASE = '/api/v1/data-officer';
export const dataOfficerApi = {
  // DSR
  listDsr:        () => api.get<DsrResponse[]>(`${BASE}/dsr`),
  getDsr:         (id: number) => api.get<DsrResponse>(`${BASE}/dsr/${id}`),
  createDsr:      (req: CreateDsrRequest) => api.post<DsrResponse>(`${BASE}/dsr`, req),
  transitionDsr:  (id: number, req: TransitionDsrRequest) => api.post<DsrResponse>(`${BASE}/dsr/${id}/transition`, req),
  // Consent
  listConsents:   (subject?: string) => api.get<ConsentResponse[]>(`${BASE}/consents${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`),
  recordConsent:  (req: CreateConsentRequest) => api.post<ConsentResponse>(`${BASE}/consents`, req),
  withdrawConsent:(id: number, reason: string) => api.post<ConsentResponse>(`${BASE}/consents/${id}/withdraw`, { reason }),
  // DPIA
  listDpia:       () => api.get<DpiaResponse[]>(`${BASE}/dpia`),
  createDpia:     (req: CreateDpiaRequest) => api.post<DpiaResponse>(`${BASE}/dpia`, req),
  transitionDpia: (id: number, req: TransitionDpiaRequest) => api.post<DpiaResponse>(`${BASE}/dpia/${id}/transition`, req),
  // Breach
  listBreaches:   () => api.get<BreachResponse[]>(`${BASE}/breaches`),
  createBreach:   (req: CreateBreachRequest) => api.post<BreachResponse>(`${BASE}/breaches`, req),
  transitionBreach:(id: number, req: TransitionBreachRequest) => api.post<BreachResponse>(`${BASE}/breaches/${id}/transition`, req),
  // Notice
  listNotices:    () => api.get<NoticeResponse[]>(`${BASE}/notices`),
  createNotice:   (req: CreateNoticeRequest) => api.post<NoticeResponse>(`${BASE}/notices`, req),
  transitionNotice:(id: number, req: TransitionNoticeRequest) => api.post<NoticeResponse>(`${BASE}/notices/${id}/transition`, req),
  // Retention
  listRules:      () => api.get<RetentionRuleResponse[]>(`${BASE}/retention-rules`),
  createRule:     (req: CreateRetentionRuleRequest) => api.post<RetentionRuleResponse>(`${BASE}/retention-rules`, req),
  listDisposals:  () => api.get<DisposalJobResponse[]>(`${BASE}/disposals`),
  scheduleDisposal:(req: CreateDisposalJobRequest) => api.post<DisposalJobResponse>(`${BASE}/disposals`, req),
  // NDR
  listNdr:        () => api.get<NdrResponse[]>(`${BASE}/ndr`),
  createNdr:      (req: CreateNdrRequest) => api.post<NdrResponse>(`${BASE}/ndr`, req),
  transitionNdr:  (id: number, req: TransitionNdrRequest) => api.post<NdrResponse>(`${BASE}/ndr/${id}/transition`, req),
  // Catalog
  listCatalog:    () => api.get<CatalogEntryResponse[]>(`${BASE}/catalog`),
  createCatalogEntry: (req: CreateCatalogEntryRequest) => api.post<CatalogEntryResponse>(`${BASE}/catalog`, req),
  listLineage:    (sourceId: number) => api.get<LineageResponse[]>(`${BASE}/lineage?sourceId=${sourceId}`),
  // Quality
  listQualityRules: () => api.get<QualityRuleResponse[]>(`${BASE}/quality/rules`),
  createQualityRule:(req: CreateQualityRuleRequest) => api.post<QualityRuleResponse>(`${BASE}/quality/rules`, req),
  listQualityRuns: (ruleId?: number) => api.get<QualityRunResponse[]>(`${BASE}/quality/runs${ruleId ? `?ruleId=${ruleId}` : ''}`),
  recordQualityRun:(req: RecordQualityRunRequest) => api.post<QualityRunResponse>(`${BASE}/quality/runs`, req),
  // Ethics
  listEthics:     () => api.get<EthicsResponse[]>(`${BASE}/ethics`),
  createEthics:   (req: CreateEthicsRequest) => api.post<EthicsResponse>(`${BASE}/ethics`, req),
  transitionEthics:(id: number, req: TransitionEthicsRequest) => api.post<EthicsResponse>(`${BASE}/ethics/${id}/transition`, req),
  // Dashboard
  dashboard:      () => api.get<DashboardResponse>(`${BASE}/dashboard`),
  timeline:       (entityType: string, entityId: number) => api.get<EventResponse[]>(`${BASE}/timeline?entityType=${entityType}&entityId=${entityId}`),
};
