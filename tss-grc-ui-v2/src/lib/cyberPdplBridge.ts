// Cyber → PDPL bridge log API client — uses shared api.ts.

import { api } from './api';

export type BridgeOutcome = 'CREATED' | 'LINKED' | 'SKIPPED' | 'ERROR';
export type BridgeEvent   = 'BRIDGE_FIRED' | 'BRIDGE_SKIPPED'
                          | 'BREACH_CREATED' | 'BREACH_LINKED' | 'ERROR';

export interface BridgeLogEntry {
  id: number;
  eventCode: BridgeEvent;
  cyberIncidentId?: number;
  cyberIncidentNumber?: string;
  dataBreachId?: number;
  dataBreachExternalId?: string;
  ruleName: string;
  triggerReason?: string;
  outcome: BridgeOutcome;
  outcomeDetail?: string;
  triggeredAt: string;
  triggeredBy?: number;
  triggeredByUsername?: string;
}

const BASE = '/api/privacy/cyber-bridge';

export function listBridgeLog(outcome?: BridgeOutcome, page = 0, size = 200):
    Promise<{ content: BridgeLogEntry[]; totalElements: number }> {
  const qs = outcome ? `&outcome=${outcome}` : '';
  return api.get(`${BASE}/log?page=${page}&size=${size}${qs}`);
}

export function triggerBridge(req: {
  cyberIncidentId: number;
  dataSubjectsAffectedEst?: number;
  dataCategoriesAffected?: string;
  piiAssessment?: string;
}): Promise<BridgeLogEntry> {
  return api.post(`${BASE}/trigger`, req);
}

export const BRIDGE_OUTCOME_TONE: Record<BridgeOutcome, 'primary'|'success'|'danger'|'warning'|'info'> = {
  CREATED: 'success', LINKED: 'primary', SKIPPED: 'info', ERROR: 'danger',
};
export const BRIDGE_EVENT_TONE: Record<BridgeEvent, 'primary'|'success'|'danger'|'warning'|'info'> = {
  BRIDGE_FIRED: 'primary',  BRIDGE_SKIPPED: 'info',
  BREACH_CREATED: 'success', BREACH_LINKED: 'success',
  ERROR: 'danger',
};
