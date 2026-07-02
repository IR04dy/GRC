// Vendor ESG surveillance API client — S-O.
//
// Replaces the frontend-only VENDOR_SURVEYS mock (lib/esg.ts) with the real
// backend at /api/sustainability/vendor-surveys/*. Surveys are bound to the
// real vendor register (tprm.vendors); the score is server-computed; a
// non-responding vendor is escalated and a corrective action opened.
//
// Lifecycle: DRAFT → SENT → RESPONDED → ACCEPTED | REJECTED, plus ESCALATED
// (non-response). AR labels live here so no on-screen string is English-only.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

export type VendorSurveyStatus =
  | 'DRAFT' | 'SENT' | 'RESPONDED' | 'ACCEPTED' | 'REJECTED' | 'ESCALATED';

export interface VendorEsgSurvey {
  id: number;
  externalId?: string | null;
  tprmVendorId: number;
  scmSupplierId?: number | null;
  vendorNameEn: string;
  vendorNameAr?: string | null;
  reportingYear: number;
  status: VendorSurveyStatus;
  spendSarK?: number | null;
  dueDate?: string | null;
  sentAt?: string | null;
  respondedAt?: string | null;
  reviewedAt?: string | null;
  reportedScope12?: number | null;
  renewablePct?: number | null;
  saudizationPct?: number | null;
  hasNetZeroTarget?: boolean | null;
  iso14001?: boolean | null;
  esgScore?: number | null;
  evidenceItemId?: number | null;
  linkedCapaId?: number | null;
  rejectReason?: string | null;
  notes?: string | null;
}

export interface VendorEsgResponseInput {
  reportedScope12?: number;
  renewablePct?: number;
  saudizationPct?: number;
  hasNetZeroTarget?: boolean;
  iso14001?: boolean;
}

export interface VendorEsgSurveyCreateInput {
  tprmVendorId: number;
  scmSupplierId?: number;
  reportingYear: number;
  spendSarK?: number;
  reviewerName?: string;
  reviewerEmail?: string;
  notes?: string;
}

export interface VendorEsgAggregate {
  total: number;
  responded: number;
  accepted: number;
  escalated: number;
  outstanding: number;
  totalSpendSarK: number;
  spendCoveragePct: number;
  reportedScope12: number;
  avgEsgScore: number;
  byStatus: Record<string, number>;
}

interface Page<T> { content: T[]; totalElements: number }

const BASE = '/api/sustainability/vendor-surveys';

export const vendorEsgApi = {
  list: (params?: { year?: number; status?: string }) => {
    const q = new URLSearchParams({ size: '200' });
    if (params?.year) q.set('year', String(params.year));
    if (params?.status) q.set('status', params.status);
    return api.get<Page<VendorEsgSurvey>>(`${BASE}?${q.toString()}`);
  },
  aggregate: () => api.get<VendorEsgAggregate>(`${BASE}/aggregate`),
  get: (id: number) => api.get<VendorEsgSurvey>(`${BASE}/${id}`),
  create: (body: VendorEsgSurveyCreateInput) => api.post<VendorEsgSurvey>(BASE, body),
  send: (id: number) => api.post<VendorEsgSurvey>(`${BASE}/${id}/send`),
  recordResponse: (id: number, body: VendorEsgResponseInput) =>
    api.post<VendorEsgSurvey>(`${BASE}/${id}/record-response`, body),
  accept: (id: number) => api.post<VendorEsgSurvey>(`${BASE}/${id}/accept`),
  reject: (id: number, reason: string) => api.post<VendorEsgSurvey>(`${BASE}/${id}/reject`, { reason }),
  sweepOverdue: () => api.post<{ escalated: number; ids: number[] }>(`${BASE}/sweep-overdue`),
};

export const SURVEY_TONE: Record<VendorSurveyStatus, BadgeTone> = {
  DRAFT: 'neutral', SENT: 'info', RESPONDED: 'warning',
  ACCEPTED: 'success', REJECTED: 'danger', ESCALATED: 'danger',
};

export const SURVEY_LABEL: Record<VendorSurveyStatus, { en: string; ar: string }> = {
  DRAFT:     { en: 'Draft',      ar: 'مسودة' },
  SENT:      { en: 'Sent',       ar: 'مُرسل' },
  RESPONDED: { en: 'Responded',  ar: 'تم الرد' },
  ACCEPTED:  { en: 'Accepted',   ar: 'مقبول' },
  REJECTED:  { en: 'Rejected',   ar: 'مرفوض' },
  ESCALATED: { en: 'Escalated',  ar: 'مُصعّد' },
};
