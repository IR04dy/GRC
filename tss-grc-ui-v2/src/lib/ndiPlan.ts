// NDI Improvement Plan API client (slice 9 · V74).
//
// Aggregates corrective actions from slice 8 into a time-bound roadmap
// with quarterly tracks, priorities and budgets. Plan totals are
// recomputed server-side on every item write so the parent stays
// consistent with its CAPAs.

import { api } from './api';

export type NdiPlanStatus =
  | 'DRAFT' | 'APPROVED' | 'IN_FLIGHT' | 'COMPLETED' | 'SUSPENDED' | 'ARCHIVED';

export type NdiPlanItemPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface NdiImprovementPlan {
  id?: number;
  organisationId?: number;
  cycleId: number;
  planCode?: string;

  nameEn: string;
  nameAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  periodStart: string;
  periodEnd: string;

  ownerName?: string;
  ownerEmail?: string;
  approverName?: string;
  approvedAt?: string;

  status: NdiPlanStatus;

  totalItems: number;
  completedItems: number;
  progressPct: number;

  budgetTotal?: number;
  budgetCurrency: string;

  notes?: string;
}

export interface NdiImprovementPlanItem {
  id?: number;
  organisationId?: number;
  planId: number;
  capaId: number;

  sequenceOrder: number;
  priority: NdiPlanItemPriority;
  targetQuarter?: string;
  allocatedBudget?: number;
  dependencies?: string;
  notes?: string;
}

/** Server returns this enriched view from /:planId/items — plan item joined to its CAPA snapshot. */
export interface EnrichedPlanItem {
  id: number;
  planId: number;
  capaId: number;
  sequenceOrder: number;
  priority: NdiPlanItemPriority;
  targetQuarter?: string;
  allocatedBudget?: number;
  dependencies?: string;
  notes?: string;

  actionCode?: string;
  actionEn?: string;
  actionAr?: string;
  actionType?: string;
  capaStatus?: 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'DEFERRED';
  capaProgressPct?: number;
  capaTargetDate?: string;
}

export interface PlanRollup {
  planId: number;
  totalItems: number;
  completedItems: number;
  progressPct: number;
  byQuarter: Record<string, number>;
  byPriority: Record<string, number>;
  byCapaStatus: Record<string, number>;
}

const BASE = '/api/ndi/plans';

export function listPlans(cycleId: number) {
  return api.get<NdiImprovementPlan[]>(`${BASE}?cycleId=${cycleId}`);
}
export function getPlan(id: number)            { return api.get<NdiImprovementPlan>(`${BASE}/${id}`); }
export function createPlan(body: Partial<NdiImprovementPlan>) { return api.post<NdiImprovementPlan>(BASE, body); }
export function updatePlan(id: number, body: Partial<NdiImprovementPlan>) { return api.put<NdiImprovementPlan>(`${BASE}/${id}`, body); }
// F2-236 — no approverName: the server takes it from the principal. The page passed 'admin'.
export function approvePlan(id: number) { return api.post<NdiImprovementPlan>(`${BASE}/${id}/approve`); }
export function transitionPlan(id: number, to: NdiPlanStatus)  { return api.post<NdiImprovementPlan>(`${BASE}/${id}/transition`, { to }); }
export function deletePlan(id: number)         { return api.delete<void>(`${BASE}/${id}`); }
export function recomputeAllPlans()            { return api.post<void>(`${BASE}/recompute-all`); }

export function listItems(planId: number) {
  return api.get<EnrichedPlanItem[]>(`${BASE}/${planId}/items`);
}
export function addItem(planId: number, body: Partial<NdiImprovementPlanItem>) {
  return api.post<NdiImprovementPlanItem>(`${BASE}/${planId}/items`, body);
}
export function updateItem(id: number, body: Partial<NdiImprovementPlanItem>) {
  return api.put<NdiImprovementPlanItem>(`${BASE}/items/${id}`, body);
}
export function deleteItem(id: number) {
  return api.delete<void>(`${BASE}/items/${id}`);
}
export function reorderItems(planId: number, orderedItemIds: number[]) {
  return api.post<NdiImprovementPlanItem[]>(`${BASE}/${planId}/reorder`, { orderedItemIds });
}
export function rollupPlan(planId: number) {
  return api.get<PlanRollup>(`${BASE}/${planId}/rollup`);
}

// ─── UI helpers ───────────────────────────────────────────────────────

export function planStatusTone(s: NdiPlanStatus):
    'neutral' | 'info' | 'primary' | 'warning' | 'success' {
  switch (s) {
    case 'DRAFT':     return 'neutral';
    case 'APPROVED':  return 'info';
    case 'IN_FLIGHT': return 'primary';
    case 'COMPLETED': return 'success';
    case 'SUSPENDED': return 'warning';
    case 'ARCHIVED':  return 'neutral';
  }
}

export function planStatusLabel(s: NdiPlanStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiPlanStatus, string> = {
    DRAFT: 'Draft', APPROVED: 'Approved', IN_FLIGHT: 'In flight',
    COMPLETED: 'Completed', SUSPENDED: 'Suspended', ARCHIVED: 'Archived',
  };
  const ar: Record<NdiPlanStatus, string> = {
    DRAFT: 'مسودّة', APPROVED: 'مُعتمَدة', IN_FLIGHT: 'قيد التنفيذ',
    COMPLETED: 'مكتملة', SUSPENDED: 'موقَّفة', ARCHIVED: 'مؤرشَفة',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function priorityTone(p: NdiPlanItemPriority): 'danger' | 'warning' | 'info' | 'neutral' {
  switch (p) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
  }
}

export function priorityLabelPlan(p: NdiPlanItemPriority, lang: 'en' | 'ar'): string {
  const en: Record<NdiPlanItemPriority, string> = {
    CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low',
  };
  const ar: Record<NdiPlanItemPriority, string> = {
    CRITICAL: 'حرج', HIGH: 'مرتفع', MEDIUM: 'متوسط', LOW: 'منخفض',
  };
  return (lang === 'ar' ? ar : en)[p];
}

/** Next state(s) reachable from current plan status. */
export function nextPlanStatuses(s: NdiPlanStatus): NdiPlanStatus[] {
  switch (s) {
    case 'DRAFT':     return ['APPROVED'];
    case 'APPROVED':  return ['IN_FLIGHT'];
    case 'IN_FLIGHT': return ['COMPLETED', 'SUSPENDED'];
    case 'SUSPENDED': return ['IN_FLIGHT', 'ARCHIVED'];
    case 'COMPLETED': return ['ARCHIVED'];
    case 'ARCHIVED':  return [];
  }
}
