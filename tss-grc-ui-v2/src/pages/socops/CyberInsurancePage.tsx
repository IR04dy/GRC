import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldPlus, RefreshCw, Clock, Wallet } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type CyberInsurance } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'active' | 'renewal_90' | 'renewal_30' | 'with_claims';

const STATUS_TONE: Record<string, 'success' | 'warning' | 'danger' | 'neutral'> = {
  ACTIVE: 'success', PENDING_RENEWAL: 'warning', LAPSED: 'danger', CANCELLED: 'neutral', QUOTING: 'neutral',
};

function sar(n: number | null | undefined) {
  if (n == null) return '—';
  return n.toLocaleString('en-US');
}

export function CyberInsurancePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [rows, setRows] = useState<CyberInsurance[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.cyberInsurance(); setRows(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); }, []);

  const visible = useMemo(() => {
    const now = Date.now();
    if (filter === 'all') return rows;
    if (filter === 'active')      return rows.filter(p => p.status === 'ACTIVE');
    if (filter === 'with_claims') return rows.filter(p => (p.claimCount ?? 0) > 0);
    if (filter === 'renewal_90')  return rows.filter(p => p.renewalDueAt
      && new Date(p.renewalDueAt).getTime() >= now && new Date(p.renewalDueAt).getTime() < now + 90 * 86400000);
    if (filter === 'renewal_30')  return rows.filter(p => p.renewalDueAt
      && new Date(p.renewalDueAt).getTime() >= now && new Date(p.renewalDueAt).getTime() < now + 30 * 86400000);
    return rows;
  }, [rows, filter]);

  const counts = useMemo(() => {
    const now = Date.now();
    return {
      total: rows.length,
      active: rows.filter(p => p.status === 'ACTIVE').length,
      renewal90: rows.filter(p => p.renewalDueAt && new Date(p.renewalDueAt).getTime() >= now && new Date(p.renewalDueAt).getTime() < now + 90 * 86400000).length,
      renewal30: rows.filter(p => p.renewalDueAt && new Date(p.renewalDueAt).getTime() >= now && new Date(p.renewalDueAt).getTime() < now + 30 * 86400000).length,
      withClaims: rows.filter(p => (p.claimCount ?? 0) > 0).length,
      aggregateLimit: rows.filter(p => p.status === 'ACTIVE').reduce((s, p) => s + Number(p.aggregateLimitSar ?? 0), 0),
    };
  }, [rows]);

  return (
    <Shell title={isAr ? 'التأمين السيبراني' : 'Cyber insurance'} sub={isAr ? 'وثائق وسعات وتجديدات + سجل المطالبات' : 'Policies · limits · renewals · claims history'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'وثائق التأمين السيبراني' : 'Cyber insurance policies'}
        sub={isAr ? 'إيجار الوسطاء (Marsh KSA …) ينظر هذا الجدول كل تجديد'
                  : 'Brokers (Marsh KSA, AON …) pull from this register at every renewal'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ShieldPlus} label={isAr ? 'وثائق نشطة' : 'Active policies'}      value={String(counts.active)}    tone={counts.active > 0 ? 'success' : 'warning'} onClick={() => setFilter('active')} active={filter === 'active'} />
        <Kpi Icon={Clock}      label={isAr ? 'تجديد ≤٣٠ يوم' : 'Renewal ≤30d'}    value={String(counts.renewal30)} tone={counts.renewal30 > 0 ? 'danger' : 'success'} onClick={() => setFilter('renewal_30')} active={filter === 'renewal_30'} />
        <Kpi Icon={Clock}      label={isAr ? 'تجديد ≤٩٠ يوم' : 'Renewal ≤90d'}    value={String(counts.renewal90)} tone={counts.renewal90 > 0 ? 'warning' : 'success'} onClick={() => setFilter('renewal_90')} active={filter === 'renewal_90'} />
        <Kpi Icon={Wallet}     label={isAr ? 'إجمالي السقف (SAR)' : 'Total limit (SAR)'} value={sar(counts.aggregateLimit)} tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',         label: isAr ? 'الكل'         : 'All',           count: counts.total },
          { id: 'active',      label: isAr ? 'نشطة'        : 'Active',         count: counts.active },
          { id: 'renewal_30',  label: isAr ? '≤٣٠ يوم'    : 'Renewal ≤30d',   count: counts.renewal30 },
          { id: 'renewal_90',  label: isAr ? '≤٩٠ يوم'    : 'Renewal ≤90d',   count: counts.renewal90 },
          { id: 'with_claims', label: isAr ? 'بمطالبات'   : 'With claims',    count: counts.withClaims },
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
        {state === 'ready' && visible.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && visible.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'الوثيقة' : 'Policy'}</th>
              <th>{isAr ? 'شركة التأمين' : 'Carrier'}</th>
              <th>{isAr ? 'الوسيط' : 'Broker'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'السقف الإجمالي (SAR)' : 'Aggregate limit (SAR)'}</th>
              <th>{isAr ? 'لكل حادث (SAR)' : 'Per-event (SAR)'}</th>
              <th>{isAr ? 'تحمّل (SAR)' : 'Retention (SAR)'}</th>
              <th>{isAr ? 'فترة السريان' : 'Effective'}</th>
              <th>{isAr ? 'تجديد' : 'Renewal'}</th>
              <th>{isAr ? 'مطالبات' : 'Claims'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{visible.map(p => (
              <tr key={p.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/cyber-insurance/${p.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {p.policyNumber}
                  </Link>
                </td>
                <td style={{ fontSize: 11 }}><strong>{p.carrierName}</strong></td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{p.brokerName ?? '—'}</td>
                <td style={{ fontSize: 11 }}><Badge tone="info">{p.policyType}</Badge></td>
                <td className="mono" style={{ fontSize: 10 }}>{sar(p.aggregateLimitSar)}</td>
                <td className="mono" style={{ fontSize: 10 }}>{sar(p.perEventLimitSar)}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{sar(p.retentionSar)}</td>
                <td className="mono" style={{ fontSize: 10 }}>{p.effectiveFrom?.slice(0, 10)} → {p.effectiveTo?.slice(0, 10)}</td>
                <td className="mono" style={{ fontSize: 10 }}>{p.renewalDueAt?.slice(0, 10) ?? '—'}</td>
                <td className="mono" style={{ fontSize: 10 }}>
                  {p.claimCount > 0 ? <Badge tone="warning">{p.claimCount}</Badge> : '0'}
                  {p.aggregateClaimsPaidSar != null && p.aggregateClaimsPaidSar > 0 && (
                    <div style={{ fontSize: 9, color: 'var(--ink-3)' }}>{sar(p.aggregateClaimsPaidSar)} SAR</div>
                  )}
                </td>
                <td><Badge tone={STATUS_TONE[p.status] ?? 'neutral'}>{p.status}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
