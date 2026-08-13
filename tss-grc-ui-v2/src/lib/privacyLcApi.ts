// PDPL data-subject-request client — typed wrappers around /api/privacy-lc/dsr-cases.
//
// F2-202. This replaces the DSR half of `lib/privacyLifecycle.ts`, which held three
// hard-coded cases in `SEED_DSR` (clock-minted from `Date.now()` at module load), stored
// them under `grc.privacy.dsr.v1`, and "transitioned" them in localStorage. The routed
// `/privacy-lifecycle` page and the lifecycle overview both read that corpus.
//
// Two things about the old code made it worse than an ordinary fabricated register:
//
//   1. `createDsr()` minted the PDPL Article 4 statutory deadline in the browser
//      (`Date.now() + 30 days`) and the tracking reference with `Math.random() * 999`.
//      F2-172 settled this for the intake substrate — only the server may issue a
//      tracking code or a statutory deadline — and deleted the client-side minters
//      rather than moving them. The same rule applies here, so neither exists in this
//      file: `create()` sends no id and no deadline, and the server stamps both.
//   2. It called `mirrorSaveToBackend(DSR_ADAPTER, …)`, which read a persistence-mode
//      switch that no code in the product could set, so it returned before sending
//      anything on every path. A reader of `saveDsr` saw a REST mirror and concluded the
//      case reached the server.
//
// The server side was already complete and hardened before this file existed:
//   • `GET  /api/privacy-lc/dsr-cases`                  — the org's register (PRIVACY:READ)
//   • `GET  /api/privacy-lc/dsr-cases?overdueOnly=true` — past the Article 4 deadline
//   • `POST /api/privacy-lc/dsr-cases`                  — files a case (PRIVACY:WRITE)
//   • `POST /api/privacy-lc/dsr-cases/{id}/transition`  — guarded, 409 outside the machine,
//     and it applies the Article 4 extension arithmetic itself on EXTENDED.

import { api } from './api';

/** Mirrors `DsrCaseDto`. */
export interface DsrCase {
  /** The server-minted reference code (`external_id`), e.g. DSR-2026-0007. */
  id: string;
  type: DsrType;
  subjectName: string;
  nationalIdLast4: string;
  contactEmail: string;
  status: DsrStatus;
  filedAt: string;
  identityVerifiedAt?: string | null;
  verificationMethod?: string | null;
  dataCollectedAt?: string | null;
  dataSummaryEn?: string | null;
  dataSummaryAr?: string | null;
  redactionCompletedAt?: string | null;
  deliveryMethod?: string | null;
  deliveredAt?: string | null;
  /** Server-issued PDPL Article 4 deadline. Never computed here. */
  slaDeadline: string;
  extended?: boolean | null;
  extensionReasonEn?: string | null;
  extensionReasonAr?: string | null;
  /**
   * ⚠ A STRING on the wire, not an array — `privacy_lc.dsr_cases.ropa_ids` is
   * NVARCHAR(MAX) and `DsrCaseDto` passes it through verbatim. The browser copy declared
   * `string[]`, which is why it needs saying out loud: use `ropaIdList()` below.
   */
  ropaIds?: string | null;
  /**
   * When `PrivacyGapService.dsrSlaSweep()` recorded a breach. F2-202 added it to the wire.
   *
   * ⚠ This is NOT the same question as "is the deadline in the past". F2-196 found two
   * EXTENDED cases 4 and 11 days past their deadline whose `sla_breached_at` was NULL,
   * because the sweep wrongly treated EXTENDED as closed — the platform had escalated
   * nothing. A client that derives breach from its own clock cannot show that gap, so both
   * are surfaced separately: `isPastDeadline()` and this field.
   */
  slaBreachedAt?: string | null;
  /** The lawful reason for refusing the request. F2-202 added it to the wire. */
  rejectionReasonEn?: string | null;
  rejectionReasonAr?: string | null;
}

export type DsrType =
  'ACCESS' | 'RECTIFICATION' | 'ERASURE' | 'OBJECTION' | 'RESTRICTION' | 'PORTABILITY' | 'WITHDRAW_CONSENT';

export type DsrStatus =
  'INTAKE' | 'VERIFYING_IDENTITY' | 'COLLECTING' | 'REDACTING' | 'DELIVERED' | 'REJECTED' | 'EXTENDED';

/**
 * Mirrors `DsrCaseDto` on the create path.
 *
 * ⚠ No `id` and no `slaDeadline`. The server mints the reference code and the Article 4
 * deadline, and as of F2-202 it IGNORES a supplied `id` outright rather than honouring it.
 */
export interface DsrCreateRequest {
  type: DsrType;
  subjectName: string;
  nationalIdLast4: string;
  contactEmail: string;
  ropaIds?: string;
}

/** Mirrors `DsrCaseDto.TransitionRequest`. */
export interface DsrTransition {
  status: DsrStatus;
  verificationMethod?: string;
  deliveryMethod?: string;
  dataSummaryEn?: string;
  dataSummaryAr?: string;
  /** Honoured only on REJECTED — the server scopes it to the rejecting transition. */
  rejectionReasonEn?: string;
  rejectionReasonAr?: string;
}

const BASE = '/api/privacy-lc/dsr-cases';

export const dsrApi = {
  list: () => api.get<DsrCase[]>(BASE),
  create: (body: DsrCreateRequest) => api.post<DsrCase>(BASE, body),
  transition: (id: string, body: DsrTransition) =>
    api.post<DsrCase>(`${BASE}/${encodeURIComponent(id)}/transition`, body),
  // NB: three server capabilities are deliberately NOT wrapped here.
  //
  //   • `GET /{id}` and `DELETE /{id}` exist and no screen opens a single case or deletes one.
  //   • `GET ?overdueOnly=true` exists too. A `listOverdue()` wrapper was written and then
  //     deleted: nothing called it, and F2-68(c)'s gate correctly reported it as a SECOND
  //     client for an endpoint `list()` already reaches — the shape that made an earlier scan
  //     report `riskAcceptanceApi` clean. The overdue figures on the page are counted over the
  //     same rows the list renders, which is also what stops a tile disagreeing with the list
  //     beneath it (F2-197's rule), so the server-side filter has no caller by design.
  //
  // Add each in the commit that adds the surface needing it. An unused client method should
  // not ship — that is F2-201's lesson, and this file lost one to it on its first run too.
};

// ─── Lifecycle ───────────────────────────────────────────────────────────────

/**
 * Mirrors `DsrCaseLifecycle.ALLOWED` exactly. The server answers 409 outside it, so this
 * exists only to stop the UI offering a button the server will refuse — never as a second
 * opinion about what is legal (F2-190's rule: an unread second copy of a state machine is
 * how the next reader decides it is the authority; this one is read by `mayTransition`).
 */
export const DSR_ALLOWED: Record<string, readonly DsrStatus[]> = {
  INTAKE:             ['VERIFYING_IDENTITY', 'REJECTED', 'EXTENDED'],
  VERIFYING_IDENTITY: ['COLLECTING', 'REJECTED', 'EXTENDED'],
  COLLECTING:         ['REDACTING', 'REJECTED', 'EXTENDED'],
  REDACTING:          ['DELIVERED', 'EXTENDED'],
  EXTENDED:           ['VERIFYING_IDENTITY', 'COLLECTING', 'REDACTING', 'DELIVERED', 'REJECTED'],
  DELIVERED:          [],
  REJECTED:           [],
};

/**
 * Terminal = no outgoing edge, DERIVED from the table — never typed as a second list.
 *
 * This is F2-196's rule, and this register is where it was learned: `dsrSlaSweep()` had
 * `EXTENDED` in a hand-written closed-status list, so the extended Article 4 deadline could
 * never be enforced. A derived set cannot disagree with the machine it comes from.
 */
export const DSR_TERMINAL: readonly DsrStatus[] =
  (Object.keys(DSR_ALLOWED) as DsrStatus[]).filter(s => DSR_ALLOWED[s].length === 0);

export function mayTransition(from: string, to: DsrStatus): boolean {
  return (DSR_ALLOWED[from] ?? []).includes(to);
}

/** "Still needs work" is the COMPLEMENT of the terminal set, not a second list. */
export function isOpen(status: string): boolean {
  return !DSR_TERMINAL.includes(status as DsrStatus);
}

/** `ropa_ids` is a joined string on the wire; this is the only place that knows the shape. */
export function ropaIdList(c: DsrCase): string[] {
  return (c.ropaIds ?? '').split(',').map(s => s.trim()).filter(Boolean);
}

// ─── Article 4 clock ─────────────────────────────────────────────────────────

/**
 * Past the server's deadline and still open. Distinct from `slaBreachedAt`, which is what
 * the platform has RECORDED — see the field's comment.
 */
export function isPastDeadline(c: DsrCase, now: Date): boolean {
  if (!isOpen(c.status) || !c.slaDeadline) return false;
  const t = new Date(c.slaDeadline).getTime();
  return !Number.isNaN(t) && t < now.getTime();
}

/** Whole days to the deadline (negative when past it), or null when it cannot be read. */
export function daysToDeadline(c: DsrCase, now: Date): number | null {
  if (!c.slaDeadline) return null;
  const t = new Date(c.slaDeadline).getTime();
  return Number.isNaN(t) ? null : Math.ceil((t - now.getTime()) / 86_400_000);
}

export interface DsrRegisterStats {
  total: number;
  open: number;
  /** Open and past the deadline, computed from the server's own deadline column. */
  pastDeadline: number;
  /** Open, past the deadline, and the platform has stamped no breach — F2-196's gap. */
  pastDeadlineUnrecorded: number;
  extended: number;
  /** Mean days from filing to delivery over DELIVERED cases, or null when there are none. */
  avgDaysToDeliver: number | null;
}

/**
 * ⚠ `avgDaysToDeliver` is `null`, not 0, when nothing has been delivered. A 0 there reads
 * as "we close requests the same day"; the honest answer is that there is nothing to
 * average (F2-197's rule that a missing figure must not read as health).
 */
export function aggregateDsr(rows: DsrCase[], now: Date): DsrRegisterStats {
  const open = rows.filter(c => isOpen(c.status));
  const past = open.filter(c => isPastDeadline(c, now));
  const delivered = rows.filter(c => c.status === 'DELIVERED' && c.deliveredAt);
  const avg = delivered.length === 0 ? null : Math.round(
    delivered.reduce((acc, c) =>
      acc + (new Date(c.deliveredAt!).getTime() - new Date(c.filedAt).getTime()), 0)
    / (delivered.length * 86_400_000));
  return {
    total: rows.length,
    open: open.length,
    pastDeadline: past.length,
    pastDeadlineUnrecorded: past.filter(c => !c.slaBreachedAt).length,
    extended: rows.filter(c => c.extended === true).length,
    avgDaysToDeliver: avg,
  };
}

// ─── Display ─────────────────────────────────────────────────────────────────

export const DSR_TYPE_LABEL: Record<DsrType, { en: string; ar: string }> = {
  ACCESS:           { en: 'Access',            ar: 'الوصول' },
  RECTIFICATION:    { en: 'Rectification',     ar: 'التصحيح' },
  ERASURE:          { en: 'Erasure',           ar: 'الحذف' },
  OBJECTION:        { en: 'Objection',         ar: 'الاعتراض' },
  RESTRICTION:      { en: 'Restriction',       ar: 'التقييد' },
  PORTABILITY:      { en: 'Portability',       ar: 'النقل' },
  WITHDRAW_CONSENT: { en: 'Withdraw consent',  ar: 'سحب الموافقة' },
};

export const DSR_STATUS_LABEL: Record<DsrStatus, { en: string; ar: string }> = {
  INTAKE:             { en: 'Intake',              ar: 'استلام' },
  VERIFYING_IDENTITY: { en: 'Verifying identity',  ar: 'التحقق من الهوية' },
  COLLECTING:         { en: 'Collecting data',     ar: 'جمع البيانات' },
  REDACTING:          { en: 'Redacting',           ar: 'إخفاء البيانات الحساسة' },
  DELIVERED:          { en: 'Delivered',           ar: 'مُسلَّم' },
  REJECTED:           { en: 'Rejected',            ar: 'مرفوض' },
  EXTENDED:           { en: 'Extended (60d)',      ar: 'مُمدَّد (60 يوم)' },
};

export const DSR_STATUS_TONE: Record<DsrStatus, 'info' | 'warning' | 'success' | 'danger' | 'neutral'> = {
  INTAKE: 'neutral', VERIFYING_IDENTITY: 'info', COLLECTING: 'info',
  REDACTING: 'warning', DELIVERED: 'success', REJECTED: 'danger', EXTENDED: 'warning',
};

export function dsrStatusLabel(status: string, isAr: boolean): string {
  const e = DSR_STATUS_LABEL[status as DsrStatus];
  return e ? (isAr ? e.ar : e.en) : status;
}

export function dsrTypeLabel(type: string, isAr: boolean): string {
  const e = DSR_TYPE_LABEL[type as DsrType];
  return e ? (isAr ? e.ar : e.en) : type;
}
