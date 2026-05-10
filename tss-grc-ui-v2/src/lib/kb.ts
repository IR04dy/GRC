// Knowledge Base API + types — Sprint 4 §2.9.

import { adapterListAll, KB_ARTICLE_ADAPTER } from './platformPersistence';

export type KbStatus     = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type KbVisibility = 'INTERNAL' | 'PUBLIC' | 'RESTRICTED';
/** Backend transition codes accepted by POST /api/kb/articles/{slug}/transition */
export type KbTransition = 'PUBLISH' | 'ARCHIVE' | 'REDRAFT';

export interface KbArticle {
  id: string;
  slug: string;
  categoryCode?: string;
  titleEn: string;
  titleAr: string;
  summaryEn?: string;
  summaryAr?: string;
  bodyEnMd?: string;
  bodyArMd?: string;
  tags?: string;
  status: KbStatus;
  visibility: KbVisibility;
  author?: string;
  createdAt: string;
  updatedAt?: string;
  publishedAt?: string;
  viewCount: number;
}

/** Write payload — server fills in id/slug/status/timestamps. */
export type KbArticleDraft = Omit<
  KbArticle,
  'id' | 'createdAt' | 'updatedAt' | 'publishedAt' | 'viewCount' | 'author'
> & { slug?: string };

function authHeaders(extra?: Record<string, string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return {
    ...(extra ?? {}),
    ...(tok ? { Authorization: `Bearer ${tok}` } : {}),
  };
}

export async function listArticles(): Promise<KbArticle[]> {
  return adapterListAll(KB_ARTICLE_ADAPTER as any, [] as KbArticle[]);
}

export async function getArticle(slug: string): Promise<KbArticle | null> {
  try {
    const res = await fetch(`/api/kb/articles/${encodeURIComponent(slug)}`, {
      headers: authHeaders(),
    });
    if (!res.ok) return null;
    return res.json() as Promise<KbArticle>;
  } catch { return null; }
}

/**
 * Create a new draft article. Backend route: POST /api/kb/articles
 * Requires PLATFORM_ADMIN role or KB_WRITE authority. Returns the persisted
 * article with its server-generated id, slug, and status=DRAFT.
 */
export async function createArticle(draft: KbArticleDraft): Promise<KbArticle> {
  const res = await fetch('/api/kb/articles', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(draft),
  });
  if (!res.ok) throw new Error(`createArticle failed: HTTP ${res.status}`);
  return res.json() as Promise<KbArticle>;
}

/** Update an existing article in place. PUT /api/kb/articles/{slug} */
export async function updateArticle(slug: string, draft: KbArticleDraft): Promise<KbArticle> {
  const res = await fetch(`/api/kb/articles/${encodeURIComponent(slug)}`, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(draft),
  });
  if (!res.ok) throw new Error(`updateArticle failed: HTTP ${res.status}`);
  return res.json() as Promise<KbArticle>;
}

/**
 * Drive the lifecycle: DRAFT → PUBLISHED, PUBLISHED → ARCHIVED, ARCHIVED → DRAFT.
 * Backend route: POST /api/kb/articles/{slug}/transition with body { status }
 * where `status` is the *target* state (validated server-side against the
 * service-layer state machine).
 *
 * UI semantics → server target state:
 *   PUBLISH → PUBLISHED
 *   ARCHIVE → ARCHIVED
 *   REDRAFT → DRAFT
 */
const TRANSITION_TARGET: Record<KbTransition, KbStatus> = {
  PUBLISH: 'PUBLISHED',
  ARCHIVE: 'ARCHIVED',
  REDRAFT: 'DRAFT',
};

export async function transitionArticle(slug: string, action: KbTransition): Promise<KbArticle> {
  const res = await fetch(`/api/kb/articles/${encodeURIComponent(slug)}/transition`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ status: TRANSITION_TARGET[action] }),
  });
  if (!res.ok) throw new Error(`transition failed: HTTP ${res.status}`);
  return res.json() as Promise<KbArticle>;
}

/** Soft-delete an article. DELETE /api/kb/articles/{slug} (admin only). */
export async function deleteArticle(slug: string): Promise<void> {
  const res = await fetch(`/api/kb/articles/${encodeURIComponent(slug)}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!res.ok) throw new Error(`deleteArticle failed: HTTP ${res.status}`);
}

/** Filter results client-side by query string (search-as-you-type). */
export function filterArticles(articles: KbArticle[], q: string, lang: 'en' | 'ar'): KbArticle[] {
  if (!q.trim()) return articles;
  const ql = q.toLowerCase();
  return articles.filter(a => {
    const title = (lang === 'ar' ? a.titleAr : a.titleEn).toLowerCase();
    const summary = ((lang === 'ar' ? a.summaryAr : a.summaryEn) ?? '').toLowerCase();
    return title.includes(ql) || summary.includes(ql) || (a.tags ?? '').toLowerCase().includes(ql);
  });
}
