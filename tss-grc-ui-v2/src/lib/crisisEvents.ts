// Crisis Event client — independent audit Tier-1 fix.
//
// Talks to /api/v1/bcm/crisis-events/* and surfaces typed shapes that the
// Crisis Room list + detail pages render directly.

import { api, downloadFile } from './api';

/**
 * Tier-2 #12: trigger a browser download for the crisis event's
 * after-action PDF (cover + SitReps + decisions + regulator notifications).
 */
export function downloadCrisisEventPdf(eventId: number, eventCode?: string): Promise<void> {
  const fallback = (eventCode ? eventCode : `crisis-${eventId}`) + '.pdf';
  return downloadFile(`/api/v1/bcm/crisis-events/${eventId}/export.pdf`, fallback);
}

export type CrisisStatus =
  | 'DETECTED' | 'ASSESSING' | 'ACTIVATED' | 'CONTAINED' | 'RECOVERED' | 'CLOSED' | 'CANCELLED';

export type TriggerSource =
  | 'MANUAL' | 'INCIDENT' | 'EXERCISE' | 'THREAT_INTEL' | 'SUPPLIER' | 'CYBER' | 'EXTERNAL_ALERT';

export type Regulator =
  | 'NCEMA' | 'SAMA' | 'NCA' | 'CMA' | 'PDPL' | 'CIVIL_DEFENCE' | 'MOH' | 'MOE' | 'OTHER';

export interface CrisisEvent {
  id: number;
  organisationId?: number;
  eventCode: string;
  titleEn: string;
  titleAr?: string;
  description?: string;
  severity: 1 | 2 | 3 | 4 | 5;
  triggerSource?: TriggerSource;
  affectedServicesJson?: string;
  affectedProcessesJson?: string;
  affectedSitesJson?: string;
  affectedSuppliersJson?: string;
  affectedSystemsJson?: string;
  detectionTime?: string;
  activationTime?: string;
  startTime?: string;
  closureTime?: string;
  crisisManagerId?: number;
  crisisTeamJson?: string;
  rootCause?: string;
  status: CrisisStatus;
  relatedBcpIdsJson?: string;
  relatedDrpIdsJson?: string;
  personalDataAffected: boolean;
  cyberRelated: boolean;
  supplierInvolved: boolean;
  lessonsLearnedId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CrisisSituationReport {
  id: number;
  crisisEventId: number;
  reportedAt: string;
  reportedBy?: number;
  summary: string;
  statusUpdate?: 'WORSENING' | 'STABILISING' | 'RECOVERING' | 'NORMAL';
  severityChange?: 1 | 2 | 3 | 4 | 5;
  attachmentsJson?: string;
}

export interface CrisisDecision {
  id: number;
  crisisEventId: number;
  decidedAt: string;
  decidedBy?: number;
  decidedByName?: string;
  decisionText: string;
  rationale?: string;
  actionRequired?: string;
  actionOwnerId?: number;
  actionDueDate?: string;
  capaId?: number;
}

export interface CrisisRegulatoryNotification {
  id: number;
  crisisEventId: number;
  regulator: Regulator;
  notifiedAt: string;
  notifiedBy?: number;
  notificationMethod?: 'EMAIL' | 'PORTAL' | 'PHONE' | 'LETTER' | 'API';
  referenceNumber?: string;
  bodySummary?: string;
  acknowledgementReceived: boolean;
  acknowledgementAt?: string;
  acknowledgementReference?: string;
}

// ─── Severity labels & tones ──────────────────────────────────────

export const SEVERITY_LABELS: Record<number, { en: string; ar: string }> = {
  1: { en: 'Operational event',     ar: 'حدث تشغيلي' },
  2: { en: 'Significant incident',  ar: 'حادث مؤثر' },
  3: { en: 'Major disruption',      ar: 'تعطل جوهري' },
  4: { en: 'Crisis',                ar: 'أزمة' },
  5: { en: 'National emergency',    ar: 'طارئ وطني' },
};

export function severityTone(sev: number | undefined): 'success' | 'info' | 'warning' | 'danger' {
  if (!sev || sev <= 1) return 'success';
  if (sev === 2)        return 'info';
  if (sev === 3)        return 'warning';
  return 'danger';                  // 4 (Crisis) and 5 (National Emergency)
}

export function statusTone(s: CrisisStatus): 'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'DETECTED':  return 'warning';
    case 'ASSESSING': return 'warning';
    case 'ACTIVATED': return 'danger';
    case 'CONTAINED': return 'info';
    case 'RECOVERED': return 'info';
    case 'CLOSED':    return 'success';
    case 'CANCELLED': return 'neutral';
  }
}

// ─── Event CRUD ───────────────────────────────────────────────────

export async function listCrisisEvents(page = 0, size = 50): Promise<{ content: CrisisEvent[]; totalElements: number }> {
  return api.get<{ content: CrisisEvent[]; totalElements: number }>(`/api/v1/bcm/crisis-events?page=${page}&size=${size}`);
}

export async function listActiveCrisisEvents(): Promise<CrisisEvent[]> {
  return api.get<CrisisEvent[]>('/api/v1/bcm/crisis-events/active');
}

export async function crisisEventStats(): Promise<Record<string, number>> {
  return api.get<Record<string, number>>('/api/v1/bcm/crisis-events/stats');
}

export async function getCrisisEvent(id: number): Promise<CrisisEvent> {
  return api.get<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}`);
}

export async function createCrisisEvent(body: Partial<CrisisEvent>): Promise<CrisisEvent> {
  return api.post<CrisisEvent>('/api/v1/bcm/crisis-events', body);
}

export async function patchCrisisEvent(id: number, body: Partial<CrisisEvent>): Promise<CrisisEvent> {
  return api.patch<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}`, body);
}

export async function transitionCrisisEvent(id: number, to: CrisisStatus): Promise<CrisisEvent> {
  return api.post<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}/transition`, { to });
}

export async function activateBcp(id: number, bcpId: number): Promise<CrisisEvent> {
  return api.post<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}/activate-bcp`, { bcpId });
}

export async function activateDrp(id: number, drpId: number): Promise<CrisisEvent> {
  return api.post<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}/activate-drp`, { drpId });
}

// ─── Timeline sub-resources ────────────────────────────────────────

export async function listSitReps(eventId: number): Promise<CrisisSituationReport[]> {
  return api.get<CrisisSituationReport[]>(`/api/v1/bcm/crisis-events/${eventId}/sitreps`);
}

export async function addSitRep(eventId: number, body: Partial<CrisisSituationReport>): Promise<CrisisSituationReport> {
  return api.post<CrisisSituationReport>(`/api/v1/bcm/crisis-events/${eventId}/sitreps`, body);
}

export async function listDecisions(eventId: number): Promise<CrisisDecision[]> {
  return api.get<CrisisDecision[]>(`/api/v1/bcm/crisis-events/${eventId}/decisions`);
}

export async function addDecision(eventId: number, body: Partial<CrisisDecision>): Promise<CrisisDecision> {
  return api.post<CrisisDecision>(`/api/v1/bcm/crisis-events/${eventId}/decisions`, body);
}

export async function listRegulatoryNotifications(eventId: number): Promise<CrisisRegulatoryNotification[]> {
  return api.get<CrisisRegulatoryNotification[]>(`/api/v1/bcm/crisis-events/${eventId}/regulatory-notifications`);
}

export async function logRegulatoryNotification(eventId: number,
                                                body: Partial<CrisisRegulatoryNotification>): Promise<CrisisRegulatoryNotification> {
  return api.post<CrisisRegulatoryNotification>(
    `/api/v1/bcm/crisis-events/${eventId}/regulatory-notifications`, body);
}

export async function ackRegulatoryNotification(eventId: number, notifId: number,
                                                reference: string): Promise<CrisisRegulatoryNotification> {
  return api.post<CrisisRegulatoryNotification>(
    `/api/v1/bcm/crisis-events/${eventId}/regulatory-notifications/${notifId}/acknowledge`,
    { reference });
}
