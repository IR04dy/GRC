// Authority Documents catalog client — Framework Intersection F1.
//
// Wraps /api/v1/regulatory/authority-documents and the citation
// sub-endpoints. Mirrors the backend's schema exactly.

import { api } from './api';

export type AuthorityFamily =
  | 'NCA Cyber' | 'SAMA' | 'Privacy' | 'BCM' | 'Risk'
  | 'Third-Party' | 'Digital-Gov' | 'Resilience' | 'International' | 'Cyber';

export type AuthorityStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'SUPERSEDED';
export type TranslationStatus = 'EN_ONLY' | 'AR_AUTO' | 'AR_HUMAN_REVIEWED' | 'AR_OFFICIAL';

export interface AuthorityDocument {
  id: number;
  organisationId?: number;
  externalId?: string;
  code: string;
  family: string;
  jurisdiction: string;
  issuer: string;
  version: string;
  titleEn: string;
  titleAr?: string;
  summaryEn?: string;
  summaryAr?: string;
  applicabilityJson?: string;
  effectiveDateG?: string;
  effectiveDateH?: string;
  isPlaceholder: boolean;
  translationStatus: TranslationStatus;
  status: AuthorityStatus;
  supersededById?: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthorityDocumentCitation {
  id?: number;
  organisationId?: number;
  authorityDocumentId: number;
  citationCode: string;
  parentCitationCode?: string;
  depth: number;
  sequenceOrder: number;
  titleEn: string;
  titleAr?: string;
  requirementTextEn?: string;
  requirementTextAr?: string;
  intentEn?: string;
  intentAr?: string;
  testingGuidanceEn?: string;
  testingGuidanceAr?: string;
  weight: number;
  mandatory: boolean;
  isPlaceholder: boolean;
  translationStatus: TranslationStatus;
}

const BASE = '/api/v1/regulatory';

// ─── Documents ────────────────────────────────────────────────────

export function listAuthorityDocuments(family?: string, status?: AuthorityStatus) {
  const qs = new URLSearchParams();
  if (family) qs.set('family', family);
  if (status) qs.set('status', status);
  const q = qs.toString();
  return api.get<AuthorityDocument[]>(`${BASE}/authority-documents${q ? `?${q}` : ''}`);
}

export function getAuthorityDocument(id: number) {
  return api.get<AuthorityDocument>(`${BASE}/authority-documents/${id}`);
}

export function rollupAuthorityDocumentsByFamily() {
  return api.get<Record<string, number>>(`${BASE}/authority-documents/rollup`);
}

export function createAuthorityDocument(body: Partial<AuthorityDocument>) {
  return api.post<AuthorityDocument>(`${BASE}/authority-documents`, body);
}

export function updateAuthorityDocument(id: number, body: Partial<AuthorityDocument>) {
  return api.put<AuthorityDocument>(`${BASE}/authority-documents/${id}`, body);
}

export function transitionAuthorityDocument(id: number, to: AuthorityStatus, supersededById?: number) {
  return api.post<AuthorityDocument>(`${BASE}/authority-documents/${id}/transition`, { to, supersededById });
}

export function deleteAuthorityDocument(id: number) {
  return api.delete<void>(`${BASE}/authority-documents/${id}`);
}

// ─── Citations ────────────────────────────────────────────────────

export function listAuthorityDocumentCitations(authorityDocumentId: number) {
  return api.get<AuthorityDocumentCitation[]>(
    `${BASE}/authority-documents/${authorityDocumentId}/citations`);
}

export function createCitation(authorityDocumentId: number, body: Partial<AuthorityDocumentCitation>) {
  return api.post<AuthorityDocumentCitation>(
    `${BASE}/authority-documents/${authorityDocumentId}/citations`, body);
}

export function updateCitation(id: number, body: Partial<AuthorityDocumentCitation>) {
  return api.put<AuthorityDocumentCitation>(`${BASE}/citations/${id}`, body);
}

export function deleteCitation(id: number) {
  return api.delete<void>(`${BASE}/citations/${id}`);
}

// ─── UI helpers ───────────────────────────────────────────────────

export function authorityStatusTone(s: AuthorityStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (s) {
    case 'DRAFT':      return 'neutral';
    case 'ACTIVE':     return 'success';
    case 'DEPRECATED': return 'warning';
    case 'SUPERSEDED': return 'danger';
  }
}

export function translationTone(t: TranslationStatus): 'danger' | 'warning' | 'info' | 'success' {
  switch (t) {
    case 'EN_ONLY':            return 'danger';
    case 'AR_AUTO':            return 'warning';
    case 'AR_HUMAN_REVIEWED':  return 'info';
    case 'AR_OFFICIAL':        return 'success';
  }
}

export function translationLabel(t: TranslationStatus, lang: 'en' | 'ar'): string {
  const en: Record<TranslationStatus, string> = {
    EN_ONLY: 'EN only', AR_AUTO: 'AR auto', AR_HUMAN_REVIEWED: 'AR reviewed', AR_OFFICIAL: 'AR official',
  };
  const ar: Record<TranslationStatus, string> = {
    EN_ONLY: 'إنجليزية فقط', AR_AUTO: 'ترجمة آلية', AR_HUMAN_REVIEWED: 'مراجعة بشرية', AR_OFFICIAL: 'النص الرسمي',
  };
  return (lang === 'ar' ? ar : en)[t];
}

/** Build a tree from a flat citation list using parent_citation_code. */
export function citationsToTree(flat: AuthorityDocumentCitation[]): CitationNode[] {
  const byCode = new Map<string, CitationNode>();
  const roots: CitationNode[] = [];
  // Sort by sequence so children appear in author-intended order
  const sorted = [...flat].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
  for (const c of sorted) byCode.set(c.citationCode, { citation: c, children: [] });
  for (const c of sorted) {
    const node = byCode.get(c.citationCode)!;
    if (c.parentCitationCode && byCode.has(c.parentCitationCode)) {
      byCode.get(c.parentCitationCode)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

export interface CitationNode {
  citation: AuthorityDocumentCitation;
  children: CitationNode[];
}
