// Real evidence-engine client — /api/v1/evidence/items/*  (module/evidence).
//
// This is the CANONICAL evidence store the S-C epic wired the sustainability
// domain onto: real binary file upload with a server-side SHA-256 hash, plus a
// proper approval lifecycle:
//
//   DRAFT ──submit──▶ PENDING_REVIEW ──approve──▶ APPROVED ──(valid_to)──▶ EXPIRED
//                          │  └─reject──▶ REJECTED ─┐
//                          └─────────── resubmit ◀──┘  (REJECTED/EXPIRED/NEEDS_REVISION → PENDING_REVIEW)
//
// The old `/api/sustainability/evidence` silo (sustainabilityEvidence.ts) is
// superseded by this; the ESG evidence tab now speaks to this engine and links
// items back to ESG entities via the S-C bridge (see sustEvidenceLinks.ts).

import { api, uploadFile, type PageResponse } from './api';

export type EvidenceItemStatus =
  | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'NEEDS_REVISION' | 'EXPIRED';

export interface EvidenceItem {
  id: number;
  externalId?: string;
  evidenceCode?: string;
  organisationId?: number;
  subControlId?: number;
  subControlCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  evidenceType: string;
  sourceModule?: string;
  ownerId?: number;
  ownerName?: string;
  version?: string;
  validFrom?: string;
  validTo?: string;
  isExpired?: boolean;
  status: EvidenceItemStatus;
  fileName?: string;
  fileSize?: number;
  mimeType?: string;
  createdAt?: string;
  updatedAt?: string;
  collectedAt?: string;
}

export interface EvidenceItemCreate {
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  evidenceType: string;
  sourceModule?: string;
  sourceEntityId?: number;
  version?: string;
  validFrom?: string;
  validTo?: string;
  status?: string;
}

const BASE = '/api/v1/evidence/items';

export function listEvidenceItems(opts: { status?: string; type?: string; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  if (opts.status) p.set('status', opts.status);
  if (opts.type)   p.set('type', opts.type);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<PageResponse<EvidenceItem>>(`${BASE}?${p.toString()}`);
}

export function getEvidenceItem(id: number)  { return api.get<EvidenceItem>(`${BASE}/${id}`); }
export function createEvidenceItem(body: EvidenceItemCreate) { return api.post<EvidenceItem>(BASE, body); }
export function updateEvidenceItem(id: number, body: EvidenceItemCreate) { return api.put<EvidenceItem>(`${BASE}/${id}`, body); }

/** Multipart upload of the real document; the backend computes the SHA-256. */
export function uploadEvidenceItemFile(id: number, file: File) {
  return uploadFile<EvidenceItem>(`${BASE}/${id}/file`, file);
}

export function submitEvidenceItem(id: number)  { return api.post<EvidenceItem>(`${BASE}/${id}/submit`); }
export function approveEvidenceItem(id: number) { return api.post<EvidenceItem>(`${BASE}/${id}/approve`); }
export function rejectEvidenceItem(id: number)  { return api.post<EvidenceItem>(`${BASE}/${id}/reject`); }
export function deleteEvidenceItem(id: number)  { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────
export function evidenceItemStatusTone(s: EvidenceItemStatus):
    'success' | 'info' | 'warning' | 'danger' | 'neutral' {
  switch (s) {
    case 'APPROVED':       return 'success';
    case 'PENDING_REVIEW': return 'info';
    case 'DRAFT':          return 'neutral';
    case 'NEEDS_REVISION': return 'warning';
    case 'REJECTED':       return 'danger';
    case 'EXPIRED':        return 'danger';
  }
}

export function evidenceItemStatusLabel(s: EvidenceItemStatus, lang: 'en' | 'ar'): string {
  const en: Record<EvidenceItemStatus, string> = {
    DRAFT: 'Draft', PENDING_REVIEW: 'Pending review', APPROVED: 'Approved',
    REJECTED: 'Rejected', NEEDS_REVISION: 'Needs revision', EXPIRED: 'Expired',
  };
  const ar: Record<EvidenceItemStatus, string> = {
    DRAFT: 'مسودة', PENDING_REVIEW: 'قيد المراجعة', APPROVED: 'معتمدة',
    REJECTED: 'مرفوضة', NEEDS_REVISION: 'تتطلب تعديلاً', EXPIRED: 'منتهية',
  };
  return (lang === 'ar' ? ar : en)[s];
}

/** DRAFT / REJECTED / NEEDS_REVISION / EXPIRED can (re)submit for review. */
export function canSubmitEvidence(s: EvidenceItemStatus): boolean {
  return s === 'DRAFT' || s === 'REJECTED' || s === 'NEEDS_REVISION' || s === 'EXPIRED';
}
