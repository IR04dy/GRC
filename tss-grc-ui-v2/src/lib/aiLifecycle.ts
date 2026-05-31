// AI/ML Model Lifecycle — Sprint 31 frontend.
//
// Backend: PR #268 (com.tss.grc.module.ai.lifecycle).
// F8 + seed: PR #269 (AR-271..AR-280).
//
// Extends Sprint 11 MlModel registry with independent validation events,
// retraining cycles, periodic recertification, SDAIA AI Ethics annual
// attestation.
//
// Saudi-native bilingual sidebar:
//   ar : دورة حياة نماذج الذكاء الاصطناعي
//   en : AI Model Lifecycle

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type ValidationStatus =
  | 'SCHEDULED' | 'IN_PROGRESS' | 'PASSED' | 'PASSED_WITH_CONDITIONS'
  | 'FAILED' | 'REMEDIATION';

export type RetrainingStatus =
  | 'PROPOSED' | 'APPROVED' | 'IN_PROGRESS' | 'DEPLOYED'
  | 'ROLLED_BACK' | 'REJECTED' | 'ABANDONED';

export type RetrainingTrigger = 'DRIFT' | 'SCHEDULED' | 'MANUAL' | 'POST_INCIDENT';

export type RecertificationStatus =
  | 'DUE' | 'IN_REVIEW' | 'RECERTIFIED' | 'RECERTIFIED_WITH_CONDITIONS' | 'REVOKED' | 'EXPIRED';

// ─── Saudi-native bilingual labels ───────────────────────────────

export const VALIDATION_STATUS_LABEL: Record<ValidationStatus, { en: string; ar: string }> = {
  SCHEDULED:              { en: 'Scheduled',                 ar: 'مجدول' },
  IN_PROGRESS:            { en: 'In progress',               ar: 'قيد التنفيذ' },
  PASSED:                 { en: 'Passed',                    ar: 'مُجتاز' },
  PASSED_WITH_CONDITIONS: { en: 'Passed with conditions',    ar: 'مُجتاز بشروط' },
  FAILED:                 { en: 'Failed',                    ar: 'فشل' },
  REMEDIATION:            { en: 'Remediation',               ar: 'علاج' },
};

export const VALIDATION_STATUS_TONE: Record<ValidationStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  SCHEDULED: 'neutral', IN_PROGRESS: 'info',
  PASSED: 'success', PASSED_WITH_CONDITIONS: 'warning',
  FAILED: 'danger', REMEDIATION: 'warning',
};

export const RETRAINING_STATUS_LABEL: Record<RetrainingStatus, { en: string; ar: string }> = {
  PROPOSED:    { en: 'Proposed',     ar: 'مُقترَح' },
  APPROVED:    { en: 'Approved',     ar: 'مُعتمد' },
  IN_PROGRESS: { en: 'In progress',  ar: 'قيد التنفيذ' },
  DEPLOYED:    { en: 'Deployed',     ar: 'مُنشَر' },
  ROLLED_BACK: { en: 'Rolled back',  ar: 'مُتراجَع عنه' },
  REJECTED:    { en: 'Rejected',     ar: 'مرفوض' },
  ABANDONED:   { en: 'Abandoned',    ar: 'مُلغى' },
};

export const RETRAINING_STATUS_TONE: Record<RetrainingStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  PROPOSED: 'neutral', APPROVED: 'info', IN_PROGRESS: 'info',
  DEPLOYED: 'success', ROLLED_BACK: 'danger',
  REJECTED: 'danger', ABANDONED: 'neutral',
};

export const RETRAINING_TRIGGER_LABEL: Record<RetrainingTrigger, { en: string; ar: string }> = {
  DRIFT:         { en: 'Drift',         ar: 'انحراف' },
  SCHEDULED:     { en: 'Scheduled',     ar: 'مجدول' },
  MANUAL:        { en: 'Manual',        ar: 'يدوي' },
  POST_INCIDENT: { en: 'Post-incident', ar: 'بعد حادث' },
};

export const RECERT_STATUS_LABEL: Record<RecertificationStatus, { en: string; ar: string }> = {
  DUE:                          { en: 'Due',                          ar: 'مستحقّ' },
  IN_REVIEW:                    { en: 'In review',                    ar: 'قيد المراجعة' },
  RECERTIFIED:                  { en: 'Recertified',                  ar: 'مُعاد اعتماده' },
  RECERTIFIED_WITH_CONDITIONS:  { en: 'Recertified with conditions',  ar: 'مُعاد اعتماده بشروط' },
  REVOKED:                      { en: 'Revoked',                      ar: 'مُلغى' },
  EXPIRED:                      { en: 'Expired',                      ar: 'مُنتهي الصلاحية' },
};

export const RECERT_STATUS_TONE: Record<RecertificationStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DUE: 'warning', IN_REVIEW: 'info',
  RECERTIFIED: 'success', RECERTIFIED_WITH_CONDITIONS: 'warning',
  REVOKED: 'danger', EXPIRED: 'danger',
};

// ─── Responses ────────────────────────────────────────────────────

export interface ValidationResponse {
  id: number;
  modelId: number;
  reference: string;
  status: ValidationStatus;
  validationType?: string;
  validatorUserId?: number;
  validatorOrgLabel?: string;
  scheduledAt?: string;
  startedAt?: string;
  completedAt?: string;
  metricsJson?: string;
  ethicsScoresJson?: string;
  fairnessDisparityPct?: number;
  findingsEn?: string;
  findingsAr?: string;
  conditionsEn?: string;
  conditionsAr?: string;
  linkedCapaId?: number;
}

export interface RetrainingResponse {
  id: number;
  modelId: number;
  reference: string;
  status: RetrainingStatus;
  triggerReason?: RetrainingTrigger;
  rationaleEn?: string;
  rationaleAr?: string;
  fromVersionLabel?: string;
  toVersionLabel?: string;
  approvedAt?: string;
  startedAt?: string;
  deployedAt?: string;
  rolledBackAt?: string;
  postDeployMetricsJson?: string;
  rollbackReasonEn?: string;
  rollbackReasonAr?: string;
  ownerUserId?: number;
}

export interface RecertificationResponse {
  id: number;
  modelId: number;
  reference: string;
  cycleLabel?: string;
  status: RecertificationStatus;
  dueDate?: string;
  startedAt?: string;
  decidedAt?: string;
  nextDueDate?: string;
  decisionRationaleEn?: string;
  decisionRationaleAr?: string;
  conditionsEn?: string;
  conditionsAr?: string;
  approverUserId?: number;
  linkedValidationId?: number;
}

export interface DashboardResponse {
  validationsScheduled: number;
  validationsInProgress: number;
  validationsFailedOrConditions: number;
  retrainingsInProgress: number;
  retrainingsDeployedThisQuarter: number;
  retrainingsRolledBack: number;
  recertificationsOverdue: number;
  recertificationsDueNext90d: number;
  validationsByStatus: Record<string, number>;
  retrainingsByStatus: Record<string, number>;
  recertificationsByStatus: Record<string, number>;
  recentValidations: ValidationResponse[];
  recentRetrainings: RetrainingResponse[];
  overdueRecertifications: RecertificationResponse[];
}

// ─── Write requests ──────────────────────────────────────────────

export interface CreateValidationRequest {
  modelId: number;
  reference: string;
  validationType?: string;
  validatorUserId?: number;
  validatorOrgLabel?: string;
  scheduledAt?: string;
}

export interface TransitionValidationRequest {
  toStatus: ValidationStatus;
  metricsJson?: string;
  ethicsScoresJson?: string;
  fairnessDisparityPct?: number;
  findingsEn?: string;
  findingsAr?: string;
  conditionsEn?: string;
  conditionsAr?: string;
  linkedCapaId?: number;
}

export interface CreateRetrainingRequest {
  modelId: number;
  reference: string;
  triggerReason?: RetrainingTrigger;
  rationaleEn?: string;
  rationaleAr?: string;
  fromVersionLabel?: string;
  toVersionLabel?: string;
  ownerUserId?: number;
}

export interface TransitionRetrainingRequest {
  toStatus: RetrainingStatus;
  postDeployMetricsJson?: string;
  rollbackReasonEn?: string;
  rollbackReasonAr?: string;
}

export interface CreateRecertificationRequest {
  modelId: number;
  reference: string;
  cycleLabel?: string;
  dueDate?: string;
}

export interface TransitionRecertificationRequest {
  toStatus: RecertificationStatus;
  decisionRationaleEn?: string;
  decisionRationaleAr?: string;
  conditionsEn?: string;
  conditionsAr?: string;
  nextDueDate?: string;
  linkedValidationId?: number;
}

// ─── State-machine helpers ───────────────────────────────────────

export function nextValidationStatuses(s: ValidationStatus): ValidationStatus[] {
  switch (s) {
    case 'SCHEDULED':              return ['IN_PROGRESS'];
    case 'IN_PROGRESS':            return ['PASSED', 'PASSED_WITH_CONDITIONS', 'FAILED'];
    case 'PASSED_WITH_CONDITIONS': return ['REMEDIATION'];
    case 'FAILED':                 return ['REMEDIATION'];
    case 'REMEDIATION':            return ['IN_PROGRESS'];
    default:                       return [];
  }
}

export function nextRetrainingStatuses(s: RetrainingStatus): RetrainingStatus[] {
  switch (s) {
    case 'PROPOSED':    return ['APPROVED', 'REJECTED', 'ABANDONED'];
    case 'APPROVED':    return ['IN_PROGRESS', 'ABANDONED'];
    case 'IN_PROGRESS': return ['DEPLOYED', 'ABANDONED'];
    case 'DEPLOYED':    return ['ROLLED_BACK'];
    default:            return [];
  }
}

export function nextRecertStatuses(s: RecertificationStatus): RecertificationStatus[] {
  switch (s) {
    case 'DUE':       return ['IN_REVIEW', 'EXPIRED'];
    case 'IN_REVIEW': return ['RECERTIFIED', 'RECERTIFIED_WITH_CONDITIONS', 'REVOKED'];
    default:          return [];
  }
}

// ─── REST client ─────────────────────────────────────────────────

const BASE = '/api/v1/ai-lifecycle';

export const aiLifecycleApi = {
  dashboard: () => api.get<DashboardResponse>(`${BASE}/dashboard`),
  listValidations: (modelId?: number) => {
    const q = modelId ? `?modelId=${modelId}` : '';
    return api.get<ValidationResponse[]>(`${BASE}/validations${q}`);
  },
  createValidation: (req: CreateValidationRequest) =>
    api.post<ValidationResponse>(`${BASE}/validations`, req),
  transitionValidation: (id: number, req: TransitionValidationRequest) =>
    api.post<ValidationResponse>(`${BASE}/validations/${id}/transition`, req),

  listRetrainings: (modelId?: number) => {
    const q = modelId ? `?modelId=${modelId}` : '';
    return api.get<RetrainingResponse[]>(`${BASE}/retrainings${q}`);
  },
  createRetraining: (req: CreateRetrainingRequest) =>
    api.post<RetrainingResponse>(`${BASE}/retrainings`, req),
  transitionRetraining: (id: number, req: TransitionRetrainingRequest) =>
    api.post<RetrainingResponse>(`${BASE}/retrainings/${id}/transition`, req),

  listRecerts: (modelId?: number) => {
    const q = modelId ? `?modelId=${modelId}` : '';
    return api.get<RecertificationResponse[]>(`${BASE}/recertifications${q}`);
  },
  createRecert: (req: CreateRecertificationRequest) =>
    api.post<RecertificationResponse>(`${BASE}/recertifications`, req),
  transitionRecert: (id: number, req: TransitionRecertificationRequest) =>
    api.post<RecertificationResponse>(`${BASE}/recertifications/${id}/transition`, req),
};
