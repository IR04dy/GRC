import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Scan, RefreshCw, AlertOctagon, Activity } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type ScannerRun, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'running' | 'completed' | 'with_critical' | 'errors';

const STATUS_TONE: Record<string, 'info' | 'success' | 'danger' | 'neutral'> = {
  RUNNING: 'info', QUEUED: 'neutral', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'neutral',
};

export function ScannerRunsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<ScannerRun> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.scannerRuns(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all')           return all;
    if (filter === 'running')       return all.filter(r => r.status === 'RUNNING' || r.status === 'QUEUED');
    if (filter === 'completed')     return all.filter(r => r.status === 'COMPLETED');
    if (filter === 'with_critical') return all.filter(r => (r.findingsCritical ?? 0) > 0);
    if (filter === 'errors')        return all.filter(r => r.status === 'FAILED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      running: all.filter(r => r.status === 'RUNNING' || r.status === 'QUEUED').length,
      completed: all.filter(r => r.status === 'COMPLETED').length,
      withCritical: all.filter(r => (r.findingsCritical ?? 0) > 0).length,
      errors: all.filter(r => r.status === 'FAILED').length,
      totalCritical: all.reduce((s, r) => s + (r.findingsCritical ?? 0), 0),
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'تشغيلات الماسحات' : 'Vulnerability scanner runs'} sub={isAr ? 'Tenable · Qualys · Defender' : 'Tenable · Qualys · Defender'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'تشغيلات الماسحات' : 'Vulnerability scanner runs'}
        sub={isAr ? 'سجل تشغيلات الماسحات + تجميع المخرجات' : 'Scan history with severity rollup'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Scan}         label={isAr ? 'قيد التشغيل' : 'Running / queued'} value={String(counts.running)}      tone={counts.running > 0 ? 'info' : 'neutral'} />
        <Kpi Icon={AlertOctagon} label={isAr ? 'بثغرات حرجة' : 'With CRITICAL'}    value={String(counts.withCritical)} tone={counts.withCritical > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Activity}     label={isAr ? 'إجمالي الحرج' : 'Total CRITICAL'}  value={String(counts.totalCritical)} tone={counts.totalCritical > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Scan}         label={isAr ? 'مكتملة' : 'Completed'}             value={String(counts.completed)}     tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',           label: isAr ? 'الكل'        : 'All',         count: counts.total },
          { id: 'running',       label: isAr ? 'يعمل الآن'  : 'Running',     count: counts.running },
          { id: 'with_critical', label: isAr ? 'بحرج'       : 'With CRITICAL', count: counts.withCritical },
          { id: 'completed',     label: isAr ? 'مكتملة'     : 'Completed',   count: counts.completed },
          { id: 'errors',        label: isAr ? 'فشلت'       : 'Failed',       count: counts.errors },
        ] as { id: Filter; label: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setFilter(td.id)} style={{ padding: '8px 12px', fontSize: 12, fontWeight: 600,
            border: `1px solid ${filter === td.id ? 'var(--primary)' : 'var(--border)'}`,
            background: filter === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
            borderRadius: 'var(--r-sm)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {td.label}<Badge tone={filter === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}><div style={{ color: 'var(--danger-fg)' }}>{errMsg}</div></div>}
        {state === 'ready' && rows.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'الماسح' : 'Scanner'}</th>
              <th>{isAr ? 'اسم المسح' : 'Scan name'}</th>
              <th>{isAr ? 'البداية' : 'Started'}</th>
              <th>{isAr ? 'الانتهاء' : 'Completed'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th>{isAr ? 'الأصول' : 'Assets'}</th>
              <th>{isAr ? 'الإجمالي' : 'Total'}</th>
              <th>{isAr ? 'حرج' : 'Crit'}</th>
              <th>{isAr ? 'عالي' : 'High'}</th>
              <th>{isAr ? 'متوسط' : 'Med'}</th>
              <th>{isAr ? 'منخفض' : 'Low'}</th>
            </tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.id}>
                <td><Badge tone="info">{r.scanner}</Badge></td>
                <td style={{ fontSize: 11 }}>
                  <Link to={`/socops/scanner-runs/${r.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {r.scanName ?? `Run #${r.id}`}
                  </Link>
                  {r.errorMessage && <div style={{ fontSize: 10, color: 'var(--danger-fg)' }}>{r.errorMessage}</div>}
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{r.startedAt?.slice(0, 16).replace('T', ' ')}</td>
                <td className="mono" style={{ fontSize: 10 }}>{r.completedAt?.slice(0, 16).replace('T', ' ') ?? '—'}</td>
                <td><Badge tone={STATUS_TONE[r.status] ?? 'neutral'}>{r.status}</Badge></td>
                <td className="mono" style={{ fontSize: 10 }}>{r.assetsScanned}</td>
                <td className="mono" style={{ fontSize: 10 }}>{r.findingsTotal}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--danger-fg)' }}>{r.findingsCritical}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--warning-fg)' }}>{r.findingsHigh}</td>
                <td className="mono" style={{ fontSize: 10 }}>{r.findingsMedium}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{r.findingsLow}</td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
