import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { KeyRound, RefreshCw, AlertTriangle, Lock, Atom } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type CryptoAsset, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'expiring_30' | 'expiring_90' | 'expired' | 'quantum_unsafe' | 'active';

const STATUS_TONE: Record<string, 'success' | 'warning' | 'danger' | 'neutral'> = {
  ACTIVE: 'success', EXPIRING: 'warning', EXPIRED: 'danger', REVOKED: 'danger', RETIRED: 'neutral',
};

export function CryptoAssetsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<CryptoAsset> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('expiring_90');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.cryptoAssets(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    if (filter === 'all') return all;
    if (filter === 'active') return all.filter(c => c.status === 'ACTIVE');
    if (filter === 'quantum_unsafe') return all.filter(c => !c.quantumSafe);
    if (filter === 'expired') return all.filter(c => c.validUntil && new Date(c.validUntil).getTime() < now);
    if (filter === 'expiring_30') return all.filter(c => c.validUntil
      && new Date(c.validUntil).getTime() >= now && new Date(c.validUntil).getTime() < now + 30 * 86400000);
    if (filter === 'expiring_90') return all.filter(c => c.validUntil
      && new Date(c.validUntil).getTime() >= now && new Date(c.validUntil).getTime() < now + 90 * 86400000);
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    return {
      total: all.length,
      expired: all.filter(c => c.validUntil && new Date(c.validUntil).getTime() < now).length,
      expiring30: all.filter(c => c.validUntil && new Date(c.validUntil).getTime() >= now && new Date(c.validUntil).getTime() < now + 30 * 86400000).length,
      expiring90: all.filter(c => c.validUntil && new Date(c.validUntil).getTime() >= now && new Date(c.validUntil).getTime() < now + 90 * 86400000).length,
      quantumUnsafe: all.filter(c => !c.quantumSafe).length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'الأصول التشفيرية' : 'Cryptographic assets'} sub={isAr ? 'شهادات TLS · مفاتيح HSM · توقيع' : 'TLS certs · HSM keys · signing certs'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'الأصول التشفيرية' : 'Cryptographic assets'}
        sub={isAr ? 'NCA + SAMA: لا شهادات منتهية · جاهزية ما بعد الكموم'
                  : 'NCA + SAMA: no expired certs · post-quantum readiness'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={AlertTriangle} label={isAr ? 'منتهية' : 'Expired'}             value={String(counts.expired)}      tone={counts.expired > 0 ? 'danger' : 'success'} />
        <Kpi Icon={KeyRound}      label={isAr ? 'تنتهي ≤٣٠ يوم' : 'Expiring ≤30d'} value={String(counts.expiring30)}   tone={counts.expiring30 > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Lock}          label={isAr ? 'تنتهي ≤٩٠ يوم' : 'Expiring ≤90d'} value={String(counts.expiring90)}   tone={counts.expiring90 > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Atom}          label={isAr ? 'غير آمنة كموماً' : 'Quantum-unsafe'} value={String(counts.quantumUnsafe)} tone={counts.quantumUnsafe > 0 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'expiring_30',     label: isAr ? '≤٣٠ يوم'      : 'Expiring ≤30d',    count: counts.expiring30 },
          { id: 'expiring_90',     label: isAr ? '≤٩٠ يوم'      : 'Expiring ≤90d',    count: counts.expiring90 },
          { id: 'expired',         label: isAr ? 'منتهية'      : 'Expired',           count: counts.expired },
          { id: 'quantum_unsafe',  label: isAr ? 'غير آمنة كموماً' : 'Quantum-unsafe', count: counts.quantumUnsafe },
          { id: 'all',             label: isAr ? 'الكل'         : 'All',              count: counts.total },
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
              <th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الخوارزمية' : 'Algorithm'}</th>
              <th>{isAr ? 'تنتهي' : 'Valid until'}</th>
              <th>{isAr ? 'منشور على' : 'Deployed on'}</th>
              <th>{isAr ? 'المالك' : 'Owner'}</th>
              <th>{isAr ? 'كمومياً' : 'Quantum-safe'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/crypto-assets/${c.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {c.assetCode}
                  </Link>
                </td>
                <td style={{ fontSize: 11 }}><strong>{isAr ? (c.titleAr ?? c.titleEn) : c.titleEn}</strong></td>
                <td style={{ fontSize: 11 }}>{c.assetType}</td>
                <td className="mono" style={{ fontSize: 10 }}>{c.algorithm ?? '—'}{c.keyLength ? `/${c.keyLength}` : ''}</td>
                <td className="mono" style={{ fontSize: 10 }}>{c.validUntil?.slice(0, 10) ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.deployedOn ?? '—'}</td>
                <td style={{ fontSize: 10 }}>{c.owner ?? '—'}</td>
                <td>{c.quantumSafe ? <Badge tone="success">{isAr ? 'نعم' : 'Yes'}</Badge> : <Badge tone="warning">{isAr ? 'لا' : 'No'}</Badge>}</td>
                <td><Badge tone={STATUS_TONE[c.status] ?? 'neutral'}>{c.status}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
