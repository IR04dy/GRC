// Sustainability Annex + Reports API client (Slice 3 · V57).

import { api, downloadFile } from './api';

export type RyStatus = 'ACTIVE' | 'DATA_COLLECTION' | 'REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED';
export type ReportStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PUBLISHED' | 'SUPERSEDED';
export type ReportType =
  | 'ANNUAL_ANNEX' | 'BOARD_ESG' | 'CMA_MATRIX' | 'TCFD_REPORT'
  | 'GHG_REPORT' | 'SGI_PROGRESS' | 'EVIDENCE_PKG'
  | 'CLIMATE_RISK' | 'CAPA_STATUS' | 'DASH_EXPORT';

export interface ReportingYear {
  id?: number;
  year: number;
  periodStart?: string;
  periodEnd?: string;
  reportingEntityEn: string;
  reportingEntityAr?: string;
  status?: RyStatus;
  preparedBy?: string;
  reviewedBy?: string;
  approvedBy?: string;
  approvedAt?: string;
  publishedAt?: string;
  readinessScorePct?: number;
  openGapsCount?: number;
  notes?: string;
}

export interface SustainabilityReport {
  id?: number;
  reportingYearId: number;
  reportCode?: string;
  reportType: ReportType;
  titleEn: string;
  titleAr?: string;
  version: string;
  status: ReportStatus;
  generatedAt: string;
  approvedAt?: string;
  publishedAt?: string;
  contentMarkdown?: string;
  contentHtml?: string;
  contentJson?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

// ─── Reporting years ──────────────────────────────────────────────────────

export function listReportingYears() {
  return api.get<ReportingYear[]>('/api/sustainability/reporting-years');
}
export function getReportingYear(id: number) {
  return api.get<ReportingYear>(`/api/sustainability/reporting-years/${id}`);
}
export function createReportingYear(body: Partial<ReportingYear>) {
  return api.post<ReportingYear>('/api/sustainability/reporting-years', body);
}
export function advanceReportingYear(id: number, to: RyStatus) {
  return api.post<ReportingYear>(`/api/sustainability/reporting-years/${id}/advance?to=${to}`);
}

// ─── Reports ──────────────────────────────────────────────────────────────

export function listSustainabilityReports(page = 0, size = 50) {
  return api.get<Page<SustainabilityReport>>(`/api/sustainability/reports?page=${page}&size=${size}`);
}
export function listReportsForYear(ryId: number) {
  return api.get<SustainabilityReport[]>(`/api/sustainability/reporting-years/${ryId}/reports`);
}
export function getSustainabilityReport(id: number) {
  return api.get<SustainabilityReport>(`/api/sustainability/reports/${id}`);
}

/** Generate a fresh Annual Annex draft for the reporting year. */
export function generateAnnex(ryId: number) {
  return api.post<SustainabilityReport>(`/api/sustainability/reporting-years/${ryId}/generate-annex`);
}

/**
 * Generic report generator (slice 12). Composes any of the 10 report
 * types via the backend dispatcher.
 */
export function generateReport(ryId: number, type: ReportType) {
  return api.post<SustainabilityReport>(
    `/api/sustainability/reporting-years/${ryId}/generate`,
    { type }
  );
}

/** Bilingual labels for the 10 report types — used by the dropdown. */
export function reportTypeLabel(type: ReportType, lang: 'en' | 'ar'): string {
  const en: Record<ReportType, string> = {
    ANNUAL_ANNEX:  'Annual Sustainability Annex',
    BOARD_ESG:     'Board ESG Report',
    CMA_MATRIX:    'CMA Disclosure Matrix',
    TCFD_REPORT:   'TCFD Alignment Report',
    GHG_REPORT:    'GHG Inventory Report',
    SGI_PROGRESS:  'SGI Progress Report',
    EVIDENCE_PKG:  'Evidence Package',
    CLIMATE_RISK:  'Climate Risk Register',
    CAPA_STATUS:   'Corrective Actions Status',
    DASH_EXPORT:   'Executive Dashboard Export',
  };
  const ar: Record<ReportType, string> = {
    ANNUAL_ANNEX:  'ملحق الاستدامة السنوي',
    BOARD_ESG:     'تقرير الاستدامة لمجلس الإدارة',
    CMA_MATRIX:    'مصفوفة إفصاحات CMA',
    TCFD_REPORT:   'تقرير محاذاة TCFD',
    GHG_REPORT:    'تقرير سجل الانبعاثات',
    SGI_PROGRESS:  'تقرير تقدّم التزامات SGI',
    EVIDENCE_PKG:  'حزمة الأدلة',
    CLIMATE_RISK:  'سجلّ المخاطر المناخية',
    CAPA_STATUS:   'تقرير حالة الإجراءات التصحيحية',
    DASH_EXPORT:   'تصدير لوحة الإدارة',
  };
  return (lang === 'ar' ? ar : en)[type];
}

export function submitReport(id: number)  { return api.post<SustainabilityReport>(`/api/sustainability/reports/${id}/submit`); }
export function approveReport(id: number) { return api.post<SustainabilityReport>(`/api/sustainability/reports/${id}/approve`); }
export function publishReport(id: number) { return api.post<SustainabilityReport>(`/api/sustainability/reports/${id}/publish`); }

/**
 * Trigger a browser download of the persisted snapshot in the chosen
 * format. The backend sets Content-Disposition so the file lands in
 * the user's downloads with a sensible name.
 */
export function exportReport(id: number, format: 'md' | 'html' | 'json') {
  const ext = format === 'md' ? 'md' : format;
  return downloadFile(`/api/sustainability/reports/${id}/export/${format}`, `report-${id}.${ext}`);
}

// ─── Tone helpers ─────────────────────────────────────────────────────────

export function ryStatusTone(s: RyStatus | undefined):
    'neutral' | 'info' | 'warning' | 'primary' | 'success' {
  switch (s) {
    case 'PUBLISHED':       return 'success';
    case 'APPROVED':        return 'primary';
    case 'REVIEW':          return 'info';
    case 'DATA_COLLECTION': return 'warning';
    case 'ARCHIVED':        return 'neutral';
    default:                return 'warning'; // ACTIVE
  }
}

export function reportStatusTone(s: ReportStatus):
    'neutral' | 'info' | 'warning' | 'primary' | 'success' {
  switch (s) {
    case 'PUBLISHED':  return 'success';
    case 'APPROVED':   return 'primary';
    case 'SUBMITTED':  return 'info';
    case 'SUPERSEDED': return 'neutral';
    default:           return 'warning'; // DRAFT
  }
}
