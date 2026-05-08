// SLA computation engine — Sprint 4 workflow runtime.
//
// Turns the per-transition `slaHours` and per-instance `dueDate` data into a
// live escalation tier. Saudi-aware: Friday–Saturday weekend, Sunday–Thursday
// work week, Hijri (Umm al-Qura) display alongside Gregorian. Pure functions
// only — no React, no localStorage; consumers wrap with hooks/components.

import type { EscalationTier, WorkflowInstance } from './workflows';

// ─── Saudi business calendar ────────────────────────────────────────────────
//
// Saudi Arabia's official weekend has been Friday–Saturday since 2013
// (Royal Decree 9/8/1434H). Public sector and most private sector follow
// this; work week is Sunday → Thursday.
// JS getDay(): 0=Sun … 5=Fri … 6=Sat.

export const SAUDI_WEEKEND_DAYS: ReadonlyArray<number> = [5, 6]; // Fri, Sat
export const SAUDI_BUSINESS_DAYS: ReadonlyArray<number> = [0, 1, 2, 3, 4]; // Sun–Thu

export function isSaudiBusinessDay(d: Date): boolean {
  return SAUDI_BUSINESS_DAYS.includes(d.getDay());
}

/** Add N business days to a date (skips Fri/Sat). N can be negative. */
export function addBusinessDays(start: Date, days: number): Date {
  const out = new Date(start);
  if (days === 0) return out;
  const step = days > 0 ? 1 : -1;
  let remaining = Math.abs(days);
  while (remaining > 0) {
    out.setDate(out.getDate() + step);
    if (isSaudiBusinessDay(out)) remaining--;
  }
  return out;
}

/** Calendar days between two dates (b - a), can be negative. Floors at midnight. */
export function calendarDaysBetween(a: Date, b: Date): number {
  const ms = b.setHours(0, 0, 0, 0) - new Date(a).setHours(0, 0, 0, 0);
  return Math.round(ms / 86_400_000);
}

/** Business days between two dates (b - a) using the Saudi calendar. */
export function businessDaysBetween(a: Date, b: Date): number {
  const start = new Date(Math.min(a.getTime(), b.getTime()));
  const end = new Date(Math.max(a.getTime(), b.getTime()));
  const sign = a.getTime() <= b.getTime() ? 1 : -1;
  let count = 0;
  const cursor = new Date(start);
  cursor.setHours(0, 0, 0, 0);
  end.setHours(0, 0, 0, 0);
  while (cursor < end) {
    cursor.setDate(cursor.getDate() + 1);
    if (isSaudiBusinessDay(cursor)) count++;
  }
  return count * sign;
}

// ─── Hijri (Umm al-Qura) formatting ─────────────────────────────────────────
// Why: Saudi government deadlines are often quoted in Hijri. Surface both so
// a Saudi user can match a date to an authority circular at a glance.

const HIJRI_AR = new Intl.DateTimeFormat('ar-SA-u-ca-islamic-umalqura', {
  day: 'numeric', month: 'long', year: 'numeric',
});
const HIJRI_EN = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura', {
  day: 'numeric', month: 'short', year: 'numeric',
});

export function formatHijri(d: Date | string, lang: 'en' | 'ar' = 'en'): string {
  const date = typeof d === 'string' ? new Date(d) : d;
  if (!isFinite(date.getTime())) return '—';
  return (lang === 'ar' ? HIJRI_AR : HIJRI_EN).format(date);
}

// ─── SLA snapshot ───────────────────────────────────────────────────────────

export interface SlaSnapshot {
  /** Calendar days from now → dueDate (negative when past due). */
  daysRemaining: number;
  /** Saudi business days from now → dueDate (negative when past due). */
  businessDaysRemaining: number;
  /** % of SLA budget consumed (≥100 means past due). */
  percentConsumed: number;
  /** % over the SLA budget (0 if on time, 50 means halfway over budget). */
  percentBreach: number;
  /** Computed tier given current breach and the rule applied. */
  tier: EscalationTier;
  /** Hijri label of the due date in the requested language. */
  dueDateHijri: string;
  /** Reason hint for tooltip / audit (English). */
  reason: string;
}

export interface SlaInput {
  /** ISO date the SLA was anchored (instance start or last transition). */
  anchorDate: string;
  /** ISO due date. */
  dueDate: string;
  /** Total SLA budget in calendar days. */
  slaDays: number;
  /** Whether the workflow is already terminal — terminal stages don't escalate. */
  terminal?: boolean;
  /** Optional explicit "now" for testing. */
  now?: Date;
}

/**
 * Compute SLA snapshot. Default escalation rule (matching ServiceNow IRM
 * conventions and our memo):
 *   AT_RISK    when ≤ 5 calendar days remain (or ≤20% of budget left)
 *   OVERDUE    when past due, breach < 50% of budget
 *   MANAGER_ESC    breach 50–100% of budget
 *   DIRECTOR_ESC   breach 100–200%
 *   EXEC_ESC       breach > 200%
 *
 * Use `computeSlaWithRule` to override per workflow definition.
 */
export function computeSla(input: SlaInput): SlaSnapshot {
  return computeSlaWithRule(input, DEFAULT_ESCALATION_RULE);
}

export interface EscalationRule {
  /** When ≤ this many days left, mark AT_RISK. */
  atRiskDays: number;
  /** Breach % at which to escalate to manager (0.5 = 50% over budget). */
  managerBreachPct: number;
  /** Breach % at which to escalate to director. */
  directorBreachPct: number;
  /** Breach % at which to escalate to executive committee. */
  execBreachPct: number;
}

export const DEFAULT_ESCALATION_RULE: EscalationRule = {
  atRiskDays: 5,
  managerBreachPct: 0.5,
  directorBreachPct: 1.0,
  execBreachPct: 2.0,
};

export function computeSlaWithRule(input: SlaInput, rule: EscalationRule): SlaSnapshot {
  const now = input.now ?? new Date();
  const due = new Date(input.dueDate);
  const anchor = new Date(input.anchorDate);

  const daysRemaining = calendarDaysBetween(now, due);
  const businessDaysRemaining = businessDaysBetween(now, due);
  const elapsed = Math.max(0, calendarDaysBetween(anchor, now));
  const slaDays = Math.max(1, input.slaDays);
  const percentConsumed = (elapsed / slaDays) * 100;
  const breachDays = -daysRemaining; // positive when past due
  const percentBreach = breachDays > 0 ? (breachDays / slaDays) * 100 : 0;

  let tier: EscalationTier;
  let reason: string;

  if (input.terminal) {
    tier = 'ON_TRACK';
    reason = 'Terminal stage — SLA closed';
  } else if (daysRemaining < 0) {
    const ratio = breachDays / slaDays;
    if (ratio >= rule.execBreachPct) {
      tier = 'EXEC_ESC';
      reason = `Overdue by ${breachDays}d (${Math.round(percentBreach)}% over budget) — exec committee escalation`;
    } else if (ratio >= rule.directorBreachPct) {
      tier = 'DIRECTOR_ESC';
      reason = `Overdue by ${breachDays}d (${Math.round(percentBreach)}% over budget) — director escalation`;
    } else if (ratio >= rule.managerBreachPct) {
      tier = 'MANAGER_ESC';
      reason = `Overdue by ${breachDays}d (${Math.round(percentBreach)}% over budget) — line-manager escalation`;
    } else {
      tier = 'OVERDUE';
      reason = `Overdue by ${breachDays}d (${Math.round(percentBreach)}% over budget)`;
    }
  } else if (daysRemaining <= rule.atRiskDays) {
    tier = 'AT_RISK';
    reason = `Due in ${daysRemaining}d (${businessDaysRemaining} business day${businessDaysRemaining === 1 ? '' : 's'})`;
  } else {
    tier = 'ON_TRACK';
    reason = `${daysRemaining}d remaining (${businessDaysRemaining} business)`;
  }

  return {
    daysRemaining,
    businessDaysRemaining,
    percentConsumed: Math.min(999, Math.round(percentConsumed)),
    percentBreach: Math.round(percentBreach),
    tier,
    dueDateHijri: formatHijri(due, 'en'),
    reason,
  };
}

// ─── Live re-derivation for WorkflowInstance ────────────────────────────────
//
// The mock instances ship with a frozen `escalation` field. Passing them
// through `deriveLiveSla` recomputes the tier from the actual `dueDate` and
// today's clock so the page reflects reality, not the snapshot.

export interface LiveWorkflowInstance extends WorkflowInstance {
  sla: SlaSnapshot;
}

export function deriveLiveSla(
  instance: WorkflowInstance,
  rule: EscalationRule = DEFAULT_ESCALATION_RULE,
  now: Date = new Date(),
): LiveWorkflowInstance {
  if (!instance.dueDate) {
    return {
      ...instance,
      sla: {
        daysRemaining: 0, businessDaysRemaining: 0, percentConsumed: 0, percentBreach: 0,
        tier: instance.escalation, dueDateHijri: '—', reason: instance.reason ?? '',
      },
    };
  }

  // Anchor the SLA window backwards from dueDate by the budget. We don't have
  // a stored startedAt on the mock; this gives us a reasonable elapsed-% in
  // the absence of an explicit anchor.
  const due = new Date(instance.dueDate);
  const anchor = new Date(due);
  anchor.setDate(anchor.getDate() - instance.slaDays);

  const terminal = instance.stages[instance.stages.length - 1] === instance.currentStage;

  const sla = computeSlaWithRule(
    {
      anchorDate: anchor.toISOString(),
      dueDate: instance.dueDate,
      slaDays: instance.slaDays,
      terminal,
      now,
    },
    rule,
  );

  return { ...instance, escalation: sla.tier, sla };
}

export function deriveAllLive(
  instances: ReadonlyArray<WorkflowInstance>,
  rule: EscalationRule = DEFAULT_ESCALATION_RULE,
  now: Date = new Date(),
): LiveWorkflowInstance[] {
  return instances.map(i => deriveLiveSla(i, rule, now));
}

// ─── Aggregation helpers ────────────────────────────────────────────────────

export interface SlaPulse {
  total: number;
  byTier: Record<EscalationTier, number>;
  breached: number;
  atRisk: number;
  onTrack: number;
}

export function aggregatePulse(items: ReadonlyArray<LiveWorkflowInstance>): SlaPulse {
  const byTier: Record<EscalationTier, number> = {
    ON_TRACK: 0, AT_RISK: 0, OVERDUE: 0, MANAGER_ESC: 0, DIRECTOR_ESC: 0, EXEC_ESC: 0,
  };
  for (const i of items) byTier[i.sla.tier]++;
  const breached = byTier.OVERDUE + byTier.MANAGER_ESC + byTier.DIRECTOR_ESC + byTier.EXEC_ESC;
  return {
    total: items.length,
    byTier,
    breached,
    atRisk: byTier.AT_RISK,
    onTrack: byTier.ON_TRACK,
  };
}
