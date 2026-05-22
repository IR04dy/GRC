import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldAlert, RefreshCw, Globe, Crosshair, CheckCircle2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type ThreatIntel, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'unactioned' | 'critical' | 'actioned';

const SEVERITY_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral',
};

const TLP_TONE: Record<string, 'danger' | 'warning' | 'success' | 'info'> = {
  RED: 'danger', AMBER: 'warning', GREEN: 'success', WHITE: 'info',
};

export function ThreatIntelPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<ThreatIntel> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('unactioned');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.threatIntel(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'unactioned') return all.filter(t => !t.actioned);
    if (filter === 'critical')   return all.filter(t => (t.severity === 'CRITICAL' || t.severity === 'HIGH') && !t.actioned);
    if (filter === 'actioned')   return all.filter(t => t.actioned);
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      unactioned: all.filter(t => !t.actioned).length,
      critical: all.filter(t => (t.severity === 'CRITICAL' || t.severity === 'HIGH') && !t.actioned).length,
      actioned: all.filter(t => t.actioned).length,
    };
  }, [page]);

  async function doAction(id: number, actioned: boolean) {
    try {
      await socopsApi.threatIntelAction(id, { actioned, actionNotes: isAr ? 'تم من شاشة الاستخبارات' : 'From TI screen' });
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      void load();
    } catch (e) { toast.danger((e as Error)?.message ?? 'Action failed'); }
  }

  return (
    <Shell title={isAr ? 'الاستخبارات السيبرانية' : 'Threat intelligence'} sub={isAr ? 'IOCs ومؤشرات MITRE' : 'IOCs and MITRE technique tracking'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'الاستخبارات السيبرانية' : 'Threat intelligence'}
        sub={isAr ? 'Saudi-CERT · NCA · FS-ISAC · MISP — تكامل المصادر' : 'Saudi-CERT · NCA · FS-ISAC · MISP — feed aggregation'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ShieldAlert} label={isAr ? 'بانتظار الإجراء' : 'Unactioned'} value={String(counts.unactioned)} tone={counts.unactioned > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Crosshair}   label={isAr ? 'حرجة/عالية' : 'CRITICAL/HIGH'}    value={String(counts.critical)}   tone={counts.critical > 0 ? 'danger' : 'success'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'تم' : 'Actioned'}                 value={String(counts.actioned)}   tone="info" />
        <Kpi Icon={Globe}       label={isAr ? 'الإجمالي' : 'Total'}                value={String(counts.total)}      tone="neutral" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'unactioned', label: isAr ? 'بانتظار الإجراء' : 'Unactioned', count: counts.unactioned },
          { id: 'critical',   label: isAr ? 'حرجة/عالية'      : 'CRITICAL/HIGH', count: counts.critical },
          { id: 'actioned',   label: isAr ? 'تم'              : 'Actioned',     count: counts.actioned },
          { id: 'all',        label: isAr ? 'الكل'            : 'All',          count: counts.total },
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
              <th>{isAr ? 'النشر' : 'Published'}</th>
              <th>{isAr ? 'المصدر' : 'Source'}</th>
              <th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'الخطورة' : 'Severity'}</th>
              <th>TLP</th>
              <th>{isAr ? 'مؤشر / قيمة' : 'IOC / Value'}</th>
              <th>MITRE</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th></th>
            </tr></thead>
            <tbody>{rows.map(t => (
              <tr key={t.id}>
                <td className="mono" style={{ fontSize: 10 }}>{t.publishedAt?.slice(0, 10)}</td>
                <td style={{ fontSize: 11 }}><Badge tone="info">{t.source}</Badge></td>
                <td style={{ fontSize: 11, maxWidth: 320 }}>
                  <Link to={`/socops/threat-intel/${t.id}`} style={{ color: 'var(--primary)', textDecoration: 'none' }}>
                    <strong>{isAr ? (t.titleAr ?? t.titleEn) : t.titleEn}</strong>
                  </Link>
                  {t.affectedSectors && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t.affectedSectors}</div>}
                </td>
                <td>{t.severity ? <Badge tone={SEVERITY_TONE[t.severity] ?? 'neutral'}>{t.severity}</Badge> : '—'}</td>
                <td>{t.tlp ? <Badge tone={TLP_TONE[t.tlp] ?? 'neutral'}>TLP:{t.tlp}</Badge> : '—'}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {t.iocType && <Badge tone="neutral" className="me-1">{t.iocType}</Badge>}
                  {t.iocValue ?? '—'}
                </td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t.mitreTechniques ?? '—'}</td>
                <td>{t.actioned ? <Badge tone="success">{isAr ? 'تم' : 'Actioned'}</Badge> : <Badge tone="warning">{isAr ? 'قيد الانتظار' : 'Pending'}</Badge>}</td>
                <td>
                  {!t.actioned && <Button size="sm" variant="primary" onClick={() => void doAction(t.id, true)}>{isAr ? 'تم' : 'Mark done'}</Button>}
                </td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
