// Climate Risk & Opportunity API client (Sustainability module · V55).
//
// Mirrors the backend `/api/sustainability/climate-risks/*` controller.
// Use this from /esg page's new "Climate Risks" tab.

import { api } from './api';

export type CroKind   = 'RISK' | 'OPPORTUNITY';
export type CroStatus = 'IDENTIFIED' | 'ASSESSED' | 'TREATED' | 'CLOSED' | 'RETIRED';
export type CroHorizon = 'SHORT' | 'MEDIUM' | 'LONG';

export type CroRiskCategory =
  'PHYSICAL' | 'TRANSITION' | 'REGULATORY' | 'MARKET'
  | 'TECHNOLOGY' | 'REPUTATION' | 'SUPPLY_CHAIN';
export type CroOppCategory  =
  'RESOURCE_EFFICIENCY' | 'ENERGY_SOURCE' | 'PRODUCT_SERVICE' | 'MARKETS' | 'RESILIENCE';

export interface ClimateRiskOpportunity {
  id?: number;
  organisationId?: number;
  externalId?: string;
  itemCode?: string;

  reportingYear: number;
  kind: CroKind;
  category: CroRiskCategory | CroOppCategory | string;

  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;

  timeHorizon: CroHorizon;
  likelihood: number;     // 1..5
  impact: number;         // 1..5
  riskScore?: number;     // computed server-side
  financialImpactSar?: number;

  relatedService?: string;
  relatedSupplier?: string;
  relatedAsset?: string;
  relatedErmRiskId?: number;
  relatedTcfdRecommendation?: string;

  controlsSummary?: string;
  treatmentPlan?: string;
  metricsSummary?: string;

  evidenceRef?: string;
  ownerName?: string;
  ownerEmail?: string;

  status?: CroStatus;
  identifiedAt?: string;
  assessedAt?: string;
  treatedAt?: string;
  closedAt?: string;
  reviewDate?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/climate-risks';

export function listClimateItems(opts: { kind?: CroKind; status?: CroStatus; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  if (opts.kind)   p.set('kind', opts.kind);
  if (opts.status) p.set('status', opts.status);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<ClimateRiskOpportunity>>(`${BASE}?${p.toString()}`);
}

export function listTopClimateItems() {
  return api.get<ClimateRiskOpportunity[]>(`${BASE}/top`);
}

export function getClimateItem(id: number) {
  return api.get<ClimateRiskOpportunity>(`${BASE}/${id}`);
}

export function createClimateItem(body: Partial<ClimateRiskOpportunity>) {
  return api.post<ClimateRiskOpportunity>(BASE, body);
}

export function updateClimateItem(id: number, body: Partial<ClimateRiskOpportunity>) {
  return api.put<ClimateRiskOpportunity>(`${BASE}/${id}`, body);
}

export function assessClimateItem(id: number)  { return api.post<ClimateRiskOpportunity>(`${BASE}/${id}/assess`); }
export function treatClimateItem(id: number)   { return api.post<ClimateRiskOpportunity>(`${BASE}/${id}/treat`); }
export function closeClimateItem(id: number)   { return api.post<ClimateRiskOpportunity>(`${BASE}/${id}/close`); }
export function retireClimateItem(id: number)  { return api.post<ClimateRiskOpportunity>(`${BASE}/${id}/retire`); }
export function deleteClimateItem(id: number)  { return api.delete<void>(`${BASE}/${id}`); }

/** Rule 8 — link a climate item to an ERM risk by id. */
export function linkClimateItemToErm(id: number, ermRiskId: number) {
  return api.post<ClimateRiskOpportunity>(`${BASE}/${id}/link-erm`, { ermRiskId });
}

// ─── Display helpers ────────────────────────────────────────────────────

/** Map a 1..25 risk score to a tone for badges / heatmap shading. */
export function riskScoreTone(score: number): 'success' | 'info' | 'warning' | 'danger' {
  if (score >= 15) return 'danger';   // High or Critical
  if (score >= 9)  return 'warning';  // Medium-High
  if (score >= 4)  return 'info';     // Medium
  return 'success';                    // Low
}

export function riskScoreBand(score: number, lang: 'en' | 'ar'): string {
  const en = score >= 20 ? 'Critical' : score >= 15 ? 'High' : score >= 9 ? 'Medium' : score >= 4 ? 'Low' : 'Minimal';
  const ar = score >= 20 ? 'حرجة'    : score >= 15 ? 'عالية' : score >= 9 ? 'متوسطة' : score >= 4 ? 'منخفضة' : 'ضئيلة';
  return lang === 'ar' ? ar : en;
}

/** Rule 8 trigger — whether the UI should offer "Link to ERM" for this item. */
export function shouldOfferErmLink(item: ClimateRiskOpportunity): boolean {
  return item.kind === 'RISK'
    && (item.riskScore ?? 0) >= 15
    && !item.relatedErmRiskId;
}
