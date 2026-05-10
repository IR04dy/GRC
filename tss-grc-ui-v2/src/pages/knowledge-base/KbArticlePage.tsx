import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ChevronLeft, BookOpen, Edit2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { hasAnyRole } from '../../lib/auth';
import { getArticle, type KbArticle } from '../../lib/kb';

/**
 * KB article reader — Sprint 4 §2.9.
 *
 * Renders bilingual markdown body. For now uses a minimal markdown→HTML
 * converter (paragraphs + headings + links + code spans + lists) — full
 * markdown support (tables, fenced code, images) is a follow-up.
 */
export function KbArticlePage() {
  const { slug = '' } = useParams<{ slug: string }>();
  const { lang } = useI18n();
  const canWrite = hasAnyRole('PLATFORM_ADMIN', 'KB_WRITE');
  const [article, setArticle] = useState<KbArticle | null | 'loading' | 'not-found'>('loading');

  useEffect(() => {
    getArticle(slug).then(a => setArticle(a ?? 'not-found'));
  }, [slug]);

  if (article === 'loading') {
    return (
      <Shell title={lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'} sub="">
        <div style={{ padding: 48, textAlign: 'center' }}>…</div>
      </Shell>
    );
  }

  if (article === 'not-found' || article === null) {
    return (
      <Shell title={lang === 'ar' ? 'غير موجود' : 'Not found'} sub="">
        <Card>
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>
            <BookOpen size={48} style={{ margin: '0 auto 16px' }} />
            <p>{lang === 'ar' ? 'المقال غير موجود' : 'Article not found'}</p>
            <Link to="/knowledge-base" style={{ color: 'var(--primary)', marginTop: 12, display: 'inline-block' }}>
              {lang === 'ar' ? '← رجوع لقاعدة المعرفة' : '← Back to Knowledge Base'}
            </Link>
          </div>
        </Card>
      </Shell>
    );
  }

  const title = lang === 'ar' ? article.titleAr : article.titleEn;
  const body = lang === 'ar' ? article.bodyArMd : article.bodyEnMd;

  return (
    <Shell title={title} sub={article.categoryCode}>
      <PageHeader
        title={title}
        sub={article.summaryEn}
        actions={canWrite ? (
          <Link to={`/knowledge-base/${article.slug}/edit`} style={{ textDecoration: 'none' }}>
            <Button variant="secondary" Icon={Edit2}>
              {lang === 'ar' ? 'تحرير' : 'Edit'}
            </Button>
          </Link>
        ) : undefined}
      />

      <div style={{ marginBottom: 12 }}>
        <Link to="/knowledge-base" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--ink-2)', fontSize: 13, textDecoration: 'none' }}>
          <ChevronLeft size={14} />
          {lang === 'ar' ? 'كل المقالات' : 'All articles'}
        </Link>
      </div>

      <Card>
        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          {article.categoryCode && <Badge tone="info">{article.categoryCode}</Badge>}
          <Badge tone="neutral">{article.viewCount} {lang === 'ar' ? 'مشاهدة' : 'views'}</Badge>
        </div>
        <article
          className="kb-body"
          style={{ fontSize: 15, lineHeight: 1.7, color: 'var(--ink-1)' }}
          dangerouslySetInnerHTML={{ __html: renderMarkdown(body ?? '') }}
        />
      </Card>
    </Shell>
  );
}

/**
 * Minimal Markdown → HTML. Handles headings, bold/italic/code, links, lists,
 * paragraphs. NOT a full Markdown spec — intentionally tiny to avoid pulling
 * in `marked` (~30KB). Upgrade to `marked` or `markdown-it` if KB content
 * needs tables / fenced code blocks.
 *
 * Outputs are HTML-escaped first, so dangerouslySetInnerHTML is safe against
 * XSS in the body. (Title/summary are NOT user-content paths.)
 */
function renderMarkdown(md: string): string {
  // 1. Escape HTML
  let html = md
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // 2. Headings
  html = html
    .replace(/^### (.*$)/gm, '<h3>$1</h3>')
    .replace(/^## (.*$)/gm, '<h2>$1</h2>')
    .replace(/^# (.*$)/gm, '<h1>$1</h1>');

  // 3. Inline: bold, italic, code, links
  html = html
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*(?!\s)(.+?)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');

  // 4. Lists (simple — paragraph-grouped runs of "- " lines)
  html = html.replace(/(?:^- (.+)\n?)+/gm, (match) => {
    const items = match.trim().split('\n').map(l => l.replace(/^- /, '')).map(l => `<li>${l}</li>`).join('');
    return `<ul>${items}</ul>`;
  });

  // 5. Paragraphs (separate by blank lines, skip raw block elements)
  html = html.split(/\n\n+/).map(block => {
    const trimmed = block.trim();
    if (!trimmed) return '';
    if (/^<(h\d|ul|ol|p|pre|blockquote)/.test(trimmed)) return trimmed;
    return `<p>${trimmed.replace(/\n/g, '<br />')}</p>`;
  }).join('\n');

  return html;
}
