// Operational Resilience self-assessment — Sprint 14 PR #9.
//
// SAMA Operational Resilience Framework (Sept 2024) — 5 pillars × 5-level
// maturity ladder. Banks must publish an annual self-assessment for SAMA;
// this is the workbench to author + sign-off + close that submission.
//
// Saudi-native bilingual sidebar:
//   ar  : التقييم الذاتي للصمود التشغيلي
//   en  : Operational Resilience Self-Assessment
//
// Pillars (SAMA Op-Res §3.2 / §4.1 / §4.3 / §5 / §6):
//   GOVERNANCE       — board ownership, accountable SE, 3LoD
//   IBS_MAPPING      — important business services catalogue
//   TOLERANCES       — quantified impact tolerances per IBS
//   MAPPING_TESTING  — resource mapping + severe-but-plausible
//   LESSONS          — lessons learned + management actions

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type AssessmentStatus =
  | 'DRAFT' | 'IN_PROGRESS' | 'APPROVED' | 'CLOSED';

export type PillarCode =
  | 'GOVERNANCE' | 'IBS_MAPPING' | 'TOLERANCES'
  | 'MAPPING_TESTING' | 'LESSONS';

export type MaturityScore = 1 | 2 | 3 | 4 | 5;

// ─── Requests ────────────────────────────────────────────────────

export interface CreateAssessmentRequest {
  fiscalYear: number;
  titleEn: string;
  titleAr: string;
  dueDate?: string;          // ISO date
  sponsorUserId?: number;
}

export interface UpdateAssessmentRequest {
  titleEn?: string;
  titleAr?: string;
  dueDate?: string;
  sponsorUserId?: number;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
}

export interface CellUpsertRequest {
  pillarId: number;
  currentScore?: number;     // 1–5
  targetScore?: number;      // 1–5
  rationaleEn?: string;
  rationaleAr?: string;
  evidenceReference?: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface MaturityRubricLevel {
  level: number;
  labelEn: string;
  en: string;
  ar: string;
}

export interface PillarResponse {
  id: number;
  code: PillarCode;
  sortOrder: number;
  nameEn: string;
  nameAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  samaReference?: string;
  maturityRubricJson?: string;   // JSON-encoded MaturityRubricLevel[]
  isActive: boolean;
}

export interface CellResponse {
  id: number;
  assessmentId: number;
  pillarId: number;
  pillarCodeSnapshot: PillarCode;
  currentScore?: number;
  targetScore?: number;
  rationaleEn?: string;
  rationaleAr?: string;
  evidenceReference?: string;
  lastEditedByUserId?: number;
}

export interface AssessmentResponse {
  id: number;
  fiscalYear: number;
  titleEn: string;
  titleAr: string;
  status: AssessmentStatus;
  dueDate?: string;
  sponsorUserId?: number;
  avgCurrentScore?: number;
  avgTargetScore?: number;
  completeness?: string;
  submittedAt?: string;
  approvedAt?: string;
  approvedByUserId?: number;
  closedAt?: string;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
  createdAt?: string;
  updatedAt?: string;
  cells: CellResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Display label maps (Saudi-native AR) ────────────────────────

export const STATUS_LABEL: Record<AssessmentStatus, { en: string; ar: string }> = {
  DRAFT:       { en: 'Draft',          ar: 'مسوّدة' },
  IN_PROGRESS: { en: 'In progress',    ar: 'قيد التعبئة' },
  APPROVED:    { en: 'Approved',       ar: 'مُعتمَدة' },
  CLOSED:      { en: 'Closed',         ar: 'مغلقة' },
};

export const STATUS_TONE: Record<AssessmentStatus,
  'neutral' | 'warning' | 'info' | 'success'> = {
  DRAFT:       'neutral',
  IN_PROGRESS: 'info',
  APPROVED:    'success',
  CLOSED:      'neutral',
};

export const PILLAR_LABEL: Record<PillarCode, { en: string; ar: string }> = {
  GOVERNANCE:      { en: 'Governance & Strategy',     ar: 'الحوكمة والاستراتيجية' },
  IBS_MAPPING:     { en: 'Important Business Services', ar: 'الخدمات التجارية المهمّة' },
  TOLERANCES:      { en: 'Impact Tolerances',         ar: 'حدود التحمّل' },
  MAPPING_TESTING: { en: 'Resource Mapping & Testing', ar: 'الربط والاختبار' },
  LESSONS:         { en: 'Lessons & Management Action', ar: 'الدروس والإجراءات الإدارية' },
};

// Tone of the cell heat-map for current maturity (lower = redder).
export const MATURITY_TONE: Record<number,
  'danger' | 'warning' | 'info' | 'success'> = {
  1: 'danger',
  2: 'danger',
  3: 'warning',
  4: 'info',
  5: 'success',
};

// One-word maturity label per level (Saudi-native AR).
export const MATURITY_LABEL: Record<number, { en: string; ar: string }> = {
  1: { en: 'Initial',     ar: 'مبدئي' },
  2: { en: 'Developing',  ar: 'قيد التطوير' },
  3: { en: 'Defined',     ar: 'محدّد' },
  4: { en: 'Managed',     ar: 'مُدار' },
  5: { en: 'Optimising',  ar: 'مُحسَّن' },
};

// Safe rubric parser — backend stores as JSON string but might be null.
export function parseRubric(json?: string): MaturityRubricLevel[] {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    return Array.isArray(arr) ? arr as MaturityRubricLevel[] : [];
  } catch {
    return [];
  }
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/opres';

export const opresApi = {
  listPillars: () =>
    api.get<PillarResponse[]>(`${PREFIX}/pillars`),

  createAssessment: (body: CreateAssessmentRequest) =>
    api.post<AssessmentResponse>(`${PREFIX}/assessments`, body),

  listAssessments: (page = 0, size = 25) =>
    api.get<Page<AssessmentResponse>>(
      `${PREFIX}/assessments?page=${page}&size=${size}`,
    ),

  getAssessment: (id: number) =>
    api.get<AssessmentResponse>(`${PREFIX}/assessments/${id}`),

  updateAssessment: (id: number, body: UpdateAssessmentRequest) =>
    api.put<AssessmentResponse>(`${PREFIX}/assessments/${id}`, body),

  upsertCell: (id: number, body: CellUpsertRequest) =>
    api.post<CellResponse>(`${PREFIX}/assessments/${id}/cells`, body),

  submit: (id: number) =>
    api.post<AssessmentResponse>(`${PREFIX}/assessments/${id}/submit`, {}),

  approve: (id: number) =>
    api.post<AssessmentResponse>(`${PREFIX}/assessments/${id}/approve`, {}),

  close: (id: number) =>
    api.post<AssessmentResponse>(`${PREFIX}/assessments/${id}/close`, {}),
};
