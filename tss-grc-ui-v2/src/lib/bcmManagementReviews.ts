// BCM management reviews (ISO 22301 Clause 9.3) client — typed wrappers
// around /api/v1/bcm/management-reviews. Top management reviews the BCMS at
// planned intervals; the register captures inputs (audit/exercise/KPI),
// decisions and improvement actions, with a DRAFT -> IN_PROGRESS -> APPROVED
// -> ARCHIVED lifecycle.

import { api } from './api';

export interface BcmManagementReview {
  id: number;
  organisationId?: number;
  externalId?: string;
  reviewCode: string;
  cycleId?: number | null;
  reviewDate: string;          // ISO date (yyyy-MM-dd)
  chairpersonName: string;
  attendeesJson?: string | null;
  agendaJson?: string | null;
  inputsSummaryEn?: string | null;
  inputsSummaryAr?: string | null;
  kpiSnapshotJson?: string | null;
  decisionsJson?: string | null;
  improvementActionsJson?: string | null;
  status: string;              // DRAFT | IN_PROGRESS | APPROVED | ARCHIVED
  approvedBy?: string | null;
  approvedAt?: string | null;
  notesEn?: string | null;
  notesAr?: string | null;
  createdAt?: string;
}

export interface BcmManagementReviewCreate {
  reviewDate: string;
  chairpersonName: string;
  inputsSummaryEn?: string;
  inputsSummaryAr?: string;
  notesEn?: string;
  notesAr?: string;
  status?: string;
}

const BASE = '/api/v1/bcm/management-reviews';

export const bcmManagementReviewApi = {
  list:       () => api.get<BcmManagementReview[]>(BASE),
  get:        (id: number) => api.get<BcmManagementReview>(`${BASE}/${id}`),
  create:     (body: BcmManagementReviewCreate) => api.post<BcmManagementReview>(BASE, body),
  transition: (id: number, to: string) => api.post<BcmManagementReview>(`${BASE}/${id}/transition`, { to }),
};

// ─── Lifecycle ───────────────────────────────────────────────────────────────

export const MR_STATUSES = ['DRAFT', 'IN_PROGRESS', 'APPROVED', 'ARCHIVED'] as const;
export type MrStatus = typeof MR_STATUSES[number];

/** The natural next state in the linear lifecycle (null = terminal). */
export function nextMrStatus(status: string): MrStatus | null {
  const i = MR_STATUSES.indexOf(status as MrStatus);
  return i >= 0 && i < MR_STATUSES.length - 1 ? MR_STATUSES[i + 1] : null;
}

export const MR_STATUS_TONE: Record<string, 'neutral' | 'info' | 'success' | 'warning'> = {
  DRAFT: 'neutral', IN_PROGRESS: 'warning', APPROVED: 'success', ARCHIVED: 'info',
};

export function mrStatusLabel(status: string, isAr: boolean): string {
  switch (status) {
    case 'DRAFT':       return isAr ? 'مسودة' : 'Draft';
    case 'IN_PROGRESS': return isAr ? 'قيد التنفيذ' : 'In progress';
    case 'APPROVED':    return isAr ? 'معتمدة' : 'Approved';
    case 'ARCHIVED':    return isAr ? 'مؤرشفة' : 'Archived';
    default:            return status;
  }
}

/** The verb for advancing from `status` to its successor. */
export function mrAdvanceLabel(status: string, isAr: boolean): string {
  switch (status) {
    case 'DRAFT':       return isAr ? 'بدء المراجعة' : 'Start review';
    case 'IN_PROGRESS': return isAr ? 'اعتماد' : 'Approve';
    case 'APPROVED':    return isAr ? 'أرشفة' : 'Archive';
    default:            return isAr ? 'تقدّم' : 'Advance';
  }
}

// ─── JSON column parsing (best-effort) ─────────────────────────────────────────

export interface MrAttendee { name?: string; role?: string }
export interface MrAgendaItem { item?: string; presenter?: string; minutes?: number }
export interface MrDecision { decision?: string; rationale?: string; owner?: string; dueDate?: string }
export interface MrAction { action?: string; owner?: string; dueDate?: string }
export interface MrKpiSnapshot {
  openGaps?: number; criticalGaps?: number; exercisesCompleted?: number;
  drTestsLast12mo?: number; avgReadinessScore?: number;
}

function parseJson<T>(raw?: string | null, fallback?: T): T | undefined {
  if (!raw) return fallback;
  try { return JSON.parse(raw) as T; } catch { return fallback; }
}

export const parseAttendees = (r: BcmManagementReview) => parseJson<MrAttendee[]>(r.attendeesJson, []) ?? [];
export const parseAgenda     = (r: BcmManagementReview) => parseJson<MrAgendaItem[]>(r.agendaJson, []) ?? [];
export const parseDecisions  = (r: BcmManagementReview) => parseJson<MrDecision[]>(r.decisionsJson, []) ?? [];
export const parseActions    = (r: BcmManagementReview) => parseJson<MrAction[]>(r.improvementActionsJson, []) ?? [];
export const parseKpiSnapshot = (r: BcmManagementReview) => parseJson<MrKpiSnapshot>(r.kpiSnapshotJson, undefined);
