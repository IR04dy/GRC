import { api } from './api';

/**
 * Frontend bindings for the buyer-assessment completion endpoints
 * (V113 + V115 + V116). Each group of methods maps to one §-numbered
 * gap in the May-2026 buyer doc:
 *
 *   §5.1 — Risk → Control linkage, appetite variance, attestation cycle
 *   §5.4 — Cross-border listing, special-category, DSR SLA, PDPL 72h
 *   §5.9 — Evidence freshness sweep, connector registry
 */

// ── Types ──────────────────────────────────────────────────────────────

export interface RiskControl {
  id: number;
  organisationId: number;
  riskId: number;
  controlId: number;
  effectiveness: string;
  contributionPct?: number | null;
  notes?: string | null;
}

export interface AppetiteVariance {
  totalRisks: number;
  risksAboveAppetite: number;
  risksWithinAppetite: number;
  byCategory: Record<string, {
    riskCount: number;
    aboveAppetite: number;
    appetiteScore?: number | null;
    maxResidualScore?: number | null;
    avgResidualScore?: number | null;
  }>;
}

export interface FreshnessSweepResult {
  itemsScanned: number;
  flippedToStale: number;
  stillCurrent: number;
}

export interface AtRiskTransfer {
  id: number;
  activityName: string;
  receivingCountry: string;
  adequacyDecision: string;
  transferMechanism: string;
  dataOfficeReference: string;
  riskNote: string;
}

export interface RiskAttestation {
  id: number;
  organisationId: number;
  riskId: number;
  attestedAt: string;
  attestedByName?: string | null;
  decision: 'CONFIRMED' | 'NEEDS_REVIEW' | 'TRANSFER_OWNER';
  statementEn?: string | null;
  statementAr?: string | null;
  residualScoreSnapshot?: number | null;
  riskLevelSnapshot?: string | null;
  nextDueDate?: string | null;
}

export interface AttestationSweepResult {
  overdue: number;
  firedEvents: number;
}

export interface DsrSlaSweepResult {
  openOverdue: number;
  firedEvents: number;
}

export interface Pdpl72hSweepResult {
  personalDataCrises: number;
  deadlinesSet: number;
  newlyBreached: number;
}

export interface EvidenceConnector {
  id: number;
  organisationId: number;
  code: string;
  nameEn: string;
  nameAr?: string | null;
  vendor?: string | null;
  category: string;
  capabilityJson?: string | null;
  status: 'DRAFT' | 'AVAILABLE' | 'ENABLED' | 'DISABLED' | 'DEPRECATED';
  version?: string | null;
  configJson?: string | null;
  lastSyncedAt?: string | null;
  lastSyncStatus?: string | null;
  lastSyncNote?: string | null;
}

// ── §5.1 ERM ───────────────────────────────────────────────────────────

export const ermGap = {
  listRiskControls: (riskId: number) =>
    api.get<RiskControl[]>(`/api/v1/risk/risks/${riskId}/controls`),

  linkRiskControl: (riskId: number, body: {
    controlId: number; effectiveness: string;
    contributionPct?: number | null; notes?: string | null;
  }) =>
    api.post<RiskControl>(`/api/v1/risk/risks/${riskId}/controls`, body),

  appetiteVariance: () =>
    api.get<AppetiteVariance>('/api/v1/risk/appetite/variance'),

  attest: (riskId: number, body: {
    decision: string; statementEn?: string; statementAr?: string;
    attestedByName?: string;
  }) =>
    api.post<RiskAttestation>(`/api/v1/risk/risks/${riskId}/attestations`, body),

  listAttestations: (riskId: number) =>
    api.get<RiskAttestation[]>(`/api/v1/risk/risks/${riskId}/attestations`),

  attestationSweep: () =>
    api.post<AttestationSweepResult>('/api/v1/risk/attestations/sweep'),
};

// ── §5.9 Evidence ──────────────────────────────────────────────────────

export const evidenceGap = {
  freshnessSweep: () =>
    api.post<FreshnessSweepResult>('/api/v1/evidence/items/freshness/sweep'),

  listConnectors: () =>
    api.get<EvidenceConnector[]>('/api/v1/evidence/connectors'),

  updateConnector: (code: string, body: { status?: string; configJson?: string }) =>
    api.patch<EvidenceConnector>(`/api/v1/evidence/connectors/${code}`, body),
};

// ── §5.4 Privacy ───────────────────────────────────────────────────────

export const privacyGap = {
  atRiskTransfers: () =>
    api.get<AtRiskTransfer[]>('/api/v1/privacy/processing-activities/at-risk-transfers'),

  specialCategoryActivities: () =>
    api.get<unknown[]>('/api/v1/privacy/processing-activities/special-category'),

  dsrSlaSweep: () =>
    api.post<DsrSlaSweepResult>('/api/v1/privacy/dsr/sla/sweep'),

  pdpl72hSweep: () =>
    api.post<Pdpl72hSweepResult>('/api/v1/privacy/breach/pdpl-72h/sweep'),
};
