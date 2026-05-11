// DR plan API client. Backend: /api/bcm/dr-plans

export type DrStatus     = 'DRAFT' | 'APPROVED' | 'ACTIVATED' | 'ARCHIVED' | 'REJECTED';
export type DrSiteType   = 'HOT' | 'WARM' | 'COLD' | 'ACTIVE_ACTIVE' | 'CLOUD_DR';
export type DrTestOutcome = 'NOT_TESTED' | 'PASSED' | 'GAP' | 'FAILED';

export interface DrPlan {
  id: number; planCode: string; externalId?: string;
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  linkedBcpCode?: string; criticalSystemName?: string; owner?: string;
  rtoHours: number; rpoHours: number; mtpdHours?: number;
  drSiteType: DrSiteType;
  drSiteLocation?: string; drSiteProvider?: string;
  recoveryStrategyEn?: string; recoveryStrategyAr?: string;
  dependenciesJson?: string; failoverStepsJson?: string; failbackStepsJson?: string;
  dataBackupStrategyEn?: string; dataBackupStrategyAr?: string;
  lastTestedAt?: string; lastTestedOutcome?: DrTestOutcome; lastTestNotesEn?: string;
  nextTestDue?: string;
  status: DrStatus;
  approvedAt?: string; approverName?: string;
  activatedAt?: string; activationReason?: string;
  archivedAt?: string; archiveReason?: string; rejectionReason?: string;
  createdAt: string; updatedAt: string;
}

export interface DrStep { seq: number; actionEn: string; actionAr?: string; owner?: string; minutesAllocated?: number; automated?: boolean; doneAt?: string|null; }
export interface DrDependency { system: string; type?: string; criticality?: string; notes?: string; }

export interface DrCreateRequest {
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  linkedBcpCode?: string; criticalSystemName?: string; owner?: string;
  rtoHours: number; rpoHours: number; mtpdHours?: number;
  drSiteType: DrSiteType;
  drSiteLocation?: string; drSiteProvider?: string;
  recoveryStrategyEn?: string; recoveryStrategyAr?: string;
  dependenciesJson?: string; failoverStepsJson?: string; failbackStepsJson?: string;
  dataBackupStrategyEn?: string; dataBackupStrategyAr?: string;
}

const BASE = '/api/bcm/dr-plans';

function authHeaders(extra?: Record<string,string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return { ...(extra ?? {}), ...(tok ? { Authorization: `Bearer ${tok}` } : {}) };
}

export async function listDrPlans(status?: DrStatus, page = 0, size = 200):
    Promise<{ content: DrPlan[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  const res = await fetch(`${BASE}?page=${page}&size=${size}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listDrPlans HTTP ${res.status}`);
  return res.json();
}

export async function createDrPlan(body: DrCreateRequest): Promise<DrPlan> {
  const res = await fetch(BASE, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createDrPlan HTTP ${res.status}`);
  return res.json();
}

export async function approveDrPlan(id: number): Promise<DrPlan> {
  const res = await fetch(`${BASE}/${id}/approve`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`approveDrPlan HTTP ${res.status}`);
  return res.json();
}

export async function rejectDrPlan(id: number, reason: string): Promise<DrPlan> {
  const res = await fetch(`${BASE}/${id}/reject`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`rejectDrPlan HTTP ${res.status}`);
  return res.json();
}

export async function activateDrPlan(id: number, reason: string): Promise<DrPlan> {
  const res = await fetch(`${BASE}/${id}/activate`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`activateDrPlan HTTP ${res.status}`);
  return res.json();
}

export async function archiveDrPlan(id: number, reason: string): Promise<DrPlan> {
  const res = await fetch(`${BASE}/${id}/archive`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error(`archiveDrPlan HTTP ${res.status}`);
  return res.json();
}

export async function recordDrTest(id: number, outcome: 'PASSED'|'GAP'|'FAILED', notesEn?: string, nextTestDue?: string): Promise<DrPlan> {
  const res = await fetch(`${BASE}/${id}/record-test`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ outcome, notesEn, nextTestDue }),
  });
  if (!res.ok) throw new Error(`recordDrTest HTTP ${res.status}`);
  return res.json();
}

export function parseDrSteps(json?: string): DrStep[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}
export function parseDrDependencies(json?: string): DrDependency[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a : []; } catch { return []; }
}

export const DR_STATUS_TONE: Record<DrStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', APPROVED: 'success', ACTIVATED: 'primary',
  ARCHIVED: 'info', REJECTED: 'danger',
};
export const DR_TEST_TONE: Record<DrTestOutcome, 'success'|'danger'|'warning'|'info'> = {
  NOT_TESTED: 'info', PASSED: 'success', GAP: 'warning', FAILED: 'danger',
};
export const DR_SITE_TONE: Record<DrSiteType, 'success'|'primary'|'warning'|'info'> = {
  ACTIVE_ACTIVE: 'success', HOT: 'primary', WARM: 'warning', COLD: 'info', CLOUD_DR: 'primary',
};
