// Risk-Based Vulnerability SLA engine — Sprint 11.
//
// Today every vulnerability gets the same SLA based purely on CVSS
// severity. This engine blends THREE drivers (CVSS severity × asset
// exposure × business criticality) into a single accelerated clock,
// plus auto-escalation when the clock approaches breach.
//
// Saudi-native bilingual page title:
//   ar: اتفاقيات مستوى الخدمة القائمة على المخاطر للثغرات الأمنية
//   en: Risk-Based Vulnerability SLAs
//
// Regulatory drivers:
//   - NCA ECC-2 control 2-12 (Vulnerability Management) — business-aligned
//   - SAMA Cyber Security Framework 3.3.8 — timelines proportionate to impact
//   - CISA SSVC — exposure + criticality drive prioritisation, not raw CVSS

import { api } from './api';

export type VulnSlaSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type VulnSlaExposure = 'PUBLIC' | 'PARTNER' | 'INTERNAL' | 'RESTRICTED';
export type VulnSlaCriticality = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type VulnSlaStatus =
  | 'OPEN'
  | 'WARNING'
  | 'BREACHED'
  | 'REMEDIATED'
  | 'ACCEPTED_RISK'
  | 'FALSE_POSITIVE';

export interface VulnSlaPolicyResponse {
  id: number;
  externalId: string;
  policyCode: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  criticalSlaHours: number | string;
  highSlaHours: number | string;
  mediumSlaHours: number | string;
  lowSlaHours: number | string;
  exposurePublicMul: number | string;
  exposurePartnerMul: number | string;
  exposureInternalMul: number | string;
  exposureRestrictedMul: number | string;
  criticalityCriticalMul: number | string;
  criticalityHighMul: number | string;
  criticalityMediumMul: number | string;
  criticalityLowMul: number | string;
  escalationWarningPct: number | string;
  escalationCriticalPct: number | string;
  isDefault: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface VulnSlaPolicyRequest {
  policyCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  criticalSlaHours: number;
  highSlaHours: number;
  mediumSlaHours: number;
  lowSlaHours: number;
  exposurePublicMul: number;
  exposurePartnerMul: number;
  exposureInternalMul: number;
  exposureRestrictedMul: number;
  criticalityCriticalMul: number;
  criticalityHighMul: number;
  criticalityMediumMul: number;
  criticalityLowMul: number;
  escalationWarningPct: number;
  escalationCriticalPct: number;
  isDefault?: boolean;
}

export interface VulnSlaFindingResponse {
  id: number;
  externalId: string;
  vulnerabilityId: number;
  policyId: number;
  cvssSeverity: VulnSlaSeverity;
  assetId?: number;
  assetExposure: VulnSlaExposure;
  assetCriticality: VulnSlaCriticality;
  baseSlaHours: number | string;
  effectiveSlaHours: number | string;
  openedAt: string;
  dueAt: string;
  lastRecomputedAt: string;
  lastEscalationAt?: string;
  status: VulnSlaStatus;
  remediatedAt?: string;
  remediatedBy?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface VulnSlaComputeRequest {
  vulnerabilityId: number;
  cvssSeverity: VulnSlaSeverity;
  assetId?: number;
  assetExposure?: VulnSlaExposure;
  assetCriticality?: VulnSlaCriticality;
  policyId?: number;
}

export interface VulnSlaDashboardSnapshot {
  totalFindings: number;
  openFindings: number;
  warningFindings: number;
  breachedFindings: number;
  remediatedFindings: number;
  acceptedRiskFindings: number;
  falsePositiveFindings: number;
  breached: VulnSlaFindingResponse[];
  warning: VulnSlaFindingResponse[];
  defaultPolicy?: VulnSlaPolicyResponse;
}

// ─── REST client ─────────────────────────────────────────────────────

export const vulnSlaApi = {
  // Policy CRUD
  listPolicies: () => api.get<VulnSlaPolicyResponse[]>('/api/v1/cyber/vuln-sla/policies'),
  getPolicy:    (externalId: string) => api.get<VulnSlaPolicyResponse>(`/api/v1/cyber/vuln-sla/policies/${externalId}`),
  createPolicy: (body: VulnSlaPolicyRequest) => api.post<VulnSlaPolicyResponse>('/api/v1/cyber/vuln-sla/policies', body),
  updatePolicy: (externalId: string, body: VulnSlaPolicyRequest) =>
    api.put<VulnSlaPolicyResponse>(`/api/v1/cyber/vuln-sla/policies/${externalId}`, body),
  setDefault:   (externalId: string) =>
    api.post<VulnSlaPolicyResponse>(`/api/v1/cyber/vuln-sla/policies/${externalId}/default`),
  deletePolicy: (externalId: string) =>
    api.delete<void>(`/api/v1/cyber/vuln-sla/policies/${externalId}`),

  // SLA findings
  compute:    (body: VulnSlaComputeRequest) =>
    api.post<VulnSlaFindingResponse>('/api/v1/cyber/vuln-sla/compute', body),
  recompute:  () =>
    api.post<{ touched: number }>('/api/v1/cyber/vuln-sla/recompute'),
  runEscalation: () =>
    api.post<{ warningsEmitted: number; breachesEmitted: number }>('/api/v1/cyber/vuln-sla/escalation/run'),
  listFindings:  (status?: VulnSlaStatus) =>
    api.get<VulnSlaFindingResponse[]>(`/api/v1/cyber/vuln-sla/findings${status ? `?status=${status}` : ''}`),
  getFinding:    (externalId: string) =>
    api.get<VulnSlaFindingResponse>(`/api/v1/cyber/vuln-sla/findings/${externalId}`),
  markRemediated: (externalId: string, note?: string) =>
    api.post<VulnSlaFindingResponse>(`/api/v1/cyber/vuln-sla/findings/${externalId}/remediated`, { note }),

  // Dashboard
  dashboard: () => api.get<VulnSlaDashboardSnapshot>('/api/v1/cyber/vuln-sla/dashboard'),
};

// ─── Saudi-native bilingual labels ──────────────────────────────────

export const VULN_SLA_SEVERITY_LABEL: Record<VulnSlaSeverity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const VULN_SLA_SEVERITY_TONE: Record<VulnSlaSeverity, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger',
  HIGH:     'warning',
  MEDIUM:   'info',
  LOW:      'neutral',
};

export const VULN_SLA_EXPOSURE_LABEL: Record<VulnSlaExposure, { en: string; ar: string }> = {
  PUBLIC:     { en: 'Public',     ar: 'عام' },
  PARTNER:    { en: 'Partner',    ar: 'شريك' },
  INTERNAL:   { en: 'Internal',   ar: 'داخلي' },
  RESTRICTED: { en: 'Restricted', ar: 'مقيّد' },
};

export const VULN_SLA_CRITICALITY_LABEL: Record<VulnSlaCriticality, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const VULN_SLA_STATUS_LABEL: Record<VulnSlaStatus, { en: string; ar: string }> = {
  OPEN:           { en: 'Open',            ar: 'مفتوح' },
  WARNING:        { en: 'Warning',         ar: 'تنبيه قبل الإخلال' },
  BREACHED:       { en: 'Breached',        ar: 'مُخَلّ بالاتفاقية' },
  REMEDIATED:     { en: 'Remediated',      ar: 'مُعالَج' },
  ACCEPTED_RISK:  { en: 'Accepted risk',   ar: 'مخاطرة مقبولة' },
  FALSE_POSITIVE: { en: 'False positive',  ar: 'إيجابي كاذب' },
};

export const VULN_SLA_STATUS_TONE: Record<VulnSlaStatus, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  OPEN:           'info',
  WARNING:        'warning',
  BREACHED:       'danger',
  REMEDIATED:     'success',
  ACCEPTED_RISK:  'neutral',
  FALSE_POSITIVE: 'neutral',
};

// ─── Pure math for the policy preview heatmap ───────────────────────

/**
 * Mirror of the backend's compute formula. The policy editor calls
 * this for every (severity × exposure × criticality) cell so the user
 * can SEE the impact of every multiplier change before saving.
 *
 *   base × exposureMul × criticalityMul   (then floor at 0.25 × base)
 *
 * Keep this in sync with VulnerabilitySlaComputeService.applyFloor.
 */
export function previewEffectiveHours(
  policy: VulnSlaPolicyRequest | VulnSlaPolicyResponse,
  severity: VulnSlaSeverity,
  exposure: VulnSlaExposure,
  criticality: VulnSlaCriticality
): number {
  const base = numericBaseHours(policy, severity);
  const expMul = numericExposureMul(policy, exposure);
  const critMul = numericCriticalityMul(policy, criticality);
  const raw = base * expMul * critMul;
  const floor = base * 0.25;
  return Math.max(raw, floor);
}

function num(v: number | string | undefined | null, fallback = 0): number {
  if (v == null) return fallback;
  if (typeof v === 'number') return v;
  const n = parseFloat(v);
  return Number.isFinite(n) ? n : fallback;
}

function numericBaseHours(
  p: VulnSlaPolicyRequest | VulnSlaPolicyResponse,
  s: VulnSlaSeverity
): number {
  switch (s) {
    case 'CRITICAL': return num(p.criticalSlaHours, 1);
    case 'HIGH':     return num(p.highSlaHours, 4);
    case 'MEDIUM':   return num(p.mediumSlaHours, 24);
    case 'LOW':      return num(p.lowSlaHours, 72);
  }
}

function numericExposureMul(
  p: VulnSlaPolicyRequest | VulnSlaPolicyResponse,
  e: VulnSlaExposure
): number {
  switch (e) {
    case 'PUBLIC':     return num(p.exposurePublicMul, 0.5);
    case 'PARTNER':    return num(p.exposurePartnerMul, 0.75);
    case 'INTERNAL':   return num(p.exposureInternalMul, 1.0);
    case 'RESTRICTED': return num(p.exposureRestrictedMul, 1.5);
  }
}

function numericCriticalityMul(
  p: VulnSlaPolicyRequest | VulnSlaPolicyResponse,
  c: VulnSlaCriticality
): number {
  switch (c) {
    case 'CRITICAL': return num(p.criticalityCriticalMul, 0.5);
    case 'HIGH':     return num(p.criticalityHighMul, 0.75);
    case 'MEDIUM':   return num(p.criticalityMediumMul, 1.0);
    case 'LOW':      return num(p.criticalityLowMul, 1.5);
  }
}

/** Pretty-format an hour count for the heatmap cell. */
export function formatHours(h: number | string | undefined | null): string {
  if (h == null) return '—';
  const n = typeof h === 'number' ? h : parseFloat(h);
  if (!Number.isFinite(n)) return '—';
  if (n >= 24) {
    const days = n / 24;
    if (days === Math.floor(days)) return `${days}d`;
    return `${days.toFixed(1)}d`;
  }
  if (n >= 1) return `${n.toFixed(n === Math.floor(n) ? 0 : 1)}h`;
  // Sub-hour — show minutes so the operator can read "15 min" rather than "0.25h".
  const mins = Math.round(n * 60);
  return `${mins} min`;
}

/** Tone for an effective-hours cell — visualises tightness on the heatmap. */
export function effectiveHoursTone(
  hours: number,
  base: number
): 'danger' | 'warning' | 'info' | 'success' {
  const ratio = base > 0 ? hours / base : 1;
  if (ratio <= 0.3) return 'danger';   // hit floor or near it
  if (ratio <= 0.6) return 'warning';
  if (ratio <= 1.0) return 'info';
  return 'success';                    // 1.5× base — operator has buffer
}
