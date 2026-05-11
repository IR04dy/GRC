// Root Cause Analysis API client.
//
// Backend: /api/incident/rca — see DigitalSignatureController layout.
// State machine on the server (RootCauseAnalysisService.java):
//   DRAFT     → IN_REVIEW (submit)
//             → APPROVED  (approve)
//             → REJECTED  (reject)
// Terminal states refuse re-transition (HTTP 409 + IllegalTransitionException).

export type RcaStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED';
export type RcaMethodology = 'FIVE_WHYS' | 'FISHBONE' | 'PARETO' | 'OTHER';

export interface FiveWhysEntry { question: string; answer: string; }
export interface ContributingFactor { category?: string; factor: string; frequency?: number; }

export interface RootCauseAnalysis {
  id: number;
  relatedEntityType: string;
  relatedEntityId: number;
  relatedEntityLabel?: string;
  methodology: RcaMethodology;
  problemStatementEn: string;
  problemStatementAr?: string;
  fiveWhysJson?: string;
  contributingFactorsJson?: string;
  rootCauseEn?: string;
  rootCauseAr?: string;
  recommendationsEn?: string;
  recommendationsAr?: string;
  facilitator?: string;
  participants?: string;
  conductedAt?: string;
  capaRefs?: string;
  status: RcaStatus;
  submittedAt?: string;
  approvedAt?: string;
  approverName?: string;
  rejectionReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RcaCreateRequest {
  relatedEntityType: string;
  relatedEntityId: number;
  relatedEntityLabel?: string;
  methodology?: RcaMethodology;
  problemStatementEn: string;
  problemStatementAr?: string;
  fiveWhysJson?: string;
  contributingFactorsJson?: string;
  rootCauseEn?: string;
  rootCauseAr?: string;
  recommendationsEn?: string;
  recommendationsAr?: string;
  facilitator?: string;
  participants?: string;
  conductedAt?: string;
  capaRefs?: string;
}

export interface RcaUpdateRequest {
  methodology?: RcaMethodology;
  problemStatementEn?: string;
  problemStatementAr?: string;
  fiveWhysJson?: string;
  contributingFactorsJson?: string;
  rootCauseEn?: string;
  rootCauseAr?: string;
  recommendationsEn?: string;
  recommendationsAr?: string;
  facilitator?: string;
  participants?: string;
  conductedAt?: string;
  capaRefs?: string;
}

const BASE = '/api/incident/rca';

function authHeaders(extra?: Record<string, string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return {
    ...(extra ?? {}),
    ...(tok ? { Authorization: `Bearer ${tok}` } : {}),
  };
}

export async function listRcas(page = 0, size = 50): Promise<{ content: RootCauseAnalysis[]; totalElements: number }> {
  const res = await fetch(`${BASE}?page=${page}&size=${size}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listRcas HTTP ${res.status}`);
  return res.json();
}

export async function rcasForEntity(entityType: string, entityId: number): Promise<RootCauseAnalysis[]> {
  const res = await fetch(`${BASE}/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`rcasForEntity HTTP ${res.status}`);
  return res.json();
}

export async function getRca(id: number): Promise<RootCauseAnalysis> {
  const res = await fetch(`${BASE}/${id}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`getRca HTTP ${res.status}`);
  return res.json();
}

export async function createRca(body: RcaCreateRequest): Promise<RootCauseAnalysis> {
  const res = await fetch(BASE, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createRca HTTP ${res.status}`);
  return res.json();
}

export async function updateRca(id: number, body: RcaUpdateRequest): Promise<RootCauseAnalysis> {
  const res = await fetch(`${BASE}/${id}`, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`updateRca HTTP ${res.status}`);
  return res.json();
}

export async function submitRca(id: number): Promise<RootCauseAnalysis> {
  const res = await fetch(`${BASE}/${id}/submit`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`submitRca HTTP ${res.status}`);
  return res.json();
}

export async function approveRca(id: number): Promise<RootCauseAnalysis> {
  const res = await fetch(`${BASE}/${id}/approve`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`approveRca HTTP ${res.status}`);
  return res.json();
}

export async function rejectRca(id: number, rejectionReason: string): Promise<RootCauseAnalysis> {
  const res = await fetch(`${BASE}/${id}/reject`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ rejectionReason }),
  });
  if (!res.ok) throw new Error(`rejectRca HTTP ${res.status}`);
  return res.json();
}

/** Safely parse 5-Whys JSON blob, falling back to []. */
export function parseFiveWhys(json?: string): FiveWhysEntry[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (!Array.isArray(arr)) return [];
    return arr.filter((e): e is FiveWhysEntry =>
      e && typeof e.question === 'string' && typeof e.answer === 'string');
  } catch { return []; }
}

/** Safely parse contributing-factors JSON blob, falling back to []. */
export function parseContributingFactors(json?: string): ContributingFactor[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (!Array.isArray(arr)) return [];
    return arr.filter((e): e is ContributingFactor => e && typeof e.factor === 'string');
  } catch { return []; }
}

export const RCA_STATUS_TONE: Record<RcaStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  DRAFT:     'warning',
  IN_REVIEW: 'info',
  APPROVED:  'success',
  REJECTED:  'danger',
};
