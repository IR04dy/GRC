// Fourth-party exposure API client (gap #1 — SCM sub-tier visibility).

import { api } from './api';

export type FpStatus = 'DISCOVERED' | 'VALIDATED' | 'MITIGATED' | 'RETIRED';
export type FpDependencyType =
  'DATA_PROCESSING' | 'INFRASTRUCTURE' | 'SOFTWARE' | 'LOGISTICS' | 'FINANCIAL' | 'SUPPORT';
export type FpCriticality   = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/**
 * F2-229 — CRITICAL is a member, and leaving it out is what made the tier invisible.
 *
 * The column held CRITICAL in both dev and prod (one row: an underlying hyperscaler region) while
 * every artefact that read it declared three tokens — this type, the entity's field javadoc, and the
 * server-side recompute whose output range is LOW|MEDIUM|HIGH. So `FP_CONC_TONE[r.concentrationRisk]`
 * was `undefined` for that row, the concentration tab's `HIGH || MEDIUM` filter excluded it, and both
 * dashboards' `=== 'HIGH'` counts skipped it: the register's most concentrated fourth party was the
 * one row none of the concentration surfaces could see.
 */
export type FpConcentration = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/**
 * Least to most concentrated, mirroring the server's `ConcentrationTier.ladder()`.
 *
 * The ORDER is the point. `ORDER BY concentration_risk DESC` — which is what the register's list
 * endpoint used to do — sorts these four alphabetically: MEDIUM, LOW, HIGH, CRITICAL.
 */
export const FP_CONCENTRATION_LADDER: readonly FpConcentration[] =
  ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

/** Position on the ladder, or -1 for anything the ladder does not declare. */
export function fpConcentrationRank(tier: string | null | undefined): number {
  if (!tier) return -1;
  return FP_CONCENTRATION_LADDER.indexOf(tier.trim().toUpperCase() as FpConcentration);
}

/**
 * Is this row's concentration at or above `threshold`?
 *
 * F2-196's rule, on a tier instead of a status: **"elevated" must be a threshold over a declared
 * ladder, never a second inclusion list.** Six readers each spelled it out as `=== 'HIGH'` or
 * `HIGH || MEDIUM`, so every one of them silently dropped the token nobody had remembered. A
 * threshold admits a new top tier by construction.
 */
export function isConcentrationAtLeast(
  tier: string | null | undefined, threshold: FpConcentration): boolean {
  return fpConcentrationRank(tier) >= FP_CONCENTRATION_LADDER.indexOf(threshold);
}

export interface FourthPartyExposure {
  id: number;
  primaryVendorId?: number;
  primaryVendorName: string;
  fourthPartyName: string;
  fourthPartyCountry?: string;
  dependencyType: FpDependencyType;
  downstreamCriticality: FpCriticality;
  concentrationRisk: FpConcentration;
  serviceDescriptionEn: string;
  serviceDescriptionAr?: string;
  dataCategoriesShared?: string;
  contractChainClauses?: string;
  subContractingAllowed: number;
  flowDownClausesPresent: number;
  lastValidatedAt?: string;
  validatorName?: string;
  nextValidationDue?: string;
  status: FpStatus;
  mitigationNotesEn?: string;
  mitigationNotesAr?: string;
  createdAt: string;
  updatedAt: string;
}

const BASE = '/api/scm/fourth-party';

export function listFourthParty(status?: FpStatus, page = 0, size = 200):
    Promise<{ content: FourthPartyExposure[]; totalElements: number }> {
  const qs = status ? `&status=${status}` : '';
  return api.get(`${BASE}?page=${page}&size=${size}${qs}`);
}

export function validateFourthParty(id: number, validatorName: string): Promise<FourthPartyExposure> {
  return api.post(`${BASE}/${id}/validate`, { validatorName });
}

export function mitigateFourthParty(id: number, notesEn: string): Promise<FourthPartyExposure> {
  return api.post(`${BASE}/${id}/mitigate`, { notesEn });
}

export function retireFourthParty(id: number): Promise<FourthPartyExposure> {
  return api.post(`${BASE}/${id}/retire`);
}

export const FP_STATUS_TONE: Record<FpStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DISCOVERED: 'warning', VALIDATED: 'success', MITIGATED: 'info', RETIRED: 'primary',
};
export const FP_CRIT_TONE: Record<FpCriticality, 'primary'|'success'|'danger'|'warning'|'info'> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};
export const FP_CONC_TONE: Record<FpConcentration, 'primary'|'success'|'danger'|'warning'|'info'> = {
  LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger',
};
