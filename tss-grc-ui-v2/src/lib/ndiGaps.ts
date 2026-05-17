// NDI Gaps + Corrective Actions API client (slice 8 · V73).
//
// Gap rollups (progress %, capa_count, capa_completed_count) are
// recomputed server-side on every CAPA write so the parent gap stays
// consistent with its children.

import { api } from './api';

export type GapSourceType = 'COMPLIANCE' | 'MATURITY' | 'KPI' | 'AUDIT' | 'MANUAL';
export type GapSeverity   = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type GapStatus     = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'ACCEPTED_RISK' | 'CLOSED';

export type CapaActionType = 'CONTROL' | 'POLICY' | 'TECH' | 'TRAINING' | 'PROCESS' | 'GOVERNANCE';
export type CapaStatus     = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'DEFERRED';

export interface NdiGap {
  id?: number;
  organisationId?: number;

  cycleId: number;
  gapCode?: string;
  sourceType: GapSourceType;
  sourceId?: number;
  domain?: string;

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  rootCause?: string;
  impactSummary?: string;

  severity: GapSeverity;
  likelihood?: 'RARE' | 'UNLIKELY' | 'POSSIBLE' | 'LIKELY' | 'ALMOST_CERTAIN';
  riskScore?: number;

  ownerName?: string;
  ownerEmail?: string;

  openDate: string;
  targetDate?: string;
  actualCloseDate?: string;

  rollupProgress: number;
  capaCount: number;
  capaCompletedCount: number;

  status: GapStatus;
  riskId?: number;
  notes?: string;
}

export interface NdiCorrectiveAction {
  id?: number;
  organisationId?: number;
  gapId: number;
  actionCode?: string;

  actionEn: string;
  actionAr?: string;
  actionType: CapaActionType;

  ownerName?: string;
  ownerEmail?: string;

  targetDate?: string;
  completionDate?: string;

  progressPct: number;
  status: CapaStatus;

  evidenceId?: number;
  blockerNote?: string;
  notes?: string;
}

export interface GapAggregate {
  total: number;
  bySeverity: Record<string, number>;
  byStatus: Record<string, number>;
  openCount: number;
  inProgressCount: number;
  resolvedOrClosedCount: number;
  overdueCount: number;
  criticalOpenCount: number;
  avgRollupProgress: number;
}

export interface Page<T> {
  content: T[]; totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/gaps';

export function listGaps(cycleId: number, opts: { status?: GapStatus; severity?: GapSeverity; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  p.set('cycleId', String(cycleId));
  if (opts.status)   p.set('status', opts.status);
  if (opts.severity) p.set('severity', opts.severity);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<Page<NdiGap>>(`${BASE}?${p.toString()}`);
}

export function gapsAggregate(cycleId: number) {
  return api.get<GapAggregate>(`${BASE}/aggregate?cycleId=${cycleId}`);
}

export function getGap(id: number)                { return api.get<NdiGap>(`${BASE}/${id}`); }
export function createGap(body: Partial<NdiGap>)  { return api.post<NdiGap>(BASE, body); }
export function updateGap(id: number, body: Partial<NdiGap>) { return api.put<NdiGap>(`${BASE}/${id}`, body); }
export function deleteGap(id: number)             { return api.delete<void>(`${BASE}/${id}`); }

export function listCapas(gapId: number) {
  return api.get<NdiCorrectiveAction[]>(`${BASE}/${gapId}/capas`);
}
export function createCapa(gapId: number, body: Partial<NdiCorrectiveAction>) {
  return api.post<NdiCorrectiveAction>(`${BASE}/${gapId}/capas`, body);
}
export function updateCapa(id: number, body: Partial<NdiCorrectiveAction>) {
  return api.put<NdiCorrectiveAction>(`${BASE}/capas/${id}`, body);
}
export function deleteCapa(id: number) {
  return api.delete<void>(`${BASE}/capas/${id}`);
}

// ─── UI helpers ───────────────────────────────────────────────────────

export function severityTone(s: GapSeverity): 'danger' | 'warning' | 'info' | 'neutral' {
  switch (s) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
  }
}

export function severityLabel(s: GapSeverity, lang: 'en' | 'ar'): string {
  const en: Record<GapSeverity, string> = { CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };
  const ar: Record<GapSeverity, string> = { CRITICAL: 'حرج', HIGH: 'مرتفع', MEDIUM: 'متوسط', LOW: 'منخفض' };
  return (lang === 'ar' ? ar : en)[s];
}

export function gapStatusTone(s: GapStatus): 'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'OPEN':          return 'danger';
    case 'IN_PROGRESS':   return 'warning';
    case 'RESOLVED':      return 'success';
    case 'ACCEPTED_RISK': return 'info';
    case 'CLOSED':        return 'success';
  }
}

export function gapStatusLabel(s: GapStatus, lang: 'en' | 'ar'): string {
  const en: Record<GapStatus, string> = {
    OPEN: 'Open', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved',
    ACCEPTED_RISK: 'Accepted risk', CLOSED: 'Closed',
  };
  const ar: Record<GapStatus, string> = {
    OPEN: 'مفتوح', IN_PROGRESS: 'قيد المعالجة', RESOLVED: 'محلول',
    ACCEPTED_RISK: 'خطر مقبول', CLOSED: 'مغلق',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function capaStatusTone(s: CapaStatus): 'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'NOT_STARTED': return 'neutral';
    case 'IN_PROGRESS': return 'info';
    case 'BLOCKED':     return 'danger';
    case 'COMPLETED':   return 'success';
    case 'DEFERRED':    return 'warning';
  }
}

export function capaStatusLabel(s: CapaStatus, lang: 'en' | 'ar'): string {
  const en: Record<CapaStatus, string> = {
    NOT_STARTED: 'Not started', IN_PROGRESS: 'In progress', BLOCKED: 'Blocked',
    COMPLETED: 'Completed', DEFERRED: 'Deferred',
  };
  const ar: Record<CapaStatus, string> = {
    NOT_STARTED: 'لم يبدأ', IN_PROGRESS: 'قيد التنفيذ', BLOCKED: 'متوقف',
    COMPLETED: 'مكتمل', DEFERRED: 'مُؤجَّل',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function actionTypeLabel(t: CapaActionType, lang: 'en' | 'ar'): string {
  const en: Record<CapaActionType, string> = {
    CONTROL: 'Control', POLICY: 'Policy', TECH: 'Tech', TRAINING: 'Training',
    PROCESS: 'Process', GOVERNANCE: 'Governance',
  };
  const ar: Record<CapaActionType, string> = {
    CONTROL: 'ضابط', POLICY: 'سياسة', TECH: 'تقني', TRAINING: 'تدريب',
    PROCESS: 'عملية', GOVERNANCE: 'حوكمة',
  };
  return (lang === 'ar' ? ar : en)[t];
}
