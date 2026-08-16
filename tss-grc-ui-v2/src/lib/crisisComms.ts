// Crisis Communications v2 client — Slice 9-E.
//
// Typed wrappers around /api/v1/bcm/comms/v2/* plus catalogues for
// audience / scenario / severity / approval-status that the list,
// detail, and dispatch-wizard pages share. Mirrors the shape used by
// ../lib/strategyRoi so a developer can switch between them without
// learning a new convention.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

export type Audience =
  | 'BOARD' | 'REGULATOR' | 'CUSTOMERS' | 'STAFF'
  | 'PARTNERS' | 'MEDIA' | 'PUBLIC';

export type ScenarioCategory =
  | 'RANSOMWARE' | 'DATA_BREACH' | 'OUTAGE' | 'NATURAL_DISASTER'
  | 'OT_INCIDENT' | 'REGULATORY_NOTIFICATION' | 'MEDIA_RESPONSE'
  | 'RECOVERY' | 'LESSONS_LEARNED' | 'GENERIC_MAJOR';

export type SeverityBand = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'INFORMATIONAL';

export type ApprovalStatus =
  | 'DRAFT' | 'IN_REVIEW' | 'APPROVED_BY_LEGAL' | 'APPROVED_BY_CISO' | 'RETIRED';

export type ChannelType = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'TEAMS' | 'VOICE_BLAST';

export type CascadeStatus =
  | 'QUEUED' | 'DISPATCHING' | 'COMPLETED' | 'FAILED' | 'PARTIAL';

export interface CrisisCommsTemplate {
  id: number;
  externalId: string;
  organisationId: number;
  templateCode: string;
  titleEn: string;
  titleAr: string;
  audience: Audience;
  scenarioCategory: ScenarioCategory;
  severityBand: SeverityBand;
  subjectEn: string;
  subjectAr: string;
  bodyEn: string;
  bodyAr: string;
  smsBodyEn?: string;
  smsBodyAr?: string;
  placeholdersJson?: string;
  regulatoryAnchor?: string;
  approvalStatus: ApprovalStatus;
  approvedByLegalAt?: string;
  approvedByLegalName?: string;
  approvedByCisoAt?: string;
  approvedByCisoName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TemplateRequest {
  templateCode?: string;
  titleEn: string;
  titleAr: string;
  audience: Audience;
  scenarioCategory: ScenarioCategory;
  severityBand: SeverityBand;
  subjectEn: string;
  subjectAr: string;
  bodyEn: string;
  bodyAr: string;
  smsBodyEn?: string;
  smsBodyAr?: string;
  placeholdersJson?: string;
  regulatoryAnchor?: string;
}

export interface ApprovalTransitionRequest {
  approvalStatus: ApprovalStatus;
  approverUserId?: number;
}

export interface DispatchRequest {
  crisisEventId: number;
  templateId: number;
  channels: ChannelType[];
  audienceFilter?: string;
  recipients?: string[];
  placeholders?: Record<string, string>;
}

export interface CascadeResponse {
  id: number;
  externalId: string;
  organisationId: number;
  crisisEventId: number;
  templateId: number;
  channel: ChannelType;
  audience?: string;
  renderedSubject?: string;
  recipientCount: number;
  successCount: number;
  failureCount: number;
  dispatchedAt: string;
  completedAt?: string;
  dispatchedByName?: string;
  status: CascadeStatus;
  responsePayload?: string;
  /**
   * F2-147 — true when no gateway was involved: the counters above are what
   * *would* have been sent. Persisted (V363), so it is still true on a cascade
   * read back months later, which is when it matters.
   */
  simulated?: boolean;
}

export interface DispatchResult {
  crisisEventId: number;
  templateId: number;
  cascades: CascadeResponse[];
  totalRecipients: number;
  totalSuccess: number;
  totalFailure: number;
  elapsedMillis: number;
  /**
   * Channels that only logged. `totalSuccess` reads as "how many people were
   * told"; the two are the same number only when this is empty — which is why
   * no success figure in the UI may be rendered without consulting it.
   */
  simulatedChannels?: string[];
}

/** Per-channel readiness, so an operator learns this before a crisis (F2-147). */
export interface ChannelReadiness {
  channelType: ChannelType;
  registered: boolean;
  healthy: boolean;
  /** LIVE | SIMULATED | UNREGISTERED | UNKNOWN — anything but LIVE means nothing is sent. */
  deliveryMode: string;
}

/** True when this channel does not hand the message to any gateway. */
export function isSimulatedChannel(r: ChannelReadiness): boolean {
  return r.deliveryMode !== 'LIVE';
}

export interface PlaceholderDescriptor {
  key: string;
  label_en?: string;
  label_ar?: string;
  type?: 'text' | 'number' | 'datetime' | 'textarea';
  required?: boolean;
}

// ─── Catalogues ───────────────────────────────────────────────────────

export const AUDIENCES: Audience[] = [
  'BOARD', 'REGULATOR', 'CUSTOMERS', 'STAFF', 'PARTNERS', 'MEDIA', 'PUBLIC',
];

export const SCENARIO_CATEGORIES: ScenarioCategory[] = [
  'RANSOMWARE', 'DATA_BREACH', 'OUTAGE', 'NATURAL_DISASTER', 'OT_INCIDENT',
  'REGULATORY_NOTIFICATION', 'MEDIA_RESPONSE', 'RECOVERY',
  'LESSONS_LEARNED', 'GENERIC_MAJOR',
];

export const SEVERITY_BANDS: SeverityBand[] = [
  'CRITICAL', 'HIGH', 'MEDIUM', 'INFORMATIONAL',
];

export const APPROVAL_STATES: ApprovalStatus[] = [
  'DRAFT', 'IN_REVIEW', 'APPROVED_BY_LEGAL', 'APPROVED_BY_CISO', 'RETIRED',
];

export const CHANNELS: ChannelType[] = ['EMAIL', 'SMS', 'WHATSAPP', 'TEAMS'];

export const AUDIENCE_LABELS: Record<Audience, { en: string; ar: string }> = {
  BOARD:     { en: 'Board',      ar: 'مجلس الإدارة' },
  REGULATOR: { en: 'Regulator',  ar: 'الجهة التنظيمية' },
  CUSTOMERS: { en: 'Customers',  ar: 'العملاء' },
  STAFF:     { en: 'Staff',      ar: 'الموظفون' },
  PARTNERS:  { en: 'Partners',   ar: 'الشركاء' },
  MEDIA:     { en: 'Media',      ar: 'الإعلام' },
  PUBLIC:    { en: 'Public',     ar: 'الجمهور' },
};

export const SCENARIO_LABELS: Record<ScenarioCategory, { en: string; ar: string }> = {
  RANSOMWARE:              { en: 'Ransomware',                ar: 'فدية إلكترونية' },
  DATA_BREACH:             { en: 'Data breach',               ar: 'خرق بيانات شخصية' },
  OUTAGE:                  { en: 'Service outage',            ar: 'انقطاع خدمة' },
  NATURAL_DISASTER:        { en: 'Natural disaster',          ar: 'كارثة طبيعية' },
  OT_INCIDENT:             { en: 'OT/ICS incident',           ar: 'حادث في أنظمة التشغيل الصناعية' },
  REGULATORY_NOTIFICATION: { en: 'Regulatory notification',   ar: 'إشعار تنظيمي' },
  MEDIA_RESPONSE:          { en: 'Media response',            ar: 'استجابة إعلامية' },
  RECOVERY:                { en: 'Recovery',                  ar: 'الاستعادة' },
  LESSONS_LEARNED:         { en: 'Lessons learned',           ar: 'الدروس المستفادة' },
  GENERIC_MAJOR:           { en: 'Major incident (generic)',  ar: 'حادث جوهري (عام)' },
};

export const SEVERITY_LABELS: Record<SeverityBand, { en: string; ar: string }> = {
  CRITICAL:      { en: 'Critical',     ar: 'حرج' },
  HIGH:          { en: 'High',         ar: 'مرتفع' },
  MEDIUM:        { en: 'Medium',       ar: 'متوسط' },
  INFORMATIONAL: { en: 'Informational', ar: 'إعلامي' },
};

export const APPROVAL_LABELS: Record<ApprovalStatus, { en: string; ar: string }> = {
  DRAFT:             { en: 'Draft',                 ar: 'مسودة' },
  IN_REVIEW:         { en: 'In review',             ar: 'قيد المراجعة' },
  APPROVED_BY_LEGAL: { en: 'Approved by Legal',     ar: 'اعتماد قانوني' },
  APPROVED_BY_CISO:  { en: 'Approved by CISO',      ar: 'اعتماد أمن المعلومات' },
  RETIRED:           { en: 'Retired',               ar: 'مُسحَب من الخدمة' },
};

export const CHANNEL_LABELS: Record<ChannelType, { en: string; ar: string }> = {
  EMAIL:       { en: 'Email',          ar: 'البريد الإلكتروني' },
  SMS:         { en: 'SMS',            ar: 'رسالة نصية' },
  WHATSAPP:    { en: 'WhatsApp',       ar: 'واتساب' },
  TEAMS:       { en: 'Microsoft Teams', ar: 'مايكروسوفت تيمز' },
  VOICE_BLAST: { en: 'Voice blast',    ar: 'اتصال صوتي جماعي' },
};

// ─── Tone helpers ─────────────────────────────────────────────────────

export function severityTone(s: SeverityBand): BadgeTone {
  switch (s) {
    case 'CRITICAL':      return 'danger';
    case 'HIGH':          return 'warning';
    case 'MEDIUM':        return 'info';
    case 'INFORMATIONAL': return 'neutral';
  }
}

export function approvalTone(s: ApprovalStatus): BadgeTone {
  switch (s) {
    case 'DRAFT':             return 'neutral';
    case 'IN_REVIEW':         return 'info';
    case 'APPROVED_BY_LEGAL': return 'warning';
    case 'APPROVED_BY_CISO':  return 'success';
    case 'RETIRED':           return 'danger';
  }
}

export function cascadeStatusTone(s: CascadeStatus): BadgeTone {
  switch (s) {
    case 'COMPLETED':   return 'success';
    case 'PARTIAL':     return 'warning';
    case 'FAILED':      return 'danger';
    case 'DISPATCHING': return 'info';
    case 'QUEUED':      return 'neutral';
  }
}

/**
 * Approval state machine — returns the set of states a template in
 * {@code from} state can transition into. Matches the backend guard.
 */
export function allowedTransitions(from: ApprovalStatus): ApprovalStatus[] {
  switch (from) {
    case 'DRAFT':             return ['IN_REVIEW'];
    case 'IN_REVIEW':         return ['APPROVED_BY_LEGAL', 'DRAFT'];
    case 'APPROVED_BY_LEGAL': return ['APPROVED_BY_CISO', 'IN_REVIEW'];
    case 'APPROVED_BY_CISO':  return ['RETIRED', 'IN_REVIEW'];
    case 'RETIRED':           return ['IN_REVIEW'];
  }
}

export function parsePlaceholders(json?: string): PlaceholderDescriptor[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    if (!Array.isArray(parsed)) return [];
    return parsed as PlaceholderDescriptor[];
  } catch {
    return [];
  }
}

// ─── API calls ────────────────────────────────────────────────────────

const TPL_BASE = '/api/v1/bcm/comms/v2/templates';
const CASCADE_BASE = '/api/v1/bcm/comms/v2';

export interface TemplateListFilters {
  audience?: Audience;
  scenario?: ScenarioCategory;
  approvalStatus?: ApprovalStatus;
}

export function listTemplates(filters: TemplateListFilters = {}): Promise<CrisisCommsTemplate[]> {
  const params = new URLSearchParams();
  if (filters.audience) params.set('audience', filters.audience);
  if (filters.scenario) params.set('scenario', filters.scenario);
  if (filters.approvalStatus) params.set('approvalStatus', filters.approvalStatus);
  const qs = params.toString();
  return api.get<CrisisCommsTemplate[]>(qs ? `${TPL_BASE}?${qs}` : TPL_BASE);
}

export function getTemplate(externalId: string): Promise<CrisisCommsTemplate> {
  return api.get<CrisisCommsTemplate>(`${TPL_BASE}/${externalId}`);
}

export function createTemplate(body: TemplateRequest): Promise<CrisisCommsTemplate> {
  return api.post<CrisisCommsTemplate>(TPL_BASE, body);
}

export function updateTemplate(externalId: string, body: TemplateRequest): Promise<CrisisCommsTemplate> {
  return api.put<CrisisCommsTemplate>(`${TPL_BASE}/${externalId}`, body);
}

export function transitionTemplate(
  externalId: string, body: ApprovalTransitionRequest,
): Promise<CrisisCommsTemplate> {
  return api.post<CrisisCommsTemplate>(`${TPL_BASE}/${externalId}/transition`, body);
}

export function archiveTemplate(externalId: string): Promise<CrisisCommsTemplate> {
  return api.delete<CrisisCommsTemplate>(`${TPL_BASE}/${externalId}`);
}

export function dispatchCascade(body: DispatchRequest): Promise<DispatchResult> {
  return api.post<DispatchResult>(`${CASCADE_BASE}/dispatch`, body);
}

export function listCascades(crisisEventId?: number): Promise<CascadeResponse[]> {
  const url = crisisEventId != null
    ? `${CASCADE_BASE}/cascades?crisisEventId=${crisisEventId}`
    : `${CASCADE_BASE}/cascades`;
  return api.get<CascadeResponse[]>(url);
}

export function listChannelReadiness(): Promise<ChannelReadiness[]> {
  return api.get<ChannelReadiness[]>(`${CASCADE_BASE}/channels`);
}
