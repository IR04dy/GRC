// SavedViews — Sprint 4 §2.5.
//
// Per-user, per-page saved combinations of (filter + sort + visible columns).
// Phase 1 (now): localStorage. Phase 2 (Sprint 5): Spring backend at
// /api/platform/saved-views with V20 migration; the shape stays identical.

import type { FilterGroup } from '../components/ui/filter';

export interface SavedView {
  id: string;                              // e.g. "sv_x9k2..."
  pageKey: string;                         // e.g. "cyber/vulns", "risk/register"
  name: string;
  isDefault?: boolean;
  isShared?: boolean;                      // (org-shared — needs backend; ignored locally)
  filter?: FilterGroup;
  sort?: { id: string; desc: boolean }[];
  visibleColumns?: string[];
  /** URL search-params snapshot — used by pages that drive filters from the URL
   *  (e.g. WorkflowSubmissionsPage). Mutually exclusive with {@code filter}
   *  in practice but both can coexist for hybrid pages. Sprint-5 will migrate
   *  these to {@code filter} once TanStack Table lands. */
  urlQuery?: string;
  createdAt: string;
  updatedAt?: string;
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

export function saveView(view: Omit<SavedView, 'id' | 'createdAt'> & { id?: string }): SavedView {
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
    createdAt: existing?.createdAt ?? new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  next = [...next.filter(v => v.id !== id), saved];
  persist(next);
  return saved;
}

export function deleteView(id: string): void {
  const next = loadSavedViews().filter(v => v.id !== id);
  persist(next);
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
