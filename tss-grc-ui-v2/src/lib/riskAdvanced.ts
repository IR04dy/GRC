// Risk-Advanced API client (V80).
//
// Reads + writes against /api/v1/risk/risks/:id/assessments and /treatments —
// the second-half of the ERM module's lifecycle (the basic Risk CRUD is
// handled by riskApi in api.ts).

import { api } from './api';

export interface RiskAssessment {
  id: number;
  externalId?: string;
  riskId: number;
  assessorId?: number;
  assessmentDate: string;
  likelihood: number;
  impact: number;
  score?: number;
  assessmentNotesEn?: string;
  createdAt?: string;
}

export type RiskTreatmentType   = 'AVOID' | 'MITIGATE' | 'TRANSFER' | 'ACCEPT';
export type RiskTreatmentStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface RiskTreatment {
  id: number;
  externalId?: string;
  riskId: number;
  treatmentType: RiskTreatmentType;
  descriptionEn: string;
  ownerId?: number;
  dueDate?: string;
  status: RiskTreatmentStatus;
  completedAt?: string;
}

const BASE = '/api/v1/risk';

// ─── Assessments ─────────────────────────────────────────────────────

export function listAssessments(riskId: number) {
  return api.get<RiskAssessment[]>(`${BASE}/risks/${riskId}/assessments`);
}

export function recordAssessment(riskId: number, body: { likelihood: number; impact: number; assessmentNotesEn?: string; assessmentDate?: string }) {
  return api.post<RiskAssessment>(`${BASE}/risks/${riskId}/assessments`, body);
}

export function correctAssessment(id: number, body: { likelihood?: number; impact?: number; assessmentNotesEn?: string }) {
  return api.put<RiskAssessment>(`${BASE}/assessments/${id}`, body);
}

// ─── Treatments ──────────────────────────────────────────────────────

export function listTreatments(riskId: number) {
  return api.get<RiskTreatment[]>(`${BASE}/risks/${riskId}/treatments`);
}

export function getTreatment(id: number) {
  return api.get<RiskTreatment>(`${BASE}/treatments/${id}`);
}

export function createTreatment(riskId: number, body: Partial<RiskTreatment>) {
  return api.post<RiskTreatment>(`${BASE}/risks/${riskId}/treatments`, body);
}

export function updateTreatment(id: number, body: Partial<RiskTreatment>) {
  return api.put<RiskTreatment>(`${BASE}/treatments/${id}`, body);
}

export function transitionTreatment(id: number, to: RiskTreatmentStatus) {
  return api.post<RiskTreatment>(`${BASE}/treatments/${id}/transition`, { to });
}

export function deleteTreatment(id: number) {
  return api.delete<void>(`${BASE}/treatments/${id}`);
}

// ─── UI helpers ──────────────────────────────────────────────────────

export function treatmentTypeTone(t: RiskTreatmentType): 'danger' | 'primary' | 'accent' | 'warning' {
  switch (t) {
    case 'AVOID':    return 'danger';
    case 'MITIGATE': return 'primary';
    case 'TRANSFER': return 'accent';
    case 'ACCEPT':   return 'warning';
  }
}

export function treatmentStatusTone(s: RiskTreatmentStatus): 'neutral' | 'info' | 'success' | 'danger' {
  switch (s) {
    case 'PLANNED':     return 'neutral';
    case 'IN_PROGRESS': return 'info';
    case 'COMPLETED':   return 'success';
    case 'CANCELLED':   return 'danger';
  }
}

/** Heatmap colour for a 1..25 score. Matches the existing RiskDetailPage palette. */
export function scoreColor(score: number | null | undefined): string {
  if (score == null) return 'var(--surface-3)';
  if (score >= 16) return 'var(--risk-crit)';
  if (score >= 12) return 'var(--risk-vhigh)';
  if (score >= 8)  return 'var(--risk-high)';
  if (score >= 4)  return 'var(--risk-med)';
  return 'var(--risk-low)';
}

export function nextTreatmentStatuses(s: RiskTreatmentStatus): RiskTreatmentStatus[] {
  switch (s) {
    case 'PLANNED':     return ['IN_PROGRESS', 'CANCELLED'];
    case 'IN_PROGRESS': return ['COMPLETED', 'PLANNED', 'CANCELLED'];
    default:            return [];
  }
}
