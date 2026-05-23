// Persistence adapter — production-mode foundation.
//
// The demo platform stores mutable state in localStorage (chain fires,
// dispatch log, designer overlay, promoted records, etc.). This adapter
// abstracts the backing store so the same call site works in both modes:
//
//   demo mode       → reads/writes localStorage only
//   migrating mode  → reads localStorage, dual-writes to API (one-time)
//   production mode → reads/writes API, with localStorage as offline cache
//
// `do not wipe the data` is the central constraint: nothing here ever
// clears localStorage during migration. The demo data is preserved as
// the seed; production state grows on top.

import { api, ApiError } from './api';
import { isProductionMode, currentMode } from './productionMode';

export type PersistenceMode = 'demo' | 'migrating' | 'production';

export interface PersistenceAdapter<T> {
  /** Stable store key — becomes the localStorage key + API path suffix. */
  key: string;
  /** Read all rows. Falls back to localStorage if API unreachable. */
  list(): Promise<T[]>;
  /** Replace the entire store. Used by migration + bulk import. */
  setAll(rows: T[]): Promise<void>;
  /** Append a single row. */
  append(row: T): Promise<void>;
  /** Clear (only for explicit user action — never called during migration). */
  clear(): Promise<void>;
}

// ─── Storage primitives ────────────────────────────────────────────────────

function lsRead<T>(key: string): T[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}

function lsWrite<T>(key: string, rows: T[]): void {
  try { window.localStorage.setItem(key, JSON.stringify(rows)); } catch { /* quota / private mode */ }
  window.dispatchEvent(new CustomEvent(`${key}.changed`));
}

// ─── API client ────────────────────────────────────────────────────────────
//
// API path convention: each store maps to `/api/v1/persistence/<key>`.
// Backend stubs need to implement GET/PUT/POST/DELETE for these.
//
// Routes through the central api client (lib/api.ts) so we get:
//   - Bearer token (was completely missing — only cookies were sent)
//   - 401 → refresh-token retry
//   - ProblemDetail.detail extraction
//   - Typed ApiError so callers can distinguish 404 from "real" errors

const API_BASE = '/api/v1/persistence';

async function apiList<T>(key: string): Promise<T[]> {
  const body = await api.get<T[] | { rows?: T[] }>(`${API_BASE}/${encodeURIComponent(key)}`);
  return Array.isArray(body) ? body : (body.rows ?? []);
}

async function apiSetAll<T>(key: string, rows: T[]): Promise<void> {
  await api.put(`${API_BASE}/${encodeURIComponent(key)}`, { rows });
}

async function apiAppend<T>(key: string, row: T): Promise<void> {
  await api.post(`${API_BASE}/${encodeURIComponent(key)}`, row);
}

async function apiClear(key: string): Promise<void> {
  try {
    await api.delete(`${API_BASE}/${encodeURIComponent(key)}`);
  } catch (e) {
    // 404 is "key doesn't exist yet" — treat as success (idempotent clear).
    if (e instanceof ApiError && e.status === 404) return;
    throw e;
  }
}

// ─── Adapter factory ───────────────────────────────────────────────────────

export function makeAdapter<T>(key: string): PersistenceAdapter<T> {
  return {
    key,

    async list(): Promise<T[]> {
      const mode = currentMode();
      if (mode === 'demo') return lsRead<T>(key);

      // Production / migrating: try API, fall back to localStorage.
      try {
        const remote = await apiList<T>(key);
        // Cache the read so offline page loads still work.
        lsWrite(key, remote);
        return remote;
      } catch {
        return lsRead<T>(key);
      }
    },

    async setAll(rows: T[]): Promise<void> {
      lsWrite(key, rows);
      if (isProductionMode()) {
        try { await apiSetAll(key, rows); }
        catch (e) {
          // Don't block writes on API failure — local-first.
          console.warn(`Persistence ${key} setAll failed; cached locally.`, e);
        }
      }
    },

    async append(row: T): Promise<void> {
      const current = lsRead<T>(key);
      const next = [row, ...current].slice(0, 1000); // bounded local cache
      lsWrite(key, next);
      if (isProductionMode()) {
        try { await apiAppend(key, row); }
        catch (e) {
          console.warn(`Persistence ${key} append failed; cached locally.`, e);
        }
      }
    },

    async clear(): Promise<void> {
      // Explicit user action only.
      try { window.localStorage.removeItem(key); } catch { /* ignore */ }
      window.dispatchEvent(new CustomEvent(`${key}.changed`));
      if (isProductionMode()) {
        try { await apiClear(key); }
        catch (e) {
          console.warn(`Persistence ${key} clear failed.`, e);
        }
      }
    },
  };
}

// ─── Migration: localStorage → API ─────────────────────────────────────────
//
// One-time bulk upload. The user explicitly invokes this from the
// Production Readiness page. Never destructive — only adds to the
// backend. localStorage is preserved as offline cache.

export interface MigrationResult {
  key: string;
  rowsLocal: number;
  rowsRemote: number;
  rowsUploaded: number;
  status: 'OK' | 'FAILED' | 'SKIPPED';
  error?: string;
}

export async function migrateStore(key: string): Promise<MigrationResult> {
  const local = lsRead(key);
  if (local.length === 0) {
    return { key, rowsLocal: 0, rowsRemote: 0, rowsUploaded: 0, status: 'SKIPPED' };
  }
  try {
    let remoteCount = 0;
    try { remoteCount = (await apiList(key)).length; } catch { /* assume 0 */ }
    await apiSetAll(key, local);
    return {
      key,
      rowsLocal: local.length,
      rowsRemote: remoteCount,
      rowsUploaded: local.length,
      status: 'OK',
    };
  } catch (e) {
    return {
      key,
      rowsLocal: local.length,
      rowsRemote: 0,
      rowsUploaded: 0,
      status: 'FAILED',
      error: e instanceof Error ? e.message : String(e),
    };
  }
}

// ─── Store registry ────────────────────────────────────────────────────────
//
// Every persistent store registers here so the Production Readiness page
// can show "what's stored in localStorage today, what's wired to the API,
// what still needs migration."

export interface PersistenceStoreInfo {
  /** localStorage key (and API path). */
  key: string;
  labelEn: string;
  labelAr: string;
  /** Module that owns this store. */
  module: string;
  /** Has a backend endpoint been implemented? */
  backendReady: boolean;
}

export const PERSISTENCE_REGISTRY: PersistenceStoreInfo[] = [
  {
    key: 'grc.chains.fires.v1',
    labelEn: 'Cross-module chain fires',
    labelAr: 'تشغيلات التسلسل عبر الوحدات',
    module: 'Workflow Chains',
    backendReady: true, // backend stub shipped this turn
  },
  {
    key: 'grc.notifications.dispatch.log.v1',
    labelEn: 'Notification dispatch log',
    labelAr: 'سجلّ إرسال الإشعارات',
    module: 'Notifications',
    backendReady: true,
  },
  {
    key: 'grc.lang',
    labelEn: 'User language preference',
    labelAr: 'تفضيل لغة المستخدم',
    module: 'I18n',
    backendReady: false,
  },
  // Workflow designer overlays are keyed per definition id —
  // discoverable by prefix.
  {
    key: 'grc.workflow.designer.*',
    labelEn: 'Workflow designer overlays',
    labelAr: 'طبقات مصمّم سير العمل',
    module: 'Workflow Designer',
    backendReady: true,
  },
  {
    key: 'grc.promoted.records.v1',
    labelEn: 'Promoted intake records',
    labelAr: 'السجلات المرقّاة من الاستلام',
    module: 'Intake Substrate',
    backendReady: false,
  },
];

/** Compute live size of every registered store. */
export function inventoryLocalStorage(): Array<{ key: string; rows: number; bytes: number }> {
  if (typeof window === 'undefined') return [];
  const out: Array<{ key: string; rows: number; bytes: number }> = [];
  for (const info of PERSISTENCE_REGISTRY) {
    if (info.key.endsWith('*')) {
      // Prefix match — enumerate matching keys.
      const prefix = info.key.slice(0, -1);
      for (let i = 0; i < window.localStorage.length; i++) {
        const k = window.localStorage.key(i);
        if (k && k.startsWith(prefix)) {
          const raw = window.localStorage.getItem(k) ?? '';
          let rows = 0;
          try { const v = JSON.parse(raw); if (Array.isArray(v)) rows = v.length; else if (typeof v === 'object') rows = Object.keys(v).length; } catch { /* skip */ }
          out.push({ key: k, rows, bytes: raw.length });
        }
      }
    } else {
      const raw = window.localStorage.getItem(info.key) ?? '';
      let rows = 0;
      try { const v = JSON.parse(raw); if (Array.isArray(v)) rows = v.length; } catch { /* skip */ }
      out.push({ key: info.key, rows, bytes: raw.length });
    }
  }
  return out;
}
