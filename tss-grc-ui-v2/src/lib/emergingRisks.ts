// Emerging risk API client — uses shared api.ts.

import { api } from './api';

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

export function listEmergingRisks(status?: EmRiskStatus, page = 0, size = 200):
    Promise<{ content: EmergingRisk[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}

export function createEmergingRisk(body: EmCreateRequest): Promise<EmergingRisk> {
  return api.post(BASE, body);
}

export function reviewEmergingRisk(id: number, notes: string, nextReviewDue?: string): Promise<EmergingRisk> {
  return api.post(`${BASE}/${id}/review`, { notes, nextReviewDue });
}

export function escalateEmergingRisk(id: number, linkedRiskCode: string): Promise<EmergingRisk> {
  return api.post(`${BASE}/${id}/escalate`, { linkedRiskCode });
}

export function matureEmergingRisk(id: number): Promise<EmergingRisk> {
  return api.post(`${BASE}/${id}/mature`);
}

export function dismissEmergingRisk(id: number, reason: string): Promise<EmergingRisk> {
  return api.post(`${BASE}/${id}/dismiss`, { reason });
}

export const EM_STATUS_TONE: Record<EmRiskStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  HORIZON: 'info', MONITORING: 'primary', ESCALATED: 'warning', MATURED: 'success', DISMISSED: 'danger',
};

export const EM_SIGNAL_TONE: Record<EmRiskSignal, 'success'|'danger'|'warning'|'info'> = {
  WEAK: 'info', MODERATE: 'warning', STRONG: 'warning', CONFIRMED: 'danger',
};
