// Key Risk Indicator (KRI) Engine — Sprint 13.
//
// Tri-track regulatory driver:
//   SAMA Compliance Framework §4.2 — documented KRIs, reviewed monthly.
//   NCA ECC-2 control 2-12         — continuous monitoring of cyber
//                                    posture metrics.
//   SAMA BCF §3.5 + PDPL Art.4/29  — BCM + privacy KRIs (DSR aging,
//                                    months-since-last-exercise).
//
// Saudi-native bilingual labels:
//   ar: مؤشّرات المخاطر الرئيسية    ← NOT literal "كاي آر آي"
//   en: Key Risk Indicators (KRIs)
//
// Replaces the manual "auditor refreshes the KRI pivot in Excel
// quarterly" ritual that today gates the audit committee meeting.

import { api } from './api';

export type KriCategory =
  | 'CYBER'
  | 'OPERATIONAL'
  | 'CREDIT'
  | 'MARKET'
  | 'LIQUIDITY'
  | 'COMPLIANCE'
  | 'REPUTATIONAL'
  | 'STRATEGIC'
  | 'AML'
  | 'PRIVACY'
  | 'BCM'
  | 'ESG';

export type KriMeasurementUnit =
  | 'COUNT'
  | 'PERCENTAGE'
  | 'RATIO'
  | 'CURRENCY_SAR'
  | 'DAYS'
  | 'HOURS'
  | 'BPS';

export type KriValueDirection = 'LOWER_IS_BETTER' | 'HIGHER_IS_BETTER';
export type KriFrequency =
  | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
export type KriSourceType =
  | 'MANUAL' | 'SQL' | 'CALLBACK' | 'METRIC_API';
export type KriStatus = 'GREEN' | 'AMBER' | 'RED' | 'NA';
export type KriBreachLevel = 'AMBER' | 'RED';
export type KriMeasurementSource =
  | 'AUTO_SQL' | 'AUTO_CALLBACK' | 'MANUAL' | 'IMPORTED';

export interface KriDefinitionResponse {
  id: number;
  externalId: string;
  kriCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  category: KriCategory;
  measurementUnit: KriMeasurementUnit;
  valueDirection: KriValueDirection;
  targetValue?: number;
  greenThreshold?: number;
  amberThreshold?: number;
  redThreshold?: number;
  frequency: KriFrequency;
  sourceQueryType: KriSourceType;
  sourceQuery?: string;
  ownerUserId?: number;
  linkedRiskId?: number;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
  latestValue?: number;
  latestStatus?: KriStatus;
  latestMeasuredAt?: string;
}

export interface KriDefinitionRequest {
  kriCode?: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  category: KriCategory;
  measurementUnit: KriMeasurementUnit;
  valueDirection?: KriValueDirection;
  targetValue?: number;
  greenThreshold?: number;
  amberThreshold?: number;
  redThreshold?: number;
  frequency?: KriFrequency;
  sourceQueryType?: KriSourceType;
  sourceQuery?: string;
  ownerUserId?: number;
  linkedRiskId?: number;
  isActive?: boolean;
}

export interface KriMeasurementResponse {
  id: number;
  externalId: string;
  kriId: number;
  kriCode?: string;
  measuredAt: string;
  value: number;
  status: KriStatus;
  source: KriMeasurementSource;
  measuredByUserId?: number;
  notesEn?: string;
  notesAr?: string;
}

export interface KriMeasurementRequest {
  value: number;
  measuredAt?: string;
  source?: KriMeasurementSource;
  notesEn?: string;
  notesAr?: string;
}

export interface KriBreachResponse {
  id: number;
  externalId: string;
  kriId: number;
  kriCode?: string;
  titleEn?: string;
  titleAr?: string;
  measurementId: number;
  breachLevel: KriBreachLevel;
  openedAt: string;
  closedAt?: string;
  acknowledgedByUserId?: number;
  acknowledgedAt?: string;
  capaTaskId?: number;
}

export interface KriDashboardCell {
  externalId: string;
  kriCode: string;
  titleEn: string;
  titleAr: string;
  category: KriCategory;
  status: KriStatus;
  latestValue?: number;
  measurementUnit: KriMeasurementUnit;
  latestMeasuredAt?: string;
  hasOpenBreach: boolean;
}

export interface KriDashboardSnapshot {
  totalKris: number;
  activeKris: number;
  greenCount: number;
  amberCount: number;
  redCount: number;
  naCount: number;
  openBreachAmber: number;
  openBreachRed: number;
  cells: KriDashboardCell[];
  openBreaches: KriBreachResponse[];
}

export interface KriHistoryPoint {
  measuredAt: string;
  value: number;
  status: KriStatus;
}

// ─── REST client ─────────────────────────────────────────────────────

export const kriApi = {
  listDefinitions: (category?: KriCategory) =>
    api.get<KriDefinitionResponse[]>(
      `/api/v1/risk/kri/definitions${category ? `?category=${category}` : ''}`),
  getDefinition: (externalId: string) =>
    api.get<KriDefinitionResponse>(`/api/v1/risk/kri/definitions/${externalId}`),
  createDefinition: (body: KriDefinitionRequest) =>
    api.post<KriDefinitionResponse>('/api/v1/risk/kri/definitions', body),
  updateDefinition: (externalId: string, body: KriDefinitionRequest) =>
    api.put<KriDefinitionResponse>(`/api/v1/risk/kri/definitions/${externalId}`, body),
  activate: (externalId: string) =>
    api.post<KriDefinitionResponse>(`/api/v1/risk/kri/definitions/${externalId}/activate`),
  deactivate: (externalId: string) =>
    api.post<KriDefinitionResponse>(`/api/v1/risk/kri/definitions/${externalId}/deactivate`),

  recordMeasurement: (externalId: string, body: KriMeasurementRequest) =>
    api.post<KriMeasurementResponse>(`/api/v1/risk/kri/${externalId}/measurements`, body),
  listMeasurements: (externalId: string, limit = 90) =>
    api.get<KriMeasurementResponse[]>(`/api/v1/risk/kri/${externalId}/measurements?limit=${limit}`),
  history: (externalId: string, days = 90) =>
    api.get<KriHistoryPoint[]>(`/api/v1/risk/kri/${externalId}/history?days=${days}`),

  openBreaches: () =>
    api.get<KriBreachResponse[]>('/api/v1/risk/kri/breaches/open'),
  breachesForKri: (externalId: string) =>
    api.get<KriBreachResponse[]>(`/api/v1/risk/kri/${externalId}/breaches`),
  acknowledgeBreach: (breachExternalId: string) =>
    api.post<KriBreachResponse>(`/api/v1/risk/kri/breaches/${breachExternalId}/acknowledge`),
  closeBreach: (breachExternalId: string) =>
    api.post<KriBreachResponse>(`/api/v1/risk/kri/breaches/${breachExternalId}/close`),

  dashboard: () => api.get<KriDashboardSnapshot>('/api/v1/risk/kri/dashboard'),
};

// ─── Display labels (Saudi-native — NOT literal translations) ───────

export const KRI_STATUS_LABEL: Record<KriStatus, { en: string; ar: string }> = {
  GREEN: { en: 'Within target',      ar: 'ضمن الهدف' },
  AMBER: { en: 'Watch',              ar: 'مراقبة' },
  RED:   { en: 'Breach',             ar: 'خرق المؤشر' },
  NA:    { en: 'No data',            ar: 'لا توجد بيانات' },
};

export const KRI_STATUS_TONE: Record<KriStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  GREEN: 'success',
  AMBER: 'warning',
  RED:   'danger',
  NA:    'neutral',
};

export const KRI_CATEGORY_LABEL: Record<KriCategory, { en: string; ar: string }> = {
  CYBER:        { en: 'Cyber',              ar: 'الأمن السيبراني' },
  OPERATIONAL:  { en: 'Operational',        ar: 'تشغيلية' },
  CREDIT:       { en: 'Credit',             ar: 'ائتمانية' },
  MARKET:       { en: 'Market',             ar: 'السوق' },
  LIQUIDITY:    { en: 'Liquidity',          ar: 'السيولة' },
  COMPLIANCE:   { en: 'Compliance',         ar: 'الامتثال' },
  REPUTATIONAL: { en: 'Reputational',       ar: 'السمعة' },
  STRATEGIC:    { en: 'Strategic',          ar: 'استراتيجية' },
  AML:          { en: 'AML',                ar: 'مكافحة غسل الأموال' },
  PRIVACY:      { en: 'Privacy',            ar: 'الخصوصية' },
  BCM:          { en: 'Business continuity', ar: 'استمرارية الأعمال' },
  ESG:          { en: 'ESG',                ar: 'الاستدامة (ESG)' },
};

export const KRI_UNIT_LABEL: Record<KriMeasurementUnit, { en: string; ar: string }> = {
  COUNT:        { en: 'count',     ar: 'عدد' },
  PERCENTAGE:   { en: '%',         ar: '%' },
  RATIO:        { en: 'ratio',     ar: 'نسبة' },
  CURRENCY_SAR: { en: 'SAR',       ar: 'ر.س' },
  DAYS:         { en: 'days',      ar: 'يوم' },
  HOURS:        { en: 'hours',     ar: 'ساعة' },
  BPS:          { en: 'bps',       ar: 'نقطة أساس' },
};

export const KRI_FREQUENCY_LABEL: Record<KriFrequency, { en: string; ar: string }> = {
  DAILY:     { en: 'Daily',     ar: 'يومي' },
  WEEKLY:    { en: 'Weekly',    ar: 'أسبوعي' },
  MONTHLY:   { en: 'Monthly',   ar: 'شهري' },
  QUARTERLY: { en: 'Quarterly', ar: 'ربع سنوي' },
  ANNUAL:    { en: 'Annual',    ar: 'سنوي' },
};

export const KRI_SOURCE_LABEL: Record<KriSourceType, { en: string; ar: string }> = {
  MANUAL:     { en: 'Manual entry',     ar: 'إدخال يدوي' },
  SQL:        { en: 'SQL query',        ar: 'استعلام SQL' },
  CALLBACK:   { en: 'In-process probe', ar: 'مسبار داخلي' },
  METRIC_API: { en: 'External metrics', ar: 'مقاييس خارجية' },
};

/** Format a measurement value with its unit for table display. */
export function formatKriValue(value: number | undefined | null,
                               unit: KriMeasurementUnit): string {
  if (value === undefined || value === null) return '—';
  switch (unit) {
    case 'PERCENTAGE':
      return `${Number(value).toLocaleString('en-GB', { maximumFractionDigits: 2 })}%`;
    case 'CURRENCY_SAR':
      return `${Number(value).toLocaleString('en-GB', { maximumFractionDigits: 0 })} ر.س`;
    case 'BPS':
      return `${Number(value).toLocaleString('en-GB', { maximumFractionDigits: 0 })} bps`;
    case 'DAYS':
      return `${Number(value).toLocaleString('en-GB', { maximumFractionDigits: 1 })} d`;
    case 'HOURS':
      return `${Number(value).toLocaleString('en-GB', { maximumFractionDigits: 1 })} h`;
    case 'RATIO':
      return Number(value).toLocaleString('en-GB', { maximumFractionDigits: 3 });
    default:
      return Number(value).toLocaleString('en-GB', { maximumFractionDigits: 0 });
  }
}
