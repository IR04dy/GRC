// TPRM Questionnaire Engine API client (V81).

import { api } from './api';

export type QtStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
export type QtCategory = 'CYBER' | 'PRIVACY' | 'FINANCIAL' | 'OPERATIONAL' | 'COMPLIANCE' | 'GENERAL';
export type AnswerType = 'SCALE_1_5' | 'BOOLEAN' | 'SELECT' | 'TEXT';
export type AssessmentWorkflowStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'REVIEWED';
export type RiskRating = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface QuestionnaireTemplate {
  id?: number;
  organisationId?: number;
  templateCode: string;
  version: string;
  nameEn: string;
  nameAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  category: QtCategory;
  applicableTo?: string;
  thresholdLowMax: number;
  thresholdMediumMax: number;
  thresholdHighMax: number;
  status: QtStatus;
  activatedAt?: string;
  archivedAt?: string;
  notes?: string;
}

export interface QuestionnaireQuestion {
  id?: number;
  templateId: number;
  sectionCode: string;
  sectionNameEn?: string;
  sectionNameAr?: string;
  questionCode: string;
  questionEn: string;
  questionAr?: string;
  answerType: AnswerType;
  optionsJson?: string;
  weight: number;
  invertScore: boolean;
  mandatory: boolean;
  sequenceOrder: number;
  guidanceEn?: string;
  guidanceAr?: string;
}

export interface QuestionnaireResponse {
  id?: number;
  vendorAssessmentId: number;
  questionId: number;
  templateId: number;
  answerValue?: string;
  scoredPoints?: number;
  maxPoints: number;
  weightedPoints?: number;
  answerNotesEn?: string;
  answerNotesAr?: string;
  evidenceRef?: string;
  notApplicable: boolean;
  naJustification?: string;
  answeredBy?: string;
  answeredAt?: string;
}

const BASE = '/api/v1/tprm';

// ─── Templates ───────────────────────────────────────────────────────

export function listQtTemplates(status?: QtStatus) {
  const q = status ? `?status=${status}` : '';
  return api.get<QuestionnaireTemplate[]>(`${BASE}/questionnaire-templates${q}`);
}
export function getQtTemplate(id: number) { return api.get<QuestionnaireTemplate>(`${BASE}/questionnaire-templates/${id}`); }
export function createQtTemplate(body: Partial<QuestionnaireTemplate>) { return api.post<QuestionnaireTemplate>(`${BASE}/questionnaire-templates`, body); }
export function updateQtTemplate(id: number, body: Partial<QuestionnaireTemplate>) { return api.put<QuestionnaireTemplate>(`${BASE}/questionnaire-templates/${id}`, body); }
export function transitionQtTemplate(id: number, to: QtStatus) { return api.post<QuestionnaireTemplate>(`${BASE}/questionnaire-templates/${id}/transition`, { to }); }

// ─── Questions ───────────────────────────────────────────────────────

export function listQtQuestions(templateId: number) { return api.get<QuestionnaireQuestion[]>(`${BASE}/questionnaire-templates/${templateId}/questions`); }
export function createQtQuestion(templateId: number, body: Partial<QuestionnaireQuestion>) { return api.post<QuestionnaireQuestion>(`${BASE}/questionnaire-templates/${templateId}/questions`, body); }
export function updateQtQuestion(id: number, body: Partial<QuestionnaireQuestion>) { return api.put<QuestionnaireQuestion>(`${BASE}/questionnaire-questions/${id}`, body); }
export function deleteQtQuestion(id: number) { return api.delete<void>(`${BASE}/questionnaire-questions/${id}`); }

// ─── Assessment workflow ─────────────────────────────────────────────

export function assignTemplateToAssessment(assessmentId: number, templateId: number) {
  return api.post<{ id: number; workflowStatus: string; totalQuestions: number; answeredCount: number; computedScorePct?: number; riskRating?: string }>(
    `${BASE}/assessments/${assessmentId}/assign-template`, { templateId });
}
export function listAssessmentResponses(assessmentId: number) { return api.get<QuestionnaireResponse[]>(`${BASE}/assessments/${assessmentId}/responses`); }
export function upsertResponse(assessmentId: number, body: Partial<QuestionnaireResponse>) {
  return api.post<QuestionnaireResponse>(`${BASE}/assessments/${assessmentId}/responses`, body);
}
export function completeAssessment(assessmentId: number) { return api.post<{ workflowStatus: string; computedScorePct?: number; riskRating?: string }>(`${BASE}/assessments/${assessmentId}/complete`); }
export function reviewAssessment(assessmentId: number) { return api.post<{ workflowStatus: string }>(`${BASE}/assessments/${assessmentId}/review`); }

// ─── UI helpers ──────────────────────────────────────────────────────

export function qtStatusTone(s: QtStatus): 'neutral' | 'success' | 'warning' {
  switch (s) {
    case 'DRAFT':    return 'neutral';
    case 'ACTIVE':   return 'success';
    case 'ARCHIVED': return 'warning';
  }
}

export function qtStatusLabel(s: QtStatus, lang: 'en' | 'ar'): string {
  const en: Record<QtStatus, string> = { DRAFT: 'Draft', ACTIVE: 'Active', ARCHIVED: 'Archived' };
  const ar: Record<QtStatus, string> = { DRAFT: 'مسوّدة', ACTIVE: 'نشط', ARCHIVED: 'مؤرشَف' };
  return (lang === 'ar' ? ar : en)[s];
}

export function riskRatingTone(r: RiskRating | string | undefined): 'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (r) {
    case 'LOW':      return 'success';
    case 'MEDIUM':   return 'info';
    case 'HIGH':     return 'warning';
    case 'CRITICAL': return 'danger';
    default:         return 'neutral';
  }
}

export function workflowStatusTone(s: AssessmentWorkflowStatus | string | undefined): 'neutral' | 'info' | 'success' | 'primary' {
  switch (s) {
    case 'NOT_STARTED': return 'neutral';
    case 'IN_PROGRESS': return 'info';
    case 'COMPLETED':   return 'primary';
    case 'REVIEWED':    return 'success';
    default:            return 'neutral';
  }
}
