// Whistleblower / Ethics Hotline — Sprint 13.
//
// SAMA CG Art.28 + CMA CG Art.87 + NCA ECC-2 5-1-3 — confidential
// reporting channel with retaliation protection.
//
// Saudi-native bilingual page titles:
//   ar (sidebar)  : خط الإبلاغ عن المخالفات
//   ar (operator) : الإبلاغ عن المخالفات الأخلاقيات
//   en           : Whistleblower Hotline / Ethics Reports
//
// Saudi-native terminology:
//   حماية المبلّغين من الانتقام   — retaliation protection
//   بلاغ مجهول الهوية             — anonymous report
//   رمز التتبّع                   — tracking token

import { api } from './api';

export type Channel =
  | 'PUBLIC_PORTAL' | 'INTERNAL_PORTAL' | 'EMAIL' | 'PHONE'
  | 'IN_PERSON' | 'EXTERNAL_PARTNER';

export type ReporterType =
  | 'EMPLOYEE' | 'MANAGER' | 'CONTRACTOR' | 'VENDOR'
  | 'CUSTOMER' | 'EXTERNAL' | 'ANONYMOUS';

export type Category =
  | 'FRAUD' | 'CORRUPTION' | 'CONFLICT_OF_INTEREST' | 'HARASSMENT'
  | 'DISCRIMINATION' | 'SAFETY' | 'ENVIRONMENT' | 'PRIVACY_VIOLATION'
  | 'FINANCIAL_MISCONDUCT' | 'IT_SECURITY'
  | 'REGULATORY_NON_COMPLIANCE' | 'OTHER';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type CaseStatus =
  | 'RECEIVED' | 'TRIAGE' | 'UNDER_INVESTIGATION'
  | 'SUBSTANTIATED' | 'UNSUBSTANTIATED' | 'REFERRED'
  | 'CLOSED_NO_ACTION' | 'CLOSED_REMEDIATED';

export type UpdateType =
  | 'TRIAGE_DECISION' | 'INVESTIGATOR_ASSIGNED' | 'FINDING_RECORDED'
  | 'STATUS_CHANGED' | 'REPORTER_REPLY' | 'INVESTIGATION_NOTE' | 'CLOSURE';

export type RetaliationStatus = 'RECEIVED' | 'VALIDATED' | 'UPHELD' | 'DISMISSED';

// ─── Requests ─────────────────────────────────────────────────────

export interface CaseSubmissionRequest {
  submissionChannel?: Channel;
  reporterType?: ReporterType;
  reporterName?: string;
  reporterEmail?: string;
  reporterPhone?: string;
  reporterEmployeeId?: string;
  isAnonymous?: boolean;
  category: Category;
  severity?: Severity;
  subjectEn: string;
  subjectAr: string;
  narrativeEn: string;
  narrativeAr: string;
  partiesInvolved?: string;
  allegedPeriodStart?: string;
  allegedPeriodEnd?: string;
  involvesSeniorExecutive?: boolean;
}

export interface ReporterReplyRequest {
  bodyEn: string;
  bodyAr: string;
}

export interface TriageRequest {
  triageDecisionEn: string;
  triageDecisionAr: string;
  severity?: Severity;
  assignedInvestigatorUserId?: number;
  involvesSeniorExecutive?: boolean;
}

export interface InvestigationUpdateRequest {
  bodyEn: string;
  bodyAr: string;
  isVisibleToReporter: boolean;
  updateType?: 'INVESTIGATION_NOTE' | 'FINDING_RECORDED';
}

export interface CloseRequest {
  terminalStatus: 'SUBSTANTIATED' | 'UNSUBSTANTIATED' | 'REFERRED'
                | 'CLOSED_NO_ACTION' | 'CLOSED_REMEDIATED';
  closureSummaryEn: string;
  closureSummaryAr: string;
  linkedCapaTaskId?: number;
}

// ─── Responses ────────────────────────────────────────────────────

export interface CaseSubmissionResponse {
  id: number;
  caseCode: string;
  submissionToken: string;
  status: CaseStatus;
  submittedAt: string;
  retentionNoticeEn: string;
  retentionNoticeAr: string;
}

export interface CaseResponse {
  id: number;
  caseCode: string;
  submissionToken: string;
  submissionChannel: Channel;
  reporterType: ReporterType;
  reporterName?: string;
  reporterEmail?: string;
  reporterPhone?: string;
  reporterEmployeeId?: string;
  isAnonymous: boolean;
  category: Category;
  severity: Severity;
  subjectEn: string;
  subjectAr: string;
  narrativeEn: string;
  narrativeAr: string;
  partiesInvolved?: string;
  allegedPeriodStart?: string;
  allegedPeriodEnd?: string;
  involvesSeniorExecutive: boolean;
  status: CaseStatus;
  triageByUserId?: number;
  triageAt?: string;
  triageDecisionEn?: string;
  triageDecisionAr?: string;
  assignedInvestigatorUserId?: number;
  investigationStartedAt?: string;
  closedAt?: string;
  closureSummaryEn?: string;
  closureSummaryAr?: string;
  linkedCapaTaskId?: number;
  submittedAt: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PublicUpdateResponse {
  updateType: UpdateType;
  bodyEn: string;
  bodyAr: string;
  postedAt: string;
  fromReporter: boolean;
}

export interface PublicCaseResponse {
  caseCode: string;
  status: CaseStatus;
  category: Category;
  severity: Severity;
  subjectEn: string;
  subjectAr: string;
  submittedAt: string;
  closedAt?: string;
  updates: PublicUpdateResponse[];
}

export interface UpdateResponse {
  id: number;
  caseId: number;
  updateType: UpdateType;
  actorUserId?: number;
  isVisibleToReporter: boolean;
  bodyEn: string;
  bodyAr: string;
  attachmentRefs?: string;
  createdAt?: string;
}

export interface RetaliationComplaintRequest {
  relatedCaseId: number;
  claimantUserId?: number;
  claimantName?: string;
  claimedActor: string;
  claimedAction: string;
  claimedAtPeriodStart?: string;
}

export interface RetaliationComplaintResponse {
  id: number;
  organisationId: number;
  relatedCaseId: number;
  claimantUserId?: number;
  claimantName?: string;
  claimedActor: string;
  claimedAction: string;
  claimedAtPeriodStart?: string;
  status: RetaliationStatus;
  assignedProtectorUserId?: number;
  createdAt?: string;
  updatedAt?: string;
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/ethics/whistleblower';

export const whistleblowerApi = {
  // Public — no auth required.
  publicSubmit: (body: CaseSubmissionRequest) =>
    api.post<CaseSubmissionResponse>(`${PREFIX}/public/submit`, body, { anonymous: true }),
  publicStatus: (token: string) =>
    api.get<PublicCaseResponse>(`${PREFIX}/public/status/${encodeURIComponent(token)}`, { anonymous: true }),
  publicReply: (token: string, body: ReporterReplyRequest) =>
    api.post<PublicCaseResponse>(`${PREFIX}/public/reply/${encodeURIComponent(token)}`, body, { anonymous: true }),

  // Internal — role-gated.
  list: (filters?: { status?: CaseStatus; severity?: Severity }) => {
    const params = new URLSearchParams();
    if (filters?.status) params.set('status', filters.status);
    if (filters?.severity) params.set('severity', filters.severity);
    const qs = params.toString();
    return api.get<CaseResponse[]>(`${PREFIX}/cases${qs ? `?${qs}` : ''}`);
  },
  stats: () => api.get<Record<string, number>>(`${PREFIX}/cases/stats`),
  get: (id: number) => api.get<CaseResponse>(`${PREFIX}/cases/${id}`),
  updates: (id: number) => api.get<UpdateResponse[]>(`${PREFIX}/cases/${id}/updates`),
  triage: (id: number, body: TriageRequest) =>
    api.post<CaseResponse>(`${PREFIX}/cases/${id}/triage`, body),
  startInvestigation: (id: number, investigatorUserId: number, openingNoteEn?: string, openingNoteAr?: string) => {
    const params = new URLSearchParams();
    params.set('investigatorUserId', String(investigatorUserId));
    if (openingNoteEn) params.set('openingNoteEn', openingNoteEn);
    if (openingNoteAr) params.set('openingNoteAr', openingNoteAr);
    return api.post<CaseResponse>(`${PREFIX}/cases/${id}/start-investigation?${params.toString()}`, {});
  },
  investigationUpdate: (id: number, body: InvestigationUpdateRequest) =>
    api.post<UpdateResponse>(`${PREFIX}/cases/${id}/investigation-update`, body),
  close: (id: number, body: CloseRequest) =>
    api.post<CaseResponse>(`${PREFIX}/cases/${id}/close`, body),
  linkCapa: (id: number, capaTaskId: number) =>
    api.post<CaseResponse>(`${PREFIX}/cases/${id}/link-capa?capaTaskId=${capaTaskId}`, {}),

  // Retaliation sub-flow.
  retaliation: {
    list: (caseId?: number) =>
      api.get<RetaliationComplaintResponse[]>(
        `${PREFIX}/retaliation${caseId ? `?caseId=${caseId}` : ''}`),
    claim: (body: RetaliationComplaintRequest) =>
      api.post<RetaliationComplaintResponse>(`${PREFIX}/retaliation`, body),
    validate: (id: number) =>
      api.post<RetaliationComplaintResponse>(`${PREFIX}/retaliation/${id}/validate`, {}),
    uphold: (id: number) =>
      api.post<RetaliationComplaintResponse>(`${PREFIX}/retaliation/${id}/uphold`, {}),
    dismiss: (id: number) =>
      api.post<RetaliationComplaintResponse>(`${PREFIX}/retaliation/${id}/dismiss`, {}),
  },
};

// ─── Saudi-native bilingual display labels ────────────────────────

export const CATEGORY_LABEL: Record<Category, { en: string; ar: string }> = {
  FRAUD:                    { en: 'Fraud',                    ar: 'غش' },
  CORRUPTION:               { en: 'Corruption',               ar: 'فساد' },
  CONFLICT_OF_INTEREST:     { en: 'Conflict of interest',     ar: 'تضارب مصالح' },
  HARASSMENT:               { en: 'Harassment',               ar: 'تحرّش' },
  DISCRIMINATION:           { en: 'Discrimination',           ar: 'تمييز' },
  SAFETY:                   { en: 'Safety',                   ar: 'سلامة' },
  ENVIRONMENT:              { en: 'Environment',              ar: 'بيئة' },
  PRIVACY_VIOLATION:        { en: 'Privacy violation',        ar: 'انتهاك خصوصية' },
  FINANCIAL_MISCONDUCT:     { en: 'Financial misconduct',     ar: 'سوء سلوك مالي' },
  IT_SECURITY:              { en: 'IT security',              ar: 'أمن معلوماتي' },
  REGULATORY_NON_COMPLIANCE:{ en: 'Regulatory non-compliance',ar: 'عدم التزام تنظيمي' },
  OTHER:                    { en: 'Other',                    ar: 'أخرى' },
};

export const SEVERITY_LABEL: Record<Severity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const SEVERITY_TONE: Record<Severity, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger',
  HIGH:     'danger',
  MEDIUM:   'warning',
  LOW:      'neutral',
};

export const STATUS_LABEL: Record<CaseStatus, { en: string; ar: string }> = {
  RECEIVED:            { en: 'Received',          ar: 'وارد' },
  TRIAGE:              { en: 'Triage',            ar: 'فرز' },
  UNDER_INVESTIGATION: { en: 'Under investigation', ar: 'قيد التحقيق' },
  SUBSTANTIATED:       { en: 'Substantiated',     ar: 'مُثبَت' },
  UNSUBSTANTIATED:     { en: 'Unsubstantiated',   ar: 'غير مُثبَت' },
  REFERRED:            { en: 'Referred',          ar: 'مُحال' },
  CLOSED_NO_ACTION:    { en: 'Closed — no action', ar: 'مُغلَق دون إجراء' },
  CLOSED_REMEDIATED:   { en: 'Closed — remediated', ar: 'مُغلَق ومُعالَج' },
};

export const STATUS_TONE: Record<CaseStatus, 'info' | 'warning' | 'success' | 'danger' | 'neutral'> = {
  RECEIVED:            'info',
  TRIAGE:              'info',
  UNDER_INVESTIGATION: 'warning',
  SUBSTANTIATED:       'danger',
  UNSUBSTANTIATED:     'success',
  REFERRED:            'warning',
  CLOSED_NO_ACTION:    'neutral',
  CLOSED_REMEDIATED:   'success',
};

export const REPORTER_TYPE_LABEL: Record<ReporterType, { en: string; ar: string }> = {
  EMPLOYEE:   { en: 'Employee',    ar: 'موظف' },
  MANAGER:    { en: 'Manager',     ar: 'مدير' },
  CONTRACTOR: { en: 'Contractor',  ar: 'متعاقد' },
  VENDOR:     { en: 'Vendor',      ar: 'مورّد' },
  CUSTOMER:   { en: 'Customer',    ar: 'عميل' },
  EXTERNAL:   { en: 'External',    ar: 'خارجي' },
  ANONYMOUS:  { en: 'Anonymous',   ar: 'مجهول الهوية' },
};

export const CHANNEL_LABEL: Record<Channel, { en: string; ar: string }> = {
  PUBLIC_PORTAL:    { en: 'Public portal',    ar: 'البوّابة العامة' },
  INTERNAL_PORTAL:  { en: 'Internal portal',  ar: 'البوّابة الداخلية' },
  EMAIL:            { en: 'Email',            ar: 'بريد إلكتروني' },
  PHONE:            { en: 'Phone',            ar: 'هاتف' },
  IN_PERSON:        { en: 'In person',        ar: 'حضوري' },
  EXTERNAL_PARTNER: { en: 'External partner', ar: 'شريك خارجي' },
};
