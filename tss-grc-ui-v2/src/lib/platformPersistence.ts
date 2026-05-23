// Platform persistence adapter — Phase F infrastructure round.
//
// One unified place for switching between modes:
//   - 'demo'  — pure localStorage (no auth, works offline; the default)
//   - 'rest'  — calls the Spring Boot REST API at /api/**, with JWT
//   - 'mixed' — read from REST, fall back to localStorage on error
//
// Mode is persisted in localStorage so a tester can switch without rebuild.
// Drives the ModeBadge in the topbar.

import { api } from './api';

export type PersistenceMode = 'demo' | 'rest' | 'mixed';

const KEY = 'grc.persistence.mode.v1';
const EVENT = 'grc.persistence.mode.changed';

export function currentPersistenceMode(): PersistenceMode {
  if (typeof window === 'undefined') return 'demo';
  try {
    const raw = window.localStorage.getItem(KEY) as PersistenceMode | null;
    return raw ?? 'demo';
  } catch { return 'demo'; }
}

export function setPersistenceMode(mode: PersistenceMode): void {
  try { window.localStorage.setItem(KEY, mode); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribePersistenceMode(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}

// ─── Pluggable adapter pattern ──────────────────────────────────────────────

/**
 * Adapter shape that any module can implement to support REST + local.
 * Pattern: each module's lib file exposes load/save/delete that delegate
 * here based on currentPersistenceMode().
 */
export interface PersistenceAdapter<T> {
  /** localStorage key for the demo mode. */
  localKey: string;
  /** REST endpoint base for the rest mode (e.g. "/api/risk-lc/treatment-plans"). */
  restEndpoint: string;
  /** Optional id getter — used to compute /endpoint/:id URLs. */
  idOf: (item: T) => string;
}

/** Read-all implementation. */
export async function adapterListAll<T>(adapter: PersistenceAdapter<T>, fallbackSeed: T[]): Promise<T[]> {
  const mode = currentPersistenceMode();
  if (mode === 'demo') return loadLocal(adapter.localKey, fallbackSeed);
  if (mode === 'rest' || mode === 'mixed') {
    try {
      return await api.get<T[]>(adapter.restEndpoint);
    } catch (e) {
      if (mode === 'mixed') return loadLocal(adapter.localKey, fallbackSeed);
      throw e;
    }
  }
  return fallbackSeed;
}

/** Save implementation. */
export async function adapterSave<T>(adapter: PersistenceAdapter<T>, item: T, allItems: T[]): Promise<T> {
  const mode = currentPersistenceMode();
  if (mode === 'demo' || mode === 'mixed') {
    saveLocal(adapter.localKey, allItems);
  }
  if (mode === 'rest' || mode === 'mixed') {
    try {
      const id = adapter.idOf(item);
      const exists = allItems.some(x => adapter.idOf(x) === id);
      const url = exists ? `${adapter.restEndpoint}/${id}` : adapter.restEndpoint;
      const saved = exists
        ? await api.put<T>(url, item)
        : await api.post<T>(url, item);
      return saved;
    } catch (e) {
      if (mode === 'rest') throw e;
      // mixed: fall through, local already saved
    }
  }
  return item;
}

/** State-machine transition — wraps the POST /<endpoint>/{id}/transition pattern. */
export async function adapterTransition<T>(adapter: PersistenceAdapter<T>, externalId: string, payload: object): Promise<T | null> {
  const mode = currentPersistenceMode();
  if (mode === 'demo') return null; // local mode doesn't go through REST
  try {
    return await api.post<T>(`${adapter.restEndpoint}/${externalId}/transition`, payload);
  } catch (e) {
    if (mode === 'rest') throw e;
    return null;
  }
}

// ─── Fire-and-forget REST mirror ──────────────────────────────────────────
//
// The lifecycle pages already write through synchronous `saveX/transitionX`
// helpers in each module's lib file. Rather than rewriting them as async,
// we call these mirror helpers AFTER the local write — they post to the
// backend best-effort, silently no-op in demo mode, and log a warning on
// failure. This lets `mode = rest` actually push data to Spring without
// breaking the demo path or forcing every page to become async.

/** Mirror a save to the REST backend. POST if new, PUT if existing.
 *  Silent in demo mode; warns on failure in rest/mixed mode. */
export function mirrorSaveToBackend<T>(
  adapter: PersistenceAdapter<T>,
  item: T,
  isNew: boolean,
): void {
  const mode = currentPersistenceMode();
  if (mode === 'demo') return;
  const id = adapter.idOf(item);
  const url = isNew ? adapter.restEndpoint : `${adapter.restEndpoint}/${id}`;
  const method = isNew ? 'POST' : 'PUT';
  const call = isNew ? api.post(url, item) : api.put(url, item);
  // Warn so a tester can see sync issues, but don't surface to the user —
  // the local copy is already written.
  call.catch((e) => console.warn(`[mirrorSaveToBackend] ${method} ${url} failed:`, e));
}

/** Mirror a state-machine transition to the REST backend.
 *  Silent in demo mode; warns on failure in rest/mixed mode. */
export function mirrorTransitionToBackend<T>(
  adapter: PersistenceAdapter<T>,
  externalId: string,
  payload: object,
): void {
  const mode = currentPersistenceMode();
  if (mode === 'demo') return;
  const url = `${adapter.restEndpoint}/${externalId}/transition`;
  api.post(url, payload).catch((e) => {
    console.warn(`[mirrorTransitionToBackend] POST ${url} failed:`, e);
  });
}

/** Mirror a soft-delete to the REST backend. */
export function mirrorDeleteToBackend<T>(
  adapter: PersistenceAdapter<T>,
  externalId: string,
): void {
  const mode = currentPersistenceMode();
  if (mode === 'demo') return;
  const url = `${adapter.restEndpoint}/${externalId}`;
  api.delete(url).catch((e) => {
    console.warn(`[mirrorDeleteToBackend] DELETE ${url} failed:`, e);
  });
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function loadLocal<T>(key: string, fallback: T[]): T[] {
  if (typeof window === 'undefined') return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) as T[] : fallback;
  } catch { return fallback; }
}

function saveLocal<T>(key: string, items: T[]): void {
  try { window.localStorage.setItem(key, JSON.stringify(items)); } catch { /* ignore */ }
}

// Note: a homegrown authHeaders() used to live here that read
// sessionStorage directly. Removed in favour of the central api client,
// which builds the Bearer header via getAccessToken() and handles the
// 401-refresh-retry flow consistently across the platform.

// ─── Concrete adapters for the four lifecycle workspaces ──────────────────
//
// Each adapter pairs a localStorage key (demo mode) with the corresponding
// Spring Boot REST endpoint backed by V15..V19 migrations.
//
//   Risk     → V15 risk_lc.treatment_plans       → /api/risk-lc/treatment-plans
//   Audit    → V16 audit_lc.findings             → /api/audit-lc/findings
//   Privacy  → V17 privacy_lc.dsr_cases          → /api/privacy-lc/dsr-cases
//   BCM      → V18 bcm_lc.invocations            → /api/bcm-lc/invocations
//   Cyber    → V19 cyber_lc.vulnerabilities      → /api/cyber-lc/vulnerabilities
//
// The shape `{ id: string }` is intentional: the adapter only needs to know
// how to find the externalId on a row. Each module's lib file passes its
// concrete row type (TreatmentPlan, Finding, DsrCase, …) — TS structural
// typing makes the assignment safe as long as `.id` is a string.

export const TREATMENT_PLAN_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.risk.treatment.v1',
  restEndpoint: '/api/risk-lc/treatment-plans',
  idOf: (item) => item.id,
};

export const AUDIT_FINDING_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.audit.findings.v1',
  restEndpoint: '/api/audit-lc/findings',
  idOf: (item) => item.id,
};

export const DSR_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.privacy.dsr.v1',
  restEndpoint: '/api/privacy-lc/dsr-cases',
  idOf: (item) => item.id,
};

export const BCM_INVOCATION_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.bcm.invocations.v1',
  restEndpoint: '/api/bcm-lc/invocations',
  idOf: (item) => item.id,
};

export const CYBER_VULN_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.cyber.vulnerabilities.v1',
  restEndpoint: '/api/cyber-lc/vulnerabilities',
  idOf: (item) => item.id,
};

// Sprint 4 §3.2 — additional wired modules.
export const APPROVAL_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.approvals.requests.v1',
  restEndpoint: '/api/approvals/requests',
  idOf: (item) => item.id,
};

export const CAPA_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.capa.tasks.v1',
  restEndpoint: '/api/capa/tasks',
  idOf: (item) => item.id,
};

export const REGCHANGE_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.regchange.changes.v1',
  restEndpoint: '/api/regchange/changes',
  idOf: (item) => item.id,
};

export const KB_ARTICLE_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.kb.articles.v1',
  restEndpoint: '/api/kb/articles',
  idOf: (item) => item.id,
};

// Sprint 4 §3.2 #4-#6 — additional wired modules.
export const AUDIT_ENG_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.audit-eng.engagements.v1',
  restEndpoint: '/api/audit-eng/engagements',
  idOf: (item) => item.id,
};

export const BOARD_PACK_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.board-pack.packs.v1',
  restEndpoint: '/api/board-pack/packs',
  idOf: (item) => item.id,
};

export const NCA_ECC_ADAPTER: PersistenceAdapter<{ id: string }> = {
  localKey: 'grc.nca-ecc.filings.v1',
  restEndpoint: '/api/nca-ecc/filings',
  idOf: (item) => item.id,
};

/** Convenience map: workspace key → adapter. Lets the ModeBadge / debug panel
 *  iterate over all wired adapters without hardcoding names. */
export const LIFECYCLE_ADAPTERS = {
  risk:      TREATMENT_PLAN_ADAPTER,
  audit:     AUDIT_FINDING_ADAPTER,
  privacy:   DSR_ADAPTER,
  bcm:       BCM_INVOCATION_ADAPTER,
  cyber:     CYBER_VULN_ADAPTER,
  approvals: APPROVAL_ADAPTER,
  capa:      CAPA_ADAPTER,
  regchange: REGCHANGE_ADAPTER,
  kb:        KB_ARTICLE_ADAPTER,
  audit_eng: AUDIT_ENG_ADAPTER,
  board_pack: BOARD_PACK_ADAPTER,
  nca_ecc:   NCA_ECC_ADAPTER,
} as const;

export type LifecycleAdapterKey = keyof typeof LIFECYCLE_ADAPTERS;
