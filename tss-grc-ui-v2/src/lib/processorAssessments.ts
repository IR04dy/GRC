// Processor Assessment API client (gap #3 — PDPL Art. 28 due diligence).

import { api } from './api';

export type ProcStatus  = 'IN_PROGRESS' | 'COMPLETED' | 'APPROVED' | 'REJECTED';
export type ProcType    = 'ONBOARDING' | 'ANNUAL' | 'RENEWAL' | 'AD_HOC' | 'INCIDENT_TRIGGERED';
export type ProcOutcome = 'PASS' | 'PASS_WITH_CONDITIONS' | 'FAIL' | 'NOT_APPLICABLE';

export interface ProcessorAssessment {
  id: number;
  assessmentCode?: string;
  processorVendorId?: number;
  processorName: string;
  processorCountry?: string;
  processingScopeEn: string;
  processingScopeAr?: string;
  assessmentType: ProcType;
  c1WrittenInstructions?: ProcOutcome;
  c2Confidentiality?: ProcOutcome;
  c3SecurityMeasures?: ProcOutcome;
  c4SubProcessorAuth?: ProcOutcome;
  c5BreachNotification?: ProcOutcome;
  c6DsrAssistance?: ProcOutcome;
  c7DeletionOrReturn?: ProcOutcome;
  c8AuditRights?: ProcOutcome;
  evidenceRefs?: string;
  findingsEn?: string;
  findingsAr?: string;
  recommendationsEn?: string;
  recommendationsAr?: string;
  overallOutcome?: ProcOutcome;
  riskScore?: number;
  nextAssessmentDue?: string;
  assessedAt?: string;
  assessorName?: string;
  approvedAt?: string;
  approverName?: string;
  status: ProcStatus;
  createdAt: string;
  updatedAt: string;
}

const BASE = '/api/privacy/processor-assessments';

export function listProcessorAssessments(status?: ProcStatus, page = 0, size = 200):
    Promise<{ content: ProcessorAssessment[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}
export function approveProcessorAssessment(id: number, approverName: string) {
  return api.post<ProcessorAssessment>(`${BASE}/${id}/approve`, { approverName });
}
export function rejectProcessorAssessment(id: number) {
  return api.post<ProcessorAssessment>(`${BASE}/${id}/reject`);
}

export const PROC_STATUS_TONE: Record<ProcStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  IN_PROGRESS: 'warning', COMPLETED: 'info', APPROVED: 'success', REJECTED: 'danger',
};
export const PROC_OUTCOME_TONE: Record<ProcOutcome, 'primary'|'success'|'danger'|'warning'|'info'> = {
  PASS: 'success', PASS_WITH_CONDITIONS: 'warning', FAIL: 'danger', NOT_APPLICABLE: 'info',
};

export const ART28_CONTROLS = [
  { key: 'c1WrittenInstructions', labelKey: 'procC1' },
  { key: 'c2Confidentiality',     labelKey: 'procC2' },
  { key: 'c3SecurityMeasures',    labelKey: 'procC3' },
  { key: 'c4SubProcessorAuth',    labelKey: 'procC4' },
  { key: 'c5BreachNotification',  labelKey: 'procC5' },
  { key: 'c6DsrAssistance',       labelKey: 'procC6' },
  { key: 'c7DeletionOrReturn',    labelKey: 'procC7' },
  { key: 'c8AuditRights',         labelKey: 'procC8' },
] as const;
