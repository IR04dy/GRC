// Sustainability Corrective Actions API client (Sustainability · V58).
//
// Wraps /api/sustainability/corrective-actions/*. Powers the new
// "Corrective Actions" tab on /esg.
//
// Workflow rules surfaced for client-side mirroring (final enforcement
// is in the service layer):
//   • Rule A — verifier name must not equal owner name.
//   • Rule B — COMPLETED requires evidence_ref to be set.
//   • Rule C — CLOSED / CANCELLED are terminal — no further transitions.
//
// State machine:
//   OPEN → IN_PROGRESS → COMPLETED → VERIFIED → CLOSED
//   any  → CANCELLED   (terminal)

import { api } from './api';

export type CapaStatus =
  | 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'VERIFIED' | 'CLOSED' | 'CANCELLED';

export type CapaPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

// CT-5 — full union of the backend whitelist / ck_ca_source (11 types).
// EVIDENCE + VENDOR_ESG arrive via the S-C/S-O auto-opens; ESG_TARGET +
// ENV_PERMIT via the G4 horizon sweeps.
export type CapaSourceType =
  | 'CLIMATE_RISK' | 'GHG_RECORD' | 'ANNEX_GAP' | 'CMA_DISCLOSURE'
  | 'TCFD_ITEM'    | 'SGI_COMMITMENT' | 'MANUAL'
  | 'EVIDENCE'     | 'VENDOR_ESG' | 'ESG_TARGET' | 'ENV_PERMIT';

// CT-3 (V297) — CAR/PAR classification. One register, one lifecycle;
// auto-opened CAPAs are always CORRECTIVE.
export type CapaActionType = 'CORRECTIVE' | 'PREVENTIVE';

export interface CorrectiveAction {
  id?: number;
  organisationId?: number;
  actionCode?: string;

  sourceType: CapaSourceType;
  sourceRef?: string;
  actionType?: CapaActionType;   // CT-3 — server defaults CORRECTIVE

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  priority: CapaPriority;
  status: CapaStatus;

  ownerName?: string;
  ownerEmail?: string;
  assignedTo?: string;

  targetDate?: string;     // ISO yyyy-MM-dd
  startedAt?: string;
  completedAt?: string;
  verifiedBy?: string;
  verifiedAt?: string;
  closedAt?: string;

  rootCauseEn?: string;
  rootCauseAr?: string;
  remediationPlanEn?: string;
  remediationPlanAr?: string;
  evidenceRef?: string;

  costEstimateSar?: number;
  effortHours?: number;

  linkedCapaId?: number;
  linkedRiskId?: number;

  notes?: string;
}

export interface CapaAggregate {
  open: number;
  inProgress: number;
  completed: number;
  verified: number;
  overdue: number;
  dueSoon: number;
  byPriority: Record<CapaPriority, number>;
  bySource:   Record<string, number>;
  byType?:    Record<CapaActionType, number>;   // CT-3
  activeTotal: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/corrective-actions';

export function listCapas(opts: {
  status?: CapaStatus; sourceType?: CapaSourceType; priority?: CapaPriority;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  if (opts.status)     p.set('status',     opts.status);
  if (opts.sourceType) p.set('sourceType', opts.sourceType);
  if (opts.priority)   p.set('priority',   opts.priority);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<CorrectiveAction>>(`${BASE}?${p.toString()}`);
}

export function capaAggregate() {
  return api.get<CapaAggregate>(`${BASE}/aggregate`);
}

export function capasForSource(sourceType: CapaSourceType, sourceRef: string) {
  const p = new URLSearchParams();
  p.set('sourceType', sourceType);
  p.set('sourceRef', sourceRef);
  return api.get<CorrectiveAction[]>(`${BASE}/for-source?${p.toString()}`);
}

export function getCapa(id: number) {
  return api.get<CorrectiveAction>(`${BASE}/${id}`);
}

export function createCapa(body: Partial<CorrectiveAction>) {
  return api.post<CorrectiveAction>(BASE, body);
}

export function updateCapa(id: number, body: Partial<CorrectiveAction>) {
  return api.put<CorrectiveAction>(`${BASE}/${id}`, body);
}

export function startCapa(id: number)    { return api.post<CorrectiveAction>(`${BASE}/${id}/start`); }
export function completeCapa(id: number) { return api.post<CorrectiveAction>(`${BASE}/${id}/complete`); }
// CT-4 — no body: the server stamps the authenticated principal as verifier
// (SEC-ESG-002); a client-supplied name was never read.
export function verifyCapa(id: number) {
  return api.post<CorrectiveAction>(`${BASE}/${id}/verify`);
}
export function closeCapa(id: number)    { return api.post<CorrectiveAction>(`${BASE}/${id}/close`); }
export function cancelCapa(id: number)   { return api.post<CorrectiveAction>(`${BASE}/${id}/cancel`); }
export function deleteCapa(id: number)   { return api.delete<void>(`${BASE}/${id}`); }

// ── UI helpers ───────────────────────────────────────────────────────

/** Tone for a status badge. */
export function capaStatusTone(s: CapaStatus):
    'neutral' | 'info' | 'warning' | 'success' | 'primary' | 'danger' {
  switch (s) {
    case 'OPEN':        return 'warning';
    case 'IN_PROGRESS': return 'info';
    case 'COMPLETED':   return 'primary';
    case 'VERIFIED':    return 'success';
    case 'CLOSED':      return 'neutral';
    case 'CANCELLED':   return 'danger';
  }
}

export function capaPriorityTone(p: CapaPriority):
    'neutral' | 'info' | 'warning' | 'success' | 'primary' | 'danger' {
  switch (p) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
  }
}

/** Next available transitions from a given status (Rule C — terminal states). */
export function capaAllowedNext(s: CapaStatus): CapaStatus[] {
  switch (s) {
    case 'OPEN':        return ['IN_PROGRESS', 'CANCELLED'];
    case 'IN_PROGRESS': return ['COMPLETED', 'CANCELLED'];
    case 'COMPLETED':   return ['VERIFIED', 'CANCELLED'];
    case 'VERIFIED':    return ['CLOSED'];
    default:            return [];   // CLOSED, CANCELLED
  }
}

/** Rule-B client mirror — disables Complete until evidence is set. */
export function canComplete(a: CorrectiveAction): boolean {
  return a.status === 'IN_PROGRESS' && !!a.evidenceRef && a.evidenceRef.trim().length > 0;
}

/**
 * Rule-A client mirror (CT-4) — the verifier IS the authenticated principal;
 * they must differ from the owner. Compared against BOTH the owner display
 * name and the owner email, trim/case-insensitive — exactly the server's
 * sameActor() check — so the client verdict matches the 400 the server
 * would return.
 */
export function canVerify(a: CorrectiveAction, principalUsername: string): boolean {
  if (a.status !== 'COMPLETED') return false;
  if (!principalUsername || !principalUsername.trim()) return false;
  const same = (x?: string) =>
    !!x && x.trim().toLowerCase() === principalUsername.trim().toLowerCase();
  return !same(a.ownerName) && !same(a.ownerEmail);
}

/**
 * Days remaining until target_date. Negative = overdue. Returns null
 * when no target date is set.
 */
export function daysRemaining(a: CorrectiveAction): number | null {
  if (!a.targetDate) return null;
  const due = new Date(a.targetDate + 'T00:00:00');
  const now = new Date();
  const ms = due.getTime() - now.getTime();
  return Math.round(ms / 86_400_000);
}

/** Bilingual source-type labels. */
export function capaSourceLabel(s: CapaSourceType, lang: 'en' | 'ar'): string {
  const en: Record<CapaSourceType, string> = {
    CLIMATE_RISK:    'Climate risk',
    GHG_RECORD:      'GHG record',
    ANNEX_GAP:       'Annex gap',
    CMA_DISCLOSURE:  'CMA disclosure',
    TCFD_ITEM:       'TCFD item',
    SGI_COMMITMENT:  'SGI commitment',
    MANUAL:          'Manual',
    EVIDENCE:        'Evidence rejection',
    VENDOR_ESG:      'Vendor ESG',
    ESG_TARGET:      'ESG target',
    ENV_PERMIT:      'Environmental permit',
  };
  const ar: Record<CapaSourceType, string> = {
    CLIMATE_RISK:    'مخاطر مناخية',
    GHG_RECORD:      'سجل غازات دفيئة',
    ANNEX_GAP:       'ثغرة في الملحق',
    CMA_DISCLOSURE:  'إفصاح هيئة السوق المالية',
    TCFD_ITEM:       'بند TCFD',
    SGI_COMMITMENT:  'التزام المبادرة الخضراء',
    MANUAL:          'يدوي',
    EVIDENCE:        'رفض دليل',
    VENDOR_ESG:      'استدامة الموردين',
    ESG_TARGET:      'مستهدف استدامة',
    ENV_PERMIT:      'تصريح بيئي',
  };
  // Safe fallback (CT-5): a source added server-side before this map learns it
  // renders as its raw code — never a blank badge.
  return (lang === 'ar' ? ar : en)[s] ?? s;
}

/** Bilingual status labels for inline rendering. */
export function capaStatusLabel(s: CapaStatus, lang: 'en' | 'ar'): string {
  const en: Record<CapaStatus, string> = {
    OPEN:        'Open',
    IN_PROGRESS: 'In progress',
    COMPLETED:   'Completed',
    VERIFIED:    'Verified',
    CLOSED:      'Closed',
    CANCELLED:   'Cancelled',
  };
  const ar: Record<CapaStatus, string> = {
    OPEN:        'مفتوحة',
    IN_PROGRESS: 'قيد التنفيذ',
    COMPLETED:   'مكتملة',
    VERIFIED:    'مُتحقَّق منها',
    CLOSED:      'مغلقة',
    CANCELLED:   'ملغاة',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function capaPriorityLabel(p: CapaPriority, lang: 'en' | 'ar'): string {
  const en: Record<CapaPriority, string> = {
    CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low',
  };
  const ar: Record<CapaPriority, string> = {
    CRITICAL: 'حرجة', HIGH: 'عالية', MEDIUM: 'متوسطة', LOW: 'منخفضة',
  };
  return (lang === 'ar' ? ar : en)[p];
}

/** CT-3 — bilingual CAR/PAR labels (same fallback contract as capaSourceLabel). */
export function capaActionTypeLabel(t: CapaActionType, lang: 'en' | 'ar'): string {
  const en: Record<CapaActionType, string> = {
    CORRECTIVE: 'Corrective', PREVENTIVE: 'Preventive',
  };
  const ar: Record<CapaActionType, string> = {
    CORRECTIVE: 'إجراء تصحيحي', PREVENTIVE: 'إجراء وقائي',
  };
  return (lang === 'ar' ? ar : en)[t] ?? t;
}

export function capaActionTypeTone(t: CapaActionType): 'neutral' | 'primary' {
  return t === 'PREVENTIVE' ? 'primary' : 'neutral';
}
