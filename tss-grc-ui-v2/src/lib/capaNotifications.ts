// Notifications + Audit-Committee rollup derived from CAPA state.
//
// DD-44. Six derived notification kinds, one quarterly digest:
//
//   NOTIF KINDS
//   1. NEW_CRITICAL — a HIGH/CRITICAL CAPA was just opened
//   2. DUE_SOON     — open CAPA whose due date is within 7 days
//   3. OVERDUE      — open CAPA past due date
//   4. EFFECTIVENESS_OVERDUE — effectivenessDueDate has passed,
//      verification not yet recorded
//   5. VERIFICATION_FAILED — status flipped to VERIFICATION_FAILED
//      (root cause re-surfaced)
//   6. CUSTOMER_DISCLOSURE — closed CAPA whose customer-disclosure
//      date is within the last 30 days (regulator follow-up window)
//
//   AUDIT COMMITTEE DIGEST
//   Per quarter, surface: opened / closed / overdue / verification-
//   failed / recurrent count, plus the top root-cause categories.
//   IIA IPPF 2410 / 2500 — reporting to senior management + audit
//   committee on outstanding management actions.
//
// Standards anchor:
//   ISO 9001:2015 §10.2 + ISO 27001:2022 §10.1+10.2 + FDA 21 CFR
//   §820.100 — corrective action evidence + customer disclosure
//   IIA IPPF 2410.A1 — communicate scope, conclusions, recommendations
//   IIA IPPF 2500.A1 — follow-up of management's action plan

import type { Capa, RootCauseCategory } from './capa';
import { daysUntilDue, effectivenessOverdue } from './capa';

export type CapaNotifKind =
  | 'NEW_CRITICAL'
  | 'DUE_SOON'
  | 'OVERDUE'
  | 'EFFECTIVENESS_OVERDUE'
  | 'VERIFICATION_FAILED'
  | 'CUSTOMER_DISCLOSURE';

export interface CapaNotif {
  id: string;
  kind: CapaNotifKind;
  capaId: string;
  capaCode: string;
  capaTitleEn: string;
  capaTitleAr?: string;
  severity: 'INFO' | 'WARN' | 'CRITICAL';
  /** Suggested recipient (stakeholder code or role). */
  recipient: string;
  /** Human-readable summary in EN + AR. */
  messageEn: string;
  messageAr: string;
  /** When the trigger fires (ISO date) — used to sort newest-first. */
  firedAt: string;
  /** Days remaining (negative = overdue) — context only. */
  daysContext?: number;
}

const SEVERITY_FOR_KIND: Record<CapaNotifKind, 'INFO' | 'WARN' | 'CRITICAL'> = {
  NEW_CRITICAL:          'CRITICAL',
  DUE_SOON:              'WARN',
  OVERDUE:               'CRITICAL',
  EFFECTIVENESS_OVERDUE: 'WARN',
  VERIFICATION_FAILED:   'CRITICAL',
  CUSTOMER_DISCLOSURE:   'INFO',
};

const RECIPIENT_FOR_KIND: Record<CapaNotifKind, string> = {
  NEW_CRITICAL:          'risk.officer@tss.sa',
  DUE_SOON:              'audit.lead@tss.sa',
  OVERDUE:               'audit.committee@tss.sa',
  EFFECTIVENESS_OVERDUE: 'audit.lead@tss.sa',
  VERIFICATION_FAILED:   'audit.committee@tss.sa',
  CUSTOMER_DISCLOSURE:   'dpo@tss.sa',
};

function newestEvent(c: Capa): string {
  return c.history.length ? c.history[c.history.length - 1].at : c.openedAt;
}

/** Derive the live notification stream from current CAPA state.
    Sorted newest-first, capped at no specific limit (the UI may slice). */
export function deriveCapaNotifs(items: Capa[], today: Date = new Date()): CapaNotif[] {
  const out: CapaNotif[] = [];
  const todayMs = today.getTime();
  const sevenDaysAgoMs = todayMs - 7 * 86_400_000;

  for (const c of items) {
    const ageMs = todayMs - new Date(c.openedAt).getTime();
    const dueIn = daysUntilDue(c, today);

    // 1. NEW_CRITICAL — opened in the last 7 days, severity high/critical, status not closed
    if ((c.severity === 'HIGH' || c.severity === 'CRITICAL') &&
        ageMs >= 0 && ageMs <= 7 * 86_400_000 &&
        c.status !== 'CLOSED' && c.status !== 'CANCELLED') {
      out.push(makeNotif('NEW_CRITICAL', c, c.openedAt,
        `${c.severity} ${c.type === 'PREVENTIVE' ? 'PA' : 'CA'} opened (${c.code}). Risk officer notification per IIA IPPF 2410.`,
        `تم فتح إجراء ${c.severity} ${c.type === 'PREVENTIVE' ? 'وقائي' : 'تصحيحي'} (${c.code}). إشعار مسؤول المخاطر وفقاً لمعيار IIA 2410.`));
    }

    // 2. DUE_SOON — within 7 days
    if (dueIn >= 0 && dueIn <= 7 && c.status !== 'CLOSED' && c.status !== 'CANCELLED') {
      out.push(makeNotif('DUE_SOON', c, today.toISOString().slice(0, 10),
        `${c.code} due in ${dueIn} day${dueIn === 1 ? '' : 's'}.`,
        `${c.code} يستحق خلال ${dueIn} يوم.`,
        dueIn));
    }

    // 3. OVERDUE
    if (dueIn < 0 && c.status !== 'CLOSED' && c.status !== 'CANCELLED') {
      out.push(makeNotif('OVERDUE', c, today.toISOString().slice(0, 10),
        `${c.code} overdue by ${Math.abs(dueIn)} day${Math.abs(dueIn) === 1 ? '' : 's'}. Audit Committee escalation per IIA IPPF 2500.A1.`,
        `${c.code} متأخر ${Math.abs(dueIn)} يوم. تصعيد إلى لجنة المراجعة وفقاً لمعيار IIA 2500.A1.`,
        dueIn));
    }

    // 4. EFFECTIVENESS_OVERDUE
    if (effectivenessOverdue(c, today)) {
      const due = c.effectivenessDueDate!;
      out.push(makeNotif('EFFECTIVENESS_OVERDUE', c, due,
        `${c.code} effectiveness re-test overdue (was due ${due}).`,
        `${c.code} اختبار فاعلية متأخر (مستحق منذ ${due}).`));
    }

    // 5. VERIFICATION_FAILED
    if (c.status === 'VERIFICATION_FAILED') {
      out.push(makeNotif('VERIFICATION_FAILED', c, newestEvent(c),
        `${c.code} flipped to VERIFICATION_FAILED — root cause re-surfaced. Audit Committee briefing required.`,
        `${c.code} تحول إلى فشل التحقق — السبب الجذري عاد للظهور. مطلوب إحاطة لجنة المراجعة.`));
    }

    // 6. CUSTOMER_DISCLOSURE — disclosure made within the last 30 days
    if (c.customerDisclosureRequired && c.customerDisclosureDate) {
      const dispMs = new Date(c.customerDisclosureDate).getTime();
      if (todayMs - dispMs <= 30 * 86_400_000 && dispMs <= todayMs) {
        out.push(makeNotif('CUSTOMER_DISCLOSURE', c, c.customerDisclosureDate,
          `${c.code} customer disclosure made on ${c.customerDisclosureDate} — DPO follow-up window open until ${new Date(dispMs + 30 * 86_400_000).toISOString().slice(0, 10)}.`,
          `${c.code} تم إبلاغ العميل بتاريخ ${c.customerDisclosureDate} — نافذة متابعة مسؤول حماية البيانات حتى ${new Date(dispMs + 30 * 86_400_000).toISOString().slice(0, 10)}.`));
      }
    }

    // Trigger marker — sevenDaysAgoMs unused-but-context
    void sevenDaysAgoMs;
  }

  // newest-first
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
    capaId: c.id, capaCode: c.code, capaTitleEn: c.titleEn, capaTitleAr: c.titleAr,
    severity: SEVERITY_FOR_KIND[kind],
    recipient: RECIPIENT_FOR_KIND[kind],
    messageEn, messageAr,
    firedAt,
    daysContext,
  };
}

// ── Audit Committee quarterly rollup digest ────────────────────────

export interface AudCommitteeDigest {
  quarterLabel: string;          // "2026 Q2"
  quarterStart: string;          // ISO date
  quarterEnd: string;            // ISO date
  opened: number;
  closed: number;
  inFlight: number;
  overdue: number;
  verificationFailed: number;
  recurrent: number;             // CAPAs with parentCapaId set
  customerDisclosures: number;
  preventive: number;            // PA opened in this quarter
  topRootCauses: Array<{ category: RootCauseCategory; count: number; pct: number }>;
}

function quarterBounds(d: Date): { label: string; start: Date; end: Date } {
  const y = d.getFullYear();
  const q = Math.floor(d.getMonth() / 3);
  const start = new Date(y, q * 3, 1);
  const end = new Date(y, q * 3 + 3, 0); // last day of last month of quarter
  return { label: `${y} Q${q + 1}`, start, end };
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

  const opened   = items.filter(c => inQuarter(c.openedAt));
  const closed   = items.filter(c => inQuarter(c.closedAt));
  const inFlight = items.filter(c => c.status !== 'CLOSED' && c.status !== 'CANCELLED' &&
    new Date(c.openedAt).getTime() <= endMs);
  const overdue  = items.filter(c => c.status !== 'CLOSED' && c.status !== 'CANCELLED' &&
    new Date(c.dueDate).getTime() < today.getTime());
  const verificationFailed = items.filter(c => c.status === 'VERIFICATION_FAILED');
  const recurrent = items.filter(c => c.parentCapaId);
  const customerDisclosures = items.filter(c => inQuarter(c.customerDisclosureDate));
  const preventive = opened.filter(c => c.type === 'PREVENTIVE');

  // root causes — top 3 by count
  const byCat = new Map<RootCauseCategory, number>();
  for (const c of items) {
    if (!c.rootCauseCategory) continue;
    byCat.set(c.rootCauseCategory, (byCat.get(c.rootCauseCategory) ?? 0) + 1);
  }
  const total = items.length || 1;
  const topRootCauses = Array.from(byCat.entries())
    .map(([category, count]) => ({ category, count, pct: Math.round((count / total) * 100) }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 3);

  return {
    quarterLabel: label,
    quarterStart: start.toISOString().slice(0, 10),
    quarterEnd:   end.toISOString().slice(0, 10),
    opened:               opened.length,
    closed:               closed.length,
    inFlight:             inFlight.length,
    overdue:              overdue.length,
    verificationFailed:   verificationFailed.length,
    recurrent:            recurrent.length,
    customerDisclosures:  customerDisclosures.length,
    preventive:           preventive.length,
    topRootCauses,
  };
}
