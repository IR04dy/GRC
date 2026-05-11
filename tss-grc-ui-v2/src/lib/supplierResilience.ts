// Supplier resilience API client — alternate suppliers + exit plans.
//
// Backend: /api/scm/resilience/{alternates,exit-plans}.
// AlternateSupplier:  DRAFT → APPROVED → ARCHIVED.
// ExitPlan:           DRAFT → APPROVED → ACTIVATED → ARCHIVED
//                     DRAFT → REJECTED (terminal).

export type AltStatus       = 'DRAFT' | 'APPROVED' | 'ARCHIVED';
export type AltRole         = 'BACKUP_PRIMARY' | 'BACKUP_SECONDARY' | 'TERTIARY';
export type AltContractual  = 'NONE' | 'MOU_SIGNED' | 'FRAMEWORK_ACTIVE';
export type AltOutcome      = 'READY' | 'CONDITIONAL' | 'GAP' | 'NOT_VIABLE';

export type PlanStatus      = 'DRAFT' | 'APPROVED' | 'ACTIVATED' | 'ARCHIVED' | 'REJECTED';
export type PlanSeverity    = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type PlanTestOutcome = 'NOT_TESTED' | 'PASSED' | 'GAP_IDENTIFIED' | 'FAILED';

export interface AlternateSupplier {
  id: number;                          externalId?: string;
  primaryVendorId?: number;            primaryVendorName: string;
  alternateVendorName: string;
  alternateVendorCountry?: string;     alternateVendorRole: AltRole;
  serviceScopeEn: string;              serviceScopeAr?: string;
  estimatedOnboardingDays?: number;    estimatedCostSar?: number;
  lastAssessedAt?: string;             lastAssessedOutcome?: AltOutcome;
  contractualStatus: AltContractual;
  notesEn?: string;                    notesAr?: string;
  status: AltStatus;
  approvedAt?: string;                 approverName?: string;
  archivedAt?: string;                 archiveReason?: string;
  createdAt: string;                   updatedAt: string;
}

export interface AltCreateRequest {
  primaryVendorId?: number;            primaryVendorName: string;
  alternateVendorName: string;
  alternateVendorCountry?: string;     alternateVendorRole: AltRole;
  serviceScopeEn: string;              serviceScopeAr?: string;
  estimatedOnboardingDays?: number;    estimatedCostSar?: number;
  lastAssessedOutcome?: AltOutcome;
  contractualStatus?: AltContractual;
  notesEn?: string;                    notesAr?: string;
}

/** One step in the exit-plan playbook (parsed from steps_json). */
export interface ExitPlanStep {
  seq:               number;
  actionEn:          string;
  actionAr?:         string;
  owner:             string;
  days:              number;
  evidenceRequired?: boolean;
  doneAt?:           string | null;
  doneBy?:           number | null;
}

export interface ExitPlan {
  id: number;                          externalId?: string;
  planCode: string;
  vendorId?: number;                   vendorName: string;
  contractCode?: string;
  titleEn: string;                     titleAr?: string;
  triggerConditions?: string;          severityThreshold?: PlanSeverity;
  targetDisengagementDays: number;
  stepsJson: string;
  dataMigrationPlanEn?: string;        dataMigrationPlanAr?: string;
  knowledgeTransferPlanEn?: string;    knowledgeTransferPlanAr?: string;
  lastTestedAt?: string;               lastTestedOutcome?: PlanTestOutcome;
  nextTestDue?: string;
  status: PlanStatus;
  approvedAt?: string;                 approverName?: string;
  activatedAt?: string;                activationReason?: string;
  archivedAt?: string;                 archiveReason?: string;
  rejectionReason?: string;
  createdAt: string;                   updatedAt: string;
}

export interface PlanCreateRequest {
  vendorId?: number;                   vendorName: string;
  contractCode?: string;
  titleEn: string;                     titleAr?: string;
  triggerConditions?: string;          severityThreshold?: PlanSeverity;
  targetDisengagementDays?: number;
  stepsJson: string;
  dataMigrationPlanEn?: string;        dataMigrationPlanAr?: string;
  knowledgeTransferPlanEn?: string;    knowledgeTransferPlanAr?: string;
}

const ALT_BASE  = '/api/scm/resilience/alternates';
const PLAN_BASE = '/api/scm/resilience/exit-plans';

function authHeaders(extra?: Record<string, string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return { ...(extra ?? {}), ...(tok ? { Authorization: `Bearer ${tok}` } : {}) };
}

// ─── Alternate suppliers ───────────────────────────────────────────────
export async function listAlternates(status?: AltStatus, page = 0, size = 100):
    Promise<{ content: AlternateSupplier[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${ALT_BASE}?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listAlternates HTTP ${res.status}`);
  return res.json();
}

export async function alternatesForPrimary(vendorId: number): Promise<AlternateSupplier[]> {
  const res = await fetch(`${ALT_BASE}/by-primary/${vendorId}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`alternatesForPrimary HTTP ${res.status}`);
  return res.json();
}

export async function createAlternate(body: AltCreateRequest): Promise<AlternateSupplier> {
  const res = await fetch(ALT_BASE, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createAlternate HTTP ${res.status}`);
  return res.json();
}

export async function approveAlternate(id: number): Promise<AlternateSupplier> {
  const res = await fetch(`${ALT_BASE}/${id}/approve`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`approveAlternate HTTP ${res.status}`);
  return res.json();
}

export async function archiveAlternate(id: number, reason: string): Promise<AlternateSupplier> {
  const res = await fetch(`${ALT_BASE}/${id}/archive`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`archiveAlternate HTTP ${res.status}`);
  return res.json();
}

// ─── Exit plans ────────────────────────────────────────────────────────
export async function listExitPlans(status?: PlanStatus, page = 0, size = 100):
    Promise<{ content: ExitPlan[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${PLAN_BASE}?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listExitPlans HTTP ${res.status}`);
  return res.json();
}

export async function plansForVendor(vendorId: number): Promise<ExitPlan[]> {
  const res = await fetch(`${PLAN_BASE}/by-vendor/${vendorId}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`plansForVendor HTTP ${res.status}`);
  return res.json();
}

export async function createExitPlan(body: PlanCreateRequest): Promise<ExitPlan> {
  const res = await fetch(PLAN_BASE, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createExitPlan HTTP ${res.status}`);
  return res.json();
}

export async function approvePlan(id: number): Promise<ExitPlan> {
  const res = await fetch(`${PLAN_BASE}/${id}/approve`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`approvePlan HTTP ${res.status}`);
  return res.json();
}

export async function rejectPlan(id: number, reason: string): Promise<ExitPlan> {
  const res = await fetch(`${PLAN_BASE}/${id}/reject`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`rejectPlan HTTP ${res.status}`);
  return res.json();
}

export async function activatePlan(id: number, reason: string): Promise<ExitPlan> {
  const res = await fetch(`${PLAN_BASE}/${id}/activate`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`activatePlan HTTP ${res.status}`);
  return res.json();
}

export async function archivePlan(id: number, reason: string): Promise<ExitPlan> {
  const res = await fetch(`${PLAN_BASE}/${id}/archive`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`archivePlan HTTP ${res.status}`);
  return res.json();
}

export async function recordPlanTest(id: number, outcome: 'PASSED' | 'GAP_IDENTIFIED' | 'FAILED',
                                     nextTestDue?: string): Promise<ExitPlan> {
  const res = await fetch(`${PLAN_BASE}/${id}/record-test`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ outcome, nextTestDue }),
  });
  if (!res.ok) throw new Error(`recordPlanTest HTTP ${res.status}`);
  return res.json();
}

// ─── Helpers ───────────────────────────────────────────────────────────
export function parseExitPlanSteps(json?: string): ExitPlanStep[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    if (!Array.isArray(arr)) return [];
    return arr.filter((e): e is ExitPlanStep =>
      e && typeof e.seq === 'number' && typeof e.actionEn === 'string');
  } catch { return []; }
}

export const ALT_STATUS_TONE: Record<AltStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  DRAFT:    'warning',
  APPROVED: 'success',
  ARCHIVED: 'info',
};
export const ALT_OUTCOME_TONE: Record<AltOutcome, 'success' | 'warning' | 'danger' | 'info'> = {
  READY:       'success',
  CONDITIONAL: 'warning',
  GAP:         'warning',
  NOT_VIABLE:  'danger',
};

export const PLAN_STATUS_TONE: Record<PlanStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  DRAFT:     'warning',
  APPROVED:  'success',
  ACTIVATED: 'primary',
  REJECTED:  'danger',
  ARCHIVED:  'info',
};
export const PLAN_TEST_TONE: Record<PlanTestOutcome, 'success' | 'warning' | 'danger' | 'info'> = {
  NOT_TESTED:     'info',
  PASSED:         'success',
  GAP_IDENTIFIED: 'warning',
  FAILED:         'danger',
};

export function fmtSar(amount?: number): string {
  if (amount == null) return '—';
  return new Intl.NumberFormat('en-US',
    { style: 'currency', currency: 'SAR', maximumFractionDigits: 0 }).format(amount);
}
