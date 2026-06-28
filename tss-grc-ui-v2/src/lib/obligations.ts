// Sprint 11 — Obligation Register + Periodic Attestation client.
//
// Typed wrappers around the /api/v1/compliance/obligations/*,
// /obligation-attestations/* and /obligation-campaigns/* endpoints,
// plus Saudi-native EN/AR label catalogs and small workflow helpers.
//
// Mirrors the shape of vendorBcp.ts (Slice 10-D) intentionally —
// same labelling, badge-tone and action-helper conventions so the
// UI stays predictable across modules.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

// ─── Enum types ───────────────────────────────────────────────────────

export type Regulator =
  | 'SAMA' | 'NCA' | 'SDAIA' | 'CMA' | 'ZATCA' | 'MOH' | 'GAZT'
  | 'MOJ' | 'NCEMA' | 'CITC' | 'MOC' | 'MOI' | 'INTERNAL';

export type ObligationType =
  | 'REPORTING' | 'ATTESTATION' | 'DOCUMENTATION' | 'NOTIFICATION'
  | 'AUDIT' | 'TRAINING' | 'REVIEW' | 'FILING' | 'REGISTRATION';

export type Frequency =
  | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUAL'
  | 'ANNUAL' | 'BIENNIAL' | 'ON_EVENT' | 'AD_HOC';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type LifecycleStage = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'RETIRED';

export type AttestationStatus =
  | 'DUE' | 'OVERDUE' | 'IN_PROGRESS'
  | 'ATTESTED' | 'ATTESTED_WITH_EXCEPTIONS' | 'NOT_APPLICABLE';

export type ReviewerDecision = 'ACCEPTED' | 'REJECTED' | 'REQUEST_CHANGES';

export type CampaignStatus =
  | 'DRAFT' | 'OPEN' | 'IN_PROGRESS' | 'ESCALATED' | 'CLOSED' | 'CANCELLED';

// ─── Records ──────────────────────────────────────────────────────────

export interface Obligation {
  id: number;
  externalId: string;
  organisationId: number;
  obligationCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  regulator: Regulator;
  regulationReference?: string;
  obligationType: ObligationType;
  frequency: Frequency;
  severityIfMissed: Severity;
  firstDueDate?: string;
  nextDueDate: string;
  leadTimeDays: number;
  ownerUserId: number;
  backupOwnerUserId?: number;
  businessUnitEn?: string;
  businessUnitAr?: string;
  relatedControlId?: number;
  relatedRiskId?: number;
  lifecycleStage: LifecycleStage;
  lastAttestedAt?: string;
  lastAttestedByUserId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ObligationKpis {
  activeCount: number;
  overdueCount: number;
  dueThisMonthCount: number;
  attestedThisCycleCount: number;
}

export interface ObligationAttestation {
  id: number;
  externalId: string;
  organisationId: number;
  obligationId: number;
  campaignId?: number;
  periodLabel?: string;
  periodStart?: string;
  periodEnd?: string;
  dueDate: string;
  attestationStatus: AttestationStatus;
  attestedByUserId?: number;
  attestedAt?: string;
  attestationTextEn?: string;
  attestationTextAr?: string;
  evidenceRefs?: string;
  exceptionsNotedEn?: string;
  exceptionsNotedAr?: string;
  reviewerUserId?: number;
  reviewedAt?: string;
  reviewerDecision?: ReviewerDecision;
  reviewerCommentEn?: string;
  reviewerCommentAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ObligationCampaign {
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
  regulatorFilter?: Regulator;
  frequencyFilter?: Frequency;
  openedAt?: string;
  closesAt: string;
  closedAt?: string;
  reminderCadenceDays: number;
  status: CampaignStatus;
  totalInvited: number;
  totalInProgress: number;
  totalAttested: number;
  totalAttestedWithExcs: number;
  totalOverdue: number;
  totalNa: number;
  closingSummaryEn?: string;
  closingSummaryAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ObligationCampaignStats {
  campaignId: number;
  invited: number;
  inProgress: number;
  attested: number;
  attestedWithExceptions: number;
  overdue: number;
  notApplicable: number;
  completionPercent: number;
  daysUntilClose: number;
}

// ─── Request bodies ───────────────────────────────────────────────────

export interface ObligationCreateRequest {
  obligationCode?: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  regulator: Regulator;
  regulationReference?: string;
  obligationType: ObligationType;
  frequency: Frequency;
  severityIfMissed?: Severity;
  firstDueDate?: string;
  nextDueDate: string;
  leadTimeDays?: number;
  ownerUserId: number;
  backupOwnerUserId?: number;
  businessUnitEn?: string;
  businessUnitAr?: string;
  relatedControlId?: number;
  relatedRiskId?: number;
}

export interface ObligationUpdateRequest extends Partial<ObligationCreateRequest> {}

export interface ObligationTransitionRequest {
  toStage: LifecycleStage;
  reason?: string;
}

export interface AttestationRecordRequest {
  obligationId: number;
  campaignId?: number;
  periodLabel?: string;
  periodStart?: string;
  periodEnd?: string;
  dueDate: string;
  attestationStatus: 'ATTESTED' | 'ATTESTED_WITH_EXCEPTIONS' | 'NOT_APPLICABLE';
  attestedByUserId: number;
  attestationTextEn?: string;
  attestationTextAr?: string;
  evidenceRefs?: string;
  exceptionsNotedEn?: string;
  exceptionsNotedAr?: string;
}

export interface AttestationReviewRequest {
  reviewerDecision: ReviewerDecision;
  reviewerUserId: number;
  reviewerCommentEn?: string;
  reviewerCommentAr?: string;
}

export interface CampaignCreateRequest {
  campaignCode?: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  cycleYear: number;
  cycleQuarter?: string;
  regulatorFilter?: Regulator | '';
  frequencyFilter?: Frequency | '';
  closesAt: string;
  reminderCadenceDays?: number;
}

export interface CampaignCloseRequest {
  closingSummaryEn?: string;
  closingSummaryAr?: string;
}

// ─── Bilingual catalogs (Saudi-native AR) ────────────────────────────

export const REGULATORS: Regulator[] = [
  'SAMA', 'NCA', 'SDAIA', 'CMA', 'ZATCA', 'MOH', 'GAZT',
  'MOJ', 'NCEMA', 'CITC', 'MOC', 'MOI', 'INTERNAL',
];

export const REGULATOR_LABELS: Record<Regulator, { en: string; ar: string }> = {
  SAMA:     { en: 'SAMA',                            ar: 'مؤسسة النقد العربي السعودي' },
  NCA:      { en: 'NCA',                             ar: 'الهيئة الوطنية للأمن السيبراني' },
  SDAIA:    { en: 'SDAIA',                           ar: 'هيئة البيانات والذكاء الاصطناعي' },
  CMA:      { en: 'CMA',                             ar: 'هيئة السوق المالية' },
  ZATCA:    { en: 'ZATCA',                           ar: 'هيئة الزكاة والضريبة والجمارك' },
  MOH:      { en: 'MoH',                             ar: 'وزارة الصحة' },
  GAZT:     { en: 'GAZT',                            ar: 'الهيئة العامة للزكاة والدخل' },
  MOJ:      { en: 'MoJ',                             ar: 'وزارة العدل' },
  NCEMA:    { en: 'NCEMA',                           ar: 'الهيئة الوطنية لإدارة الكوارث والأزمات' },
  CITC:     { en: 'CITC',                            ar: 'هيئة الاتصالات وتقنية المعلومات' },
  MOC:      { en: 'MoC',                             ar: 'وزارة التجارة' },
  MOI:      { en: 'MoI',                             ar: 'وزارة الداخلية' },
  INTERNAL: { en: 'Internal',                        ar: 'داخلي' },
};

export const TYPES: ObligationType[] = [
  'REPORTING', 'ATTESTATION', 'DOCUMENTATION', 'NOTIFICATION',
  'AUDIT', 'TRAINING', 'REVIEW', 'FILING', 'REGISTRATION',
];

export const TYPE_LABELS: Record<ObligationType, { en: string; ar: string }> = {
  REPORTING:     { en: 'Reporting',          ar: 'تقرير' },
  ATTESTATION:   { en: 'Attestation',        ar: 'إقرار' },
  DOCUMENTATION: { en: 'Documentation',      ar: 'توثيق' },
  NOTIFICATION:  { en: 'Notification',       ar: 'إشعار' },
  AUDIT:         { en: 'Audit',              ar: 'تدقيق' },
  TRAINING:      { en: 'Training',           ar: 'تدريب' },
  REVIEW:        { en: 'Review',             ar: 'مراجعة' },
  FILING:        { en: 'Filing',             ar: 'إيداع' },
  REGISTRATION:  { en: 'Registration',       ar: 'تسجيل' },
};

export const FREQUENCIES: Frequency[] = [
  'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMI_ANNUAL',
  'ANNUAL', 'BIENNIAL', 'ON_EVENT', 'AD_HOC',
];

// Saudi-native frequency labels per the spec
// ("يومي / أسبوعي / شهري / ربع سنوي / نصف سنوي / سنوي / ثنائي السنوية / حسب الحدث / حسب الطلب")
export const FREQUENCY_LABELS: Record<Frequency, { en: string; ar: string }> = {
  DAILY:       { en: 'Daily',         ar: 'يومي' },
  WEEKLY:      { en: 'Weekly',        ar: 'أسبوعي' },
  MONTHLY:     { en: 'Monthly',       ar: 'شهري' },
  QUARTERLY:   { en: 'Quarterly',     ar: 'ربع سنوي' },
  SEMI_ANNUAL: { en: 'Semi-annual',   ar: 'نصف سنوي' },
  ANNUAL:      { en: 'Annual',        ar: 'سنوي' },
  BIENNIAL:    { en: 'Biennial',      ar: 'ثنائي السنوية' },
  ON_EVENT:    { en: 'On event',      ar: 'حسب الحدث' },
  AD_HOC:      { en: 'Ad-hoc',        ar: 'حسب الطلب' },
};

export const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export const SEVERITY_LABELS: Record<Severity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const LIFECYCLE_STAGES: LifecycleStage[] = ['DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED'];

export const LIFECYCLE_LABELS: Record<LifecycleStage, { en: string; ar: string }> = {
  DRAFT:     { en: 'Draft',     ar: 'مسوّدة' },
  ACTIVE:    { en: 'Active',    ar: 'نشط' },
  SUSPENDED: { en: 'Suspended', ar: 'مُعَلَّق' },
  RETIRED:   { en: 'Retired',   ar: 'مُتقاعد' },
};

// Saudi-native attestation status labels per spec
// ("مستحق / متأخر / قيد الإنجاز / مُقَر / مُقَر مع استثناءات / غير منطبق")
export const ATTESTATION_STATUSES: AttestationStatus[] = [
  'DUE', 'OVERDUE', 'IN_PROGRESS',
  'ATTESTED', 'ATTESTED_WITH_EXCEPTIONS', 'NOT_APPLICABLE',
];

export const ATTESTATION_STATUS_LABELS: Record<AttestationStatus, { en: string; ar: string }> = {
  DUE:                       { en: 'Due',                       ar: 'مستحق' },
  OVERDUE:                   { en: 'Overdue',                   ar: 'متأخر' },
  IN_PROGRESS:               { en: 'In progress',               ar: 'قيد الإنجاز' },
  ATTESTED:                  { en: 'Attested',                  ar: 'مُقَر' },
  ATTESTED_WITH_EXCEPTIONS:  { en: 'Attested with exceptions',  ar: 'مُقَر مع استثناءات' },
  NOT_APPLICABLE:            { en: 'Not applicable',            ar: 'غير منطبق' },
};

export const CAMPAIGN_STATUSES: CampaignStatus[] = [
  'DRAFT', 'OPEN', 'IN_PROGRESS', 'ESCALATED', 'CLOSED', 'CANCELLED',
];

export const CAMPAIGN_STATUS_LABELS: Record<CampaignStatus, { en: string; ar: string }> = {
  DRAFT:       { en: 'Draft',                  ar: 'مسوّدة' },
  OPEN:        { en: 'Open',                   ar: 'مفتوحة' },
  IN_PROGRESS: { en: 'In progress',            ar: 'قيد الإنجاز' },
  ESCALATED:   { en: 'Escalated to committee', ar: 'مُصعَّدة إلى اللجنة' },
  CLOSED:      { en: 'Closed',                 ar: 'مُغلَقة' },
  CANCELLED:   { en: 'Cancelled',              ar: 'مُلغاة' },
};

export const REVIEWER_DECISIONS: ReviewerDecision[] = ['ACCEPTED', 'REJECTED', 'REQUEST_CHANGES'];

export const REVIEWER_DECISION_LABELS: Record<ReviewerDecision, { en: string; ar: string }> = {
  ACCEPTED:        { en: 'Accepted',         ar: 'مقبول' },
  REJECTED:        { en: 'Rejected',         ar: 'مرفوض' },
  REQUEST_CHANGES: { en: 'Request changes',  ar: 'طلب تعديلات' },
};

// ─── Tones & action helpers ──────────────────────────────────────────

export function lifecycleTone(s: LifecycleStage): BadgeTone {
  switch (s) {
    case 'DRAFT':     return 'neutral';
    case 'ACTIVE':    return 'success';
    case 'SUSPENDED': return 'warning';
    case 'RETIRED':   return 'neutral';
  }
}

export function severityTone(s: Severity): BadgeTone {
  switch (s) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
  }
}

export function attestationStatusTone(s: AttestationStatus): BadgeTone {
  switch (s) {
    case 'DUE':                      return 'info';
    case 'OVERDUE':                  return 'danger';
    case 'IN_PROGRESS':              return 'warning';
    case 'ATTESTED':                 return 'success';
    case 'ATTESTED_WITH_EXCEPTIONS': return 'warning';
    case 'NOT_APPLICABLE':           return 'neutral';
  }
}

export function campaignStatusTone(s: CampaignStatus): BadgeTone {
  switch (s) {
    case 'DRAFT':       return 'neutral';
    case 'OPEN':        return 'info';
    case 'IN_PROGRESS': return 'warning';
    case 'ESCALATED':   return 'danger';
    case 'CLOSED':      return 'success';
    case 'CANCELLED':   return 'neutral';
  }
}

export function campaignActions(s: CampaignStatus): {
  canOpen: boolean;
  canRemind: boolean;
  canEscalate: boolean;
  canClose: boolean;
  canCancel: boolean;
} {
  return {
    canOpen:     s === 'DRAFT',
    canRemind:   s === 'OPEN' || s === 'IN_PROGRESS',
    canEscalate: s === 'OPEN' || s === 'IN_PROGRESS',
    canClose:    s === 'OPEN' || s === 'IN_PROGRESS' || s === 'ESCALATED',
    canCancel:   s !== 'CLOSED' && s !== 'CANCELLED',
  };
}

/**
 * Days remaining until next_due_date — positive when in the future,
 * negative when overdue. Used by the register table to colour-code
 * the "due in" cell.
 */
export function daysUntilDue(dueDate: string): number {
  const d = new Date(dueDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  d.setHours(0, 0, 0, 0);
  return Math.round((d.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
}

// ─── API: obligations ─────────────────────────────────────────────────

const OBL_BASE = '/api/v1/compliance/obligations';

export function listObligations(): Promise<Obligation[]> {
  return api.get<Obligation[]>(OBL_BASE);
}

export function getObligationKpis(): Promise<ObligationKpis> {
  return api.get<ObligationKpis>(`${OBL_BASE}/kpis`);
}

export function listObligationsDue(days = 30): Promise<Obligation[]> {
  return api.get<Obligation[]>(`${OBL_BASE}/due?days=${days}`);
}

export function listObligationsByRegulator(regulator: Regulator): Promise<Obligation[]> {
  return api.get<Obligation[]>(`${OBL_BASE}/by-regulator/${regulator}`);
}

export function listObligationsByOwner(ownerUserId: number): Promise<Obligation[]> {
  return api.get<Obligation[]>(`${OBL_BASE}/by-owner/${ownerUserId}`);
}

export function getObligation(id: number): Promise<Obligation> {
  return api.get<Obligation>(`${OBL_BASE}/${id}`);
}

export function createObligation(body: ObligationCreateRequest): Promise<Obligation> {
  return api.post<Obligation>(OBL_BASE, body);
}

export function updateObligation(id: number, body: ObligationUpdateRequest): Promise<Obligation> {
  return api.patch<Obligation>(`${OBL_BASE}/${id}`, body);
}

export function transitionObligation(id: number, body: ObligationTransitionRequest): Promise<Obligation> {
  return api.post<Obligation>(`${OBL_BASE}/${id}/transition`, body);
}

export function deleteObligation(id: number): Promise<void> {
  return api.delete<void>(`${OBL_BASE}/${id}`);
}

// ─── Evidence package (C-11) ──────────────────────────────────────────
// One-call audit manifest: the obligation, its own attachments, and every
// attestation cycle with that cycle's uploaded evidence (sha256 + links).

export interface ObligationAttachment {
  id: number;
  entityType: string;
  entityId: number;
  docType: string;
  titleEn?: string;
  fileName: string;
  fileSize?: number;
  mimeType?: string;
  sha256?: string;
  validTo?: string;
  version?: number;
  downloadUrl?: string;
}

export interface AttestationEvidence {
  attestationId: number;
  periodLabel?: string;
  status?: string;
  attachments: ObligationAttachment[];
}

export interface ObligationEvidencePackage {
  obligation: Obligation;
  obligationAttachments: ObligationAttachment[];
  attestations: AttestationEvidence[];
  generatedAt: string;
}

export function getObligationEvidencePackage(id: number): Promise<ObligationEvidencePackage> {
  return api.get<ObligationEvidencePackage>(`${OBL_BASE}/${id}/evidence-package`);
}

// ─── API: attestations ────────────────────────────────────────────────

const ATT_BASE = '/api/v1/compliance/obligation-attestations';

export function listAttestations(): Promise<ObligationAttestation[]> {
  return api.get<ObligationAttestation[]>(ATT_BASE);
}

export function listAttestationsForObligation(obligationId: number): Promise<ObligationAttestation[]> {
  return api.get<ObligationAttestation[]>(`${ATT_BASE}/by-obligation/${obligationId}`);
}

export function listAttestationsForCampaign(campaignId: number): Promise<ObligationAttestation[]> {
  return api.get<ObligationAttestation[]>(`${ATT_BASE}/by-campaign/${campaignId}`);
}

export function getAttestation(id: number): Promise<ObligationAttestation> {
  return api.get<ObligationAttestation>(`${ATT_BASE}/${id}`);
}

export function recordAttestation(body: AttestationRecordRequest): Promise<ObligationAttestation> {
  return api.post<ObligationAttestation>(`${ATT_BASE}/record`, body);
}

export function reviewAttestation(id: number, body: AttestationReviewRequest): Promise<ObligationAttestation> {
  return api.post<ObligationAttestation>(`${ATT_BASE}/${id}/review`, body);
}

// ─── API: campaigns ───────────────────────────────────────────────────

const CAM_BASE = '/api/v1/compliance/obligation-campaigns';

export function listCampaigns(): Promise<ObligationCampaign[]> {
  return api.get<ObligationCampaign[]>(CAM_BASE);
}

export function getCampaign(id: number): Promise<ObligationCampaign> {
  return api.get<ObligationCampaign>(`${CAM_BASE}/${id}`);
}

export function getCampaignStats(id: number): Promise<ObligationCampaignStats> {
  return api.get<ObligationCampaignStats>(`${CAM_BASE}/${id}/stats`);
}

export function createCampaign(body: CampaignCreateRequest): Promise<ObligationCampaign> {
  return api.post<ObligationCampaign>(CAM_BASE, body);
}

export function openCampaign(id: number): Promise<ObligationCampaign> {
  return api.post<ObligationCampaign>(`${CAM_BASE}/${id}/open`, {});
}

export function sendCampaignReminders(id: number): Promise<ObligationCampaign> {
  return api.post<ObligationCampaign>(`${CAM_BASE}/${id}/send-reminders`, {});
}

export function escalateCampaign(id: number): Promise<ObligationCampaign> {
  return api.post<ObligationCampaign>(`${CAM_BASE}/${id}/escalate`, {});
}

export function closeCampaign(id: number, body: CampaignCloseRequest): Promise<ObligationCampaign> {
  return api.post<ObligationCampaign>(`${CAM_BASE}/${id}/close`, body);
}

export function cancelCampaign(id: number, reason?: string): Promise<ObligationCampaign> {
  return api.post<ObligationCampaign>(`${CAM_BASE}/${id}/cancel`, { reason });
}
