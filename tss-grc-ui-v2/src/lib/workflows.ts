// Unified active-workflow representation used across modules.
// Every module surfaces "what's in flight" as a list of WorkflowInstance
// rows so the user always sees what is awaiting their attention.

export type EscalationTier =
  | 'ON_TRACK'
  | 'AT_RISK'         // due in <= 5 days
  | 'OVERDUE'         // past due, no manager escalation yet
  | 'MANAGER_ESC'     // escalated to line manager
  | 'DIRECTOR_ESC'    // escalated to division director
  | 'EXEC_ESC';       // escalated to executive committee

export interface WorkflowInstance {
  /** Stable id ("RWF-0042"). */
  id: string;
  /** Module name shown in the chip ("Risk", "Compliance", "Cyber"…). */
  module: string;
  /** Workflow definition name ("Risk Lifecycle", "CAPA Closure"…). */
  workflow: string;
  /** Entity type the workflow runs on ("Risk", "Finding", "Vendor", "DPIA"…). */
  entityType: string;
  /** Stable reference of the entity ("R-0042", "AUD-2026-014"). */
  entityRef: string;
  /** Human-readable subject. */
  title: string;
  /** All stages in the workflow definition, in canonical order. */
  stages: string[];
  /** Current stage id (must be one of `stages`). */
  currentStage: string;
  /** 0–100 progress percentage. */
  progress: number;
  /** Display name of the current owner. */
  owner: string;
  /** Optional ownerRole label ("Risk Manager", "DPO"). */
  ownerRole?: string;
  /** ISO date string. */
  dueDate?: string;
  /** Days the instance has been open. */
  ageDays: number;
  /** Calendar-day SLA target. */
  slaDays: number;
  /** Escalation tier. */
  escalation: EscalationTier;
  /** Optional next-action label. */
  nextStep?: string;
  /** Optional reason text shown in the escalation badge tooltip. */
  reason?: string;
  /** Optional URL the row links to (entity detail). */
  href?: string;
}

export const ESCALATION_TONE: Record<EscalationTier, 'success' | 'warning' | 'danger' | 'accent' | 'info'> = {
  ON_TRACK:    'success',
  AT_RISK:     'info',
  OVERDUE:     'warning',
  MANAGER_ESC: 'warning',
  DIRECTOR_ESC:'accent',
  EXEC_ESC:    'danger',
};

export const ESCALATION_LABEL: Record<EscalationTier, string> = {
  ON_TRACK:    'On track',
  AT_RISK:     'Due soon',
  OVERDUE:     'Overdue',
  MANAGER_ESC: 'Manager escalated',
  DIRECTOR_ESC:'Director escalated',
  EXEC_ESC:    'Executive escalated',
};
