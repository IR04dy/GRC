// Derived CAPA signals + the Audit-Committee quarterly rollup.
//
// Standards anchor:
//   IIA IPPF 2410.A1 — communicate scope, conclusions, recommendations
//   IIA IPPF 2500.A1 — follow-up of management's action plan
//   ISO 9001:2015 §10.2 / ISO 27001:2022 §10.1-2 — corrective action evidence
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠⚠ F2-208 — this file was reduced, and the reduction is the point.
//
// It derived SIX notification kinds and an eleven-figure digest from the `Capa`
// shape. Three of the kinds and four of the figures read fields that exist on no
// column of `capa.tasks` (26 columns, read from sys.columns):
//
//   EFFECTIVENESS_OVERDUE  ← effectivenessDueDate / effectivenessVerified
//   VERIFICATION_FAILED    ← a status the server's machine does not have
//   CUSTOMER_DISCLOSURE    ← customerDisclosureRequired / customerDisclosureDate
//   digest.recurrent       ← parentCapaId
//   digest.topRootCauses   ← rootCauseCategory
//
// They worked only because the twenty-row browser corpus supplied the values.
// Deleting the corpus and keeping the derivations would have left an audit
// committee reading "verification failures: 0 · recurrent: 0 · top root causes:
// none" — F2-197's trap exactly: an invented figure invites a question, an
// invented ALL-CLEAR closes one, and zero here reads as an assurance that the
// same problem has not come back.
//
// So the three kinds and four figures are GONE rather than zeroed. What remains
// is computed from real columns only, and the digest states its own scope.
//
// Also deleted: RECIPIENT_FOR_KIND, which mapped each kind to an invented
// mailbox (`risk.officer@tss.sa`, `audit.committee@tss.sa`, `dpo@tss.sa`) under
// the field name "suggested recipient" — while nothing in the platform delivers
// a CAPA notification anywhere (that channel is F2-38d's filed finding). A
// recipient nothing sends to is a claim with no source (F2-193).
// ─────────────────────────────────────────────────────────────────────────────

import type { Capa } from './capa';
import { daysUntilDue, isCapaOpen } from './capa';

/** Only kinds derivable from a column on `capa.tasks`. */
export type CapaNotifKind = 'NEW_CRITICAL' | 'DUE_SOON' | 'OVERDUE';

export interface CapaNotif {
  id: string;
  kind: CapaNotifKind;
  capaId: string;
  capaTitleEn: string;
  capaTitleAr?: string;
  severity: 'INFO' | 'WARN' | 'CRITICAL';
  messageEn: string;
  messageAr: string;
  /** ISO date the signal is anchored on — used to sort newest-first. */
  firedAt: string;
  /** Days remaining (negative = overdue) — context only. */
  daysContext?: number;
}

const SEVERITY_FOR_KIND: Record<CapaNotifKind, 'INFO' | 'WARN' | 'CRITICAL'> = {
  NEW_CRITICAL: 'CRITICAL',
  DUE_SOON:     'WARN',
  OVERDUE:      'CRITICAL',
};

/** Window for "just raised", in days. */
const NEW_WINDOW_DAYS = 7;
/** Window for "due soon", in days. */
const DUE_SOON_DAYS = 7;

/**
 * Derive the live signal list from current register state, newest first.
 *
 * ⚠ NEW_CRITICAL needs `createdAt`, which the server DTO did not expose until
 * F2-208. A row without it is skipped for that kind rather than defaulted to
 * "raised today" — a CAPA of unknown age is not a new one.
 */
export function deriveCapaNotifs(items: Capa[], today: Date = new Date()): CapaNotif[] {
  const out: CapaNotif[] = [];
  const todayMs = today.getTime();
  const todayIso = today.toISOString().slice(0, 10);

  for (const c of items) {
    const open = isCapaOpen(c);
    const dueIn = daysUntilDue(c, today);

    if (open && (c.severity === 'HIGH' || c.severity === 'CRITICAL') && c.createdAt) {
      const ageMs = todayMs - new Date(c.createdAt).getTime();
      if (ageMs >= 0 && ageMs <= NEW_WINDOW_DAYS * 86_400_000) {
        out.push(makeNotif('NEW_CRITICAL', c, c.createdAt.slice(0, 10),
          `${c.severity} ${c.type === 'PREVENTIVE' ? 'PA' : 'CA'} raised (${c.id}). Risk-officer notification per IIA IPPF 2410.`,
          `تم فتح إجراء ${c.severity} ${c.type === 'PREVENTIVE' ? 'وقائي' : 'تصحيحي'} (${c.id}). إشعار مسؤول المخاطر وفقاً لمعيار IIA 2410.`));
      }
    }

    if (open && dueIn >= 0 && dueIn <= DUE_SOON_DAYS) {
      out.push(makeNotif('DUE_SOON', c, todayIso,
        `${c.id} due in ${dueIn} day${dueIn === 1 ? '' : 's'}.`,
        `${c.id} يستحق خلال ${dueIn} يوم.`,
        dueIn));
    }

    if (open && dueIn < 0) {
      out.push(makeNotif('OVERDUE', c, todayIso,
        `${c.id} overdue by ${Math.abs(dueIn)} day${Math.abs(dueIn) === 1 ? '' : 's'}. Audit Committee escalation per IIA IPPF 2500.A1.`,
        `${c.id} متأخر ${Math.abs(dueIn)} يوم. تصعيد إلى لجنة المراجعة وفقاً لمعيار IIA 2500.A1.`,
        dueIn));
    }
  }

  return out.sort((a, b) => b.firedAt.localeCompare(a.firedAt));
}

function makeNotif(
  kind: CapaNotifKind, c: Capa, firedAt: string,
  messageEn: string, messageAr: string,
  daysContext?: number,
): CapaNotif {
  return {
    id: `notif-${kind}-${c.id}-${firedAt}`,
    kind,
    capaId: c.id,
    capaTitleEn: c.titleEn,
    capaTitleAr: c.titleAr,
    severity: SEVERITY_FOR_KIND[kind],
    messageEn, messageAr,
    firedAt,
    daysContext,
  };
}

// ── Audit Committee quarterly rollup ────────────────────────────────

export interface AudCommitteeDigest {
  quarterLabel: string;
  quarterStart: string;
  quarterEnd: string;
  /** CAPAs raised inside the quarter (needs `createdAt`). */
  opened: number;
  /** CAPAs closed inside the quarter. */
  closed: number;
  /** Non-terminal today. */
  inFlight: number;
  /** Non-terminal and past their committed target date. */
  overdue: number;
  /** Non-terminal at HIGH or CRITICAL severity. */
  criticalOpen: number;
  /** Awaiting independent verification today. */
  awaitingVerification: number;
  /** Of `opened`, how many are preventive actions. */
  preventive: number;
  /**
   * Rows whose age could not be measured because `createdAt` is absent, so they
   * are outside `opened`/`preventive`. Disclosed rather than folded into a zero
   * — the committee needs to know the denominator is short.
   */
  undatedRows: number;
}

function quarterBounds(d: Date): { label: string; start: Date; end: Date } {
  const y = d.getFullYear();
  const q = Math.floor(d.getMonth() / 3);
  return {
    label: `${y} Q${q + 1}`,
    start: new Date(y, q * 3, 1),
    end:   new Date(y, q * 3 + 3, 0),
  };
}

export function audCommitteeDigest(items: Capa[], today: Date = new Date()): AudCommitteeDigest {
  const { label, start, end } = quarterBounds(today);
  const startMs = start.getTime();
  const endMs = end.getTime();
  const inQuarter = (iso: string | undefined) => {
    if (!iso) return false;
    const ms = new Date(iso).getTime();
    return ms >= startMs && ms <= endMs;
  };

  const opened = items.filter(c => inQuarter(c.createdAt));

  return {
    quarterLabel: label,
    quarterStart: start.toISOString().slice(0, 10),
    quarterEnd:   end.toISOString().slice(0, 10),
    opened:               opened.length,
    closed:               items.filter(c => inQuarter(c.closedAt)).length,
    inFlight:             items.filter(isCapaOpen).length,
    overdue:              items.filter(c => isCapaOpen(c) && daysUntilDue(c, today) < 0).length,
    criticalOpen:         items.filter(c => isCapaOpen(c)
                            && (c.severity === 'HIGH' || c.severity === 'CRITICAL')).length,
    awaitingVerification: items.filter(c => c.status === 'AWAITING_VERIFICATION').length,
    preventive:           opened.filter(c => c.type === 'PREVENTIVE').length,
    undatedRows:          items.filter(c => !c.createdAt).length,
  };
}
