// ESG Materiality API client (Sustainability · V62 · slice 8).
//
// Wraps /api/sustainability/materiality/*. S-Q: each topic is scored on two
// INDEPENDENT double-materiality axes — impact (inside-out: the org's impact on
// people/environment) and financial (outside-in: the topic's effect on the
// org's finances), 0..5. materialityScore = max(impact, financial) — material
// if material from EITHER axis — computed server-side; tier bucketed from it.

import { api } from './api';

export type MaterialStatus = 'DRAFT' | 'SCORED' | 'APPROVED' | 'ARCHIVED';
export type MaterialCategory = 'ENVIRONMENTAL' | 'SOCIAL' | 'GOVERNANCE';
export type MaterialTier = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface MaterialTopic {
  id?: number;
  organisationId?: number;
  topicCode?: string;
  reportingYear: number;
  category: MaterialCategory;

  topicEn: string;
  topicAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  stakeholderImportance: number;
  businessImpact: number;
  /** S-Q double-materiality axes (0..5). */
  impactMateriality?: number;
  financialMateriality?: number;
  materialityScore?: number;
  priorityTier?: MaterialTier;
  stakeholderGroups?: string;

  status: MaterialStatus;

  linkedCmaDisclosureId?: number;
  linkedClimateRiskId?: number;
  linkedCapaId?: number;

  evidenceRef?: string;
  ownerName?: string;
  ownerEmail?: string;
  notes?: string;

  approvedAt?: string;
  approvedBy?: string;
}

export interface MaterialMatrixEntry {
  id: number;
  topicCode?: string;
  topicEn: string;
  topicAr?: string;
  category: MaterialCategory;
  impactMateriality: number;
  financialMateriality: number;
  stakeholderImportance: number;
  businessImpact: number;
  materialityScore: number;
  priorityTier: MaterialTier;
  materialFromImpact?: boolean;
  materialFromFinancial?: boolean;
  doubleMaterial?: boolean;
  status: MaterialStatus;
}

export interface MaterialMatrix {
  year: number;
  total: number;
  byTier: Record<MaterialTier, number>;
  byCategory: Record<MaterialCategory, number>;
  criticalCount: number;
  highCount: number;
  /** S-Q double-materiality quadrant rollup. */
  impactMaterialCount?: number;
  financialMaterialCount?: number;
  doubleMaterialCount?: number;
  materialThreshold?: number;
  matrix: MaterialMatrixEntry[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/materiality';

export function listMateriality(opts: {
  year?: number; category?: MaterialCategory; status?: MaterialStatus;
  page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  p.set('year', String(opts.year ?? 2025));
  if (opts.category) p.set('category', opts.category);
  if (opts.status)   p.set('status', opts.status);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 50));
  return api.get<Page<MaterialTopic>>(`${BASE}?${p.toString()}`);
}

export function generateMatrix(year = 2025) {
  return api.get<MaterialMatrix>(`${BASE}/generate-matrix?year=${year}`);
}

export function getMaterialTopic(id: number) { return api.get<MaterialTopic>(`${BASE}/${id}`); }
export function createMaterialTopic(b: Partial<MaterialTopic>) { return api.post<MaterialTopic>(BASE, b); }
export function updateMaterialTopic(id: number, b: Partial<MaterialTopic>) { return api.put<MaterialTopic>(`${BASE}/${id}`, b); }

export function scoreMaterialTopic(id: number, impactMateriality: number, financialMateriality: number) {
  return api.post<MaterialTopic>(`${BASE}/${id}/score`, { impactMateriality, financialMateriality });
}
// F2-236 — no approvedBy. The server required this field and never verified it, while stamping
// approvedAt from its own clock; it now takes the approver from the authenticated principal.
export function approveMaterialTopic(id: number) {
  return api.post<MaterialTopic>(`${BASE}/${id}/approve`);
}
export function archiveMaterialTopic(id: number) {
  return api.post<MaterialTopic>(`${BASE}/${id}/archive`);
}
export function deleteMaterialTopic(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function materialTierTone(t: MaterialTier):
    'danger' | 'warning' | 'info' | 'neutral' {
  switch (t) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
  }
}

export function materialTierLabel(t: MaterialTier, lang: 'en' | 'ar'): string {
  const en: Record<MaterialTier, string> = {
    CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low',
  };
  const ar: Record<MaterialTier, string> = {
    CRITICAL: 'حرج', HIGH: 'مرتفع', MEDIUM: 'متوسط', LOW: 'منخفض',
  };
  return (lang === 'ar' ? ar : en)[t];
}

export function materialCategoryLabel(c: MaterialCategory, lang: 'en' | 'ar'): string {
  const en: Record<MaterialCategory, string> = {
    ENVIRONMENTAL: 'Environmental', SOCIAL: 'Social', GOVERNANCE: 'Governance',
  };
  const ar: Record<MaterialCategory, string> = {
    ENVIRONMENTAL: 'البيئة', SOCIAL: 'الاجتماع', GOVERNANCE: 'الحوكمة',
  };
  return (lang === 'ar' ? ar : en)[c];
}

export function materialStatusTone(s: MaterialStatus):
    'success' | 'info' | 'warning' | 'neutral' {
  switch (s) {
    case 'APPROVED': return 'success';
    case 'SCORED':   return 'info';
    case 'DRAFT':    return 'warning';
    case 'ARCHIVED': return 'neutral';
  }
}

export function materialStatusLabel(s: MaterialStatus, lang: 'en' | 'ar'): string {
  const en: Record<MaterialStatus, string> = {
    DRAFT: 'Draft', SCORED: 'Scored', APPROVED: 'Approved', ARCHIVED: 'Archived',
  };
  const ar: Record<MaterialStatus, string> = {
    DRAFT: 'مسوّدة', SCORED: 'مُقيَّمة', APPROVED: 'معتمدة', ARCHIVED: 'مؤرشفة',
  };
  return (lang === 'ar' ? ar : en)[s];
}
