// RFC-7807 ProblemDetail extraction for ApiError.
//
// Backend PR #117 standardised `@ExceptionHandler` responses to RFC-7807:
// every error carries { type, title, status, detail, properties? } in the
// body. The most user-visible one is "Module not yet bootstrapped" (503)
// returned by GlobalExceptionHandler.handleMissingDbObject() +
// handleUnexpectedRollback() when a JPA query hits a missing table —
// typically a fresh dev DB that hasn't run the per-module setup_*_db.py.
//
// This module gives the frontend three small helpers:
//   - `getProblemDetail(err)`    → typed ProblemDetail or undefined
//   - `getErrorDisplayMessage()` → best human string (title + detail or fallback)
//   - `isBootstrapNeeded(err)`   → recognise the bootstrap-missing flavour
//
// Pages pass an `ApiError` (or anything from a catch) and get back a
// sensible string + a flag to switch UX when the user just needs to run
// the bootstrap script.

import { ApiError } from './api';

/**
 * RFC-7807 ProblemDetail shape. Spring's `ProblemDetail.forStatusAndDetail()`
 * serialises this exact set of fields; additional properties land at the
 * top level (Spring uses `pd.setProperty(...)`), so we keep `properties`
 * permissive.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  // Spring serialises pd.properties at top-level alongside the standard
  // fields — we capture the whole thing as `unknown` and let callers
  // narrow when they need a specific property.
  [k: string]: unknown;
}

/** Type-guard for ApiError so callers don't repeat `instanceof` checks. */
export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/**
 * Extract the ProblemDetail body from an ApiError if it has one.
 * Returns undefined for non-API errors or for errors whose body is a
 * plain string / non-object.
 */
export function getProblemDetail(err: unknown): ProblemDetail | undefined {
  if (!isApiError(err)) return undefined;
  const body = err.body;
  if (body == null || typeof body !== 'object' || Array.isArray(body)) return undefined;
  // Spring ProblemDetail always has at least a `status` field. Use that
  // as a cheap signal — a regular validation-error response also fits
  // (it has `title` + `status`) so we accept it for the same UX.
  if (typeof (body as ProblemDetail).status === 'number'
   || typeof (body as ProblemDetail).title  === 'string'
   || typeof (body as ProblemDetail).detail === 'string') {
    return body as ProblemDetail;
  }
  return undefined;
}

/**
 * Best human-readable string for an error, in this priority:
 *   1. ProblemDetail.title (+ detail joined with " — ") when present
 *   2. err.message (raw Error.message)
 *   3. String(err) as last resort
 *
 * Strips a redundant "status NNN" prefix Spring sometimes adds to the
 * title so the surface reads cleanly.
 */
export function getErrorDisplayMessage(err: unknown): string {
  const pd = getProblemDetail(err);
  if (pd) {
    const parts: string[] = [];
    if (pd.title)  parts.push(String(pd.title).replace(/^\s*\d{3}\s+/, ''));
    if (pd.detail && pd.detail !== pd.title) parts.push(String(pd.detail));
    if (parts.length) return parts.join(' — ');
  }
  if (err instanceof Error && err.message) return err.message;
  if (typeof err === 'string') return err;
  return 'Something went wrong.';
}

/**
 * True when the error indicates "module's DB tables aren't bootstrapped" —
 * the dev-install signal added in backend PR #117. Pages can use this to
 * render a setup-script hint instead of generic "couldn't load".
 *
 * The backend signals this via:
 *   - HTTP status 503
 *   - title "Module not yet bootstrapped"
 *
 * We accept either signal (status alone, or title pattern) to stay
 * resilient against future title tweaks.
 */
export function isBootstrapNeeded(err: unknown): boolean {
  const pd = getProblemDetail(err);
  if (!pd) return false;
  if (pd.title && /not yet bootstrapped/i.test(String(pd.title))) return true;
  // The 503 + bootstrap-hint shape — broad fallback in case title gets
  // localised on the backend later.
  if (pd.status === 503 && pd.detail && /setup_.*_db\.py/i.test(String(pd.detail))) {
    return true;
  }
  return false;
}

/**
 * Best-effort guess at the module name + bootstrap script from a
 * ProblemDetail — used to render a one-click-copy command snippet.
 * Looks for `rootCause` (added by GlobalExceptionHandler) containing
 * a schema-qualified table name like "admin.committees" → "committees".
 * Falls back to `setup_<module>_db.py` placeholder.
 */
export function suggestedBootstrapCommand(err: unknown): string {
  const pd = getProblemDetail(err);
  const root = pd?.rootCause;
  if (typeof root === 'string') {
    // Common patterns:
    //   "Invalid object name 'admin.committee_templates'."  (SQL Server)
    //   "ERROR: relation \"socops.security_event\" does not exist"  (Postgres)
    const m = root.match(/['"]?([a-z_]+)\.[a-z_]+['"]?/i);
    if (m) {
      const schema = m[1].toLowerCase();
      // Schema → setup-script naming convention: setup_<schema>_db.py
      return `python setup_${schema}_db.py`;
    }
  }
  return 'python setup_<module>_db.py';
}
