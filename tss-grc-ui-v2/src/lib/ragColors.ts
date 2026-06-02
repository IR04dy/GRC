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

/** The `var(--…)` colour for a RAG tone. */
export function ragColor(rag: Rag): string {
  return `var(--${rag})`;
}

/** RAG tone → Badge tone (Badge uses the same vocabulary, plus 'neutral'). */
export function ragBadgeTone(rag: Rag): BadgeTone {
  return rag;
}
