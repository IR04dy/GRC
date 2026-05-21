import { api } from './api';

/**
 * AI-assisted features (Saudi GRC implementation prompt §27 / V116).
 * Heuristic backend today — same shape will hold when an LLM gateway is wired.
 */

export type AiFeature =
  | 'RISK_DESCRIPTION'
  | 'BIA_RTO_RPO'
  | 'EVIDENCE_QUALITY'
  | 'INCIDENT_ROOT_CAUSE'
  | 'BOARD_SUMMARY'
  | 'WORKFLOW_SUBMISSION_REVIEW';

export type AiStatus = 'DRAFT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';

export interface AiSuggestion {
  id: number;
  organisationId: number;
  featureCode: AiFeature;
  relatedEntity?: string | null;
  relatedEntityId?: number | null;
  inputJson: string;
  outputJson: string;
  status: AiStatus;
  confidence?: number | null;
  rationale?: string | null;
  reviewedBy?: number | null;
  reviewedAt?: string | null;
  reviewerNote?: string | null;
  createdAt: string;
}

export const ai = {
  riskDescription: (body: {
    riskId?: number; titleEn: string; categoryCode?: string;
    inherentLikelihood?: number; inherentImpact?: number;
  }) => api.post<AiSuggestion>('/api/v1/ai/risk-description', body),

  biaRtoRpo: (body: {
    serviceId?: number; serviceTitle: string;
    criticality?: string; tier?: string;
  }) => api.post<AiSuggestion>('/api/v1/ai/bia-rto-rpo', body),

  evidenceQuality: (body: {
    evidenceId?: number; evidenceTitle: string; evidenceType?: string;
    controlCode?: string; ageDays?: number; hasSignature?: boolean;
  }) => api.post<AiSuggestion>('/api/v1/ai/evidence-quality', body),

  incidentRootCause: (body: {
    crisisEventId?: number; title: string; severity?: number;
    triggerSource?: string; sitRepHighlights?: string[];
  }) => api.post<AiSuggestion>('/api/v1/ai/incident-root-cause', body),

  boardSummary: (body: {
    openHighRisks?: number; overdueCapa?: number;
    ncaCompliancePct?: number; pdplCompliancePct?: number;
    bcmReadinessScore?: number; activeIncidents?: number;
  }) => api.post<AiSuggestion>('/api/v1/ai/board-summary', body),

  /** PR #23 — per-submission reviewer guidance. Called by
   *  WorkflowSubmissionDetailPage on demand. */
  workflowSubmissionReview: (body: {
    submissionId?: number; workflowId: string; workflowModule?: string;
    status?: string; regulatoryAnchor?: string;
    titleEn?: string; summaryEn?: string;
  }) => api.post<AiSuggestion>('/api/v1/ai/workflow-submission-review', body),

  listByFeature: (feature: AiFeature) =>
    api.get<AiSuggestion[]>(`/api/v1/ai/suggestions?feature=${feature}`),

  listByEntity: (entity: string, id: number) =>
    api.get<AiSuggestion[]>(`/api/v1/ai/suggestions/entity/${entity}/${id}`),

  review: (id: number, body: { decision: 'ACCEPTED' | 'REJECTED'; reviewerNote?: string }) =>
    api.post<AiSuggestion>(`/api/v1/ai/suggestions/${id}/review`, body),
};
