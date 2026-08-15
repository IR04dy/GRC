// NDI Evidence Repository API client (slice 7 · V72).
//
// Metadata pointers to evidence artefacts feeding the NDI submission
// package. Status auto-flips ACTIVE → EXPIRED server-side when
// expiry_date < today via Rule 6.

import { api } from './api';

export type NdiEvidenceType =
  | 'POLICY' | 'PROCEDURE' | 'REPORT' | 'SCREENSHOT'
  | 'ATTESTATION' | 'CERTIFICATE' | 'AUDIT_LOG' | 'DATASET' | 'OTHER';

export type NdiEvidenceClassification =
  | 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';

export type NdiEvidenceStatus =
  | 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'SUPERSEDED' | 'REJECTED';

export interface NdiEvidenceItem {
  id?: number;
  organisationId?: number;
  cycleId?: number;
  evidenceCode?: string;

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  evidenceType: NdiEvidenceType;
  classification: NdiEvidenceClassification;

  fileRef?: string;
  mimeType?: string;
  fileSizeBytes?: number;
  sourceSystem?: string;

  ownerName?: string;
  ownerEmail?: string;
  reviewerName?: string;

  effectiveDate?: string;
  expiryDate?: string;
  reviewedAt?: string;

  status: NdiEvidenceStatus;
  supersedesId?: number;
  rejectionReason?: string;

  tags?: string;
  notes?: string;
}

export interface NdiEvidenceAggregate {
  total: number;
  activeCount: number;
  pendingCount: number;
  expiredCount: number;
  rejectedCount: number;
  supersededCount: number;
  expiringSoonCount: number;
  byType: Record<string, number>;
  byClassification: Record<string, number>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/evidence';

export function listEvidence(opts: {
  cycleId?: number; type?: NdiEvidenceType; status?: NdiEvidenceStatus;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  if (opts.cycleId != null) p.set('cycleId', String(opts.cycleId));
  if (opts.type)            p.set('type', opts.type);
  if (opts.status)          p.set('status', opts.status);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<Page<NdiEvidenceItem>>(`${BASE}?${p.toString()}`);
}

export function evidenceAggregate() {
  return api.get<NdiEvidenceAggregate>(`${BASE}/aggregate`);
}

export function expiringSoon(days = 60) {
  return api.get<NdiEvidenceItem[]>(`${BASE}/expiring-soon?days=${days}`);
}

export function getEvidence(id: number) {
  return api.get<NdiEvidenceItem>(`${BASE}/${id}`);
}

export function createEvidence(body: Partial<NdiEvidenceItem>) {
  return api.post<NdiEvidenceItem>(BASE, body);
}

export function updateEvidence(id: number, body: Partial<NdiEvidenceItem>) {
  return api.put<NdiEvidenceItem>(`${BASE}/${id}`, body);
}

// F2-218: no `reviewerName` parameter. SEC-NDI-003 resolves the reviewer from the
// authenticated principal server-side and ignores any body field, so the old parameter was
// inert — and the one call site passed the literal 'admin', which read as the client naming
// who approved the evidence. A parameter the server discards is a claim about where the
// attribution comes from.
export function approveEvidence(id: number) {
  return api.post<NdiEvidenceItem>(`${BASE}/${id}/approve`);
}

export function rejectEvidence(id: number, reason: string) {
  return api.post<NdiEvidenceItem>(`${BASE}/${id}/reject`, { reason });
}

export function supersedeEvidence(oldId: number, newId: number) {
  return api.post<NdiEvidenceItem>(`${BASE}/${oldId}/supersede-by/${newId}`);
}

export function runExpiryCheck() {
  return api.post<{ flippedCount: number; flippedIds: number[] }>(`${BASE}/check-expiry`);
}

export function deleteEvidence(id: number) {
  return api.delete<void>(`${BASE}/${id}`);
}

// ─── UI helpers ───────────────────────────────────────────────────────

export function evidenceStatusTone(s: NdiEvidenceStatus):
    'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'ACTIVE':     return 'success';
    case 'PENDING':    return 'info';
    case 'EXPIRED':    return 'danger';
    case 'REJECTED':   return 'danger';
    case 'SUPERSEDED': return 'neutral';
  }
}

export function evidenceStatusLabel(s: NdiEvidenceStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiEvidenceStatus, string> = {
    PENDING: 'Pending', ACTIVE: 'Active', EXPIRED: 'Expired',
    SUPERSEDED: 'Superseded', REJECTED: 'Rejected',
  };
  const ar: Record<NdiEvidenceStatus, string> = {
    PENDING: 'قيد المراجعة', ACTIVE: 'سارٍ', EXPIRED: 'منتهٍ',
    SUPERSEDED: 'مُستبدَل', REJECTED: 'مرفوض',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function evidenceTypeLabel(t: NdiEvidenceType, lang: 'en' | 'ar'): string {
  const en: Record<NdiEvidenceType, string> = {
    POLICY: 'Policy', PROCEDURE: 'Procedure', REPORT: 'Report',
    SCREENSHOT: 'Screenshot', ATTESTATION: 'Attestation',
    CERTIFICATE: 'Certificate', AUDIT_LOG: 'Audit log',
    DATASET: 'Dataset', OTHER: 'Other',
  };
  const ar: Record<NdiEvidenceType, string> = {
    POLICY: 'سياسة', PROCEDURE: 'إجراء', REPORT: 'تقرير',
    SCREENSHOT: 'لقطة شاشة', ATTESTATION: 'تصريح',
    CERTIFICATE: 'شهادة', AUDIT_LOG: 'سجل تدقيق',
    DATASET: 'مجموعة بيانات', OTHER: 'أخرى',
  };
  return (lang === 'ar' ? ar : en)[t];
}

export function classificationTone(c: NdiEvidenceClassification):
    'success' | 'info' | 'warning' | 'danger' {
  switch (c) {
    case 'PUBLIC':       return 'success';
    case 'INTERNAL':     return 'info';
    case 'CONFIDENTIAL': return 'warning';
    case 'RESTRICTED':   return 'danger';
  }
}

export function classificationLabel(c: NdiEvidenceClassification, lang: 'en' | 'ar'): string {
  const en: Record<NdiEvidenceClassification, string> = {
    PUBLIC: 'Public', INTERNAL: 'Internal', CONFIDENTIAL: 'Confidential', RESTRICTED: 'Restricted',
  };
  const ar: Record<NdiEvidenceClassification, string> = {
    PUBLIC: 'عام', INTERNAL: 'داخلي', CONFIDENTIAL: 'سرّي', RESTRICTED: 'مقيَّد',
  };
  return (lang === 'ar' ? ar : en)[c];
}

export function daysUntil(date: string | undefined): number | null {
  if (!date) return null;
  const d = new Date(date + 'T00:00:00');
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  return Math.round((d.getTime() - now.getTime()) / 86400000);
}
