import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Globe, RefreshCw, AlertTriangle, Send, CheckCircle2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type BrandMonitoring, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'critical' | 'open' | 'takedown_pending' | 'completed';

const SEV_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral',
};

const STATUS_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  NEW: 'danger', TRIAGED: 'warning', TAKEDOWN_REQUESTED: 'info', TAKEDOWN_COMPLETED: 'success',
  CLOSED: 'neutral', FALSE_POSITIVE: 'neutral',
};

const OPEN = new Set(['NEW', 'TRIAGED']);

export function BrandMonitoringPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<BrandMonitoring> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('critical');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.brandAlerts(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'critical')         return all.filter(a => a.severity === 'CRITICAL');
    if (filter === 'open')             return all.filter(a => OPEN.has(a.status));
    if (filter === 'takedown_pending') return all.filter(a => a.status === 'TAKEDOWN_REQUESTED');
    if (filter === 'completed')        return all.filter(a => a.status === 'TAKEDOWN_COMPLETED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      critical: all.filter(a => a.severity === 'CRITICAL').length,
      open: all.filter(a => OPEN.has(a.status)).length,
      takedown: all.filter(a => a.status === 'TAKEDOWN_REQUESTED').length,
      completed: all.filter(a => a.status === 'TAKEDOWN_COMPLETED').length,
    };
  }, [page]);

  async function doRequest(id: number) {
    try { await socopsApi.brandRequestTakedown(id); toast.success(isAr ? 'تم طلب الإزالة' : 'Takedown requested'); void load(); }
    catch (e) { toast.danger((e as Error)?.message ?? 'Request failed'); }
  }
  async function doComplete(id: number) {
    try { await socopsApi.brandCompleteTakedown(id, isAr ? 'تأكيد الإزالة' : 'Takedown confirmed'); toast.success(isAr ? 'تم تأكيد الإزالة' : 'Takedown confirmed'); void load(); }
    catch (e) { toast.danger((e as Error)?.message ?? 'Complete failed'); }
  }

  return (
    <Shell title={isAr ? 'مراقبة العلامة / الويب المظلم' : 'Brand / dark-web monitoring'} sub={isAr ? 'Recorded Future · Digital Shadows · SOCRadar' : 'Recorded Future · Digital Shadows · SOCRadar'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'مراقبة العلامة التجارية والويب المظلم' : 'Brand & dark-web monitoring'}
        sub={isAr ? 'تصيد · نطاقات مزيفة · تسريبات بيانات اعتماد — مع مسار إزالة قانوني'
                  : 'Phishing · spoof domains · credential leaks — with legal takedown trail'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={AlertTriangle} label={isAr ? 'حرجة' : 'CRITICAL'}              value={String(counts.critical)}  tone={counts.critical > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Globe}         label={isAr ? 'مفتوحة' : 'Open'}                value={String(counts.open)}      tone={counts.open > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Send}          label={isAr ? 'إزالة قيد المعالجة' : 'Takedown in progress'} value={String(counts.takedown)} tone={counts.takedown > 0 ? 'warning' : 'success'} />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'إزالة مكتملة' : 'Takedown done'} value={String(counts.completed)} tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'critical',         label: isAr ? 'حرجة'         : 'CRITICAL',           count: counts.critical },
          { id: 'open',             label: isAr ? 'مفتوحة'      : 'Open',                count: counts.open },
          { id: 'takedown_pending', label: isAr ? 'إزالة جارية' : 'Takedown pending',    count: counts.takedown },
          { id: 'completed',        label: isAr ? 'مكتملة'      : 'Completed',           count: counts.completed },
          { id: 'all',              label: isAr ? 'الكل'         : 'All',                 count: counts.total },
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
              <th>{isAr ? 'الرمز' : 'Code'}</th>
              <th>{isAr ? 'المصدر' : 'Source'}</th>
              <th>{isAr ? 'الاكتشاف' : 'Detected'}</th>
              <th>{isAr ? 'الخطورة' : 'Severity'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'كلمة العلامة' : 'Brand keyword'}</th>
              <th>{isAr ? 'الموقع/URL' : 'Location / URL'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th></th>
            </tr></thead>
            <tbody>{rows.map(a => (
              <tr key={a.id}>
                <td className="mono" style={{ fontSize: 10 }}>
                  <Link to={`/socops/brand-monitoring/${a.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {a.alertCode}
                  </Link>
                </td>
                <td><Badge tone="info">{a.sourceSystem}</Badge></td>
                <td className="mono" style={{ fontSize: 10 }}>{a.detectedAt?.slice(0, 10)}</td>
                <td><Badge tone={SEV_TONE[a.severity] ?? 'neutral'}>{a.severity}</Badge></td>
                <td style={{ fontSize: 10 }}>{a.alertType ?? '—'}</td>
                <td style={{ fontSize: 10 }}>{a.brandKeyword ?? '—'}</td>
                <td className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {a.location ?? '—'}{a.urlObserved && <div>{a.urlObserved}</div>}
                  {a.evidenceSummary && <div style={{ fontSize: 9, color: 'var(--ink-3)' }}>{a.evidenceSummary.slice(0, 100)}</div>}
                </td>
                <td><Badge tone={STATUS_TONE[a.status] ?? 'neutral'}>{a.status}</Badge></td>
                <td>
                  {OPEN.has(a.status) && <Button size="sm" variant="primary" onClick={() => void doRequest(a.id)}><Send size={12} /> {isAr ? 'طلب إزالة' : 'Request'}</Button>}
                  {a.status === 'TAKEDOWN_REQUESTED' && <Button size="sm" variant="primary" onClick={() => void doComplete(a.id)}>{isAr ? 'تأكيد' : 'Confirm'}</Button>}
                </td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
