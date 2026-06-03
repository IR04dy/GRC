// BCM SIEM inbound-event bridge client — /api/v1/bcm/siem-events.
//
// SOC/SIEM (Splunk, Sentinel, QRadar, CrowdStrike …) alerts are posted into
// the BCM crisis bridge. The backend auto-triages on ingest: severity >= 3
// ESCALATES and spawns a CrisisEvent (DETECTED, triggerSource=CYBER); severity
// 1-2 lands IGNORED — auditable but no crisis.

import { api } from './api';

export interface SiemInboundEvent {
  id: number;
  organisationId?: number;
  externalId?: string;
  sourceSystem: string;
  sourceAlertId?: string | null;
  severity: number;            // 1-5
  title: string;
  summary?: string | null;
  rawPayloadJson?: string | null;
  receivedAt: string;
  processedAt?: string | null;
  processingStatus: string;    // RECEIVED | EVALUATED | IGNORED | ESCALATED
  crisisEventId?: number | null;
}

export interface SiemIngestPayload {
  sourceSystem?: string;
  sourceAlertId?: string;
  severity?: number;
  title?: string;
  summary?: string;
  rawPayload?: Record<string, unknown>;
}

const BASE = '/api/v1/bcm/siem-events';

export const bcmSiemApi = {
  list:   (status?: string) => api.get<SiemInboundEvent[]>(status ? `${BASE}?status=${encodeURIComponent(status)}` : BASE),
  ingest: (body: SiemIngestPayload) => api.post<SiemInboundEvent>(BASE, body),
};

// ─── Severity (1-5) ────────────────────────────────────────────────────────────

export function sevTone(sev: number): 'neutral' | 'info' | 'warning' | 'danger' {
  if (sev >= 5) return 'danger';
  if (sev >= 4) return 'danger';
  if (sev >= 3) return 'warning';
  if (sev >= 2) return 'info';
  return 'neutral';
}

export function sevLabel(sev: number, isAr: boolean): string {
  switch (sev) {
    case 5: return isAr ? 'حرِج' : 'Critical';
    case 4: return isAr ? 'عالٍ' : 'High';
    case 3: return isAr ? 'متوسط' : 'Medium';
    case 2: return isAr ? 'منخفض' : 'Low';
    default: return isAr ? 'معلومة' : 'Info';
  }
}

/** Severity >= 3 escalates to a crisis event on ingest. */
export const ESCALATION_THRESHOLD = 3;

// ─── Processing status ─────────────────────────────────────────────────────────

export const SIEM_STATUS_TONE: Record<string, 'neutral' | 'info' | 'warning' | 'danger' | 'success'> = {
  RECEIVED: 'neutral', EVALUATED: 'info', IGNORED: 'neutral', ESCALATED: 'danger',
};

export function siemStatusLabel(status: string, isAr: boolean): string {
  switch (status) {
    case 'RECEIVED':  return isAr ? 'وارد' : 'Received';
    case 'EVALUATED': return isAr ? 'مُقيَّم' : 'Evaluated';
    case 'IGNORED':   return isAr ? 'مُتجاهَل' : 'Ignored';
    case 'ESCALATED': return isAr ? 'مُصعَّد' : 'Escalated';
    default:          return status;
  }
}

export const SIEM_STATUS_FILTERS = ['ALL', 'ESCALATED', 'EVALUATED', 'IGNORED', 'RECEIVED'] as const;
