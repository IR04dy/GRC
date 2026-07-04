// Sustainability Evidence Repository API client (V64 · slice 10).
//
// Wraps /api/sustainability/evidence/*. Each sustainability entity
// (climate risk, GHG record, CMA / TCFD / SGI / materiality / metric /
// CAPA) carries an evidence_ref string today; this repository is the
// backing store that those references resolve to.
//
// Activates Rule 9 — evidence whose valid_to has passed flips to
// EXPIRED via sweepExpired(); downstream services then mark the
// linked disclosures EVIDENCE_REQUIRED.

import { api } from './api';

export type EvidenceApprovalStatus =
  | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'SUPERSEDED';

export type EvidenceConfidentiality =
  | 'INTERNAL' | 'RESTRICTED' | 'CONFIDENTIAL' | 'PUBLIC';

export type EvidenceVerificationStatus =
  | 'VERIFIED' | 'UNVERIFIED' | 'EXTERNAL_VERIFIED';

export type EvidenceModuleArea =
  | 'GHG' | 'SGI' | 'CMA' | 'TCFD' | 'MATERIALITY' | 'METRIC'
  | 'CLIMATE_RISK' | 'CORRECTIVE_ACTION' | 'ANNEX' | 'OTHER';

export interface SustainabilityEvidence {
  id?: number;
  organisationId?: number;
  evidenceCode?: string;

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  moduleArea?: EvidenceModuleArea;
  evidenceType?: string;

  fileUrl?: string;
  fileName?: string;
  version?: string;

  reportingYear?: number;
  validFrom?: string;
  validTo?: string;
  expiryDate?: string;

  confidentiality: EvidenceConfidentiality;
  verificationStatus?: EvidenceVerificationStatus;
  verificationProvider?: string;
  verificationDate?: string;

  approvalStatus: EvidenceApprovalStatus;
  rejectionReason?: string;

  digitalSignatureStatus?: 'SIGNED' | 'UNSIGNED' | 'INVALID';

  ownerName?: string;
  ownerEmail?: string;
  reviewerName?: string;
  reviewedAt?: string;
  approvedBy?: string;
  approvedAt?: string;

  supersedesId?: number;
  notes?: string;
}

export interface EvidenceAggregate {
  total: number;
  byStatus: Record<EvidenceApprovalStatus, number>;
  byModule: Record<string, number>;
  expiringSoon30d: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/evidence';

export function listEvidence(opts: {
  moduleArea?: EvidenceModuleArea; status?: EvidenceApprovalStatus;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  if (opts.moduleArea) p.set('moduleArea', opts.moduleArea);
  if (opts.status)     p.set('status', opts.status);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<SustainabilityEvidence>>(`${BASE}?${p.toString()}`);
}

export function evidenceAggregate() { return api.get<EvidenceAggregate>(`${BASE}/aggregate`); }

export function getEvidence(id: number)  { return api.get<SustainabilityEvidence>(`${BASE}/${id}`); }
export function createEvidence(b: Partial<SustainabilityEvidence>) {
  return api.post<SustainabilityEvidence>(BASE, b);
}
export function updateEvidence(id: number, b: Partial<SustainabilityEvidence>) {
  return api.put<SustainabilityEvidence>(`${BASE}/${id}`, b);
}

export function approveEvidence(id: number, approvedBy: string) {
  return api.post<SustainabilityEvidence>(`${BASE}/${id}/approve`, { approvedBy });
}
export function rejectEvidence(id: number, reviewerName: string, reason: string) {
  return api.post<SustainabilityEvidence>(`${BASE}/${id}/reject`, { reviewerName, reason });
}
export function supersedeEvidence(id: number, newRow: Partial<SustainabilityEvidence>) {
  return api.post<SustainabilityEvidence>(`${BASE}/${id}/supersede`, newRow);
}
export function sweepExpiredEvidence() {
  return api.post<SustainabilityEvidence[]>(`${BASE}/sweep-expired`);
}
export function deleteEvidence(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function evidenceStatusTone(s: EvidenceApprovalStatus):
    'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'APPROVED':       return 'success';
    case 'PENDING_REVIEW': return 'info';
    case 'REJECTED':       return 'danger';
    case 'EXPIRED':        return 'danger';
    case 'SUPERSEDED':     return 'neutral';
  }
}

export function evidenceStatusLabel(s: EvidenceApprovalStatus, lang: 'en' | 'ar'): string {
  const en: Record<EvidenceApprovalStatus, string> = {
    PENDING_REVIEW: 'Pending review', APPROVED: 'Approved',
    REJECTED: 'Rejected', EXPIRED: 'Expired', SUPERSEDED: 'Superseded',
  };
  const ar: Record<EvidenceApprovalStatus, string> = {
    PENDING_REVIEW: 'قيد المراجعة', APPROVED: 'معتمدة',
    REJECTED: 'مرفوضة', EXPIRED: 'منتهية', SUPERSEDED: 'مُستبدَلة',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function evidenceConfidentialityTone(c: EvidenceConfidentiality):
    'success' | 'info' | 'warning' | 'danger' {
  switch (c) {
    case 'PUBLIC':        return 'success';
    case 'INTERNAL':      return 'info';
    case 'RESTRICTED':    return 'warning';
    case 'CONFIDENTIAL':  return 'danger';
  }
}

export function evidenceConfidentialityLabel(c: EvidenceConfidentiality, lang: 'en' | 'ar'): string {
  const en: Record<EvidenceConfidentiality, string> = {
    INTERNAL: 'Internal', RESTRICTED: 'Restricted',
    CONFIDENTIAL: 'Confidential', PUBLIC: 'Public',
  };
  const ar: Record<EvidenceConfidentiality, string> = {
    INTERNAL: 'داخلي', RESTRICTED: 'مُقيَّد', CONFIDENTIAL: 'سرّي', PUBLIC: 'عام',
  };
  return (lang === 'ar' ? ar : en)[c];
}

export function evidenceModuleLabel(m: EvidenceModuleArea, lang: 'en' | 'ar'): string {
  const en: Record<EvidenceModuleArea, string> = {
    GHG: 'GHG inventory', SGI: 'SGI commitments', CMA: 'CMA disclosures',
    TCFD: 'TCFD alignment', MATERIALITY: 'Materiality', METRIC: 'ESG metrics',
    CLIMATE_RISK: 'Climate risks', CORRECTIVE_ACTION: 'Corrective actions',
    ANNEX: 'Annex', OTHER: 'Other',
  };
  const ar: Record<EvidenceModuleArea, string> = {
    GHG: 'الانبعاثات', SGI: 'التزامات SGI', CMA: 'إفصاحات CMA',
    TCFD: 'محاذاة TCFD', MATERIALITY: 'الأهمية النسبية', METRIC: 'المؤشّرات',
    CLIMATE_RISK: 'المخاطر المناخية', CORRECTIVE_ACTION: 'الإجراءات التصحيحية',
    ANNEX: 'الملحق', OTHER: 'أخرى',
  };
  return (lang === 'ar' ? ar : en)[m];
}

/** Days remaining until valid_to. Negative = expired. */
export function daysUntilExpiry(e: SustainabilityEvidence): number | null {
  if (!e.validTo) return null;
  const dt = new Date(e.validTo + 'T00:00:00').getTime();
  return Math.round((dt - Date.now()) / 86_400_000);
}
