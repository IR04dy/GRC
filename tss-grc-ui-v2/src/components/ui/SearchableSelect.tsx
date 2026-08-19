import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, Search, Plus, Check } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';
import { FieldError } from './Input';

export interface SelectOption {
  value: string;
  label: string;
  /** Optional one-line subtitle (e.g. "INC-0042 · 12 Mar 2026"). */
  sub?: string;
}

interface SearchableSelectProps {
  id?: string;
  label?: string;
  required?: boolean;
  value: string;
  onChange: (next: string) => void;
  options: SelectOption[];
  placeholder?: string;
  /**
   * When the typed text doesn't match any option, render an inline
   * "+ Create <typed>" row. The handler should create the entity and
   * return its new id (string) so the dropdown can select it. Return
   * null to abort (validation failed, user cancelled, etc.).
   */
  onCreate?: (typedLabel: string) => Promise<string | null> | string | null;
  /** Label for the create row. Defaults to "Create '{text}'". */
  createLabel?: (typedLabel: string) => string;
  /** Helper text shown beneath the input when the option list is empty. */
  emptyHint?: string;
  disabled?: boolean;
  /** Inline validation message — sets aria-invalid + renders a role="alert". */
  error?: string;
}

/**
 * Searchable select with optional "create new" affordance.
 *
 * Designed to replace the bare `<select>` in cases where:
 *   • the option list is long enough that scanning is slow (50+ items)
 *   • the user often needs an entity that doesn't yet exist (e.g. linking
 *     a lessons-learned to an incident before that incident is created)
 *
 * Falls back to a normal click-to-toggle behaviour when typeahead isn't
 * useful (short lists). Always renders the .input class so contrast +
 * focus styles match the rest of the form system.
 *
 * Keyboard:
 *   • ArrowDown / ArrowUp    cycle highlighted option
 *   • Enter                  pick the highlighted option (or create new)
 *   • Escape                 close popover, keep current value
 *   • Type                   filter options + reveal create row if no match
 */
export function SearchableSelect({
  id, label, required, value, onChange, options,
  placeholder, onCreate, createLabel, emptyHint, disabled, error,
}: SearchableSelectProps) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlight, setHighlight] = useState(0);
  const [creating, setCreating] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  // F6 — collision flip: open upward (and clamp height) when the trigger sits
  // near the bottom of a scrolling modal, so the popover never clips off-screen.
  const [placement, setPlacement] = useState<{ up: boolean; maxH: number }>({ up: false, maxH: 320 });
  useLayoutEffect(() => {
    if (!open || !triggerRef.current) return;
    const rect = triggerRef.current.getBoundingClientRect();
    const margin = 12;
    const spaceBelow = window.innerHeight - rect.bottom - margin;
    const spaceAbove = rect.top - margin;
    const up = spaceBelow < 220 && spaceAbove > spaceBelow;
    setPlacement({ up, maxH: Math.max(160, Math.min(320, up ? spaceAbove : spaceBelow)) });
  }, [open]);

  const selected = options.find(o => o.value === value);

  // Filter options on query (case-insensitive substring).
  const filtered = useMemo(() => {
    if (!query.trim()) return options;
    const q = query.trim().toLowerCase();
    return options.filter(o =>
      o.label.toLowerCase().includes(q) ||
      (o.sub ?? '').toLowerCase().includes(q)
    );
  }, [options, query]);

  // The create row is shown when:
  //   1. onCreate is wired, AND
  //   2. the user typed something, AND
  //   3. that typed text doesn't exactly match an existing label.
  const trimmed = query.trim();
  const exactMatch = !!filtered.find(o => o.label.toLowerCase() === trimmed.toLowerCase());
  const showCreate = !!onCreate && trimmed.length > 0 && !exactMatch;
  const itemCount = filtered.length + (showCreate ? 1 : 0);

  // Reset highlight when filter changes so the first row is always active.
  useEffect(() => { setHighlight(0); }, [query, open]);

  // Close on outside click.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!popoverRef.current?.contains(e.target as Node)) setOpen(false);
    };
    window.addEventListener('mousedown', onDown);
    return () => window.removeEventListener('mousedown', onDown);
  }, [open]);

  function commit(opt: SelectOption) {
    onChange(opt.value);
    setOpen(false);
    setQuery('');
  }

  async function commitCreate() {
    if (!onCreate || creating || !trimmed) return;
    setCreating(true);
    try {
      const id = await onCreate(trimmed);
      if (id) {
        onChange(id);
        setOpen(false);
        setQuery('');
      }
    } finally { setCreating(false); }
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setHighlight(h => (h + 1) % Math.max(1, itemCount)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlight(h => (h - 1 + Math.max(1, itemCount)) % Math.max(1, itemCount)); }
    else if (e.key === 'Enter') {
      e.preventDefault();
      if (highlight < filtered.length) commit(filtered[highlight]);
      else if (showCreate) void commitCreate();
    }
    else if (e.key === 'Escape') { setOpen(false); }
  }

  // The displayed text in the closed state.
  const displayText = selected
    ? selected.label
    : (placeholder ?? (isAr ? 'اختر…' : 'Select…'));
  const errId = error ? `${id}-err` : undefined;

  return (
    <label htmlFor={id} style={{ display: 'block', position: 'relative' }}>
      {label && <span className="input-label">{label}</span>}

      {/* Trigger — looks like a regular .input field for visual consistency */}
      <button
        ref={triggerRef}
        type="button"
        id={id}
        disabled={disabled}
        onClick={() => { if (!disabled) { setOpen(o => !o); setTimeout(() => inputRef.current?.focus(), 0); } }}
        className={cn('input', error && 'is-error')}
        style={{
          textAlign: isAr ? 'right' : 'left',
          cursor: disabled ? 'not-allowed' : 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          gap: 8,
        }}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={errId}
      >
        <span style={{
          color: selected ? 'var(--ink-1)' : 'var(--ink-3)',
          fontWeight: selected ? 600 : 500,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>
          {displayText}
        </span>
        <ChevronDown size={14} style={{
          color: 'var(--ink-3)',
          transition: 'transform var(--dur-fast)',
          transform: open ? 'rotate(180deg)' : '',
          flexShrink: 0,
        }} />
      </button>

      {/* Hint shown when the option pool is empty AND we don't have create. */}
      {options.length === 0 && !onCreate && emptyHint && (
        <span className="input-hint">{emptyHint}</span>
      )}

      <FieldError id={errId} error={error} />

      {/* Popover */}
      {open && (
        <div
          ref={popoverRef}
          style={{
            position: 'absolute',
            insetInlineStart: 0, insetInlineEnd: 0,
            ...(placement.up ? { bottom: 'calc(100% + 4px)' } : { top: 'calc(100% + 4px)' }),
            zIndex: 1100,
            background: 'var(--surface)',
            border: '1px solid var(--border-strong)',
            borderRadius: 'var(--r-md)',
            boxShadow: 'var(--shadow-3)',
            maxHeight: placement.maxH,
            display: 'flex', flexDirection: 'column',
            overflow: 'hidden',
          }}>
          {/* Search input row */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '8px 10px',
            borderBottom: '1px solid var(--border)',
            background: 'var(--surface-2)',
          }}>
            <Search size={14} style={{ color: 'var(--ink-3)', flexShrink: 0 }} />
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={e => setQuery(e.currentTarget.value)}
              onKeyDown={onKeyDown}
              /*
               * F2-246 — the placeholder may only promise what THIS instance offers.
               *
               * It read "Search or create new…" / "ابحث أو أنشئ جديداً…" unconditionally,
               * while the create row 130 lines above is correctly gated on `!!onCreate`. So
               * the affordance was conditional and only the promise was not: measured, 17 of
               * the 19 `select-searchable` fields in the app wire no `onCreate`, so almost
               * every picker in the platform invited the operator to create a record it
               * cannot create. F2-184's shape — a construct that answers the question a
               * reader was about to ask — at the level of the text a user actually reads.
               *
               * ⚠ Found in the BROWSER after every gate in this slice was green, which is now
               * the eighth consecutive slice where that is where a text-level claim surfaced:
               * no code-scoped gate can see a string that is only wrong in relation to a
               * sibling prop. Checked in BOTH languages (house rule 7) — the Arabic half
               * carried the same promise.
               */
              placeholder={
                onCreate
                  ? (isAr ? 'ابحث أو أنشئ جديداً…' : 'Search or create new…')
                  : (isAr ? 'ابحث…' : 'Search…')
              }
              style={{
                flex: 1, border: 'none', outline: 'none', background: 'transparent',
                font: '500 13px var(--font-sans)', color: 'var(--ink-1)',
                padding: 0,
              }}
              aria-label={isAr ? 'بحث' : 'Search'}
              autoComplete="off"
              spellCheck={false}
            />
          </div>

          {/* Option list */}
          <div role="listbox" style={{ overflowY: 'auto', flex: 1 }}>
            {filtered.length === 0 && !showCreate && (
              <div className="empty-inline">{isAr ? 'لا توجد نتائج' : 'No matches'}</div>
            )}
            {filtered.map((opt, i) => (
              <button
                key={opt.value}
                type="button"
                role="option"
                aria-selected={opt.value === value}
                onMouseEnter={() => setHighlight(i)}
                onClick={() => commit(opt)}
                className={cn('ss-item', i === highlight && 'is-highlight', opt.value === value && 'is-selected')}
              >
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span className="ss-item-label">{opt.label}</span>
                  {opt.sub && <span className="ss-item-sub">{opt.sub}</span>}
                </span>
                {opt.value === value && <Check size={14} style={{ color: 'var(--primary-700)', flexShrink: 0 }} />}
              </button>
            ))}

            {showCreate && (
              <button
                type="button"
                role="option"
                onMouseEnter={() => setHighlight(filtered.length)}
                onClick={commitCreate}
                disabled={creating}
                className={cn('ss-item ss-item-create', filtered.length === highlight && 'is-highlight')}
              >
                <Plus size={14} style={{ color: 'var(--primary)', flexShrink: 0 }} />
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span className="ss-item-label">
                    {createLabel
                      ? createLabel(trimmed)
                      : (isAr ? `إضافة جديد: "${trimmed}"` : `Create new: "${trimmed}"`)}
                  </span>
                  <span className="ss-item-sub">
                    {creating ? t('saving') : (isAr ? 'سيُحفظ ويُختار تلقائياً' : 'Saved and selected automatically')}
                  </span>
                </span>
              </button>
            )}
          </div>
        </div>
      )}
    </label>
  );
}
