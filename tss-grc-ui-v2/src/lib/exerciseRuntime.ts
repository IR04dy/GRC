// Slice 10-B — Exercise Runtime client.
//
// Typed wrappers around /api/v1/bcm/exercise-runs/* — used by
// ../pages/bcm/FacilitatorConsolePage and ParticipantViewPage.
//
// Real-time updates come over the platform STOMP topic shape
// /topic/org.{orgId}.exercise-run.{runId}; subscribe via
// realtime.subscribe() with the helper exported below.
//
// Tenancy: every endpoint is org-scoped on the server, the JWT carries
// the tenant claim, so the client just calls the URL.

import { api } from './api';
import { subscribe, currentOrgId, type RealtimeHandler } from './realtime';

// ─── Domain types ─────────────────────────────────────────────────────

export type RunStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ABORTED';

export type Channel = 'IN_PERSON' | 'VIRTUAL_MEET' | 'HYBRID' | 'CHAT_ONLY';

export type InjectType =
  | 'INFORMATION'
  | 'ESCALATION'
  | 'DECISION'
  | 'MEDIA'
  | 'REGULATOR'
  | 'CUSTOM';

export type InjectStatus =
  | 'PENDING'
  | 'DISPATCHED'
  | 'ACKNOWLEDGED'
  | 'COMPLETED'
  | 'SKIPPED';

export interface RunSummary {
  id: number;
  exerciseId: number;
  runCode: string;
  status: RunStatus;
  channel: Channel;
  facilitatorUserId?: number;
  participantCount: number;
  startedAt?: string;
  endedAt?: string;
  createdAt?: string;
  totalInjects: number;
  dispatchedInjects: number;
  pendingInjects: number;
}

export interface InjectView {
  id: number;
  sourceInjectId?: number | null;
  plannedTimeOffsetMinutes: number;
  actualDispatchedAt?: string | null;
  injectType: InjectType;
  injectTextEn: string;
  injectTextAr?: string;
  expectedActionEn?: string;
  expectedActionAr?: string;
  status: InjectStatus;
  responseCount: number;
}

export interface ResponseView {
  id: number;
  runInjectId: number;
  participantUserId: number;
  responseTextEn?: string;
  responseTextAr?: string;
  decisionMadeEn?: string;
  decisionMadeAr?: string;
  responseAt: string;
  responseTimeSeconds?: number;
  qualityScore?: number;
  facilitatorCommentEn?: string;
  facilitatorCommentAr?: string;
}

export interface RunDetail {
  summary: RunSummary;
  facilitatorNotesEn?: string;
  facilitatorNotesAr?: string;
  injects: InjectView[];
}

export interface TimelineEntry {
  at: string;
  kind: 'INJECT_DISPATCHED' | 'RESPONSE' | 'STATE_CHANGE' | 'CUSTOM_INJECT';
  runInjectId?: number;
  responseId?: number;
  offsetMinutesPlanned?: number;
  responseTimeSeconds?: number;
  injectType?: InjectType;
  label?: string;
  detailEn?: string;
  detailAr?: string;
}

export interface RunTimeline {
  runId: number;
  runCode: string;
  status: RunStatus;
  startedAt?: string;
  endedAt?: string;
  entries: TimelineEntry[];
}

export interface StartRunRequest {
  exerciseId: number;
  facilitatorUserId?: number;
  channel?: Channel;
  participantCount?: number;
  facilitatorNotesEn?: string;
  facilitatorNotesAr?: string;
  runCode?: string;
}

export interface CustomInjectRequest {
  injectTextEn: string;
  injectTextAr?: string;
  expectedActionEn?: string;
  expectedActionAr?: string;
  injectType?: InjectType;
  plannedTimeOffsetMinutes?: number;
  dispatchNow?: boolean;
}

export interface ResponseRequest {
  participantUserId: number;
  responseTextEn?: string;
  responseTextAr?: string;
  decisionMadeEn?: string;
  decisionMadeAr?: string;
}

export type TransitionAction = 'PAUSE' | 'RESUME' | 'END' | 'ABORT';

export interface TransitionRequest {
  summaryEn?: string;
  summaryAr?: string;
}

// ─── Static labels (Saudi-native AR) ──────────────────────────────────

export const INJECT_TYPE_LABELS: Record<InjectType, { en: string; ar: string }> = {
  INFORMATION: { en: 'Information', ar: 'معلومة' },
  ESCALATION:  { en: 'Escalation',  ar: 'تصعيد' },
  DECISION:    { en: 'Decision',    ar: 'قرار' },
  MEDIA:       { en: 'Media',       ar: 'إعلام' },
  REGULATOR:   { en: 'Regulator',   ar: 'جهة تنظيمية' },
  CUSTOM:      { en: 'Custom',      ar: 'مخصص' },
};

export const INJECT_STATUS_LABELS: Record<InjectStatus, { en: string; ar: string }> = {
  PENDING:      { en: 'Pending',      ar: 'قيد الانتظار' },
  DISPATCHED:   { en: 'Dispatched',   ar: 'تمّ الإطلاق' },
  ACKNOWLEDGED: { en: 'Acknowledged', ar: 'تمّ الاستلام' },
  COMPLETED:    { en: 'Completed',    ar: 'مكتمل' },
  SKIPPED:      { en: 'Skipped',      ar: 'تمّ التخطّي' },
};

export const RUN_STATUS_LABELS: Record<RunStatus, { en: string; ar: string }> = {
  SCHEDULED:   { en: 'Scheduled',   ar: 'مُجدوَل' },
  IN_PROGRESS: { en: 'In progress', ar: 'جارٍ التنفيذ' },
  PAUSED:      { en: 'Paused',      ar: 'متوقف مؤقتاً' },
  COMPLETED:   { en: 'Completed',   ar: 'مكتمل' },
  ABORTED:     { en: 'Aborted',     ar: 'مُلغى' },
};

export const CHANNEL_LABELS: Record<Channel, { en: string; ar: string }> = {
  IN_PERSON:    { en: 'In person',    ar: 'حضوري' },
  VIRTUAL_MEET: { en: 'Virtual',      ar: 'افتراضي' },
  HYBRID:       { en: 'Hybrid',       ar: 'مختلط' },
  CHAT_ONLY:    { en: 'Chat only',    ar: 'دردشة فقط' },
};

// ─── Helpers ──────────────────────────────────────────────────────────

/** Convert a Western digit string to Arabic-Indic numerals. */
export function toArabicDigits(n: number | string): string {
  return String(n).replace(/[0-9]/g, d => '٠١٢٣٤٥٦٧٨٩'[Number(d)]);
}

/** Compute the schedule status of a PENDING inject vs the run start. */
export function injectScheduleStatus(
  inj: InjectView,
  runStartedAt: string | undefined,
  now: Date,
): { state: 'EARLY' | 'ON_TIME' | 'LATE'; deltaMinutes: number } {
  if (!runStartedAt) return { state: 'ON_TIME', deltaMinutes: 0 };
  const elapsed = (now.getTime() - new Date(runStartedAt).getTime()) / 60000;
  const delta = Math.round(elapsed - inj.plannedTimeOffsetMinutes);
  if (delta < -1) return { state: 'EARLY',   deltaMinutes: -delta };
  if (delta > 1)  return { state: 'LATE',    deltaMinutes: delta };
  return { state: 'ON_TIME', deltaMinutes: 0 };
}

/** Pretty-print "T+90" / "T+1h 30m". */
export function fmtOffset(mins: number, isAr = false): string {
  if (mins === 0) return 'T+0';
  if (mins < 60) {
    return isAr ? `الدقيقة ${toArabicDigits(mins)}` : `T+${mins}m`;
  }
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (m === 0) return isAr ? `+${toArabicDigits(h)} ساعة` : `T+${h}h`;
  return isAr ? `+${toArabicDigits(h)} س ${toArabicDigits(m)} د` : `T+${h}h ${m}m`;
}

// ─── REST calls ───────────────────────────────────────────────────────

const BASE = '/api/v1/bcm/exercise-runs';

export function startRun(body: StartRunRequest): Promise<RunSummary> {
  return api.post<RunSummary>(BASE, body);
}

export function getRun(id: number): Promise<RunDetail> {
  return api.get<RunDetail>(`${BASE}/${id}`);
}

export function listRunsForExercise(exerciseId: number): Promise<RunSummary[]> {
  return api.get<RunSummary[]>(`${BASE}/by-exercise/${exerciseId}`);
}

export function getRunTimeline(id: number): Promise<RunTimeline> {
  return api.get<RunTimeline>(`${BASE}/${id}/timeline`);
}

export function dispatchInject(runId: number, injectId: number): Promise<InjectView> {
  return api.post<InjectView>(`${BASE}/${runId}/injects/${injectId}/dispatch`, {});
}

export function recordResponse(
  runId: number, injectId: number, body: ResponseRequest,
): Promise<ResponseView> {
  return api.post<ResponseView>(`${BASE}/${runId}/injects/${injectId}/responses`, body);
}

export function listResponses(runId: number, injectId: number): Promise<ResponseView[]> {
  return api.get<ResponseView[]>(`${BASE}/${runId}/injects/${injectId}/responses`);
}

export function addCustomInject(runId: number, body: CustomInjectRequest): Promise<InjectView> {
  return api.post<InjectView>(`${BASE}/${runId}/custom-inject`, body);
}

export function transitionRun(
  runId: number, action: TransitionAction, body?: TransitionRequest,
): Promise<RunSummary> {
  return api.post<RunSummary>(
    `${BASE}/${runId}/transition?action=${action}`, body ?? {});
}

// ─── Realtime ─────────────────────────────────────────────────────────

/**
 * Subscribe to the live run topic. Server publishes:
 *   { type: 'INJECT_DISPATCHED', runInjectId, injectTextEn/Ar, … }
 *   { type: 'RESPONSE_RECORDED', runInjectId, responseId, … }
 *   { type: 'STATE_CHANGE',     from, to, runId }
 *   { type: 'CUSTOM_INJECT_ADDED', runInjectId, injectType }
 *
 * Topic shape mirrors {@link RealtimeBroadcaster.broadcastOrgEvent}:
 *   /topic/org.{orgId}.exercise-run.{runId}
 */
export function subscribeToRun(runId: number, handler: RealtimeHandler): () => void {
  const org = currentOrgId();
  if (!org) {
    // No active session — nothing to subscribe to; return a no-op unsubscribe.
    return () => {};
  }
  const topic = `/topic/org.${org}.exercise-run.${runId}`;
  return subscribe(topic, handler);
}
