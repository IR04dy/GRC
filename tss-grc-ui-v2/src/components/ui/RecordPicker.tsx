import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, Search, X } from 'lucide-react';
import {
  type EntityKind, type PickerRecord,
  getRecordDef, recordRecents, pushRecord,
} from '../../lib/recordRegistry';
import { useI18n } from '../../lib/i18n';

export interface RecordPickerProps {
  /** Entity kind — must be registered via registerRecord(). */
  entity: EntityKind;
  /** Selected id (single-select) — caller controls. */
  value?: string;
  /** Selected ids (multi-select). */
  values?: string[];
  /** Multi-select mode? */
  multiple?: boolean;
  onChange?: (value: string | string[]) => void;
  /** Override the default placeholder. */
  placeholder?: string;
  /** Allow clearing the selection. */
  allowClear?: boolean;
  size?: 'sm' | 'md';
}

/**
 * Generic record picker. Replaces hand-rolled "select a Risk / Vendor / Control"
 * code that was previously sprinkled across pages. Backed by `recordRegistry`.
 *
 * Features:
 *   - Type-ahead search (debounced 200ms)
 *   - Recents (5 most-recently-picked, per entity, in localStorage)
 *   - Single + multi select modes
 *   - Bilingual placeholder + labels via the registry's labelEn/Ar
 *   - Keyboard navigation (↑↓ + Enter)
 */
export function RecordPicker({
  entity, value, values = [], multiple = false,
  onChange, placeholder, allowClear = true, size = 'md',
}: RecordPickerProps) {
  const { lang } = useI18n();
  const def = getRecordDef(entity);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PickerRecord[]>([]);
  const [highlight, setHighlight] = useState(0);
  const [labelCache, setLabelCache] = useState<Record<string, PickerRecord>>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const recents = useMemo(() => recordRecents(entity), [entity]);

  // Debounced search
  useEffect(() => {
    if (!def) return;
    const handle = setTimeout(async () => {
      const out = query.trim() === '' ? recents : await def.search(query);
      setResults(out.slice(0, def.resultLimit ?? 8));
      setHighlight(0);
    }, 200);
    return () => clearTimeout(handle);
  }, [query, def, recents]);

  // Click-outside to close
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

  // Focus input when opening
  useEffect(() => { if (open) inputRef.current?.focus(); }, [open]);

  if (!def) {
    return <div style={{ color: 'var(--danger)', fontSize: 12 }}>Unknown record entity: {entity}</div>;
  }

  const Icon = def.icon;
  const labelEntity = lang === 'ar' ? def.labelAr : def.labelEn;
  const ph = placeholder ?? (lang === 'ar' ? `اختر ${labelEntity}` : `Pick a ${labelEntity}`);

  const selectedIds = multiple ? values : (value ? [value] : []);
  const selectedRecords = selectedIds.map(id => labelCache[id]).filter(Boolean);

  const pick = (r: PickerRecord) => {
    pushRecord(entity, r);
    setLabelCache((c) => ({ ...c, [r.id]: r }));
    if (multiple) {
      const next = selectedIds.includes(r.id) ? selectedIds.filter(i => i !== r.id) : [...selectedIds, r.id];
      onChange?.(next);
    } else {
      onChange?.(r.id);
      setOpen(false);
    }
  };

  const clear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange?.(multiple ? [] : '');
  };

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setHighlight(h => Math.min(h + 1, results.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlight(h => Math.max(h - 1, 0)); }
    else if (e.key === 'Enter' && results[highlight]) { e.preventDefault(); pick(results[highlight]); }
    else if (e.key === 'Escape') { setOpen(false); }
  };

  return (
    <div ref={containerRef} className={`rp-root rp-${size}`}>
      <button type="button" className="rp-trigger" onClick={() => setOpen(o => !o)}>
        <Icon size={14} />
        {selectedRecords.length === 0 && <span className="rp-placeholder">{ph}</span>}
        {selectedRecords.length > 0 && (
          <span className="rp-selected">
            {selectedRecords.map(r => r.label).join(', ')}
          </span>
        )}
        {allowClear && selectedRecords.length > 0 && (
          <X size={12} onClick={clear} className="rp-clear" />
        )}
        <ChevronDown size={12} className="rp-caret" />
      </button>

      {open && (
        <div className="rp-popover">
          <div className="rp-search">
            <Search size={12} />
            <input
              ref={inputRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={onKey}
              placeholder={lang === 'ar' ? 'بحث…' : 'Search…'}
            />
          </div>
          <div className="rp-results">
            {results.length === 0 && (
              <div className="rp-empty">{lang === 'ar' ? 'لا نتائج' : 'No results'}</div>
            )}
            {results.map((r, i) => {
              const isSel = selectedIds.includes(r.id);
              return (
                <button
                  key={r.id}
                  type="button"
                  className={`rp-result ${i === highlight ? 'rp-highlight' : ''} ${isSel ? 'rp-sel' : ''}`}
                  onMouseEnter={() => setHighlight(i)}
                  onClick={() => pick(r)}
                >
                  {r.iconHint && <span className="rp-avatar">{r.iconHint}</span>}
                  <span className="rp-label">
                    <span>{r.label}</span>
                    {r.sublabel && <span className="rp-sublabel">{r.sublabel}</span>}
                  </span>
                  {isSel && <span className="rp-check">✓</span>}
                </button>
              );
            })}
          </div>
          {query.trim() === '' && recents.length > 0 && (
            <div className="rp-foot">{lang === 'ar' ? 'الأخيرة' : 'Recent'}</div>
          )}
        </div>
      )}
    </div>
  );
}
