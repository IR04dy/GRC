// MTPD Inheritance client — Slice 10-E.
//
// Typed wrappers around /api/v1/bcm/mtpd-inheritance/* and
// /api/v1/bcm/process-vendor-dependencies/* plus helpers for severity
// tone, finding-type labels, and Saudi-native bilingual strings.
//
// Mirrors the shape of ../lib/vendorBcp (Slice 10-D) — keep them aligned
// so a developer can switch between them without learning a new
// convention.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

// ─── Types ────────────────────────────────────────────────────────────

export type DependencyType = 'REQUIRED' | 'RECOMMENDED' | 'FALLBACK';

export type DependencyStrength = 'TIGHT' | 'LOOSE' | 'TRANSIENT';

export type FindingType = 'BREACH' | 'WARNING' | 'OK' | 'NO_ATTESTATION';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface ProcessVendorDependency {
  id: number;
  externalId: string;
  organisationId: number;
  businessProcessId: number;
  businessProcessNameEn?: string;
  businessProcessNameAr?: string;
  vendorId: number;
  vendorName?: string;
  vendorNameAr?: string;
  dependencyType: DependencyType;
  dependencyStrength: DependencyStrength;
  notesEn?: string;
  notesAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DependencyCreateRequest {
  businessProcessId: number;
  vendorId: number;
  dependencyType: DependencyType;
  dependencyStrength: DependencyStrength;
  notesEn?: string;
  notesAr?: string;
}

export interface DependencyUpdateRequest {
  dependencyType?: DependencyType;
  dependencyStrength?: DependencyStrength;
  notesEn?: string;
  notesAr?: string;
}

export interface MtpdAssessment {
  id: number;
  externalId: string;
  organisationId: number;
  assessmentCode: string;
  cycleYear: number;
  cycleQuarter?: string;
  computedAt: string;
  computedByUserId?: number;
  totalProcesses: number;
  totalBreaches: number;
  totalWarnings: number;
  totalOk: number;
  totalNoAttestation: number;
  isPublished: boolean;
  publishedAt?: string;
  publishedByUserId?: number;
  notesEn?: string;
  notesAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface MtpdFinding {
  id: number;
  externalId: string;
  organisationId: number;
  assessmentId: number;
  businessProcessId: number;
  businessProcessNameEn: string;
  businessProcessNameAr?: string;
  vendorId: number;
  vendorName: string;
  processMtpdHours?: number;
  vendorAttestedMtpdHours?: number;
  varianceHours?: number;
  findingType: FindingType;
  severity: Severity;
  dependencyStrength: DependencyStrength;
  dependencyType: DependencyType;
  recommendationEn?: string;
  recommendationAr?: string;
  capaTaskId?: number;
  capaExternalId?: string;
  capaCreatedAt?: string;
  sourceAttestationId?: number;
  sourceCampaignCode?: string;
  sourceSubmittedAt?: string;
  createdAt?: string;
}

export interface AssessmentDetail {
  assessment: MtpdAssessment;
  findings: MtpdFinding[];
}

export interface ComputeRequest {
  cycleYear: number;
  cycleQuarter?: string;
  notesEn?: string;
  notesAr?: string;
}

export interface PublishRequest {
  notesEn?: string;
  notesAr?: string;
  // F2-224 — publishedByUserId removed; the server stamps the publisher from the principal.
}

export interface CapaCreateRequest {
  owner?: string;
  severityOverride?: string;
  notesEn?: string;
  notesAr?: string;
}

export interface CapaCreateResponse {
  findingId: number;
  capaTaskId: number;
  capaExternalId: string;
  capaCreatedAt: string;
}

// ─── Labels (Saudi-native) ────────────────────────────────────────────

export const DEPENDENCY_TYPE_LABELS: Record<DependencyType, { en: string; ar: string }> = {
  REQUIRED:    { en: 'Required',    ar: 'ضروري' },
  RECOMMENDED: { en: 'Recommended', ar: 'موصى به' },
  FALLBACK:    { en: 'Fallback',    ar: 'احتياطي' },
};

export const DEPENDENCY_STRENGTH_LABELS: Record<DependencyStrength, { en: string; ar: string }> = {
  TIGHT:     { en: 'Tight',     ar: 'تابع متين' },
  LOOSE:     { en: 'Loose',     ar: 'تابع مرن' },
  TRANSIENT: { en: 'Transient', ar: 'تابع عابر' },
};

export const FINDING_TYPE_LABELS: Record<FindingType, { en: string; ar: string }> = {
  BREACH:         { en: 'Breach',          ar: 'خرق' },
  WARNING:        { en: 'Warning',         ar: 'تحذير' },
  OK:             { en: 'OK',              ar: 'ضمن النطاق' },
  NO_ATTESTATION: { en: 'No attestation',  ar: 'غياب الإقرار' },
};

export const SEVERITY_LABELS: Record<Severity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرجة' },
  HIGH:     { en: 'High',     ar: 'مرتفعة' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسطة' },
  LOW:      { en: 'Low',      ar: 'منخفضة' },
};

// ─── Tones ────────────────────────────────────────────────────────────

export function findingTypeTone(t: FindingType): BadgeTone {
  switch (t) {
    case 'BREACH':         return 'danger';
    case 'WARNING':        return 'warning';
    case 'OK':             return 'success';
    case 'NO_ATTESTATION': return 'neutral';
  }
}

export function severityTone(s: Severity): BadgeTone {
  switch (s) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'danger';
    case 'MEDIUM':   return 'warning';
    case 'LOW':      return 'neutral';
  }
}

export function dependencyTypeTone(t: DependencyType): BadgeTone {
  switch (t) {
    case 'REQUIRED':    return 'danger';
    case 'RECOMMENDED': return 'warning';
    case 'FALLBACK':    return 'neutral';
  }
}

// ─── API: assessments ─────────────────────────────────────────────────

const ASSESS_BASE = '/api/v1/bcm/mtpd-inheritance';

export function computeAssessment(body: ComputeRequest): Promise<MtpdAssessment> {
  return api.post<MtpdAssessment>(`${ASSESS_BASE}/assessments`, body);
}

export function listAssessments(): Promise<MtpdAssessment[]> {
  return api.get<MtpdAssessment[]>(`${ASSESS_BASE}/assessments`);
}

export function getAssessmentDetail(id: number): Promise<AssessmentDetail> {
  return api.get<AssessmentDetail>(`${ASSESS_BASE}/assessments/${id}`);
}

export function publishAssessment(id: number, body?: PublishRequest): Promise<MtpdAssessment> {
  return api.post<MtpdAssessment>(`${ASSESS_BASE}/assessments/${id}/publish`, body ?? {});
}

export function createCapaForFinding(id: number, body?: CapaCreateRequest): Promise<CapaCreateResponse> {
  return api.post<CapaCreateResponse>(`${ASSESS_BASE}/findings/${id}/capa`, body ?? {});
}

export function getProcessHistory(processId: number): Promise<MtpdFinding[]> {
  return api.get<MtpdFinding[]>(`${ASSESS_BASE}/processes/${processId}/history`);
}

// ─── API: dependencies ────────────────────────────────────────────────

const DEP_BASE = '/api/v1/bcm/process-vendor-dependencies';

export function listDependencies(): Promise<ProcessVendorDependency[]> {
  return api.get<ProcessVendorDependency[]>(DEP_BASE);
}

export function getDependency(id: number): Promise<ProcessVendorDependency> {
  return api.get<ProcessVendorDependency>(`${DEP_BASE}/${id}`);
}

export function createDependency(body: DependencyCreateRequest): Promise<ProcessVendorDependency> {
  return api.post<ProcessVendorDependency>(DEP_BASE, body);
}

export function updateDependency(id: number, body: DependencyUpdateRequest): Promise<ProcessVendorDependency> {
  return api.put<ProcessVendorDependency>(`${DEP_BASE}/${id}`, body);
}

export function deleteDependency(id: number): Promise<void> {
  return api.delete<void>(`${DEP_BASE}/${id}`);
}

// ─── Filtering helpers used by the detail page ────────────────────────

export function filterFindings(
  findings: MtpdFinding[],
  severityFilter: Set<Severity>,
  typeFilter: Set<FindingType>,
  strengthFilter: Set<DependencyStrength>,
): MtpdFinding[] {
  return findings.filter(f => {
    if (severityFilter.size > 0 && !severityFilter.has(f.severity)) return false;
    if (typeFilter.size > 0 && !typeFilter.has(f.findingType)) return false;
    if (strengthFilter.size > 0 && !strengthFilter.has(f.dependencyStrength)) return false;
    return true;
  });
}
