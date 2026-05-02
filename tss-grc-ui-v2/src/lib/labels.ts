// Translation helpers for status / severity tokens used across many modules.

import { type BadgeTone } from '../components/ui/Badge';

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
