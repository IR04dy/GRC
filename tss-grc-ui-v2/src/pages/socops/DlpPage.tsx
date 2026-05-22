import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldX, RefreshCw, FileWarning, EyeOff } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type DlpEvent, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'critical' | 'unreviewed' | 'top_secret' | 'blocked';

const SEV_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral',
};

const CLASSIFICATION_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  TOP_SECRET: 'danger', SECRET: 'danger', CONFIDENTIAL: 'warning', INTERNAL: 'info', PUBLIC: 'neutral',
};

const ACTION_TONE: Record<string, 'success' | 'warning' | 'danger' | 'neutral'> = {
  BLOCKED: 'success', ALERTED: 'warning', ALLOWED: 'danger', QUARANTINED: 'success',
};

const UNREVIEWED = new Set(['NEW', 'PENDING_REVIEW']);

export function DlpPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<DlpEvent> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('critical');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.dlp(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'critical')   return all.filter(e => e.severity === 'CRITICAL');
    if (filter === 'unreviewed') return all.filter(e => UNREVIEWED.has(e.status));
    if (filter === 'top_secret') return all.filter(e => e.dataClassification === 'TOP_SECRET' || e.dataClassification === 'SECRET');
    if (filter === 'blocked')    return all.filter(e => e.action === 'BLOCKED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      critical: all.filter(e => e.severity === 'CRITICAL').length,
      unreviewed: all.filter(e => UNREVIEWED.has(e.status)).length,
      topSecret: all.filter(e => e.dataClassification === 'TOP_SECRET' || e.dataClassification === 'SECRET').length,
      blocked: all.filter(e => e.action === 'BLOCKED').length,
    };
  }, [page]);

  async function doReview(id: number, outcome: string) {
    try {
      await socopsApi.dlpReview(id, outcome, isAr ? 'من شاشة DLP' : 'From DLP screen');
      toast.success(isAr ? 'تم تسجيل المراجعة' : 'Review recorded');
      void load();
    } catch (e) { toast.danger((e as Error)?.message ?? 'Review failed'); }
  }

  return (
    <Shell title={isAr ? 'أحداث DLP' : 'DLP events'} sub={isAr ? 'Microsoft Purview · Forcepoint · Symantec · Netskope' : 'Microsoft Purview · Forcepoint · Symantec · Netskope'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'أحداث منع تسرب البيانات (DLP)' : 'Data Loss Prevention events'}
        sub={isAr ? 'تصنيف NDC: عام · داخلي · سري · سري للغاية — مع مسار مراجعة'
                  : 'NDC classification: Public · Internal · Confidential · Secret · Top-Secret — with reviewer trail'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ShieldX}     label={isAr ? 'حرجة' : 'CRITICAL'}             value={String(counts.critical)}   tone={counts.critical > 0 ? 'danger' : 'success'} />
        <Kpi Icon={EyeOff}      label={isAr ? 'بانتظار المراجعة' : 'Unreviewed'} value={String(counts.unreviewed)} tone={counts.unreviewed > 0 ? 'warning' : 'success'} />
        <Kpi Icon={FileWarning} label={isAr ? 'سري/سري للغاية' : 'TOP_SECRET / SECRET'} value={String(counts.topSecret)} tone={counts.topSecret > 0 ? 'danger' : 'success'} />
        <Kpi Icon={ShieldX}     label={isAr ? 'محظور' : 'Blocked'}              value={String(counts.blocked)}    tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'critical',   label: isAr ? 'حرجة'         : 'CRITICAL',          count: counts.critical },
          { id: 'unreviewed', label: isAr ? 'بانتظار'      : 'Unreviewed',        count: counts.unreviewed },
          { id: 'top_secret', label: isAr ? 'سري'          : 'TOP_SECRET',        count: counts.topSecret },
          { id: 'blocked',    label: isAr ? 'محظور'        : 'Blocked',           count: counts.blocked },
          { id: 'all',        label: isAr ? 'الكل'         : 'All',               count: counts.total },
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
              <th>{isAr ? 'الوقت' : 'Time'}</th>
              <th>{isAr ? 'المصدر' : 'Source'}</th>
              <th>{isAr ? 'الخطورة' : 'Severity'}</th>
              <th>{isAr ? 'المستخدم' : 'User'}</th>
              <th>{isAr ? 'القناة' : 'Channel'}</th>
              <th>{isAr ? 'السياسة' : 'Policy'}</th>
              <th>{isAr ? 'التصنيف' : 'Classification'}</th>
              <th>{isAr ? 'الملف' : 'File'}</th>
              <th>{isAr ? 'الوجهة' : 'Destination'}</th>
              <th>{isAr ? 'الإجراء' : 'Action'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th></th>
            </tr></thead>
            <tbody>{rows.map(e => (
              <tr key={e.id}>
                <td className="mono" style={{ fontSize: 10 }}>{e.eventTime?.slice(0, 16).replace('T', ' ')}</td>
                <td><Badge tone="info">{e.sourceSystem}</Badge></td>
                <td><Badge tone={SEV_TONE[e.severity] ?? 'neutral'}>{e.severity}</Badge></td>
                <td style={{ fontSize: 10 }}>{e.username ?? '—'}{e.endpoint && <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{e.endpoint}</div>}</td>
                <td style={{ fontSize: 10 }}>{e.channel ?? '—'}</td>
                <td style={{ fontSize: 10, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  <Link to={`/socops/dlp/${e.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {e.policyName ?? `DLP #${e.id}`}
                  </Link>
                </td>
                <td>{e.dataClassification ? <Badge tone={CLASSIFICATION_TONE[e.dataClassification] ?? 'neutral'}>{e.dataClassification}</Badge> : '—'}</td>
                <td style={{ fontSize: 10, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }}>{e.filename ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)', maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }}>{e.destination ?? '—'}</td>
                <td>{e.action ? <Badge tone={ACTION_TONE[e.action] ?? 'neutral'}>{e.action}</Badge> : '—'}</td>
                <td><Badge tone={UNREVIEWED.has(e.status) ? 'warning' : 'success'}>{e.status}</Badge></td>
                <td>
                  {UNREVIEWED.has(e.status) && (
                    <>
                      <Button size="sm" variant="primary" onClick={() => void doReview(e.id, 'TRUE_POSITIVE')}>{isAr ? 'صحيح' : 'TP'}</Button>
                      <Button size="sm" variant="ghost"   onClick={() => void doReview(e.id, 'FALSE_POSITIVE')}>{isAr ? 'خطأ' : 'FP'}</Button>
                    </>
                  )}
                </td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
