// Third-party cyber risk API client (gap #4 — dedicated TPRM cyber assessment).

import { api } from './api';

export type TpcStatus      = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'RETIRED';
export type TpcInherent    = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TpcResidual    = 'LOW' | 'MODERATE' | 'ELEVATED' | 'HIGH' | 'CRITICAL';

export interface ThirdPartyCyberAssessment {
  id: number;
  assessmentCode?: string;
  vendorId?: number;
  vendorName: string;
  vendorCountry?: string;
  inherentCyberRisk: TpcInherent;
  scopeDescriptionEn: string;
  scopeDescriptionAr?: string;
  certifications?: string;
  certificationsExpiry?: string;
  externalPostureScore?: number;
  questionnaireScore?: number;
  questionnaireCompletedAt?: string;
  breachHistory24m: number;
  lastKnownBreachDate?: string;
  lastKnownBreachSummary?: string;
  criticalVulnsOpen?: number;
  patchSlaCompliancePct?: number;
  mfaEnforced: number;
  penTestWithin12m: number;
  residualRiskTier: TpcResidual;
  riskScore?: number;
  findingsEn?: string;  findingsAr?: string;
  treatmentPlanEn?: string;  treatmentPlanAr?: string;
  nextReviewDue?: string;
  assessedAt?: string;
  assessorName?: string;
  approvedAt?: string;
  approverName?: string;
  status: TpcStatus;
  createdAt: string; updatedAt: string;
}

const BASE = '/api/tprm/cyber-assessments';

export function listTpc(status?: TpcStatus, page = 0, size = 200):
    Promise<{ content: ThirdPartyCyberAssessment[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}
export function submitTpc(id: number)   { return api.post<ThirdPartyCyberAssessment>(`${BASE}/${id}/submit`); }
export function approveTpc(id: number, approverName: string) {
  return api.post<ThirdPartyCyberAssessment>(`${BASE}/${id}/approve`, { approverName });
}
export function retireTpc(id: number)   { return api.post<ThirdPartyCyberAssessment>(`${BASE}/${id}/retire`); }

export const TPC_STATUS_TONE: Record<TpcStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', IN_REVIEW: 'info', APPROVED: 'success', RETIRED: 'primary',
};
export const TPC_RESIDUAL_TONE: Record<TpcResidual, 'primary'|'success'|'danger'|'warning'|'info'> = {
  LOW: 'success', MODERATE: 'info', ELEVATED: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};
export const TPC_INHERENT_TONE: Record<TpcInherent, 'primary'|'success'|'danger'|'warning'|'info'> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};
