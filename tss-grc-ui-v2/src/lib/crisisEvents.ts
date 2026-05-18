// Crisis Event client — independent audit Tier-1 fix.
//
// Talks to /api/v1/bcm/crisis-events/* and surfaces typed shapes that the
// Crisis Room list + detail pages render directly.

import { api } from './api';

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
  return (await api.get(`/api/v1/bcm/crisis-events?page=${page}&size=${size}`)).data;
}

export async function listActiveCrisisEvents(): Promise<CrisisEvent[]> {
  return (await api.get<CrisisEvent[]>('/api/v1/bcm/crisis-events/active')).data;
}

export async function crisisEventStats(): Promise<Record<string, number>> {
  return (await api.get<Record<string, number>>('/api/v1/bcm/crisis-events/stats')).data;
}

export async function getCrisisEvent(id: number): Promise<CrisisEvent> {
  return (await api.get<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}`)).data;
}

export async function createCrisisEvent(body: Partial<CrisisEvent>): Promise<CrisisEvent> {
  return (await api.post<CrisisEvent>('/api/v1/bcm/crisis-events', body)).data;
}

export async function patchCrisisEvent(id: number, body: Partial<CrisisEvent>): Promise<CrisisEvent> {
  return (await api.patch<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}`, body)).data;
}

export async function transitionCrisisEvent(id: number, to: CrisisStatus): Promise<CrisisEvent> {
  return (await api.post<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}/transition`, { to })).data;
}

export async function activateBcp(id: number, bcpId: number): Promise<CrisisEvent> {
  return (await api.post<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}/activate-bcp`, { bcpId })).data;
}

export async function activateDrp(id: number, drpId: number): Promise<CrisisEvent> {
  return (await api.post<CrisisEvent>(`/api/v1/bcm/crisis-events/${id}/activate-drp`, { drpId })).data;
}

// ─── Timeline sub-resources ────────────────────────────────────────

export async function listSitReps(eventId: number): Promise<CrisisSituationReport[]> {
  return (await api.get<CrisisSituationReport[]>(`/api/v1/bcm/crisis-events/${eventId}/sitreps`)).data;
}

export async function addSitRep(eventId: number, body: Partial<CrisisSituationReport>): Promise<CrisisSituationReport> {
  return (await api.post<CrisisSituationReport>(`/api/v1/bcm/crisis-events/${eventId}/sitreps`, body)).data;
}

export async function listDecisions(eventId: number): Promise<CrisisDecision[]> {
  return (await api.get<CrisisDecision[]>(`/api/v1/bcm/crisis-events/${eventId}/decisions`)).data;
}

export async function addDecision(eventId: number, body: Partial<CrisisDecision>): Promise<CrisisDecision> {
  return (await api.post<CrisisDecision>(`/api/v1/bcm/crisis-events/${eventId}/decisions`, body)).data;
}

export async function listRegulatoryNotifications(eventId: number): Promise<CrisisRegulatoryNotification[]> {
  return (await api.get<CrisisRegulatoryNotification[]>(`/api/v1/bcm/crisis-events/${eventId}/regulatory-notifications`)).data;
}

export async function logRegulatoryNotification(eventId: number,
                                                body: Partial<CrisisRegulatoryNotification>): Promise<CrisisRegulatoryNotification> {
  return (await api.post<CrisisRegulatoryNotification>(
    `/api/v1/bcm/crisis-events/${eventId}/regulatory-notifications`, body)).data;
}

export async function ackRegulatoryNotification(eventId: number, notifId: number,
                                                reference: string): Promise<CrisisRegulatoryNotification> {
  return (await api.post<CrisisRegulatoryNotification>(
    `/api/v1/bcm/crisis-events/${eventId}/regulatory-notifications/${notifId}/acknowledge`,
    { reference })).data;
}
