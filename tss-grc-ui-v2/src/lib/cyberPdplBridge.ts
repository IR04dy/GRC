// Cyber → PDPL bridge log API client. Backend: /api/privacy/cyber-bridge

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

function authHeaders(extra?: Record<string,string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return { ...(extra ?? {}), ...(tok ? { Authorization: `Bearer ${tok}` } : {}) };
}

export async function listBridgeLog(outcome?: BridgeOutcome, page = 0, size = 200):
    Promise<{ content: BridgeLogEntry[]; totalElements: number }> {
  const qs = outcome ? `&outcome=${outcome}` : '';
  const res = await fetch(`${BASE}/log?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listBridgeLog HTTP ${res.status}`);
  return res.json();
}

export async function triggerBridge(req: {
  cyberIncidentId: number;
  dataSubjectsAffectedEst?: number;
  dataCategoriesAffected?: string;
  piiAssessment?: string;
}): Promise<BridgeLogEntry> {
  const res = await fetch(`${BASE}/trigger`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`triggerBridge HTTP ${res.status}`);
  return res.json();
}

export const BRIDGE_OUTCOME_TONE: Record<BridgeOutcome, 'primary'|'success'|'danger'|'warning'|'info'> = {
  CREATED: 'success', LINKED: 'primary', SKIPPED: 'info', ERROR: 'danger',
};
export const BRIDGE_EVENT_TONE: Record<BridgeEvent, 'primary'|'success'|'danger'|'warning'|'info'> = {
  BRIDGE_FIRED: 'primary',  BRIDGE_SKIPPED: 'info',
  BREACH_CREATED: 'success', BREACH_LINKED: 'success',
  ERROR: 'danger',
};
