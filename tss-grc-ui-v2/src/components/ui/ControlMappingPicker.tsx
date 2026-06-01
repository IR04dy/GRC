// Reusable, OPTIONAL, MANUAL control-mapping picker — links an entity to many
// sub-controls across frameworks (ECC ↔ ISO ↔ PDPL …). Nothing is auto-derived;
// the user adds/removes mappings, which are saved (replace-set) immediately.
import { useEffect, useMemo, useState } from 'react';
import { Link2, X, Search } from 'lucide-react';
import { Badge } from './Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { controlLinksApi, ApiError, type ControlLinkDto } from '../../lib/api';
import { listExpertCatalog } from '../../lib/grcExpert';

interface FlatSub { id: number; fw: string; ctrl: string; code: string; nameEn: string; nameAr: string; }

export interface ControlMappingPickerProps {
  entityType: string;
  entityId: number;
  label?: string;
}

export function ControlMappingPicker({ entityType, entityId, label }: ControlMappingPickerProps) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [mapped, setMapped] = useState<ControlLinkDto[]>([]);
  const [catalog, setCatalog] = useState<FlatSub[]>([]);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    controlLinksApi.get(entityType, entityId).then(setMapped).catch(() => setMapped([]));
  }, [entityType, entityId]);

  useEffect(() => {
    listExpertCatalog().then(fws => {
      const flat: FlatSub[] = [];
      for (const f of fws) for (const c of f.controls) for (const s of c.subControls)
        flat.push({ id: s.id, fw: f.code, ctrl: c.code, code: s.code, nameEn: s.nameEn, nameAr: s.nameAr });
      setCatalog(flat);
    }).catch(() => setCatalog([]));
  }, []);

  const mappedIds = useMemo(() => new Set(mapped.map(m => m.subControlId)), [mapped]);

  async function save(ids: number[]) {
    setBusy(true);
    try { setMapped(await controlLinksApi.set(entityType, entityId, ids)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : (ar ? 'تعذّر الحفظ' : 'Save failed')); }
    finally { setBusy(false); }
  }
  function add(id: number) { if (!mappedIds.has(id)) void save([...mappedIds, id]); setQ(''); }
  function remove(id: number) { void save([...mappedIds].filter(x => x !== id)); }

  const results = useMemo(() => {
    const t = q.trim().toLowerCase();
    if (!t) return [];
    return catalog.filter(s => !mappedIds.has(s.id) && (
      s.code.toLowerCase().includes(t) || s.fw.toLowerCase().includes(t) ||
      s.nameEn.toLowerCase().includes(t) || (s.nameAr || '').includes(q.trim())
    )).slice(0, 25);
  }, [q, catalog, mappedIds]);

  return (
    <div>
      <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700, marginBottom: 8 }}>
        <Link2 size={15} /> {label ?? (ar ? 'الضوابط المرتبطة' : 'Mapped controls')}
        <Badge tone="neutral">{mapped.length}</Badge>
        <span style={{ fontSize: 11, fontWeight: 400, color: 'var(--ink-3)' }}>
          {ar ? '(اختياري — عبر الأطر)' : '(optional · cross-framework)'}
        </span>
      </div>

      {mapped.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 8 }}>
          {mapped.map(m => (
            <span key={m.subControlId} title={ar ? m.subControlNameAr ?? '' : m.subControlNameEn ?? ''}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12,
                padding: '4px 8px', borderRadius: 'var(--r-pill, 999px)',
                background: 'var(--surface-2)', border: '1px solid var(--border)',
              }}>
              <Badge tone="primary">{m.frameworkCode ?? '—'}</Badge>
              <span className="mono" style={{ fontWeight: 600 }}>{m.subControlCode ?? m.subControlId}</span>
              <span style={{ color: 'var(--ink-3)', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {ar ? m.subControlNameAr : m.subControlNameEn}
              </span>
              <button onClick={() => remove(m.subControlId)} disabled={busy}
                aria-label="remove" style={{ border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-3)', display: 'inline-flex' }}>
                <X size={13} />
              </button>
            </span>
          ))}
        </div>
      )}

      <div style={{ position: 'relative' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, border: '1px solid var(--border)', borderRadius: 6, padding: '6px 8px', background: 'var(--surface)' }}>
          <Search size={14} style={{ color: 'var(--ink-3)' }} />
          <input
            value={q}
            onChange={e => setQ(e.currentTarget.value)}
            placeholder={ar ? 'ابحث عن ضابط لربطه (الرمز/الإطار/الاسم)…' : 'Search a control to map (code / framework / name)…'}
            style={{ border: 'none', outline: 'none', flex: 1, fontSize: 12, background: 'transparent', color: 'var(--ink-1)' }}
          />
        </div>
        {results.length > 0 && (
          <div style={{
            position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 20, marginTop: 2,
            maxHeight: 280, overflowY: 'auto', background: 'var(--surface)',
            border: '1px solid var(--border)', borderRadius: 6, boxShadow: 'var(--shadow-md, 0 4px 16px rgba(0,0,0,.12))',
          }}>
            {results.map(s => (
              <button key={s.id} onClick={() => add(s.id)} disabled={busy}
                style={{
                  display: 'flex', width: '100%', gap: 8, alignItems: 'center', padding: '7px 10px',
                  border: 'none', borderBottom: '1px solid var(--border)', background: 'var(--surface)',
                  cursor: 'pointer', textAlign: 'start', fontSize: 12,
                }}>
                <Badge tone="info">{s.fw}</Badge>
                <span className="mono" style={{ fontWeight: 600 }}>{s.code}</span>
                <span style={{ color: 'var(--ink-2)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {ar ? s.nameAr || s.nameEn : s.nameEn}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
