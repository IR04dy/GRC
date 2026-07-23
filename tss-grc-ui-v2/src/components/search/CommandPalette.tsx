import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, X as XIcon, Clock, CornerDownLeft } from 'lucide-react';
import { searchIndex, getSearchItemById, hydratePolicySearch, type SearchItem, type EntityKind } from '../../lib/searchIndex';
import { listPolicies } from '../../lib/policy';

/**
 * Cmd/Ctrl+K command palette — cross-entity universal search.
 *
 * Searches risks, findings, controls, policies, evidence, authorities,
 * vendors, DPIAs, KRIs, engagements, incidents, changes and module
 * landing pages. Type-prefix filters supported:
 *   "risk: data"       — only risks matching "data"
 *   "auth: nca"        — only authorities matching "nca"
 *   "ev: pam"          — only evidence matching "pam"
 *
 * Pattern: industry-standard GRC tooling Now Platform global search + Linear/Notion's
 * Cmd-K modal idiom. Recent selections persist in localStorage.
 */

const RECENT_KEY = 'cmdk.recent';
const MAX_RECENT = 8;

const KIND_LABEL: Record<EntityKind, string> = {
  PAGE: 'Page', RISK: 'Risk', FINDING: 'Finding', CONTROL: 'Control',
  POLICY: 'Policy', EVIDENCE: 'Evidence', AUTHORITY: 'Authority',
  VENDOR: 'Vendor', DPIA: 'DPIA', KRI: 'KRI', ENGAGEMENT: 'Engagement',
  INCIDENT: 'Incident', CHANGE: 'Change',
};

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [query, setQuery] = useState('');
  const [activeIdx, setActiveIdx] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const [policyHydrated, setPolicyHydrated] = useState(false);

  // CH-9: on first open, pull canonical policies so policy search results link
  // to a real /policies/:id (the mock ids don't exist on the canonical detail
  // page). Best-effort — keeps the mock fallback on failure.
  useEffect(() => {
    if (!open || policyHydrated) return;
    listPolicies({ latestOnly: true })
      .then(docs => { hydratePolicySearch(docs); setPolicyHydrated(true); })
      .catch(() => { /* keep mock fallback */ });
  }, [open, policyHydrated]);

  const results = useMemo(() => searchIndex(query, { limit: 50 }), [query, policyHydrated]);

  // Recent items (when palette opens with empty query, prepend these)
  const recent = useMemo(() => {
    if (query.trim() !== '') return [];
    try {
      const ids = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as string[];
      // Re-resolve to fresh items in case data shifted
      return ids
        .map(getSearchItemById)
        .filter((x): x is SearchItem => !!x)
        .slice(0, MAX_RECENT);
    } catch { return []; }
  }, [query, open]);

  const flat = useMemo(() => {
    if (query.trim() === '' && recent.length > 0) {
      // recent first, then suggested pages, deduped by id
      const seen = new Set(recent.map(r => r.id));
      const tail = results.filter(r => !seen.has(r.id));
      return [...recent, ...tail];
    }
    return results;
  }, [query, recent, results]);

  // Group by kind for headers
  const grouped = useMemo(() => {
    const out = new Map<EntityKind | '__recent', SearchItem[]>();
    if (query.trim() === '' && recent.length > 0) {
      out.set('__recent' as never, recent);
    }
    for (const it of flat) {
      if (recent.includes(it)) continue;
      if (!out.has(it.kind)) out.set(it.kind, []);
      out.get(it.kind)!.push(it);
    }
    return out;
  }, [flat, recent, query]);

  // Reset state when opened
  useEffect(() => {
    if (open) {
      setQuery('');
      setActiveIdx(0);
      // small delay to ensure focus after the modal mounts
      setTimeout(() => inputRef.current?.focus(), 30);
    }
  }, [open]);

  // Reset active index when query changes
  useEffect(() => { setActiveIdx(0); }, [query]);

  // Keyboard handlers
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        setActiveIdx(i => Math.min(flat.length - 1, i + 1));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setActiveIdx(i => Math.max(0, i - 1));
      } else if (e.key === 'Enter') {
        const target = flat[activeIdx];
        if (target) {
          e.preventDefault();
          select(target);
        }
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, flat, activeIdx]); // eslint-disable-line react-hooks/exhaustive-deps

  function select(it: SearchItem) {
    // Persist as recent (mru)
    try {
      const ids = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as string[];
      const next = [it.id, ...ids.filter(x => x !== it.id)].slice(0, MAX_RECENT);
      localStorage.setItem(RECENT_KEY, JSON.stringify(next));
    } catch { /* ignore */ }
    onClose();
    navigate(it.href);
  }

  if (!open) return null;

  return (
    <div onClick={onClose} role="dialog" aria-modal="true" aria-label="Command palette"
      style={{
        position: 'fixed', inset: 0,
        background: 'rgba(15, 28, 30, 0.55)',
        backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
        zIndex: 100,
        paddingTop: '14vh',
      }}>
      <div onClick={(e) => e.stopPropagation()} style={{
        width: 'min(680px, 92vw)',
        maxHeight: '70vh',
        background: 'var(--surface-1)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        boxShadow: '0 30px 80px rgba(0,0,0,0.30)',
        overflow: 'hidden',
        display: 'flex', flexDirection: 'column',
      }}>
        {/* Search input */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', borderBottom: '1px solid var(--border)' }}>
          <Search size={18} color="var(--ink-3)" />
          <input ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search risks, findings, controls, evidence… or type 'risk:' / 'auth:' / 'ev:' for filtered search"
            style={{
              flex: 1,
              border: 'none',
              background: 'transparent',
              fontSize: 15,
              color: 'var(--ink-1)',
              outline: 'none',
              fontFamily: 'inherit',
            }} />
          <span style={{
            fontSize: 10, fontWeight: 600,
            color: 'var(--ink-3)',
            padding: '2px 6px',
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-sm)',
            fontFamily: 'var(--font-mono)',
          }}>ESC</span>
          <button type="button" onClick={onClose} aria-label="Close"
            style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--ink-3)', padding: 4, display: 'flex' }}>
            <XIcon size={16} />
          </button>
        </div>

        {/* Results body */}
        <div style={{ overflowY: 'auto', padding: 6 }}>
          {flat.length === 0
            ? <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
                No matches for <code style={{ background: 'var(--surface-2)', padding: '1px 6px', borderRadius: 4 }}>{query}</code>
              </div>
            : (() => {
                let cursor = 0;
                return Array.from(grouped.entries()).map(([kind, list]) => (
                  <div key={String(kind)}>
                    <div style={{
                      fontSize: 10, fontWeight: 700, color: 'var(--ink-3)',
                      textTransform: 'uppercase', letterSpacing: '0.04em',
                      padding: '8px 12px 4px',
                      display: 'flex', alignItems: 'center', gap: 4,
                    }}>
                      {kind === '__recent'
                        ? <><Clock size={11} /> Recent</>
                        : KIND_LABEL[kind as EntityKind]}
                      <span style={{ marginInlineStart: 'auto', color: 'var(--ink-4)', fontWeight: 400 }}>{list.length}</span>
                    </div>
                    {list.map((it) => {
                      const idx = cursor++;
                      const active = idx === activeIdx;
                      return (
                        <button key={it.id}
                          type="button"
                          onMouseEnter={() => setActiveIdx(idx)}
                          onClick={() => select(it)}
                          style={{
                            width: '100%', textAlign: 'start',
                            display: 'grid',
                            gridTemplateColumns: '28px 1fr auto auto',
                            gap: 10,
                            alignItems: 'center',
                            padding: '8px 12px',
                            background: active ? 'var(--primary-50)' : 'transparent',
                            border: 'none',
                            borderRadius: 'var(--r-sm)',
                            cursor: 'pointer',
                            color: 'inherit',
                            transition: 'background var(--dur-fast) var(--ease-standard)',
                          }}>
                          <span style={{
                            width: 26, height: 26,
                            borderRadius: 'var(--r-sm)',
                            background: 'var(--surface-2)',
                            color: 'var(--primary-700)',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                          }}>
                            <it.Icon size={14} strokeWidth={2} />
                          </span>
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                                          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                              {it.title}
                            </div>
                            {it.subtitle && (
                              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1,
                                            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                {it.subtitle}
                              </div>
                            )}
                          </div>
                          {it.ref && <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{it.ref}</span>}
                          {active && (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 10, color: 'var(--ink-3)' }}>
                              <CornerDownLeft size={11} />
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                ));
              })()}
        </div>

        {/* Footer hint */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '8px 12px',
          borderTop: '1px solid var(--border)',
          background: 'var(--surface-2)',
          fontSize: 11, color: 'var(--ink-3)',
        }}>
          <span><Kbd>↑</Kbd> <Kbd>↓</Kbd> navigate</span>
          <span><Kbd>↵</Kbd> open</span>
          <span><Kbd>esc</Kbd> close</span>
          <span style={{ marginInlineStart: 'auto' }}>Type <code style={{ background: 'var(--surface-3)', padding: '0 4px', borderRadius: 3 }}>kind:</code> to filter</span>
        </div>
      </div>
    </div>
  );
}

function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <span style={{
      display: 'inline-block',
      padding: '0 5px',
      border: '1px solid var(--border)',
      borderRadius: 4,
      background: 'var(--surface-1)',
      fontFamily: 'var(--font-mono)',
      fontSize: 10,
      lineHeight: 1.4,
      color: 'var(--ink-1)',
    }}>{children}</span>
  );
}
