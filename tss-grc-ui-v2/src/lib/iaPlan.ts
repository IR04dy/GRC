// Internal Audit Annual Plan + Engagement API client — Sprint 13.
//
// Wires the React frontend to the V194 module's REST surface
// (IaController under /api/v1/ia/*).
//
// Shapes mirror the backend DTOs exactly:
//   IaDtos.PlanResponse              → IaPlan
//   IaDtos.PlanDashboardResponse     → IaPlanDashboard
//   IaDtos.UniverseResponse          → IaUniverseEntry
//   IaDtos.EngagementResponse        → IaEngagement
//   IaDtos.MilestoneResponse         → IaEngagementMilestone

import { api } from './api';

export type PlanStatus =
  | 'DRAFT' | 'BOARD_REVIEW' | 'APPROVED'
  | 'EXECUTING' | 'COMPLETED' | 'ARCHIVED';

export type EngagementPhase =
  | 'PLANNED' | 'PLANNING' | 'FIELDWORK' | 'REPORTING'
  | 'REPORT_ISSUED' | 'FOLLOW_UP' | 'CLOSED' | 'CANCELLED';

export type EngagementType =
  | 'OPERATIONAL' | 'FINANCIAL' | 'COMPLIANCE' | 'IT_GENERAL'
  | 'IT_APPLICATION' | 'FRAUD' | 'SPECIAL_REQUEST'
  | 'FOLLOW_UP' | 'MANAGEMENT_REVIEW';

export type EngagementOpinion = 'SATISFACTORY' | 'PARTIAL' | 'UNSATISFACTORY' | 'NA';

export type MilestoneType =
  | 'KICKOFF' | 'FIELDWORK_START' | 'DRAFT_REPORT'
  | 'MANAGEMENT_RESPONSE' | 'FINAL_REPORT' | 'FOLLOW_UP_DUE';

export interface IaPlan {
  id: number;
  externalId: string;
  organisationId: number;
  planCode: string;
  cycleYear: number;
  titleEn: string;
  titleAr: string;
  preparedByUserId: number | null;
  status: PlanStatus;
  methodologySummaryEn: string | null;
  methodologySummaryAr: string | null;
  auditUniverseCount: number;
  totalEngagementsPlanned: number;
  approvedByUserId: number | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface IaPlanDashboard {
  planId: number;
  planCode: string;
  cycleYear: number;
  status: PlanStatus;
  auditUniverseCount: number;
  totalEngagementsPlanned: number;
  engagementsPlanned: number;
  engagementsInPlanning: number;
  engagementsInFieldwork: number;
  engagementsInReporting: number;
  engagementsReportIssued: number;
  engagementsInFollowUp: number;
  engagementsClosed: number;
  engagementsCancelled: number;
  onTimePercentage: number | null;
}

export interface IaUniverseEntry {
  id: number;
  externalId: string;
  organisationId: number;
  planId: number | null;
  entryCode: string;
  titleEn: string;
  titleAr: string;
  processNameEn: string | null;
  processNameAr: string | null;
  businessUnitEn: string | null;
  businessUnitAr: string | null;
  inherentRiskScore: number | null;
  controlEnvironmentScore: number | null;
  residualRiskScore: number | null;
  lastAuditedDate: string | null;
  lastAuditRating: 'SATISFACTORY' | 'PARTIAL' | 'UNSATISFACTORY' | null;
  plannedAuditYear: number | null;
  plannedQuarter: 'Q1' | 'Q2' | 'Q3' | 'Q4' | null;
  linkedRiskId: number | null;
  linkedControlIds: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface IaEngagementMilestone {
  id: number;
  engagementId: number;
  milestoneType: MilestoneType;
  plannedDate: string | null;
  actualDate: string | null;
  completedByUserId: number | null;
  notesEn: string | null;
  notesAr: string | null;
  createdAt: string;
}

export interface IaEngagement {
  id: number;
  externalId: string;
  organisationId: number;
  planId: number;
  universeEntryId: number | null;
  engagementCode: string;
  titleEn: string;
  titleAr: string;
  auditType: EngagementType;
  scopeSummaryEn: string | null;
  scopeSummaryAr: string | null;
  leadAuditorUserId: number | null;
  teamMemberIds: string | null;
  sponsorUserId: number | null;
  plannedStartDate: string | null;
  plannedEndDate: string | null;
  actualStartDate: string | null;
  actualEndDate: string | null;
  phase: EngagementPhase;
  planningMemoUrl: string | null;
  planningMemoSignedOffAt: string | null;
  opinion: EngagementOpinion | null;
  findingCount: number;
  criticalFindingCount: number;
  milestones: IaEngagementMilestone[];
  createdAt: string;
  updatedAt: string | null;
}

export interface PlanCreateRequest {
  planCode?: string;
  cycleYear: number;
  titleEn: string;
  titleAr: string;
  preparedByUserId?: number;
  methodologySummaryEn?: string;
  methodologySummaryAr?: string;
}

export interface PlanTransitionRequest {
  newStatus: PlanStatus;
  approverUserId?: number;
  noteEn?: string;
  noteAr?: string;
}

export interface EngagementCreateRequest {
  planId: number;
  universeEntryId?: number | null;
  engagementCode?: string;
  titleEn: string;
  titleAr: string;
  auditType: EngagementType;
  scopeSummaryEn?: string;
  scopeSummaryAr?: string;
  leadAuditorUserId?: number;
  teamMemberIds?: string;
  sponsorUserId?: number;
  plannedStartDate?: string;
  plannedEndDate?: string;
}

export interface EngagementCreateFromUniverseRequest {
  planId: number;
  universeEntryId: number;
  engagementCode?: string;
  auditType?: EngagementType;
  leadAuditorUserId?: number;
  sponsorUserId?: number;
  plannedStartDate?: string;
  plannedEndDate?: string;
}

export interface EngagementPhaseTransitionRequest {
  newPhase: EngagementPhase;
  actorUserId?: number;
  noteEn?: string;
  noteAr?: string;
}

export interface MilestoneRecordRequest {
  milestoneType: MilestoneType;
  actualDate: string;
  completedByUserId?: number;
  notesEn?: string;
  notesAr?: string;
}

const BASE = '/api/v1/ia';

/** One auditable entity for the universe bulk-import (subset of UniverseCreateRequest). */
export interface UniverseEntryCreate {
  entryCode: string;
  titleEn: string;
  titleAr: string;
  processNameEn?: string;
  businessUnitEn?: string;
  inherentRiskScore?: number;
  controlEnvironmentScore?: number;
  plannedAuditYear?: number;
  plannedQuarter?: string;
}
export interface UniverseBulkImportRequest {
  planId?: number;
  entries: UniverseEntryCreate[];
}
export interface UniverseBulkImportResponse {
  totalSubmitted: number;
  created: number;
  skippedExisting: number;
}

export const iaApi = {
  // Plans
  listPlans:    () => api.get<IaPlan[]>(`${BASE}/plan`),
  getPlan:      (id: number) => api.get<IaPlan>(`${BASE}/plan/${id}`),
  createPlan:   (req: PlanCreateRequest) => api.post<IaPlan>(`${BASE}/plan`, req),
  transitionPlan: (id: number, req: PlanTransitionRequest) =>
    api.post<IaPlan>(`${BASE}/plan/${id}/transition`, req),
  planDashboard: (id: number) => api.get<IaPlanDashboard>(`${BASE}/plan/${id}/dashboard`),

  // Universe
  listAllUniverse:    () => api.get<IaUniverseEntry[]>(`${BASE}/universe`),
  listMasterUniverse: () => api.get<IaUniverseEntry[]>(`${BASE}/universe/master`),
  listUniverseForPlan: (planId: number) => api.get<IaUniverseEntry[]>(`${BASE}/universe/by-plan/${planId}`),
  /** Re-run inherent × (1 − control-environment strength) → residual for one entry. */
  recomputeUniverseResidual: (id: number) => api.post<IaUniverseEntry>(`${BASE}/universe/${id}/recompute-residual`),
  /** Load a batch of auditable entities into the universe (IF-NOT-EXISTS on entryCode). */
  bulkImportUniverse: (req: UniverseBulkImportRequest) =>
    api.post<UniverseBulkImportResponse>(`${BASE}/universe/bulk-import`, req),

  // Engagements
  listAllEngagements: () => api.get<IaEngagement[]>(`${BASE}/engagements`),
  listEngagementsForPlan: (planId: number) => api.get<IaEngagement[]>(`${BASE}/engagements/by-plan/${planId}`),
  getEngagement: (id: number) => api.get<IaEngagement>(`${BASE}/engagements/${id}`),
  createEngagement: (req: EngagementCreateRequest) =>
    api.post<IaEngagement>(`${BASE}/engagements`, req),
  createFromUniverse: (req: EngagementCreateFromUniverseRequest) =>
    api.post<IaEngagement>(`${BASE}/engagements/from-universe`, req),
  transitionEngagement: (id: number, req: EngagementPhaseTransitionRequest) =>
    api.post<IaEngagement>(`${BASE}/engagements/${id}/transition`, req),
  recordMilestone: (engagementId: number, req: MilestoneRecordRequest) =>
    api.post<IaEngagementMilestone>(`${BASE}/engagements/${engagementId}/milestones/record`, req),
};

// ── Display helpers ─────────────────────────────────────────────────

export function planStatusLabel(s: PlanStatus, isAr: boolean): string {
  if (isAr) {
    const m: Record<PlanStatus, string> = {
      DRAFT: 'مسوّدة',
      BOARD_REVIEW: 'قيد مراجعة المجلس',
      APPROVED: 'مُعتمد',
      EXECUTING: 'قيد التنفيذ',
      COMPLETED: 'مُنجَز',
      ARCHIVED: 'مُؤرشَف',
    };
    return m[s];
  }
  const m: Record<PlanStatus, string> = {
    DRAFT: 'Draft',
    BOARD_REVIEW: 'Board review',
    APPROVED: 'Approved',
    EXECUTING: 'Executing',
    COMPLETED: 'Completed',
    ARCHIVED: 'Archived',
  };
  return m[s];
}

export function engagementPhaseLabel(p: EngagementPhase, isAr: boolean): string {
  if (isAr) {
    const m: Record<EngagementPhase, string> = {
      PLANNED: 'مُجدوَل',
      PLANNING: 'مرحلة التخطيط',
      FIELDWORK: 'العمل الميداني',
      REPORTING: 'مرحلة كتابة التقرير',
      REPORT_ISSUED: 'صدر التقرير',
      FOLLOW_UP: 'المتابعة',
      CLOSED: 'مُغلَق',
      CANCELLED: 'ملغى',
    };
    return m[p];
  }
  const m: Record<EngagementPhase, string> = {
    PLANNED: 'Planned',
    PLANNING: 'Planning',
    FIELDWORK: 'Fieldwork',
    REPORTING: 'Reporting',
    REPORT_ISSUED: 'Report issued',
    FOLLOW_UP: 'Follow-up',
    CLOSED: 'Closed',
    CANCELLED: 'Cancelled',
  };
  return m[p];
}

export function engagementTypeLabel(t: EngagementType, isAr: boolean): string {
  if (isAr) {
    const m: Record<EngagementType, string> = {
      OPERATIONAL: 'تشغيلية',
      FINANCIAL: 'مالية',
      COMPLIANCE: 'الالتزام',
      IT_GENERAL: 'ضوابط تقنية المعلومات العامة',
      IT_APPLICATION: 'ضوابط تطبيقات تقنية المعلومات',
      FRAUD: 'الاحتيال',
      SPECIAL_REQUEST: 'طلب خاص',
      FOLLOW_UP: 'متابعة',
      MANAGEMENT_REVIEW: 'مراجعة إدارية',
    };
    return m[t];
  }
  const m: Record<EngagementType, string> = {
    OPERATIONAL: 'Operational',
    FINANCIAL: 'Financial',
    COMPLIANCE: 'Compliance',
    IT_GENERAL: 'IT general',
    IT_APPLICATION: 'IT application',
    FRAUD: 'Fraud',
    SPECIAL_REQUEST: 'Special request',
    FOLLOW_UP: 'Follow-up',
    MANAGEMENT_REVIEW: 'Management review',
  };
  return m[t];
}

export function milestoneTypeLabel(m: MilestoneType, isAr: boolean): string {
  if (isAr) {
    const map: Record<MilestoneType, string> = {
      KICKOFF: 'اجتماع الانطلاق',
      FIELDWORK_START: 'بدء العمل الميداني',
      DRAFT_REPORT: 'مسوّدة التقرير',
      MANAGEMENT_RESPONSE: 'ردّ الإدارة',
      FINAL_REPORT: 'التقرير النهائي',
      FOLLOW_UP_DUE: 'موعد المتابعة',
    };
    return map[m];
  }
  const map: Record<MilestoneType, string> = {
    KICKOFF: 'Kickoff',
    FIELDWORK_START: 'Fieldwork start',
    DRAFT_REPORT: 'Draft report',
    MANAGEMENT_RESPONSE: 'Management response',
    FINAL_REPORT: 'Final report',
    FOLLOW_UP_DUE: 'Follow-up due',
  };
  return map[m];
}

/**
 * Residual-risk heat band on the 0-25 IA scale.
 * Low ≤ 8, Medium 8-15, High 15-20, Critical > 20.
 */
export function residualBand(score: number | null): 'low' | 'medium' | 'high' | 'critical' | 'unscored' {
  if (score == null) return 'unscored';
  if (score >= 20) return 'critical';
  if (score >= 15) return 'high';
  if (score >= 8)  return 'medium';
  return 'low';
}
