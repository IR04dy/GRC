// NDI Framework Library API client (slice 2 · V67).
//
// Catalog of NDI requirements across the 3 components. Every seed row
// is flagged as placeholder until SDAIA publishes the official library
// and an import lands via /import-json.

import { api } from './api';

export type NdiComponent = 'COMPLIANCE' | 'MATURITY' | 'OPERATIONAL_EXCELLENCE';
export type NdiPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type NdiFrameworkStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED';

export interface NdiFrameworkItem {
  id?: number;
  organisationId?: number;
  frameworkVersion: string;
  component: NdiComponent;
  domain: string;
  subdomain?: string;
  requirementCode: string;

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  questionEn?: string;
  questionAr?: string;
  evidenceGuidanceEn?: string;
  evidenceGuidanceAr?: string;

  scoringMethod?: 'LEVEL_0_5' | 'BINARY' | 'PERCENT' | 'RUBRIC';
  weight: number;
  priority: NdiPriority;
  mandatory: boolean;
  ownerRole?: string;
  reviewFrequency?: 'ANNUAL' | 'SEMI_ANNUAL' | 'QUARTERLY' | 'MONTHLY';

  relatedNdmoControl?: string;
  relatedPdplRequirement?: string;
  relatedCyberControl?: string;
  relatedIsoMapping?: string;

  isOfficialImport: boolean;
  isPlaceholder: boolean;

  status: NdiFrameworkStatus;
  effectiveDate?: string;
  notes?: string;
}

export interface NdiFrameworkAggregate {
  total: number;
  placeholderCount: number;
  officialCount: number;
  mandatoryCount: number;
  byComponent: Record<NdiComponent, number>;
  byDomain: Record<string, number>;
  byPriority: Record<NdiPriority, number>;
}

export interface ImportSummary {
  inserted: number;
  updated: number;
  failed: number;
  errors: Array<{ row: string; requirementCode: string; error: string }>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/framework';

export function listFramework(opts: { component?: NdiComponent; domain?: string; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  if (opts.component) p.set('component', opts.component);
  if (opts.domain)    p.set('domain', opts.domain);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<NdiFrameworkItem>>(`${BASE}?${p.toString()}`);
}

export function frameworkAggregate() { return api.get<NdiFrameworkAggregate>(`${BASE}/aggregate`); }

export function getFrameworkItem(id: number) { return api.get<NdiFrameworkItem>(`${BASE}/${id}`); }

export function createFrameworkItem(body: Partial<NdiFrameworkItem>) {
  return api.post<NdiFrameworkItem>(BASE, body);
}

export function updateFrameworkItem(id: number, body: Partial<NdiFrameworkItem>) {
  return api.put<NdiFrameworkItem>(`${BASE}/${id}`, body);
}

export function markOfficial(id: number) {
  return api.post<NdiFrameworkItem>(`${BASE}/${id}/mark-official`);
}

export function markPlaceholder(id: number) {
  return api.post<NdiFrameworkItem>(`${BASE}/${id}/mark-placeholder`);
}

export function importFrameworkJson(items: Partial<NdiFrameworkItem>[]) {
  return api.post<ImportSummary>(`${BASE}/import-json`, { items });
}

export function deleteFrameworkItem(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

export function componentTone(c: NdiComponent):
    'info' | 'primary' | 'success' {
  switch (c) {
    case 'COMPLIANCE':              return 'info';
    case 'MATURITY':                return 'primary';
    case 'OPERATIONAL_EXCELLENCE':  return 'success';
  }
}

export function componentLabel(c: NdiComponent, lang: 'en' | 'ar'): string {
  const en: Record<NdiComponent, string> = {
    COMPLIANCE: 'Compliance',
    MATURITY: 'Maturity',
    OPERATIONAL_EXCELLENCE: 'Operational excellence',
  };
  const ar: Record<NdiComponent, string> = {
    COMPLIANCE: 'الامتثال',
    MATURITY: 'النضج',
    OPERATIONAL_EXCELLENCE: 'التميز التشغيلي',
  };
  return (lang === 'ar' ? ar : en)[c];
}

export function priorityTone(p: NdiPriority):
    'danger' | 'warning' | 'info' | 'neutral' {
  switch (p) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
  }
}

export function priorityLabel(p: NdiPriority, lang: 'en' | 'ar'): string {
  const en: Record<NdiPriority, string> = {
    CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low',
  };
  const ar: Record<NdiPriority, string> = {
    CRITICAL: 'حرج', HIGH: 'مرتفع', MEDIUM: 'متوسط', LOW: 'منخفض',
  };
  return (lang === 'ar' ? ar : en)[p];
}
