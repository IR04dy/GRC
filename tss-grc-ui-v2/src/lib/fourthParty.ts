// Fourth-party exposure API client (gap #1 — SCM sub-tier visibility).

import { api } from './api';

export type FpStatus = 'DISCOVERED' | 'VALIDATED' | 'MITIGATED' | 'RETIRED';
export type FpDependencyType =
  'DATA_PROCESSING' | 'INFRASTRUCTURE' | 'SOFTWARE' | 'LOGISTICS' | 'FINANCIAL' | 'SUPPORT';
export type FpCriticality   = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type FpConcentration = 'LOW' | 'MEDIUM' | 'HIGH';

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
  LOW: 'success', MEDIUM: 'warning', HIGH: 'danger',
};
