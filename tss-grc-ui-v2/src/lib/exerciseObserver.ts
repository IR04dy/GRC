// Slice 10-C — Observer Dashboard client.
//
// Typed wrappers around /api/v1/bcm/exercise-runs/{runId}/{observers|observations|...}
// — used by ../pages/bcm/ObserverDashboardPage and the assignment modal
// embedded in FacilitatorConsolePage.
//
// Real-time updates come over the platform STOMP topic shape
// /topic/org.{orgId}.exercise-run.{runId} (reused from Slice 10-B); the
// payload type discriminator is `OBSERVATION_ADDED` / `OBSERVATION_FLAGGED`
// / `OBSERVER_ASSIGNED` / `OBSERVER_REMOVED` on top of the 10-B types.
//
// Tenancy: every endpoint is org-scoped on the server, the JWT carries the
// tenant claim, so the client just calls the URL.

import { api } from './api';
import type { InjectView, ResponseView, RunSummary } from './exerciseRuntime';

// ─── Domain types ─────────────────────────────────────────────────────

export type ObserverRole =
  | 'INTERNAL_AUDIT'
  | 'EXTERNAL_AUDIT'
  | 'COMPLIANCE_OBSERVER'
  | 'SAMA_LIAISON'
  | 'NCA_LIAISON'
  | 'BOARD_OBSERVER';

export type ObservationType =
  | 'STRENGTH'
  | 'GAP'
  | 'RISK'
  | 'COMMUNICATION'
  | 'DECISION_QUALITY'
  | 'ESCALATION'
  | 'RECOVERY_TIME'
  | 'COMPLIANCE'
  | 'GENERAL';

export type ObservationSeverity = 'INFO' | 'MINOR' | 'MODERATE' | 'MAJOR' | 'CRITICAL';

export interface AssignmentView {
  id: number;
  exerciseRunId: number;
  observerUserId: number;
  observerRole: ObserverRole;
  assignedByUserId?: number;
  assignedAt: string;
}

export interface ObservationView {
  id: number;
  exerciseRunId: number;
  observerUserId: number;
  relatedInjectId?: number | null;
  observationTextEn: string;
  observationTextAr?: string;
  observationType: ObservationType;
  severity: ObservationSeverity;
  recommendationEn?: string;
  recommendationAr?: string;
  recordedAt: string;
  timeOffsetMinutes: number;
  isFlaggedForAar: boolean;
}

export interface ObserverDashboard {
  summary: RunSummary;
  injects: InjectView[];
  responsesByInject: Record<string, ResponseView[]>;
  observations: ObservationView[];
  observers: AssignmentView[];
  currentObserverUserId?: number | null;
  isAssignedObserver: boolean;
}

export interface ObservationsByType {
  exerciseRunId: number;
  byType: Partial<Record<ObservationType, ObservationView[]>>;
}

export interface AssignObserverRequest {
  observerUserId: number;
  observerRole: ObserverRole;
}

export interface ObservationRequest {
  observationTextEn?: string;
  observationTextAr?: string;
  observationType?: ObservationType;
  severity?: ObservationSeverity;
  recommendationEn?: string;
  recommendationAr?: string;
  relatedInjectId?: number | null;
  flagForAar?: boolean;
}

// ─── Saudi-native labels (EN + AR) ────────────────────────────────────

/** Observer console title — Saudi-native, NOT literal "لوحة المراقب". */
export const OBSERVER_CONSOLE_TITLE = { en: 'Observer console', ar: 'وحدة المراقب' } as const;

export const OBSERVER_ROLE_LABELS: Record<ObserverRole, { en: string; ar: string }> = {
  INTERNAL_AUDIT:      { en: 'Internal Audit',      ar: 'المراجعة الداخلية' },
  EXTERNAL_AUDIT:      { en: 'External Audit',      ar: 'المراجعة الخارجية' },
  COMPLIANCE_OBSERVER: { en: 'Compliance Observer', ar: 'مراقب الالتزام' },
  SAMA_LIAISON:        { en: 'SAMA Liaison',        ar: 'ممثل ساما' },
  NCA_LIAISON:         { en: 'NCA Liaison',         ar: 'ممثل الهيئة الوطنية للأمن السيبراني' },
  BOARD_OBSERVER:      { en: 'Board Observer',      ar: 'مراقب من المجلس' },
};

/**
 * Observation type labels — Saudi-native Arabic phrasing, deliberately NOT
 * literal English calques.
 */
export const OBSERVATION_TYPE_LABELS: Record<ObservationType, { en: string; ar: string }> = {
  STRENGTH:         { en: 'Strength',         ar: 'نقطة قوة' },
  GAP:              { en: 'Gap',              ar: 'فجوة' },
  RISK:             { en: 'Risk',             ar: 'مخاطرة' },
  COMMUNICATION:    { en: 'Communication',    ar: 'اتصالات' },
  DECISION_QUALITY: { en: 'Decision quality', ar: 'جودة القرار' },
  ESCALATION:       { en: 'Escalation',       ar: 'تصعيد' },
  RECOVERY_TIME:    { en: 'Recovery time',    ar: 'زمن الاسترداد' },
  COMPLIANCE:       { en: 'Compliance',       ar: 'امتثال' },
  GENERAL:          { en: 'General note',     ar: 'ملاحظة عامة' },
};

export const SEVERITY_LABELS: Record<ObservationSeverity, { en: string; ar: string }> = {
  INFO:     { en: 'Info',     ar: 'معلومة' },
  MINOR:    { en: 'Minor',    ar: 'منخفضة' },
  MODERATE: { en: 'Moderate', ar: 'متوسطة' },
  MAJOR:    { en: 'Major',    ar: 'مرتفعة' },
  CRITICAL: { en: 'Critical', ar: 'حرجة' },
};

/** Stable type-order matching the backend's grouping. */
export const OBSERVATION_TYPE_ORDER: ObservationType[] = [
  'STRENGTH', 'GAP', 'RISK', 'COMMUNICATION', 'DECISION_QUALITY',
  'ESCALATION', 'RECOVERY_TIME', 'COMPLIANCE', 'GENERAL',
];

/** Severity ordering (low → high) for the dropdown. */
export const SEVERITY_ORDER: ObservationSeverity[] = [
  'INFO', 'MINOR', 'MODERATE', 'MAJOR', 'CRITICAL',
];

/** Color-coding for the observation pill on the dashboard. */
export function observationTypeBadgeTone(type: ObservationType):
    'success' | 'warning' | 'danger' | 'info' | 'accent' | 'neutral' {
  switch (type) {
    case 'STRENGTH':         return 'success';
    case 'GAP':              return 'warning';
    case 'RISK':             return 'danger';
    case 'COMMUNICATION':    return 'info';
    case 'DECISION_QUALITY': return 'accent';
    case 'ESCALATION':       return 'warning';
    case 'RECOVERY_TIME':    return 'info';
    case 'COMPLIANCE':       return 'danger';
    case 'GENERAL':          return 'neutral';
  }
}

export function severityBadgeTone(s: ObservationSeverity):
    'neutral' | 'info' | 'warning' | 'danger' {
  switch (s) {
    case 'INFO':     return 'neutral';
    case 'MINOR':    return 'info';
    case 'MODERATE': return 'warning';
    case 'MAJOR':    return 'danger';
    case 'CRITICAL': return 'danger';
  }
}

// ─── REST calls ───────────────────────────────────────────────────────

const BASE = '/api/v1/bcm/exercise-runs';

export function assignObserver(runId: number, body: AssignObserverRequest): Promise<AssignmentView> {
  return api.post<AssignmentView>(`${BASE}/${runId}/observers`, body);
}

export function removeObserver(runId: number, assignmentId: number): Promise<void> {
  return api.delete<void>(`${BASE}/${runId}/observers/${assignmentId}`);
}

export function listObservers(runId: number): Promise<AssignmentView[]> {
  return api.get<AssignmentView[]>(`${BASE}/${runId}/observers`);
}

export function getObserverDashboard(
  runId: number, observerUserId?: number,
): Promise<ObserverDashboard> {
  const q = observerUserId != null ? `?observerUserId=${observerUserId}` : '';
  return api.get<ObserverDashboard>(`${BASE}/${runId}/observer-dashboard${q}`);
}

export function recordObservation(runId: number, body: ObservationRequest): Promise<ObservationView> {
  return api.post<ObservationView>(`${BASE}/${runId}/observations`, body);
}

export function flagObservation(
  runId: number, observationId: number, flagged: boolean,
): Promise<ObservationView> {
  return api.put<ObservationView>(
    `${BASE}/${runId}/observations/${observationId}/flag`, { flagged });
}

export function listObservations(runId: number): Promise<ObservationView[]> {
  return api.get<ObservationView[]>(`${BASE}/${runId}/observations`);
}

export function observationsByType(runId: number): Promise<ObservationsByType> {
  return api.get<ObservationsByType>(`${BASE}/${runId}/observations/by-type`);
}
