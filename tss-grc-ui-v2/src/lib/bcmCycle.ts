// BCM cycle / governance / risk / strategy / contacts / remediation
// client — typed wrappers around the BCM-1, BCM-2, and BCM-3 REST
// surfaces. Mirrors the backend Java records.

import { api } from './api';

// ─── Cycles ───────────────────────────────────────────────────────

export type CycleStatus = 'DRAFT' | 'IN_PROGRESS' | 'UNDER_REVIEW' | 'APPROVED' | 'ARCHIVED';

export interface BcmAssessmentCycle {
  id: number;
  organisationId?: number;
  cycleNameEn: string;
  cycleNameAr?: string;
  reportingYear: number;
  startDate?: string;
  endDate?: string;
  scope?: string;
  bcmManagerId?: number;
  executiveSponsorId?: number;
  status: CycleStatus;
  overallScore?: number;
  biaCompletionPercent?: number;
  bcpCompletionPercent?: number;
  drpCompletionPercent?: number;
  evidenceCompletionPercent?: number;
  openGapCount: number;
  criticalGapCount: number;
  approvedBy?: string;
  approvedAt?: string;
}

// ─── Governance ───────────────────────────────────────────────────

export type GovernanceStatus =
  | 'DRAFT' | 'UNDER_REVIEW' | 'CHANGES_REQUIRED'
  | 'APPROVED' | 'ACTIVE' | 'REVIEW_DUE' | 'ARCHIVED';

export interface BcmGovernanceRecord {
  id: number;
  organisationId?: number;
  policyTitleEn: string;
  policyTitleAr?: string;
  policyVersion: string;
  scope?: string;
  objective?: string;
  bcmManagerId?: number;
  executiveSponsorId?: number;
  committeeId?: number;
  reviewFrequency?: 'ANNUAL' | 'SEMI_ANNUAL' | 'QUARTERLY' | 'ON_CHANGE';
  lastReviewDate?: string;
  nextReviewDate?: string;
  evidenceId?: number;
  approvalStatus: GovernanceStatus;
  approvedBy?: string;
  approvedAt?: string;
}

// ─── BCM risk ─────────────────────────────────────────────────────

export type BcmRiskStatus =
  | 'DRAFT' | 'ASSESSED' | 'TREATMENT_PLANNED'
  | 'ACCEPTED' | 'MITIGATED' | 'CLOSED';

export type ThreatCategory =
  | 'TECH_FAILURE' | 'CYBERATTACK' | 'DATA_CENTER_OUTAGE' | 'CLOUD_OUTAGE'
  | 'TELECOM_OUTAGE' | 'POWER_FAILURE' | 'FACILITY_UNAVAILABILITY'
  | 'WORKFORCE_UNAVAILABILITY' | 'SUPPLIER_FAILURE' | 'PANDEMIC'
  | 'NATURAL_HAZARD' | 'FIRE' | 'FLOOD' | 'REGIONAL_DISRUPTION'
  | 'REGULATORY_DISRUPTION' | 'DATA_LOSS' | 'THIRD_PARTY_DEPENDENCY'
  | 'CRITICAL_INFRASTRUCTURE';

export interface BcmRisk {
  id: number;
  cycleId?: number;
  relatedServiceId?: number;
  relatedProcessId?: number;
  relatedSupplierId?: number;
  titleEn: string;
  titleAr?: string;
  cause?: string;
  event?: string;
  consequence?: string;
  threatCategory: ThreatCategory;
  existingControls?: string;
  likelihood?: number;
  impact?: number;
  inherentRiskScore?: number;
  controlEffectiveness?: 'EFFECTIVE' | 'NEEDS_IMPROVEMENT' | 'INEFFECTIVE' | 'NOT_TESTED';
  residualRiskScore?: number;
  riskOwnerId?: number;
  relatedErmRiskId?: number;
  status: BcmRiskStatus;
}

// ─── Continuity strategy ──────────────────────────────────────────

export type StrategyType =
  | 'ACTIVE_ACTIVE' | 'ACTIVE_PASSIVE' | 'BACKUP_SITE' | 'ALTERNATE_WORKPLACE'
  | 'REMOTE_WORK' | 'MANUAL_WORKAROUND' | 'ALTERNATE_SUPPLIER' | 'STOCKPILING'
  | 'CLOUD_FAILOVER' | 'DATA_REPLICATION' | 'MUTUAL_AID' | 'PROCESS_TRANSFER'
  | 'WORKFORCE_CROSS_TRAINING' | 'EMERGENCY_PROCUREMENT' | 'CRISIS_COMMUNICATION';

export type StrategyApproval = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';

export interface ContinuityStrategy {
  id: number;
  cycleId?: number;
  relatedServiceId?: number;
  relatedProcessId?: number;
  rtoRequirementHours?: number;
  rpoRequirementHours?: number;
  selectedStrategy: StrategyType;
  strategyDescriptionEn?: string;
  strategyDescriptionAr?: string;
  costEstimate?: number;
  feasibility?: 'HIGH' | 'MEDIUM' | 'LOW';
  residualRisk?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  ownerId?: number;
  evidenceId?: number;
  approvalStatus: StrategyApproval;
  implementationStatus?: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'ON_HOLD';
}

// ─── Communication contact (BCM-2) ────────────────────────────────

export interface BcmCommunicationContact {
  id: number;
  name: string;
  roleTitle?: string;
  departmentId?: number;
  primaryPhone?: string;
  secondaryPhone?: string;
  email?: string;
  escalationLevel: number;
  relatedPlanId?: number;
  availabilityStatus: 'AVAILABLE' | 'ON_LEAVE' | 'UNAVAILABLE' | 'BACKUP';
  lastTestedDate?: string;
  lastTestResult?: 'PASSED' | 'FAILED' | 'NOT_REACHED';
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
}

// ─── Gap + CAPA (BCM-3) ───────────────────────────────────────────

export interface BcmGap {
  id: number;
  cycleId?: number;
  source: 'BIA' | 'BCP' | 'DRP' | 'EXERCISE' | 'CALL_TREE' | 'AUDIT' | 'RISK' | 'MANUAL';
  titleEn: string;
  titleAr?: string;
  description?: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  dueDate?: string;
  status: 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'UNDER_REVIEW' | 'CLOSED' | 'ACCEPTED_RISK' | 'REOPENED';
}

export interface BcmCorrectiveAction {
  id: number;
  cycleId?: number;
  gapId?: number;
  titleEn: string;
  titleAr?: string;
  actionPlan?: string;
  ownerId?: number;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  dueDate?: string;
  progressPercent: number;
  evidenceId?: number;
  closureDate?: string;
  status: 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'EVIDENCE_SUBMITTED' | 'UNDER_REVIEW' | 'CLOSED' | 'OVERDUE' | 'REOPENED';
}

// ─── REST clients ─────────────────────────────────────────────────

const BASE = '/api/v1/bcm';

export function listCycles(status?: CycleStatus) {
  const q = status ? `?status=${status}` : '';
  return api.get<BcmAssessmentCycle[]>(`${BASE}/cycles${q}`);
}
export function getCycle(id: number)          { return api.get<BcmAssessmentCycle>(`${BASE}/cycles/${id}`); }
export function createCycle(b: Partial<BcmAssessmentCycle>)     { return api.post<BcmAssessmentCycle>(`${BASE}/cycles`, b); }
export function updateCycle(id: number, b: Partial<BcmAssessmentCycle>) { return api.put<BcmAssessmentCycle>(`${BASE}/cycles/${id}`, b); }
export function transitionCycle(id: number, to: CycleStatus)    { return api.post<BcmAssessmentCycle>(`${BASE}/cycles/${id}/transition`, { to }); }

export function listGovernance(approvalStatus?: GovernanceStatus) {
  const q = approvalStatus ? `?approvalStatus=${approvalStatus}` : '';
  return api.get<BcmGovernanceRecord[]>(`${BASE}/governance${q}`);
}
export function createGovernance(b: Partial<BcmGovernanceRecord>)         { return api.post<BcmGovernanceRecord>(`${BASE}/governance`, b); }
export function updateGovernance(id: number, b: Partial<BcmGovernanceRecord>) { return api.put<BcmGovernanceRecord>(`${BASE}/governance/${id}`, b); }
export function transitionGovernance(id: number, to: GovernanceStatus)    { return api.post<BcmGovernanceRecord>(`${BASE}/governance/${id}/transition`, { to }); }

export function listRisks(opts: { cycleId?: number; serviceId?: number; status?: BcmRiskStatus } = {}) {
  const qs = new URLSearchParams();
  if (opts.cycleId)   qs.set('cycleId',   String(opts.cycleId));
  if (opts.serviceId) qs.set('serviceId', String(opts.serviceId));
  if (opts.status)    qs.set('status',    opts.status);
  const q = qs.toString();
  return api.get<BcmRisk[]>(`${BASE}/risks${q ? `?${q}` : ''}`);
}
export function createRisk(b: Partial<BcmRisk>)            { return api.post<BcmRisk>(`${BASE}/risks`, b); }
export function updateRisk(id: number, b: Partial<BcmRisk>) { return api.put<BcmRisk>(`${BASE}/risks/${id}`, b); }
export function transitionRisk(id: number, to: BcmRiskStatus) { return api.post<BcmRisk>(`${BASE}/risks/${id}/transition`, { to }); }
export function deleteRisk(id: number)                       { return api.delete<void>(`${BASE}/risks/${id}`); }

export function listStrategies(opts: { cycleId?: number; serviceId?: number; approvalStatus?: StrategyApproval } = {}) {
  const qs = new URLSearchParams();
  if (opts.cycleId)        qs.set('cycleId',        String(opts.cycleId));
  if (opts.serviceId)      qs.set('serviceId',      String(opts.serviceId));
  if (opts.approvalStatus) qs.set('approvalStatus', opts.approvalStatus);
  const q = qs.toString();
  return api.get<ContinuityStrategy[]>(`${BASE}/strategies${q ? `?${q}` : ''}`);
}
export function createStrategy(b: Partial<ContinuityStrategy>)            { return api.post<ContinuityStrategy>(`${BASE}/strategies`, b); }
export function transitionStrategy(id: number, to: StrategyApproval)      { return api.post<ContinuityStrategy>(`${BASE}/strategies/${id}/transition`, { to }); }

export function listContacts(opts: { planId?: number; status?: string } = {}) {
  const qs = new URLSearchParams();
  if (opts.planId) qs.set('planId', String(opts.planId));
  if (opts.status) qs.set('status', opts.status);
  const q = qs.toString();
  return api.get<BcmCommunicationContact[]>(`${BASE}/contacts${q ? `?${q}` : ''}`);
}
export function createContact(b: Partial<BcmCommunicationContact>) { return api.post<BcmCommunicationContact>(`${BASE}/contacts`, b); }
export function recordCallTreeTest(id: number, result: 'PASSED' | 'FAILED' | 'NOT_REACHED', notes?: string) {
  return api.post<BcmCommunicationContact>(`${BASE}/contacts/${id}/test-result`, { result, notes });
}
export function contactTestStats() {
  return api.get<{ passed: number; failed: number; notReached: number }>(`${BASE}/contacts/test-stats`);
}

// ─── UI helpers ───────────────────────────────────────────────────

export function cycleStatusTone(s: CycleStatus): 'neutral' | 'info' | 'warning' | 'success' {
  switch (s) {
    case 'DRAFT':         return 'neutral';
    case 'IN_PROGRESS':   return 'info';
    case 'UNDER_REVIEW':  return 'warning';
    case 'APPROVED':      return 'success';
    case 'ARCHIVED':      return 'neutral';
  }
}

export function governanceStatusTone(s: GovernanceStatus): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'DRAFT':            return 'neutral';
    case 'UNDER_REVIEW':     return 'info';
    case 'CHANGES_REQUIRED': return 'warning';
    case 'APPROVED':         return 'success';
    case 'ACTIVE':           return 'success';
    case 'REVIEW_DUE':       return 'warning';
    case 'ARCHIVED':         return 'neutral';
  }
}

export function bcmRiskStatusTone(s: BcmRiskStatus): 'neutral' | 'info' | 'warning' | 'success' {
  switch (s) {
    case 'DRAFT':              return 'neutral';
    case 'ASSESSED':           return 'info';
    case 'TREATMENT_PLANNED':  return 'warning';
    case 'ACCEPTED':           return 'info';
    case 'MITIGATED':          return 'success';
    case 'CLOSED':             return 'neutral';
  }
}

export function residualToTone(score?: number): 'success' | 'warning' | 'danger' {
  if (score == null) return 'success';
  if (score >= 15) return 'danger';
  if (score >= 8)  return 'warning';
  return 'success';
}
