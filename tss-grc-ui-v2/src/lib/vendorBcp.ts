// Vendor BCP attestation client — Slice 10-D.
//
// Typed wrappers around /api/v1/tprm/bcp-campaigns/* and
// /api/v1/tprm/bcp-attestations/* plus helpers (status tones, threshold
// labels, response-rate formatting).
//
// Token-authenticated portal endpoints are intentionally exposed as
// distinct functions — the vendor-portal page calls those without a
// JWT, so we want them clearly delineated from the operator surface.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

// ─── Types ────────────────────────────────────────────────────────────

export type CampaignStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'REMINDED_R1'
  | 'REMINDED_R2'
  | 'ESCALATED'
  | 'CLOSED'
  | 'CANCELLED';

export type AttestationStatus =
  | 'PENDING_INVITE'
  | 'INVITED'
  | 'REMINDED'
  | 'OVERDUE'
  | 'SUBMITTED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'NA_OUT_OF_SCOPE';

export type Threshold = 'ALL' | 'CRITICAL_ONLY' | 'HIGH_AND_ABOVE';

export interface VendorBcpCampaign {
  id: number;
  externalId: string;
  organisationId: number;
  campaignCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  cycleYear: number;
  cycleQuarter?: string;
  openedAt?: string;
  closesAt?: string;
  closedAt?: string;
  reminderCadenceDays: number;
  escalationThresholdDays: number;
  status: CampaignStatus;
  mandatoryThreshold: Threshold;
  regulatorReference?: string;
  totalInvited: number;
  totalSubmitted: number;
  totalAccepted: number;
  totalRejected: number;
  totalOverdue: number;
  totalNa: number;
  closingSummaryEn?: string;
  closingSummaryAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CampaignStats {
  campaignId: number;
  invited: number;
  submitted: number;
  accepted: number;
  rejected: number;
  overdue: number;
  na: number;
  responseRatePercent: number;
  daysUntilClose: number;
}

export interface VendorBcpAttestation {
  id: number;
  externalId: string;
  organisationId: number;
  campaignId: number;
  vendorId: number;
  vendorName?: string;
  vendorNameAr?: string;
  submissionToken: string;
  invitedAt?: string;
  firstReminderAt?: string;
  secondReminderAt?: string;
  escalatedAt?: string;
  overdueAt?: string;
  submittedAt?: string;
  submittedByContactEmail?: string;
  submittedByContactName?: string;
  rtoHoursAttested?: number;
  mtpdHoursAttested?: number;
  contractualRtoHours?: number;
  contractualMtpdHours?: number;
  rtoBreach?: boolean;
  mtpdBreach?: boolean;
  hasTestedBcp?: boolean;
  lastBcpTestDate?: string;
  lastDrTestDate?: string;
  subprocessorDependencies?: string;
  evidenceFilePath?: string;
  evidenceFileName?: string;
  status: AttestationStatus;
  reviewerCommentEn?: string;
  reviewerCommentAr?: string;
  reviewedByUserId?: number;
  reviewedByName?: string;
  reviewedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CampaignCreateRequest {
  campaignCode?: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  cycleYear: number;
  cycleQuarter?: string;
  closesAt?: string;
  reminderCadenceDays?: number;
  escalationThresholdDays?: number;
  mandatoryThreshold: Threshold;
  regulatorReference?: string;
}

export interface CampaignCloseRequest {
  closingSummaryEn?: string;
  closingSummaryAr?: string;
}

export interface AttestationSubmitRequest {
  rtoHoursAttested: number;
  mtpdHoursAttested: number;
  hasTestedBcp: boolean;
  lastBcpTestDate?: string;
  lastDrTestDate?: string;
  subprocessorDependencies?: string;
  evidenceFilePath?: string;
  evidenceFileName?: string;
  submittedByContactEmail?: string;
  submittedByContactName?: string;
}

/**
 * F2-224 — reviewedByName/reviewedByUserId are gone: the server stamps the reviewer from the
 * authenticated principal. They remain on the RESPONSE type above, which is where they belong —
 * the register still displays who reviewed each attestation, it just no longer asks the browser.
 */
export interface AttestationReviewRequest {
  reviewerCommentEn?: string;
  reviewerCommentAr?: string;
}

// ─── Catalogs / labels ────────────────────────────────────────────────

export const CAMPAIGN_STATUSES: CampaignStatus[] = [
  'DRAFT', 'OPEN', 'REMINDED_R1', 'REMINDED_R2', 'ESCALATED', 'CLOSED', 'CANCELLED',
];

export const ATTESTATION_STATUSES: AttestationStatus[] = [
  'PENDING_INVITE', 'INVITED', 'REMINDED', 'OVERDUE', 'SUBMITTED',
  'ACCEPTED', 'REJECTED', 'NA_OUT_OF_SCOPE',
];

export const CAMPAIGN_STATUS_LABELS: Record<CampaignStatus, { en: string; ar: string }> = {
  DRAFT:       { en: 'Draft',                   ar: 'مسوّدة' },
  OPEN:        { en: 'Open',                    ar: 'مفتوحة' },
  REMINDED_R1: { en: 'Reminded — round 1',      ar: 'تذكير — الجولة الأولى' },
  REMINDED_R2: { en: 'Reminded — round 2',      ar: 'تذكير — الجولة الثانية' },
  ESCALATED:   { en: 'Escalated to committee',  ar: 'مُصعَّدة إلى اللجنة' },
  CLOSED:      { en: 'Closed',                  ar: 'مُغلَقة' },
  CANCELLED:   { en: 'Cancelled',               ar: 'مُلغاة' },
};

export const ATTESTATION_STATUS_LABELS: Record<AttestationStatus, { en: string; ar: string }> = {
  PENDING_INVITE:  { en: 'Pending invite',  ar: 'بانتظار الدعوة' },
  INVITED:         { en: 'Invited',         ar: 'مُرسَل' },
  REMINDED:        { en: 'Reminded',        ar: 'بعد التذكير' },
  OVERDUE:         { en: 'Overdue',         ar: 'متأخر' },
  SUBMITTED:       { en: 'Submitted',       ar: 'مُقدَّم — قيد المراجعة' },
  ACCEPTED:        { en: 'Accepted',        ar: 'مُعتمَد' },
  REJECTED:        { en: 'Rejected',        ar: 'مرفوض' },
  NA_OUT_OF_SCOPE: { en: 'Out of scope',    ar: 'خارج النطاق' },
};

export const THRESHOLD_LABELS: Record<Threshold, { en: string; ar: string }> = {
  ALL:            { en: 'All active vendors',           ar: 'جميع الموردين النشطين' },
  CRITICAL_ONLY:  { en: 'Critical vendors only',        ar: 'الموردون الجوهريون فقط' },
  HIGH_AND_ABOVE: { en: 'High-risk and above',          ar: 'مرتفع المخاطر فأعلى' },
};

export function campaignStatusTone(s: CampaignStatus): BadgeTone {
  switch (s) {
    case 'DRAFT':       return 'neutral';
    case 'OPEN':        return 'info';
    case 'REMINDED_R1': return 'warning';
    case 'REMINDED_R2': return 'warning';
    case 'ESCALATED':   return 'danger';
    case 'CLOSED':      return 'success';
    case 'CANCELLED':   return 'neutral';
  }
}

export function attestationStatusTone(s: AttestationStatus): BadgeTone {
  switch (s) {
    case 'PENDING_INVITE': return 'neutral';
    case 'INVITED':        return 'info';
    case 'REMINDED':       return 'warning';
    case 'OVERDUE':        return 'danger';
    case 'SUBMITTED':      return 'info';
    case 'ACCEPTED':       return 'success';
    case 'REJECTED':       return 'danger';
    case 'NA_OUT_OF_SCOPE':return 'neutral';
  }
}

/** What buttons can the operator click for a campaign in this state? */
export function campaignActions(s: CampaignStatus): {
  canOpen: boolean;
  canSendR1: boolean;
  canSendR2: boolean;
  canEscalate: boolean;
  canClose: boolean;
  canCancel: boolean;
} {
  return {
    canOpen:     s === 'DRAFT',
    canSendR1:   s === 'OPEN',
    canSendR2:   s === 'REMINDED_R1',
    canEscalate: s === 'OPEN' || s === 'REMINDED_R1' || s === 'REMINDED_R2',
    canClose:    s === 'OPEN' || s === 'REMINDED_R1' || s === 'REMINDED_R2' || s === 'ESCALATED',
    canCancel:   s !== 'CLOSED' && s !== 'CANCELLED',
  };
}

// ─── API: campaigns ───────────────────────────────────────────────────

const CAMPAIGN_BASE = '/api/v1/tprm/bcp-campaigns';

export function listCampaigns(): Promise<VendorBcpCampaign[]> {
  return api.get<VendorBcpCampaign[]>(CAMPAIGN_BASE);
}

export function getCampaign(id: number): Promise<VendorBcpCampaign> {
  return api.get<VendorBcpCampaign>(`${CAMPAIGN_BASE}/${id}`);
}

export function getCampaignStats(id: number): Promise<CampaignStats> {
  return api.get<CampaignStats>(`${CAMPAIGN_BASE}/${id}/stats`);
}

export function createCampaign(body: CampaignCreateRequest): Promise<VendorBcpCampaign> {
  return api.post<VendorBcpCampaign>(CAMPAIGN_BASE, body);
}

export function openCampaign(id: number): Promise<VendorBcpCampaign> {
  return api.post<VendorBcpCampaign>(`${CAMPAIGN_BASE}/${id}/open`, {});
}

export function sendReminders(id: number, round: 1 | 2): Promise<VendorBcpCampaign> {
  return api.post<VendorBcpCampaign>(`${CAMPAIGN_BASE}/${id}/send-reminders?round=${round}`, {});
}

export function escalateCampaign(id: number): Promise<VendorBcpCampaign> {
  return api.post<VendorBcpCampaign>(`${CAMPAIGN_BASE}/${id}/escalate`, {});
}

export function closeCampaign(id: number, body: CampaignCloseRequest): Promise<VendorBcpCampaign> {
  return api.post<VendorBcpCampaign>(`${CAMPAIGN_BASE}/${id}/close`, body);
}

export function cancelCampaign(id: number, reason?: string): Promise<VendorBcpCampaign> {
  return api.post<VendorBcpCampaign>(`${CAMPAIGN_BASE}/${id}/cancel`, { reason });
}

// ─── API: attestations ────────────────────────────────────────────────

const ATT_BASE = '/api/v1/tprm/bcp-attestations';

export function listAttestations(campaignId: number, status?: AttestationStatus): Promise<VendorBcpAttestation[]> {
  const url = status ? `${ATT_BASE}?campaignId=${campaignId}&status=${status}` : `${ATT_BASE}?campaignId=${campaignId}`;
  return api.get<VendorBcpAttestation[]>(url);
}

export function getAttestation(id: number): Promise<VendorBcpAttestation> {
  return api.get<VendorBcpAttestation>(`${ATT_BASE}/${id}`);
}

export function getAttestationByToken(token: string): Promise<VendorBcpAttestation> {
  // Anonymous: vendor portal — token IS the credential; no JWT involved.
  return api.get<VendorBcpAttestation>(`${ATT_BASE}/by-token/${token}`, { anonymous: true });
}

export function submitAttestation(id: number, body: AttestationSubmitRequest): Promise<VendorBcpAttestation> {
  return api.post<VendorBcpAttestation>(`${ATT_BASE}/${id}/submit`, body);
}

export function submitAttestationByToken(token: string, body: AttestationSubmitRequest): Promise<VendorBcpAttestation> {
  // Anonymous: vendor portal submit.
  return api.post<VendorBcpAttestation>(`${ATT_BASE}/by-token/${token}/submit`, body, { anonymous: true });
}

export function acceptAttestation(id: number, body: AttestationReviewRequest): Promise<VendorBcpAttestation> {
  return api.post<VendorBcpAttestation>(`${ATT_BASE}/${id}/accept`, body);
}

export function rejectAttestation(id: number, body: AttestationReviewRequest): Promise<VendorBcpAttestation> {
  return api.post<VendorBcpAttestation>(`${ATT_BASE}/${id}/reject`, body);
}

export function markAttestationOutOfScope(id: number, body: AttestationReviewRequest): Promise<VendorBcpAttestation> {
  return api.post<VendorBcpAttestation>(`${ATT_BASE}/${id}/mark-out-of-scope`, body);
}

// ─── Evidence-pack export (client-side CSV) ───────────────────────────

export function attestationsToCsv(rows: VendorBcpAttestation[], campaign: VendorBcpCampaign): string {
  const header = [
    'campaign_code', 'vendor_id', 'vendor_name', 'status',
    'invited_at', 'submitted_at', 'reviewed_at',
    'rto_hours_attested', 'mtpd_hours_attested',
    'contractual_rto', 'contractual_mtpd',
    'rto_breach', 'mtpd_breach',
    'has_tested_bcp', 'last_bcp_test_date', 'last_dr_test_date',
    'evidence_file_name', 'reviewed_by_name',
  ];
  const lines = [header.join(',')];
  for (const r of rows) {
    const cells = [
      campaign.campaignCode,
      String(r.vendorId),
      csvEscape(r.vendorName ?? ''),
      r.status,
      r.invitedAt ?? '',
      r.submittedAt ?? '',
      r.reviewedAt ?? '',
      r.rtoHoursAttested != null ? String(r.rtoHoursAttested) : '',
      r.mtpdHoursAttested != null ? String(r.mtpdHoursAttested) : '',
      r.contractualRtoHours != null ? String(r.contractualRtoHours) : '',
      r.contractualMtpdHours != null ? String(r.contractualMtpdHours) : '',
      r.rtoBreach ? 'YES' : 'NO',
      r.mtpdBreach ? 'YES' : 'NO',
      r.hasTestedBcp == null ? '' : (r.hasTestedBcp ? 'YES' : 'NO'),
      r.lastBcpTestDate ?? '',
      r.lastDrTestDate ?? '',
      csvEscape(r.evidenceFileName ?? ''),
      csvEscape(r.reviewedByName ?? ''),
    ];
    lines.push(cells.join(','));
  }
  return lines.join('\n');
}

function csvEscape(s: string): string {
  if (s == null) return '';
  if (s.includes(',') || s.includes('"') || s.includes('\n')) {
    return '"' + s.replace(/"/g, '""') + '"';
  }
  return s;
}
