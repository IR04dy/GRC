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

/**
 * The published article list. `GET /api/kb/articles` — `isAuthenticated()`, so any signed-in
 * user may read it.
 *
 * ⚠ F2-202 fixed a live defect here, and it is the only read in this file that ever had it.
 * This function used to call `adapterListAll(KB_ARTICLE_ADAPTER, [])` from
 * `lib/platformPersistence.ts`. That helper branches on a persistence-mode switch, and the
 * switch was `'demo'` in every build because nothing in the product could set it — so the
 * call resolved to `loadLocal('grc.kb.articles.v1', [])`, read an empty localStorage key,
 * and returned `[]`. Every OTHER call in this file — `getArticle`, `createArticle`,
 * `updateArticle`, `transitionArticle`, `deleteArticle` — went straight to the real server.
 *
 * So the Knowledge Base list was permanently empty while `kb.articles` held 22 rows: an
 * author could create an article, watch it persist, open it by slug, publish it — and never
 * see it in the list. `KbHomePage` carried a comment reading "(empty until articles are
 * published)", which explained the symptom away with a cause that was not the cause. That is
 * the F2-184 shape: the most misleading comment is the one that answers the question the
 * reader was about to ask.
 */
export async function listArticles(): Promise<KbArticle[]> {
  return api.get<KbArticle[]>('/api/kb/articles');
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
