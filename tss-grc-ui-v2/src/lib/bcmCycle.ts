// BCM cycle / governance / risk / strategy / contacts / remediation
// client — typed wrappers around the BCM-1, BCM-2, and BCM-3 REST
// surfaces. Mirrors the backend Java records.

import { api } from './api';
import { bilingual } from './statusLabels';

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

// ─── BCM domain label catalogs ────────────────────────────────────
//
// Threat categories, continuity-strategy types and review frequencies
// are BCM-specific vocabularies, so their bilingual labels live here
// next to the type defs rather than bloating the shared statusLabels
// table. Lookup reuses bilingual() so matching + English fallback behave
// exactly like the status badges (case-insensitive, '-'/' ' → '_').

const THREAT_CATEGORY_LABELS = {
  TECH_FAILURE:             { en: 'Tech failure',             ar: 'عطل تقني' },
  CYBERATTACK:              { en: 'Cyberattack',              ar: 'هجوم سيبراني' },
  DATA_CENTER_OUTAGE:       { en: 'Data centre outage',       ar: 'تعطّل مركز البيانات' },
  CLOUD_OUTAGE:             { en: 'Cloud outage',             ar: 'تعطّل الخدمات السحابية' },
  TELECOM_OUTAGE:           { en: 'Telecom outage',           ar: 'انقطاع الاتصالات' },
  POWER_FAILURE:            { en: 'Power failure',            ar: 'انقطاع الكهرباء' },
  FACILITY_UNAVAILABILITY:  { en: 'Facility unavailability',  ar: 'تعذّر استخدام المقر' },
  WORKFORCE_UNAVAILABILITY: { en: 'Workforce unavailability', ar: 'نقص القوى العاملة' },
  SUPPLIER_FAILURE:         { en: 'Supplier failure',         ar: 'إخفاق المورّد' },
  PANDEMIC:                 { en: 'Pandemic',                 ar: 'جائحة' },
  NATURAL_HAZARD:           { en: 'Natural hazard',           ar: 'خطر طبيعي' },
  FIRE:                     { en: 'Fire',                     ar: 'حريق' },
  FLOOD:                    { en: 'Flood',                    ar: 'فيضان' },
  REGIONAL_DISRUPTION:      { en: 'Regional disruption',      ar: 'اضطراب إقليمي' },
  REGULATORY_DISRUPTION:    { en: 'Regulatory disruption',    ar: 'اضطراب تنظيمي' },
  DATA_LOSS:                { en: 'Data loss',                ar: 'فقدان البيانات' },
  THIRD_PARTY_DEPENDENCY:   { en: 'Third-party dependency',   ar: 'اعتماد على طرف ثالث' },
  CRITICAL_INFRASTRUCTURE:  { en: 'Critical infrastructure',  ar: 'بنية تحتية حرجة' },
} satisfies Record<ThreatCategory, { en: string; ar: string }>;

/** Bilingual label for a BCM threat category. */
export function threatCategoryLabel(c: string | null | undefined, lang: string): string {
  return bilingual(THREAT_CATEGORY_LABELS, c, lang);
}

const STRATEGY_TYPE_LABELS = {
  ACTIVE_ACTIVE:            { en: 'Active-active',            ar: 'نشط-نشط' },
  ACTIVE_PASSIVE:           { en: 'Active-passive',           ar: 'نشط-احتياطي' },
  BACKUP_SITE:              { en: 'Backup site',              ar: 'موقع بديل' },
  ALTERNATE_WORKPLACE:      { en: 'Alternate workplace',      ar: 'مقر عمل بديل' },
  REMOTE_WORK:              { en: 'Remote work',              ar: 'العمل عن بُعد' },
  MANUAL_WORKAROUND:        { en: 'Manual workaround',        ar: 'حل يدوي بديل' },
  ALTERNATE_SUPPLIER:       { en: 'Alternate supplier',       ar: 'مورّد بديل' },
  STOCKPILING:              { en: 'Stockpiling',              ar: 'تخزين احتياطي' },
  CLOUD_FAILOVER:           { en: 'Cloud failover',           ar: 'تحويل سحابي' },
  DATA_REPLICATION:         { en: 'Data replication',         ar: 'تكرار البيانات' },
  MUTUAL_AID:               { en: 'Mutual aid',               ar: 'مساعدة متبادلة' },
  PROCESS_TRANSFER:         { en: 'Process transfer',         ar: 'نقل العملية' },
  WORKFORCE_CROSS_TRAINING: { en: 'Workforce cross-training', ar: 'تدريب متعدد للكوادر' },
  EMERGENCY_PROCUREMENT:    { en: 'Emergency procurement',    ar: 'شراء طارئ' },
  CRISIS_COMMUNICATION:     { en: 'Crisis communication',     ar: 'تواصل الأزمات' },
} satisfies Record<StrategyType, { en: string; ar: string }>;

/** Bilingual label for a continuity-strategy type. */
export function strategyTypeLabel(t: string | null | undefined, lang: string): string {
  return bilingual(STRATEGY_TYPE_LABELS, t, lang);
}

const REVIEW_FREQUENCY_LABELS = {
  ANNUAL:       { en: 'Annual',       ar: 'سنوي' },
  SEMI_ANNUAL:  { en: 'Semi-annual',  ar: 'نصف سنوي' },
  QUARTERLY:    { en: 'Quarterly',    ar: 'ربع سنوي' },
  ON_CHANGE:    { en: 'On change',    ar: 'عند التغيير' },
} satisfies Record<NonNullable<BcmGovernanceRecord['reviewFrequency']>, { en: string; ar: string }>;

/** Bilingual label for a governance review frequency. */
export function reviewFrequencyLabel(f: string | null | undefined, lang: string): string {
  return bilingual(REVIEW_FREQUENCY_LABELS, f, lang);
}

// ─── Lessons / Gaps / CAPA — audit Tier-1 #4 ──────────────────────
//
// Backend (BcmRemediationService + BcmRemediationController) was shipped in
// BCM-3 but never had frontend wiring. These wrappers + register page
// (BcmRemediationPage) close that loop.

export type LessonStatus = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'CLOSED';
export type LessonSource = 'INCIDENT' | 'CRISIS' | 'EXERCISE' | 'AUDIT' | 'MANUAL';
export type GapStatus  = BcmGap['status'];
export type CapaStatus = BcmCorrectiveAction['status'];

export interface BcmLessonLearned {
  id: number;
  cycleId?: number;
  sourceType: LessonSource;
  sourceId?: number;
  titleEn: string;
  titleAr?: string;
  description?: string;
  rootCause?: string;
  recommendation?: string;
  relatedPlanId?: number;
  relatedRiskId?: number;
  correctiveActionId?: number;
  ownerId?: number;
  status: LessonStatus;
}

// Lessons
export function listLessons(status?: LessonStatus) {
  const q = status ? `?status=${status}` : '';
  return api.get<BcmLessonLearned[]>(`${BASE}/lessons${q}`);
}
export function createLesson(b: Partial<BcmLessonLearned>)         { return api.post<BcmLessonLearned>(`${BASE}/lessons`, b); }
export function transitionLesson(id: number, to: LessonStatus)     { return api.post<BcmLessonLearned>(`${BASE}/lessons/${id}/transition`, { to }); }

// Gaps
export function listGaps(opts: { status?: GapStatus; severity?: BcmGap['severity']; cycleId?: number } = {}) {
  const qs = new URLSearchParams();
  if (opts.status)   qs.set('status',   opts.status);
  if (opts.severity) qs.set('severity', opts.severity);
  if (opts.cycleId)  qs.set('cycleId',  String(opts.cycleId));
  const q = qs.toString();
  return api.get<BcmGap[]>(`${BASE}/gaps${q ? `?${q}` : ''}`);
}
export function createGap(b: Partial<BcmGap>)                  { return api.post<BcmGap>(`${BASE}/gaps`, b); }
export function transitionGap(id: number, to: GapStatus)       { return api.post<BcmGap>(`${BASE}/gaps/${id}/transition`, { to }); }

// CAPAs
export function listCapas(opts: { status?: CapaStatus; gapId?: number } = {}) {
  const qs = new URLSearchParams();
  if (opts.status) qs.set('status', opts.status);
  if (opts.gapId)  qs.set('gapId',  String(opts.gapId));
  const q = qs.toString();
  return api.get<BcmCorrectiveAction[]>(`${BASE}/capas${q ? `?${q}` : ''}`);
}
export function createCapa(b: Partial<BcmCorrectiveAction>)               { return api.post<BcmCorrectiveAction>(`${BASE}/capas`, b); }
export function updateCapa(id: number, b: Partial<BcmCorrectiveAction>)   { return api.put<BcmCorrectiveAction>(`${BASE}/capas/${id}`, b); }
export function transitionCapa(id: number, to: CapaStatus, evidenceId?: number) {
  return api.post<BcmCorrectiveAction>(`${BASE}/capas/${id}/transition`, { to, evidenceId });
}
/**
 * Backend returns `{flippedToOverdue: n}` per BcmRemediationController;
 * the earlier client typed it as `flagged` and the page consumer read
 * `out.flagged`, leaving the user with "Flagged undefined CAPAs as overdue".
 * Type now matches the wire format.
 */
export function flagOverdueCapas() { return api.post<{ flippedToOverdue: number }>(`${BASE}/capas/flag-overdue`, {}); }
export function remediationStats() {
  return api.get<{
    openGaps: number; criticalGaps: number;
    openCapas: number; overdueCapas: number; closedCapas: number;
  }>(`${BASE}/remediation/stats`);
}

// ── Tone helpers
export function gapStatusTone(s: GapStatus): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'OPEN':          return 'warning';
    case 'ASSIGNED':      return 'info';
    case 'IN_PROGRESS':   return 'info';
    case 'UNDER_REVIEW':  return 'info';
    case 'CLOSED':        return 'success';
    case 'ACCEPTED_RISK': return 'warning';
    case 'REOPENED':      return 'danger';
  }
}

export function severityTone(s: BcmGap['severity']): 'success' | 'info' | 'warning' | 'danger' {
  switch (s) {
    case 'LOW':      return 'success';
    case 'MEDIUM':   return 'info';
    case 'HIGH':     return 'warning';
    case 'CRITICAL': return 'danger';
  }
}

export function capaStatusTone(s: CapaStatus): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'OPEN':                return 'warning';
    case 'ASSIGNED':            return 'info';
    case 'IN_PROGRESS':         return 'info';
    case 'EVIDENCE_SUBMITTED':  return 'info';
    case 'UNDER_REVIEW':        return 'info';
    case 'CLOSED':              return 'success';
    case 'OVERDUE':             return 'danger';
    case 'REOPENED':            return 'danger';
  }
}
