// Plan review attestation client — audit Tier-2 #10.
//
// Surfaces /api/v1/bcm/plans/review/* and /plans/{id}/attest* to the UI.
// The shared `api` helper resolves to the parsed JSON body — never chain
// `.data` (same convention as every other BCM lib).

import { api } from './api';
import type { BcpPlan } from './bcpPlans';

export type AttestationOutcome = 'STILL_CURRENT' | 'CHANGES_REQUIRED' | 'REOPENED_TO_EDIT';
export type ReviewState        = 'CURRENT' | 'DUE_SOON' | 'OVERDUE';

export interface PlanReviewAttestation {
  id:                       number;
  organisationId:           number;
  planId:                   number;
  planVersion:              number;
  attestedAt:               string;
  attestedById?:            number;
  attestedByName:           string;
  attestationOutcome:       AttestationOutcome;
  notes?:                   string;
  previousNextReviewDate?:  string;
  newNextReviewDate?:       string;
  createdAt?:               string;
}

export interface SweepResult {
  current:          number;
  dueSoon:          number;
  overdue:          number;
  flippedToOverdue: number;
}

const BASE = '/api/v1/bcm/plans';

export function reviewQueue(withinDays = 30): Promise<BcpPlan[]> {
  return api.get<BcpPlan[]>(`${BASE}/review/queue?withinDays=${withinDays}`);
}

export function planAttestations(planId: number): Promise<PlanReviewAttestation[]> {
  return api.get<PlanReviewAttestation[]>(`${BASE}/${planId}/attestations`);
}

export function attestPlan(planId: number, outcome: AttestationOutcome,
                           notes?: string): Promise<PlanReviewAttestation> {
  return api.post<PlanReviewAttestation>(`${BASE}/${planId}/attest`, { outcome, notes });
}

export function sweepReview(): Promise<SweepResult> {
  return api.post<SweepResult>(`${BASE}/review/sweep`, {});
}

/** Map a review state to a tone for badge / KPI rendering. */
export function reviewStateTone(s: ReviewState | string | undefined):
    'success' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'CURRENT':  return 'success';
    case 'DUE_SOON': return 'warning';
    case 'OVERDUE':  return 'danger';
    default:         return 'neutral';
  }
}
