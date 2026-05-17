// SCM-specific resilience tests API client (gap #5).

import { api } from './api';

export type SrtStatus  = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'APPROVED' | 'CANCELLED';
export type SrtType    = 'TABLETOP' | 'FAILOVER_DRILL' | 'EXIT_REHEARSAL' | 'SLA_VALIDATION' | 'JOINT_CRISIS_SIM';
export type SrtScope   = 'DESKTOP' | 'PARTIAL_INVOCATION' | 'FULL_INVOCATION';
export type SrtOutcome = 'PASS' | 'PASS_WITH_OBSERVATIONS' | 'FAIL' | 'NOT_RUN';

export interface SupplierResilienceTest {
  id: number;
  testCode?: string;
  vendorId?: number;
  vendorName: string;
  titleEn: string; titleAr?: string;
  testType: SrtType;
  scenarioScope: SrtScope;
  scenarioDescriptionEn: string;
  scenarioDescriptionAr?: string;
  successCriteriaEn?: string;
  participants?: string;
  linkedAlternateId?: number;
  linkedExitPlanId?: number;
  linkedBcmPlanId?: number;
  scheduledFor?: string;
  executedAt?: string;
  duration?: number;
  outcome?: SrtOutcome;
  rtoTargetMinutes?: number;
  rtoActualMinutes?: number;
  observationsEn?: string;
  observationsAr?: string;
  actionItemsEn?: string;
  capaRefs?: string;
  evidenceRefs?: string;
  nextTestDue?: string;
  facilitator?: string;
  approverName?: string;
  approvedAt?: string;
  status: SrtStatus;
  cancellationReason?: string;
  createdAt: string;
  updatedAt: string;
}

const BASE = '/api/scm/resilience/tests';

export function listSrt(status?: SrtStatus, page = 0, size = 200):
    Promise<{ content: SupplierResilienceTest[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}
export function startSrt(id: number) { return api.post<SupplierResilienceTest>(`${BASE}/${id}/start`); }
export function completeSrt(id: number, body: Partial<{ outcome: SrtOutcome; rtoActualMinutes: number; observationsEn: string; actionItemsEn: string; evidenceRefs: string; }>) {
  return api.post<SupplierResilienceTest>(`${BASE}/${id}/complete`, body);
}
export function approveSrt(id: number, approverName: string) {
  return api.post<SupplierResilienceTest>(`${BASE}/${id}/approve`, { approverName });
}
export function cancelSrt(id: number, reason: string) {
  return api.post<SupplierResilienceTest>(`${BASE}/${id}/cancel`, { reason });
}

export const SRT_STATUS_TONE: Record<SrtStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  SCHEDULED: 'warning', IN_PROGRESS: 'info', COMPLETED: 'primary', APPROVED: 'success', CANCELLED: 'danger',
};
export const SRT_OUTCOME_TONE: Record<SrtOutcome, 'primary'|'success'|'danger'|'warning'|'info'> = {
  PASS: 'success', PASS_WITH_OBSERVATIONS: 'warning', FAIL: 'danger', NOT_RUN: 'info',
};
