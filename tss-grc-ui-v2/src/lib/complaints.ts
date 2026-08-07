// Customer Complaints Management — Sprint 13.
//
// SAMA Banking Consumer Protection Principles — bank-grade complaint
// intake, lifecycle workflow, SAMA escalation, and quarterly aggregate
// reporting. Backend endpoints under `/api/v1/complaints/*` (PR #218).
//
// Two surfaces are exposed by the same controller:
//   • PUBLIC  — `/public` + `/public/track/{token}` are `permitAll()`
//               on the backend. Used by the customer-facing submit and
//               track pages — MUST be reachable without a JWT.
//   • INTERNAL — every other endpoint is gated server-side by
//               `hasPermission('CONSUMER', READ | WRITE | APPROVE)` since
//               F2-145(e) slice 3. Reads are READ; acknowledge, transition,
//               assign and the quarterly-report generation are WRITE; and
//               `escalate-sama` alone is APPROVE, because telling the
//               regulator is an outbound act with legal consequence (the
//               analogue of openbanking's notify-sama). It read
//               `hasAnyRole(PLATFORM_ADMIN, CONSUMER_PROTECTION_OFFICER,
//               COMPLIANCE_OFFICER)` on all nine identically until then;
//               the population is unchanged.
//
// Saudi-native terminology (NOT literal translation):
//   إدارة شكاوى العملاء         — Customer Complaints
//   تم الإقرار باستلامها         — Acknowledged
//   قيد الدراسة                  — Under investigation (operator-natural,
//                                  not "قيد التحقيق")
//   بانتظار رد العميل            — Awaiting customer response
//   مُحالة إلى ساما              — Escalated to SAMA
//   تجاوز مدة الإقرار            — Acknowledgement SLA breached
//   خمسة أيام عمل / عشرون يوم عمل — SAMA 5- and 20-working-day SLAs
//   رمز التتبّع                   — Tracking token
//   التعويض (ر.س)                — Compensation (SAR)

import { api } from './api';

// ─── Enums (mirror backend Complaint javadoc) ─────────────────────────

export type Channel =
  | 'WEB' | 'EMAIL' | 'PHONE' | 'BRANCH' | 'SAMA_REFERRAL' | 'SOCIAL_MEDIA';

export type Category =
  | 'ACCOUNT_ACCESS' | 'CARDS_DISPUTES' | 'ATM_ISSUES' | 'TRANSFER_DELAYS'
  | 'FRAUD_UNAUTHORIZED_TXN' | 'FEES_CHARGES' | 'LOAN_FINANCING' | 'INSURANCE'
  | 'INVESTMENT_PRODUCTS' | 'DIGITAL_BANKING' | 'BRANCH_SERVICE'
  | 'MISLEADING_INFORMATION' | 'OTHER';

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type ComplaintStatus =
  | 'RECEIVED' | 'ACKNOWLEDGED' | 'UNDER_INVESTIGATION'
  | 'AWAITING_CUSTOMER' | 'RESOLVED' | 'CLOSED'
  | 'ESCALATED_TO_SAMA' | 'REOPENED' | 'REJECTED';

export type CustomerIdType = 'NATIONAL_ID' | 'IQAMA' | 'PASSPORT' | 'CR';

export type UpdateType =
  | 'STATUS_CHANGE' | 'CUSTOMER_CONTACT' | 'INTERNAL_NOTE'
  | 'EVIDENCE_REQUEST' | 'SAMA_NOTIFICATION';

// ─── Requests ────────────────────────────────────────────────────────

/** Public anonymous submission. `descriptionAr` is the only NotBlank
 *  field — everything else is optional so a walk-in customer who only
 *  has an Iqama and a phone can still file. */
export interface SubmitComplaintRequest {
  channel: Channel;
  category: Category;
  subcategory?: string;
  severity?: Severity;
  customerNameEn?: string;
  customerNameAr?: string;
  customerIdType?: CustomerIdType;
  /** Exactly 4 digits when provided — Spring @Size(min=4,max=4). */
  customerIdLast4?: string;
  customerPhone?: string;
  customerEmail?: string;
  customerCity?: string;
  productCode?: string;
  /** Exactly 4 digits when provided. */
  accountLast4?: string;
  descriptionEn?: string;
  descriptionAr: string;
}

export interface AcknowledgeRequest {
  noteEn?: string;
  noteAr?: string;
}

export interface TransitionRequest {
  toStatus: ComplaintStatus;
  noteEn?: string;
  noteAr?: string;
  customerVisible?: boolean;
  // Used on RESOLVED transition:
  resolutionSummaryEn?: string;
  resolutionSummaryAr?: string;
  compensationAmountSar?: number;
  rootCauseCode?: string;
  lessonsLearned?: string;
}

export interface EscalateToSamaRequest {
  samaReferenceNo: string;
  noteEn?: string;
  noteAr?: string;
}

export interface AssignRequest {
  assignedTo: string;
}

export interface GenerateQuarterlyReportRequest {
  reportPeriod: string;
}

// ─── Responses ──────────────────────────────────────────────────────

export interface SubmitComplaintResponse {
  complaintNo: string;
  submissionToken: string;
  ackSlaDueOn: string;
  resolutionSlaDueOn: string;
  retentionNoticeEn: string;
  retentionNoticeAr: string;
}

export interface ComplaintResponse {
  id: number;
  complaintNo: string;
  submissionToken: string;
  channel: Channel;
  category: Category;
  subcategory?: string;
  severity: Severity;
  status: ComplaintStatus;
  customerNameEn?: string;
  customerNameAr?: string;
  customerIdType?: CustomerIdType;
  customerIdLast4?: string;
  customerPhone?: string;
  customerEmail?: string;
  customerCity?: string;
  productCode?: string;
  accountLast4?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  submittedOn: string;
  acknowledgedOn?: string;
  acknowledgedBy?: string;
  ackSlaDueOn?: string;
  resolvedOn?: string;
  resolutionSummaryEn?: string;
  resolutionSummaryAr?: string;
  resolvedBy?: string;
  resolutionSlaDueOn?: string;
  escalatedToSamaOn?: string;
  samaReferenceNo?: string;
  assignedTo?: string;
  rootCauseCode?: string;
  compensationAmountSar?: number;
  lessonsLearned?: string;
}

export interface ComplaintUpdateResponse {
  id: number;
  complaintId: number;
  updateType: UpdateType;
  fromStatus?: ComplaintStatus;
  toStatus?: ComplaintStatus;
  noteEn?: string;
  noteAr?: string;
  actor?: string;
  customerVisible?: boolean;
  occurredOn: string;
}

/** Anonymous tracking view — name reduced to initials, NO PII. */
export interface PublicTimelineEntry {
  updateType: UpdateType;
  toStatus?: ComplaintStatus;
  noteEn?: string;
  noteAr?: string;
  occurredOn: string;
}

export interface PublicTrackingResponse {
  complaintNo: string;
  status: ComplaintStatus;
  category: Category;
  severity: Severity;
  customerInitials?: string;
  submittedOn: string;
  acknowledgedOn?: string;
  resolvedOn?: string;
  escalatedToSamaOn?: string;
  timeline: PublicTimelineEntry[];
}

export interface QuarterlyReportResponse {
  id: number;
  reportPeriod: string;
  totalReceived?: number;
  totalResolved?: number;
  totalEscalatedSama?: number;
  avgResolutionDays?: number;
  ackSlaBreachPct?: number;
  resolutionSlaBreachPct?: number;
  topCategory?: string;
  topCategoryCount?: number;
  compensationTotalSar?: number;
  generatedOn?: string;
  generatedBy?: string;
}

// ─── REST client ────────────────────────────────────────────────────

const PREFIX = '/api/v1/complaints';

export const complaintsApi = {
  // Public — anonymous, MUST NOT send Authorization header.
  // F2-83: `org` is the tenant's opaque short code from the portal link. There is no JWT on this
  // path, so it is the only thing that says which bank the complaint belongs to; the server 404s an
  // unknown or inactive code rather than defaulting to org 1, which is what it used to do.
  publicSubmit: (body: SubmitComplaintRequest, org: string) =>
    api.post<SubmitComplaintResponse>(
      `${PREFIX}/public?org=${encodeURIComponent(org)}`, body, { anonymous: true }),

  publicTrack: (token: string) =>
    api.get<PublicTrackingResponse>(
      `${PREFIX}/public/track/${encodeURIComponent(token)}`,
      { anonymous: true }),

  // Internal — server enforces role gating.
  list: (filters?: { status?: ComplaintStatus; category?: Category; severity?: Severity }) => {
    const params = new URLSearchParams();
    if (filters?.status)   params.set('status', filters.status);
    if (filters?.category) params.set('category', filters.category);
    if (filters?.severity) params.set('severity', filters.severity);
    const qs = params.toString();
    return api.get<ComplaintResponse[]>(`${PREFIX}${qs ? `?${qs}` : ''}`);
  },

  get: (id: number) =>
    api.get<ComplaintResponse>(`${PREFIX}/${id}`),

  updates: (id: number) =>
    api.get<ComplaintUpdateResponse[]>(`${PREFIX}/${id}/updates`),

  acknowledge: (id: number, body?: AcknowledgeRequest) =>
    api.post<ComplaintResponse>(`${PREFIX}/${id}/acknowledge`, body ?? {}),

  transition: (id: number, body: TransitionRequest) =>
    api.post<ComplaintResponse>(`${PREFIX}/${id}/transition`, body),

  escalateSama: (id: number, body: EscalateToSamaRequest) =>
    api.post<ComplaintResponse>(`${PREFIX}/${id}/escalate-sama`, body),

  assign: (id: number, body: AssignRequest) =>
    api.post<ComplaintResponse>(`${PREFIX}/${id}/assign`, body),

  reports: {
    list: () =>
      api.get<QuarterlyReportResponse[]>(`${PREFIX}/reports/quarterly`),
    generate: (period: string) =>
      api.post<QuarterlyReportResponse>(
        `${PREFIX}/reports/quarterly/generate`, { reportPeriod: period }),
  },
};

// ─── Saudi-native bilingual display labels ───────────────────────────

export const CATEGORY_LABEL: Record<Category, { en: string; ar: string }> = {
  ACCOUNT_ACCESS:         { en: 'Account access',         ar: 'وصول للحساب' },
  CARDS_DISPUTES:         { en: 'Card disputes',          ar: 'نزاعات بطاقات' },
  ATM_ISSUES:             { en: 'ATM issues',             ar: 'مشكلات الصرّاف الآلي' },
  TRANSFER_DELAYS:        { en: 'Transfer delays',        ar: 'تأخر التحويلات' },
  FRAUD_UNAUTHORIZED_TXN: { en: 'Fraud / unauthorised transaction', ar: 'احتيال أو معاملة غير مصرّح بها' },
  FEES_CHARGES:           { en: 'Fees and charges',       ar: 'رسوم وأتعاب' },
  LOAN_FINANCING:         { en: 'Loan / financing',       ar: 'تمويل/قرض' },
  INSURANCE:              { en: 'Insurance',              ar: 'تأمين' },
  INVESTMENT_PRODUCTS:    { en: 'Investment products',    ar: 'منتجات استثمارية' },
  DIGITAL_BANKING:        { en: 'Digital banking',        ar: 'خدمات رقمية' },
  BRANCH_SERVICE:         { en: 'Branch service',         ar: 'خدمات الفرع' },
  MISLEADING_INFORMATION: { en: 'Misleading information', ar: 'معلومات مضللة' },
  OTHER:                  { en: 'Other',                  ar: 'أخرى' },
};

export const CHANNEL_LABEL: Record<Channel, { en: string; ar: string }> = {
  WEB:           { en: 'Web',           ar: 'موقع إلكتروني' },
  EMAIL:         { en: 'Email',         ar: 'بريد إلكتروني' },
  PHONE:         { en: 'Phone',         ar: 'هاتف' },
  BRANCH:        { en: 'Branch',        ar: 'فرع' },
  SAMA_REFERRAL: { en: 'SAMA referral', ar: 'إحالة من ساما' },
  SOCIAL_MEDIA:  { en: 'Social media',  ar: 'وسائل تواصل اجتماعي' },
};

export const SEVERITY_LABEL: Record<Severity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرجة' },
  HIGH:     { en: 'High',     ar: 'عالية' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسطة' },
  LOW:      { en: 'Low',      ar: 'منخفضة' },
};

export const SEVERITY_TONE: Record<Severity, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low'> = {
  CRITICAL: 'risk-crit',
  HIGH:     'risk-high',
  MEDIUM:   'risk-med',
  LOW:      'risk-low',
};

export const STATUS_LABEL: Record<ComplaintStatus, { en: string; ar: string }> = {
  RECEIVED:            { en: 'Received',                  ar: 'تم الاستلام' },
  ACKNOWLEDGED:        { en: 'Acknowledged',              ar: 'تم الإقرار باستلامها' },
  UNDER_INVESTIGATION: { en: 'Under investigation',       ar: 'قيد الدراسة' },
  AWAITING_CUSTOMER:   { en: 'Awaiting customer',         ar: 'بانتظار رد العميل' },
  RESOLVED:            { en: 'Resolved',                  ar: 'تم الحل' },
  CLOSED:              { en: 'Closed',                    ar: 'مُغلقة' },
  ESCALATED_TO_SAMA:   { en: 'Escalated to SAMA',         ar: 'مُحالة إلى ساما' },
  REOPENED:            { en: 'Reopened',                  ar: 'أُعيد فتحها' },
  REJECTED:            { en: 'Rejected',                  ar: 'مرفوضة' },
};

export const STATUS_TONE: Record<ComplaintStatus, 'info' | 'warning' | 'success' | 'danger' | 'neutral'> = {
  RECEIVED:            'info',
  ACKNOWLEDGED:        'info',
  UNDER_INVESTIGATION: 'warning',
  AWAITING_CUSTOMER:   'warning',
  RESOLVED:            'success',
  CLOSED:              'neutral',
  ESCALATED_TO_SAMA:   'danger',
  REOPENED:            'warning',
  REJECTED:            'neutral',
};

export const CUSTOMER_ID_TYPE_LABEL: Record<CustomerIdType, { en: string; ar: string }> = {
  NATIONAL_ID: { en: 'National ID', ar: 'هوية وطنية' },
  IQAMA:       { en: 'Iqama',       ar: 'إقامة' },
  PASSPORT:    { en: 'Passport',    ar: 'جواز سفر' },
  CR:          { en: 'CR',          ar: 'سجل تجاري' },
};

export const UPDATE_TYPE_LABEL: Record<UpdateType, { en: string; ar: string }> = {
  STATUS_CHANGE:     { en: 'Status change',     ar: 'تغيير حالة' },
  CUSTOMER_CONTACT:  { en: 'Customer contact',  ar: 'تواصل مع العميل' },
  INTERNAL_NOTE:     { en: 'Internal note',     ar: 'ملاحظة داخلية' },
  EVIDENCE_REQUEST:  { en: 'Evidence request',  ar: 'طلب مستندات' },
  SAMA_NOTIFICATION: { en: 'SAMA notification', ar: 'إشعار لساما' },
};

/**
 * Status state-machine — mirrors `ComplaintService.allowedTransitions(...)`
 * in PR #218. Empty array means terminal. Drives the "Transition" dropdown
 * on the detail page so the user only sees legal next states.
 */
export const NEXT_STATUSES: Record<ComplaintStatus, ComplaintStatus[]> = {
  RECEIVED:            ['ACKNOWLEDGED', 'REJECTED'],
  ACKNOWLEDGED:        ['UNDER_INVESTIGATION', 'AWAITING_CUSTOMER', 'RESOLVED', 'REJECTED'],
  UNDER_INVESTIGATION: ['AWAITING_CUSTOMER', 'RESOLVED', 'ESCALATED_TO_SAMA', 'REJECTED'],
  AWAITING_CUSTOMER:   ['UNDER_INVESTIGATION', 'RESOLVED', 'CLOSED', 'REJECTED'],
  RESOLVED:            ['CLOSED', 'REOPENED'],
  CLOSED:              ['REOPENED'],
  ESCALATED_TO_SAMA:   ['RESOLVED', 'CLOSED'],
  REOPENED:            ['UNDER_INVESTIGATION', 'AWAITING_CUSTOMER', 'RESOLVED'],
  REJECTED:            ['REOPENED'],
};

/** SLA breach check used by the register row tint. Returns the breach kind
 *  if past-due against the relevant SAMA SLA, otherwise null. */
export function slaBreachKind(c: ComplaintResponse, now: Date = new Date()):
  'ACK' | 'RESOLUTION' | null {
  const isAckPending = c.status === 'RECEIVED';
  const isResolutionPending =
    c.status !== 'RESOLVED' && c.status !== 'CLOSED' && c.status !== 'REJECTED';
  if (isAckPending && c.ackSlaDueOn && new Date(c.ackSlaDueOn) < now) return 'ACK';
  if (isResolutionPending && c.resolutionSlaDueOn && new Date(c.resolutionSlaDueOn) < now) return 'RESOLUTION';
  return null;
}

/** Convenience: bilingual labels grouped as one object the way some
 *  call-sites prefer. Kept separate from the per-enum exports so the
 *  per-enum versions remain tree-shake-friendly. */
export const complaintsLabels = {
  category: CATEGORY_LABEL,
  channel:  CHANNEL_LABEL,
  severity: SEVERITY_LABEL,
  status:   STATUS_LABEL,
  idType:   CUSTOMER_ID_TYPE_LABEL,
  updateType: UPDATE_TYPE_LABEL,
} as const;
