// Unified Control Library client — Framework Intersection F2.
//
// Wraps the /api/v1/controls + /api/v1/control-mappings +
// /api/v1/risk-control-links + /api/v1/control-assessments surfaces.

import { api } from './api';

export type ControlDomain = 'RISK' | 'CYBER' | 'BCM' | 'PRIVACY' | 'TPRM' | 'COMPLIANCE' | 'GENERAL';
export type ControlType = 'PREVENTIVE' | 'DETECTIVE' | 'CORRECTIVE' | 'DETERRENT' | 'RECOVERY';
export type AutomationLevel = 'MANUAL' | 'SEMI' | 'AUTOMATED';
export type ControlStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED';
export type Coverage = 'FULL' | 'PARTIAL' | 'COMPENSATING';
export type RiskLinkType = 'MITIGATES' | 'DETECTS' | 'TRANSFERS' | 'AVOIDS' | 'COMPENSATES';
export type Effectiveness = 'EFFECTIVE' | 'NEEDS_IMPROVEMENT' | 'INEFFECTIVE' | 'NOT_TESTED';
export type AssessmentWorkflow = 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';

export interface Control {
  id: number;
  organisationId?: number;
  controlCode: string;
  version: string;
  domain: ControlDomain;
  objectiveId?: number;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  controlType: ControlType;
  automationLevel: AutomationLevel;
  frequency?: string;
  ownerUserId?: number;
  status: ControlStatus;
  legacySourceModule?: string;
  legacyId?: number;
}

export interface ControlObjective {
  id: number;
  objectiveCode: string;
  domain: ControlDomain;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  status: ControlStatus;
}

export interface ControlMapping {
  id?: number;
  controlId: number;
  citationId: number;
  coverage: Coverage;
  rationaleEn?: string;
  rationaleAr?: string;
  lastReviewedAt?: string;
  reviewerUserId?: number;
}

export interface RiskControlLink {
  id?: number;
  riskId: number;
  controlId: number;
  linkType: RiskLinkType;
  effectiveness?: Effectiveness;
  rationaleEn?: string;
  rationaleAr?: string;
  lastReviewedAt?: string;
}

export interface ControlAssessment {
  id?: number;
  controlId: number;
  assessmentCycleId?: number;
  effectiveness: Effectiveness;
  assessorUserId?: number;
  assessorName?: string;
  assessmentDate: string;
  evidenceId?: number;
  notesEn?: string;
  notesAr?: string;
  findingId?: number;
  capaId?: number;
  workflowStatus: AssessmentWorkflow;
}

const BASE = '/api/v1';

// ─── Controls ─────────────────────────────────────────────────────

export function listControls(domain?: ControlDomain, status?: ControlStatus) {
  const q = new URLSearchParams();
  if (domain) q.set('domain', domain);
  if (status) q.set('status', status);
  const qs = q.toString();
  return api.get<Control[]>(`${BASE}/controls${qs ? `?${qs}` : ''}`);
}
export function getControl(id: number) { return api.get<Control>(`${BASE}/controls/${id}`); }
export function createControl(body: Partial<Control>) { return api.post<Control>(`${BASE}/controls`, body); }
export function updateControl(id: number, body: Partial<Control>) { return api.put<Control>(`${BASE}/controls/${id}`, body); }
export function transitionControl(id: number, to: ControlStatus) {
  return api.post<Control>(`${BASE}/controls/${id}/transition`, { to });
}
export function deleteControl(id: number) { return api.delete<void>(`${BASE}/controls/${id}`); }

// ─── Objectives ───────────────────────────────────────────────────

export function listObjectives(domain?: ControlDomain) {
  const q = domain ? `?domain=${domain}` : '';
  return api.get<ControlObjective[]>(`${BASE}/control-objectives${q}`);
}
export function createObjective(body: Partial<ControlObjective>) {
  return api.post<ControlObjective>(`${BASE}/control-objectives`, body);
}
export function listObjectiveControls(id: number) {
  return api.get<Control[]>(`${BASE}/control-objectives/${id}/controls`);
}

// ─── Mappings ─────────────────────────────────────────────────────

export function listControlMappings(controlId: number) {
  return api.get<ControlMapping[]>(`${BASE}/controls/${controlId}/mappings`);
}
export function listCitationMappings(citationId: number) {
  return api.get<ControlMapping[]>(`${BASE}/citations/${citationId}/mappings`);
}
export function createMapping(body: Partial<ControlMapping>) {
  return api.post<ControlMapping>(`${BASE}/control-mappings`, body);
}
export function updateMapping(id: number, body: Partial<ControlMapping>) {
  return api.put<ControlMapping>(`${BASE}/control-mappings/${id}`, body);
}
export function deleteMapping(id: number) {
  return api.delete<void>(`${BASE}/control-mappings/${id}`);
}
export function mappingCoverage() {
  return api.get<number[]>(`${BASE}/control-mappings/coverage`);
}

// ─── Risk ↔ Control links ───────────────────────────────────────

export function listRiskControlLinks(riskId: number) {
  return api.get<RiskControlLink[]>(`${BASE}/risks/${riskId}/control-links`);
}
export function listControlRiskLinks(controlId: number) {
  return api.get<RiskControlLink[]>(`${BASE}/controls/${controlId}/risk-links`);
}
export function linkRiskToControl(body: Partial<RiskControlLink>) {
  return api.post<RiskControlLink>(`${BASE}/risk-control-links`, body);
}
export function deleteRiskControlLink(id: number) {
  return api.delete<void>(`${BASE}/risk-control-links/${id}`);
}

// ─── Assessments ──────────────────────────────────────────────────

export function listControlAssessments(controlId: number) {
  return api.get<ControlAssessment[]>(`${BASE}/controls/${controlId}/assessments`);
}
export function createControlAssessment(body: Partial<ControlAssessment>) {
  return api.post<ControlAssessment>(`${BASE}/control-assessments`, body);
}
export function transitionControlAssessment(id: number, to: AssessmentWorkflow) {
  return api.post<ControlAssessment>(`${BASE}/control-assessments/${id}/transition`, { to });
}

// ─── UI helpers ───────────────────────────────────────────────────

export function controlStatusTone(s: ControlStatus): 'neutral' | 'success' | 'warning' {
  switch (s) {
    case 'DRAFT':   return 'neutral';
    case 'ACTIVE':  return 'success';
    case 'RETIRED': return 'warning';
  }
}

export function controlTypeTone(t: ControlType): 'info' | 'primary' | 'warning' | 'neutral' {
  switch (t) {
    case 'PREVENTIVE': return 'info';
    case 'DETECTIVE':  return 'primary';
    case 'CORRECTIVE': return 'warning';
    case 'DETERRENT':  return 'neutral';
    case 'RECOVERY':   return 'warning';
  }
}

export function coverageTone(c: Coverage): 'success' | 'info' | 'warning' {
  switch (c) {
    case 'FULL':         return 'success';
    case 'PARTIAL':      return 'warning';
    case 'COMPENSATING': return 'info';
  }
}

export function effectivenessTone(e: Effectiveness | undefined): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (e) {
    case 'EFFECTIVE':         return 'success';
    case 'NEEDS_IMPROVEMENT': return 'warning';
    case 'INEFFECTIVE':       return 'danger';
    default:                  return 'neutral';
  }
}

export function domainLabel(d: ControlDomain, lang: 'en' | 'ar'): string {
  const en: Record<ControlDomain, string> = {
    RISK: 'Risk', CYBER: 'Cyber', BCM: 'BCM', PRIVACY: 'Privacy',
    TPRM: 'TPRM', COMPLIANCE: 'Compliance', GENERAL: 'General',
  };
  const ar: Record<ControlDomain, string> = {
    RISK: 'مخاطر', CYBER: 'أمن سيبراني', BCM: 'استمرارية', PRIVACY: 'خصوصية',
    TPRM: 'أطراف خارجية', COMPLIANCE: 'امتثال', GENERAL: 'عام',
  };
  return (lang === 'ar' ? ar : en)[d];
}
