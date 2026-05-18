// BIA Assessment client — independent audit Tier-1 #3.
//
// Typed wrappers around /api/v1/bcm/bia-assessments/* plus a couple of
// helpers (criticality tone, time-band labels) that the wizard reads.

import { api } from './api';

export type BiaStatus =
  | 'DRAFT' | 'UNDER_REVIEW' | 'CHANGES_REQUIRED' | 'APPROVED' | 'ACTIVE' | 'ARCHIVED';

export type BiaCriticality = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'SOVEREIGN';

/** Per-dimension score 0..5 for a single time band. */
export type TimeBandScores = Partial<Record<string, number>>;

export interface BcmBiaAssessment {
  id: number;
  organisationId?: number;
  biaCode: string;
  cycleId?: number;
  serviceId?: number;
  processId?: number;
  department?: string;
  biaOwnerId?: number;
  serviceOwnerId?: number;
  processOwnerId?: number;
  description?: string;

  mtpdHours?: number;
  rtoHours?: number;
  rpoHours?: number;
  mbcoDescription?: string;

  minStaff?: number;
  minTechText?: string;
  minFacilityText?: string;
  minSupplierText?: string;

  manualWorkaroundAvailable: boolean;
  manualWorkaroundDescription?: string;
  peakPeriodsText?: string;

  /** JSON string of `{ [timeBand]: { [dimension]: 1..5 } }` */
  impactTimeBandsJson?: string;
  /** JSON string of `{ [dimension]: 1..5 }` — denormalised on calculate. */
  impactDimensionsJson?: string;

  overallCriticality?: BiaCriticality;
  evidenceId?: number;
  reviewerId?: number;
  approvalStatus: BiaStatus;
  approvedBy?: string;
  approvedAt?: string;
  generatedBcpId?: number;
  generatedDrpId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface BiaCatalogResponse {
  timeBands: string[];
  impactDimensions: string[];
}

// ─── Display helpers ──────────────────────────────────────────────

export const CRITICALITY_LABELS: Record<BiaCriticality, { en: string; ar: string }> = {
  LOW:       { en: 'Low',         ar: 'منخفض' },
  MEDIUM:    { en: 'Medium',      ar: 'متوسط' },
  HIGH:      { en: 'High',        ar: 'مرتفع' },
  CRITICAL:  { en: 'Critical',    ar: 'حرج' },
  SOVEREIGN: { en: 'Sovereign',   ar: 'سيادي / وطني' },
};

export const DIMENSION_LABELS: Record<string, { en: string; ar: string }> = {
  regulatory:         { en: 'Regulatory',         ar: 'تنظيمي' },
  financial:          { en: 'Financial',          ar: 'مالي' },
  operational:        { en: 'Operational',        ar: 'تشغيلي' },
  reputation:         { en: 'Reputation',         ar: 'سمعة' },
  legal_contractual:  { en: 'Legal / contract',   ar: 'قانوني / تعاقدي' },
  public_service:     { en: 'Public service',     ar: 'خدمة عامة' },
  national_sovereign: { en: 'National sovereign', ar: 'سيادي / وطني' },
  data:               { en: 'Data',               ar: 'بيانات' },
  cyber:              { en: 'Cyber',              ar: 'سيبراني' },
  supplier:           { en: 'Supplier',           ar: 'مورّد' },
};

export function criticalityTone(c?: BiaCriticality): 'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (c) {
    case 'LOW':       return 'success';
    case 'MEDIUM':    return 'info';
    case 'HIGH':      return 'warning';
    case 'CRITICAL':  return 'danger';
    case 'SOVEREIGN': return 'danger';
    default:          return 'neutral';
  }
}

export function biaStatusTone(s: BiaStatus): 'neutral' | 'info' | 'warning' | 'success' {
  switch (s) {
    case 'DRAFT':            return 'neutral';
    case 'UNDER_REVIEW':     return 'warning';
    case 'CHANGES_REQUIRED': return 'warning';
    case 'APPROVED':         return 'success';
    case 'ACTIVE':           return 'success';
    case 'ARCHIVED':         return 'neutral';
  }
}

/** Parse the impact-time-band JSON safely; returns {} on bad input. */
export function parseTimeBandMatrix(json?: string): Record<string, TimeBandScores> {
  if (!json) return {};
  try { return JSON.parse(json); } catch { return {}; }
}

/** Re-stringify the matrix for PATCH. Drops zero-valued cells to keep it tidy. */
export function stringifyTimeBandMatrix(matrix: Record<string, TimeBandScores>): string {
  const cleaned: Record<string, TimeBandScores> = {};
  for (const [band, scores] of Object.entries(matrix)) {
    const row: TimeBandScores = {};
    for (const [dim, v] of Object.entries(scores)) {
      if (typeof v === 'number' && v > 0) row[dim] = v;
    }
    if (Object.keys(row).length) cleaned[band] = row;
  }
  return JSON.stringify(cleaned);
}

// ─── API calls ────────────────────────────────────────────────────

export async function listBiaCatalog(): Promise<BiaCatalogResponse> {
  return api.get<BiaCatalogResponse>('/api/v1/bcm/bia-assessments/catalog');
}

export async function listBiaAssessments(page = 0, size = 100): Promise<{ content: BcmBiaAssessment[]; totalElements: number }> {
  return api.get<{ content: BcmBiaAssessment[]; totalElements: number }>(`/api/v1/bcm/bia-assessments?page=${page}&size=${size}`);
}

export async function biaStats(): Promise<Record<string, number>> {
  return api.get<Record<string, number>>('/api/v1/bcm/bia-assessments/stats');
}

export async function getBiaAssessment(id: number): Promise<BcmBiaAssessment> {
  return api.get<BcmBiaAssessment>(`/api/v1/bcm/bia-assessments/${id}`);
}

export async function createBiaAssessment(body: Partial<BcmBiaAssessment>): Promise<BcmBiaAssessment> {
  return api.post<BcmBiaAssessment>('/api/v1/bcm/bia-assessments', body);
}

export async function patchBiaAssessment(id: number, body: Partial<BcmBiaAssessment>): Promise<BcmBiaAssessment> {
  return api.patch<BcmBiaAssessment>(`/api/v1/bcm/bia-assessments/${id}`, body);
}

export async function transitionBiaAssessment(id: number, to: BiaStatus): Promise<BcmBiaAssessment> {
  return api.post<BcmBiaAssessment>(`/api/v1/bcm/bia-assessments/${id}/transition`, { to });
}

export async function calculateBiaCriticality(id: number): Promise<BcmBiaAssessment> {
  return api.post<BcmBiaAssessment>(`/api/v1/bcm/bia-assessments/${id}/calculate-criticality`);
}

export interface GeneratedPlanResponse {
  bia: BcmBiaAssessment;
  bcp?: { id: number; planNameEn: string; planType: string; status: string };
  drp?: { id: number; planNameEn: string; planType: string; status: string };
}

export async function generateBcpFromBia(id: number, body: {
  planNameEn?: string; planNameAr?: string; planType?: string; version?: string;
}): Promise<GeneratedPlanResponse> {
  return api.post<GeneratedPlanResponse>(`/api/v1/bcm/bia-assessments/${id}/generate-bcp`, body);
}

export async function generateDrpFromBia(id: number, body: {
  planNameEn?: string; planNameAr?: string; planType?: string; version?: string;
}): Promise<GeneratedPlanResponse> {
  return api.post<GeneratedPlanResponse>(`/api/v1/bcm/bia-assessments/${id}/generate-drp`, body);
}
