import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { HelpCircle, X } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { getArticle, type KbArticle } from '../../lib/kb';

export interface HelpIconProps {
  /** KB article slug to lazy-load on first hover. */
  slug: string;
  /** Optional inline tooltip-style fallback when KB is empty. */
  fallbackEn?: string;
  fallbackAr?: string;
  /** Icon size — matches form-field font sizing. */
  size?: number;
}

/**
 * Inline `?` help icon — Sprint 4 §2.9. Hover to show summary, click to
 * navigate to the full KB article. Lazy-loads the article on first hover so
 * the network cost is paid only when the user shows interest.
 */
export function HelpIcon({ slug, fallbackEn, fallbackAr, size = 14 }: HelpIconProps) {
  const { lang } = useI18n();
  const [open, setOpen] = useState(false);
  const [article, setArticle] = useState<KbArticle | null | 'loading'>(null);
  const containerRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open || article !== null) return;
    setArticle('loading');
    getArticle(slug).then(a => setArticle(a));
  }, [open, slug, article]);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  const fallback = lang === 'ar' ? fallbackAr : fallbackEn;
  const articleTitle = article && article !== 'loading'
    ? (lang === 'ar' ? article.titleAr : article.titleEn)
    : null;
  const articleSummary = article && article !== 'loading'
    ? (lang === 'ar' ? article.summaryAr : article.summaryEn)
    : null;

  return (
    <span ref={containerRef} className="hi-root" style={{ position: 'relative', display: 'inline-block', marginInlineStart: 4 }}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        title={fallback}
        style={{
          background: 'transparent', border: 'none', cursor: 'help',
          padding: 0, display: 'inline-flex', alignItems: 'center',
          color: 'var(--ink-3)',
        }}
      >
        <HelpCircle size={size} />
      </button>

      {open && (
        <div className="hi-popover" style={{
          position: 'absolute',
          top: 'calc(100% + 6px)',
          insetInlineStart: 0,
          zIndex: 100,
          width: 280,
          padding: 12,
          background: 'var(--surface-1)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md)',
          boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
          fontSize: 12,
          lineHeight: 1.5,
          color: 'var(--ink-1)',
        }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 6 }}>
            <strong style={{ fontSize: 13 }}>
              {article === 'loading' ? '…' : articleTitle ?? fallback ?? slug}
            </strong>
            <button onClick={() => setOpen(false)} style={{ background: 'none', border: 'none', color: 'var(--ink-3)', cursor: 'pointer', padding: 0 }}>
              <X size={12} />
            </button>
          </div>

          {article === 'loading' && (
            <div style={{ marginTop: 8, color: 'var(--ink-3)' }}>
              {lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}
            </div>
          )}

          {article && article !== 'loading' && (
            <>
              {articleSummary && <div style={{ marginTop: 6 }}>{articleSummary}</div>}
              <Link to={`/knowledge-base/${article.slug}`}
                    style={{ display: 'inline-block', marginTop: 8, color: 'var(--primary)', textDecoration: 'none' }}>
                {lang === 'ar' ? 'اقرأ المزيد ←' : 'Read more →'}
              </Link>
            </>
          )}

          {!article && fallback && (
            <div style={{ marginTop: 6, color: 'var(--ink-2)' }}>{fallback}</div>
          )}
        </div>
      )}
    </span>
  );
}
