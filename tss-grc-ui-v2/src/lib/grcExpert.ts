// GRC Expert — subject-matter-expert surface that links framework controls
// to the live evidence lifecycle.  Sprint 33+.

import { api } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type ExpertFamily = 'NCA' | 'NDI' | 'PDPL' | 'BCM' | 'OTHER';
/** EXPIRED = approved evidence exists but all of it is past valid_to (stale) — distinct from COVERED. */
export type CoverageStatus = 'COVERED' | 'PARTIAL' | 'GAP' | 'EXPIRED';

export interface ExpertSubControl {
  id: number;
  code: string;
  nameEn: string;
  nameAr: string;
  /** Normative guidance on what evidence satisfies this sub-control. */
  evidenceGuidance: string;
  /** Arabic version of the recommended-evidence guidance. */
  evidenceGuidanceAr?: string;
  /** Total evidence items linked. */
  total: number;
  approved: number;
  pendingReview: number;
  draft: number;
  rejected: number;
  expiringSoon: number;
  expired: number;
  coverage: CoverageStatus;
}

export interface ExpertControl {
  id: number;
  code: string;
  nameEn: string;
  nameAr: string;
  subControlCount: number;
  coveredCount: number;
  partialCount: number;
  gapCount: number;
  subControls: ExpertSubControl[];
}

export interface ExpertFramework {
  id: number;
  code: string;
  family: ExpertFamily;
  nameEn: string;
  nameAr: string;
  version?: string;
  issuer?: string;
  descriptionEn?: string;
  controlCount: number;
  subControlCount: number;
  coveredCount: number;
  partialCount: number;
  gapCount: number;
  controls: ExpertControl[];
}

export interface RaiseEvidenceRequestInput {
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  subControlId: number;
  priority?: 'LOW' | 'MEDIUM' | 'HIGH';
}

// ─── API functions ────────────────────────────────────────────────────────────

export function listExpertCatalog(): Promise<ExpertFramework[]> {
  return api.get<ExpertFramework[]>('/api/v1/evidence/expert');
}

export function raiseEvidenceRequest(input: RaiseEvidenceRequestInput): Promise<unknown> {
  return api.post<unknown>('/api/v1/evidence/requests', input);
}

export function listSubControlEvidence(subControlId: number): Promise<unknown[]> {
  return api.get<unknown[]>(`/api/v1/evidence/items/by-sub-control/${subControlId}`);
}

// ─── Label maps ───────────────────────────────────────────────────────────────

export const FAMILY_LABELS: Record<ExpertFamily, { en: string; ar: string }> = {
  NCA:   { en: 'NCA',      ar: 'هيئة الأمن السيبراني' },
  NDI:   { en: 'NDI',      ar: 'إدارة البيانات الوطنية' },
  PDPL:  { en: 'PDPL',     ar: 'نظام حماية البيانات' },
  BCM:   { en: 'BCM',      ar: 'استمرارية الأعمال' },
  OTHER: { en: 'Other',    ar: 'أطر أخرى' },
};

export const COVERAGE_TONE: Record<CoverageStatus, 'success' | 'warning' | 'danger'> = {
  COVERED: 'success',
  PARTIAL: 'warning',
  EXPIRED: 'warning',
  GAP:     'danger',
};

export const COVERAGE_LABEL: Record<CoverageStatus, { en: string; ar: string }> = {
  COVERED: { en: 'Covered',  ar: 'مغطّى' },
  PARTIAL: { en: 'Partial',  ar: 'جزئي' },
  EXPIRED: { en: 'Expired',  ar: 'منتهي الصلاحية' },
  GAP:     { en: 'Gap',      ar: 'فجوة' },
};
