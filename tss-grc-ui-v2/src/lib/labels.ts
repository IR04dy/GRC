// Translation helpers for status / severity tokens used across many modules.

import { type BadgeTone } from '../components/ui/Badge';

/**
 * tlabel — crash-proof bilingual enum-label lookup.
 *
 * Detail pages render badges from `Record<Enum, {en;ar}>` maps via
 * `isAr ? MAP[value].ar : MAP[value].en`. That `.ar`/`.en` access throws
 * `Cannot read properties of undefined` and white-screens the WHOLE record
 * the moment a value isn't in the map — on enum drift (backend adds a value
 * before the frontend map does) or bad data (a real crash was found on an
 * outsourcing arrangement whose `serviceType` had no label entry).
 *
 * `tlabel` returns the localized label, or the raw key as a safe fallback, so
 * a missing entry degrades to e.g. "PAYMENTS" instead of a blank error screen.
 * Behaviour is identical to the inline ternary whenever the key IS mapped, so
 * it is a drop-in replacement.
 */
export type BiLabel = { en: string; ar: string };

export function tlabel(
  map: Record<string, BiLabel> | undefined | null,
  key: string | number | null | undefined,
  isAr: boolean,
): string {
  if (key == null) return '';
  const entry = map?.[key];
  if (!entry) return String(key);
  return isAr ? entry.ar : entry.en;
}

const STATUS_KEY: Record<string, string> = {
  OPEN: 'statusOpen', IN_PROGRESS: 'statusInProgress', CLOSED: 'statusClosed',
  APPROVED: 'statusApproved', PENDING: 'statusPending', UNDER_REVIEW: 'statusReview',
  ACTIVE: 'statusActive', EXPIRED: 'statusExpired', DRAFT: 'statusDraft',
  CONTAINED: 'statusContained', ERADICATED: 'statusEradicated', RECOVERED: 'statusRecovered',
  IMPLEMENTED: 'statusActive', NOT_IMPLEMENTED: 'statusDraft',
  // F2-211 — the five PDPL breach statuses this map did not carry. `statusLabel`
  // falls back to the raw token, so these printed `NOTIFIED` / `INVESTIGATING` /
  // `ESCALATED` / `INVESTIGATED` / `REMEDIATED` in an Arabic-primary UI on 11 of the
  // 17 live rows, beside correctly-localised neighbours. Measured against the stored
  // vocabulary of privacy.data_breaches (7 values), not guessed from the machine —
  // that column has no CHECK and no declared machine, which is filed separately.
  NOTIFIED: 'statusNotified', INVESTIGATING: 'statusInvestigating',
  INVESTIGATED: 'statusInvestigated', ESCALATED: 'statusEscalated',
  REMEDIATED: 'statusRemediated',
};

const SEVERITY_KEY: Record<string, string> = {
  CRITICAL: 'severityCritical', HIGH: 'severityHigh',
  MEDIUM: 'severityMedium', LOW: 'severityLow', INFO: 'severityInfo',
};

export function statusLabel(t: (k: string, fallback?: string) => string, status: string): string {
  return t(STATUS_KEY[status] ?? status, status);
}

export function severityLabel(t: (k: string, fallback?: string) => string, severity: string): string {
  return t(SEVERITY_KEY[severity] ?? severity, severity);
}

/** Map a status value to a Badge tone. */
export const STATUS_TONE: Record<string, BadgeTone> = {
  OPEN: 'danger',
  IN_PROGRESS: 'warning',
  CLOSED: 'neutral',
  APPROVED: 'success',
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  ACTIVE: 'success',
  EXPIRED: 'danger',
  DRAFT: 'neutral',
  CONTAINED: 'info',
  ERADICATED: 'success',
  RECOVERED: 'success',
  IMPLEMENTED: 'success',
  NOT_IMPLEMENTED: 'danger',
};

/** Map a severity value to a Badge tone on the 6-step --risk-* ramp, so HIGH,
 *  VERY_HIGH and CRITICAL stay distinct (the old danger/danger/warning set
 *  collapsed them). Mirrors severityBadgeTone() in ragColors. */
export const SEVERITY_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'risk-crit',
  VERY_HIGH: 'risk-vhigh',
  HIGH: 'risk-high',
  MEDIUM: 'risk-med',
  LOW: 'risk-low',
  VERY_LOW: 'risk-vlow',
  INFO: 'neutral',
};
