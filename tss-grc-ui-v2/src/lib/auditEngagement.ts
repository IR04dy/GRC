// Audit engagement milestone SLA engine — Sprint 4.
//
// An external/internal audit has 7 milestones with hard regulatory or
// engagement-letter deadlines (engagement letter, opening, fieldwork
// start/end, report issued, closure, follow-up). Today the platform
// stores the dates but doesn't *track* slippage against them. This module
// turns each open milestone into a `WorkflowInstance` so the SLA engine
// drives the same countdown / escalation behaviour we use for risk and
// privacy work.
//
// Status mapping → which milestone is "current" / SLA-tracked:
//   PLANNED       → openingMeetingDate
//   FIELDWORK     → fieldworkEndDate
//   REPORT_ISSUED → closureDate (or reportIssuedDate if closure isn't set)
//   CLOSED        → no active milestone

import type { AuditEngagementDto } from './api';
import type { WorkflowInstance } from './workflows';

export interface AuditMilestone {
  /** Stage code shared with the workflow engine. */
  code: AuditMilestoneCode;
  /** Bilingual labels. */
  labelEn: string;
  labelAr: string;
  /** ISO date the milestone is targeted at. */
  date?: string;
  /** Whether the engagement is currently sitting on this milestone. */
  isCurrent: boolean;
  /** Whether the milestone date is in the past. */
  isPast: boolean;
}

export type AuditMilestoneCode =
  | 'ENGAGEMENT_LETTER'
  | 'OPENING_MEETING'
  | 'FIELDWORK_START'
  | 'FIELDWORK_END'
  | 'REPORT_ISSUED'
  | 'CLOSURE'
  | 'FOLLOWUP';

const MILESTONE_LABELS: Record<AuditMilestoneCode, { en: string; ar: string }> = {
  ENGAGEMENT_LETTER: { en: 'Engagement letter signed', ar: 'توقيع خطاب التكليف' },
  OPENING_MEETING:   { en: 'Opening meeting',          ar: 'اجتماع الافتتاح' },
  FIELDWORK_START:   { en: 'Fieldwork started',         ar: 'بدء العمل الميداني' },
  FIELDWORK_END:     { en: 'Fieldwork complete',        ar: 'انتهاء العمل الميداني' },
  REPORT_ISSUED:     { en: 'Audit report issued',       ar: 'إصدار تقرير التدقيق' },
  CLOSURE:           { en: 'Engagement closed',         ar: 'إغلاق الارتباط' },
  FOLLOWUP:          { en: 'Follow-up review',          ar: 'مراجعة المتابعة' },
};

/** Map engagement status → which milestone is currently active. */
function currentMilestone(status: string | undefined): AuditMilestoneCode | null {
  switch (status) {
    case 'PLANNED':       return 'OPENING_MEETING';
    case 'FIELDWORK':     return 'FIELDWORK_END';
    case 'REPORT_ISSUED': return 'CLOSURE';
    default:              return null;
  }
}

export function buildMilestones(eng: AuditEngagementDto): AuditMilestone[] {
  const cur = currentMilestone(eng.status);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const dateMap: Array<[AuditMilestoneCode, string | undefined]> = [
    ['ENGAGEMENT_LETTER', eng.engagementLetterDate],
    ['OPENING_MEETING',   eng.openingMeetingDate],
    ['FIELDWORK_START',   eng.fieldworkStartDate],
    ['FIELDWORK_END',     eng.fieldworkEndDate],
    ['REPORT_ISSUED',     eng.reportIssuedDate],
    ['CLOSURE',           eng.closureDate],
    ['FOLLOWUP',          eng.nextFollowupDate],
  ];

  return dateMap.map(([code, date]) => {
    const labels = MILESTONE_LABELS[code];
    const isCurrent = cur === code;
    const isPast = date != null && new Date(date) < today;
    return {
      code,
      labelEn: labels.en,
      labelAr: labels.ar,
      date,
      isCurrent,
      isPast,
    };
  });
}

// ─── SLA budget per milestone (calendar days) ───────────────────────────────
//
// Anchored against the previous milestone date to produce a meaningful
// "days remaining" picture. If a previous date isn't available we fall
// back to a sensible window so the SLA engine still has an anchor.

const MILESTONE_BUDGET_DAYS: Record<AuditMilestoneCode, number> = {
  ENGAGEMENT_LETTER: 30,
  OPENING_MEETING:   30,
  FIELDWORK_START:    7,
  FIELDWORK_END:     21,
  REPORT_ISSUED:     14,
  CLOSURE:           21,
  FOLLOWUP:          90,
};

function previousMilestoneDate(eng: AuditEngagementDto, code: AuditMilestoneCode): string | undefined {
  switch (code) {
    case 'ENGAGEMENT_LETTER': return undefined;
    case 'OPENING_MEETING':   return eng.engagementLetterDate;
    case 'FIELDWORK_START':   return eng.openingMeetingDate ?? eng.engagementLetterDate;
    case 'FIELDWORK_END':     return eng.fieldworkStartDate ?? eng.openingMeetingDate;
    case 'REPORT_ISSUED':     return eng.fieldworkEndDate;
    case 'CLOSURE':           return eng.reportIssuedDate ?? eng.fieldworkEndDate;
    case 'FOLLOWUP':          return eng.closureDate ?? eng.reportIssuedDate;
  }
}

// ─── Workflow synthesis ─────────────────────────────────────────────────────
//
// Build a WorkflowInstance for the *current* milestone of every active
// engagement. CLOSED engagements get nothing — their lifecycle is over.
// This is what feeds the LiveSlaPulse so an audit lead sees their
// fieldwork-end deadline alongside cyber and privacy SLAs.

const ENGAGEMENT_STAGES = [
  'PLANNING',
  'OPENING',
  'FIELDWORK',
  'REPORTING',
  'CLOSURE',
  'FOLLOWUP',
] as const;

function statusToStage(status: string | undefined): string {
  switch (status) {
    case 'PLANNED':       return 'OPENING';
    case 'FIELDWORK':     return 'FIELDWORK';
    case 'REPORT_ISSUED': return 'REPORTING';
    default:              return 'PLANNING';
  }
}

function progressFromStatus(status: string | undefined): number {
  switch (status) {
    case 'PLANNED':       return 15;
    case 'FIELDWORK':     return 50;
    case 'REPORT_ISSUED': return 80;
    case 'CLOSED':        return 100;
    default:              return 5;
  }
}

export function synthesiseEngagementWorkflow(
  eng: AuditEngagementDto,
  now: Date = new Date(),
): WorkflowInstance | null {
  const cur = currentMilestone(eng.status);
  if (!cur) return null;

  const dueDate =
    cur === 'OPENING_MEETING' ? eng.openingMeetingDate
    : cur === 'FIELDWORK_END' ? eng.fieldworkEndDate
    : cur === 'CLOSURE'        ? (eng.closureDate ?? eng.reportIssuedDate)
    : undefined;
  if (!dueDate) return null;

  const previousDate = previousMilestoneDate(eng, cur) ?? dueDate;
  const slaDays = MILESTONE_BUDGET_DAYS[cur];

  // Anchor: previous milestone date — gives the SLA engine a meaningful
  // "elapsed since previous milestone" reading.
  const anchor = new Date(previousDate);
  if (!isFinite(anchor.getTime())) return null;

  const ageDays = Math.max(0, Math.round((now.getTime() - anchor.getTime()) / 86_400_000));

  const milestoneLabelEn = MILESTONE_LABELS[cur].en;

  return {
    id: `AWF-${eng.id}`,
    module: 'Audit',
    workflow: 'Audit Engagement',
    entityType: 'Engagement',
    entityRef: eng.engagementCode ?? String(eng.id),
    title: `${eng.titleEn} — ${milestoneLabelEn}`,
    stages: [...ENGAGEMENT_STAGES],
    currentStage: statusToStage(eng.status),
    progress: progressFromStatus(eng.status),
    owner: eng.leadAuditor ?? eng.auditorFirm ?? 'Audit team',
    ownerRole: eng.auditorFirm,
    dueDate,
    ageDays,
    slaDays,
    escalation: 'ON_TRACK', // overridden by deriveLiveSla
    nextStep: milestoneLabelEn,
    href: `/audit-workspace/engagement/${eng.id}`,
  };
}

export function synthesiseAllEngagementWorkflows(
  engagements: ReadonlyArray<AuditEngagementDto>,
  now: Date = new Date(),
): WorkflowInstance[] {
  return engagements
    .map(e => synthesiseEngagementWorkflow(e, now))
    .filter((w): w is WorkflowInstance => w != null);
}
