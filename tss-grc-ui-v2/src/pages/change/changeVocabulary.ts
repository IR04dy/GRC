/**
 * S5c — the Change workspace's shared vocabulary: tab identifiers, the status/type/risk
 * badge tones, the board lanes and the form option lists.
 *
 * <p>Extracted from the 1,036-line `ChangesPage` so the panels can be read, tested and
 * (in CE-13) routed independently. Values are unchanged — this is the same table the
 * monolith held, in a file that does not also fetch data.
 *
 * ⚠ These are display vocabularies, not authority. The lifecycle's legal moves live in
 * `changeLifecycle.ts` and are pinned against the backend's transition table; the tones
 * here only decide what colour a status renders.
 */
import type { BadgeTone } from '../../components/ui/Badge';
import type { ChangeRequestDto } from '../../lib/api';

export type TabId = 'register' | 'kanban' | 'workflow' | 'analytics' | 'integrations' | 'governance' | 'comms';
export const TAB_IDS: TabId[] = ['register', 'kanban', 'workflow', 'analytics', 'integrations', 'governance', 'comms'];
// Changes eligible for a post-implementation review (the PIR process queue).
export const REVIEW_STATUSES = ['IMPLEMENTED', 'ROLLED_BACK', 'POST_REVIEW'];
/**
 * Statuses that mean a change is still moving through the pipeline. F2-197 needed
 * this named because the "In flight" KPI and the four process tiles must agree:
 * two copies of a status list is how F2-189 and F2-191 got a scheduler and a guard
 * disagreeing about the same column.
 */
export const IN_FLIGHT_STATUSES = [
  'SUBMITTED', 'IMPACT_ASSESSED', 'CAB_PENDING', 'CAB_APPROVED',
  'SCHEDULED', 'EMERGENCY_APPROVED', 'IMPLEMENTING',
];

export const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', SUBMITTED: 'info', IMPACT_ASSESSED: 'info',
  CAB_PENDING: 'warning', CAB_APPROVED: 'primary', CAB_REJECTED: 'danger',
  EMERGENCY_APPROVED: 'accent',
  SCHEDULED: 'primary', IMPLEMENTING: 'accent', IMPLEMENTED: 'success',
  ROLLED_BACK: 'danger', POST_REVIEW: 'info', CLOSED: 'success', CANCELLED: 'neutral',
};
export const TYPE_TONE: Record<string, BadgeTone> = {
  STANDARD: 'success', NORMAL: 'primary', EMERGENCY: 'danger',
};
export const RISK_TONE: Record<string, BadgeTone> = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};

// Simplified kanban board lanes (post-review + closed go to a final lane)
export const KANBAN_LANES = [
  { id: 'DRAFT',           statuses: ['DRAFT'] },
  { id: 'SUBMITTED',       statuses: ['SUBMITTED', 'IMPACT_ASSESSED'] },
  { id: 'CAB',             statuses: ['CAB_PENDING'] },
  { id: 'APPROVED',        statuses: ['CAB_APPROVED', 'EMERGENCY_APPROVED', 'SCHEDULED'] },
  { id: 'IMPLEMENTING',    statuses: ['IMPLEMENTING'] },
  { id: 'COMPLETED',       statuses: ['IMPLEMENTED', 'POST_REVIEW', 'CLOSED', 'ROLLED_BACK', 'CAB_REJECTED', 'CANCELLED'] },
];

export const TYPE_OPTIONS = [
  { value: 'STANDARD',  label: 'Standard (pre-approved)' },
  { value: 'NORMAL',    label: 'Normal (CAB review)' },
  { value: 'EMERGENCY', label: 'Emergency' },
];
export const CATEGORY_OPTIONS = [
  'INFRASTRUCTURE', 'APPLICATION', 'NETWORK', 'SECURITY', 'POLICY',
  'PROCESS', 'COMPLIANCE_REMEDIATION', 'EMERGENCY_FIX', 'DATA', 'THIRD_PARTY',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
export const RISK_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(v => ({ value: v, label: v }));

// S7d/CE-7 added CAPA + FOLLOW_UP. The server auto-creates exactly that pair when a PIR follow-up
// is recorded, and change_links has no CHECK on either column — so without them here the register
// could render a link the user had no way to create, which reads as an inconsistency rather than as
// the deliberate asymmetry it would be.
export const ENTITY_TYPE_OPTIONS = [
  'RISK', 'CONTROL', 'INCIDENT', 'VENDOR', 'BCM_PLAN', 'AUDIT_FINDING',
  'POLICY', 'REGULATORY_CHANGE', 'DPIA', 'KRI', 'ASSET', 'EVIDENCE', 'CAPA',
].map(v => ({ value: v, label: v.replace(/_/g, ' ') }));
export const LINK_TYPE_OPTIONS = ['AFFECTS', 'TRIGGERED_BY', 'MITIGATES', 'TARGETS', 'REMEDIATES', 'DEPENDS_ON', 'INFORMS', 'FOLLOW_UP']
  .map(v => ({ value: v, label: v.replace(/_/g, ' ') }));

export const EMPTY: Partial<ChangeRequestDto> = {
  changeType: 'NORMAL', riskLevel: 'MEDIUM', category: 'INFRASTRUCTURE',
  titleEn: '', titleAr: '', descriptionEn: '',
  impactSummary: '', rollbackPlan: '', testPlan: '', implementationPlan: '',
  businessJustification: '', requestedByName: '', changeOwnerName: '',
  scheduledStart: undefined, scheduledEnd: undefined,
};
