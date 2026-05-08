// MFA enrolment state — Sprint 6 production hardening.
//
// Stores per-user MFA enrolment status in the persistence adapter so it
// survives demo→prod migration. In production, the backend mirror
// (forthcoming auth service) becomes the source of truth and this lib
// becomes a read-through cache.

const KEY = 'grc.mfa.enrolment.v1';
const EVENT = 'grc.mfa.enrolment.changed';

export interface MfaEnrolment {
  /** User identifier (username / email). */
  userRef: string;
  /** Base32-encoded TOTP secret. Stored client-side ONLY when MFA is being
   *  set up; in production the server holds the secret + only sends the
   *  user a `setup_token` to prove enrolment was completed. */
  secret?: string;
  /** Timestamp of successful enrolment (verified first code). */
  enrolledAt?: string;
  /** Recovery codes — single-use; consumed on use. */
  recoveryCodes: string[];
  /** Recovery codes already consumed (for audit). */
  consumedRecoveryCodes: string[];
  /** Last successful authentication. */
  lastUsedAt?: string;
  /** When the user was prompted to re-enrol (e.g. lost device). */
  lastResetAt?: string;
}

function loadAll(): Record<string, MfaEnrolment> {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return typeof parsed === 'object' && parsed !== null ? parsed : {};
  } catch { return {}; }
}

function saveAll(map: Record<string, MfaEnrolment>): void {
  try { window.localStorage.setItem(KEY, JSON.stringify(map)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function getEnrolment(userRef: string): MfaEnrolment | undefined {
  return loadAll()[userRef];
}

export function isEnrolled(userRef: string): boolean {
  const e = getEnrolment(userRef);
  return !!(e && e.enrolledAt);
}

export function setEnrolment(userRef: string, enrolment: MfaEnrolment): void {
  const all = loadAll();
  all[userRef] = enrolment;
  saveAll(all);
}

export function clearEnrolment(userRef: string): void {
  const all = loadAll();
  delete all[userRef];
  saveAll(all);
}

export function consumeRecoveryCode(userRef: string, code: string): boolean {
  const all = loadAll();
  const e = all[userRef];
  if (!e) return false;
  const idx = e.recoveryCodes.indexOf(code);
  if (idx < 0) return false;
  e.recoveryCodes.splice(idx, 1);
  e.consumedRecoveryCodes.push(code);
  e.lastUsedAt = new Date().toISOString();
  saveAll(all);
  return true;
}

export function recordLogin(userRef: string): void {
  const all = loadAll();
  if (!all[userRef]) return;
  all[userRef].lastUsedAt = new Date().toISOString();
  saveAll(all);
}

export function subscribe(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

export const MFA_KEY = KEY;
