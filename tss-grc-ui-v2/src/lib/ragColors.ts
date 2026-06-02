/**
 * ragColors — single source of truth for risk / severity / RAG colour.
 *
 * The design review found "four different criticals" across the app because
 * every page hardcoded its own hex. This helper returns design-token `var(...)`
 * strings so risk colour is consistent, dark-mode-correct, and never drifts.
 *
 * Use in SVG fills, chart series, badge tones, and cell backgrounds — anywhere
 * a risk score, severity level, or RAG percentage drives colour.
 */

import type { BadgeTone } from '../components/ui/Badge';
import type { GaugeBand } from '../components/viz/RadialGauge';

/** Map a 1–25 risk score (likelihood × impact) to a `--risk-*` token colour. */
export function riskColorForScore(score: number): string {
  if (score >= 20) return 'var(--risk-crit)';
  if (score >= 15) return 'var(--risk-vhigh)';
  if (score >= 10) return 'var(--risk-high)';
  if (score >= 5)  return 'var(--risk-med)';
  if (score >= 3)  return 'var(--risk-low)';
  return 'var(--risk-vlow)';
}

/** Map a 1–25 risk score to a short severity label (bilingual handled by caller). */
export function riskBandForScore(score: number): 'CRITICAL' | 'VERY_HIGH' | 'HIGH' | 'MEDIUM' | 'LOW' {
  if (score >= 20) return 'CRITICAL';
  if (score >= 15) return 'VERY_HIGH';
  if (score >= 10) return 'HIGH';
  if (score >= 5)  return 'MEDIUM';
  return 'LOW';
}

/** Map a severity / risk-level label to its `--risk-*` token colour. */
export function severityColor(level: string | null | undefined): string {
  switch ((level ?? '').toUpperCase()) {
    case 'CRITICAL':                       return 'var(--risk-crit)';
    case 'VERY_HIGH': case 'VHIGH':        return 'var(--risk-vhigh)';
    case 'HIGH':                           return 'var(--risk-high)';
    case 'MEDIUM': case 'MED':             return 'var(--risk-med)';
    case 'LOW':                            return 'var(--risk-low)';
    case 'VERY_LOW': case 'VLOW':
    case 'INFO': case 'MINIMAL': case 'NEGLIGIBLE': return 'var(--risk-vlow)';
    default:                               return 'var(--ink-4)';
  }
}

/**
 * Map a severity / risk-level label to a Badge tone on the 6-step `--risk-*`
 * ramp. This is the badge counterpart of `severityColor()` — it keeps HIGH vs
 * VERY_HIGH vs CRITICAL visually distinct, which the coarse semantic set
 * (danger/warning/info) collapses. Handles the common label vocabularies
 * (CRITICAL/HIGH/MEDIUM/LOW, P1–P4, MINOR/MODERATE/MAJOR). INFO/observation
 * fall through to a neutral chip.
 */
export function severityBadgeTone(level: string | null | undefined): BadgeTone {
  switch ((level ?? '').toUpperCase()) {
    case 'CRITICAL': case 'CRIT': case 'P1':                 return 'risk-crit';
    case 'VERY_HIGH': case 'VHIGH': case 'SEVERE':           return 'risk-vhigh';
    case 'HIGH': case 'MAJOR': case 'P2':                    return 'risk-high';
    case 'MEDIUM': case 'MED': case 'MODERATE': case 'P3':   return 'risk-med';
    case 'LOW': case 'MINOR': case 'P4':                     return 'risk-low';
    case 'VERY_LOW': case 'VLOW':                            return 'risk-vlow';
    default:                                                 return 'neutral';
  }
}

export type Rag = 'success' | 'info' | 'warning' | 'danger';

/**
 * RAG tone for a 0–100 percentage metric. Defaults match the dashboards'
 * existing `tonePct` cutoffs (85 / 60 / 30) so this is a drop-in replacement
 * for the duplicated copies. Pass `invert: true` when lower is better.
 */
export function ragForPercent(
  pct: number,
  opts?: { good?: number; ok?: number; warn?: number; invert?: boolean },
): Rag {
  const good = opts?.good ?? 85;
  const ok   = opts?.ok ?? 60;
  const warn = opts?.warn ?? 30;
  const v = opts?.invert ? 100 - pct : pct;
  if (v >= good) return 'success';
  if (v >= ok)   return 'info';
  if (v >= warn) return 'warning';
  return 'danger';
}

/**
 * Canonical RAG breakpoints for a "higher is better" 0–100 percentage —
 * readiness, compliance, completion, SLA health. ONE set everywhere so a
 * gauge and its KPI can never disagree. Before this the gauges banded at
 * 60/85 (Cyber, BCM), 70/90 (PDPL) and 60/80 (Executive) while the KPI
 * `tonePct` used 30/60/85 with a blue `info` tier — so a 50% metric was red
 * on the gauge but amber on the KPI, and a 70% metric amber vs blue.
 */
export const RAG_PCT = { warn: 60, good: 85 } as const;

/**
 * RAG tone for a higher-is-better percentage, keyed on RAG_PCT. Three bands
 * (no `info`) so it lines up exactly with the gauge zones from `ragBands()`.
 */
export function ragTonePercent(pct: number | null | undefined): Rag {
  const v = pct ?? 0;
  if (v >= RAG_PCT.good) return 'success';
  if (v >= RAG_PCT.warn) return 'warning';
  return 'danger';
}

/** Faint RAG zones for a RadialGauge, aligned to RAG_PCT (so arc-colour and
 *  the KPI tone agree at every value). `to: 101` keeps 100% inside green. */
export function ragBands(): GaugeBand[] {
  return [
    { from: 0,            to: RAG_PCT.warn, color: 'var(--danger)'  },
    { from: RAG_PCT.warn, to: RAG_PCT.good, color: 'var(--warning)' },
    { from: RAG_PCT.good, to: 101,          color: 'var(--success)' },
  ];
}

/** The `var(--…)` colour for a RAG tone. */
export function ragColor(rag: Rag): string {
  return `var(--${rag})`;
}

/** RAG tone → Badge tone (Badge uses the same vocabulary, plus 'neutral'). */
export function ragBadgeTone(rag: Rag): BadgeTone {
  return rag;
}
