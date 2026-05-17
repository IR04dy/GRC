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

import { api } from './api';

const BASE = '/api/incident/rca';

export function listRcas(page = 0, size = 50):
    Promise<{ content: RootCauseAnalysis[]; totalElements: number }> {
  return api.get(`${BASE}?page=${page}&size=${size}`);
}

export function rcasForEntity(entityType: string, entityId: number): Promise<RootCauseAnalysis[]> {
  return api.get(`${BASE}/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`);
}

export function getRca(id: number): Promise<RootCauseAnalysis> {
  return api.get(`${BASE}/${id}`);
}

export function createRca(body: RcaCreateRequest): Promise<RootCauseAnalysis> {
  return api.post(BASE, body);
}

export function updateRca(id: number, body: RcaUpdateRequest): Promise<RootCauseAnalysis> {
  return api.put(`${BASE}/${id}`, body);
}

export function submitRca(id: number): Promise<RootCauseAnalysis> {
  return api.post(`${BASE}/${id}/submit`);
}

export function approveRca(id: number): Promise<RootCauseAnalysis> {
  return api.post(`${BASE}/${id}/approve`);
}

export function rejectRca(id: number, rejectionReason: string): Promise<RootCauseAnalysis> {
  return api.post(`${BASE}/${id}/reject`, { rejectionReason });
}

/**
 * Cross-module: turn the RCA's approved recommendations into actual
 * CAPA tasks. Server-side parses the numbered list, spawns one CAPA
 * per item with originType="rca", originId=<RCA external id>, and
 * appends the new CAPA ids to the RCA's capa_refs field.
 *
 * APPROVED only — call results in HTTP 409 IllegalTransition otherwise.
 * Returns the list of newly-created CAPA DTOs so the UI can offer a
 * "Jump to CAPA" link per result.
 */
export interface GeneratedCapa {
  id: string;        // CAPA external id (e.g. "CAPA-202612-0011")
  originType: string;
  originId: string;
  titleEn: string;
  status: string;
  owner: string;
  targetDate: string;
}
export function generateCapasFromRca(id: number, owner?: string): Promise<GeneratedCapa[]> {
  const qs = owner ? `?owner=${encodeURIComponent(owner)}` : '';
  return api.post(`${BASE}/${id}/generate-capas${qs}`);
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
