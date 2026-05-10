// SSO / SAML / Nafath — Sprint 6 production hardening (final item).
//
// Two flavours of single sign-on:
//
//   1. Nafath (citizen-facing) — Saudi National Single Sign-On. Citizen
//      enters their national ID + Iqama, picks the displayed random
//      number in the Nafath mobile app to approve the login. Used for
//      public-facing portals (DSR, Speak-up, Trust Center, Vendor).
//
//   2. SAML 2.0 (enterprise) — corporate IdP integration (Microsoft Entra
//      ID, Okta, Ping). For staff users; SP-initiated SAML with metadata
//      exchange.
//
// Production talks to:
//   • Nafath: https://api.nafath.sa/api/v1/mfa/request and /callback
//   • Enterprise IdPs via SAML 2.0 / SP-initiated AuthnRequest
//
// In demo mode we simulate the Nafath round-trip with a 10-second
// approval timer + a deterministic random number, and the SAML config
// is form-only (no real IdP roundtrip).

import { generateRecoveryCodes } from './totp'; // re-use random hex helper

// ─── Provider config ───────────────────────────────────────────────────────

export type SsoProviderKind = 'NAFATH' | 'SAML' | 'OIDC';

export interface SsoProviderConfig {
  kind: SsoProviderKind;
  /** Display name for the login button. */
  displayNameEn: string;
  displayNameAr: string;
  configured: boolean;
  /** Nafath app id (assigned when an entity registers with SDAIA Nafath). */
  nafathAppId?: string;
  /** SAML IdP metadata URL. */
  samlMetadataUrl?: string;
  /** SAML entity id (issuer) we expect from the IdP. */
  samlIssuerEntityId?: string;
  /** OIDC well-known URL for OIDC providers. */
  oidcIssuerUrl?: string;
  /** Last successful login through this provider. */
  lastLoginAt?: string;
  /** Logo URL or icon ref for the login button. */
  iconRef?: string;
}

const CONFIG_KEY = 'grc.sso.config.v1';
const CONFIG_EVENT = 'grc.sso.config.changed';

const DEFAULT_CONFIG: SsoProviderConfig[] = [
  {
    kind: 'NAFATH',
    displayNameEn: 'Sign in with Nafath',
    displayNameAr: 'دخول عبر نفاذ',
    configured: false,
  },
  {
    kind: 'SAML',
    displayNameEn: 'Sign in with corporate SSO',
    displayNameAr: 'دخول بحساب الشركة',
    configured: false,
  },
];

export function loadConfig(): SsoProviderConfig[] {
  if (typeof window === 'undefined') return DEFAULT_CONFIG;
  try {
    const raw = window.localStorage.getItem(CONFIG_KEY);
    if (!raw) return DEFAULT_CONFIG;
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : DEFAULT_CONFIG;
  } catch { return DEFAULT_CONFIG; }
}

export function saveConfig(rows: SsoProviderConfig[]): void {
  try { window.localStorage.setItem(CONFIG_KEY, JSON.stringify(rows)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(CONFIG_EVENT));
}

export function configureProvider(kind: SsoProviderKind, patch: Partial<SsoProviderConfig>): void {
  const all = loadConfig();
  const next = all.map(p => p.kind === kind ? { ...p, ...patch, configured: true } : p);
  saveConfig(next);
}

export function disableProvider(kind: SsoProviderKind): void {
  const all = loadConfig();
  const next = all.map(p => p.kind === kind ? { ...p, configured: false } : p);
  saveConfig(next);
}

export function subscribeConfig(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(CONFIG_EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === CONFIG_KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(CONFIG_EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── Nafath transaction state machine ──────────────────────────────────────

export type NafathStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

export interface NafathTransaction {
  id: string;
  /** National ID / Iqama. */
  nationalId: string;
  /** Random 2-digit number the user picks in the app to approve. */
  randomNumber: number;
  status: NafathStatus;
  /** When the transaction was initiated. */
  startedAt: string;
  /** When the transaction was decided (approved / rejected / expired). */
  decidedAt?: string;
  /** Auto-expire window in seconds. */
  expiresInSeconds: number;
}

const TX_KEY = 'grc.nafath.tx.v1';
const TX_EVENT = 'grc.nafath.tx.changed';

function loadTransactions(): NafathTransaction[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(TX_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}

function saveTransactions(rows: NafathTransaction[]): void {
  try { window.localStorage.setItem(TX_KEY, JSON.stringify(rows.slice(0, 50))); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(TX_EVENT));
}

/** Initiate a Nafath transaction. Production POSTs to Nafath API; demo
 *  generates a 2-digit random number and records the pending request. */
export function initiateNafath(nationalId: string): NafathTransaction {
  // 2-digit number that's user-friendly to spot in the app.
  const randomNumber = Math.floor(Math.random() * 90) + 10;
  const tx: NafathTransaction = {
    id: `nafath-${Date.now()}-${generateRecoveryCodes(1)[0].replace('-', '').slice(0, 8)}`,
    nationalId,
    randomNumber,
    status: 'PENDING',
    startedAt: new Date().toISOString(),
    expiresInSeconds: 60,
  };
  const all = loadTransactions();
  saveTransactions([tx, ...all]);
  return tx;
}

/** Poll a Nafath transaction. Returns the latest state. Auto-expires on
 *  the timer. Demo-mode "approval" can be triggered by `simulateApprove`. */
export function getNafathTransaction(id: string): NafathTransaction | undefined {
  const tx = loadTransactions().find(t => t.id === id);
  if (!tx) return undefined;
  // Auto-expire if past window.
  if (tx.status === 'PENDING') {
    const elapsed = (Date.now() - new Date(tx.startedAt).getTime()) / 1000;
    if (elapsed > tx.expiresInSeconds) {
      const next = { ...tx, status: 'EXPIRED' as const, decidedAt: new Date().toISOString() };
      const all = loadTransactions().map(t => t.id === id ? next : t);
      saveTransactions(all);
      return next;
    }
  }
  return tx;
}

/** Demo-mode helper — simulate the user approving in the Nafath app. */
export function simulateNafathApprove(id: string): NafathTransaction | undefined {
  const all = loadTransactions();
  const target = all.find(t => t.id === id);
  if (!target || target.status !== 'PENDING') return target;
  const next: NafathTransaction = { ...target, status: 'APPROVED', decidedAt: new Date().toISOString() };
  saveTransactions(all.map(t => t.id === id ? next : t));
  return next;
}

/** Demo-mode helper — simulate the user rejecting. */
export function simulateNafathReject(id: string): NafathTransaction | undefined {
  const all = loadTransactions();
  const target = all.find(t => t.id === id);
  if (!target || target.status !== 'PENDING') return target;
  const next: NafathTransaction = { ...target, status: 'REJECTED', decidedAt: new Date().toISOString() };
  saveTransactions(all.map(t => t.id === id ? next : t));
  return next;
}

export function listNafathTransactions(): NafathTransaction[] {
  return loadTransactions();
}

export function subscribeTransactions(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(TX_EVENT, handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === TX_KEY) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(TX_EVENT, handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── Aggregation ───────────────────────────────────────────────────────────

export interface SsoStats {
  totalProviders: number;
  configuredProviders: number;
  /** Whether at least one provider is configured — drives prod-readiness. */
  ssoReady: boolean;
  /** Whether Nafath specifically is wired (citizen-portal enabler). */
  nafathReady: boolean;
  recentTransactions: number;
}

export function aggregateSso(): SsoStats {
  const config = loadConfig();
  const totalProviders = config.length;
  const configuredProviders = config.filter(p => p.configured).length;
  const nafathReady = !!config.find(p => p.kind === 'NAFATH' && p.configured);
  return {
    totalProviders,
    configuredProviders,
    ssoReady: configuredProviders > 0,
    nafathReady,
    recentTransactions: loadTransactions().length,
  };
}

// ─── Display ───────────────────────────────────────────────────────────────

export const STATUS_TONE: Record<NafathStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  PENDING:  'info',
  APPROVED: 'success',
  REJECTED: 'danger',
  EXPIRED:  'warning',
};

export const STATUS_LABEL: Record<NafathStatus, { en: string; ar: string }> = {
  PENDING:  { en: 'Awaiting approval', ar: 'بانتظار الموافقة' },
  APPROVED: { en: 'Approved',          ar: 'تمّت الموافقة' },
  REJECTED: { en: 'Rejected',          ar: 'تمّ الرفض' },
  EXPIRED:  { en: 'Expired',           ar: 'انتهت الصلاحية' },
};
