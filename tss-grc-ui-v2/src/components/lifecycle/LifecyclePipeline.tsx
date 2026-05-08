import { useMemo, useState } from 'react';
import { ArrowRight, Clock, AlertTriangle, Search, type LucideIcon } from 'lucide-react';
import { Badge, type BadgeTone } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';

/**
 * LifecyclePipeline — reusable kanban-style pipeline visualisation.
 *
 * Used by every lifecycle workspace (Risk, Audit, Privacy, BCM, Cyber) to
 * render a horizontal flow of stages with item cards in each column.
 * Cards can be clicked to advance to the next stage in one tap.
 */

export interface PipelineStage {
  /** Status code matching the cards' status field. */
  code: string;
  /** Bilingual stage label. */
  labelEn: string;
  labelAr: string;
  /** Stage tone — drives header colour. */
  tone: BadgeTone;
  /** Optional icon. */
  Icon?: LucideIcon;
}

export interface PipelineCard {
  id: string;
  /** Status code matching one of the stages. */
  status: string;
  /** Bilingual title. */
  titleEn: string;
  titleAr: string;
  /** Optional bilingual subtitle (owner / due date / asset). */
  subtitleEn?: string;
  subtitleAr?: string;
  /** Reference code shown small. */
  refCode?: string;
  /** Badges on the card body. */
  badges?: { label: string; tone: BadgeTone }[];
  /** Whether the card is overdue / urgent (red border + alert icon). */
  urgent?: boolean;
  /** Due-date string shown as countdown. */
  dueLabel?: string;
}

export interface LifecyclePipelineProps {
  /** Stages in left-to-right order. RTL flips automatically. */
  stages: PipelineStage[];
  /** Items to render. */
  cards: PipelineCard[];
  /** Optional intro narrative. */
  introEn?: string;
  introAr?: string;
  /**
   * Called when the user clicks the advance button on a card.
   * Defaults to advancing to the next stage in `stages`.
   */
  onAdvance?: (cardId: string, fromStatus: string, toStatus: string | null) => void;
  /** Called when the user clicks the card body. */
  onCardClick?: (cardId: string) => void;
  /** If false, hide the advance button. */
  showAdvance?: boolean;
  /** Optional empty-state hint. */
  emptyEn?: string;
  emptyAr?: string;
  /**
   * Bulk action handler — when provided, cards become multi-selectable
   * (checkbox on hover, click to toggle) and a floating action bar appears.
   */
  onBulkAdvance?: (cardIds: string[]) => void;
  /** Optional history opener — shown as a small clock icon per card. */
  onCardHistory?: (cardId: string) => void;
}

export function LifecyclePipeline({
  stages, cards, introEn, introAr,
  onAdvance, onCardClick,
  showAdvance = true,
  emptyEn, emptyAr,
  onBulkAdvance, onCardHistory,
}: LifecyclePipelineProps) {
  const { t, lang } = useI18n();
  const [query, setQuery] = useState('');
  const [showOverdueOnly, setShowOverdueOnly] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  function toggleSelect(cardId: string) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(cardId)) next.delete(cardId);
      else next.add(cardId);
      return next;
    });
  }
  function clearSelection() {
    setSelectedIds(new Set());
  }
  function bulkAdvance() {
    if (!onBulkAdvance || selectedIds.size === 0) return;
    onBulkAdvance(Array.from(selectedIds));
    clearSelection();
  }

  // Filter cards by query + urgent flag.
  const filteredCards = useMemo(() => {
    return cards.filter(c => {
      if (showOverdueOnly && !c.urgent) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase().trim();
      return [c.id, c.refCode, c.titleEn, c.titleAr, c.subtitleEn, c.subtitleAr]
        .filter(Boolean)
        .some(s => s!.toLowerCase().includes(q));
    });
  }, [cards, query, showOverdueOnly]);

  const overdueCount = cards.filter(c => c.urgent).length;

  function nextStage(currentCode: string): PipelineStage | null {
    const idx = stages.findIndex(s => s.code === currentCode);
    if (idx < 0 || idx >= stages.length - 1) return null;
    return stages[idx + 1];
  }

  function cardsForStage(code: string): PipelineCard[] {
    return filteredCards.filter(c => c.status === code);
  }

  return (
    <div>
      {(introEn || introAr) && (
        <div style={{
          fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.6,
          marginBottom: 16, padding: 12,
          background: 'var(--surface-2)', borderRadius: 'var(--r-md)',
          borderInlineStart: '3px solid var(--primary)',
        }}>
          {lang === 'ar' ? introAr : introEn}
        </div>
      )}

      {/* Filter bar */}
      {cards.length > 3 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', flex: 1, minWidth: 200 }}>
            <Search size={12} style={{ position: 'absolute', insetInlineStart: 8, top: 9, color: 'var(--ink-3)' }} />
            <input value={query} onChange={e => setQuery(e.target.value)}
              placeholder={t('lpcSearchPlaceholder')}
              style={{
                width: '100%', padding: '6px 10px 6px 26px', fontSize: 11,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)',
              }} />
          </div>
          {overdueCount > 0 && (
            <button onClick={() => setShowOverdueOnly(s => !s)}
              style={{
                padding: '6px 10px', fontSize: 11, fontWeight: 600,
                border: `1px solid ${showOverdueOnly ? 'var(--danger-fg)' : 'var(--border)'}`,
                background: showOverdueOnly ? 'rgba(220,38,38,0.06)' : 'var(--surface-1)',
                color: showOverdueOnly ? 'var(--danger-fg)' : 'var(--ink-2)',
                borderRadius: 'var(--r-sm)', cursor: 'pointer',
                display: 'inline-flex', alignItems: 'center', gap: 4,
              }}>
              <AlertTriangle size={11} />
              {showOverdueOnly ? t('lpcOverdueOn') : t('lpcOverdueOff')} ({overdueCount})
            </button>
          )}
          {(query || showOverdueOnly) && (
            <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {filteredCards.length} / {cards.length}
            </span>
          )}
        </div>
      )}

      <div style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${stages.length}, minmax(220px, 1fr))`,
        gap: 8,
        overflowX: 'auto',
      }}>
        {stages.map(stage => {
          const items = cardsForStage(stage.code);
          return (
            <div key={stage.code} style={{
              display: 'flex', flexDirection: 'column', gap: 6,
              minWidth: 220,
              padding: 8,
              background: 'var(--surface-2)',
              borderRadius: 'var(--r-md)',
              border: '1px solid var(--border)',
            }}>
              {/* Column header */}
              <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '4px 6px', marginBottom: 4,
                borderBottom: `2px solid var(--${toneVar(stage.tone)})`,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  {stage.Icon && <stage.Icon size={14} color={`var(--${toneVar(stage.tone)})`} />}
                  <span style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    {lang === 'ar' ? stage.labelAr : stage.labelEn}
                  </span>
                </div>
                <Badge tone={stage.tone}>{items.length}</Badge>
              </div>

              {/* Cards */}
              {items.length === 0 ? (
                <div style={{
                  padding: 16, textAlign: 'center',
                  color: 'var(--ink-3)', fontSize: 11, fontStyle: 'italic',
                }}>
                  —
                </div>
              ) : items.map(card => {
                const next = nextStage(card.status);
                const selected = selectedIds.has(card.id);
                return (
                  <div key={card.id} style={{
                    padding: 10,
                    background: selected ? 'var(--primary-50)' : 'var(--surface-1)',
                    borderRadius: 'var(--r-sm)',
                    border: selected ? '2px solid var(--primary)' : (card.urgent ? '1px solid var(--danger-fg)' : '1px solid var(--border)'),
                    cursor: onCardClick ? 'pointer' : 'default',
                    display: 'flex', flexDirection: 'column', gap: 6,
                    boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
                    transition: 'transform 0.1s, box-shadow 0.1s',
                  }}
                    onClick={(e) => {
                      // Shift/Ctrl/Cmd-click toggles selection (when bulk mode enabled).
                      if (onBulkAdvance && (e.shiftKey || e.ctrlKey || e.metaKey)) {
                        e.preventDefault();
                        toggleSelect(card.id);
                        return;
                      }
                      onCardClick?.(card.id);
                    }}
                    onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-1px)'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)'; }}
                    onMouseLeave={e => { e.currentTarget.style.transform = ''; e.currentTarget.style.boxShadow = '0 1px 2px rgba(0,0,0,0.04)'; }}>
                    {/* Header line */}
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 6 }}>
                      {onBulkAdvance && (
                        <input type="checkbox" checked={selected}
                          onChange={(e) => { e.stopPropagation(); toggleSelect(card.id); }}
                          onClick={(e) => e.stopPropagation()}
                          style={{ marginTop: 2, cursor: 'pointer' }} />
                      )}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        {card.refCode && (
                          <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{card.refCode}</div>
                        )}
                        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', lineHeight: 1.35 }}>
                          {lang === 'ar' ? card.titleAr : card.titleEn}
                        </div>
                      </div>
                      {card.urgent && <AlertTriangle size={12} color="var(--danger-fg)" style={{ flexShrink: 0 }} />}
                    </div>

                    {/* Subtitle */}
                    {(card.subtitleEn || card.subtitleAr) && (
                      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                        {lang === 'ar' ? (card.subtitleAr ?? card.subtitleEn) : (card.subtitleEn ?? card.subtitleAr)}
                      </div>
                    )}

                    {/* Badges */}
                    {card.badges && card.badges.length > 0 && (
                      <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                        {card.badges.map((b, i) => (
                          <Badge key={i} tone={b.tone}>{b.label}</Badge>
                        ))}
                      </div>
                    )}

                    {/* Footer with due date + history + advance button */}
                    {(card.dueLabel || (showAdvance && next && onAdvance) || onCardHistory) && (
                      <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        gap: 6, marginTop: 2, paddingTop: 6,
                        borderTop: '1px dashed var(--border)',
                      }}>
                        {card.dueLabel ? (
                          <div style={{ fontSize: 9, color: card.urgent ? 'var(--danger-fg)' : 'var(--ink-3)', display: 'flex', alignItems: 'center', gap: 3 }} className="mono">
                            <Clock size={10} />
                            {card.dueLabel}
                          </div>
                        ) : <div />}
                        <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                          {onCardHistory && (
                            <button onClick={(e) => { e.stopPropagation(); onCardHistory(card.id); }}
                              title="History"
                              style={{
                                padding: '3px 5px', border: '1px solid var(--border)',
                                background: 'transparent', color: 'var(--ink-3)',
                                borderRadius: 4, cursor: 'pointer',
                              }}>
                              <Clock size={10} />
                            </button>
                          )}
                          {showAdvance && next && onAdvance && (
                            <button onClick={(e) => { e.stopPropagation(); onAdvance(card.id, card.status, next.code); }}
                              style={{
                                padding: '3px 7px',
                                fontSize: 10, fontWeight: 600,
                                border: `1px solid var(--${toneVar(next.tone)})`,
                                background: 'transparent',
                                color: `var(--${toneVar(next.tone)})`,
                                borderRadius: 4,
                                cursor: 'pointer',
                                display: 'inline-flex', alignItems: 'center', gap: 3,
                              }}
                              title={lang === 'ar' ? next.labelAr : next.labelEn}>
                              {lang === 'ar' ? next.labelAr : next.labelEn}
                              <ArrowRight size={10} style={{ transform: lang === 'ar' ? 'rotate(180deg)' : undefined }} />
                            </button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>

      {cards.length === 0 && (emptyEn || emptyAr) && (
        <div style={{
          marginTop: 16, padding: 24, textAlign: 'center',
          color: 'var(--ink-3)', fontSize: 12,
          background: 'var(--surface-2)', borderRadius: 'var(--r-md)',
        }}>
          {lang === 'ar' ? (emptyAr ?? emptyEn) : (emptyEn ?? emptyAr)}
        </div>
      )}

      {/* Floating bulk action bar */}
      {onBulkAdvance && selectedIds.size > 0 && (
        <div style={{
          position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)',
          padding: '10px 16px',
          background: 'var(--ink-1)', color: 'white',
          borderRadius: 'var(--r-md)',
          boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
          display: 'flex', alignItems: 'center', gap: 12,
          zIndex: 999,
        }}>
          <span style={{ fontSize: 12, fontWeight: 600 }}>
            {selectedIds.size} {t('bulkSelected')}
          </span>
          <button onClick={bulkAdvance}
            style={{
              padding: '6px 12px', fontSize: 11, fontWeight: 600,
              border: '1px solid var(--primary)', background: 'var(--primary)', color: 'white',
              borderRadius: 4, cursor: 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 4,
            }}>
            <ArrowRight size={11} />
            {t('bulkAdvance')}
          </button>
          <button onClick={() => {
            // CSV export of selected.
            const selected = filteredCards.filter(c => selectedIds.has(c.id));
            const csv = ['id,status,title'].concat(selected.map(c => `${c.id},${c.status},"${c.titleEn.replace(/"/g, '""')}"`)).join('\n');
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url; a.download = `pipeline-export-${Date.now()}.csv`;
            document.body.appendChild(a); a.click(); document.body.removeChild(a);
            URL.revokeObjectURL(url);
          }}
            style={{
              padding: '6px 12px', fontSize: 11, fontWeight: 600,
              border: '1px solid rgba(255,255,255,0.3)', background: 'transparent', color: 'white',
              borderRadius: 4, cursor: 'pointer',
            }}>
            {t('bulkExport')}
          </button>
          <button onClick={clearSelection}
            style={{
              padding: '6px 8px', fontSize: 11,
              border: 'none', background: 'transparent', color: 'rgba(255,255,255,0.7)',
              borderRadius: 4, cursor: 'pointer',
            }}>
            {t('bulkClear')}
          </button>
        </div>
      )}
    </div>
  );
}

/** Map BadgeTone to a CSS variable name we use elsewhere. */
function toneVar(tone: BadgeTone): string {
  switch (tone) {
    case 'success': return 'success';
    case 'warning': return 'warning';
    case 'danger':  return 'danger';
    case 'info':    return 'info-fg';
    case 'primary': return 'primary';
    case 'accent':  return 'accent-700';
    default:         return 'ink-3';
  }
}

/**
 * Helper: map a record to a PipelineCard with optional defaults.
 * Useful when the source record uses different field names.
 */
export function toCard<T extends { id: string }>(
  record: T,
  options: Omit<PipelineCard, 'id'>
): PipelineCard {
  return { id: record.id, ...options };
}

/**
 * Helper to build the "due" label as a Hijri-aware countdown.
 * Returns null if no date or already past in a way we don't want to highlight.
 */
export function dueLabel(iso: string | undefined, t: (k: string) => string): { label: string; urgent: boolean } | null {
  if (!iso) return null;
  const due = new Date(iso).getTime();
  const days = Math.round((due - Date.now()) / 86_400_000);
  const urgent = days < 0;
  const label = days < 0
    ? `${Math.abs(days)}${t('lpcDaysLate')}`
    : days === 0
      ? t('lpcDueToday')
      : `${days}${t('lpcDaysLeft')}`;
  return { label, urgent };
}
