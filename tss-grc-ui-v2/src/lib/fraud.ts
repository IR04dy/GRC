// Fraud Risk Management — Sprint 14.
//
// SAMA Anti-Fraud Framework 2018 (Art.4-9) + SAMA AML/CFT Art.25 +
// SAMA Cyber Subdomain 3.3.13 — codified fraud typology catalog +
// investigation lifecycle.
//
// Saudi-native bilingual labels:
//   ar (sidebar)    : إدارة مخاطر الاحتيال
//   ar (operator)   : قضايا الاحتيال
//   en              : Fraud Risk Management
//
// Saudi-native terminology used in seed + UI:
//   احتيال البريد الإلكتروني للأعمال          — Business Email Compromise (BEC)
//   الدفع الفوري عبر سداد/IPS                — SADAD/IPS push-payment fraud
//   نسخ بطاقات الصراف الآلي                  — ATM card skimming
//   الاستيلاء على الحساب                      — Account takeover (ATO)
//   الاختلاس الداخلي                          — Internal embezzlement
//   تحويل برقي احتيالي (سويفت)               — SWIFT MT103 wire-transfer fraud
//   حساب وسيط لغسيل أموال                    — Money mule account
//   بلاغ معاملة مشبوهة (ساما/الإدارة العامة)   — SAFIU STR

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type CaseStatus =
  | 'DETECTED' | 'INVESTIGATING' | 'CONFIRMED'
  | 'CLEARED' | 'FALSE_POSITIVE' | 'CLOSED';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type SamaCategory =
  | 'EXTERNAL' | 'INTERNAL' | 'CYBER' | 'CARD' | 'PAYMENT' | 'OTHER';

export type PerpetratorOrigin = 'EXTERNAL' | 'INTERNAL';

export type DetectionSource =
  | 'DETECTION_SYSTEM' | 'CUSTOMER_REPORT' | 'INTERNAL_REPORT'
  | 'AUDIT_FINDING' | 'EXTERNAL_PARTY' | 'OTHER';

export type UpdateType =
  | 'STATUS_CHANGED' | 'TRIAGE_DECISION' | 'EVIDENCE_RECORDED'
  | 'FINDING_RECORDED' | 'STR_FILED' | 'LAW_ENFORCEMENT_NOTIFIED'
  | 'RECOVERY_RECORDED' | 'CLOSURE';

export type Outcome = 'CONFIRMED' | 'CLEARED' | 'FALSE_POSITIVE';

// ─── Requests ────────────────────────────────────────────────────

export interface CreateCaseRequest {
  typologyId: number;
  subjectEn: string;
  subjectAr: string;
  narrativeEn?: string;
  narrativeAr?: string;
  detectionSource: DetectionSource;
  detectionReference?: string;
  incidentDate?: string;          // ISO date
  estimatedLossSar?: number;
  affectedCustomerCount?: number;
  assignedInvestigatorUserId?: number;
}

export interface TriageRequest {
  severity: Severity;
  proceedToInvestigation: boolean;
  triageNotesEn?: string;
  triageNotesAr?: string;
  assignedInvestigatorUserId?: number;
}

export interface InvestigationUpdateRequest {
  updateType: 'EVIDENCE_RECORDED' | 'FINDING_RECORDED' | 'STR_FILED'
            | 'LAW_ENFORCEMENT_NOTIFIED' | 'RECOVERY_RECORDED';
  noteEn?: string;
  noteAr?: string;
  evidenceReference?: string;
  recoveredAmountSar?: number;
  strReference?: string;
  lawEnforcementReference?: string;
}

export interface OutcomeRequest {
  outcome: Outcome;
  confirmedLossSar?: number;
  findingsEn?: string;
  findingsAr?: string;
}

export interface CloseCaseRequest {
  closureNotesEn: string;
  closureNotesAr: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface TypologyResponse {
  id: number;
  code: string;
  nameEn: string;
  nameAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  samaCategoryCode: SamaCategory;
  perpetratorOrigin: PerpetratorOrigin;
  primaryChannel: string;
  typicalSeverity: Severity;
  requiresStrFiling: boolean;
  isActive: boolean;
}

export interface CaseResponse {
  id: number;
  externalReference: string;
  typologyId: number;
  typologyCodeSnapshot: string;
  typologyNameEn?: string;
  typologyNameAr?: string;
  subjectEn: string;
  subjectAr: string;
  narrativeEn?: string;
  narrativeAr?: string;
  status: CaseStatus;
  severity: Severity;
  detectionSource: DetectionSource;
  detectionReference?: string;
  incidentDate?: string;
  detectedAt: string;
  reportedAt?: string;
  estimatedLossSar?: number;
  confirmedLossSar?: number;
  recoveredAmountSar?: number;
  lossCurrency: string;
  affectedCustomerCount: number;
  assignedInvestigatorUserId?: number;
  triagedAt?: string;
  triagedByUserId?: number;
  investigationStartedAt?: string;
  outcomeSetAt?: string;
  outcomeSetByUserId?: number;
  closedAt?: string;
  closedByUserId?: number;
  closureNotesEn?: string;
  closureNotesAr?: string;
  strFiled: boolean;
  strReference?: string;
  strFiledAt?: string;
  lawEnforcementEngaged: boolean;
  lawEnforcementReference?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CaseUpdateResponse {
  id: number;
  caseId: number;
  updateType: UpdateType;
  occurredAt: string;
  actorUserId?: number;
  actorName?: string;
  noteEn?: string;
  noteAr?: string;
  statusAfter?: CaseStatus;
  evidenceReference?: string;
}

export interface DashboardResponse {
  openCases: number;
  detectedCount: number;
  triageCount: number;          // always 0 — TRIAGE is event-only
  investigatingCount: number;
  confirmedCount: number;
  closedCount: number;
  confirmedLossYtdSar: number;
  recentCases: CaseResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Display label maps (Saudi-native AR, EN parallel) ───────────

export const CASE_STATUS_LABEL: Record<CaseStatus, { en: string; ar: string }> = {
  DETECTED:       { en: 'Detected',       ar: 'مرصودة' },
  INVESTIGATING:  { en: 'Investigating',  ar: 'قيد التحقيق' },
  CONFIRMED:      { en: 'Confirmed',      ar: 'مؤكَّدة' },
  CLEARED:        { en: 'Cleared',        ar: 'تمت تبرئتها' },
  FALSE_POSITIVE: { en: 'False positive', ar: 'إنذار كاذب' },
  CLOSED:         { en: 'Closed',         ar: 'مغلقة' },
};

export const CASE_STATUS_TONE: Record<CaseStatus,
  'neutral' | 'warning' | 'danger' | 'success' | 'info'> = {
  DETECTED:       'warning',
  INVESTIGATING:  'info',
  CONFIRMED:      'danger',
  CLEARED:        'success',
  FALSE_POSITIVE: 'neutral',
  CLOSED:         'neutral',
};

export const SEVERITY_LABEL: Record<Severity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرجة' },
  HIGH:     { en: 'High',     ar: 'مرتفعة' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسطة' },
  LOW:      { en: 'Low',      ar: 'منخفضة' },
};

export const SEVERITY_TONE: Record<Severity,
  'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low'> = {
  CRITICAL: 'risk-crit',
  HIGH:     'risk-high',
  MEDIUM:   'risk-med',
  LOW:      'risk-low',
};

export const SAMA_CATEGORY_LABEL: Record<SamaCategory, { en: string; ar: string }> = {
  EXTERNAL: { en: 'External',           ar: 'خارجي' },
  INTERNAL: { en: 'Internal',           ar: 'داخلي' },
  CYBER:    { en: 'Cyber',              ar: 'سيبراني' },
  CARD:     { en: 'Card',               ar: 'بطاقات' },
  PAYMENT:  { en: 'Payment',            ar: 'مدفوعات' },
  OTHER:    { en: 'Other',              ar: 'أخرى' },
};

export const DETECTION_SOURCE_LABEL: Record<DetectionSource, { en: string; ar: string }> = {
  DETECTION_SYSTEM: { en: 'Detection system',           ar: 'نظام كشف آلي' },
  CUSTOMER_REPORT:  { en: 'Customer report',            ar: 'بلاغ من عميل' },
  INTERNAL_REPORT:  { en: 'Internal report',            ar: 'بلاغ داخلي' },
  AUDIT_FINDING:    { en: 'Audit finding',              ar: 'ملاحظة تدقيق' },
  EXTERNAL_PARTY:   { en: 'External party (SAMA/Police/Bank)', ar: 'جهة خارجية (ساما/شرطة/بنك)' },
  OTHER:            { en: 'Other',                      ar: 'أخرى' },
};

export const UPDATE_TYPE_LABEL: Record<UpdateType, { en: string; ar: string }> = {
  STATUS_CHANGED:           { en: 'Status changed',              ar: 'تغيير الحالة' },
  TRIAGE_DECISION:          { en: 'Triage decision',             ar: 'قرار الفرز' },
  EVIDENCE_RECORDED:        { en: 'Evidence recorded',           ar: 'تسجيل دليل' },
  FINDING_RECORDED:         { en: 'Finding recorded',            ar: 'تسجيل ملاحظة تحقيق' },
  STR_FILED:                { en: 'STR filed (SAFIU)',           ar: 'إيداع بلاغ معاملة مشبوهة' },
  LAW_ENFORCEMENT_NOTIFIED: { en: 'Law enforcement notified',    ar: 'إبلاغ جهة إنفاذ' },
  RECOVERY_RECORDED:        { en: 'Recovery recorded',           ar: 'تسجيل استرداد' },
  CLOSURE:                  { en: 'Case closure',                ar: 'إغلاق القضية' },
};

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/fraud';

export const fraudApi = {
  listTypologies: () =>
    api.get<TypologyResponse[]>(`${PREFIX}/typologies`),

  createCase: (body: CreateCaseRequest) =>
    api.post<CaseResponse>(`${PREFIX}/cases`, body),
  listCases: (page = 0, size = 25) =>
    api.get<Page<CaseResponse>>(`${PREFIX}/cases?page=${page}&size=${size}`),
  getCase: (id: number) =>
    api.get<CaseResponse>(`${PREFIX}/cases/${id}`),
  listTimeline: (id: number) =>
    api.get<CaseUpdateResponse[]>(`${PREFIX}/cases/${id}/timeline`),

  triage: (id: number, body: TriageRequest) =>
    api.post<CaseResponse>(`${PREFIX}/cases/${id}/triage`, body),
  recordUpdate: (id: number, body: InvestigationUpdateRequest) =>
    api.post<CaseUpdateResponse>(`${PREFIX}/cases/${id}/updates`, body),
  setOutcome: (id: number, body: OutcomeRequest) =>
    api.post<CaseResponse>(`${PREFIX}/cases/${id}/outcome`, body),
  closeCase: (id: number, body: CloseCaseRequest) =>
    api.post<CaseResponse>(`${PREFIX}/cases/${id}/close`, body),

  dashboard: () =>
    api.get<DashboardResponse>(`${PREFIX}/dashboard`),
};

// Convenience aliases.
export const listTypologies = fraudApi.listTypologies;
export const createCase     = fraudApi.createCase;
export const listCases      = fraudApi.listCases;
export const getCase        = fraudApi.getCase;
export const listTimeline   = fraudApi.listTimeline;
export const triageCase     = fraudApi.triage;
export const recordUpdate   = fraudApi.recordUpdate;
export const setOutcome     = fraudApi.setOutcome;
export const closeCase      = fraudApi.closeCase;
export const fraudDashboard = fraudApi.dashboard;
