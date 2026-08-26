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

/** Status → i18n label key. Exported so the tone-completeness gate can assert every
 *  labellable status also has a STATUS_TONE entry (F2-357). */
export const STATUS_KEY: Record<string, string> = {
  OPEN: 'statusOpen', IN_PROGRESS: 'statusInProgress', CLOSED: 'statusClosed',
  APPROVED: 'statusApproved', PENDING: 'statusPending', UNDER_REVIEW: 'statusReview',
  ACTIVE: 'statusActive', EXPIRED: 'statusExpired', DRAFT: 'statusDraft',
  CONTAINED: 'statusContained', ERADICATED: 'statusEradicated', RECOVERED: 'statusRecovered',
  IMPLEMENTED: 'statusActive', NOT_IMPLEMENTED: 'statusDraft',
  // F2-359 — RESOLVED is the incident status the backend actually writes
  // (IncidentService.legalStatus coerces to OPEN | INVESTIGATING | RESOLVED | CLOSED;
  // the NIST RECOVERY / POST_INCIDENT phases map to it). It was absent from BOTH this map and
  // STATUS_TONE while the phantom RECOVERED (0 rows, not a legal status) carried both — so a
  // RESOLVED incident (10 of 44 register rows) rendered a grey, raw-token badge on an
  // Arabic-primary UI. The F2-357 completeness gate could not catch it: absent from STATUS_KEY,
  // it sat outside that gate's population (STATUS_KEY ⊆ STATUS_TONE says nothing about a status
  // in neither map).
  RESOLVED: 'statusResolved',
  // F2-211 — the five PDPL breach statuses this map did not carry. `statusLabel`
  // falls back to the raw token, so these printed `NOTIFIED` / `INVESTIGATING` /
  // `ESCALATED` / `INVESTIGATED` / `REMEDIATED` in an Arabic-primary UI on 11 of the
  // 17 live rows, beside correctly-localised neighbours. Measured against the stored
  // vocabulary of privacy.data_breaches (7 values), not guessed from the machine —
  // that column has no CHECK and no declared machine, which is filed separately.
  NOTIFIED: 'statusNotified', INVESTIGATING: 'statusInvestigating',
  INVESTIGATED: 'statusInvestigated', ESCALATED: 'statusEscalated',
  REMEDIATED: 'statusRemediated',
  // F2-379 — the evidence subsystem's item + request statuses this map did not carry.
  // EvidencePage renders both the item table and the request table with the shared
  // `statusLabel`/`STATUS_TONE`, and the two declared machines produce PENDING_REVIEW /
  // REJECTED / NEEDS_REVISION (EvidenceItemService.ITEM_STATUSES) and SUBMITTED / REJECTED
  // (EvidenceRequestService's allowed set). None was in STATUS_KEY, so an item in the review
  // queue, a rejected item/request, a revision-requested item and a submitted request all
  // printed their RAW English token + a grey badge on an Arabic-primary UI — on the compliance
  // assurance core (every framework control resolves to approved evidence). Same blind spot the
  // F2-359 leg below names: absent from STATUS_KEY, they sat outside the F2-357 completeness gate.
  // Measured from the declared machines (both enforce transitions), not guessed.
  PENDING_REVIEW: 'statusPendingReview', REJECTED: 'statusRejected',
  NEEDS_REVISION: 'statusNeedsRevision', SUBMITTED: 'statusSubmitted',
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
  RESOLVED: 'success',   // F2-359 — recovery-phase terminal; 'success' (done, awaiting formal closure) matches RECOVERED and stays distinct from CLOSED's neutral (archived).
  IMPLEMENTED: 'success',
  NOT_IMPLEMENTED: 'danger',
  // F2-357 — the five PDPL breach statuses F2-211 added to STATUS_KEY (so statusLabel
  // localises them) were never added here, so on the privacy.data_breaches register
  // (PrivacyPage) and PdplBreachConsole a `STATUS_TONE[status] ?? 'neutral'` badge rendered
  // GREY for them — an ESCALATED breach looked like a CLOSED one, INVESTIGATING/NOTIFIED lost
  // their warning/info tone, REMEDIATED lost its success tone. Tones follow the existing
  // convention (danger = urgent/bad, warning = active, info = in-progress, success = done).
  NOTIFIED: 'info',
  INVESTIGATING: 'warning',
  INVESTIGATED: 'info',
  ESCALATED: 'danger',
  REMEDIATED: 'success',
  // F2-379 — evidence item/request statuses (see STATUS_KEY above). Tones follow the same
  // convention: info = in the review pipeline, warning = awaiting author action, danger = bad
  // outcome. Without these an evidence item in PENDING_REVIEW / REJECTED / NEEDS_REVISION or a
  // request in SUBMITTED / REJECTED rendered a grey neutral badge via the '?? neutral' fallback.
  PENDING_REVIEW: 'info',
  REJECTED: 'danger',
  NEEDS_REVISION: 'warning',
  SUBMITTED: 'info',
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
