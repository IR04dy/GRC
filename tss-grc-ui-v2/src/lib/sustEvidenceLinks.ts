// S-C bridge client — /api/sustainability/evidence-links.
//
// Links a real evidence item (see evidenceItems.ts) to a sustainability entity,
// sets that entity's evidence_item_id FK, and records a row in the generic
// evidence.evidence_links table. entityType is the JPA entity class name the
// backend whitelist expects.

import { api } from './api';

/** The 11 ESG entity types the S-C bridge accepts (JPA class names) —
 *  V250 six + V256 vendor survey + V298 the four green-finance objects (GF-2). */
export type SustEvidenceEntityType =
  | 'GhgEmissionRecord' | 'CmaDisclosure' | 'TcfdItem'
  | 'SgiCommitment' | 'ClimateRiskOpportunity' | 'EsgMetric' | 'VendorEsgSurvey'
  | 'GreenProject' | 'GreenInstrument' | 'InstrumentAllocation' | 'CarbonCredit';

export const SUST_EVIDENCE_ENTITY_TYPES: Array<{ value: SustEvidenceEntityType; en: string; ar: string }> = [
  { value: 'GhgEmissionRecord',      en: 'GHG record',        ar: 'سجل انبعاثات' },
  { value: 'CmaDisclosure',          en: 'CMA disclosure',    ar: 'إفصاح CMA' },
  { value: 'TcfdItem',               en: 'TCFD item',         ar: 'بند TCFD' },
  { value: 'SgiCommitment',          en: 'SGI commitment',    ar: 'التزام SGI' },
  { value: 'ClimateRiskOpportunity', en: 'Climate risk',      ar: 'مخاطرة مناخية' },
  { value: 'EsgMetric',              en: 'ESG metric',        ar: 'مؤشر ESG' },
  { value: 'VendorEsgSurvey',        en: 'Vendor ESG survey', ar: 'استبيان استدامة مورد' },
  { value: 'GreenProject',           en: 'Green project',     ar: 'مشروع أخضر' },
  { value: 'GreenInstrument',        en: 'Green instrument',  ar: 'أداة تمويل أخضر' },
  { value: 'InstrumentAllocation',   en: 'Proceeds allocation', ar: 'تخصيص متحصلات' },
  { value: 'CarbonCredit',           en: 'Carbon credit',     ar: 'أرصدة كربونية' },
];

export function linkSustEvidence(
  entityType: SustEvidenceEntityType, entityId: number, evidenceItemId: number, notes?: string,
) {
  return api.post<Record<string, unknown>>('/api/sustainability/evidence-links',
    { entityType, entityId, evidenceItemId, notes });
}

export function listSustEvidenceForEntity(entityType: SustEvidenceEntityType, entityId: number) {
  return api.get<Array<Record<string, unknown>>>(
    `/api/sustainability/evidence-links?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`);
}

export function sustEvidencePackage(entityType?: SustEvidenceEntityType) {
  const q = entityType ? `?entityType=${encodeURIComponent(entityType)}` : '';
  return api.get<Record<string, unknown>>(`/api/sustainability/evidence-links/package${q}`);
}
