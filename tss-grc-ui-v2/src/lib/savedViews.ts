// SavedViews — Sprint 4 §2.5.
//
// Per-user, per-page saved combinations of (filter + sort + visible columns).
// Phase 1: localStorage. Phase 2 (now — V160): Spring backend at
// /api/platform/saved-views. The local shape stays identical so callers
// don't have to change; the backend syncs in the background through
// {@link syncSavedViewsFromBackend} (invoked once per session on login).
//
// Reads stay synchronous against the localStorage cache so component
// renders aren't async; writes update localStorage immediately and
// fire a best-effort backend write that updates the row's id on
// success. If the backend is unreachable (kiosk / demo / offline /
// not yet signed in), the local copy is the source of truth.

import type { FilterGroup } from '../components/ui/filter';
import { api, ApiError } from './api';
import { isAuthenticated } from './auth';

export interface SavedView {
  id: string;                              // e.g. "sv_x9k2..." or "be_<backendId>"
  pageKey: string;                         // e.g. "cyber/vulns", "risk/register"
  name: string;
  isDefault?: boolean;
  isShared?: boolean;                      // (org-shared — Phase 3)
  filter?: FilterGroup;
  sort?: { id: string; desc: boolean }[];
  visibleColumns?: string[];
  /** URL search-params snapshot — used by pages that drive filters from the URL
   *  (e.g. WorkflowSubmissionsPage, RiskRegisterPage). Mutually exclusive with
   *  {@code filter} in practice but both can coexist for hybrid pages.
   *  Sprint-5 will migrate these to {@code filter} once TanStack Table lands. */
  urlQuery?: string;
  createdAt: string;
  updatedAt?: string;
  /** Backend persistence id (numeric). Present once the row has been
   *  synced to /api/platform/saved-views; absent for local-only rows. */
  backendId?: number;
}

/** Wire-shape returned by the Spring controller. */
interface SavedViewWireDto {
  id: number;
  pageKey: string;
  name: string;
  isDefault?: boolean;
  isShared?: boolean;
  filterJson?: string | null;
  sortJson?: string | null;
  visibleColumnsJson?: string | null;
  urlQuery?: string | null;
  createdAt: string;
  updatedAt: string;
}

function fromWire(w: SavedViewWireDto): SavedView {
  return {
    id: `be_${w.id}`,
    backendId: w.id,
    pageKey: w.pageKey,
    name: w.name,
    isDefault: w.isDefault ?? false,
    isShared: w.isShared ?? false,
    filter: w.filterJson ? safeParse<FilterGroup>(w.filterJson) : undefined,
    sort: w.sortJson ? safeParse<{ id: string; desc: boolean }[]>(w.sortJson) : undefined,
    visibleColumns: w.visibleColumnsJson ? safeParse<string[]>(w.visibleColumnsJson) : undefined,
    urlQuery: w.urlQuery ?? undefined,
    createdAt: w.createdAt,
    updatedAt: w.updatedAt,
  };
}

function toWire(v: SavedView): Omit<SavedViewWireDto, 'id' | 'createdAt' | 'updatedAt'> & { id?: number } {
  return {
    id: v.backendId,
    pageKey: v.pageKey,
    name: v.name,
    isDefault: v.isDefault,
    isShared: v.isShared,
    filterJson: v.filter ? JSON.stringify(v.filter) : null,
    sortJson: v.sort ? JSON.stringify(v.sort) : null,
    visibleColumnsJson: v.visibleColumns ? JSON.stringify(v.visibleColumns) : null,
    urlQuery: v.urlQuery ?? null,
  };
}

function safeParse<T>(s: string): T | undefined {
  try { return JSON.parse(s) as T; } catch { return undefined; }
}

const KEY = 'grc.saved-views.v1';
const EVT = 'grc.saved-views.changed';

export function loadSavedViews(): SavedView[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? JSON.parse(raw) as SavedView[] : [];
  } catch { return []; }
}

export function viewsForPage(pageKey: string): SavedView[] {
  return loadSavedViews().filter(v => v.pageKey === pageKey);
}

export function defaultViewForPage(pageKey: string): SavedView | undefined {
  return viewsForPage(pageKey).find(v => v.isDefault);
}

export function saveView(view: Omit<SavedView, 'id' | 'createdAt'> & { id?: string; backendId?: number }): SavedView {
  const all = loadSavedViews();
  const id = view.id ?? `sv_${Math.random().toString(36).slice(2, 10)}`;
  const existing = all.find(v => v.id === id);

  // If this view is set as default, unset any other default for the same page
  let next = all;
  if (view.isDefault) {
    next = next.map(v => v.pageKey === view.pageKey && v.id !== id ? { ...v, isDefault: false } : v);
  }

  const saved: SavedView = {
    id,
    pageKey: view.pageKey,
    name: view.name,
    isDefault: view.isDefault,
    isShared: view.isShared,
    filter: view.filter,
    sort: view.sort,
    visibleColumns: view.visibleColumns,
    urlQuery: view.urlQuery,
    backendId: view.backendId ?? existing?.backendId,
    createdAt: existing?.createdAt ?? new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  next = [...next.filter(v => v.id !== id), saved];
  persist(next);

  // Best-effort: write through to the backend if the user is signed in.
  // If the call fails (offline, kiosk, demo, 5xx) the local copy keeps
  // working; we'll retry on next save.
  if (isAuthenticated()) {
    void syncWriteToBackend(saved);
  }
  return saved;
}

export function deleteView(id: string): void {
  const all = loadSavedViews();
  const target = all.find(v => v.id === id);
  const next = all.filter(v => v.id !== id);
  persist(next);
  if (isAuthenticated() && target?.backendId != null) {
    void api.delete<void>(`/api/platform/saved-views/${target.backendId}`).catch(() => { /* offline ok */ });
  }
}

// ── Backend sync ──────────────────────────────────────────────────────

/** Persist a single saved view to the backend, updating its
 *  {@code backendId} in localStorage once the server responds. Silently
 *  swallows errors so the UI never blocks on backend availability. */
async function syncWriteToBackend(v: SavedView): Promise<void> {
  try {
    const payload = toWire(v);
    let dto: SavedViewWireDto;
    if (v.backendId != null) {
      dto = await api.put<SavedViewWireDto>(`/api/platform/saved-views/${v.backendId}`, payload);
    } else {
      dto = await api.post<SavedViewWireDto>('/api/platform/saved-views', payload);
    }
    // Update local cache with the backendId so next save hits PUT.
    const all = loadSavedViews();
    const idx = all.findIndex(x => x.id === v.id);
    if (idx >= 0) {
      all[idx] = { ...all[idx], backendId: dto.id, updatedAt: dto.updatedAt };
      persist(all);
    }
  } catch (e) {
    // ApiError 401 → user signed out mid-write; 5xx → backend hiccup.
    // Either way, the local copy is fine — we'll re-attempt on next save.
    if (!(e instanceof ApiError)) {
      // eslint-disable-next-line no-console
      console.warn('[savedViews] backend sync failed (will use local copy)', e);
    }
  }
}

/** One-shot pull of all the current user's saved views from the backend
 *  for the given page. Merges server rows into localStorage by
 *  {@code backendId}; preserves any local-only rows. Call once after
 *  login (e.g. from a top-level layout effect). */
export async function syncSavedViewsFromBackend(pageKey: string): Promise<void> {
  if (!isAuthenticated()) return;
  try {
    const rows = await api.get<SavedViewWireDto[]>(`/api/platform/saved-views?pageKey=${encodeURIComponent(pageKey)}`);
    const remote = rows.map(fromWire);
    const local = loadSavedViews();
    // Merge: remote rows for this page replace any same-backendId local rows.
    const remoteIds = new Set(remote.map(r => r.backendId));
    const survivors = local.filter(l => l.pageKey !== pageKey || l.backendId == null || !remoteIds.has(l.backendId));
    const localOnly = survivors.filter(l => l.pageKey !== pageKey);
    const localForPage = survivors.filter(l => l.pageKey === pageKey);
    persist([...localOnly, ...localForPage, ...remote]);
  } catch (e) {
    if (!(e instanceof ApiError)) {
      // eslint-disable-next-line no-console
      console.warn('[savedViews] backend pull failed (using local cache)', e);
    }
  }
}

export function subscribeSavedViews(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVT, handler);
  return () => window.removeEventListener(EVT, handler);
}

function persist(views: SavedView[]): void {
  try { window.localStorage.setItem(KEY, JSON.stringify(views)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVT));
}
