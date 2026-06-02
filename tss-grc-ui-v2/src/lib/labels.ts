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
  key: string | null | undefined,
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

/** Map a severity value to a Badge tone. */
export const SEVERITY_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'danger',
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'info',
  INFO: 'neutral',
};
