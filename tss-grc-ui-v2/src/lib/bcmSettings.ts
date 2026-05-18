// BCM tenant settings client — audit Tier-1 #6.

import { api } from './api';

export interface BcmSettings {
  id: number;
  organisationId: number;

  rtoWarningHours: number;
  rtoCriticalHours: number;
  rpoWarningHours: number;
  rpoCriticalHours: number;

  exerciseFrequencyMonthsDefault: number;
  exerciseFrequencyMonthsCritical: number;

  evidenceExpiryWarningDays: number;
  capaOverdueGraceDays: number;

  riskMatrixLikelihoodMax: number;
  riskMatrixImpactMax: number;

  approvalMatrixJson?: string;
  notificationRulesJson?: string;

  iso22301Version?: string;
  samaOpresVersion?: string;
  dgaFrameworkVersion?: string;
  ncemaFrameworkVersion?: string;
  regulatoryImportStatus: 'PLACEHOLDER' | 'PARTIAL' | 'COMPLETE';
  regulatoryImportNotes?: string;

  createdAt?: string;
  updatedAt?: string;
}

export async function getBcmSettings(): Promise<BcmSettings> {
  return (await api.get<BcmSettings>('/api/v1/bcm/settings')).data;
}

export async function updateBcmSettings(patch: Partial<BcmSettings>): Promise<BcmSettings> {
  return (await api.patch<BcmSettings>('/api/v1/bcm/settings', patch)).data;
}

export async function resetBcmSettings(): Promise<BcmSettings> {
  return (await api.post<BcmSettings>('/api/v1/bcm/settings/reset', {})).data;
}
