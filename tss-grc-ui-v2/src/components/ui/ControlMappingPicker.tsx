// Reusable, OPTIONAL, MANUAL control-mapping picker — links an entity to many
// sub-controls across frameworks (ECC ↔ ISO ↔ PDPL …). Nothing is auto-derived;
// the user adds/removes mappings, which are saved (replace-set) immediately.
import { useEffect, useMemo, useState } from 'react';
import { Link2, X, Search, FileCheck2, ChevronDown, ChevronRight } from 'lucide-react';
import { Badge } from './Badge';
import { EvidenceReadinessPanel } from './EvidenceReadinessPanel';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { controlLinksApi, ApiError, type ControlLinkDto, type ControlSatisfaction } from '../../lib/api';
import { listExpertCatalog } from '../../lib/grcExpert';

interface FlatSub { id: number; fw: string; ctrl: string; code: string; nameEn: string; nameAr: string; }

const SAT_LEVELS: ControlSatisfaction[] = ['FULL', 'PARTIAL', 'PLANNED'];
const SAT_LABEL: Record<ControlSatisfaction, { en: string; ar: string; tone: 'success' | 'warning' | 'neutral' }> = {
  FULL:    { en: 'Full',    ar: 'كامل',  tone: 'success' },
  PARTIAL: { en: 'Partial', ar: 'جزئي',  tone: 'warning' },
  PLANNED: { en: 'Planned', ar: 'مخطط',  tone: 'neutral' },
};

export interface ControlMappingPickerProps {
  entityType: string;
  entityId: number;
  label?: string;
  /**
   * Show the collapsible "Evidence readiness" section (recommended evidence +
   * live coverage + raise request) under the mapping, when controls are mapped.
   * Defaults to true so every register that maps controls gets it for free.
   * Pages with a dedicated evidence surface pass `evidence={false}`.
   */
  evidence?: boolean;
}

export function ControlMappingPicker({ entityType, entityId, label, evidence = true }: ControlMappingPickerProps) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [mapped, setMapped] = useState<ControlLinkDto[]>([]);
  const [catalog, setCatalog] = useState<FlatSub[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const [evOpen, setEvOpen] = useState(false);

  useEffect(() => {
    controlLinksApi.get(entityType, entityId).then(setMapped).catch(() => setMapped([]));
  }, [entityType, entityId]);

  useEffect(() => {
    setCatalogLoading(true);
    listExpertCatalog().then(fws => {
      const flat: FlatSub[] = [];
      for (const f of fws) for (const c of f.controls) for (const s of c.subControls)
        flat.push({ id: s.id, fw: f.code, ctrl: c.code, code: s.code, nameEn: s.nameEn, nameAr: s.nameAr });
      setCatalog(flat);
    }).catch(() => setCatalog([])).finally(() => setCatalogLoading(false));
  }, []);

  const mappedIds = useMemo(() => new Set(mapped.map(m => m.subControlId)), [mapped]);

  /** Current satisfaction map from the loaded mappings (default FULL). */
  function currentSat(): Record<number, ControlSatisfaction> {
    return Object.fromEntries(mapped.map(m => [m.subControlId, m.satisfaction ?? 'FULL']));
  }

  async function save(ids: number[], sat: Record<number, ControlSatisfaction>) {
    setBusy(true);
    try { setMapped(await controlLinksApi.set(entityType, entityId, ids, sat)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : (ar ? 'تعذّر الحفظ' : 'Save failed')); }
    finally { setBusy(false); }
  }
  function add(id: number) {
    if (!mappedIds.has(id)) void save([...mappedIds, id], { ...currentSat(), [id]: 'FULL' });
    setQ('');
  }
  function remove(id: number) { void save([...mappedIds].filter(x => x !== id), currentSat()); }
  function setSat(id: number, level: ControlSatisfaction) {
    void save([...mappedIds], { ...currentSat(), [id]: level });
  }

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
              <select
                value={m.satisfaction ?? 'FULL'}
                disabled={busy}
                onChange={e => setSat(m.subControlId, e.currentTarget.value as ControlSatisfaction)}
                aria-label={ar ? 'مستوى التغطية' : 'Satisfaction'}
                title={ar ? 'مستوى تغطية الضابط' : 'How fully this satisfies the control'}
                style={{
                  fontSize: 11, padding: '1px 4px', borderRadius: 6, cursor: 'pointer',
                  border: '1px solid var(--border)', background: 'var(--surface)',
                  color: `var(--${SAT_LABEL[m.satisfaction ?? 'FULL'].tone}-fg, var(--ink-2))`,
                }}>
                {SAT_LEVELS.map(l => (
                  <option key={l} value={l}>{ar ? SAT_LABEL[l].ar : SAT_LABEL[l].en}</option>
                ))}
              </select>
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
        {q.trim() && results.length === 0 && (
          <div style={{
            position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 20, marginTop: 2,
            background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 6,
            boxShadow: 'var(--shadow-md, 0 4px 16px rgba(0,0,0,.12))',
            padding: '9px 10px', fontSize: 12, color: 'var(--ink-3)',
          }}>
            {catalogLoading
              ? (ar ? 'جارٍ تحميل دليل الضوابط…' : 'Loading control catalog…')
              : (ar ? 'لا توجد ضوابط مطابقة.' : 'No matching controls.')}
          </div>
        )}
      </div>

      {evidence && mapped.length > 0 && (
        <div style={{ marginTop: 10, borderTop: '1px solid var(--border)', paddingTop: 8 }}>
          <button type="button" onClick={() => setEvOpen(o => !o)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', fontWeight: 700, color: 'var(--ink-1)', padding: 0 }}>
            {evOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <FileCheck2 size={14} /> {ar ? 'جاهزية الأدلة' : 'Evidence readiness'}
            <Badge tone="neutral">{mapped.length}</Badge>
          </button>
          {evOpen && (
            <div style={{ marginTop: 10 }}>
              <EvidenceReadinessPanel entityType={entityType} entityId={entityId} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
