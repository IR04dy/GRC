// SDAIA National Data Index (NDI) — Assessment Cycle API client (slice 1).
//
// Wraps /api/ndi/cycles/*. Foundation entity — every other NDI surface
// in slices 2-14 hangs off a cycle.
//
// Status machine (13 forward-only states + ARCHIVED terminal):
//   DRAFT → PLANNING → DATA_COLLECTION → ASSESSMENT_IN_PROGRESS
//         → EVIDENCE_REVIEW → GAP_REMEDIATION
//         → READY_FOR_DATA_OFFICE_REVIEW → UNDER_DATA_OFFICE_REVIEW
//         → READY_FOR_EXECUTIVE_APPROVAL → APPROVED → SUBMITTED → CLOSED
//   any → ARCHIVED  (terminal)

import { api } from './api';

export type NdiCycleStatus =
  | 'DRAFT' | 'PLANNING' | 'DATA_COLLECTION' | 'ASSESSMENT_IN_PROGRESS'
  | 'EVIDENCE_REVIEW' | 'GAP_REMEDIATION'
  | 'READY_FOR_DATA_OFFICE_REVIEW' | 'UNDER_DATA_OFFICE_REVIEW'
  | 'READY_FOR_EXECUTIVE_APPROVAL' | 'APPROVED' | 'SUBMITTED'
  | 'CLOSED' | 'ARCHIVED';

export type NdiReadinessStatus = 'ON_TRACK' | 'AT_RISK' | 'BLOCKED';

export interface NdiAssessmentCycle {
  id?: number;
  organisationId?: number;
  cycleCode?: string;

  cycleNameEn: string;
  cycleNameAr?: string;

  reportingYear: number;
  periodStart?: string;
  periodEnd?: string;

  scope?: string;
  entityNameEn?: string;
  entityNameAr?: string;

  dataOfficeOwnerName?: string;
  dataOfficeOwnerEmail?: string;
  assessmentManagerName?: string;
  assessmentManagerEmail?: string;

  status: NdiCycleStatus;

  maturityScore?: number;
  complianceScore?: number;
  operationalExcellenceScore?: number;
  overallScore?: number;
  evidenceCompletionPercent?: number;

  gapCount?: number;
  criticalGapCount?: number;

  submissionReadinessStatus?: NdiReadinessStatus;

  approvedBy?: string;
  approvedAt?: string;
  submittedAt?: string;
  closedAt?: string;

  notes?: string;
}

export interface NdiCycleAggregate {
  total: number;
  active: number;
  byStatus: Record<NdiCycleStatus, number>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/cycles';

export function listCycles(opts: { status?: NdiCycleStatus; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  if (opts.status) p.set('status', opts.status);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 50));
  return api.get<Page<NdiAssessmentCycle>>(`${BASE}?${p.toString()}`);
}

export function cyclesAggregate() { return api.get<NdiCycleAggregate>(`${BASE}/aggregate`); }

export function getCycle(id: number) { return api.get<NdiAssessmentCycle>(`${BASE}/${id}`); }

export function createCycle(body: Partial<NdiAssessmentCycle>) {
  return api.post<NdiAssessmentCycle>(BASE, body);
}

export function updateCycle(id: number, body: Partial<NdiAssessmentCycle>) {
  return api.put<NdiAssessmentCycle>(`${BASE}/${id}`, body);
}

export function transitionCycle(id: number, to: NdiCycleStatus) {
  return api.post<NdiAssessmentCycle>(`${BASE}/${id}/transition`, { to });
}

export function archiveCycle(id: number) {
  return api.post<NdiAssessmentCycle>(`${BASE}/${id}/archive`);
}

export function cloneCycle(id: number, newYear: number) {
  return api.post<NdiAssessmentCycle>(`${BASE}/${id}/clone`, { newYear });
}

export function deleteCycle(id: number) { return api.delete<void>(`${BASE}/${id}`); }

// ─── UI helpers ───────────────────────────────────────────────────────

/** Forward-only flow order. Used by the UI to render the next-step
 *  buttons in the right order and to compute "which transition is next". */
export const NDI_CYCLE_FLOW: NdiCycleStatus[] = [
  'DRAFT', 'PLANNING', 'DATA_COLLECTION', 'ASSESSMENT_IN_PROGRESS',
  'EVIDENCE_REVIEW', 'GAP_REMEDIATION',
  'READY_FOR_DATA_OFFICE_REVIEW', 'UNDER_DATA_OFFICE_REVIEW',
  'READY_FOR_EXECUTIVE_APPROVAL', 'APPROVED', 'SUBMITTED', 'CLOSED',
];

export function nextStep(s: NdiCycleStatus): NdiCycleStatus | null {
  const i = NDI_CYCLE_FLOW.indexOf(s);
  if (i < 0 || i + 1 >= NDI_CYCLE_FLOW.length) return null;
  return NDI_CYCLE_FLOW[i + 1];
}

export function cycleStatusTone(s: NdiCycleStatus):
    'neutral' | 'info' | 'warning' | 'success' | 'primary' | 'danger' {
  switch (s) {
    case 'DRAFT':                        return 'neutral';
    case 'PLANNING':                     return 'info';
    case 'DATA_COLLECTION':              return 'info';
    case 'ASSESSMENT_IN_PROGRESS':       return 'primary';
    case 'EVIDENCE_REVIEW':              return 'primary';
    case 'GAP_REMEDIATION':              return 'warning';
    case 'READY_FOR_DATA_OFFICE_REVIEW': return 'info';
    case 'UNDER_DATA_OFFICE_REVIEW':     return 'primary';
    case 'READY_FOR_EXECUTIVE_APPROVAL': return 'info';
    case 'APPROVED':                     return 'success';
    case 'SUBMITTED':                    return 'success';
    case 'CLOSED':                       return 'neutral';
    case 'ARCHIVED':                     return 'neutral';
  }
}

export function cycleStatusLabel(s: NdiCycleStatus, lang: 'en' | 'ar'): string {
  const en: Record<NdiCycleStatus, string> = {
    DRAFT:                          'Draft',
    PLANNING:                       'Planning',
    DATA_COLLECTION:                'Data collection',
    ASSESSMENT_IN_PROGRESS:         'Assessment in progress',
    EVIDENCE_REVIEW:                'Evidence review',
    GAP_REMEDIATION:                'Gap remediation',
    READY_FOR_DATA_OFFICE_REVIEW:   'Ready — Data Office review',
    UNDER_DATA_OFFICE_REVIEW:       'Under Data Office review',
    READY_FOR_EXECUTIVE_APPROVAL:   'Ready — executive approval',
    APPROVED:                       'Approved',
    SUBMITTED:                      'Submitted',
    CLOSED:                         'Closed',
    ARCHIVED:                       'Archived',
  };
  const ar: Record<NdiCycleStatus, string> = {
    DRAFT:                          'مسودة',
    PLANNING:                       'التخطيط',
    DATA_COLLECTION:                'جمع البيانات',
    ASSESSMENT_IN_PROGRESS:         'التقييم جارٍ',
    EVIDENCE_REVIEW:                'مراجعة الأدلة',
    GAP_REMEDIATION:                'معالجة الفجوات',
    READY_FOR_DATA_OFFICE_REVIEW:   'جاهز — مراجعة مكتب البيانات',
    UNDER_DATA_OFFICE_REVIEW:       'تحت مراجعة مكتب البيانات',
    READY_FOR_EXECUTIVE_APPROVAL:   'جاهز — اعتماد تنفيذي',
    APPROVED:                       'مُعتمَد',
    SUBMITTED:                      'مُقدَّم',
    CLOSED:                         'مُغلَق',
    ARCHIVED:                       'مؤرشَف',
  };
  return (lang === 'ar' ? ar : en)[s];
}

export function readinessTone(r?: NdiReadinessStatus):
    'success' | 'warning' | 'danger' | 'neutral' {
  switch (r) {
    case 'ON_TRACK': return 'success';
    case 'AT_RISK':  return 'warning';
    case 'BLOCKED':  return 'danger';
    default:         return 'neutral';
  }
}

export function readinessLabel(r: NdiReadinessStatus | undefined, lang: 'en' | 'ar'): string {
  if (!r) return lang === 'ar' ? '—' : '—';
  const en: Record<NdiReadinessStatus, string> = {
    ON_TRACK: 'On track', AT_RISK: 'At risk', BLOCKED: 'Blocked',
  };
  const ar: Record<NdiReadinessStatus, string> = {
    ON_TRACK: 'على المسار', AT_RISK: 'مُعرَّض', BLOCKED: 'محظور',
  };
  return (lang === 'ar' ? ar : en)[r];
}
