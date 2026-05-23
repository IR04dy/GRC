// Knowledge Base API + types — Sprint 4 §2.9.
//
// Refactored to use the central `api` client so we get for free:
//   - 401 → refresh-token retry (was hard-failing on token expiry)
//   - ProblemDetail.detail extraction (was showing generic "HTTP 500")
//   - Consistent Bearer-header construction via getAccessToken()
//   - ApiError-typed errors for downstream apiError.ts helpers
//
// Before this refactor, every write call would on token expiry throw
// "createArticle failed: HTTP 401" instead of transparently refreshing,
// and every server-side validation failure surfaced as a meaningless
// "HTTP 400" instead of the actual "Title is required" / "Slug already
// taken" message.

import { api, ApiError } from './api';
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

export async function listArticles(): Promise<KbArticle[]> {
  return adapterListAll(KB_ARTICLE_ADAPTER as any, [] as KbArticle[]);
}

export async function getArticle(slug: string): Promise<KbArticle | null> {
  try {
    return await api.get<KbArticle>(`/api/kb/articles/${encodeURIComponent(slug)}`);
  } catch (e) {
    // 404 is a normal "no such article" — return null silently.
    // For any other status (403 forbidden, 500 server error) re-throw so
    // callers can surface the real reason via apiError.ts helpers.
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/**
 * Create a new draft article. Backend route: POST /api/kb/articles
 * Requires PLATFORM_ADMIN role or KB_WRITE authority. Returns the persisted
 * article with its server-generated id, slug, and status=DRAFT.
 */
export async function createArticle(draft: KbArticleDraft): Promise<KbArticle> {
  return api.post<KbArticle>('/api/kb/articles', draft);
}

/** Update an existing article in place. PUT /api/kb/articles/{slug} */
export async function updateArticle(slug: string, draft: KbArticleDraft): Promise<KbArticle> {
  return api.put<KbArticle>(`/api/kb/articles/${encodeURIComponent(slug)}`, draft);
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
  return api.post<KbArticle>(
    `/api/kb/articles/${encodeURIComponent(slug)}/transition`,
    { status: TRANSITION_TARGET[action] },
  );
}

/** Soft-delete an article. DELETE /api/kb/articles/{slug} (admin only). */
export async function deleteArticle(slug: string): Promise<void> {
  await api.delete(`/api/kb/articles/${encodeURIComponent(slug)}`);
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
