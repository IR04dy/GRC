import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Flame, RefreshCw, Search, Calculator, Upload } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  iaApi, residualBand,
  type IaUniverseEntry, type UniverseEntryCreate,
} from '../../lib/iaPlan';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const BAND_TONE: Record<ReturnType<typeof residualBand>, BadgeTone> = {
  critical: 'danger',
  high:     'danger',
  medium:   'warning',
  low:      'success',
  unscored: 'neutral',
};

const BAND_LABEL_EN: Record<ReturnType<typeof residualBand>, string> = {
  critical: 'Critical (>20)',
  high:     'High (15-20)',
  medium:   'Medium (8-15)',
  low:      'Low (<8)',
  unscored: 'Unscored',
};
const BAND_LABEL_AR: Record<ReturnType<typeof residualBand>, string> = {
  critical: 'حرج (>20)',
  high:     'مرتفع (15-20)',
  medium:   'متوسط (8-15)',
  low:      'منخفض (<8)',
  unscored: 'بدون تقييم',
};

/**
 * Sprint 13 — Audit universe register with residual-risk heatmap.
 *
 * <p>Shows every auditable process the bank tracks (the "Jardah Shamilah"
 * — الجَردة الشاملة للعمليات), grouped by residual-risk band so the CAE
 * can see at a glance what the next year's plan needs to cover.
 */
export function IaUniversePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [state, setState] = useState<LoadState>('idle');
  const [entries, setEntries] = useState<IaUniverseEntry[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [filter, setFilter] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [importOpen, setImportOpen] = useState(false);

  async function load() {
    setState('loading'); setErr(null);
    try {
      setEntries(await iaApi.listAllUniverse());
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); }, []);

  // Re-run inherent × (1 − control-environment strength) → residual for one entry.
  async function onRecompute(entry: IaUniverseEntry) {
    setBusyId(entry.id);
    try {
      const updated = await iaApi.recomputeUniverseResidual(entry.id);
      setEntries(prev => prev.map(x => (x.id === updated.id ? updated : x)));
      toast.success(isAr
        ? `أُعيد احتساب الخطر المتبقي: ${updated.residualRiskScore?.toFixed(2) ?? '—'}`
        : `Residual recomputed: ${updated.residualRiskScore?.toFixed(2) ?? '—'}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إعادة الاحتساب' : 'Recompute failed'));
    } finally { setBusyId(null); }
  }

  async function submitImport(v: { planId: string; rows: string }): Promise<void> {
    const lines = v.rows.split('\n').map(s => s.trim()).filter(Boolean);
    if (lines.length === 0) { toast.danger(isAr ? 'لا صفوف للاستيراد' : 'No rows to import'); return; }
    const parsed: UniverseEntryCreate[] = lines.map(line => {
      const [code, titleEn, titleAr, bu, inh, ctrl] = line.split('|').map(s => s.trim());
      return {
        entryCode: code,
        titleEn: titleEn || code,
        titleAr: titleAr || titleEn || code,
        businessUnitEn: bu || undefined,
        inherentRiskScore: inh ? Number(inh) : undefined,
        controlEnvironmentScore: ctrl ? Number(ctrl) : undefined,
      };
    }).filter(e => e.entryCode);
    if (parsed.length === 0) { toast.danger(isAr ? 'تعذّر تحليل أي صف' : 'Could not parse any rows'); return; }
    try {
      const planId = v.planId.trim() ? Number(v.planId) : undefined;
      const res = await iaApi.bulkImportUniverse({ planId, entries: parsed });
      toast.success(isAr
        ? `استيراد: ${res.created} جديد · ${res.skippedExisting} موجود مسبقاً (من ${res.totalSubmitted})`
        : `Imported ${res.created} new · ${res.skippedExisting} existing (of ${res.totalSubmitted})`);
      setImportOpen(false);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الاستيراد' : 'Import failed'));
    }
  }

  const importFields: FieldDef[] = [
    { kind: 'text', key: 'planId', label: isAr ? 'معرّف الخطة (اختياري)' : 'Plan id (optional)', span: 12 },
    { kind: 'textarea', key: 'rows', rows: 8, span: 12,
      label: isAr
        ? 'صف لكل سطر — الرمز | العنوان EN | العنوان AR | الوحدة | متأصل | بيئة الضبط'
        : 'One row per line — Code | Title EN | Title AR | Business unit | Inherent | Control-env' },
  ];

  const filtered = useMemo(() => {
    if (!filter.trim()) return entries;
    const f = filter.trim().toLowerCase();
    return entries.filter(e =>
      e.entryCode.toLowerCase().includes(f)
      || (e.titleEn ?? '').toLowerCase().includes(f)
      || (e.titleAr ?? '').includes(filter.trim())
      || (e.businessUnitEn ?? '').toLowerCase().includes(f)
    );
  }, [entries, filter]);

  // Group by residual band for the heatmap
  const grouped = useMemo(() => {
    const bands: Record<ReturnType<typeof residualBand>, IaUniverseEntry[]> = {
      critical: [], high: [], medium: [], low: [], unscored: [],
    };
    for (const e of filtered) {
      bands[residualBand(e.residualRiskScore)].push(e);
    }
    // Sort each band by residual desc
    Object.keys(bands).forEach(k => {
      bands[k as keyof typeof bands].sort(
        (a, b) => (b.residualRiskScore ?? 0) - (a.residualRiskScore ?? 0));
    });
    return bands;
  }, [filtered]);

  return (
    <Shell
      title={isAr ? 'الجَردة الشاملة للعمليات' : 'Audit Universe'}
      sub={isAr
        ? 'سجل العمليات القابلة للمراجعة على مستوى البنك مع الخطر المتبقي'
        : 'Bank-wide register of auditable processes with residual-risk scoring'}
    >
      <PageHeader
        eyebrow={isAr ? 'المراجعة الداخلية' : 'Internal Audit'}
        title={isAr ? 'الجَردة الشاملة للعمليات' : 'Audit Universe'}
        sub={isAr
          ? 'منهجية IA: الخطر المتأصل × (1 − قوة بيئة الضبط) = الخطر المتبقي بمقياس 0-25'
          : 'IA convention: inherent × (1 − control environment strength) = residual on 0-25 scale'}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/audit/ia/plans">
              <Button variant="ghost" size="sm">{isAr ? 'الخطط' : 'Annual plans'}</Button>
            </Link>
            <Button variant="primary" size="sm" onClick={() => setImportOpen(true)}>
              <Upload size={14} /> {isAr ? 'استيراد دفعة' : 'Bulk import'}
            </Button>
          </>
        }
      />

      {/* Search */}
      <Card>
        <div style={{ padding: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
          <Search size={14} style={{ opacity: 0.6 }} />
          <input
            type="search"
            placeholder={isAr ? 'بحث بالرمز أو العنوان…' : 'Search by code or title…'}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            style={{
              flex: 1, border: 'none', background: 'transparent',
              outline: 'none', fontSize: 13,
            }}
          />
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {filtered.length}/{entries.length} {isAr ? 'عملية' : 'entries'}
          </span>
        </div>
      </Card>

      {state === 'loading' && (
        <Card><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>
      )}
      {state === 'error' && (
        <Card><div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>{err}</div></Card>
      )}

      {state === 'ready' && (
        <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {(['critical', 'high', 'medium', 'low', 'unscored'] as const).map(band => {
            const rows = grouped[band];
            if (rows.length === 0) return null;
            return (
              <Card key={band}>
                <div style={{
                  padding: 10, borderBottom: '1px solid var(--border)',
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Flame size={14} />
                    <span style={{ fontSize: 13, fontWeight: 600 }}>
                      {isAr ? BAND_LABEL_AR[band] : BAND_LABEL_EN[band]}
                    </span>
                    <Badge tone={BAND_TONE[band]}>{rows.length}</Badge>
                  </div>
                </div>
                {rows.map(e => (
                  <div key={e.id} style={{
                    padding: 10, borderBottom: '1px solid var(--border)',
                    display: 'grid', gridTemplateColumns: '140px 1fr auto auto auto auto',
                    gap: 12, alignItems: 'center',
                  }}>
                    <div style={{ fontFamily: 'monospace', fontSize: 11, fontWeight: 700 }}>{e.entryCode}</div>
                    <div>
                      <div style={{ fontSize: 12 }}>{isAr ? e.titleAr : e.titleEn}</div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                        {isAr ? (e.businessUnitAr ?? e.processNameAr ?? '') : (e.businessUnitEn ?? e.processNameEn ?? '')}
                      </div>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center' }}>
                      <div>{isAr ? 'متأصل' : 'Inherent'}</div>
                      <strong style={{ color: 'var(--ink-1)' }}>{e.inherentRiskScore?.toFixed(1) ?? '—'}</strong>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center' }}>
                      <div>{isAr ? 'متبقّ' : 'Residual'}</div>
                      <Badge tone={BAND_TONE[band]}>{e.residualRiskScore?.toFixed(2) ?? '—'}</Badge>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                      {e.plannedAuditYear && e.plannedQuarter && (
                        <Badge tone="info">{e.plannedQuarter} {e.plannedAuditYear}</Badge>
                      )}
                    </div>
                    <Button variant="ghost" size="sm" disabled={busyId === e.id}
                      title={isAr ? 'إعادة احتساب الخطر المتبقي' : 'Recompute residual risk'}
                      onClick={() => void onRecompute(e)}>
                      <Calculator size={13} />
                      <span style={{ marginInlineStart: 4 }}>{isAr ? 'احتساب' : 'Recompute'}</span>
                    </Button>
                  </div>
                ))}
              </Card>
            );
          })}
        </div>
      )}

      <FormModal<{ planId: string; rows: string }>
        open={importOpen}
        title={isAr ? 'استيراد دفعة عمليات للجَردة' : 'Bulk-import universe entries'}
        fields={importFields}
        initial={{ planId: '', rows: '' }}
        onClose={() => setImportOpen(false)}
        onSubmit={submitImport}
        submitLabel={isAr ? 'استيراد' : 'Import'}
      />
    </Shell>
  );
}
