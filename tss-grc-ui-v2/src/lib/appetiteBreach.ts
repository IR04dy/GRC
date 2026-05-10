// Appetite-breach detection engine — Sprint 4.
//
// Bridges the static appetite library to the live SLA runtime: every
// statement that is currently breached is synthesised as a `WorkflowInstance`
// with a severity-driven SLA budget so it shows up in the LiveSlaPulse and
// inherits the Saudi business-calendar / Hijri / escalation-tier behaviour
// the SLA engine already provides. No persistence — this is a pure
// derivation from appetite state to workflow state.
//
// Severity → SLA budget (calendar days to "WITHIN_TOLERANCE"):
//   CRITICAL  →  7  days  (board / exec committee)
//   EXCEEDED  → 30  days  (executive owner)
//   WARNING   → 90  days  (line manager — proactive, not yet breached)
//
// Owner role is derived from the statement's `approvedBy` field so the
// notification routing already knows where to send the breach packet.

import type { WorkflowInstance } from './workflows';
import type { RiskAppetiteStatementDto } from './api';
import {
  classifyAppetite, appetiteSeverityLabel, type AppetiteSeverity,
} from './riskAppetiteSaudi';

const SEVERITY_SLA_DAYS: Record<AppetiteSeverity, number> = {
  WITHIN_APPETITE: 0,
  WARNING:  90,
  EXCEEDED: 30,
  CRITICAL:  7,
};

const APPETITE_BREACH_STAGES = [
  'DETECTED',
  'INVESTIGATED',
  'TREATMENT',
  'WITHIN_TOLERANCE',
] as const;

const SEVERITY_TO_NEXT: Record<AppetiteSeverity, string> = {
  WITHIN_APPETITE: '—',
  WARNING:  'Brief line-manager; refresh exposure forecast',
  EXCEEDED: 'Present remediation plan to risk committee',
  CRITICAL: 'Escalate to board risk committee within 7 days',
};

export interface AppetiteBreachWorkflow {
  /** Source appetite statement. */
  statement: RiskAppetiteStatementDto;
  /** Severity tier driving the SLA. */
  severity: AppetiteSeverity;
  /** The synthesised workflow instance for the SLA pulse / countdown UIs. */
  workflow: WorkflowInstance;
}

/**
 * Synthesise a WorkflowInstance for an appetite statement that is at or
 * past its tolerance. Returns null for statements that are within appetite.
 *
 * `now` lets tests / fixtures pin the breach window to a specific date.
 */
export function synthesiseBreachWorkflow(
  statement: RiskAppetiteStatementDto,
  now: Date = new Date(),
): AppetiteBreachWorkflow | null {
  const severity = classifyAppetite(statement);
  if (severity === 'WITHIN_APPETITE') return null;

  const slaDays = SEVERITY_SLA_DAYS[severity];
  // Anchor the breach window from "now" (forward-looking) for WARNING tier,
  // or backwards-from-due for already-breached tiers so the SLA engine sees
  // realistic elapsed time.
  const detectedDaysAgo = severity === 'WARNING' ? 0
                        : severity === 'EXCEEDED' ? Math.round(slaDays * 0.4)
                        : Math.round(slaDays * 0.8); // CRITICAL — most of budget consumed
  const detectedAt = new Date(now);
  detectedAt.setDate(detectedAt.getDate() - detectedDaysAgo);
  const dueDate = new Date(detectedAt);
  dueDate.setDate(dueDate.getDate() + slaDays);

  const refCode = `APB-${statement.id}`;
  const obs = statement.currentExposure ?? 0;
  const upper = statement.toleranceUpper ?? 0;
  const reasonEn = statement.toleranceUpper != null && obs > upper
    ? `${obs}${statement.unit ?? ''} observed vs ${upper}${statement.unit ?? ''} tolerance`
    : `${appetiteSeverityLabel(severity, 'en')} — exposure ${obs}${statement.unit ?? ''}`;

  // Map severity to currentStage. Fresh CRITICALs sit in DETECTED, EXCEEDED
  // sit in INVESTIGATED (someone's got eyes on it), WARNING in TREATMENT
  // (proactive — already being addressed before formal breach).
  const currentStage =
    severity === 'CRITICAL' ? 'DETECTED'
    : severity === 'EXCEEDED' ? 'INVESTIGATED'
    : 'TREATMENT';
  const progress = severity === 'CRITICAL' ? 15
                 : severity === 'EXCEEDED' ? 45
                 : 70;

  const workflow: WorkflowInstance = {
    id: `APB-${statement.id}`,
    module: 'Risk',
    workflow: 'Appetite Breach Response',
    entityType: 'Appetite',
    entityRef: refCode,
    title: statement.titleEn,
    stages: [...APPETITE_BREACH_STAGES],
    currentStage,
    progress,
    owner: statement.approvedBy ?? 'Risk Committee',
    ownerRole: statement.riskDimension,
    dueDate: dueDate.toISOString().slice(0, 10),
    ageDays: detectedDaysAgo,
    slaDays,
    // Initial value — LiveSlaPulse will override via deriveLiveSla.
    escalation: severity === 'CRITICAL' ? 'EXEC_ESC'
              : severity === 'EXCEEDED' ? 'DIRECTOR_ESC'
              : 'AT_RISK',
    nextStep: SEVERITY_TO_NEXT[severity],
    reason: reasonEn,
    href: `/risk?tab=appetite&id=${statement.id}`,
  };

  return { statement, severity, workflow };
}

/** Synthesise breach workflows for every statement currently outside appetite. */
export function synthesiseAllBreaches(
  statements: ReadonlyArray<RiskAppetiteStatementDto>,
  now: Date = new Date(),
): AppetiteBreachWorkflow[] {
  const out: AppetiteBreachWorkflow[] = [];
  for (const s of statements) {
    const b = synthesiseBreachWorkflow(s, now);
    if (b) out.push(b);
  }
  return out;
}
