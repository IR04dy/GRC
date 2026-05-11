// Supplier resilience API client — uses shared api.ts.

import { api } from './api';

export type AltStatus       = 'DRAFT' | 'APPROVED' | 'ARCHIVED';
export type AltRole         = 'BACKUP_PRIMARY' | 'BACKUP_SECONDARY' | 'TERTIARY';
export type AltContractual  = 'NONE' | 'MOU_SIGNED' | 'FRAMEWORK_ACTIVE';
export type AltOutcome      = 'READY' | 'CONDITIONAL' | 'GAP' | 'NOT_VIABLE';

export type PlanStatus      = 'DRAFT' | 'APPROVED' | 'ACTIVATED' | 'ARCHIVED' | 'REJECTED';
export type PlanSeverity    = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type PlanTestOutcome = 'NOT_TESTED' | 'PASSED' | 'GAP_IDENTIFIED' | 'FAILED';

export interface AlternateSupplier {
  id: number; externalId?: string;
  primaryVendorId?: number; primaryVendorName: string;
  alternateVendorName: string;
  alternateVendorCountry?: string; alternateVendorRole: AltRole;
  serviceScopeEn: string; serviceScopeAr?: string;
  estimatedOnboardingDays?: number; estimatedCostSar?: number;
  lastAssessedAt?: string; lastAssessedOutcome?: AltOutcome;
  contractualStatus: AltContractual;
  notesEn?: string; notesAr?: string;
  status: AltStatus;
  approvedAt?: string; approverName?: string;
  archivedAt?: string; archiveReason?: string;
  createdAt: string; updatedAt: string;
}

export interface AltCreateRequest {
  primaryVendorId?: number; primaryVendorName: string;
  alternateVendorName: string;
  alternateVendorCountry?: string; alternateVendorRole: AltRole;
  serviceScopeEn: string; serviceScopeAr?: string;
  estimatedOnboardingDays?: number; estimatedCostSar?: number;
  lastAssessedOutcome?: AltOutcome;
  contractualStatus?: AltContractual;
  notesEn?: string; notesAr?: string;
}

export interface ExitPlanStep {
  seq: number; actionEn: string; actionAr?: string;
  owner: string; days: number;
  evidenceRequired?: boolean; doneAt?: string | null; doneBy?: number | null;
}

export interface ExitPlan {
  id: number; externalId?: string;
  planCode: string;
  vendorId?: number; vendorName: string; contractCode?: string;
  titleEn: string; titleAr?: string;
  triggerConditions?: string; severityThreshold?: PlanSeverity;
  targetDisengagementDays: number;
  stepsJson: string;
  dataMigrationPlanEn?: string; dataMigrationPlanAr?: string;
  knowledgeTransferPlanEn?: string; knowledgeTransferPlanAr?: string;
  lastTestedAt?: string; lastTestedOutcome?: PlanTestOutcome;
  nextTestDue?: string;
  status: PlanStatus;
  approvedAt?: string; approverName?: string;
  activatedAt?: string; activationReason?: string;
  archivedAt?: string; archiveReason?: string;
  rejectionReason?: string;
  createdAt: string; updatedAt: string;
}

export interface PlanCreateRequest {
  vendorId?: number; vendorName: string; contractCode?: string;
  titleEn: string; titleAr?: string;
  triggerConditions?: string; severityThreshold?: PlanSeverity;
  targetDisengagementDays?: number;
  stepsJson: string;
  dataMigrationPlanEn?: string; dataMigrationPlanAr?: string;
  knowledgeTransferPlanEn?: string; knowledgeTransferPlanAr?: string;
}

const ALT_BASE  = '/api/scm/resilience/alternates';
const PLAN_BASE = '/api/scm/resilience/exit-plans';

export function listAlternates(status?: AltStatus, page = 0, size = 100):
    Promise<{ content: AlternateSupplier[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${ALT_BASE}?page=${page}&size=${size}${qs}`);
}

export function alternatesForPrimary(vendorId: number): Promise<AlternateSupplier[]> {
  return api.get(`${ALT_BASE}/by-primary/${vendorId}`);
}

export function createAlternate(body: AltCreateRequest): Promise<AlternateSupplier> {
  return api.post(ALT_BASE, body);
}

export function approveAlternate(id: number): Promise<AlternateSupplier> {
  return api.post(`${ALT_BASE}/${id}/approve`);
}

export function archiveAlternate(id: number, reason: string): Promise<AlternateSupplier> {
  return api.post(`${ALT_BASE}/${id}/archive`, { reason });
}

export function listExitPlans(status?: PlanStatus, page = 0, size = 100):
    Promise<{ content: ExitPlan[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${PLAN_BASE}?page=${page}&size=${size}${qs}`);
}

export function plansForVendor(vendorId: number): Promise<ExitPlan[]> {
  return api.get(`${PLAN_BASE}/by-vendor/${vendorId}`);
}

export function createExitPlan(body: PlanCreateRequest): Promise<ExitPlan> {
  return api.post(PLAN_BASE, body);
}

export function approvePlan(id: number): Promise<ExitPlan> {
  return api.post(`${PLAN_BASE}/${id}/approve`);
}

export function rejectPlan(id: number, reason: string): Promise<ExitPlan> {
  return api.post(`${PLAN_BASE}/${id}/reject`, { reason });
}

export function activatePlan(id: number, reason: string): Promise<ExitPlan> {
  return api.post(`${PLAN_BASE}/${id}/activate`, { reason });
}

export function archivePlan(id: number, reason: string): Promise<ExitPlan> {
  return api.post(`${PLAN_BASE}/${id}/archive`, { reason });
}

export function recordPlanTest(id: number, outcome: 'PASSED'|'GAP_IDENTIFIED'|'FAILED',
                               nextTestDue?: string): Promise<ExitPlan> {
  return api.post(`${PLAN_BASE}/${id}/record-test`, { outcome, nextTestDue });
}

export function parseExitPlanSteps(json?: string): ExitPlanStep[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (!Array.isArray(arr)) return [];
    return arr.filter((e): e is ExitPlanStep =>
      e && typeof e.seq === 'number' && typeof e.actionEn === 'string');
  } catch { return []; }
}

export const ALT_STATUS_TONE: Record<AltStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', APPROVED: 'success', ARCHIVED: 'info',
};
export const ALT_OUTCOME_TONE: Record<AltOutcome, 'success'|'warning'|'danger'|'info'> = {
  READY: 'success', CONDITIONAL: 'warning', GAP: 'warning', NOT_VIABLE: 'danger',
};

export const PLAN_STATUS_TONE: Record<PlanStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', APPROVED: 'success', ACTIVATED: 'primary',
  REJECTED: 'danger', ARCHIVED: 'info',
};
export const PLAN_TEST_TONE: Record<PlanTestOutcome, 'success'|'warning'|'danger'|'info'> = {
  NOT_TESTED: 'info', PASSED: 'success', GAP_IDENTIFIED: 'warning', FAILED: 'danger',
};

export function fmtSar(amount?: number): string {
  if (amount == null) return '—';
  return new Intl.NumberFormat('en-US',
    { style: 'currency', currency: 'SAR', maximumFractionDigits: 0 }).format(amount);
}
