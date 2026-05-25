// Regulatory Inspection Workspace — Sprint 15 PR #3.
//
// Backend: PR #234 (com.tss.grc.module.reginspect).
// F8 rules: PR #235 (AR-201..AR-210).
//
// Workspace for managing SAMA / NCA / CMA / SDAIA / NCEMA on-site
// inspections. Every Saudi bank gets a SAMA on-site every 12-18
// months; this is the day-to-day workbench for that engagement.
//
// Saudi-native bilingual sidebar:
//   ar : التفتيش الرقابي
//   en : Regulatory Inspection
//
// Inspection lifecycle:
//   PLANNED → ENTRY_MEETING → FIELDWORK → EXIT_MEETING →
//   RESPONSE_DRAFTING → CLOSED

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type InspectionStatus =
  | 'PLANNED' | 'ENTRY_MEETING' | 'FIELDWORK'
  | 'EXIT_MEETING' | 'RESPONSE_DRAFTING' | 'CLOSED';

export type Regulator =
  | 'SAMA' | 'NCA' | 'CMA' | 'SDAIA' | 'NCEMA' | 'OTHER';

export type DrStatus = 'OPEN' | 'IN_PROGRESS' | 'SUBMITTED';

export type UpdateType =
  | 'STATUS_CHANGED' | 'DOCUMENT_REQUEST' | 'DR_SUBMITTED'
  | 'FINDING_RECORDED' | 'RESPONSE_DRAFTED' | 'INSPECTOR_COMMENT'
  | 'INTERNAL_NOTE' | 'CLOSURE';

// ─── Requests ────────────────────────────────────────────────────

export interface CreateInspectionRequest {
  regulator: Regulator;
  titleEn: string;
  titleAr: string;
  notifiedAt?: string;        // ISO date
  entryDate?: string;
  exitDate?: string;
  leadInspectorName?: string;
  scopeEn?: string;
  scopeAr?: string;
  coordinatorUserId?: number;
  sponsorUserId?: number;
}

export interface UpdateInspectionRequest {
  titleEn?: string;
  titleAr?: string;
  notifiedAt?: string;
  entryDate?: string;
  exitDate?: string;
  leadInspectorName?: string;
  scopeEn?: string;
  scopeAr?: string;
  coordinatorUserId?: number;
  sponsorUserId?: number;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
}

export interface TransitionRequest {
  toStatus: InspectionStatus;
  noteEn?: string;
  noteAr?: string;
}

export interface CreateDocumentRequestRequest {
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  dueDate: string;
  assignedToUserId?: number;
}

export interface SubmitDocumentRequestRequest {
  evidenceReference: string;
  submissionNotesEn?: string;
  submissionNotesAr?: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface InspectionResponse {
  id: number;
  externalReference: string;
  regulator: Regulator;
  titleEn: string;
  titleAr: string;
  status: InspectionStatus;
  notifiedAt?: string;
  entryDate?: string;
  exitDate?: string;
  closedAt?: string;
  leadInspectorName?: string;
  scopeEn?: string;
  scopeAr?: string;
  coordinatorUserId?: number;
  sponsorUserId?: number;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
  openDrCount?: number;
  overdueDrCount?: number;
  submittedDrCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DocumentRequestResponse {
  id: number;
  inspectionId: number;
  reference: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  status: DrStatus;
  requestedAt: string;
  dueDate?: string;
  submittedAt?: string;
  assignedToUserId?: number;
  evidenceReference?: string;
  submissionNotesEn?: string;
  submissionNotesAr?: string;
  overdue: boolean;
}

export interface InspectionUpdateResponse {
  id: number;
  inspectionId: number;
  occurredAt: string;
  updateType: UpdateType;
  actorUserId?: number;
  actorName?: string;
  statusAfter?: InspectionStatus;
  noteEn?: string;
  noteAr?: string;
  linkedDocumentRequestId?: number;
}

export interface DashboardResponse {
  activeCount: number;
  closedCount: number;
  openDrCount: number;
  overdueDrCount: number;
  recentInspections: InspectionResponse[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Display labels (Saudi-native AR) ────────────────────────────

export const INSPECTION_STATUS_LABEL: Record<InspectionStatus,
  { en: string; ar: string }> = {
  PLANNED:           { en: 'Planned',            ar: 'مخطّطة' },
  ENTRY_MEETING:     { en: 'Entry meeting',      ar: 'اجتماع الافتتاح' },
  FIELDWORK:         { en: 'Fieldwork',          ar: 'العمل الميداني' },
  EXIT_MEETING:      { en: 'Exit meeting',       ar: 'اجتماع الإغلاق' },
  RESPONSE_DRAFTING: { en: 'Drafting responses', ar: 'صياغة الردود' },
  CLOSED:            { en: 'Closed',             ar: 'مغلقة' },
};

export const INSPECTION_STATUS_TONE: Record<InspectionStatus,
  'neutral' | 'info' | 'warning' | 'danger' | 'success'> = {
  PLANNED:           'neutral',
  ENTRY_MEETING:     'info',
  FIELDWORK:         'warning',
  EXIT_MEETING:      'warning',
  RESPONSE_DRAFTING: 'danger',
  CLOSED:            'success',
};

export const REGULATOR_LABEL: Record<Regulator, { en: string; ar: string }> = {
  SAMA:  { en: 'SAMA',  ar: 'ساما' },
  NCA:   { en: 'NCA',   ar: 'هيئة الأمن السيبراني' },
  CMA:   { en: 'CMA',   ar: 'هيئة السوق المالية' },
  SDAIA: { en: 'SDAIA', ar: 'سدايا' },
  NCEMA: { en: 'NCEMA', ar: 'الهيئة الوطنية لإدارة الطوارئ' },
  OTHER: { en: 'Other', ar: 'أخرى' },
};

export const DR_STATUS_LABEL: Record<DrStatus, { en: string; ar: string }> = {
  OPEN:        { en: 'Open',         ar: 'مفتوح' },
  IN_PROGRESS: { en: 'In progress',  ar: 'قيد التنفيذ' },
  SUBMITTED:   { en: 'Submitted',    ar: 'مُقدَّم' },
};

export const DR_STATUS_TONE: Record<DrStatus,
  'neutral' | 'warning' | 'success'> = {
  OPEN:        'warning',
  IN_PROGRESS: 'neutral',
  SUBMITTED:   'success',
};

export const UPDATE_TYPE_LABEL: Record<UpdateType, { en: string; ar: string }> = {
  STATUS_CHANGED:    { en: 'Status changed',     ar: 'تغيير الحالة' },
  DOCUMENT_REQUEST:  { en: 'Document request',   ar: 'طلب وثائق' },
  DR_SUBMITTED:      { en: 'DR submitted',       ar: 'تقديم طلب' },
  FINDING_RECORDED:  { en: 'Finding recorded',   ar: 'تسجيل ملاحظة' },
  RESPONSE_DRAFTED:  { en: 'Response drafted',   ar: 'صياغة ردّ' },
  INSPECTOR_COMMENT: { en: 'Inspector comment',  ar: 'تعليق المفتّش' },
  INTERNAL_NOTE:     { en: 'Internal note',      ar: 'ملاحظة داخلية' },
  CLOSURE:           { en: 'Closure',            ar: 'إغلاق' },
};

/**
 * Next allowed status given current — drives the action panel.
 * Mirrors RegInspectService.ALLOWED.
 */
export function nextAllowed(status: InspectionStatus): InspectionStatus[] {
  switch (status) {
    case 'PLANNED':           return ['ENTRY_MEETING', 'CLOSED'];
    case 'ENTRY_MEETING':     return ['FIELDWORK', 'CLOSED'];
    case 'FIELDWORK':         return ['EXIT_MEETING', 'CLOSED'];
    case 'EXIT_MEETING':      return ['RESPONSE_DRAFTING', 'CLOSED'];
    case 'RESPONSE_DRAFTING': return ['CLOSED'];
    case 'CLOSED':            return [];
  }
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/reginspect';

export const reginspectApi = {
  dashboard: () =>
    api.get<DashboardResponse>(`${PREFIX}/dashboard`),

  createInspection: (body: CreateInspectionRequest) =>
    api.post<InspectionResponse>(`${PREFIX}/inspections`, body),

  listInspections: (page = 0, size = 50) =>
    api.get<Page<InspectionResponse>>(
      `${PREFIX}/inspections?page=${page}&size=${size}`),

  getInspection: (id: number) =>
    api.get<InspectionResponse>(`${PREFIX}/inspections/${id}`),

  updateInspection: (id: number, body: UpdateInspectionRequest) =>
    api.put<InspectionResponse>(`${PREFIX}/inspections/${id}`, body),

  transition: (id: number, body: TransitionRequest) =>
    api.post<InspectionResponse>(`${PREFIX}/inspections/${id}/transition`, body),

  listDr: (inspectionId: number) =>
    api.get<DocumentRequestResponse[]>(
      `${PREFIX}/inspections/${inspectionId}/document-requests`),

  createDr: (inspectionId: number, body: CreateDocumentRequestRequest) =>
    api.post<DocumentRequestResponse>(
      `${PREFIX}/inspections/${inspectionId}/document-requests`, body),

  submitDr: (drId: number, body: SubmitDocumentRequestRequest) =>
    api.post<DocumentRequestResponse>(
      `${PREFIX}/document-requests/${drId}/submit`, body),

  timeline: (inspectionId: number) =>
    api.get<InspectionUpdateResponse[]>(
      `${PREFIX}/inspections/${inspectionId}/timeline`),
};
