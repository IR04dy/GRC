// Slice 10-A — Exercise Scenario Library client.
//
// Typed wrappers around /api/v1/bcm/scenario-library/* — used by
// ../pages/bcm/ExerciseScenarioLibraryPage and ExerciseScenarioDetailPage.
//
// The library is a global catalog (no organisationId on the rows) — every
// tenant sees the same scenarios. The "instantiate" call creates an
// org-scoped exercise row, so it does carry tenant context via the JWT.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

// ─── Domain types ─────────────────────────────────────────────────────

export type ScenarioType =
  | 'TABLETOP'
  | 'FUNCTIONAL'
  | 'FULL_SCALE'
  | 'WORKSHOP'
  | 'DRILL'
  | 'SIMULATION';

export type Complexity = 'BASIC' | 'INTERMEDIATE' | 'ADVANCED';

export type InjectType =
  | 'INFORMATION'
  | 'ESCALATION'
  | 'DECISION'
  | 'MEDIA'
  | 'REGULATOR';

export interface ScenarioSummary {
  id: number;
  scenarioCode: string;
  titleEn: string;
  titleAr?: string;
  scenarioCategory: string;
  exerciseType: string;
  scenarioType?: ScenarioType;
  complexity?: Complexity;
  durationMinutes?: number;
  recommendedDurationMinutes?: number;
  frameworkAlignment?: string;
  targetAudience?: string;
  usageCount: number;
}

export interface InjectView {
  id: number;
  injectOrder: number;
  timeOffsetMinutes: number;
  injectType: InjectType;
  injectTextEn: string;
  injectTextAr?: string;
  expectedActionEn?: string;
  expectedActionAr?: string;
}

export interface ScenarioDetail {
  id: number;
  scenarioCode: string;
  titleEn: string;
  titleAr?: string;
  scenarioCategory: string;
  exerciseType: string;
  scenarioType?: ScenarioType;
  complexity?: Complexity;
  durationMinutes?: number;
  recommendedDurationMinutes?: number;
  frameworkAlignment?: string;
  targetAudience?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  learningObjectivesEn?: string;
  learningObjectivesAr?: string;
  successCriteriaEn?: string;
  successCriteriaAr?: string;
  recommendedObservers?: string;
  usageCount: number;
  injects: InjectView[];
}

export interface InstantiateRequest {
  plannedDate: string;          // ISO YYYY-MM-DD
  leadFacilitator: string;
  exerciseNameEn?: string;
  exerciseNameAr?: string;
  observers?: string;
  participants?: string;
}

export interface InstantiatedExercise {
  exerciseId: number;
  scenarioId: number;
  scenarioCode: string;
  exerciseNameEn?: string;
  exerciseNameAr?: string;
  exerciseType?: string;
  scheduledDate?: string;
  facilitator?: string;
  status?: string;
  injectCount: number;
  usageCountAfter: number;
}

// Spring Page shape
export interface PageOf<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Static catalogues ────────────────────────────────────────────────

export const SCENARIO_TYPES: ScenarioType[] = [
  'TABLETOP', 'FUNCTIONAL', 'FULL_SCALE', 'WORKSHOP', 'DRILL', 'SIMULATION',
];

export const COMPLEXITIES: Complexity[] = ['BASIC', 'INTERMEDIATE', 'ADVANCED'];

export const FRAMEWORKS: { code: string; en: string; ar: string }[] = [
  { code: 'SAMA_CSF',    en: 'SAMA CSF',          ar: 'إطار ساما السيبراني' },
  { code: 'NCA_ECC2',    en: 'NCA ECC-2',         ar: 'الضوابط الأساسية NCA ECC-2' },
  { code: 'ISO_22301',   en: 'ISO 22301',         ar: 'الأيزو 22301' },
  { code: 'PDPL',        en: 'PDPL',              ar: 'نظام حماية البيانات الشخصية' },
  { code: 'NCA_OT_CSCC', en: 'NCA OT-CSCC',       ar: 'NCA ضوابط التشغيل OT-CSCC' },
  { code: 'NCA_CSCC',    en: 'NCA CSCC (Cloud)',  ar: 'ضوابط NCA السحابية CSCC' },
  { code: 'SAMA_AFC',    en: 'SAMA AFC (AML/CFT)', ar: 'مكافحة الجرائم المالية ساما' },
  { code: 'IEC_61511',   en: 'IEC 61511',         ar: 'IEC 61511 (سلامة وظيفية)' },
  { code: 'IEC_62443',   en: 'IEC 62443',         ar: 'IEC 62443 (أمن الصناعي)' },
];

export const SCENARIO_TYPE_LABELS: Record<ScenarioType, { en: string; ar: string }> = {
  TABLETOP:   { en: 'Tabletop',          ar: 'حواري (Tabletop)' },
  FUNCTIONAL: { en: 'Functional',        ar: 'وظيفي' },
  FULL_SCALE: { en: 'Full-scale',        ar: 'كامل النطاق' },
  WORKSHOP:   { en: 'Workshop',          ar: 'ورشة' },
  DRILL:      { en: 'Drill',             ar: 'تمرين سريع' },
  SIMULATION: { en: 'Simulation',        ar: 'محاكاة' },
};

export const COMPLEXITY_LABELS: Record<Complexity, { en: string; ar: string }> = {
  BASIC:        { en: 'Basic',        ar: 'أساسي' },
  INTERMEDIATE: { en: 'Intermediate', ar: 'متوسط' },
  ADVANCED:     { en: 'Advanced',     ar: 'متقدم' },
};

export const INJECT_TYPE_LABELS: Record<InjectType, { en: string; ar: string }> = {
  INFORMATION: { en: 'Information', ar: 'معلومة' },
  ESCALATION:  { en: 'Escalation',  ar: 'تصعيد' },
  DECISION:    { en: 'Decision',    ar: 'قرار' },
  MEDIA:       { en: 'Media',       ar: 'إعلام' },
  REGULATOR:   { en: 'Regulator',   ar: 'جهة تنظيمية' },
};

export function complexityTone(c?: Complexity): BadgeTone {
  switch (c) {
    case 'BASIC':        return 'success';
    case 'INTERMEDIATE': return 'info';
    case 'ADVANCED':     return 'warning';
    default:             return 'neutral';
  }
}

export function scenarioTypeTone(t?: ScenarioType): BadgeTone {
  switch (t) {
    case 'FULL_SCALE': return 'danger';
    case 'SIMULATION': return 'warning';
    case 'FUNCTIONAL': return 'info';
    case 'TABLETOP':   return 'neutral';
    case 'WORKSHOP':   return 'success';
    case 'DRILL':      return 'info';
    default:           return 'neutral';
  }
}

export function injectTypeTone(t: InjectType): BadgeTone {
  switch (t) {
    case 'REGULATOR':   return 'danger';
    case 'DECISION':    return 'warning';
    case 'ESCALATION':  return 'warning';
    case 'MEDIA':       return 'info';
    case 'INFORMATION': return 'neutral';
  }
}

/** Render a time-offset in minutes as e.g. "T+0", "T+90", "T+2h 30m". */
export function fmtTimeOffset(mins: number, isAr = false): string {
  if (mins === 0) return 'T+0';
  if (mins < 60) return isAr ? `الدقيقة ${mins}` : `T+${mins}`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (m === 0) return isAr ? `+${h} ساعة` : `T+${h}h`;
  return isAr ? `+${h} س ${m} د` : `T+${h}h ${m}m`;
}

/** Render duration in minutes as e.g. "90 minutes" / "2h" / "ساعتان". */
export function fmtDuration(mins?: number, isAr = false): string {
  if (mins === null || mins === undefined) return '—';
  if (mins < 60) return isAr ? `${mins} دقيقة` : `${mins} min`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (m === 0) return isAr ? `${h} ساعة` : `${h}h`;
  return isAr ? `${h} س ${m} د` : `${h}h ${m}m`;
}

/** Parse the comma-separated framework_alignment string into chips. */
export function parseFrameworks(s?: string): string[] {
  if (!s) return [];
  return s.split(',').map(t => t.trim()).filter(Boolean);
}

export function frameworkLabel(code: string, isAr = false): string {
  const f = FRAMEWORKS.find(x => x.code === code);
  if (!f) return code;
  return isAr ? f.ar : f.en;
}

// ─── API calls ────────────────────────────────────────────────────────

const BASE = '/api/v1/bcm/scenario-library';

export interface SearchParams {
  type?: ScenarioType | '';
  complexity?: Complexity | '';
  framework?: string;
  q?: string;
  page?: number;
  size?: number;
}

export function listScenarios(params: SearchParams = {}): Promise<PageOf<ScenarioSummary>> {
  const qs = new URLSearchParams();
  if (params.type)       qs.set('type', params.type);
  if (params.complexity) qs.set('complexity', params.complexity);
  if (params.framework)  qs.set('framework', params.framework);
  if (params.q)          qs.set('q', params.q);
  if (params.page != null) qs.set('page', String(params.page));
  if (params.size != null) qs.set('size', String(params.size));
  const qstr = qs.toString();
  return api.get<PageOf<ScenarioSummary>>(qstr ? `${BASE}?${qstr}` : BASE);
}

export function getScenario(id: number): Promise<ScenarioDetail> {
  return api.get<ScenarioDetail>(`${BASE}/${id}`);
}

export function instantiateScenario(
  id: number, body: InstantiateRequest,
): Promise<InstantiatedExercise> {
  return api.post<InstantiatedExercise>(`${BASE}/${id}/instantiate`, body);
}

export function popularScenarios(limit = 5): Promise<ScenarioSummary[]> {
  return api.get<ScenarioSummary[]>(`${BASE}/popular?limit=${limit}`);
}

export function recommendedScenarios(framework: string): Promise<ScenarioSummary[]> {
  return api.get<ScenarioSummary[]>(`${BASE}/recommended?framework=${encodeURIComponent(framework)}`);
}
