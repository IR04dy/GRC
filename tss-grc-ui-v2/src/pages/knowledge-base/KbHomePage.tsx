import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Link } from 'react-router-dom';
import { Search, BookOpen, FileText, ArrowRight, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { hasAnyRole } from '../../lib/auth';
import { listArticles, filterArticles, type KbArticle } from '../../lib/kb';

/**
 * Knowledge Base home — search + browse — Sprint 4 §2.9.
 *
 * Reads `GET /api/kb/articles` unconditionally. There is no mode branch and no localStorage
 * fallback.
 *
 * ⚠ F2-202. The two sentences that used to be here read: "Hits /api/kb/articles when in REST
 * mode; falls back to localStorage seed (empty until articles are published)." Both halves
 * were wrong in a way that concealed a live defect.
 *
 * There was no reachable REST mode, so the first branch never ran. And the list was not empty
 * because nothing had been published — measured 2026-08-13, `kb.articles` holds 22 rows of
 * which 21 are PUBLISHED, and `GET /api/kb/articles` returns all 21. It was empty because
 * F2-202-EMPTY-KEY: `listArticles()` resolved through a stuck persistence-mode switch to an
 * empty localStorage key. A reader who wondered why the Knowledge Base had no articles found
 * that parenthesis and stopped.
 */
export function KbHomePage() {
  const { lang } = useI18n();
  const canWrite = hasAnyRole('PLATFORM_ADMIN', 'KB_WRITE');
  const [articles, setArticles] = useState<KbArticle[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    listArticles().then(rows => {
      if (cancelled) return;
      setArticles(rows);
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, []);

  const filtered = useMemo(() => filterArticles(articles, query, lang), [articles, query, lang]);

  const categories = useMemo(() => {
    const set = new Set(articles.map(a => a.categoryCode).filter(Boolean) as string[]);
    return Array.from(set).sort();
  }, [articles]);

  return (
    <Shell title={lang === 'ar' ? 'قاعدة المعرفة' : 'Knowledge Base'} sub={lang === 'ar' ? 'بحث وتصفح المقالات' : 'Search & browse articles'}>
      <PageHeader
        title={lang === 'ar' ? 'قاعدة المعرفة' : 'Knowledge Base'}
        sub={lang === 'ar' ? 'إجابات وأدلة وتفسيرات' : 'Guides, FAQs, and explainers'}
        actions={canWrite ? (
          <Link to="/knowledge-base/new" style={{ textDecoration: 'none' }}>
            <Button variant="primary" Icon={Plus}>
              {lang === 'ar' ? 'مقال جديد' : 'New article'}
            </Button>
          </Link>
        ) : undefined}
      />

      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0' }}>
          <Search size={18} color="var(--ink-3)" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={lang === 'ar' ? 'ابحث عن مقال…' : 'Search articles…'}
            style={{
              flex: 1, padding: '8px 0', border: 'none', outline: 'none',
              fontSize: 16, background: 'transparent', color: 'var(--ink-1)',
            }}
            autoFocus
          />
          <Badge tone="neutral">{filtered.length}</Badge>
        </div>
      </Card>

      {categories.length > 0 && (
        <Card title={lang === 'ar' ? 'التصنيفات' : 'Categories'}>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {categories.map(c => (
              <Badge key={c} tone="info">{c}</Badge>
            ))}
          </div>
        </Card>
      )}

      <Card title={lang === 'ar' ? 'المقالات' : 'Articles'}>
        {loading && (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        )}
        {!loading && filtered.length === 0 && (
          <EmptyState
            Icon={BookOpen}
            title={lang === 'ar' ? 'لا توجد مقالات منشورة' : 'No published articles yet'}
            sub={lang === 'ar'
              ? 'سيظهر المحتوى عندما يبدأ المسؤولون في النشر.'
              : 'Content will appear once admins start publishing.'}
          />
        )}
        {!loading && filtered.map(a => (
          <Link key={a.id} to={`/knowledge-base/${a.slug}`}
                style={{ display: 'flex', gap: 12, padding: '12px 0', borderBottom: '1px solid var(--border)', textDecoration: 'none', color: 'inherit' }}>
            <FileText size={20} color="var(--primary)" style={{ flexShrink: 0, marginTop: 2 }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, color: 'var(--ink-1)', marginBottom: 4 }}>
                {lang === 'ar' ? a.titleAr : a.titleEn}
              </div>
              {a.summaryEn && (
                <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                  {lang === 'ar' ? a.summaryAr : a.summaryEn}
                </div>
              )}
              <div style={{ display: 'flex', gap: 8, marginTop: 6, alignItems: 'center' }}>
                {a.categoryCode && <Badge tone="neutral">{a.categoryCode}</Badge>}
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {a.viewCount} {lang === 'ar' ? 'مشاهدة' : 'views'}
                </span>
              </div>
            </div>
            <ArrowRight size={16} color="var(--ink-3)" style={{ marginTop: 4 }} />
          </Link>
        ))}
      </Card>
    </Shell>
  );
}
