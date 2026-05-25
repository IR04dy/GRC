// Mandatory Regulatory Reporting Calendar — Sprint 16 PR #3.
//
// Backend: PR #239 (com.tss.grc.module.regreporting).
// F8 rules: PR #240 (AR-211..AR-220).
//
// Operational calendar for the 40-100+ recurring statutory returns
// every Saudi-licensed firm runs (SAMA, CMA, NCA, SDAIA, ZATCA,
// NCEMA) at cadences from daily liquidity to annual capital
// adequacy.
//
// Saudi-native bilingual:
//   ar : تقويم البلاغات
//   en : Reporting Calendar
//
// Filing lifecycle:
//   SCHEDULED → IN_PREPARATION → IN_REVIEW → APPROVED → SUBMITTED →
//   ACKNOWLEDGED → CLOSED
//     + REJECTED  (regulator pushback → back to IN_PREPARATION)
//     + EXEMPTED  (formal waiver, terminal)

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type Frequency =
  | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY'
  | 'SEMIANNUAL' | 'ANNUAL' | 'AD_HOC';

export type Regulator =
  | 'SAMA' | 'NCA' | 'CMA' | 'SDAIA' | 'NCEMA' | 'ZATCA' | 'OTHER';

export type FilingStatus =
  | 'SCHEDULED' | 'IN_PREPARATION' | 'IN_REVIEW' | 'APPROVED'
  | 'SUBMITTED' | 'ACKNOWLEDGED' | 'CLOSED'
  | 'REJECTED' | 'EXEMPTED';

export type EventType =
  | 'STATUS_CHANGED' | 'REMINDER_FIRED' | 'SUBMITTED'
  | 'ACK_RECEIVED' | 'REJECTED' | 'EXEMPTION_GRANTED' | 'INTERNAL_NOTE';

// ─── Requests ────────────────────────────────────────────────────

export interface CreateObligationRequest {
  code: string;
  regulator: Regulator;
  frequency: Frequency;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  dueOffsetBusinessDays: number;
  statutoryBasis?: string;
  ownerRole?: string;
  reviewerRole?: string;
  filingChannel?: string;
  reminderLadder?: string;
}

export interface UpdateObligationRequest {
  titleEn?: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  dueOffsetBusinessDays?: number;
  statutoryBasis?: string;
  ownerRole?: string;
  reviewerRole?: string;
  filingChannel?: string;
  reminderLadder?: string;
  active?: boolean;
}

export interface GenerateFilingRequest {
  periodEnd?: string;
  dueDate?: string;
  preparerUserId?: number;
  reviewerUserId?: number;
  approverUserId?: number;
}

export interface UpdateFilingRequest {
  internalTargetDate?: string;
  preparerUserId?: number;
  reviewerUserId?: number;
  approverUserId?: number;
  preparationNotesEn?: string;
  preparationNotesAr?: string;
  filingArtifactReference?: string;
}

export interface TransitionFilingRequest {
  toStatus: FilingStatus;
  noteEn?: string;
  noteAr?: string;
}

export interface SubmitFilingRequest {
  regulatorAckReference: string;
  filingArtifactReference?: string;
  noteEn?: string;
  noteAr?: string;
}

export interface RejectFilingRequest {
  rejectionReasonEn: string;
  rejectionReasonAr: string;
}

export interface ExemptFilingRequest {
  exemptionReasonEn: string;
  exemptionReasonAr: string;
  exemptionReference: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface ObligationResponse {
  id: number;
  code: string;
  regulator: Regulator;
  frequency: Frequency;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  dueOffsetBusinessDays: number;
  statutoryBasis?: string;
  ownerRole?: string;
  reviewerRole?: string;
  filingChannel?: string;
  reminderLadder?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
  latestFilingStatus?: FilingStatus;
  latestFilingDueDate?: string;
}

export interface FilingResponse {
  id: number;
  obligationId: number;
  obligationCode?: string;
  obligationTitleEn?: string;
  obligationTitleAr?: string;
  regulator?: Regulator;
  frequency?: Frequency;
  reference: string;
  status: FilingStatus;
  periodStart: string;
  periodEnd: string;
  dueDate: string;
  internalTargetDate?: string;
  preparerUserId?: number;
  reviewerUserId?: number;
  approverUserId?: number;
  submittedAt?: string;
  regulatorAckReference?: string;
  acknowledgedAt?: string;
  closedAt?: string;
  filingArtifactReference?: string;
  preparationNotesEn?: string;
  preparationNotesAr?: string;
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
  exemptionReasonEn?: string;
  exemptionReasonAr?: string;
  exemptionReference?: string;
  overdue: boolean;
  daysUntilDue: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface FilingEventResponse {
  id: number;
  filingId: number;
  occurredAt: string;
  eventType: EventType;
  actorUserId?: number;
  actorName?: string;
  statusAfter?: FilingStatus;
  noteEn?: string;
  noteAr?: string;
}

export interface DashboardResponse {
  activeObligationCount: number;
  inFlightFilingCount: number;
  dueWithin7Count: number;
  overdueCount: number;
  submittedThisQuarterCount: number;
  inFlightByRegulator: Record<string, number>;
  inFlightByStatus: Record<string, number>;
  upcoming: FilingResponse[];
  overdueRecent: FilingResponse[];
}

export interface CalendarResponse {
  from: string;
  to: string;
  filings: FilingResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Display labels (Saudi-native AR) ────────────────────────────

export const FILING_STATUS_LABEL: Record<FilingStatus,
  { en: string; ar: string }> = {
  SCHEDULED:       { en: 'Scheduled',      ar: 'مجدوَل' },
  IN_PREPARATION:  { en: 'In preparation', ar: 'قيد الإعداد' },
  IN_REVIEW:       { en: 'In review',      ar: 'قيد المراجعة' },
  APPROVED:        { en: 'Approved',       ar: 'مُعتمَد' },
  SUBMITTED:       { en: 'Submitted',      ar: 'مُقدَّم' },
  ACKNOWLEDGED:    { en: 'Acknowledged',   ar: 'مُستلَم' },
  CLOSED:          { en: 'Closed',         ar: 'مغلق' },
  REJECTED:        { en: 'Rejected',       ar: 'مرفوض' },
  EXEMPTED:        { en: 'Exempted',       ar: 'مُعفى' },
};

export const FILING_STATUS_TONE: Record<FilingStatus,
  'neutral' | 'info' | 'warning' | 'danger' | 'success'> = {
  SCHEDULED:       'neutral',
  IN_PREPARATION:  'info',
  IN_REVIEW:       'warning',
  APPROVED:        'info',
  SUBMITTED:       'success',
  ACKNOWLEDGED:    'success',
  CLOSED:          'success',
  REJECTED:        'danger',
  EXEMPTED:        'neutral',
};

export const REGULATOR_LABEL: Record<Regulator, { en: string; ar: string }> = {
  SAMA:  { en: 'SAMA',  ar: 'ساما' },
  NCA:   { en: 'NCA',   ar: 'هيئة الأمن السيبراني' },
  CMA:   { en: 'CMA',   ar: 'هيئة السوق المالية' },
  SDAIA: { en: 'SDAIA', ar: 'سدايا' },
  NCEMA: { en: 'NCEMA', ar: 'إدارة الطوارئ' },
  ZATCA: { en: 'ZATCA', ar: 'زاتكا' },
  OTHER: { en: 'Other', ar: 'أخرى' },
};

export const FREQUENCY_LABEL: Record<Frequency, { en: string; ar: string }> = {
  DAILY:      { en: 'Daily',         ar: 'يومي' },
  WEEKLY:     { en: 'Weekly',        ar: 'أسبوعي' },
  MONTHLY:    { en: 'Monthly',       ar: 'شهري' },
  QUARTERLY:  { en: 'Quarterly',     ar: 'ربع‑سنوي' },
  SEMIANNUAL: { en: 'Semi-annual',   ar: 'نصف‑سنوي' },
  ANNUAL:     { en: 'Annual',        ar: 'سنوي' },
  AD_HOC:     { en: 'Ad hoc',        ar: 'حسب الحاجة' },
};

export const EVENT_TYPE_LABEL: Record<EventType, { en: string; ar: string }> = {
  STATUS_CHANGED:   { en: 'Status changed',     ar: 'تغيير الحالة' },
  REMINDER_FIRED:   { en: 'Reminder fired',     ar: 'إطلاق تذكير' },
  SUBMITTED:        { en: 'Submitted',          ar: 'تقديم' },
  ACK_RECEIVED:     { en: 'Acknowledgment',     ar: 'إقرار الاستلام' },
  REJECTED:         { en: 'Rejected',           ar: 'رفض' },
  EXEMPTION_GRANTED:{ en: 'Exemption granted',  ar: 'منح إعفاء' },
  INTERNAL_NOTE:    { en: 'Internal note',      ar: 'ملاحظة داخلية' },
};

const TERMINAL = new Set<FilingStatus>(['CLOSED', 'EXEMPTED']);

/**
 * Next allowed transitions for a filing — drives the action panel.
 * Mirrors RegReportingService.ALLOWED. Note: SUBMITTED / REJECTED /
 * EXEMPTED have dedicated endpoints — the generic transition list
 * here excludes those.
 */
export function nextAllowed(status: FilingStatus): FilingStatus[] {
  switch (status) {
    case 'SCHEDULED':       return ['IN_PREPARATION'];
    case 'IN_PREPARATION':  return ['IN_REVIEW'];
    case 'IN_REVIEW':       return ['APPROVED', 'IN_PREPARATION'];
    case 'APPROVED':        return ['IN_PREPARATION'];
    case 'ACKNOWLEDGED':    return ['CLOSED'];
    case 'REJECTED':        return ['IN_PREPARATION'];
    default:                return [];
  }
}

export function isTerminal(status: FilingStatus): boolean {
  return TERMINAL.has(status);
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/regreporting';

export const regreportingApi = {
  dashboard: () =>
    api.get<DashboardResponse>(`${PREFIX}/dashboard`),

  calendar: (from?: string, to?: string) => {
    const qs = new URLSearchParams();
    if (from) qs.set('from', from);
    if (to) qs.set('to', to);
    const q = qs.toString();
    return api.get<CalendarResponse>(`${PREFIX}/calendar${q ? '?' + q : ''}`);
  },

  // Obligations
  listObligations: (page = 0, size = 100) =>
    api.get<Page<ObligationResponse>>(
      `${PREFIX}/obligations?page=${page}&size=${size}`),
  getObligation: (id: number) =>
    api.get<ObligationResponse>(`${PREFIX}/obligations/${id}`),
  createObligation: (body: CreateObligationRequest) =>
    api.post<ObligationResponse>(`${PREFIX}/obligations`, body),
  updateObligation: (id: number, body: UpdateObligationRequest) =>
    api.put<ObligationResponse>(`${PREFIX}/obligations/${id}`, body),
  listFilingsForObligation: (id: number) =>
    api.get<FilingResponse[]>(`${PREFIX}/obligations/${id}/filings`),
  generateFiling: (id: number, body: GenerateFilingRequest) =>
    api.post<FilingResponse>(`${PREFIX}/obligations/${id}/filings`, body),

  // Filings
  listFilings: (page = 0, size = 50) =>
    api.get<Page<FilingResponse>>(
      `${PREFIX}/filings?page=${page}&size=${size}`),
  getFiling: (id: number) =>
    api.get<FilingResponse>(`${PREFIX}/filings/${id}`),
  updateFiling: (id: number, body: UpdateFilingRequest) =>
    api.put<FilingResponse>(`${PREFIX}/filings/${id}`, body),
  transitionFiling: (id: number, body: TransitionFilingRequest) =>
    api.post<FilingResponse>(`${PREFIX}/filings/${id}/transition`, body),
  submitFiling: (id: number, body: SubmitFilingRequest) =>
    api.post<FilingResponse>(`${PREFIX}/filings/${id}/submit`, body),
  acknowledgeFiling: (id: number) =>
    api.post<FilingResponse>(`${PREFIX}/filings/${id}/acknowledge`, {}),
  rejectFiling: (id: number, body: RejectFilingRequest) =>
    api.post<FilingResponse>(`${PREFIX}/filings/${id}/reject`, body),
  exemptFiling: (id: number, body: ExemptFilingRequest) =>
    api.post<FilingResponse>(`${PREFIX}/filings/${id}/exempt`, body),
  listEvents: (id: number) =>
    api.get<FilingEventResponse[]>(`${PREFIX}/filings/${id}/events`),
};
