// BCM compliance mapping client — audit Tier-1 #5.

import { api } from './api';

export type Framework = 'ISO_22301' | 'SAMA_OPRES' | 'DGA' | 'NCEMA' | 'INTERNAL' | 'CUSTOM';
export type ComplianceStatus =
  | 'NOT_ASSESSED' | 'COMPLIANT' | 'PARTIAL' | 'NON_COMPLIANT' | 'NOT_APPLICABLE';
export type RelatedWorkflow =
  | 'GOVERNANCE' | 'BIA' | 'BCP' | 'DRP' | 'EXERCISE' | 'CRISIS'
  | 'CALL_TREE' | 'STRATEGY' | 'TRAINING' | 'EVIDENCE' | 'OTHER';

export interface BcmComplianceMapping {
  id: number;
  organisationId?: number;
  sourceFramework: Framework;
  requirementCode: string;
  requirementTitleEn: string;
  requirementTitleAr?: string;
  requirementDescription?: string;
  domain?: string;
  isPlaceholder: boolean;
  relatedWorkflow?: RelatedWorkflow;
  relatedRecordType?: string;
  relatedRecordId?: number;
  relatedControlId?: number;
  ownerId?: number;
  evidenceId?: number;
  complianceStatus: ComplianceStatus;
  assessedAt?: string;
  assessedBy?: string;
  notes?: string;
  gapId?: number;
}

export const FRAMEWORK_LABELS: Record<Framework, { en: string; ar: string }> = {
  ISO_22301:  { en: 'ISO 22301',  ar: 'ISO 22301' },
  SAMA_OPRES: { en: 'SAMA OPRES', ar: 'إطار SAMA للمرونة التشغيلية' },
  DGA:        { en: 'DGA',        ar: 'هيئة الحكومة الرقمية' },
  NCEMA:      { en: 'NCEMA',      ar: 'المركز الوطني للأزمات' },
  INTERNAL:   { en: 'Internal',   ar: 'داخلي' },
  CUSTOM:     { en: 'Custom',     ar: 'مخصص' },
};

export function statusTone(s: ComplianceStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (s) {
    case 'COMPLIANT':       return 'success';
    case 'PARTIAL':         return 'warning';
    case 'NON_COMPLIANT':   return 'danger';
    case 'NOT_APPLICABLE':  return 'neutral';
    case 'NOT_ASSESSED':    return 'info';
  }
}

const BASE = '/api/v1/bcm/compliance';

export async function listCompliance(framework?: Framework): Promise<BcmComplianceMapping[]> {
  const q = framework ? `?framework=${framework}` : '';
  return api.get<BcmComplianceMapping[]>(`${BASE}${q}`);
}

export async function coverageByFramework(): Promise<Record<string, Record<string, number>>> {
  return api.get<Record<string, Record<string, number>>>(`${BASE}/coverage`);
}

export async function overallStats(): Promise<Record<string, number>> {
  return api.get<Record<string, number>>(`${BASE}/stats`);
}

export async function getMapping(id: number): Promise<BcmComplianceMapping> {
  return api.get<BcmComplianceMapping>(`${BASE}/${id}`);
}

// F2-234 — there is no `assessedBy` here on purpose. The server takes the assessing identity from
// the authenticated principal; sending one would be a field the server ignores. It used to be a
// window.prompt asking the operator to TYPE the reviewer's name, with `?? ''` on Cancel, so the
// recorded assessor of an ISO 22301 / SAMA OPRES / NCEMA requirement could be anybody at all.
export async function assess(id: number, status: ComplianceStatus, notes: string): Promise<BcmComplianceMapping> {
  return api.post<BcmComplianceMapping>(`${BASE}/${id}/assess`, { status, notes });
}

export async function attachEvidence(id: number, evidenceId: number): Promise<BcmComplianceMapping> {
  return api.post<BcmComplianceMapping>(`${BASE}/${id}/attach-evidence`, { evidenceId });
}

export async function attachGap(id: number, gapId: number): Promise<BcmComplianceMapping> {
  return api.post<BcmComplianceMapping>(`${BASE}/${id}/attach-gap`, { gapId });
}
