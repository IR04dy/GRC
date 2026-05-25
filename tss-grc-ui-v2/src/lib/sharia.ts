// Sharia Governance & Audit — Sprint 20 PR #3.
//
// Backend: PR #260 (com.tss.grc.module.sharia).
// F8 rules: PR #261 (AR-251..AR-260).
//
// SAMA's Implementing Regulation on Sharia Governance (Aug 2020) + AAOIFI
// GSIFI 1-5 + IFSB-10. SSB scholar register, fatwa lifecycle, internal
// Sharia audit findings, Sharia non-compliance income (SNCI) purification
// tracking, Islamic-product approval pipeline.
//
// Saudi-native bilingual sidebar:
//   ar : الحوكمة الشرعية
//   en : Sharia Governance

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type SsbRole = 'CHAIRMAN' | 'MEMBER' | 'SECRETARY' | 'EXTERNAL_ADVISOR';
export type ScholarStatus =
  | 'ACTIVE' | 'TERM_EXPIRING' | 'TERM_EXPIRED' | 'RESIGNED' | 'REMOVED';

export type ResolutionStatus =
  | 'DRAFT' | 'SSB_REVIEW' | 'APPROVED' | 'ACTIVE' | 'SUPERSEDED' | 'REJECTED';

export type SubjectArea =
  | 'MURABAHA' | 'IJARA' | 'TAWARRUQ' | 'SUKUK' | 'MUDARABA'
  | 'MUSHARAKA' | 'TRANSACTION' | 'OPERATIONAL' | 'OTHER';

export type Severity = 'HIGH' | 'MEDIUM' | 'LOW';

export type FindingStatus = 'OPEN' | 'REMEDIATION' | 'VERIFIED' | 'CLOSED';

export type NcStatus =
  | 'DETECTED' | 'INVESTIGATION' | 'INCOME_PURIFIED' | 'CLOSED';

export type DetectionSource =
  | 'AUDIT' | 'SSB_REVIEW' | 'CUSTOMER_COMPLAINT' | 'OPERATIONS' | 'EXTERNAL';

export type ProductStatus =
  | 'SUBMITTED' | 'SSB_REVIEW' | 'APPROVED_PROVISIONAL' | 'APPROVED_FINAL'
  | 'LAUNCHED' | 'DECOMMISSIONED' | 'REJECTED';

export type ProductStructure =
  | 'MURABAHA' | 'IJARA' | 'TAWARRUQ' | 'SUKUK' | 'MUDARABA'
  | 'MUSHARAKA' | 'SALAM' | 'ISTISNA' | 'OTHER';

// ─── Requests ────────────────────────────────────────────────────

export interface CreateScholarRequest {
  reference: string;
  fullNameEn: string;
  fullNameAr: string;
  ssbRole?: SsbRole;
  termStart?: string;
  termEnd?: string;
  credentialsEn?: string;
  credentialsAr?: string;
  annualFeeSar?: number;
  email?: string;
  phone?: string;
}

export interface DeclareCoiRequest {
  coiNotesEn?: string;
  coiNotesAr?: string;
}

export interface CreateResolutionRequest {
  reference: string;
  subjectArea: SubjectArea;
  titleEn: string;
  titleAr: string;
  bodyEn?: string;
  bodyAr?: string;
  rationaleEn?: string;
  rationaleAr?: string;
  signingScholars?: string;
  effectiveDate?: string;
  supersedesResolutionId?: number;
}

export interface TransitionResolutionRequest {
  toStatus: ResolutionStatus;
  reasonEn?: string;
  reasonAr?: string;
}

export interface RaiseAuditFindingRequest {
  reference: string;
  auditCycleLabel?: string;
  severity: Severity;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  rootCauseEn?: string;
  rootCauseAr?: string;
  remediationPlanEn?: string;
  remediationPlanAr?: string;
  linkedResolutionId?: number;
  productLine?: string;
  remediationDueAt?: string;
  assignedToUserId?: number;
}

export interface TransitionAuditFindingRequest {
  toStatus: FindingStatus;
  noteEn?: string;
  noteAr?: string;
}

export interface DetectNcEventRequest {
  reference: string;
  severity: Severity;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  productLine?: string;
  linkedResolutionId?: number;
  detectionSource?: DetectionSource;
  snciAmountSar: number;
  affectedCustomerCount?: number;
  assignedToUserId?: number;
}

export interface PurifyNcEventRequest {
  purificationDestination: string;
  charityReceiptRef: string;
  purifiedAt: string;
  noteEn?: string;
  noteAr?: string;
}

export interface SubmitProductRequest {
  reference: string;
  productNameEn: string;
  productNameAr: string;
  structure: ProductStructure;
  descriptionEn?: string;
  descriptionAr?: string;
  businessOwnerUserId?: number;
  shariaOfficerUserId?: number;
}

export interface TransitionProductRequest {
  toStatus: ProductStatus;
  linkedResolutionId?: number;
  reasonEn?: string;
  reasonAr?: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface ScholarResponse {
  id: number;
  reference: string;
  fullNameEn: string;
  fullNameAr: string;
  ssbRole: SsbRole;
  status: ScholarStatus;
  termStart?: string;
  termEnd?: string;
  credentialsEn?: string;
  credentialsAr?: string;
  coiDeclaredAt?: string;
  coiNotesEn?: string;
  coiNotesAr?: string;
  annualFeeSar?: number;
  meetingsAttendedYtd?: number;
  email?: string;
  phone?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ResolutionResponse {
  id: number;
  reference: string;
  subjectArea: SubjectArea;
  titleEn: string;
  titleAr: string;
  bodyEn?: string;
  bodyAr?: string;
  rationaleEn?: string;
  rationaleAr?: string;
  status: ResolutionStatus;
  submittedAt?: string;
  ssbReviewStartedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  effectiveDate?: string;
  supersedesResolutionId?: number;
  signingScholars?: string;
  meetingId?: number;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
}

export interface AuditFindingResponse {
  id: number;
  reference: string;
  auditCycleLabel?: string;
  severity: Severity;
  status: FindingStatus;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  rootCauseEn?: string;
  rootCauseAr?: string;
  remediationPlanEn?: string;
  remediationPlanAr?: string;
  linkedResolutionId?: number;
  productLine?: string;
  raisedAt?: string;
  remediationDueAt?: string;
  verifiedAt?: string;
  closedAt?: string;
  assignedToUserId?: number;
}

export interface NcEventResponse {
  id: number;
  reference: string;
  status: NcStatus;
  severity: Severity;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  productLine?: string;
  linkedResolutionId?: number;
  detectedAt: string;
  detectionSource?: DetectionSource;
  snciAmountSar: number;
  purificationDestination?: string;
  purifiedAt?: string;
  charityReceiptRef?: string;
  closedAt?: string;
  affectedCustomerCount?: number;
  assignedToUserId?: number;
}

export interface ProductResponse {
  id: number;
  reference: string;
  productNameEn: string;
  productNameAr: string;
  structure: ProductStructure;
  status: ProductStatus;
  descriptionEn?: string;
  descriptionAr?: string;
  linkedResolutionId?: number;
  submittedAt?: string;
  ssbReviewStartedAt?: string;
  approvedProvisionalAt?: string;
  approvedFinalAt?: string;
  rejectedAt?: string;
  launchedAt?: string;
  decommissionedAt?: string;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
  businessOwnerUserId?: number;
  shariaOfficerUserId?: number;
}

export interface EventResponse {
  id: number;
  scholarId?: number;
  resolutionId?: number;
  auditFindingId?: number;
  ncEventId?: number;
  productApprovalId?: number;
  occurredAt: string;
  eventType: string;
  actorUserId?: number;
  actorName?: string;
  statusAfter?: string;
  noteEn?: string;
  noteAr?: string;
}

export interface DashboardResponse {
  activeScholars: number;
  termsExpiring90d: number;
  activeResolutions: number;
  resolutionsInReview: number;
  openFindingsHigh: number;
  openFindingsTotal: number;
  ncEventsPendingPurification: number;
  snciOutstandingSar: number;
  productsInReview: number;
  productsLaunched: number;
  resolutionsByStatus: Record<string, number>;
  findingsBySeverity: Record<string, number>;
  productsByStatus: Record<string, number>;
  recentResolutions: ResolutionResponse[];
  openHighFindings: AuditFindingResponse[];
  openNcEvents: NcEventResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Saudi-native labels ─────────────────────────────────────────

export const SSB_ROLE_LABEL: Record<SsbRole, { en: string; ar: string }> = {
  CHAIRMAN:         { en: 'Chairman',         ar: 'رئيس الهيئة' },
  MEMBER:           { en: 'Member',           ar: 'عضو' },
  SECRETARY:        { en: 'Secretary',        ar: 'أمين السرّ' },
  EXTERNAL_ADVISOR: { en: 'External advisor', ar: 'مستشار خارجي' },
};

export const SCHOLAR_STATUS_LABEL: Record<ScholarStatus,
  { en: string; ar: string }> = {
  ACTIVE:         { en: 'Active',           ar: 'نشط' },
  TERM_EXPIRING:  { en: 'Term expiring',    ar: 'فترة على وشك الانتهاء' },
  TERM_EXPIRED:   { en: 'Term expired',     ar: 'منتهية الفترة' },
  RESIGNED:       { en: 'Resigned',         ar: 'مستقيل' },
  REMOVED:        { en: 'Removed',          ar: 'مُعزَل' },
};

export const SCHOLAR_STATUS_TONE: Record<ScholarStatus,
  'success' | 'warning' | 'danger' | 'neutral'> = {
  ACTIVE:         'success',
  TERM_EXPIRING:  'warning',
  TERM_EXPIRED:   'danger',
  RESIGNED:       'neutral',
  REMOVED:        'danger',
};

export const RESOLUTION_STATUS_LABEL: Record<ResolutionStatus,
  { en: string; ar: string }> = {
  DRAFT:       { en: 'Draft',       ar: 'مسوّدة' },
  SSB_REVIEW:  { en: 'SSB review',  ar: 'قيد مراجعة الهيئة' },
  APPROVED:    { en: 'Approved',    ar: 'مُعتمدة' },
  ACTIVE:      { en: 'Active',      ar: 'نافذة' },
  SUPERSEDED:  { en: 'Superseded',  ar: 'مُستبدَلة' },
  REJECTED:    { en: 'Rejected',    ar: 'مرفوضة' },
};

export const RESOLUTION_STATUS_TONE: Record<ResolutionStatus,
  'success' | 'warning' | 'danger' | 'neutral' | 'info'> = {
  DRAFT:       'neutral',
  SSB_REVIEW:  'info',
  APPROVED:    'success',
  ACTIVE:      'success',
  SUPERSEDED:  'neutral',
  REJECTED:    'danger',
};

export const SUBJECT_AREA_LABEL: Record<SubjectArea,
  { en: string; ar: string }> = {
  MURABAHA:    { en: 'Murabaha',    ar: 'مرابحة' },
  IJARA:       { en: 'Ijara',       ar: 'إجارة' },
  TAWARRUQ:    { en: 'Tawarruq',    ar: 'تورّق' },
  SUKUK:       { en: 'Sukuk',       ar: 'صكوك' },
  MUDARABA:    { en: 'Mudaraba',    ar: 'مضاربة' },
  MUSHARAKA:   { en: 'Musharaka',   ar: 'مشاركة' },
  TRANSACTION: { en: 'Transaction', ar: 'معاملة' },
  OPERATIONAL: { en: 'Operational', ar: 'تشغيلي' },
  OTHER:       { en: 'Other',       ar: 'أخرى' },
};

export const SEVERITY_LABEL: Record<Severity, { en: string; ar: string }> = {
  HIGH:   { en: 'High',   ar: 'عالية' },
  MEDIUM: { en: 'Medium', ar: 'متوسّطة' },
  LOW:    { en: 'Low',    ar: 'منخفضة' },
};

export const SEVERITY_TONE: Record<Severity,
  'danger' | 'warning' | 'neutral'> = {
  HIGH:   'danger',
  MEDIUM: 'warning',
  LOW:    'neutral',
};

export const FINDING_STATUS_LABEL: Record<FindingStatus,
  { en: string; ar: string }> = {
  OPEN:         { en: 'Open',          ar: 'مفتوحة' },
  REMEDIATION:  { en: 'Remediation',   ar: 'قيد المعالجة' },
  VERIFIED:     { en: 'Verified',      ar: 'تم التحقّق' },
  CLOSED:       { en: 'Closed',        ar: 'مغلقة' },
};

export const FINDING_STATUS_TONE: Record<FindingStatus,
  'danger' | 'warning' | 'success' | 'neutral'> = {
  OPEN:         'danger',
  REMEDIATION:  'warning',
  VERIFIED:     'success',
  CLOSED:       'neutral',
};

export const NC_STATUS_LABEL: Record<NcStatus, { en: string; ar: string }> = {
  DETECTED:         { en: 'Detected',         ar: 'مرصودة' },
  INVESTIGATION:    { en: 'Investigation',    ar: 'قيد التحقيق' },
  INCOME_PURIFIED:  { en: 'Income purified',  ar: 'تم تطهير الدخل' },
  CLOSED:           { en: 'Closed',           ar: 'مغلقة' },
};

export const NC_STATUS_TONE: Record<NcStatus,
  'danger' | 'warning' | 'success' | 'neutral'> = {
  DETECTED:         'danger',
  INVESTIGATION:    'warning',
  INCOME_PURIFIED:  'success',
  CLOSED:           'neutral',
};

export const DETECTION_SOURCE_LABEL: Record<DetectionSource,
  { en: string; ar: string }> = {
  AUDIT:              { en: 'Audit',              ar: 'مراجعة' },
  SSB_REVIEW:         { en: 'SSB review',         ar: 'مراجعة الهيئة' },
  CUSTOMER_COMPLAINT: { en: 'Customer complaint', ar: 'شكوى عميل' },
  OPERATIONS:         { en: 'Operations',         ar: 'العمليات' },
  EXTERNAL:           { en: 'External auditor',   ar: 'مراجع خارجي' },
};

export const PRODUCT_STATUS_LABEL: Record<ProductStatus,
  { en: string; ar: string }> = {
  SUBMITTED:            { en: 'Submitted',            ar: 'مقدَّم' },
  SSB_REVIEW:           { en: 'SSB review',           ar: 'قيد مراجعة الهيئة' },
  APPROVED_PROVISIONAL: { en: 'Approved (pilot)',     ar: 'مُعتمد مبدئياً (تجريبي)' },
  APPROVED_FINAL:       { en: 'Approved (final)',     ar: 'مُعتمد نهائياً' },
  LAUNCHED:             { en: 'Launched',             ar: 'مُطلَق' },
  DECOMMISSIONED:       { en: 'Decommissioned',       ar: 'مُتوقّف' },
  REJECTED:             { en: 'Rejected',             ar: 'مرفوض' },
};

export const PRODUCT_STATUS_TONE: Record<ProductStatus,
  'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  SUBMITTED:            'neutral',
  SSB_REVIEW:           'info',
  APPROVED_PROVISIONAL: 'warning',
  APPROVED_FINAL:       'success',
  LAUNCHED:             'success',
  DECOMMISSIONED:       'neutral',
  REJECTED:             'danger',
};

export const PRODUCT_STRUCTURE_LABEL: Record<ProductStructure,
  { en: string; ar: string }> = {
  MURABAHA:  { en: 'Murabaha',  ar: 'مرابحة' },
  IJARA:     { en: 'Ijara',     ar: 'إجارة' },
  TAWARRUQ:  { en: 'Tawarruq',  ar: 'تورّق' },
  SUKUK:     { en: 'Sukuk',     ar: 'صكوك' },
  MUDARABA:  { en: 'Mudaraba',  ar: 'مضاربة' },
  MUSHARAKA: { en: 'Musharaka', ar: 'مشاركة' },
  SALAM:     { en: 'Salam',     ar: 'سَلَم' },
  ISTISNA:   { en: 'Istisna',   ar: 'استصناع' },
  OTHER:     { en: 'Other',     ar: 'أخرى' },
};

// ─── State-machine helpers (mirror backend ALLOWED maps) ─────────

export function nextResolutionStatuses(s: ResolutionStatus): ResolutionStatus[] {
  switch (s) {
    case 'DRAFT':      return ['SSB_REVIEW', 'REJECTED'];
    case 'SSB_REVIEW': return ['APPROVED', 'REJECTED'];
    case 'APPROVED':   return ['ACTIVE', 'REJECTED'];
    case 'ACTIVE':     return ['SUPERSEDED'];
    default:           return [];
  }
}

export function nextFindingStatuses(s: FindingStatus): FindingStatus[] {
  switch (s) {
    case 'OPEN':         return ['REMEDIATION', 'VERIFIED'];
    case 'REMEDIATION':  return ['VERIFIED'];
    case 'VERIFIED':     return ['CLOSED'];
    default:             return [];
  }
}

export function nextNcStatuses(s: NcStatus): NcStatus[] {
  switch (s) {
    case 'DETECTED':         return ['INVESTIGATION'];
    case 'INVESTIGATION':    return ['INCOME_PURIFIED'];
    case 'INCOME_PURIFIED':  return ['CLOSED'];
    default:                 return [];
  }
}

export function nextProductStatuses(s: ProductStatus): ProductStatus[] {
  switch (s) {
    case 'SUBMITTED':             return ['SSB_REVIEW', 'REJECTED'];
    case 'SSB_REVIEW':            return ['APPROVED_PROVISIONAL', 'APPROVED_FINAL', 'REJECTED'];
    case 'APPROVED_PROVISIONAL':  return ['APPROVED_FINAL', 'REJECTED'];
    case 'APPROVED_FINAL':        return ['LAUNCHED', 'REJECTED'];
    case 'LAUNCHED':              return ['DECOMMISSIONED'];
    default:                      return [];
  }
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/sharia';

export const shariaApi = {
  dashboard: () => api.get<DashboardResponse>(`${PREFIX}/dashboard`),

  // Scholars
  listScholars: (page = 0, size = 50) =>
    api.get<Page<ScholarResponse>>(`${PREFIX}/scholars?page=${page}&size=${size}`),
  getScholar: (id: number) =>
    api.get<ScholarResponse>(`${PREFIX}/scholars/${id}`),
  createScholar: (body: CreateScholarRequest) =>
    api.post<ScholarResponse>(`${PREFIX}/scholars`, body),
  declareCoi: (id: number, body: DeclareCoiRequest) =>
    api.post<ScholarResponse>(`${PREFIX}/scholars/${id}/coi`, body),

  // Resolutions
  listResolutions: (page = 0, size = 50) =>
    api.get<Page<ResolutionResponse>>(`${PREFIX}/resolutions?page=${page}&size=${size}`),
  getResolution: (id: number) =>
    api.get<ResolutionResponse>(`${PREFIX}/resolutions/${id}`),
  createResolution: (body: CreateResolutionRequest) =>
    api.post<ResolutionResponse>(`${PREFIX}/resolutions`, body),
  transitionResolution: (id: number, body: TransitionResolutionRequest) =>
    api.post<ResolutionResponse>(`${PREFIX}/resolutions/${id}/transition`, body),
  listResolutionEvents: (id: number) =>
    api.get<EventResponse[]>(`${PREFIX}/resolutions/${id}/events`),

  // Audit Findings
  listFindings: (page = 0, size = 50) =>
    api.get<Page<AuditFindingResponse>>(`${PREFIX}/audit-findings?page=${page}&size=${size}`),
  raiseFinding: (body: RaiseAuditFindingRequest) =>
    api.post<AuditFindingResponse>(`${PREFIX}/audit-findings`, body),
  transitionFinding: (id: number, body: TransitionAuditFindingRequest) =>
    api.post<AuditFindingResponse>(`${PREFIX}/audit-findings/${id}/transition`, body),

  // NC Events
  listNcEvents: (page = 0, size = 50) =>
    api.get<Page<NcEventResponse>>(`${PREFIX}/nc-events?page=${page}&size=${size}`),
  detectNcEvent: (body: DetectNcEventRequest) =>
    api.post<NcEventResponse>(`${PREFIX}/nc-events`, body),
  transitionNcEvent: (id: number, body: { toStatus: NcStatus; noteEn?: string; noteAr?: string }) =>
    api.post<NcEventResponse>(`${PREFIX}/nc-events/${id}/transition`, body),
  purifyNcEvent: (id: number, body: PurifyNcEventRequest) =>
    api.post<NcEventResponse>(`${PREFIX}/nc-events/${id}/purify`, body),

  // Product Approvals
  listProducts: (page = 0, size = 50) =>
    api.get<Page<ProductResponse>>(`${PREFIX}/product-approvals?page=${page}&size=${size}`),
  submitProduct: (body: SubmitProductRequest) =>
    api.post<ProductResponse>(`${PREFIX}/product-approvals`, body),
  transitionProduct: (id: number, body: TransitionProductRequest) =>
    api.post<ProductResponse>(`${PREFIX}/product-approvals/${id}/transition`, body),
};
