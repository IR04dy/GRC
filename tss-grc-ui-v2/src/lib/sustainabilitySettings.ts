// Sustainability Settings API client (slice 14-A · V65).
//
// Reads / writes per-org thresholds that drive Rules 1, 3, 9, 13, 14.
// The backend lazy-creates a default row on first GET, so callers
// never need a "missing settings" branch.

import { api } from './api';

export interface OrganisationSettings {
  id?: number;
  organisationId?: number;

  /** Rule 14 — banner shows below this (default 60). */
  esgReadinessWarningThreshold: number;
  /** Rule 14 — banner switches to critical tone below this (default 40). */
  esgReadinessCriticalThreshold: number;

  /** Rule 9 — days before expiry to flag as "expiring soon" (default 30). */
  evidenceExpiryWarningDays: number;
  /** Cron expression for the nightly expiry sweep. */
  evidenceSweepCron: string;

  /** Rule 1 — Scope-3 weighted-coverage threshold below which the
   *  aggregate sets scope3PartialFlag = true (default 70.00). */
  scope3PartialCoverageThreshold: number;

  /** Rule 13 — days after due-date when CAPA escalates to module owner
   *  (default 7) and to exec (default 30). */
  capaOverdueWarningDays: number;
  capaExecutiveEscalationDays: number;

  /** Rule 3 — SGI at-risk threshold factor (default 0.70 = 70% of
   *  expected progress on the linear timeline). */
  sgiAtRiskProgressFactor: number;

  /** V295 (decision #4) — retired offsets ≤ this % of the disclosed gross reduction. */
  carbonOffsetCapPct: number;

  notes?: string;
}

const BASE = '/api/sustainability/settings';

export function getSettings() {
  return api.get<OrganisationSettings>(BASE);
}

export function updateSettings(body: Partial<OrganisationSettings>) {
  return api.put<OrganisationSettings>(BASE, body);
}

export function resetSettings() {
  return api.post<OrganisationSettings>(`${BASE}/reset`);
}
