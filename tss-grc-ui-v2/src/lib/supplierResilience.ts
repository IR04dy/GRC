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
  // F2-246 — DERIVED, never the caller's claim: when `primaryVendorId` resolves, the
  // server writes the vendor register's own name here. NOT NULL in the column, so it
  // is always sent; the create request is where it may be omitted.
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
  // F2-246 — supply `primaryVendorId` to link this record to the vendor register (the
  // server resolves it in-tenant and derives the label), or `primaryVendorName` when
  // the supplier is not registered. One of the two must identify the vendor; the form
  // always sends the id. `alternateVendorName` stays required because the schema has
  // no `alternate_vendor_id` column at all — a substitute need not be registered.
  primaryVendorId?: number; primaryVendorName?: string;
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
  // F2-246 — DERIVED from `vendorId` when the row is linked, the caller's own text when
  // it is not. NOT NULL in the column, so a response always carries one.
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
  // F2-246 — `vendorId` links to the register and the server derives the label;
  // `vendorName` identifies an unregistered supplier. One of the two is required.
  vendorId?: number; vendorName?: string; contractCode?: string;
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

// Delegate to the central compact formatter so KPI tiles render
// "SAR 12.5M" instead of overflowing with "SAR 12,480,000". See
// lib/format.ts for the full set (compact / full / number / percent).
export { formatSarCompact as fmtSar, formatSarFull as fmtSarFull } from './format';
