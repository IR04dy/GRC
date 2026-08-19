// Third-party cyber risk API client (gap #4 — dedicated TPRM cyber assessment).

import { api } from './api';

export type TpcStatus      = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'RETIRED';
export type TpcInherent    = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TpcResidual    = 'LOW' | 'MODERATE' | 'ELEVATED' | 'HIGH' | 'CRITICAL';

export interface ThirdPartyCyberAssessment {
  id: number;
  assessmentCode?: string;
  vendorId?: number;
  vendorName: string;
  vendorCountry?: string;
  inherentCyberRisk: TpcInherent;
  scopeDescriptionEn: string;
  scopeDescriptionAr?: string;
  certifications?: string;
  certificationsExpiry?: string;
  externalPostureScore?: number;
  questionnaireScore?: number;
  questionnaireCompletedAt?: string;
  breachHistory24m: number;
  lastKnownBreachDate?: string;
  lastKnownBreachSummary?: string;
  criticalVulnsOpen?: number;
  patchSlaCompliancePct?: number;
  mfaEnforced: number;
  penTestWithin12m: number;
  residualRiskTier: TpcResidual;
  riskScore?: number;
  findingsEn?: string;  findingsAr?: string;
  treatmentPlanEn?: string;  treatmentPlanAr?: string;
  nextReviewDue?: string;
  assessedAt?: string;
  assessorName?: string;
  approvedAt?: string;
  approverName?: string;
  status: TpcStatus;
  createdAt: string; updatedAt: string;
}

const BASE = '/api/tprm/cyber-assessments';

export function listTpc(status?: TpcStatus, page = 0, size = 200):
    Promise<{ content: ThirdPartyCyberAssessment[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}
export function submitTpc(id: number)   { return api.post<ThirdPartyCyberAssessment>(`${BASE}/${id}/submit`); }
// F2-247 — no approverName on purpose. `ThirdPartyCyberAssessmentController#approve` reads the
// approving identity from `principal.getUsername()` and has always discarded this body field, so
// the page was passing the literal 'admin' into a value the server never used. A required-looking
// parameter the server ignores tells the caller their value matters (F2-184/F2-225).
export function approveTpc(id: number) {
  return api.post<ThirdPartyCyberAssessment>(`${BASE}/${id}/approve`);
}
export function retireTpc(id: number)   { return api.post<ThirdPartyCyberAssessment>(`${BASE}/${id}/retire`); }

export const TPC_STATUS_TONE: Record<TpcStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', IN_REVIEW: 'info', APPROVED: 'success', RETIRED: 'primary',
};
export const TPC_RESIDUAL_TONE: Record<TpcResidual, 'primary'|'success'|'danger'|'warning'|'info'> = {
  LOW: 'success', MODERATE: 'info', ELEVATED: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};
export const TPC_INHERENT_TONE: Record<TpcInherent, 'primary'|'success'|'danger'|'warning'|'info'> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};

/**
 * F2-230 — the residual ladder, least to most exposed. The mirror of the backend's
 * `ResidualRiskTier`, which is what `OrderedVocabularySortContractTest` compares it against.
 *
 * The type above already declared these five in this order; what was missing was anything that
 * treated the order as data. Six readers each spelled "is this vendor a concern?" for themselves and
 * between them produced three different answers to it:
 *
 *   - `ThirdPartyCyberPage`'s two KPI tiles counted `=== 'HIGH'` and `=== 'CRITICAL'`, so on 16 real
 *     rows (dev = prod) both read **0** — while the tab they both navigate to showed **2**, the
 *     ELEVATED rows neither tile could see. F2-197: a tile whose figure disagrees with the list it
 *     opens is worse than a tile with no figure.
 *   - `TprmConsolidatedOverview` counted `HIGH || CRITICAL` as concerning and `LOW || MODERATE` as
 *     fine, two inclusion lists that between them **omit ELEVATED entirely** — so every ELEVATED
 *     assessment fell into neither bucket and the two KPIs did not sum to the register.
 *   - the page's own "high-risk" tab did include ELEVATED, so the page and the overview held two
 *     different thresholds for one question and each looked self-consistent.
 */
export const TPC_RESIDUAL_LADDER: readonly TpcResidual[] =
  ['LOW', 'MODERATE', 'ELEVATED', 'HIGH', 'CRITICAL'] as const;

/** Position on the ladder; -1 for null, blank or anything unrecognised. */
export function tpcResidualRank(tier: string | null | undefined): number {
  if (!tier) return -1;
  return TPC_RESIDUAL_LADDER.indexOf(tier.trim().toUpperCase() as TpcResidual);
}

/**
 * The threshold question, asked once. This is what replaces the inclusion lists — and the reason it
 * takes a threshold rather than exposing a fixed "is high risk" predicate is that the register has two
 * legitimate thresholds (≥ ELEVATED for "needs attention", CRITICAL for the top rung) and hard-coding
 * either is how the second one gets written as a fresh inclusion list.
 */
export function isResidualAtLeast(tier: string | null | undefined, threshold: TpcResidual): boolean {
  return tpcResidualRank(tier) >= TPC_RESIDUAL_LADDER.indexOf(threshold);
}
