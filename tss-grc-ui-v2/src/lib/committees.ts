// Committees + meetings API client — uses shared api.ts.

import { api } from './api';

export type CommitteeStatus = 'ACTIVE' | 'INACTIVE' | 'DISSOLVED';
export type MeetingStatus   = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'MINUTES_APPROVED' | 'CANCELLED';
export type CommitteeType   = 'BOARD' | 'EXEC_COMMITTEE' | 'RISK_COMMITTEE' | 'AUDIT_COMMITTEE'
                            | 'BCM_COMMITTEE' | 'COMPLIANCE_COMMITTEE' | 'TECH_COMMITTEE' | 'OTHER';
export type MeetingCadence  = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'AD_HOC';

export interface Committee {
  id: number; code: string; externalId?: string;
  nameEn: string; nameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  committeeType: CommitteeType;
  chairpersonName?: string; chairpersonRole?: string;
  secretaryName?: string;
  membersJson?: string;
  charterEn?: string; charterAr?: string;
  meetingCadence: MeetingCadence;
  quorumPct?: number;
  status: CommitteeStatus;
  dissolvedAt?: string; dissolvedReason?: string;
  createdAt: string; updatedAt: string;
}

export interface CommitteeMember { name: string; role: string; votingMember?: boolean; }

export interface CommitteeMeeting {
  id: number; meetingCode: string; externalId?: string;
  committeeId: number; committeeCode?: string; committeeNameEn?: string;
  titleEn?: string; titleAr?: string;
  scheduledAt: string; startedAt?: string; endedAt?: string;
  locationOrLink?: string;
  agendaJson?: string; attendeesJson?: string; quorumMet?: number;
  minutesEn?: string; minutesAr?: string;
  decisionsJson?: string; actionItemsJson?: string;
  chairName?: string; secretaryName?: string;
  status: MeetingStatus; cancelReason?: string;
  minutesApprovedAt?: string; approverName?: string;
  createdAt: string; updatedAt: string;
}

export interface AgendaItem { seq: number; topicEn: string; topicAr?: string; presenter?: string; minutesAllocated?: number; }
export interface AttendeeEntry { name: string; role?: string; present?: boolean | null; votingMember?: boolean; }
export interface Decision { seq: number; decisionEn: string; decisionAr?: string; voteFor?: number; voteAgainst?: number; voteAbstain?: number; outcome?: string; }
export interface ActionItem { seq: number; actionEn: string; owner?: string; dueDate?: string; status?: string; }

const BASE = '/api/admin/committees';

export function listCommittees(status?: CommitteeStatus, page = 0, size = 200):
    Promise<{ content: Committee[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}

export function createCommittee(body: {
  code: string; nameEn: string; nameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  committeeType: CommitteeType;
  chairpersonName?: string; secretaryName?: string;
  charterEn?: string; charterAr?: string;
  meetingCadence?: MeetingCadence; quorumPct?: number;
}): Promise<Committee> {
  return api.post(BASE, body);
}

export function dissolveCommittee(id: number, reason: string): Promise<Committee> {
  return api.post(`${BASE}/${id}/dissolve`, { reason });
}

export function listMeetings(status?: MeetingStatus, page = 0, size = 200):
    Promise<{ content: CommitteeMeeting[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}/meetings?page=${page}&size=${size}${qs}`);
}

export function createMeeting(body: {
  committeeId: number; titleEn?: string; titleAr?: string;
  scheduledAt: string; locationOrLink?: string; agendaJson?: string;
}): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings`, body);
}

export function startMeeting(id: number): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/start`);
}

export function completeMeeting(id: number, body?: { minutesEn?: string; minutesAr?: string }): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/complete`, body ?? {});
}

export function approveMinutes(id: number): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/approve-minutes`);
}

export function cancelMeeting(id: number, reason: string): Promise<CommitteeMeeting> {
  return api.post(`${BASE}/meetings/${id}/cancel`, { reason });
}

// ─── Catalog (V154-V156) ───────────────────────────────────────────────
// The 19-template Saudi-regulated catalog + standing-item library + forms
// library. Global (not tenant-scoped) — every tenant sees the same data.
// Endpoint base: /api/v1/committees/catalog

export type RegulatoryDomain = 'BANKING' | 'INSURANCE' | 'CMA' | 'PUBLIC' | 'HEALTHCARE' | 'GENERAL';
export type ExtendedCommitteeType =
  | 'BOARD' | 'EXEC_COMMITTEE' | 'RISK_COMMITTEE' | 'AUDIT_COMMITTEE'
  | 'BCM_COMMITTEE' | 'COMPLIANCE_COMMITTEE' | 'TECH_COMMITTEE'
  | 'NOMINATION_REMUNERATION' | 'CYBER_SECURITY' | 'DATA_GOVERNANCE'
  | 'PRIVACY_DPO' | 'CRISIS_MANAGEMENT' | 'AML' | 'IT_STEERING'
  | 'ALCO' | 'INVESTMENT' | 'AI_ETHICS' | 'ANTI_FRAUD'
  | 'INTERNAL_CONTROL' | 'ESG_SUSTAINABILITY' | 'ARCH_REVIEW' | 'OTHER';

export type FormType =
  | 'CHARTER' | 'AGENDA' | 'PRE_READ_COVERSHEET' | 'ATTENDANCE'
  | 'MOTION' | 'MINUTES' | 'ACTION_REGISTER' | 'COI_DECLARATION'
  | 'SKILLS_MATRIX' | 'ANNUAL_EVALUATION';

export interface CommitteeTemplate {
  id: number;
  templateCode: string;
  committeeType: ExtendedCommitteeType;
  regulatoryDomain: RegulatoryDomain;
  regulatorAnchors: string | null;       // comma-separated
  nameEn: string;
  nameAr: string;
  mandateEn: string;
  mandateAr: string;
  authorityEn: string;
  authorityAr: string;
  compositionEn: string;
  compositionAr: string;
  quorumPct: number;
  cadence: 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUAL' | 'ANNUAL' | 'AD_HOC';
  termYears: number | null;
  reportsToTemplateCode: string | null;
  escalationEn: string | null;
  escalationAr: string | null;
  regulatorCitationEn: string | null;
  regulatorCitationAr: string | null;
  independenceRequired: number;
  financialExpertRequired: number;
  regulatoryReportingEn: string | null;
  isActive: number;
}

export interface StandingItem {
  id: number;
  templateCode: string;
  seq: number;
  topicEn: string;
  topicAr: string;
  presenterRole: string | null;
  minutesAllocated: number;
  isMandatory: number;
  sourceModule: string | null;
  descriptionEn: string | null;
  descriptionAr: string | null;
}

export interface FormTemplate {
  id: number;
  formCode: string;
  formType: FormType;
  applicableTo: string;                  // comma-separated committee_type or 'ALL'
  regulatorAnchors: string | null;
  titleEn: string;
  titleAr: string;
  purposeEn: string;
  purposeAr: string;
  contentMdEn: string;
  contentMdAr: string;
  completionGuideEn: string | null;
  completionGuideAr: string | null;
  version: string;
  isActive: number;
}

const CATALOG_BASE = '/api/v1/committees/catalog';

export function listTemplates(domain?: RegulatoryDomain): Promise<CommitteeTemplate[]> {
  const qs = domain ? `?domain=${domain}` : '';
  return api.get(`${CATALOG_BASE}/templates${qs}`);
}

export function getTemplate(templateCode: string): Promise<CommitteeTemplate> {
  return api.get(`${CATALOG_BASE}/templates/${encodeURIComponent(templateCode)}`);
}

export function getStandingItems(templateCode: string): Promise<StandingItem[]> {
  return api.get(`${CATALOG_BASE}/templates/${encodeURIComponent(templateCode)}/standing-items`);
}

export function listForms(committeeType?: ExtendedCommitteeType): Promise<FormTemplate[]> {
  const qs = committeeType ? `?committeeType=${committeeType}` : '';
  return api.get(`${CATALOG_BASE}/forms${qs}`);
}

export function getForm(formCode: string): Promise<FormTemplate> {
  return api.get(`${CATALOG_BASE}/forms/${encodeURIComponent(formCode)}`);
}

/** Tone palette for the badge column in the catalog page — anchors render
 *  primary, type renders info, domain renders warning. Keeps the look
 *  consistent across template list, charter detail, and forms library. */
export const DOMAIN_TONE: Record<RegulatoryDomain, 'info' | 'warning' | 'success' | 'neutral'> = {
  BANKING: 'warning', INSURANCE: 'warning', CMA: 'info',
  PUBLIC: 'success', HEALTHCARE: 'success', GENERAL: 'neutral',
};

export const TYPE_LABEL_EN: Record<ExtendedCommitteeType, string> = {
  BOARD: 'Board',
  EXEC_COMMITTEE: 'Executive',
  RISK_COMMITTEE: 'Risk',
  AUDIT_COMMITTEE: 'Audit',
  BCM_COMMITTEE: 'BCM',
  COMPLIANCE_COMMITTEE: 'Compliance',
  TECH_COMMITTEE: 'Technology',
  NOMINATION_REMUNERATION: 'Nomination & Remuneration',
  CYBER_SECURITY: 'Cyber Security',
  DATA_GOVERNANCE: 'Data Governance',
  PRIVACY_DPO: 'Privacy / DPO',
  CRISIS_MANAGEMENT: 'Crisis Management',
  AML: 'AML',
  IT_STEERING: 'IT Steering',
  ALCO: 'ALCO',
  INVESTMENT: 'Investment',
  AI_ETHICS: 'AI Ethics',
  ANTI_FRAUD: 'Anti-Fraud',
  INTERNAL_CONTROL: 'Internal Control',
  ESG_SUSTAINABILITY: 'ESG / Sustainability',
  ARCH_REVIEW: 'Architecture Review',
  OTHER: 'Other',
};

// ─── Resolutions + Action Items (V154 + operational layer) ───────────
// First-class records replacing the V40 decisions_json + action_items_json
// blobs. Resolutions are immutable; actions have a full lifecycle.

export type ResolutionOutcome = 'APPROVED' | 'REJECTED' | 'DEFERRED' | 'WITHDRAWN';
export type ConfidentialityLevel = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
export type ActionStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE' | 'CANCELLED' | 'DEFERRED';
export type ActionPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';

export interface Resolution {
  id: number;
  meetingId: number;
  committeeId: number;
  resolutionCode: string;
  seq: number;
  agendaItemSeq: number | null;
  titleEn: string;
  titleAr: string | null;
  bodyEn: string;
  bodyAr: string | null;
  proposedBy: string | null;
  secondedBy: string | null;
  voteFor: number;
  voteAgainst: number;
  voteAbstain: number;
  outcome: ResolutionOutcome;
  confidentiality: ConfidentialityLevel;
  effectiveFrom: string | null;
  reviewRequiredBy: string | null;
  regulatorNotify: number;
  dissentNotesEn: string | null;
  dissentNotesAr: string | null;
  createdAt: string;
}

export interface ActionItemRecord {
  id: number;
  meetingId: number;
  committeeId: number;
  actionCode: string;
  seq: number;
  agendaItemSeq: number | null;
  titleEn: string;
  titleAr: string | null;
  descriptionEn: string | null;
  descriptionAr: string | null;
  ownerUserId: number | null;
  ownerName: string;
  ownerRole: string | null;
  dueDate: string;
  priority: ActionPriority;
  status: ActionStatus;
  completionNotesEn: string | null;
  completedAt: string | null;
  completedBy: number | null;
  linkedModule: string | null;
  linkedEntityType: string | null;
  linkedEntityId: number | null;
  linkedEntityCode: string | null;
  carriedFromMeetingId: number | null;
  createdAt: string;
}

const RES_BASE = '/api/v1/committees/resolutions';
const ACT_BASE = '/api/v1/committees/action-items';
const CAL_BASE = '/api/v1/committees/calendar';

export function listResolutions(page = 0, size = 50):
    Promise<{ content: Resolution[]; totalElements: number }> {
  return api.get(`${RES_BASE}?page=${page}&size=${size}`);
}

export function getResolution(id: number): Promise<Resolution> {
  return api.get(`${RES_BASE}/${id}`);
}

export function resolutionsByMeeting(meetingId: number): Promise<Resolution[]> {
  return api.get(`${RES_BASE}/by-meeting/${meetingId}`);
}

export function resolutionsByCommittee(committeeId: number): Promise<Resolution[]> {
  return api.get(`${RES_BASE}/by-committee/${committeeId}`);
}

export function createResolution(body: Partial<Resolution>): Promise<Resolution> {
  return api.post(RES_BASE, body);
}

export function listActions(page = 0, size = 50):
    Promise<{ content: ActionItemRecord[]; totalElements: number }> {
  return api.get(`${ACT_BASE}?page=${page}&size=${size}`);
}

export function getAction(id: number): Promise<ActionItemRecord> {
  return api.get(`${ACT_BASE}/${id}`);
}

export function actionsByMeeting(meetingId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/by-meeting/${meetingId}`);
}

export function actionsByCommittee(committeeId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/by-committee/${committeeId}`);
}

export function openActionsForCommittee(committeeId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/open-for-committee/${committeeId}`);
}

export function openActionsForOwner(ownerUserId: number): Promise<ActionItemRecord[]> {
  return api.get(`${ACT_BASE}/open-for-owner/${ownerUserId}`);
}

export function createAction(body: Partial<ActionItemRecord>): Promise<ActionItemRecord> {
  return api.post(ACT_BASE, body);
}

export function startAction(id: number): Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/start`);
}

export function completeAction(id: number, completionNotesEn?: string, completedBy?: number):
    Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/complete`, { completionNotesEn: completionNotesEn ?? '', completedBy: completedBy ?? null });
}

export function cancelAction(id: number, reason: string): Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/cancel`, { reason });
}

export function deferAction(id: number, newDueDate: string, reason: string): Promise<ActionItemRecord> {
  return api.post(`${ACT_BASE}/${id}/defer`, { newDueDate, reason });
}

export function generateAnnualCalendar(committeeId: number, year: number):
    Promise<{ committeeId: number; year: number; created: number }> {
  return api.post(`${CAL_BASE}/generate-calendar/${committeeId}?year=${year}`);
}

export function populateAgenda(meetingId: number): Promise<CommitteeMeeting> {
  return api.post(`${CAL_BASE}/populate-agenda/${meetingId}`);
}

export const OUTCOME_TONE: Record<ResolutionOutcome, 'success' | 'danger' | 'warning' | 'neutral'> = {
  APPROVED: 'success', REJECTED: 'danger', DEFERRED: 'warning', WITHDRAWN: 'neutral',
};

export const ACTION_STATUS_TONE: Record<ActionStatus, 'warning' | 'info' | 'success' | 'danger' | 'neutral'> = {
  OPEN: 'warning', IN_PROGRESS: 'info', COMPLETED: 'success',
  OVERDUE: 'danger', CANCELLED: 'neutral', DEFERRED: 'warning',
};

export const ACTION_PRIORITY_TONE: Record<ActionPriority, 'neutral' | 'info' | 'warning' | 'danger'> = {
  LOW: 'neutral', NORMAL: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

export const TYPE_LABEL_AR: Record<ExtendedCommitteeType, string> = {
  BOARD: 'مجلس الإدارة',
  EXEC_COMMITTEE: 'تنفيذية',
  RISK_COMMITTEE: 'المخاطر',
  AUDIT_COMMITTEE: 'المراجعة',
  BCM_COMMITTEE: 'استمرارية الأعمال',
  COMPLIANCE_COMMITTEE: 'الالتزام',
  TECH_COMMITTEE: 'التقنية',
  NOMINATION_REMUNERATION: 'الترشيحات والمكافآت',
  CYBER_SECURITY: 'الأمن السيبراني',
  DATA_GOVERNANCE: 'حوكمة البيانات',
  PRIVACY_DPO: 'حماية البيانات',
  CRISIS_MANAGEMENT: 'إدارة الأزمات',
  AML: 'مكافحة غسل الأموال',
  IT_STEERING: 'توجيه تقنية المعلومات',
  ALCO: 'الأصول والخصوم',
  INVESTMENT: 'الاستثمار',
  AI_ETHICS: 'أخلاقيات الذكاء الاصطناعي',
  ANTI_FRAUD: 'مكافحة الاحتيال',
  INTERNAL_CONTROL: 'الرقابة الداخلية',
  ESG_SUSTAINABILITY: 'الاستدامة',
  ARCH_REVIEW: 'مراجعة المعمارية',
  OTHER: 'أخرى',
};

export function parseAgenda(json?: string): AgendaItem[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseAttendees(json?: string): AttendeeEntry[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseDecisions(json?: string): Decision[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseActions(json?: string): ActionItem[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseMembers(json?: string): CommitteeMember[] {
  if (!json) return []; try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}

export const COM_STATUS_TONE: Record<CommitteeStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  ACTIVE: 'success', INACTIVE: 'warning', DISSOLVED: 'info',
};
export const MTG_STATUS_TONE: Record<MeetingStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  SCHEDULED: 'info', IN_PROGRESS: 'primary', COMPLETED: 'warning',
  MINUTES_APPROVED: 'success', CANCELLED: 'danger',
};
