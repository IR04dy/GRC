// Sustainability Dashboard API client (slice 11).
//
// Single-call aggregator that pulls every slice's readiness/aggregate
// into one payload + the composite ESG Overall Score. Saves the
// frontend 8 round-trips on first paint of /esg.

import { api } from './api';

export type EsgBand = 'STRONG' | 'ADEQUATE' | 'DEVELOPING' | 'CRITICAL';

export interface DashboardScoreBreakdown {
  ghgVerification: number;
  sgiOnTrack: number;
  cmaDisclosure: number;
  tcfdAlignment: number;
  capaClosure: number;
  evidenceApproved: number;
  climateTreated: number;
}

export interface DashboardPayload {
  year: number;
  organisationId: number;
  overall: {
    score: number;
    band: EsgBand;
    breakdown: DashboardScoreBreakdown;
  };
  ghg: {
    year: number;
    scope1Total: number; scope2Total: number; scope3Total: number;
    grandTotal: number;
    scope3Coverage: number;
    scope3PartialFlag: boolean;
    recordCount: number;
  };
  ghgYoy: {
    year: number;
    scope1YoY: number; scope2YoY: number; scope3YoY: number; totalYoY: number;
  };
  sgi: {
    activeTotal: number; achievedTotal: number; retiredTotal: number;
    byTier: Record<string, number>;
    bySector: Record<string, number>;
  };
  cma: {
    year: number; total: number; applicable: number;
    byStatus: Record<string, number>;
    withEvidence: number; withGap: number;
    readinessPct: number;
  };
  tcfd: {
    year: number; total: number; applicable: number;
    byStatus: Record<string, number>;
    byPillar: Record<string, number>;
    disclosedByPillar: Record<string, number>;
    withEvidence: number; withGap: number;
    alignmentPct: number;
    strategyResilienceGap: boolean;
    strategyResilienceItemId: number | null;
  };
  capa: {
    open: number; inProgress: number; completed: number; verified: number;
    overdue: number; dueSoon: number;
    byPriority: Record<string, number>;
    bySource: Record<string, number>;
    activeTotal: number;
  };
  evidence: {
    total: number;
    byStatus: Record<string, number>;
    byModule: Record<string, number>;
    expiringSoon30d: number;
  };
  materiality: {
    year: number; total: number;
    byTier: Record<string, number>;
    byCategory: Record<string, number>;
    criticalCount: number; highCount: number;
    matrix: Array<{ id: number; topicCode?: string; topicEn: string; topicAr?: string;
      category: string; stakeholderImportance: number; businessImpact: number;
      materialityScore: number; priorityTier: string; status: string; }>;
  };
  metrics: {
    year: number; total: number;
    byCategory: Record<string, number>;
    atTargetByCategory: Record<string, number>;
    offTrackByCategory: Record<string, number>;
    meanProgressByCategory: Record<string, number>;
  };
  topGaps: string[];
}

export function getDashboard(year = 2025) {
  return api.get<DashboardPayload>(`/api/sustainability/dashboard?year=${year}`);
}

// ─── UI helpers ───────────────────────────────────────────────────────

export function esgBandTone(b: EsgBand):
    'success' | 'info' | 'warning' | 'danger' {
  switch (b) {
    case 'STRONG':     return 'success';
    case 'ADEQUATE':   return 'info';
    case 'DEVELOPING': return 'warning';
    case 'CRITICAL':   return 'danger';
  }
}

export function esgBandLabel(b: EsgBand, lang: 'en' | 'ar'): string {
  const en: Record<EsgBand, string> = {
    STRONG: 'Strong', ADEQUATE: 'Adequate',
    DEVELOPING: 'Developing', CRITICAL: 'Critical gaps',
  };
  const ar: Record<EsgBand, string> = {
    STRONG: 'قوي', ADEQUATE: 'كافٍ',
    DEVELOPING: 'قيد التطوّر', CRITICAL: 'ثغرات حرجة',
  };
  return (lang === 'ar' ? ar : en)[b];
}

export function esgBandColor(b: EsgBand): string {
  switch (b) {
    case 'STRONG':     return 'var(--success)';
    case 'ADEQUATE':   return 'var(--info)';
    case 'DEVELOPING': return 'var(--warning)';
    case 'CRITICAL':   return 'var(--danger)';
  }
}

/** Bilingual rendering for the topGaps[] codes returned by the backend. */
export function topGapLabel(code: string, lang: 'en' | 'ar'): string {
  // Codes carry an optional numeric suffix (e.g. CAPA_OVERDUE_3) that
  // we extract for the label.
  const m = code.match(/^([A-Z_]+?)(?:_(\d+))?$/);
  const base = m ? m[1] : code;
  const n    = m && m[2] ? Number(m[2]) : null;

  const en: Record<string, string> = {
    SCOPE_3_PARTIAL_COVERAGE:       'Scope 3 coverage below 70% (Rule 1)',
    TCFD_STRATEGY_RESILIENCE_GAP:   'TCFD Strategy resilience scenario not fully disclosed (Rule 7)',
    CAPA_OVERDUE:                   n ? `${n} corrective action(s) overdue` : 'Overdue corrective actions',
    EVIDENCE_EXPIRING:              n ? `${n} evidence record(s) expiring within 30 days` : 'Evidence expiring soon',
    CMA_INCOMPLETE:                 n ? `${n} CMA disclosure(s) Partial or Not Disclosed` : 'CMA disclosures incomplete',
    SGI_BEHIND:                     n ? `${n} SGI commitment(s) at-risk or delayed` : 'SGI commitments behind plan',
  };
  const ar: Record<string, string> = {
    SCOPE_3_PARTIAL_COVERAGE:       'تغطية النطاق 3 دون 70% (القاعدة 1)',
    TCFD_STRATEGY_RESILIENCE_GAP:   'سيناريو صمود استراتيجية TCFD غير مُفصَح بالكامل (القاعدة 7)',
    CAPA_OVERDUE:                   n ? `${n} إجراء(ات) تصحيحية متأخّرة` : 'إجراءات تصحيحية متأخّرة',
    EVIDENCE_EXPIRING:              n ? `${n} دليل ينتهي خلال 30 يوماً` : 'أدلة تنتهي قريباً',
    CMA_INCOMPLETE:                 n ? `${n} إفصاح(ات) CMA جزئية أو غير مُفصَحة` : 'إفصاحات CMA ناقصة',
    SGI_BEHIND:                     n ? `${n} التزام(ات) SGI متأخّرة أو معرّضة للخطر` : 'التزامات SGI متأخّرة',
  };
  return (lang === 'ar' ? ar : en)[base] ?? code;
}
