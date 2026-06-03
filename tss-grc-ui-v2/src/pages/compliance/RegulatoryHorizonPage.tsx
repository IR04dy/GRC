import { useCallback, useEffect, useMemo, useState } from 'react';
import { Telescope, Layers, AlertOctagon, Clock, ShieldAlert, RefreshCw, CalendarClock } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { regulatoryHorizonApi, ApiError, type RegulatoryHorizonDto, type RegulatoryHorizonRowDto, type HorizonBucket } from '../../lib/api';

const BUCKET_ORDER: HorizonBucket[] = ['PAST_DUE', 'IMMINENT', 'NEAR_TERM', 'QUARTER', 'HORIZON'];

const BUCKET_COLOR: Record<HorizonBucket, string> = {
  PAST_DUE:  'var(--danger)',
  IMMINENT:  'var(--risk-high)',
  NEAR_TERM: 'var(--warning)',
  QUARTER:   'var(--info)',
  HORIZON:   'var(--success)',
};
const BUCKET_TONE: Record<HorizonBucket, BadgeTone> = {
  PAST_DUE: 'danger', IMMINENT: 'warning', NEAR_TERM: 'primary', QUARTER: 'info', HORIZON: 'neutral',
};
const BUCKET_LABEL: Record<HorizonBucket, { en: string; ar: string; range: string }> = {
  PAST_DUE:  { en: 'Past due',    ar: 'متأخّر',        range: '< 0d' },
  IMMINENT:  { en: 'Imminent',    ar: 'وشيك',          range: '≤ 30d' },
  NEAR_TERM: { en: 'Near-term',   ar: 'قريب',          range: '31–90d' },
  QUARTER:   { en: 'This quarter', ar: 'ربع السنة',    range: '91–180d' },
  HORIZON:   { en: 'Horizon',     ar: 'الأفق البعيد',  range: '> 180d' },
};

const IMPACT_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success', UNKNOWN: 'neutral',
};
const IMPACT_LABEL_AR: Record<string, string> = {
  CRITICAL: 'حرج', HIGH: 'مرتفع', MEDIUM: 'متوسط', LOW: 'منخفض', UNKNOWN: 'غير محدّد',
};
const STATUS_LABEL_AR: Record<string, string> = {
  NEW: 'جديد', APPLICABILITY_ASSESSED: 'قُيّم الانطباق', IMPACT_ASSESSED: 'قُيّم الأثر',
  CONTROLS_UPDATED: 'حُدّثت الضوابط', CLOSED: 'مُغلَق', NOT_APPLICABLE: 'غير منطبق',
};
const AUTHORITY_LABEL_AR: Record<string, string> = {
  SAMA: 'ساما', NCA: 'الأمن السيبراني', SDAIA: 'سدايا', CMA: 'هيئة السوق',
  ZATCA: 'الزكاة والضريبة', NCEMA: 'إدارة الأزمات', MOH: 'وزارة الصحة', CITC: 'الاتصالات',
};

const SELECT_STYLE: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 130, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

/**
 * Regulatory Horizon scanner — surfaces GET /api/v1/compliance/regulatory-horizon.
 * A tenant-wide executive view of every in-flight regulatory change bucketed by
 * effective date (PAST_DUE → HORIZON), with authority + impact distributions and
 * a per-change countdown register. Pure read aggregator over compliance.regulatory_changes.
 */
export function RegulatoryHorizonPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [data, setData] = useState<RegulatoryHorizonDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [bucketFilter, setBucketFilter] = useState<string>('');
  const [authorityFilter, setAuthorityFilter] = useState<string>('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await regulatoryHorizonApi.scan());
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الأفق التنظيمي' : 'Failed to load regulatory horizon'));
      setData(null);
    } finally { setLoading(false); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  const authorities = useMemo(
    () => Object.keys(data?.bySourceAuthority ?? {}).sort((a, b) => (data!.bySourceAuthority[b] - data!.bySourceAuthority[a])),
    [data],
  );
  const total = data?.activeChangeCount ?? 0;
  const pastDue = data?.byBucket?.PAST_DUE ?? 0;
  const imminent = data?.byBucket?.IMMINENT ?? 0;
  const critical = data?.byImpactLevel?.CRITICAL ?? 0;

  const authLabel = (a: string) => (isAr ? (AUTHORITY_LABEL_AR[a] ?? a) : a);
  const impactLabel = (i: string) => (isAr ? (IMPACT_LABEL_AR[i] ?? i) : i);
  const statusLabel = (s: string) => (isAr ? (STATUS_LABEL_AR[s] ?? s) : s.replace(/_/g, ' '));

  const countdown = (d: number) => {
    if (d < 0) return isAr ? `متأخّر ${Math.abs(d)} يوم` : `${Math.abs(d)}d overdue`;
    if (d === 0) return isAr ? 'اليوم' : 'today';
    return isAr ? `خلال ${d} يوم` : `in ${d}d`;
  };

  const rows: RegulatoryHorizonRowDto[] = useMemo(() => {
    let r = data?.rows ?? [];
    if (bucketFilter) r = r.filter(x => x.bucket === bucketFilter);
    if (authorityFilter) r = r.filter(x => x.sourceAuthority === authorityFilter);
    return [...r].sort((a, b) => a.daysUntilEffective - b.daysUntilEffective);
  }, [data, bucketFilter, authorityFilter]);

  const maxImpact = Math.max(1, ...Object.values(data?.byImpactLevel ?? {}));

  return (
    <Shell title={isAr ? 'الأفق التنظيمي' : 'Regulatory horizon'} sub={isAr ? 'ماسح التغييرات التنظيمية القادمة' : 'Forward regulatory-change scanner'}>
      <PageHeader
        eyebrow={isAr ? 'الامتثال' : 'Compliance'}
        title={isAr ? 'الأفق التنظيمي' : 'Regulatory horizon'}
        sub={isAr
          ? 'لقطة على مستوى المنشأة لكل تغيير تنظيمي لم يُغلَق بعد، مُصنَّفاً حسب تاريخ النفاذ مع توزيع الجهات ومستوى الأثر.'
          : 'A tenant-wide snapshot of every not-yet-closed regulatory change, bucketed by effective date with authority and impact distributions.'}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Layers}      label={isAr ? 'تغييرات نشطة' : 'Active changes'} value={loading ? '—' : total} tone="primary" />
        <Kpi Icon={AlertOctagon} label={isAr ? 'متأخّرة' : 'Past due'} value={loading ? '—' : pastDue} tone="danger" />
        <Kpi Icon={Clock}       label={isAr ? 'وشيكة (≤30 يوم)' : 'Imminent (≤30d)'} value={loading ? '—' : imminent} tone="warning" />
        <Kpi Icon={ShieldAlert} label={isAr ? 'أثر حرج' : 'Critical impact'} value={loading ? '—' : critical} tone="danger" />
      </div>

      <Card title={isAr ? 'التوزيع حسب تاريخ النفاذ' : 'Distribution by effective date'} Icon={CalendarClock}
        action={<Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>{isAr ? 'تحديث' : 'Refresh'}</Button>}>
        {/* Segmented horizon timeline */}
        <div style={{ display: 'flex', height: 14, borderRadius: 'var(--r-sm)', overflow: 'hidden', background: 'var(--surface-2)', marginBottom: 10 }}>
          {BUCKET_ORDER.map(b => {
            const n = data?.byBucket?.[b] ?? 0;
            if (!n) return null;
            return <div key={b} title={`${BUCKET_LABEL[b][isAr ? 'ar' : 'en']}: ${n}`}
              style={{ flex: n, background: BUCKET_COLOR[b] }} />;
          })}
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14 }}>
          {BUCKET_ORDER.map(b => {
            const n = data?.byBucket?.[b] ?? 0;
            return (
              <button key={b} type="button"
                onClick={() => setBucketFilter(f => (f === b ? '' : b))}
                style={{
                  display: 'inline-flex', alignItems: 'center', gap: 7, background: 'none',
                  border: bucketFilter === b ? '1px solid var(--accent)' : '1px solid transparent',
                  borderRadius: 'var(--r-sm)', padding: '3px 7px', cursor: 'pointer',
                  opacity: bucketFilter && bucketFilter !== b ? 0.5 : 1,
                }}>
                <span style={{ width: 9, height: 9, borderRadius: 2, background: BUCKET_COLOR[b], display: 'inline-block' }} />
                <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>{BUCKET_LABEL[b][isAr ? 'ar' : 'en']}</span>
                <span className="text-2xs text-muted">{BUCKET_LABEL[b].range}</span>
                <span className="mono" style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-0)' }}>{n}</span>
              </button>
            );
          })}
        </div>

        {/* Authority + impact mini-distributions */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20, marginTop: 18 }}>
          <div>
            <div className="text-2xs text-muted" style={{ marginBottom: 8, textTransform: 'uppercase', letterSpacing: '.04em' }}>{isAr ? 'حسب الجهة' : 'By authority'}</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {authorities.length === 0 && <span className="text-2xs text-muted">{isAr ? 'لا بيانات' : 'No data'}</span>}
              {authorities.map(a => (
                <button key={a} type="button"
                  onClick={() => setAuthorityFilter(f => (f === a ? '' : a))}
                  style={{ ...SELECT_STYLE, minWidth: 0, cursor: 'pointer', display: 'inline-flex', gap: 6, alignItems: 'center',
                    borderColor: authorityFilter === a ? 'var(--accent)' : 'var(--border)',
                    opacity: authorityFilter && authorityFilter !== a ? 0.55 : 1 }}>
                  <span style={{ fontWeight: 600 }}>{authLabel(a)}</span>
                  <span className="mono text-2xs" style={{ color: 'var(--ink-0)', fontWeight: 700 }}>{data!.bySourceAuthority[a]}</span>
                </button>
              ))}
            </div>
          </div>
          <div>
            <div className="text-2xs text-muted" style={{ marginBottom: 8, textTransform: 'uppercase', letterSpacing: '.04em' }}>{isAr ? 'حسب مستوى الأثر' : 'By impact level'}</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'].map(lvl => {
                const n = data?.byImpactLevel?.[lvl] ?? 0;
                return (
                  <div key={lvl} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ width: 70, fontSize: 12, color: 'var(--ink-2)' }}>{impactLabel(lvl)}</span>
                    <div style={{ flex: 1, height: 8, background: 'var(--surface-2)', borderRadius: 4, overflow: 'hidden' }}>
                      <div style={{ width: `${(n / maxImpact) * 100}%`, height: '100%',
                        background: lvl === 'CRITICAL' ? 'var(--risk-crit)' : lvl === 'HIGH' ? 'var(--risk-high)' : lvl === 'MEDIUM' ? 'var(--warning)' : lvl === 'LOW' ? 'var(--success)' : 'var(--ink-4)' }} />
                    </div>
                    <span className="mono text-2xs" style={{ width: 20, textAlign: 'end', color: 'var(--ink-1)' }}>{n}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </Card>

      <Card title={isAr ? 'التغييرات التنظيمية القادمة' : 'Upcoming regulatory changes'} Icon={Telescope}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <select value={bucketFilter} onChange={e => setBucketFilter(e.target.value)} style={SELECT_STYLE} aria-label={isAr ? 'النطاق الزمني' : 'Horizon bucket'}>
              <option value="">{isAr ? 'كل النطاقات' : 'All buckets'}</option>
              {BUCKET_ORDER.map(b => <option key={b} value={b}>{BUCKET_LABEL[b][isAr ? 'ar' : 'en']}</option>)}
            </select>
            <select value={authorityFilter} onChange={e => setAuthorityFilter(e.target.value)} style={SELECT_STYLE} aria-label={isAr ? 'الجهة' : 'Authority'}>
              <option value="">{isAr ? 'كل الجهات' : 'All authorities'}</option>
              {authorities.map(a => <option key={a} value={a}>{authLabel(a)}</option>)}
            </select>
            {(bucketFilter || authorityFilter) &&
              <Button size="sm" variant="ghost" onClick={() => { setBucketFilter(''); setAuthorityFilter(''); }}>{isAr ? 'مسح' : 'Clear'}</Button>}
          </div>
        }>
        {loading
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : rows.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد تغييرات مطابقة' : 'No matching changes'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الجهة' : 'Authority'}</th>
                  <th>{isAr ? 'التغيير التنظيمي' : 'Regulatory change'}</th>
                  <th>{isAr ? 'تاريخ النفاذ' : 'Effective'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'العدّ التنازلي' : 'Countdown'}</th>
                  <th>{isAr ? 'النطاق' : 'Bucket'}</th>
                  <th>{isAr ? 'الأثر' : 'Impact'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                </tr></thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.id}>
                      <td><Badge tone="neutral">{authLabel(r.sourceAuthority)}</Badge></td>
                      <td>
                        <div style={{ fontWeight: 600, fontSize: 13, maxWidth: 420 }} title={isAr && r.titleAr ? r.titleAr : r.titleEn}>
                          {isAr && r.titleAr ? r.titleAr : r.titleEn}
                        </div>
                        {r.citation && <div className="mono text-2xs text-muted">{r.citation}</div>}
                      </td>
                      <td className="mono text-2xs">{r.effectiveDate ?? '—'}</td>
                      <td className="mono text-2xs" style={{ textAlign: 'end', fontWeight: 600, color: r.daysUntilEffective < 0 ? 'var(--danger)' : r.daysUntilEffective <= 30 ? 'var(--risk-high)' : 'var(--ink-1)' }}>
                        {countdown(r.daysUntilEffective)}
                      </td>
                      <td><Badge tone={BUCKET_TONE[r.bucket]} dot>{BUCKET_LABEL[r.bucket][isAr ? 'ar' : 'en']}</Badge></td>
                      <td><Badge tone={IMPACT_TONE[r.impactLevel] ?? 'neutral'}>{impactLabel(r.impactLevel)}</Badge></td>
                      <td className="text-2xs text-muted">{statusLabel(r.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>}
      </Card>
    </Shell>
  );
}
