// Quantitative BIA client — Slice 9-C.
//
// Typed wrappers around /api/v1/bcm/bia/quantitative/* plus a couple of
// helpers (status tone, time-band ordering, loss-type labels) that the
// page reads. Mirrors the shape used by the qualitative BIA client at
// ./biaAssessments.ts — keep the two aligned so a developer can switch
// between them without learning a new convention.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

export type BiaQuantStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'CFO_REVIEW'
  | 'CFO_APPROVED'
  | 'BOARD_APPROVED'
  | 'ARCHIVED'
  | 'REJECTED';

export type BiaQuantTimeBand = '24h' | '72h' | '1w' | '2w' | '1m';

export type BiaQuantLossType =
  | 'REVENUE_LOSS'
  | 'REGULATORY_FINE'
  | 'REPUTATIONAL_DAMAGE'
  | 'OPERATIONAL_RECOVERY'
  | 'LEGAL_COSTS'
  | 'PRODUCTIVITY_LOSS'
  | 'CUSTOMER_CHURN'
  | 'CONTRACTUAL_PENALTY';

export type BiaQuantConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export interface BiaQuantitativeLossLine {
  id?: number;
  timeBand: BiaQuantTimeBand;
  lossType: BiaQuantLossType;
  amountSar: number | string;
  basisNarrativeEn?: string;
  basisNarrativeAr?: string;
  confidence?: BiaQuantConfidence;
  isEstimate?: boolean;
}

export interface BiaQuantitative {
  id: number;
  externalId: string;
  organisationId: number;
  processId: number;
  assessmentCode: string;
  assessmentDate: string;
  cycleYear: number;
  currency: string;
  methodologyNotesEn?: string;
  methodologyNotesAr?: string;
  totalLoss24hSar?: number | string;
  totalLoss72hSar?: number | string;
  totalLoss1wSar?: number | string;
  totalLoss2wSar?: number | string;
  totalLoss1mSar?: number | string;
  maxTolerableLossSar?: number | string;
  mtpdBreachAt?: BiaQuantTimeBand | null;
  status: BiaQuantStatus;
  preparedByUserId?: number;
  preparedByName?: string;
  preparedAt?: string;
  bcmLeadReviewedAt?: string;
  bcmLeadReviewedBy?: string;
  cfoApprovedAt?: string;
  cfoApproverName?: string;
  cfoComments?: string;
  boardApprovedAt?: string;
  boardMinutesRef?: string;
  rejectionReason?: string;
  createdAt?: string;
  updatedAt?: string;
  lossLines: BiaQuantitativeLossLine[];
}

export interface BiaQuantitativeRequest {
  processId: number;
  assessmentCode?: string;
  assessmentDate: string;
  cycleYear: number;
  currency?: string;
  methodologyNotesEn?: string;
  methodologyNotesAr?: string;
  maxTolerableLossSar?: number | string;
  preparedByName?: string;
  preparedByUserId?: number;
  lossLines?: BiaQuantitativeLossLine[];
}

export interface CfoApprovalRequest {
  comments?: string;
  decision: 'APPROVE' | 'REJECT';
  rejectionReason?: string;
}

// ─── Static catalogues ────────────────────────────────────────────────

export const TIME_BANDS: BiaQuantTimeBand[] = ['24h', '72h', '1w', '2w', '1m'];

export const LOSS_TYPES: BiaQuantLossType[] = [
  'REVENUE_LOSS', 'REGULATORY_FINE', 'REPUTATIONAL_DAMAGE',
  'OPERATIONAL_RECOVERY', 'LEGAL_COSTS', 'PRODUCTIVITY_LOSS',
  'CUSTOMER_CHURN', 'CONTRACTUAL_PENALTY',
];

export const LOSS_TYPE_LABELS: Record<BiaQuantLossType, { en: string; ar: string }> = {
  REVENUE_LOSS:         { en: 'Revenue loss',         ar: 'خسارة الإيرادات' },
  REGULATORY_FINE:      { en: 'Regulatory fine',      ar: 'غرامة تنظيمية' },
  REPUTATIONAL_DAMAGE:  { en: 'Reputational damage',  ar: 'أضرار السمعة' },
  OPERATIONAL_RECOVERY: { en: 'Operational recovery', ar: 'تكلفة التعافي التشغيلي' },
  LEGAL_COSTS:          { en: 'Legal costs',          ar: 'تكاليف قانونية' },
  PRODUCTIVITY_LOSS:    { en: 'Productivity loss',    ar: 'فقد الإنتاجية' },
  CUSTOMER_CHURN:       { en: 'Customer churn',       ar: 'خروج العملاء' },
  CONTRACTUAL_PENALTY:  { en: 'Contractual penalty',  ar: 'غرامات تعاقدية' },
};

export const TIME_BAND_LABELS: Record<BiaQuantTimeBand, { en: string; ar: string }> = {
  '24h': { en: '24 hours', ar: '24 ساعة' },
  '72h': { en: '72 hours', ar: '72 ساعة' },
  '1w':  { en: '1 week',   ar: 'أسبوع' },
  '2w':  { en: '2 weeks',  ar: 'أسبوعان' },
  '1m':  { en: '1 month',  ar: 'شهر' },
};

export const STATUS_LABELS: Record<BiaQuantStatus, { en: string; ar: string }> = {
  DRAFT:          { en: 'Draft',          ar: 'مسودة' },
  IN_REVIEW:      { en: 'In review',      ar: 'قيد المراجعة' },
  CFO_REVIEW:     { en: 'CFO review',     ar: 'مراجعة المدير المالي' },
  CFO_APPROVED:   { en: 'CFO approved',   ar: 'اعتمد من المدير المالي' },
  BOARD_APPROVED: { en: 'Board approved', ar: 'اعتمد من المجلس' },
  ARCHIVED:       { en: 'Archived',       ar: 'مؤرشف' },
  REJECTED:       { en: 'Rejected',       ar: 'مرفوض' },
};

export function statusTone(s: BiaQuantStatus): BadgeTone {
  switch (s) {
    case 'DRAFT':          return 'neutral';
    case 'IN_REVIEW':      return 'info';
    case 'CFO_REVIEW':     return 'warning';
    case 'CFO_APPROVED':   return 'success';
    case 'BOARD_APPROVED': return 'success';
    case 'ARCHIVED':       return 'neutral';
    case 'REJECTED':       return 'danger';
  }
}

export function confidenceTone(c?: BiaQuantConfidence): BadgeTone {
  switch (c) {
    case 'HIGH':   return 'success';
    case 'MEDIUM': return 'info';
    case 'LOW':    return 'warning';
    default:        return 'neutral';
  }
}

/** Format a SAR amount compactly (e.g. SAR 12.5M / SAR 940K). */
export function fmtSar(amount?: number | string | null, isAr = false): string {
  if (amount === null || amount === undefined || amount === '') return '—';
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (Number.isNaN(n)) return '—';
  const sym = isAr ? 'ر.س' : 'SAR';
  const abs = Math.abs(n);
  if (abs >= 1_000_000_000) return `${sym} ${(n / 1_000_000_000).toFixed(2)}B`;
  if (abs >= 1_000_000)     return `${sym} ${(n / 1_000_000).toFixed(2)}M`;
  if (abs >= 1_000)         return `${sym} ${(n / 1_000).toFixed(1)}K`;
  return `${sym} ${n.toFixed(0)}`;
}

/** Cumulative loss across bands up to and including the supplied band. */
export function cumulativeLoss(b: BiaQuantitative, upTo: BiaQuantTimeBand): number {
  let sum = 0;
  for (const band of TIME_BANDS) {
    const v = bandTotal(b, band);
    if (v != null) sum += v;
    if (band === upTo) break;
  }
  return sum;
}

export function bandTotal(b: BiaQuantitative, band: BiaQuantTimeBand): number | null {
  const raw = (() => {
    switch (band) {
      case '24h': return b.totalLoss24hSar;
      case '72h': return b.totalLoss72hSar;
      case '1w':  return b.totalLoss1wSar;
      case '2w':  return b.totalLoss2wSar;
      case '1m':  return b.totalLoss1mSar;
    }
  })();
  if (raw === null || raw === undefined || raw === '') return null;
  const n = typeof raw === 'string' ? Number(raw) : raw;
  return Number.isNaN(n) ? null : n;
}

// ─── API calls ────────────────────────────────────────────────────────

const BASE = '/api/v1/bcm/bia/quantitative';

export function listQuantitativeBias(): Promise<BiaQuantitative[]> {
  return api.get<BiaQuantitative[]>(BASE);
}

export function getQuantitativeBia(externalId: string): Promise<BiaQuantitative> {
  return api.get<BiaQuantitative>(`${BASE}/${externalId}`);
}

export function createQuantitativeBia(body: BiaQuantitativeRequest): Promise<BiaQuantitative> {
  return api.post<BiaQuantitative>(BASE, body);
}

export function updateQuantitativeBia(externalId: string, body: BiaQuantitativeRequest): Promise<BiaQuantitative> {
  return api.put<BiaQuantitative>(`${BASE}/${externalId}`, body);
}

export function submitQuantitativeBia(externalId: string): Promise<BiaQuantitative> {
  return api.post<BiaQuantitative>(`${BASE}/${externalId}/submit`);
}

export function bcmReviewQuantitativeBia(externalId: string): Promise<BiaQuantitative> {
  return api.post<BiaQuantitative>(`${BASE}/${externalId}/bcm-review`);
}

export function cfoApproveQuantitativeBia(externalId: string, body: CfoApprovalRequest): Promise<BiaQuantitative> {
  return api.post<BiaQuantitative>(`${BASE}/${externalId}/cfo-approve`, body);
}

export function boardApproveQuantitativeBia(externalId: string, minutesRef: string): Promise<BiaQuantitative> {
  return api.post<BiaQuantitative>(`${BASE}/${externalId}/board-approve`, { minutesRef });
}

export function archiveQuantitativeBia(externalId: string): Promise<BiaQuantitative> {
  return api.delete<BiaQuantitative>(`${BASE}/${externalId}`);
}
