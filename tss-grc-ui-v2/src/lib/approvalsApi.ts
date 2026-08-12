// Cross-module approval inbox client — typed wrappers around /api/approvals/requests.
//
// F2-201. This replaces `lib/approvals.ts`, which was a 407-line approval ENGINE in the
// browser: 12 hard-coded requests, a 16-role registry naming invented individuals as the
// organisation's CISO / DPO / CEO, and a parallel/sequential routing machine. The routed
// `/approvals` page read that corpus and "decided" it in React state, so an approver could
// click Approve on a policy publication or a risk acceptance, receive a success toast, and
// have nothing persisted anywhere — while the REAL register (22 rows in dev, 21 in prod,
// 12 and 11 of them PENDING) was never shown.
//
// The server side was already complete and hardened before this file existed:
//   • `GET  /api/approvals/requests`                  — the org's register
//   • `GET  /api/approvals/requests?myInbox=true`      — the caller's own pending queue
//   • `POST /api/approvals/requests/{id}/transition`   — guarded `WORKFLOW:APPROVE`
//   • SEC-IAM-002: the deciding identity is the authenticated principal, NEVER a body
//     field, and `ApprovalRequestService` refuses a decision from anyone but the current
//     assignee (or a PLATFORM_ADMIN).
//
// ⚠ What this client deliberately does NOT model, because the server does not:
// approval MODES (`PARALLEL_ANY` / `PARALLEL_ALL` / `SEQUENTIAL`), an approver-role
// registry, and a multi-approver `decisions[]` progress bar. `approvals.requests` has a
// single `assigned_to` plus `delegated_from` — one assignee at a time, with delegation.
// Re-creating the richer machine client-side is what made the old page a fabrication: it
// was MORE granular than the platform's own model, which is exactly why it looked more
// finished (the F2-199 shape).

import { api } from './api';

/** Mirrors `ApprovalRequestDto`. */
export interface ApprovalRequest {
  id: string;
  /** What is being approved — POLICY, RISK_ACCEPTANCE, CAPA, BOARD_PACK, … (17 in use). */
  entityType: string;
  entityId: string;
  titleEn: string;
  titleAr: string;
  summaryEn?: string | null;
  summaryAr?: string | null;
  requestedBy: string;
  requestedAt: string;
  /** Username the decision is currently with. */
  assignedTo: string;
  /** Who it was originally raised to, before any delegation. */
  originalAssignedTo: string;
  /** P1 | P2 | P3. */
  priority: string;
  slaDeadline?: string | null;
  status: string;
  decidedAt?: string | null;
  decidedBy?: string | null;
  decisionCommentEn?: string | null;
  decisionCommentAr?: string | null;
  delegatedFrom?: string | null;
  delegationReasonEn?: string | null;
}

/**
 * Mirrors `ApprovalRequestDto.TransitionRequest`.
 *
 * ⚠ There is no `decidedBy` here on purpose. The server takes the deciding identity from
 * the authenticated principal (SEC-IAM-002) — sending one would be a field the server
 * ignores, which is the kind of decoy F2-184 is about.
 */
export interface ApprovalTransition {
  status: string;
  decisionCommentEn?: string;
  decisionCommentAr?: string;
  /** Required when status is DELEGATED — the server throws without it. */
  newAssignedTo?: string;
  delegationReasonEn?: string;
}

const BASE = '/api/approvals/requests';

export const approvalsApi = {
  /** The whole org register. */
  list:    () => api.get<ApprovalRequest[]>(BASE),
  /** Only what is assigned to the caller and still PENDING. */
  myInbox: () => api.get<ApprovalRequest[]>(`${BASE}?myInbox=true`),
  transition: (id: string, body: ApprovalTransition) =>
    api.post<ApprovalRequest>(`${BASE}/${id}/transition`, body),
  // NB: `GET /{id}` is deliberately NOT wrapped. The server has it, but no screen opens a
  // single approval, so a client method for it would ship dark — and F2-165's gate said so on
  // the first run. Add it in the commit that adds the detail view, not before.
};

// ─── Lifecycle ───────────────────────────────────────────────────────────────

/**
 * Mirrors `ApprovalRequestService.ALLOWED` exactly. The server answers 409 outside it, so
 * this exists to stop the UI offering a button the server will refuse — never as a second
 * opinion about what is legal.
 *
 * ⚠ F2-190's lesson: an unread second copy of a state machine is how the next reader decides
 * it is the authority. This one IS read — `mayTransition` below is the only thing the page
 * consults — and `approvalsLifecycle.test.ts` pins it against the Java source.
 */
export const APPROVAL_ALLOWED: Record<string, readonly string[]> = {
  PENDING:   ['APPROVED', 'REJECTED', 'DELEGATED', 'EXPIRED'],
  DELEGATED: ['PENDING'],
  APPROVED:  [],
  REJECTED:  [],
  EXPIRED:   [],
};

/** Terminal = no outgoing edge. DERIVED, never typed twice (F2-196's rule). */
export const APPROVAL_TERMINAL: readonly string[] =
  Object.keys(APPROVAL_ALLOWED).filter(s => APPROVAL_ALLOWED[s].length === 0);

export function mayTransition(from: string, to: string): boolean {
  return (APPROVAL_ALLOWED[from] ?? []).includes(to);
}

/** "Still needs a decision" is the COMPLEMENT of the terminal set, not a second list. */
export function isOpen(status: string): boolean {
  return !APPROVAL_TERMINAL.includes(status);
}

export const APPROVAL_STATUS_TONE: Record<string, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  PENDING:   'warning',
  APPROVED:  'success',
  REJECTED:  'danger',
  DELEGATED: 'info',
  EXPIRED:   'danger',
};

export function approvalStatusLabel(status: string, isAr: boolean): string {
  switch (status) {
    case 'PENDING':   return isAr ? 'قيد الاعتماد' : 'Pending';
    case 'APPROVED':  return isAr ? 'معتمد' : 'Approved';
    case 'REJECTED':  return isAr ? 'مرفوض' : 'Rejected';
    case 'DELEGATED': return isAr ? 'مُفوَّض' : 'Delegated';
    case 'EXPIRED':   return isAr ? 'منتهي المدة' : 'Expired';
    default:          return status;
  }
}

export const PRIORITY_TONE: Record<string, 'danger' | 'warning' | 'neutral'> = {
  P1: 'danger', P2: 'warning', P3: 'neutral',
};

// ─── SLA, derived from the server's own deadline ─────────────────────────────

export type SlaState = 'OVERDUE' | 'DUE_SOON' | 'ON_TRACK' | 'NO_DEADLINE';

/**
 * ⚠ `slaDeadline` is populated on only 12 of 22 rows in dev (12 of 21 in prod), so
 * `NO_DEADLINE` is a real, common state and not a null stand-in. Rendering an absent
 * deadline as "on track" would assert something the data does not say — F2-197's rule that
 * a missing figure must read as unmeasured rather than as health.
 */
export function slaState(r: ApprovalRequest, now: Date): SlaState {
  if (!r.slaDeadline) return 'NO_DEADLINE';
  const ms = new Date(r.slaDeadline).getTime() - now.getTime();
  if (Number.isNaN(ms)) return 'NO_DEADLINE';
  if (ms < 0) return 'OVERDUE';
  if (ms <= 5 * 86_400_000) return 'DUE_SOON';
  return 'ON_TRACK';
}

/** Whole days remaining (negative when overdue), or null when there is no deadline. */
export function daysRemaining(r: ApprovalRequest, now: Date): number | null {
  if (!r.slaDeadline) return null;
  const ms = new Date(r.slaDeadline).getTime() - now.getTime();
  return Number.isNaN(ms) ? null : Math.ceil(ms / 86_400_000);
}

export const SLA_TONE: Record<SlaState, 'danger' | 'warning' | 'success' | 'neutral'> = {
  OVERDUE: 'danger', DUE_SOON: 'warning', ON_TRACK: 'success', NO_DEADLINE: 'neutral',
};
