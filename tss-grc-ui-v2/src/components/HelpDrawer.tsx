import { useEffect, useMemo, useRef, useState } from 'react';
import { X, BookOpen, ExternalLink, Sparkles, Search, AlertTriangle, Info, Lightbulb } from 'lucide-react';
import { useI18n } from '../lib/i18n';
import {
  helpApi,
  HELP_MODULE_LABEL,
  HELP_SEVERITY_LABEL,
  type HelpTopicResponse,
} from '../lib/help';

/**
 * Help drawer — Sprint 12 Embedded Help.
 *
 * <p>Slides in from the inline-end edge (right in LTR, left in RTL),
 * resolves the topics for the current route, and renders:
 *   - the topic title + summary
 *   - a markdown body
 *   - a highlighted Saudi-regulator anchor + outbound link
 *   - related-wizard cards
 *   - a "more help" link to the Knowledge Base
 *   - a top-row search box that hits the global search endpoint
 */
export function HelpDrawer({
  open, onClose, route,
}: {
  open: boolean;
  onClose: () => void;
  route: string;
}) {
  const { t, lang } = useI18n();
  const [topics, setTopics] = useState<HelpTopicResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchHits, setSearchHits] = useState<HelpTopicResponse[]>([]);
  const trackedRef = useRef<Set<string>>(new Set());

  // ─── Initial lookup on open / route change ──────────────────────
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    helpApi.lookup(route).then(res => {
      if (cancelled) return;
      setTopics(res.topics ?? []);
      setLoading(false);
    }).catch(err => {
      if (cancelled) return;
      setError(err?.message ?? 'Help unavailable');
      setTopics([]);
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [open, route]);

  // Fire-and-forget view tracking — once per topic per drawer
  // session so we don't flood the audit log when the user scrolls.
  useEffect(() => {
    if (!open || topics.length === 0) return;
    for (const t of topics) {
      if (trackedRef.current.has(t.externalId)) continue;
      trackedRef.current.add(t.externalId);
      helpApi.trackView(t.externalId, route).catch(() => { /* ignore */ });
    }
  }, [open, topics, route]);

  // Reset tracked set when the drawer closes so the next open
  // re-tracks (signal that the user re-engaged with the help).
  useEffect(() => {
    if (!open) trackedRef.current.clear();
  }, [open]);

  // ─── Search ─────────────────────────────────────────────────────
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) { setSearchHits([]); return; }
    let cancelled = false;
    setSearching(true);
    const handle = setTimeout(() => {
      helpApi.search(q).then(hits => {
        if (!cancelled) {
          setSearchHits(hits);
          setSearching(false);
        }
      }).catch(() => { if (!cancelled) setSearching(false); });
    }, 200);
    return () => { cancelled = true; clearTimeout(handle); };
  }, [query]);

  // Esc to close
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  const visible = useMemo(
    () => query.trim().length >= 2 ? searchHits : topics,
    [query, searchHits, topics],
  );

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
        zIndex: 1000, display: 'flex',
        // RTL-aware: in LTR drawer comes from the right; in RTL from the left.
        justifyContent: lang === 'ar' ? 'flex-start' : 'flex-end',
      }}
      role="dialog"
      aria-modal="true"
      aria-label={t('helpDrawerTitle', 'Contextual help')}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          width: 480, maxWidth: '100vw', height: '100vh',
          background: 'var(--surface-1)',
          boxShadow: lang === 'ar'
            ? '8px 0 24px rgba(0,0,0,0.15)'
            : '-8px 0 24px rgba(0,0,0,0.15)',
          display: 'flex', flexDirection: 'column',
        }}>

        {/* ── Header ─────────────────────────────────────────── */}
        <div style={{
          padding: 16, borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          background: 'var(--surface-2)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <BookOpen size={18} style={{ color: 'var(--primary)' }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 700 }}>
                {t('helpDrawerTitle', 'Contextual help')}
              </div>
              <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                {route}
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label={t('helpDrawerClose', 'Close')}
            style={{
              border: 'none', background: 'transparent', cursor: 'pointer',
              padding: 6, color: 'var(--ink-2)',
            }}>
            <X size={18} />
          </button>
        </div>

        {/* ── Search ─────────────────────────────────────────── */}
        <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '8px 12px',
            background: 'var(--surface-2)', borderRadius: 6,
            border: '1px solid var(--border)',
          }}>
            <Search size={14} style={{ color: 'var(--ink-3)' }} />
            <input
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={t('helpDrawerSearchPlaceholder', 'Search all help')}
              aria-label={t('helpDrawerSearch', 'Search help')}
              style={{
                flex: 1, border: 'none', background: 'transparent',
                outline: 'none', fontSize: 13, color: 'var(--ink-1)',
              }}
            />
          </div>
          {query.trim().length >= 2 && (
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6 }}>
              {searching
                ? t('helpDrawerSearching', 'Searching…')
                : `${searchHits.length} ${t('helpDrawerSearchResults', 'result(s)')}`}
            </div>
          )}
        </div>

        {/* ── Body ───────────────────────────────────────────── */}
        <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
          {loading && (
            <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
              {t('helpDrawerLoading', 'Loading…')}
            </div>
          )}
          {error && (
            <div style={{
              padding: 12, background: 'var(--danger-50, #fef2f2)',
              border: '1px solid var(--danger, #ef4444)', borderRadius: 6,
              fontSize: 12, color: 'var(--danger-700, #991b1b)',
            }}>
              {error}
            </div>
          )}
          {!loading && !error && visible.length === 0 && (
            <EmptyState query={query} t={t} />
          )}
          {!loading && !error && visible.map(topic => (
            <HelpTopicCard key={topic.id} topic={topic} t={t} lang={lang} />
          ))}
        </div>

        {/* ── Footer ─────────────────────────────────────────── */}
        <div style={{
          padding: 12, borderTop: '1px solid var(--border)',
          background: 'var(--surface-2)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <a
            href="/knowledge-base"
            style={{
              fontSize: 12, color: 'var(--primary)',
              textDecoration: 'none', display: 'inline-flex',
              alignItems: 'center', gap: 6,
            }}>
            <BookOpen size={13} />
            {t('helpDrawerMoreHelp', 'More help')}
          </a>
          <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>
            {t('helpDrawerFooter', 'Press Esc to close')}
          </span>
        </div>
      </div>
    </div>
  );
}

function EmptyState({ query, t }: { query: string; t: (k: string, fb?: string) => string }) {
  return (
    <div style={{
      padding: '32px 16px', textAlign: 'center',
      color: 'var(--ink-3)', fontSize: 12,
    }}>
      <Sparkles size={28} style={{ opacity: 0.4, marginBottom: 8 }} />
      <div style={{ fontWeight: 600, color: 'var(--ink-2)', marginBottom: 4 }}>
        {query.trim().length >= 2
          ? t('helpDrawerNoSearchResults', 'No results for this search')
          : t('helpDrawerNoTopicsForRoute', 'No help for this page yet')}
      </div>
      <div style={{ fontSize: 11 }}>
        {t('helpDrawerNoTopicsHint', 'Open the Knowledge Base for the long-form articles.')}
      </div>
    </div>
  );
}

function HelpTopicCard({
  topic, t, lang,
}: {
  topic: HelpTopicResponse;
  t: (k: string, fb?: string) => string;
  lang: 'en' | 'ar';
}) {
  const title   = lang === 'ar' ? topic.titleAr   : topic.titleEn;
  const summary = lang === 'ar' ? topic.summaryAr : topic.summaryEn;
  const body    = lang === 'ar' ? topic.bodyAr    : topic.bodyEn;
  const moduleLabel = HELP_MODULE_LABEL[topic.module]?.[lang] ?? topic.module;
  const severityLabel = HELP_SEVERITY_LABEL[topic.severity]?.[lang] ?? topic.severity;
  const SeverityIcon =
    topic.severity === 'WARNING' ? AlertTriangle :
    topic.severity === 'TIP'     ? Lightbulb     :
    Info;
  const severityTone =
    topic.severity === 'WARNING' ? 'var(--warning, #f59e0b)' :
    topic.severity === 'TIP'     ? 'var(--success, #10b981)' :
    'var(--primary, #3b82f6)';

  const workflows = (topic.relatedWorkflowCodes ?? '')
    .split(',').map(s => s.trim()).filter(Boolean);

  return (
    <div style={{
      padding: 14, marginBottom: 14,
      background: 'var(--surface-1)',
      border: '1px solid var(--border)',
      borderInlineStart: `3px solid ${severityTone}`,
      borderRadius: 6,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <SeverityIcon size={14} style={{ color: severityTone }} />
        <span style={{
          fontSize: 10, fontWeight: 700, color: severityTone,
          textTransform: 'uppercase', letterSpacing: '0.05em',
        }}>
          {severityLabel}
        </span>
        <span style={{
          fontSize: 10, padding: '1px 6px',
          background: 'var(--surface-2)', borderRadius: 3,
          color: 'var(--ink-2)',
        }}>
          {moduleLabel}
        </span>
      </div>

      <h3 style={{
        fontSize: 15, fontWeight: 700, color: 'var(--ink-1)',
        margin: 0, marginBottom: 6,
      }}>
        {title}
      </h3>
      {summary && (
        <div style={{
          fontSize: 12, color: 'var(--ink-2)',
          marginBottom: 10, lineHeight: 1.5,
        }}>
          {summary}
        </div>
      )}

      <MarkdownLite text={body} />

      {topic.regulatoryAnchor && (
        <RegulatorCallout
          anchor={topic.regulatoryAnchor}
          url={topic.regulatorLinkUrl}
          t={t}
        />
      )}

      {workflows.length > 0 && (
        <div style={{ marginTop: 10 }}>
          <div style={{
            fontSize: 10, fontWeight: 700, textTransform: 'uppercase',
            letterSpacing: '0.05em', color: 'var(--ink-3)', marginBottom: 6,
          }}>
            {t('helpDrawerRelatedWizards', 'Related wizards')}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {workflows.map(w => (
              <span key={w} style={{
                fontSize: 11, padding: '3px 8px',
                background: 'var(--surface-2)', borderRadius: 4,
                color: 'var(--ink-2)', fontFamily: 'var(--font-mono)',
              }}>
                {w}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function RegulatorCallout({
  anchor, url, t,
}: {
  anchor: string; url?: string;
  t: (k: string, fb?: string) => string;
}) {
  return (
    <div style={{
      marginTop: 12, padding: '10px 12px',
      background: 'var(--primary-50, #eff6ff)',
      border: '1px solid var(--primary-200, #bfdbfe)',
      borderRadius: 6,
    }}>
      <div style={{
        fontSize: 10, fontWeight: 700, textTransform: 'uppercase',
        letterSpacing: '0.05em', color: 'var(--primary, #1d4ed8)',
        marginBottom: 4,
      }}>
        {t('helpDrawerRegulatorReference', 'Regulatory reference')}
      </div>
      <div style={{
        fontSize: 12, color: 'var(--ink-1)', fontWeight: 600,
        marginBottom: url ? 6 : 0,
      }}>
        {anchor}
      </div>
      {url && (
        <a
          href={url} target="_blank" rel="noopener noreferrer"
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            fontSize: 11, color: 'var(--primary, #1d4ed8)',
            textDecoration: 'none',
          }}>
          <ExternalLink size={11} />
          {t('helpDrawerOpenRegulator', 'Open on regulator portal')}
        </a>
      )}
    </div>
  );
}

/**
 * Minimal markdown renderer. Supports:
 *   - `## heading`
 *   - `### sub-heading`
 *   - ordered + unordered lists
 *   - fenced code blocks
 *   - inline `code`
 *   - **bold**
 * Deliberately no remote images, no html, no scripts.
 */
function MarkdownLite({ text }: { text: string }) {
  const lines = text.split(/\r?\n/);
  const nodes: React.ReactNode[] = [];
  let inCode = false;
  let codeBuf: string[] = [];
  let listBuf: string[] = [];
  let listType: 'ul' | 'ol' | null = null;

  function flushList() {
    if (listBuf.length > 0) {
      const Tag = listType === 'ol' ? 'ol' : 'ul';
      nodes.push(
        <Tag key={`list-${nodes.length}`} style={{
          margin: '6px 0', paddingInlineStart: 20, color: 'var(--ink-2)',
          fontSize: 12, lineHeight: 1.7,
        }}>
          {listBuf.map((item, i) => (
            <li key={i}>{renderInline(item)}</li>
          ))}
        </Tag>
      );
      listBuf = [];
      listType = null;
    }
  }

  function flushCode() {
    if (codeBuf.length > 0) {
      nodes.push(
        <pre key={`code-${nodes.length}`} style={{
          margin: '6px 0', padding: 10,
          background: 'var(--surface-2)', borderRadius: 4,
          fontSize: 11, fontFamily: 'var(--font-mono, monospace)',
          color: 'var(--ink-1)', overflowX: 'auto',
          whiteSpace: 'pre',
        }}>
          {codeBuf.join('\n')}
        </pre>
      );
      codeBuf = [];
    }
  }

  for (const raw of lines) {
    const line = raw;
    if (line.trim().startsWith('```')) {
      if (inCode) { flushCode(); inCode = false; }
      else        { flushList(); inCode = true; }
      continue;
    }
    if (inCode) { codeBuf.push(line); continue; }

    if (line.startsWith('## ')) {
      flushList();
      nodes.push(
        <h4 key={`h-${nodes.length}`} style={{
          fontSize: 13, fontWeight: 700, color: 'var(--ink-1)',
          margin: '10px 0 4px',
        }}>
          {line.slice(3)}
        </h4>
      );
      continue;
    }
    if (line.startsWith('### ')) {
      flushList();
      nodes.push(
        <h5 key={`h-${nodes.length}`} style={{
          fontSize: 12, fontWeight: 700, color: 'var(--ink-1)',
          margin: '8px 0 2px',
        }}>
          {line.slice(4)}
        </h5>
      );
      continue;
    }
    const olMatch = /^(\d+)\.\s+(.+)$/.exec(line);
    if (olMatch) {
      if (listType !== 'ol') { flushList(); listType = 'ol'; }
      listBuf.push(olMatch[2]);
      continue;
    }
    const ulMatch = /^[-*]\s+(.+)$/.exec(line);
    if (ulMatch) {
      if (listType !== 'ul') { flushList(); listType = 'ul'; }
      listBuf.push(ulMatch[1]);
      continue;
    }
    flushList();
    if (line.trim().length === 0) continue;
    nodes.push(
      <p key={`p-${nodes.length}`} style={{
        fontSize: 12, color: 'var(--ink-2)',
        margin: '4px 0', lineHeight: 1.6,
      }}>
        {renderInline(line)}
      </p>
    );
  }
  flushList();
  if (inCode) flushCode();

  return <div>{nodes}</div>;
}

function renderInline(text: string): React.ReactNode {
  // **bold** + `code`
  const parts: React.ReactNode[] = [];
  const regex = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = regex.exec(text)) !== null) {
    if (m.index > last) parts.push(text.slice(last, m.index));
    const tok = m[0];
    if (tok.startsWith('**')) {
      parts.push(<strong key={parts.length}>{tok.slice(2, -2)}</strong>);
    } else if (tok.startsWith('`')) {
      parts.push(
        <code key={parts.length} style={{
          padding: '0 4px', background: 'var(--surface-2)',
          borderRadius: 3, fontSize: '0.9em',
          fontFamily: 'var(--font-mono, monospace)',
        }}>
          {tok.slice(1, -1)}
        </code>
      );
    }
    last = m.index + tok.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts.length === 0 ? text : parts;
}
