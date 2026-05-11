// Emerging risk API client. Backend: /api/risk/emerging
// State: HORIZON → MONITORING → (ESCALATED | MATURED | DISMISSED)

export type EmRiskStatus   = 'HORIZON' | 'MONITORING' | 'ESCALATED' | 'MATURED' | 'DISMISSED';
export type EmRiskCategory = 'REGULATORY' | 'GEOPOLITICAL' | 'TECHNOLOGY' | 'ENVIRONMENTAL' | 'ECONOMIC' | 'SOCIAL' | 'OTHER';
export type EmRiskHorizon  = 'NEAR_TERM_6M' | 'MID_TERM_12M' | 'LONG_TERM_24M';
export type EmRiskVelocity = 'SLOW' | 'MEDIUM' | 'FAST';
export type EmRiskSignal   = 'WEAK' | 'MODERATE' | 'STRONG' | 'CONFIRMED';
export type EmRiskSource   = 'HORIZON_SCAN' | 'INDUSTRY_REPORT' | 'NEWS' | 'REGULATOR' | 'INTERNAL_INTEL' | 'OTHER';

export interface EmergingRisk {
  id: number; riskCode: string; externalId?: string;
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  category: EmRiskCategory; source: EmRiskSource;
  sourceUrl?: string; sourceCitation?: string;
  horizon: EmRiskHorizon; velocity: EmRiskVelocity; signalStrength: EmRiskSignal;
  estimatedLikelihood?: number; estimatedImpact?: number;
  mitigationThoughtsEn?: string; mitigationThoughtsAr?: string;
  linkedRiskCode?: string;
  status: EmRiskStatus;
  firstObservedAt: string; lastReviewedAt?: string;
  lastReviewNotes?: string; nextReviewDue?: string;
  escalatedAt?: string; maturedAt?: string;
  dismissedAt?: string; dismissReason?: string;
  owner?: string; tags?: string;
  createdAt: string; updatedAt: string;
}

export interface EmCreateRequest {
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  category: EmRiskCategory; source?: EmRiskSource;
  sourceUrl?: string; sourceCitation?: string;
  horizon: EmRiskHorizon; velocity?: EmRiskVelocity; signalStrength?: EmRiskSignal;
  estimatedLikelihood?: number; estimatedImpact?: number;
  mitigationThoughtsEn?: string; mitigationThoughtsAr?: string;
  owner?: string; tags?: string;
}

const BASE = '/api/risk/emerging';

function authHeaders(extra?: Record<string,string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return { ...(extra ?? {}), ...(tok ? { Authorization: `Bearer ${tok}` } : {}) };
}

export async function listEmergingRisks(status?: EmRiskStatus, page = 0, size = 200):
    Promise<{ content: EmergingRisk[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${BASE}?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listEmergingRisks HTTP ${res.status}`);
  return res.json();
}

export async function createEmergingRisk(body: EmCreateRequest): Promise<EmergingRisk> {
  const res = await fetch(BASE, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createEmergingRisk HTTP ${res.status}`);
  return res.json();
}

export async function reviewEmergingRisk(id: number, notes: string, nextReviewDue?: string): Promise<EmergingRisk> {
  const res = await fetch(`${BASE}/${id}/review`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ notes, nextReviewDue }),
  });
  if (!res.ok) throw new Error(`reviewEmergingRisk HTTP ${res.status}`);
  return res.json();
}

export async function escalateEmergingRisk(id: number, linkedRiskCode: string): Promise<EmergingRisk> {
  const res = await fetch(`${BASE}/${id}/escalate`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ linkedRiskCode }),
  });
  if (!res.ok) throw new Error(`escalateEmergingRisk HTTP ${res.status}`);
  return res.json();
}

export async function matureEmergingRisk(id: number): Promise<EmergingRisk> {
  const res = await fetch(`${BASE}/${id}/mature`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`matureEmergingRisk HTTP ${res.status}`);
  return res.json();
}

export async function dismissEmergingRisk(id: number, reason: string): Promise<EmergingRisk> {
  const res = await fetch(`${BASE}/${id}/dismiss`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`dismissEmergingRisk HTTP ${res.status}`);
  return res.json();
}

export const EM_STATUS_TONE: Record<EmRiskStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  HORIZON:    'info',
  MONITORING: 'primary',
  ESCALATED:  'warning',
  MATURED:    'success',
  DISMISSED:  'danger',
};

export const EM_SIGNAL_TONE: Record<EmRiskSignal, 'success'|'danger'|'warning'|'info'> = {
  WEAK:      'info',
  MODERATE:  'warning',
  STRONG:    'warning',
  CONFIRMED: 'danger',
};
