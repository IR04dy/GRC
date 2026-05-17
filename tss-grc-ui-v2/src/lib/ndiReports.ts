// NDI Reports API client (slice 11 · V76).

import { api } from './api';

export type NdiReportType =
  | 'NDI_EXECUTIVE_SUMMARY' | 'MATURITY_HEATMAP' | 'COMPLIANCE_STATUS'
  | 'OPEX_KPI_DASHBOARD' | 'GAP_REGISTER' | 'CAPA_REGISTER'
  | 'IMPROVEMENT_PLAN_ROADMAP' | 'EVIDENCE_INVENTORY'
  | 'DOMAIN_ROLLUP' | 'CYCLE_HISTORY' | 'AUDIT_TRAIL'
  | 'SUBMISSION_PACKAGE_EXPORT';

export type NdiReportFormat = 'JSON' | 'PDF' | 'XLSX' | 'CSV';
export type NdiReportStatus = 'ACTIVE' | 'DISABLED' | 'ARCHIVED';

export interface NdiReportDefinition {
  id?: number;
  organisationId?: number;
  reportCode?: string;
  nameEn: string;
  nameAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  reportType: NdiReportType;
  format: NdiReportFormat;
  language: 'EN' | 'AR' | 'BOTH';
  filtersJson?: string;
  scheduleCron?: string;
  recipients?: string;
  lastRunAt?: string;
  lastRunStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING';
  lastRunMessage?: string;
  lastRunSizeBytes?: number;
  status: NdiReportStatus;
}

export interface ReportResult {
  reportType: NdiReportType;
  cycleId?: number;
  generatedAt: string;
  summary: Record<string, unknown>;
  rows: Array<Record<string, unknown>>;
}

const BASE = '/api/ndi/reports';

export function listReports(type?: NdiReportType) {
  const q = type ? `?type=${type}` : '';
  return api.get<NdiReportDefinition[]>(`${BASE}${q}`);
}
export function getReport(id: number)                { return api.get<NdiReportDefinition>(`${BASE}/${id}`); }
export function createReport(body: Partial<NdiReportDefinition>) { return api.post<NdiReportDefinition>(BASE, body); }
export function updateReport(id: number, body: Partial<NdiReportDefinition>) { return api.put<NdiReportDefinition>(`${BASE}/${id}`, body); }
export function deleteReport(id: number) { return api.delete<void>(`${BASE}/${id}`); }
export function runReport(id: number, cycleId?: number) {
  const q = cycleId != null ? `?cycleId=${cycleId}` : '';
  return api.post<ReportResult>(`${BASE}/${id}/run${q}`);
}
export function runAdHocReport(type: NdiReportType, cycleId?: number) {
  const q = `?type=${type}${cycleId != null ? `&cycleId=${cycleId}` : ''}`;
  return api.post<ReportResult>(`${BASE}/run-adhoc${q}`);
}

/** Trigger a browser download for the given saved-report export. */
export function downloadReport(id: number, format: 'PDF' | 'XLSX' | 'CSV', cycleId?: number) {
  const q = new URLSearchParams({ format });
  if (cycleId != null) q.set('cycleId', String(cycleId));
  const token = sessionStorage.getItem('grc.accessToken') ?? '';
  const url = `${BASE}/${id}/export?${q.toString()}`;
  return fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    .then(async r => {
      if (!r.ok) throw new Error(`Export failed: ${r.status} ${r.statusText}`);
      const blob = await r.blob();
      const cd = r.headers.get('content-disposition') ?? '';
      const m = cd.match(/filename="([^"]+)"/);
      const fname = m ? m[1] : `ndi-report.${format.toLowerCase()}`;
      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl; a.download = fname; document.body.appendChild(a); a.click();
      a.remove(); URL.revokeObjectURL(objectUrl);
    });
}

/** Same as downloadReport but for ad-hoc runs. */
export function downloadAdHocReport(type: NdiReportType, format: 'PDF' | 'XLSX' | 'CSV', cycleId?: number) {
  const q = new URLSearchParams({ type, format });
  if (cycleId != null) q.set('cycleId', String(cycleId));
  const token = sessionStorage.getItem('grc.accessToken') ?? '';
  const url = `${BASE}/export-adhoc?${q.toString()}`;
  return fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    .then(async r => {
      if (!r.ok) throw new Error(`Export failed: ${r.status} ${r.statusText}`);
      const blob = await r.blob();
      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = `ndi-${type.toLowerCase().replace(/_/g, '-')}.${format.toLowerCase()}`;
      document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(objectUrl);
    });
}

export function reportTypeLabel(t: NdiReportType, lang: 'en' | 'ar'): string {
  const en: Record<NdiReportType, string> = {
    NDI_EXECUTIVE_SUMMARY:     'NDI executive summary',
    MATURITY_HEATMAP:          'Maturity heatmap',
    COMPLIANCE_STATUS:         'Compliance status',
    OPEX_KPI_DASHBOARD:        'OpEx KPI dashboard',
    GAP_REGISTER:              'Gap register',
    CAPA_REGISTER:             'CAPA register',
    IMPROVEMENT_PLAN_ROADMAP:  'Improvement plan roadmap',
    EVIDENCE_INVENTORY:        'Evidence inventory',
    DOMAIN_ROLLUP:             'Domain rollup',
    CYCLE_HISTORY:             'Cycle history',
    AUDIT_TRAIL:               'Audit trail',
    SUBMISSION_PACKAGE_EXPORT: 'Submission package export',
  };
  const ar: Record<NdiReportType, string> = {
    NDI_EXECUTIVE_SUMMARY:     'الملخص التنفيذي لنضيء',
    MATURITY_HEATMAP:          'خريطة حرارية للنضج',
    COMPLIANCE_STATUS:         'حالة الالتزام',
    OPEX_KPI_DASHBOARD:        'لوحة مؤشّرات التميز التشغيلي',
    GAP_REGISTER:              'سجل الفجوات',
    CAPA_REGISTER:             'سجل الإجراءات التصحيحية',
    IMPROVEMENT_PLAN_ROADMAP:  'خارطة طريق التحسين',
    EVIDENCE_INVENTORY:        'فهرس الأدلة',
    DOMAIN_ROLLUP:             'تجميع المجالات',
    CYCLE_HISTORY:             'سجل الدورات',
    AUDIT_TRAIL:               'مسار التدقيق',
    SUBMISSION_PACKAGE_EXPORT: 'تصدير حزمة التقديم',
  };
  return (lang === 'ar' ? ar : en)[t];
}
